#!/usr/bin/env python3
"""
Gera app/src/main/assets/pacotes/base-real.gpkg: um pacote BASE de verdade, com camadas reais
e leves do WFS do IDE-Sisema, sempre embarcado no APK — nao depende de rodar
ferramentas/montar-pacote.sh nem de conexao em campo.

So entram aqui camadas cujo GeoJSON bruto veio leve o bastante para simplificar com seguranca
(tolerancia de 5 m, o mesmo padrao de montar-pacote.sh) e ainda caber no aplicativo. Varias
camadas do pacote completo (rios de preservacao permanente, corredores ecologicos por instancia,
zonas de amortecimento) sao pesadas demais ou vem fragmentadas em muitas sub-camadas — ficam
para o pacote oficial via montar-pacote.sh, nao aqui.

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
from _geo_gpkg import bbox_de, blob_gpkg, contar_vertices, simplificar_multipoligono, wkb_multipoligono

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DESTINO = os.path.join(RAIZ, "app/src/main/assets/pacotes/base-real.gpkg")

WFS = "https://geoserver.meioambiente.mg.gov.br/ows"
SRS_SIRGAS2000 = 4674
TOLERANCIA_M = 5.0
TOLERANCIA_GRAUS = TOLERANCIA_M / 111_320.0

# tabela, nome, fonte, tecnico (WFS), tipo — mesmas colunas de ferramentas/camadas.tsv.
CAMADAS = [
    (
        "terras_indigenas", "Terras indígenas", "Funai/IDE-Sisema",
        "IDE:ide_2003_mg_terras_indigenas_pol",
    ),
    (
        "terras_quilombolas", "Terras quilombolas", "Incra/IDE-Sisema",
        "IDE:ide_2005_mg_terras_quilombolas_pol",
    ),
    (
        "area_lei_mata_atlantica", "Área de aplicação da Lei da Mata Atlântica (11.428/2006)",
        "MMA/IDE-Sisema", "IDE:ide_2020_mg_area_lei_mata_atlantica_pol",
    ),
]


def baixar_geojson(tecnico):
    url = (
        f"{WFS}?service=wfs&version=2.0.0&request=GetFeature"
        f"&typeNames={tecnico}&outputFormat=application/json&srsName=EPSG:{SRS_SIRGAS2000}"
    )
    with urllib.request.urlopen(url, timeout=120) as r:
        return json.load(r)


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

    for prioridade, (tabela, nome, fonte, tecnico) in enumerate(CAMADAS, start=1):
        print(f"Baixando {nome} ({tecnico})...")
        dados = baixar_geojson(tecnico)

        cur.execute(f'CREATE TABLE "{tabela}" (fid INTEGER PRIMARY KEY, geom BLOB, rotulo TEXT)')
        cur.execute(f'CREATE VIRTUAL TABLE "rtree_{tabela}_geom" USING rtree(id, minx, maxx, miny, maxy)')

        antes = depois = 0
        for i, feat in enumerate(dados["features"], start=1):
            geom = feat["geometry"]
            coords = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
            antes += contar_vertices(coords)
            simples = simplificar_multipoligono(coords, TOLERANCIA_GRAUS)
            depois += contar_vertices(simples)

            blob = blob_gpkg(wkb_multipoligono(simples), SRS_SIRGAS2000)
            p = feat["properties"]
            rotulo = (
                p.get("terrai_nom") or p.get("nm_comunid") or p.get("bioma") or nome
            )
            cur.execute(
                f'INSERT INTO "{tabela}" (fid, geom, rotulo) VALUES (?, ?, ?)',
                (i, blob, rotulo),
            )
            minx, maxx, miny, maxy = bbox_de(simples)
            cur.execute(
                f'INSERT INTO "rtree_{tabela}_geom" VALUES (?, ?, ?, ?, ?)',
                (i, minx, maxx, miny, maxy),
            )

        cur.execute(
            "INSERT INTO pericia_camadas VALUES (?,?,?,?,?,?,?,?,?)",
            (tabela, nome, fonte, None, hoje, TOLERANCIA_M, "poligono", None, prioridade),
        )
        print(f"  {len(dados['features'])} feições, {antes} -> {depois} vértices")

    con.commit()
    con.close()
    tamanho = os.path.getsize(DESTINO)
    print(f"Gerado: {DESTINO} ({tamanho/1024:.0f} KB)")


if __name__ == "__main__":
    main()
