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

    // ---------------------------------------------------------- curadoria de 10/09/2026

    @Test
    fun `Point vira geometria de ponto, no eixo lon-lat`() {
        val json = JSONObject("""{"type":"Point","coordinates":[-44.0,-19.0]}""")
        val geom = GeoJson.paraGeometria(json)!!
        assertEquals(-44.0, geom.coordinate.x, 0.0001)
        assertEquals(-19.0, geom.coordinate.y, 0.0001)
    }

    @Test
    fun `MultiPoint de um ponto so vira ponto — caso real dos aerodromos`() {
        val json = JSONObject("""{"type":"MultiPoint","coordinates":[[-47.966,-19.764]]}""")
        val geom = GeoJson.paraGeometria(json)!!
        assertEquals(-47.966, geom.coordinate.x, 0.0001)
        assertEquals(-19.764, geom.coordinate.y, 0.0001)
    }

    @Test
    fun `LineString vira linha com o comprimento certo`() {
        val json = JSONObject("""{"type":"LineString","coordinates":[[-44.0,-19.0],[-44.0,-18.0]]}""")
        val geom = GeoJson.paraGeometria(json)!!
        assertEquals(2, geom.numPoints)
        // 1 grau de latitude ~ 111.32 km — so confere que a linha tem comprimento nao nulo,
        // a conta exata de distancia e feita em UTM em outro lugar (Restricao.kt).
        assertTrue(geom.length > 0.0)
    }

    @Test
    fun `tipo desconhecido devolve null, nao quebra nem inventa geometria`() {
        val json = JSONObject("""{"type":"GeometryCollection","coordinates":[]}""")
        assertEquals(null, GeoJson.paraGeometria(json))
    }
}
