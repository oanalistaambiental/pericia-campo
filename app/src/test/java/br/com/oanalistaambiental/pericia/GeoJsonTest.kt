package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.GeoJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory

/**
 * A leitura do GeoJSON do WFS ao vivo precisa concordar com o mesmo desenho que o GeoPackage
 * offline usa (x=lon, y=lat) — um erro de eixo aqui inverteria toda consulta online.
 */
class GeoJsonTest {

    private val gf = GeometryFactory()

    @Test
    fun `poligono simples vira Polygon com o ponto certo dentro`() {
        val json = JSONObject(
            """{"type":"Polygon","coordinates":[[[-44.0,-19.0],[-43.0,-19.0],[-43.0,-18.0],[-44.0,-18.0],[-44.0,-19.0]]]}"""
        )
        val geom = GeoJson.paraGeometria(json)!!
        assertTrue(geom.contains(gf.createPoint(Coordinate(-43.5, -18.5))))
        assertTrue(!geom.contains(gf.createPoint(Coordinate(0.0, 0.0))))
    }

    @Test
    fun `multipoligono junta as duas partes`() {
        val json = JSONObject(
            """{"type":"MultiPolygon","coordinates":[
                [[[-44.0,-19.0],[-43.5,-19.0],[-43.5,-18.5],[-44.0,-18.5],[-44.0,-19.0]]],
                [[[-42.0,-17.0],[-41.5,-17.0],[-41.5,-16.5],[-42.0,-16.5],[-42.0,-17.0]]]
            ]}"""
        )
        val geom = GeoJson.paraGeometria(json)!!
        assertTrue(geom.contains(gf.createPoint(Coordinate(-43.7, -18.7))))
        assertTrue(geom.contains(gf.createPoint(Coordinate(-41.7, -16.7))))
    }

    @Test
    fun `feature collection traz geometria e atributos`() {
        val json = JSONObject(
            """{"type":"FeatureCollection","features":[
                {"type":"Feature",
                 "geometry":{"type":"Polygon","coordinates":[[[-44.0,-19.0],[-43.0,-19.0],[-43.0,-18.0],[-44.0,-18.0],[-44.0,-19.0]]]},
                 "properties":{"nome":"Área de teste","area_km2":123.4}}
            ]}"""
        )
        val feicoes = GeoJson.paraFeicoes(json)
        assertEquals(1, feicoes.size)
        assertEquals("Área de teste", feicoes[0].second["nome"])
        assertTrue(feicoes[0].first.contains(gf.createPoint(Coordinate(-43.5, -18.5))))
    }

    @Test
    fun `feature collection vazia nao quebra`() {
        val json = JSONObject("""{"type":"FeatureCollection","features":[]}""")
        assertEquals(emptyList<Any>(), GeoJson.paraFeicoes(json))
    }
}
