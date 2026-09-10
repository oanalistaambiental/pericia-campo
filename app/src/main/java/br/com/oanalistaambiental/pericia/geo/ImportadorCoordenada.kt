package br.com.oanalistaambiental.pericia.geo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lê um arquivo Geo (KML, GPX ou GeoJSON) e tira dele UMA coordenada — a primeira que achar —
 * para "ir para uma coordenada" poder guiar até um ponto que veio de fora, sem digitar.
 *
 * Kotlin puro sobre o TEXTO do arquivo (nunca sobre uma Uri ou InputStream direto), pela mesma
 * razão de todo o resto do pacote `geo`: testável sem aparelho.
 *
 * NÃO lê Shapefile. .shp é um formato binário que vem espalhado em vários arquivos (.shp, .shx,
 * .dbf, no mínimo) — não dá para abrir "um arquivo" e ler ponto nenhum sem os outros três junto,
 * e o app não tem leitor de Shapefile pronto. Dizer isso na tela é mais honesto que aceitar o
 * arquivo e falhar calado.
 */
object ImportadorCoordenada {

    data class PontoImportado(val lat: Double, val lon: Double, val rotulo: String)

    /** [nomeArquivo] só decide o formato pela extensão — o conteúdo é sempre texto. */
    fun extrairPrimeiroPonto(nomeArquivo: String, conteudo: String): PontoImportado? {
        val ext = nomeArquivo.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kml" -> deKml(conteudo)
            "gpx" -> deGpx(conteudo)
            "geojson", "json" -> deGeoJson(conteudo)
            "kmz" -> null // zip — precisaria descompactar o doc.kml de dentro, não é so texto
            "shp", "shx", "dbf" -> null // binario, formato de varios arquivos: ver nota da classe
            else -> null
        }
    }

    // ------------------------------------------------------------------ KML

    /**
     * Primeiro `<coordinates>` do documento, de qualquer geometria (Point, LineString ou
     * Polygon) — pega só o PRIMEIRO par lon,lat dentro dele, então uma linha ou polígono vira o
     * ponto onde começa, não o centro.
     */
    private fun deKml(conteudo: String): PontoImportado? {
        val nome = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
            .find(conteudo)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val coordsTexto = Regex("<coordinates>(.*?)</coordinates>", RegexOption.DOT_MATCHES_ALL)
            .find(conteudo)?.groupValues?.get(1) ?: return null
        val primeiroPar = coordsTexto.trim().split(Regex("\\s+")).firstOrNull() ?: return null
        val partes = primeiroPar.split(',')
        if (partes.size < 2) return null
        val lon = partes[0].toDoubleOrNull() ?: return null
        val lat = partes[1].toDoubleOrNull() ?: return null
        return PontoImportado(lat, lon, nome ?: "Ponto importado (KML)")
    }

    // ------------------------------------------------------------------ GPX

    /** Primeiro `<wpt>`; na ausência, primeiro `<rtept>`; na ausência, primeiro `<trkpt>`. */
    private fun deGpx(conteudo: String): PontoImportado? {
        for (tag in listOf("wpt", "rtept", "trkpt")) {
            val m = Regex("<$tag\\s+([^>]*)>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(conteudo)
                ?: Regex("<$tag\\s+([^>]*)/>").find(conteudo)
            val atributos = m?.groupValues?.get(1) ?: continue
            val lat = Regex("""lat\s*=\s*"([-0-9.]+)"""").find(atributos)?.groupValues?.get(1)?.toDoubleOrNull()
            val lon = Regex("""lon\s*=\s*"([-0-9.]+)"""").find(atributos)?.groupValues?.get(1)?.toDoubleOrNull()
            if (lat == null || lon == null) continue
            val corpo = m.groupValues.getOrNull(2) ?: ""
            val nome = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
                .find(corpo)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            return PontoImportado(lat, lon, nome ?: "Ponto importado (GPX)")
        }
        return null
    }

    // ------------------------------------------------------------------ GeoJSON

    /**
     * Primeira Feature (ou geometria solta, sem FeatureCollection) com coordenadas — Point pega
     * direto; Polygon/LineString/MultiPolygon descem até o primeiro par de números da estrutura
     * aninhada, que é sempre o primeiro vértice.
     */
    private fun deGeoJson(conteudo: String): PontoImportado? {
        val raiz = runCatching { JSONObject(conteudo) }.getOrNull() ?: return null
        // Par (geometria, propriedades) — o nome vem das propriedades da MESMA feature, nao da
        // raiz do documento (numa FeatureCollection, "properties" e por feature).
        val candidatos: List<Pair<JSONObject, JSONObject?>> = when (raiz.optString("type")) {
            "FeatureCollection" -> {
                val feats = raiz.optJSONArray("features") ?: JSONArray()
                (0 until feats.length()).mapNotNull { i ->
                    val f = feats.optJSONObject(i) ?: return@mapNotNull null
                    val g = f.optJSONObject("geometry") ?: return@mapNotNull null
                    g to f.optJSONObject("properties")
                }
            }
            "Feature" -> listOfNotNull(raiz.optJSONObject("geometry")?.let { it to raiz.optJSONObject("properties") })
            else -> listOf(raiz to null)
        }
        val (geom, props) = candidatos.firstOrNull() ?: return null
        val nome = props?.let { p ->
            p.optString("name", "").ifBlank { null } ?: p.optString("nome", "").ifBlank { null }
        }
        val coords = geom.optJSONArray("coordinates") ?: return null
        val par = primeiroPar(coords) ?: return null
        return PontoImportado(par.second, par.first, nome ?: "Ponto importado (GeoJSON)")
    }

    /** Desce recursivamente até achar o primeiro par [lon, lat] — nao importa quantos níveis. */
    private fun primeiroPar(arr: JSONArray): Pair<Double, Double>? {
        if (arr.length() >= 2 && arr.opt(0) is Number && arr.opt(1) is Number) {
            return arr.getDouble(0) to arr.getDouble(1)
        }
        for (i in 0 until arr.length()) {
            val filho = arr.optJSONArray(i) ?: continue
            primeiroPar(filho)?.let { return it }
        }
        return null
    }
}
