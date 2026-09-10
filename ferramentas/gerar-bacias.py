#!/usr/bin/env python3
"""
Gera app/src/main/assets/pacotes/circunscricoes-hidrograficas.gpkg a partir do WFS publico
do IDE-Sisema (dado real do IGAM, GEIRH — nao e exemplo/ficticio, ao contrario de
gerar-exemplo.py).

Circunscricoes Hidrograficas (CH) sao as unidades de gestao de recursos hidricos de MG — a
peca que faltava para "em que bacia/comite de bacia estou", util antes de uma conversa de
outorga. Camada leve e informativa, separada da logica de ALERTA de restricao (GeoPacote /
ConsultaRestricao): estar numa CH nao e uma restricao, e so contexto.

O poligono bruto do WFS tem ~1,6 milhao de vertices ao todo (43 regioes) — pesado demais para
embarcar no APK sem simplificar. Este script aplica Douglas-Peucker por anel antes de gravar,
com uma tolerancia deliberadamente grosseira (a finalidade e "em qual CH", nao limite fundiario
preciso).

Uso:  python3 ferramentas/gerar-bacias.py [arquivo.geojson]
Sem argumento, baixa direto do WFS (precisa de conexao que alcance geoserver.meioambiente.mg.gov.br).
"""
import json
import os
import struct
import sys
import urllib.request
from datetime import date

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DESTINO = os.path.join(RAIZ, "app/src/main/assets/pacotes/circunscricoes-hidrograficas.gpkg")

WFS = "https://geoserver.meioambiente.mg.gov.br/ows"
TYPE_NAME = "IDE:ide_1108_mg_circunscricoes_hidrograficas_pol"
SRS_SIRGAS2000 = 4674

# Em graus: ~300 m no paralelo do Brasil (1 grau ~= 111.32 km). E so para "em qual CH",
# nao para limite fundiario — a folga cabe no proprio nome da ferramenta.
TOLERANCIA_GRAUS = 300.0 / 111_320.0


def baixar_geojson():
    url = (
        f"{WFS}?service=wfs&version=2.0.0&request=GetFeature"
        f"&typeNames={TYPE_NAME}&outputFormat=application/json&srsName=EPSG:{SRS_SIRGAS2000}"
    )
    with urllib.request.urlopen(url, timeout=120) as r:
        return json.load(r)


def dist_ponto_reta(p, a, b):
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
    t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    cx, cy = ax + t * dx, ay + t * dy
    return ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5


def douglas_peucker(pontos, tolerancia):
    """Iterativo (pilha propria) para nao estourar profundidade de recursao em anel grande."""
    if len(pontos) <= 2:
        return pontos
    manter = bytearray(len(pontos))
    manter[0] = manter[-1] = 1
    pilha = [(0, len(pontos) - 1)]
    while pilha:
        ini, fim = pilha.pop()
        if fim - ini < 2:
            continue
        a, b = pontos[ini], pontos[fim]
        maior_dist, maior_idx = -1.0, -1
        for i in range(ini + 1, fim):
            d = dist_ponto_reta(pontos[i], a, b)
            if d > maior_dist:
                maior_dist, maior_idx = d, i
        if maior_dist > tolerancia:
            manter[maior_idx] = 1
            pilha.append((ini, maior_idx))
            pilha.append((maior_idx, fim))
    return [p for p, m in zip(pontos, manter) if m]


def simplificar_anel(anel, tolerancia):
    if len(anel) <= 4:
        return anel
    corpo = douglas_peucker(anel[:-1], tolerancia)  # remove o fechamento p/ nao viesar o DP
    if len(corpo) < 3:
        return anel  # simplificacao demais colapsaria o anel — mantem o original
    corpo.append(corpo[0])
    return corpo


def simplificar_multipoligono(coords, tolerancia):
    return [
        [simplificar_anel(anel, tolerancia) for anel in poligono]
        for poligono in coords
    ]


def wkb_multipoligono(multipoligono):
    partes = [struct.pack("<B", 1), struct.pack("<I", 6), struct.pack("<I", len(multipoligono))]
    for poligono in multipoligono:
        partes.append(struct.pack("<B", 1))
        partes.append(struct.pack("<I", 3))
        partes.append(struct.pack("<I", len(poligono)))
        for anel in poligono:
            partes.append(struct.pack("<I", len(anel)))
            for x, y in anel:
                partes.append(struct.pack("<dd", x, y))
    return b"".join(partes)


