#!/usr/bin/env python3
"""
Gera app/src/main/assets/pacotes/base-real.gpkg: um pacote BASE de verdade, com camadas reais
e leves do WFS do IDE-Sisema, sempre embarcado no APK — nao depende de rodar
ferramentas/montar-pacote.sh nem de conexao em campo.

So entram aqui camadas cujo GeoJSON bruto veio leve o bastante para simplificar com seguranca
(tolerancia de 5 m, o mesmo padrao de montar-pacote.sh) e ainda caber no aplicativo. Camadas
fragmentadas em dezenas de milhares de feicoes (drenagem de classe especial, por exemplo) ou
grandes demais ficam so na consulta AO VIVO (`geo/ConsultaOnline.kt`), nunca aqui.

Curadoria de 10/09/2026: pedido de Francisco para cobrir mais fatores de restricao/vedacao/
criterio locacional do IDE-Sisema (reserva da biosfera, sitios Ramsar, area de seguranca
aeroportuaria, unidades de conservacao, RPPN, zona de amortecimento, corredor ecologico,
patrimonio cultural, conflito por recursos hidricos) — contagem de feicoes conferida via
`resultType=hits` antes de baixar qualquer coisa (ver conversa da sessao), exatamente a mesma
disciplina que ja valeu para terras indigenas/quilombolas/Mata Atlantica.

Uso:  python3 ferramentas/gerar-pacote-base-real.py
Precisa de conexao que alcance geoserver.meioambiente.mg.gov.br.
"""
import json
import os
import sqlite3
import sys
import urllib.request
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _geo_gpkg import (
    bbox_de,
    blob_gpkg,
    contar_vertices,
    simplificar_multipoligono,
    wkb_multipoligono,
    wkb_ponto,
)

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DESTINO = os.path.join(RAIZ, "app/src/main/assets/pacotes/base-real.gpkg")

WFS = "https://geoserver.meioambiente.mg.gov.br/ows"
SRS_SIRGAS2000 = 4674
TOLERANCIA_M = 5.0
TOLERANCIA_GRAUS = TOLERANCIA_M / 111_320.0

# tabela, nome, fonte, tecnico (WFS), tipo, raio_m, campos de rotulo (na ordem de preferencia),
# tolerancia_m (None = usa TOLERANCIA_M padrao, 5 m).
#
# hits= é a contagem de feicoes conferida com resultType=hits em 10/09/2026, antes de baixar —
# fica no comentario para a proxima curadoria nao precisar reconferir do zero.
#
# Tolerancia MAIOR que o padrao nas camadas de contorno grande e numeroso (unidade de
# conservacao, reserva da biosfera, zona de amortecimento, RPPN, seguranca aeroportuaria):
# com poucas dezenas a centenas de feicoes mas contornos administrativos bem detalhados, 5 m
# gerava dezenas de milhares de vertices por camada e inflava o pacote na casa dos MB. 20 m e
# grosseiro demais para um raio de restricao de metros (por isso as camadas ja embarcadas —
# terras indigenas/quilombolas, Mata Atlantica — continuam em 5 m), mas serve bem para "este
# ponto esta dentro/perto desta unidade de conservacao", que e exatamente o alerta que estas
# camadas dao.
TOLERANCIA_CONTEXTO_M = 20.0
# As duas ou tres camadas mais pesadas de cada leva de curadoria continuam grandes demais mesmo
# a 20 m (dezenas de milhares de vertices) — sao contornos administrativos com detalhe muito
# fino (reserva da biosfera, patrimonio cultural, conflito por recursos hidricos). 50-100 m
# ainda da "este ponto esta perto/dentro desta area", que e o alerta que a camada existe para
# dar; o valor exato da tolerancia fica gravado por camada e a tela de configuracoes MOSTRA esse
# numero — a app nunca finge uma precisao que nao tem.
TOLERANCIA_PESADA_M = 50.0
TOLERANCIA_MUITO_PESADA_M = 100.0

