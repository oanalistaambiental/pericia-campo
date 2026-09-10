#!/usr/bin/env python3
"""
Funcoes compartilhadas por gerar-exemplo.py, gerar-bacias.py e gerar-pacote-base-real.py:
simplificacao de poligono (Douglas-Peucker puro) e codificacao no formato binario exato que
geo/GeoPacote.kt sabe ler (cabecalho GPKG minimo + WKB padrao, sem depender de gdal).

Nao duplicar esta logica entre scripts era o proprio motivo de existir deste arquivo — o projeto
ja pagou o preco de logica geometrica duplicada divergir (ver o `Utm.formatado()` do enquadra-mg
antigo, sem Locale.US, na skill do projeto).
"""
import struct


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
    return [[simplificar_anel(anel, tolerancia) for anel in poligono] for poligono in coords]


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


def blob_gpkg(wkb, srs_id):
    flags = 0b00000001  # bit0 = little endian; bits1-3 = 0 (sem envelope); bit4 = 0 (nao vazio)
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


def contar_vertices(coords_multipoligono):
    return sum(len(anel) for poligono in coords_multipoligono for anel in poligono)
