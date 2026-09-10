package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.ImportadorCoordenada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportadorCoordenadaTest {

    @Test
    fun `KML com Point`() {
        val kml = """
            <?xml version="1.0"?>
            <kml><Document><Placemark>
              <name>Ponto de interesse</name>
              <Point><coordinates>-43.9345,-19.9167,0</coordinates></Point>
            </Placemark></Document></kml>
        """.trimIndent()
        val p = ImportadorCoordenada.extrairPrimeiroPonto("achado.kml", kml)!!
        assertEquals(-19.9167, p.lat, 0.0001)
        assertEquals(-43.9345, p.lon, 0.0001)
        assertEquals("Ponto de interesse", p.rotulo)
    }

    @Test
    fun `KML com Polygon pega o primeiro vertice, nao o centro`() {
        val kml = """
            <kml><Placemark>
              <Polygon><outerBoundaryIs><LinearRing><coordinates>
                -44.0,-20.0,0 -44.1,-20.0,0 -44.1,-20.1,0 -44.0,-20.0,0
              </coordinates></LinearRing></outerBoundaryIs></Polygon>
            </Placemark></kml>
        """.trimIndent()
        val p = ImportadorCoordenada.extrairPrimeiroPonto("area.kml", kml)!!
        assertEquals(-20.0, p.lat, 0.0001)
        assertEquals(-44.0, p.lon, 0.0001)
    }

    @Test
    fun `GPX com waypoint`() {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1"><wpt lat="-19.9167" lon="-43.9345">
              <name>Marco</name>
            </wpt></gpx>
        """.trimIndent()
        val p = ImportadorCoordenada.extrairPrimeiroPonto("pontos.gpx", gpx)!!
        assertEquals(-19.9167, p.lat, 0.0001)
        assertEquals(-43.9345, p.lon, 0.0001)
        assertEquals("Marco", p.rotulo)
    }

    @Test
    fun `GPX so com trkpt tambem funciona`() {
        val gpx = """<gpx><trk><trkseg><trkpt lat="-20.5" lon="-44.5"></trkpt></trkseg></trk></gpx>"""
        val p = ImportadorCoordenada.extrairPrimeiroPonto("trilha.gpx", gpx)!!
        assertEquals(-20.5, p.lat, 0.0001)
        assertEquals(-44.5, p.lon, 0.0001)
    }

    @Test
    fun `GeoJSON Point`() {
        val json = """{"type":"Point","coordinates":[-43.9345,-19.9167]}"""
        val p = ImportadorCoordenada.extrairPrimeiroPonto("ponto.geojson", json)!!
        assertEquals(-19.9167, p.lat, 0.0001)
        assertEquals(-43.9345, p.lon, 0.0001)
    }

    @Test
    fun `GeoJSON FeatureCollection com nome na propriedade`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"name":"Sede"},
               "geometry":{"type":"Point","coordinates":[-43.0,-19.0]}}
            ]}
        """.trimIndent()
        val p = ImportadorCoordenada.extrairPrimeiroPonto("area.geojson", json)!!
        assertEquals(-19.0, p.lat, 0.0001)
        assertEquals(-43.0, p.lon, 0.0001)
        assertEquals("Sede", p.rotulo)
    }

    @Test
    fun `GeoJSON Polygon desce ate o primeiro vertice`() {
        val json = """{"type":"Polygon","coordinates":[[[-44.0,-20.0],[-44.1,-20.0],[-44.1,-20.1],[-44.0,-20.0]]]}"""
        val p = ImportadorCoordenada.extrairPrimeiroPonto("area.geojson", json)!!
        assertEquals(-20.0, p.lat, 0.0001)
        assertEquals(-44.0, p.lon, 0.0001)
    }

    @Test
    fun `Shapefile nao e suportado, devolve null em vez de tentar adivinhar`() {
        assertNull(ImportadorCoordenada.extrairPrimeiroPonto("lote.shp", "qualquer coisa"))
    }

    @Test
    fun `formato nao reconhecido ou conteudo invalido devolve null`() {
        assertNull(ImportadorCoordenada.extrairPrimeiroPonto("arquivo.txt", "nada aqui"))
        assertNull(ImportadorCoordenada.extrairPrimeiroPonto("quebrado.geojson", "{ isso nao e json"))
        assertNull(ImportadorCoordenada.extrairPrimeiroPonto("vazio.kml", "<kml></kml>"))
    }

    @Test
    fun `extensao decide o formato, nao o conteudo`() {
        assertTrue(ImportadorCoordenada.extrairPrimeiroPonto("Ponto.KML",
            "<kml><Point><coordinates>-43.0,-19.0</coordinates></Point></kml>") != null)
    }
}