CAMADAS = [
    (
        "terras_indigenas", "Terras indígenas", "Funai/IDE-Sisema",
        "IDE:ide_2003_mg_terras_indigenas_pol", "poligono", None, ["terrai_nom"], None,
    ),
    (
        "terras_quilombolas", "Terras quilombolas", "Incra/IDE-Sisema",
        "IDE:ide_2005_mg_terras_quilombolas_pol", "poligono", None, ["nm_comunid"], None,
    ),
    (
        "area_lei_mata_atlantica", "Área de aplicação da Lei da Mata Atlântica (11.428/2006)",
        "MMA/IDE-Sisema", "IDE:ide_2020_mg_area_lei_mata_atlantica_pol", "poligono", None, ["bioma"], None,
    ),
    # --- curadoria de 10/09/2026 daqui para baixo ---
    (
        "reserva_biosfera_caatinga", "Reserva da Biosfera da Caatinga", "MMA/UNESCO/IDE-Sisema",
        "IDE:ide_2012_mg_reserva_biosfera_caatinga_pol", "poligono", None, ["zona"], None,  # hits=8
    ),
    (
        "reserva_biosfera_mata_atlantica", "Reserva da Biosfera da Mata Atlântica", "MMA/UNESCO/IDE-Sisema",
        "IDE:ide_2012_mg_reserva_biosfera_mata_atlantica_pol", "poligono", None, ["zona"],
        TOLERANCIA_MUITO_PESADA_M,  # hits=426, pesadissima mesmo a 20 m
    ),
    (
        "reserva_biosfera_serra_espinhaco", "Reserva da Biosfera da Serra do Espinhaço", "MMA/UNESCO/IDE-Sisema",
        "IDE:ide_2012_mg_reserva_biosfera_serra_espinhaco_pol", "poligono", None, ["zona"],
        TOLERANCIA_PESADA_M,  # hits=42
    ),
    (
        "sitios_ramsar", "Sítios Ramsar (zonas úmidas de importância internacional)", "Igam/IDE-Sisema",
        "IDE:ide_2016_mg_sitios_ramsar_pol", "poligono", None, ["nome"], None,  # hits=2
    ),
    (
        "seguranca_aeroportuaria", "Área de segurança aeroportuária", "Feam/Decea/IDE-Sisema",
        "IDE:ide_2015_mg_areas_seguranca_aeroportuaria_pol", "poligono", None, ["nome", "localidade"],
        TOLERANCIA_PESADA_M,  # hits=339
    ),
    (
        "aerodromos", "Aeródromos", "Decea/IDE-Sisema",
        "IDE:ide_0403_mg_aerodromos_pto", "ponto", 0.0, ["nome", "localidade"], None,  # hits=339
    ),
    (
        "uc_estaduais", "Unidades de Conservação estaduais", "IEF/IDE-Sisema",
        "IDE:ide_2010_mg_unidades_conservacao_estaduais_pol", "poligono", None, ["nome_uc"],
        TOLERANCIA_PESADA_M,  # hits=95
    ),
    (
        "uc_federais", "Unidades de Conservação federais", "ICMBio/IDE-Sisema",
        "IDE:ide_2010_mg_unidades_conservacao_federais_pol", "poligono", None, ["nome_uc"],
        TOLERANCIA_PESADA_M,  # hits=95
    ),
    (
        "uc_municipais", "Unidades de Conservação municipais", "Municípios/IDE-Sisema",
        "IDE:ide_2010_mg_unidades_conservacao_municipais_pol", "poligono", None, ["nome_uc"],
        TOLERANCIA_PESADA_M,  # hits=195
    ),
    (
        "rppn", "Reserva Particular do Patrimônio Natural (RPPN)", "IEF/IDE-Sisema",
        "IDE:ide_2010_mg_reservas_particulares_patrimonio_natural_pol", "poligono", None, ["nome_uc"],
        TOLERANCIA_PESADA_M,  # hits=349
    ),
    (
        "amortecimento_plano_manejo", "Zona de amortecimento — plano de manejo", "IEF/IDE-Sisema",
        "IDE:ide_2011_mg_amortecimento_uc_plano_manejo_pol", "poligono", None, ["nome_uc"],
        TOLERANCIA_PESADA_M,  # hits=58
    ),
    (
        "amortecimento_raio_3km", "Zona de amortecimento — raio de 3 km (sem plano de manejo)", "IEF/IDE-Sisema",
        "IDE:ide_2011_mg_amortecimento_uc_raio_3km_pol", "poligono", None, ["nome_uc"],
        TOLERANCIA_CONTEXTO_M,  # hits=71
    ),
    (
        "corredor_espinhaco_serra_curral", "Corredor Ecológico Espinhaço–Serra do Curral", "IEF/IDE-Sisema",
        "IDE:ide_2013_mg_corredor_ecologico_espinhaco_serra_curral_pol", "poligono", None, ["nome"], None,  # hits=1
    ),
    (
        "corredor_serra_moeda_aredes", "Corredor Ecológico Serra da Moeda–Aredes", "IEF/IDE-Sisema",
        "IDE:ide_2013_mg_corredor_ecologico_serra_moeda_aredes_pol", "poligono", None, ["nome"], None,  # hits=1
    ),
    (
        "corredor_sossego_caratinga", "Corredor Ecológico Sossêgo–Caratinga", "IEF/IDE-Sisema",
        "IDE:ide_2013_mg_corredor_ecologico_sossego_caratinga_pol", "poligono", None, ["nome"], None,  # hits=1
    ),
    (
        "patrimonio_cultural", "Área de influência do patrimônio cultural protegido", "Iepha-MG/IDE-Sisema",
        "IDE:ide_2017_mg_ai_patrimonio_cultural_iepha_pol", "poligono", None, ["poligono"],
        TOLERANCIA_MUITO_PESADA_M,  # hits=1, poligono unico com detalhe altissimo
    ),
    (
        "conflito_recursos_hidricos", "Área de conflito por recursos hídricos", "Igam/IDE-Sisema",
        "IDE:ide_2007_mg_area_conflito_recursos_hidricos_pol", "poligono", None, ["nomtrecho"],
        TOLERANCIA_MUITO_PESADA_M,  # hits=110
    ),
]


