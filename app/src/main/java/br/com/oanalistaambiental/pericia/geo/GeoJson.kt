package br.com.oanalistaambiental.pericia.geo

import org.json.JSONArray
import org.json.JSONObject
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon

/**
 * GeoJSON -> JTS, so o suficiente para as camadas de restricao do IDE-Sisema (Polygon e
 * MultiPolygon). Kotlin puro, testavel sem aparelho nem rede — usado por [ConsultaOnline] para
 * ler a resposta do WFS ao vivo, do mesmo jeito que [GeoPacote] le o GeoPackage offline.
 */
object GeoJson {
    private val gf = GeometryFactory()

    fun paraGeometria(geometryJson: JSONObject): Geometry? {
        val coords = geometryJson.optJSONArray("coordinates") ?: return null
        return when (geometryJson.optString("type")) {
            "Polygon" -> poligono(coords)
            "MultiPolygon" -> {
                val polys = (0 until coords.length()).map { poligono(coords.getJSONArray(it)) }
                gf.createMultiPolygon(polys.toTypedArray())
            }
            else -> null
        }
    }

    private fun poligono(aneis: JSONArray): Polygon {
        val shell = anel(aneis.getJSONArray(0))
        val buracos = (1 until aneis.length()).map { anel(aneis.getJSONArray(it)) }
        return gf.createPolygon(shell, buracos.toTypedArray())
    }

    private fun anel(pontos: JSONArray): LinearRing {
        val coords = (0 until pontos.length()).map {
            val p = pontos.getJSONArray(it)
            Coordinate(p.getDouble(0), p.getDouble(1))
        }
        return gf.createLinearRing(coords.toTypedArray())
    }

    /** Uma FeatureCollection inteira: cada feicao vira geometria + atributos de texto. */
    fun paraFeicoes(featureCollectionJson: JSONObject): List<Pair<Geometry, Map<String, String>>> {
        val feats = featureCollectionJson.optJSONArray("features") ?: return emptyList()
        return (0 until feats.length()).mapNotNull { i ->
            val f = feats.getJSONObject(i)
            val geomJson = f.optJSONObject("geometry") ?: return@mapNotNull null
            val geom = paraGeometria(geomJson) ?: return@mapNotNull null
            val props = f.optJSONObject("properties") ?: JSONObject()
            val atributos = props.keys().asSequence()
                .associateWith { k -> props.opt(k)?.toString() ?: "" }
            geom to atributos
        }
    }
}