def wkb_poligono_simples(aneis):
    return wkb_multipoligono([aneis])


def blob_gpkg(wkb, srs_id):
    flags = 0b00000001
    cabecalho = b"GP" + struct.pack("<B", 0) + struct.pack("<B", flags) + struct.pack("<i", srs_id)
    return cabecalho + wkb


def bbox_de(coords_multipoligono):
    xs, ys = [], []
    for poligono in coords_multipoligono:
        for anel in poligono:
            for x, y in anel:
                xs.append(x)
                ys.append(y)
    return min(xs), max(xs), min(ys), max(ys)


def main():
    if len(sys.argv) > 1:
        dados = json.load(open(sys.argv[1], encoding="utf-8"))
    else:
        print("Baixando do WFS...")
        dados = baixar_geojson()

    import sqlite3

    os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
    if os.path.exists(DESTINO):
        os.remove(DESTINO)
    con = sqlite3.connect(DESTINO)
    cur = con.cursor()

    hoje = date.today().isoformat()
    cur.execute("CREATE TABLE pericia_pacote (chave TEXT PRIMARY KEY, valor TEXT)")
    for chave, valor in [
        ("versao", "geirh_v12_2025-02-25"),
        ("gerado_em", hoje),
        ("origem", "IGAM/SEMAD — GEIRH v12 (25/02/2025), via WFS do IDE-Sisema"),
        ("endpoint", WFS),
    ]:
        cur.execute("INSERT INTO pericia_pacote VALUES (?, ?)", (chave, valor))

    cur.execute(
        "CREATE TABLE circunscricoes_hidrograficas ("
        " fid INTEGER PRIMARY KEY, geom BLOB,"
        " sigla TEXT, nome TEXT, cbh TEXT, situ_cbh TEXT, area_km2 TEXT, decreto TEXT)"
    )
    cur.execute("CREATE VIRTUAL TABLE rtree_circunscricoes_hidrograficas_geom USING rtree(id, minx, maxx, miny, maxy)")

    total_antes = total_depois = 0
    for i, feat in enumerate(dados["features"], start=1):
        geom = feat["geometry"]
        coords = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]

        def conta(c):
            return sum(len(anel) for poligono in c for anel in poligono)

        total_antes += conta(coords)
        simplificado = simplificar_multipoligono(coords, TOLERANCIA_GRAUS)
        total_depois += conta(simplificado)

        wkb = wkb_multipoligono(simplificado)
        blob = blob_gpkg(wkb, SRS_SIRGAS2000)
        p = feat["properties"]
        cur.execute(
            "INSERT INTO circunscricoes_hidrograficas "
            "(fid, geom, sigla, nome, cbh, situ_cbh, area_km2, decreto) VALUES (?,?,?,?,?,?,?,?)",
            (i, blob, p.get("sigla"), p.get("nome"), p.get("cbh"), p.get("situ_cbh"),
             str(p.get("area_km2")), p.get("decreto")),
        )
        minx, maxx, miny, maxy = bbox_de(simplificado)
        cur.execute(
            "INSERT INTO rtree_circunscricoes_hidrograficas_geom VALUES (?,?,?,?,?)",
            (i, minx, maxx, miny, maxy),
        )

    cur.execute(
        "CREATE TABLE pericia_camadas ("
        " tabela TEXT PRIMARY KEY, nome TEXT, fonte TEXT, uuid TEXT,"
        " data_extracao TEXT, tolerancia_m REAL, tipo TEXT, raio_m REAL, prioridade INTEGER)"
    )
    cur.execute(
        "INSERT INTO pericia_camadas VALUES (?,?,?,?,?,?,?,?,?)",
        (
            "circunscricoes_hidrograficas",
            "Circunscrições Hidrográficas (CH) — IGAM",
            "IGAM/SEMAD, GEIRH v12 (25/02/2025)",
            None,
            hoje,
            300.0,
            "poligono",
            None,
            1,
        ),
    )

    con.commit()
    con.close()
    tamanho = os.path.getsize(DESTINO)
    print(f"Vertices: {total_antes} -> {total_depois} ({100*total_depois/total_antes:.1f}%)")
    print(f"Gerado: {DESTINO} ({tamanho/1024:.0f} KB)")


if __name__ == "__main__":
    main()