def baixar_geojson(tecnico):
    url = (
        f"{WFS}?service=wfs&version=2.0.0&request=GetFeature"
        f"&typeNames={tecnico}&outputFormat=application/json&srsName=EPSG:{SRS_SIRGAS2000}"
    )
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.load(r)


def remover_z(coords):
    """Algumas camadas do WFS devolvem [x, y, 0] em vez de [x, y] — normaliza para 2D antes de
    simplificar/gravar, senao Douglas-Peucker e o encoder WKB (que so esperam x,y) quebram."""
    if not coords:
        return coords
    primeiro = coords[0]
    if isinstance(primeiro, (int, float)):
        return coords[:2]
    return [remover_z(c) for c in coords]


def rotulo_de(props, campos, nome_padrao):
    for campo in campos:
        v = props.get(campo)
        if v not in (None, ""):
            return str(v)
    return nome_padrao


def main():
    os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
    if os.path.exists(DESTINO):
        os.remove(DESTINO)
    con = sqlite3.connect(DESTINO)
    cur = con.cursor()

    hoje = date.today().isoformat()
    cur.execute("CREATE TABLE pericia_pacote (chave TEXT PRIMARY KEY, valor TEXT)")
    for chave, valor in [
        ("versao", f"base-real-{hoje}"),
        ("gerado_em", hoje),
        ("origem", "IDE-Sisema (WFS publico) — camadas reais leves, embarcadas no APK"),
        ("endpoint", WFS),
    ]:
        cur.execute("INSERT INTO pericia_pacote VALUES (?, ?)", (chave, valor))

    cur.execute(
        "CREATE TABLE pericia_camadas ("
        " tabela TEXT PRIMARY KEY, nome TEXT, fonte TEXT, uuid TEXT,"
        " data_extracao TEXT, tolerancia_m REAL, tipo TEXT, raio_m REAL, prioridade INTEGER)"
    )

    for prioridade, (tabela, nome, fonte, tecnico, tipo, raio_m, campos_rotulo, tolerancia_m) in enumerate(CAMADAS, start=1):
        tolerancia_efetiva_m = tolerancia_m or TOLERANCIA_M
        tolerancia_graus = tolerancia_efetiva_m / 111_320.0
        print(f"Baixando {nome} ({tecnico})...")
        dados = baixar_geojson(tecnico)
        feats = dados.get("features", [])

        cur.execute(f'CREATE TABLE "{tabela}" (fid INTEGER PRIMARY KEY, geom BLOB, rotulo TEXT)')
        cur.execute(f'CREATE VIRTUAL TABLE "rtree_{tabela}_geom" USING rtree(id, minx, maxx, miny, maxy)')

        antes = depois = 0
        for i, feat in enumerate(feats, start=1):
            geom = feat["geometry"]
            rotulo = rotulo_de(feat.get("properties") or {}, campos_rotulo, nome)

            if tipo == "ponto":
                # O WFS devolve MultiPoint de um ponto so (ex.: aerodromos) tanto quanto Point
                # puro, dependendo da camada — aceita os dois formatos.
                coords = geom["coordinates"]
                x, y = coords[0] if geom["type"] == "MultiPoint" else coords
                blob = blob_gpkg(wkb_ponto(x, y), SRS_SIRGAS2000)
                minx = maxx = x
                miny = maxy = y
            else:
                coords = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
                coords = remover_z(coords)
                antes += contar_vertices(coords)
                simples = simplificar_multipoligono(coords, tolerancia_graus)
                depois += contar_vertices(simples)
                blob = blob_gpkg(wkb_multipoligono(simples), SRS_SIRGAS2000)
                minx, maxx, miny, maxy = bbox_de(simples)

            cur.execute(
                f'INSERT INTO "{tabela}" (fid, geom, rotulo) VALUES (?, ?, ?)',
                (i, blob, rotulo),
            )
            cur.execute(
                f'INSERT INTO "rtree_{tabela}_geom" VALUES (?, ?, ?, ?, ?)',
                (i, minx, maxx, miny, maxy),
            )

        cur.execute(
            "INSERT INTO pericia_camadas VALUES (?,?,?,?,?,?,?,?,?)",
            (tabela, nome, fonte, None, hoje, 0.0 if tipo == "ponto" else tolerancia_efetiva_m, tipo, raio_m, prioridade),
        )
        if tipo == "ponto":
            print(f"  {len(feats)} feições (pontos)")
        else:
            print(f"  {len(feats)} feições, {antes} -> {depois} vértices")

    con.commit()
    con.close()
    tamanho = os.path.getsize(DESTINO)
    print(f"Gerado: {DESTINO} ({tamanho/1024:.0f} KB)")


if __name__ == "__main__":
    main()
