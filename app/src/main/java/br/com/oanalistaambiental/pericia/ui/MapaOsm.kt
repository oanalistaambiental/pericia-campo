package br.com.oanalistaambiental.pericia.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Color
import br.com.oanalistaambiental.pericia.geo.Restricao
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

/**
 * Configuração única do osmdroid, feita na primeira vez que um mapa é pedido. Aponta o cache de
 * blocos (tiles) para a pasta própria do app — evita pedir permissão de armazenamento extra, que
 * o osmdroid pediria por padrão num Android antigo. O "user agent" é exigido pela política de
 * uso dos servidores públicos de tile da OpenStreetMap: sem ele, as requisições podem ser
 * bloqueadas por vir sem identificação nenhuma.
 */
private fun configurarOsmdroidSeNecessario(contexto: Context) {
    val cfg = Configuration.getInstance()
    if (cfg.userAgentValue != contexto.packageName) {
        cfg.userAgentValue = contexto.packageName
        val base = contexto.getExternalFilesDir("osmdroid") ?: contexto.filesDir
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
    }
}

private fun poligonoRestricao(pontos: List<GeoPoint>): Polygon = Polygon().apply {
    points = pontos
    fillColor = Color.argb(60, 220, 38, 38)
    strokeColor = Color.argb(200, 220, 38, 38)
    strokeWidth = 3f
}

/**
 * Mapinha de referência — OpenStreetMap, de uso público e sem chave de API (ao contrário do
 * Google Maps, que exige conta Google Cloud com faturamento habilitado). Só para dar noção do
 * lugar: não é ferramenta de medição, e não substitui a coordenada em texto que já aparece em
 * toda tela que usa GNSS.
 *
 * [contorno] desenha o polígono de uma camada de restrição (lat, lon — ver
 * `Restricao.contornoLatLon`); [raioCirculoM] desenha o círculo de influência de uma camada de
 * ponto (`Restricao.raioCirculoM`). No máximo um dos dois costuma vir preenchido por vez.
 */
@Composable
fun MapaReferencia(
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    zoom: Double = 15.0,
    contorno: List<DoubleArray>? = null,
    raioCirculoM: Double? = null
) {
    AndroidView(
        modifier = modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)),
        factory = { ctx ->
            configurarOsmdroidSeNecessario(ctx)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(zoom)
                val ponto = GeoPoint(lat, lon)
                controller.setCenter(ponto)
                overlays.add(Marker(this).apply { position = ponto })
                contorno?.let { overlays.add(poligonoRestricao(it.map { p -> GeoPoint(p[0], p[1]) })) }
                raioCirculoM?.let { overlays.add(poligonoRestricao(Polygon.pointsAsCircle(ponto, it))) }
            }
        },
        update = { mapa ->
            val ponto = GeoPoint(lat, lon)
            mapa.controller.setCenter(ponto)
            mapa.overlays.clear()
            mapa.overlays.add(Marker(mapa).apply { position = ponto })
            contorno?.let { mapa.overlays.add(poligonoRestricao(it.map { p -> GeoPoint(p[0], p[1]) })) }
            raioCirculoM?.let { mapa.overlays.add(poligonoRestricao(Polygon.pointsAsCircle(ponto, it))) }
            mapa.invalidate()
        }
    )
}
