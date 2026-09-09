#!/usr/bin/env python3
"""
Gera app/src/main/assets/pacotes/exemplo.gpkg — a camada de exemplo pre-carregada no APK.

Por que existe. O pacote de verdade (ferramentas/montar-pacote.sh) precisa de gdal e de uma
conexao no Brasil, e so fica pronto depois que alguem roda os tres passos manualmente. Sem ele,
quem acabou de instalar o app nao ve NENHUM exemplo de como o alerta de restricao funciona. Este
script gera um pacote sintetico, com um poligono FICTICIO — nao e dado do IDE-Sisema, e cada
campo de nome e fonte diz isso em letras claras, para que um alerta gerado a partir dele nunca
possa ser confundido com uma restricao real (mesma logica de nao fabricar dado: ver a skill
apps-kit-pericia-ambiental).

So usa a biblioteca padrao do Python (sqlite3 + struct): nao precisa de gdal/ogr2ogr para um
poligono so. O layout binario do BLOB de geometria segue exatamente o que
geo/GeoPacote.kt.lerGeometria() sabe ler: cabecalho GPKG (magic 'GP' + versao + flags + srs_id,
sem envelope) seguido de WKB padrao (little endian).

Uso:  python3 ferramentas/gerar-exemplo.py
"""
import os
import sqlite3
import struct
from datetime import date

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DESTINO = os.path.join(RAIZ, "app/src/main/assets/pacotes/exemplo.gpkg")

# Ponto sintetico em MG, num numero redondo de grau — deliberadamente NAO e o endereco de
# nenhum imovel real, so um centro de grade para o exemplo.
LAT_CENTRO = -19.90
LON_CENTRO = -43.90
MEIO_LADO_GRAUS = 0.00075  # ~83 m de meio-lado, ~165 m de lado

SRS_SIRGAS2000 = 4674


def anel_quadrado(lat_c, lon_c, meio_lado):
    """Anel fechado (primeiro == ultimo ponto), sentido horario, em (x=lon, y=lat)."""
    pontos = [
        (lon_c - meio_lado, lat_c - meio_lado),
        (lon_c + meio_lado, lat_c - meio_lado),
        (lon_c + meio_lado, lat_c + meio_lado),
        (lon_c - meio_lado, lat_c + meio_lado),
    ]
    pontos.append(pontos[0])
    return pontos


def wkb_poligono(anel):
    """WKB Polygon, little endian, um anel externo, sem buraco."""
    partes = [struct.pack("<B", 1)]          # byte order: little endian
    partes.append(struct.pack("<I", 3))      # tipo: Polygon
    partes.append(struct.pack("<I", 1))      # 1 anel
    partes.append(struct.pack("<I", len(anel)))
    for x, y in anel:
        partes.append(struct.pack("<dd", x, y))
    return b"".join(partes)


def blob_gpkg(wkb, srs_id):
    """Cabecalho GPKG minimo: magic 'GP', versao 0, flags (LE, sem envelope), srs_id (LE)."""
    flags = 0b00000001  # bit0 = little endian; bits1-3 = 0 (sem envelope); bit4 = 0 (nao vazio)
    cabecalho = b"GP" + struct.pack("<B", 0) + struct.pack("<B", flags) + struct.pack("<i", srs_id)
    return cabecalho + wkb


def main():
    os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
    if os.path.exists(DESTINO):
        os.remove(DESTINO)

    con = sqlite3.connect(DESTINO)
    cur = con.cursor()

    cur.execute("CREATE TABLE pericia_pacote (chave TEXT PRIMARY KEY, valor TEXT)")
    hoje = date.today().isoformat()
    for chave, valor in [
        ("versao", "exemplo"),
        ("gerado_em", hoje),
        ("origem", "Camada de exemplo (ferramentas/gerar-exemplo.py) — NAO e dado do IDE-Sisema"),
        ("endpoint", ""),
    ]:
        cur.execute("INSERT INTO pericia_pacote VALUES (?, ?)", (chave, valor))

    cur.execute(
        "CREATE TABLE pericia_camadas ("
        " tabela TEXT PRIMARY KEY, nome TEXT, fonte TEXT, uuid TEXT,"
        " data_extracao TEXT, tolerancia_m REAL, tipo TEXT, raio_m REAL, prioridade INTEGER)"
    )
    cur.execute(
        "INSERT INTO pericia_camadas VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            "exemplo_area",
            "Área de exemplo (fictícia)",
            "Camada de demonstração — não é dado oficial do IDE-Sisema",
            None,
            hoje,
            5.0,
            "poligono",
            None,
            1,
        ),
    )

    cur.execute("CREATE TABLE exemplo_area (fid INTEGER PRIMARY KEY, geom BLOB, nome TEXT)")
    anel = anel_quadrado(LAT_CENTRO, LON_CENTRO, MEIO_LADO_GRAUS)
    geom = blob_gpkg(wkb_poligono(anel), SRS_SIRGAS2000)
    cur.execute(
        "INSERT INTO exemplo_area (fid, geom, nome) VALUES (1, ?, ?)",
        (geom, "Área de exemplo (fictícia) — não é uma restrição real"),
    )

    lons = [p[0] for p in anel]
    lats = [p[1] for p in anel]
    cur.execute("CREATE VIRTUAL TABLE rtree_exemplo_area_geom USING rtree(id, minx, maxx, miny, maxy)")
    cur.execute(
        "INSERT INTO rtree_exemplo_area_geom VALUES (1, ?, ?, ?, ?)",
        (min(lons), max(lons), min(lats), max(lats)),
    )

    con.commit()
    con.close()
    tamanho = os.path.getsize(DESTINO)
    print(f"Gerado: {DESTINO} ({tamanho} bytes)")


if __name__ == "__main__":
    main()
