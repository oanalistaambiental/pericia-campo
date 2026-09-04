#!/usr/bin/env bash
#
# Monta o pacote GeoPackage de restricoes ambientais a partir do WFS publico do IDE-Sisema.
#
# RODE ESTE SCRIPT DE UMA CONEXAO NO BRASIL. O ambiente onde o projeto foi gerado nao alcanca
# hosts .gov.br, entao os nomes tecnicos das camadas ainda precisam ser descobertos aqui.
#
# Precisa de: curl e gdal (ogr2ogr).
#   Ubuntu/Debian: sudo apt install gdal-bin curl
#   macOS:         brew install gdal curl
#   Windows:       use o OSGeo4W, ou rode dentro do WSL
#
# Uso:
#   ./montar-pacote.sh nomes                 # passo 1: lista as camadas publicadas no WFS
#   ./montar-pacote.sh base                  # passo 2: baixa e monta o pacote estadual
#   ./montar-pacote.sh regional -46 -20 -43 -18   # passo 3: pacote regional por retangulo

set -euo pipefail

WFS="https://geoserver.meioambiente.mg.gov.br/ows"
VERSAO_WFS="2.0.0"
SAIDA="${SAIDA:-./pacote}"
TOLERANCIA_M="${TOLERANCIA_M:-5}"          # simplificacao, em metros — DECLARADA no laudo
VERSAO_PACOTE="$(date +%Y.%m)"
TSV="$(dirname "$0")/camadas.tsv"

mkdir -p "$SAIDA"

passo_nomes() {
  echo "==> Consultando o catalogo de camadas do IDE-Sisema..."
  curl -sS "${WFS}?service=wfs&version=${VERSAO_WFS}&request=GetCapabilities" \
    -o "$SAIDA/capabilities.xml"

  echo "==> Camadas publicadas:"
  grep -oE '<(wfs:)?Name>[^<]+</(wfs:)?Name>' "$SAIDA/capabilities.xml" \
    | sed -E 's/<[^>]+>//g' | sort -u | tee "$SAIDA/camadas-disponiveis.txt"

  echo
  echo "==> Formatos de saida oferecidos:"
  grep -oiE 'application/json|shape-zip|geopackage|gml' "$SAIDA/capabilities.xml" | sort -u

  echo
  echo "Agora abra camadas.tsv e preencha a coluna 'tecnico' de cada linha com o nome"
  echo "correspondente da lista acima. Depois rode:  ./montar-pacote.sh base"
}

baixar_camada() {
  local tecnico="$1" destino="$2"
  curl -sS --get "$WFS" \
    --data-urlencode "service=wfs" \
    --data-urlencode "version=${VERSAO_WFS}" \
    --data-urlencode "request=GetFeature" \
    --data-urlencode "typeNames=${tecnico}" \
    --data-urlencode "outputFormat=application/json" \
    --data-urlencode "srsName=EPSG:4674" \
    -o "$destino"
}

montar() {
  local filtro_prioridade="$1"; shift
  local bbox_args=("$@")
  local gpkg="$SAIDA/mg-${filtro_prioridade}.gpkg"
  rm -f "$gpkg"

  echo "==> Montando $gpkg (tolerancia ${TOLERANCIA_M} m, EPSG:4674 SIRGAS 2000)"

  while IFS=$'\t' read -r prioridade tabela nome fonte tipo raio tecnico; do
    [[ "$prioridade" =~ ^# ]] && continue
    [[ -z "${tabela:-}" ]] && continue
    [[ "$prioridade" != "$filtro_prioridade" ]] && continue
    if [[ -z "${tecnico// }" ]]; then
      echo "  [pular] $nome — coluna 'tecnico' vazia em camadas.tsv"
      continue
    fi

    echo "  [baixar] $nome  <- $tecnico"
    local tmp="$SAIDA/_${tabela}.geojson"
    baixar_camada "$tecnico" "$tmp" || { echo "  [falhou] $nome"; continue; }

    if ! head -c 200 "$tmp" | grep -q FeatureCollection; then
      echo "  [erro] resposta inesperada para $nome — veja $tmp"
      continue
    fi

    # -simplify usa a unidade do SRC. Em graus, ~5 m equivale a 0.000045.
    local tol_graus
    tol_graus=$(python3 -c "print(${TOLERANCIA_M}/111320.0)")

    ogr2ogr -f GPKG "$gpkg" "$tmp" \
      -nln "$tabela" -nlt PROMOTE_TO_MULTI \
      -t_srs EPSG:4674 -simplify "$tol_graus" \
      -lco SPATIAL_INDEX=YES -lco GEOMETRY_NAME=geom -lco FID=fid \
      ${bbox_args[@]+"${bbox_args[@]}"} \
      -update -overwrite 2>/dev/null || { echo "  [erro] ogr2ogr falhou em $nome"; continue; }

    rm -f "$tmp"
    echo "  [ok] $tabela"
  done < "$TSV"

  gravar_manifesto "$gpkg" "$filtro_prioridade"
  echo
  echo "==> Pronto: $gpkg  ($(du -h "$gpkg" | cut -f1))"
  echo "    Copie para o celular em:  Android/data/br.com.oanalistaambiental.pericia/files/pacotes/mg-base.gpkg"
}

# O pacote guarda a propria proveniencia: o app le isso e imprime no laudo.
gravar_manifesto() {
  local gpkg="$1" filtro="$2"
  local hoje; hoje="$(date +%Y-%m-%d)"

  sqlite3 "$gpkg" <<SQL
CREATE TABLE IF NOT EXISTS pericia_pacote (chave TEXT PRIMARY KEY, valor TEXT);
INSERT OR REPLACE INTO pericia_pacote VALUES ('versao','${VERSAO_PACOTE}');
INSERT OR REPLACE INTO pericia_pacote VALUES ('gerado_em','${hoje}');
INSERT OR REPLACE INTO pericia_pacote VALUES ('origem','IDE-Sisema / GeoServer WFS');
INSERT OR REPLACE INTO pericia_pacote VALUES ('endpoint','${WFS}');

CREATE TABLE IF NOT EXISTS pericia_camadas (
  tabela TEXT PRIMARY KEY, nome TEXT, fonte TEXT, uuid TEXT,
  data_extracao TEXT, tolerancia_m REAL, tipo TEXT, raio_m REAL, prioridade INTEGER);
SQL

  while IFS=$'\t' read -r prioridade tabela nome fonte tipo raio tecnico; do
    [[ "$prioridade" =~ ^# ]] && continue
    [[ -z "${tabela:-}" ]] && continue
    [[ "$prioridade" != "$filtro" ]] && continue
    sqlite3 "$gpkg" "INSERT OR REPLACE INTO pericia_camadas VALUES (
      '${tabela}', '${nome//\'/\'\'}', '${fonte//\'/\'\'}', NULL,
      '${hoje}', ${TOLERANCIA_M}, '${tipo}', ${raio:-NULL}, ${prioridade});" 2>/dev/null || true
  done < "$TSV"
}

case "${1:-}" in
  nomes)    passo_nomes ;;
  base)     montar 1 ;;
  regional)
    shift
    [[ $# -eq 4 ]] || { echo "uso: $0 regional MINX MINY MAXX MAXY"; exit 1; }
    montar 2 -spat "$1" "$2" "$3" "$4" ;;
  *) sed -n '2,20p' "$0" ;;
esac
