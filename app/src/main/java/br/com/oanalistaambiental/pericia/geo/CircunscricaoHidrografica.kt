package br.com.oanalistaambiental.pericia.geo

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.io.File

/**
 * Em qual Circunscricao Hidrografica (CH) um ponto cai.
 *
 * Dado REAL do IGAM/SEMAD (GEIRH), gerado por `ferramentas/gerar-bacias.py` a partir do WFS
 * publico do IDE-Sisema — ao contrario do pacote de exemplo, este nao e ficticio. Fica FORA do
 * pipeline de [Restricao]/[Situacao] de proposito: estar dentro de uma CH nao e indicio de
 * restricao nenhuma, e so contexto util antes de uma conversa de outorga com o IGAM.
 */
object CircunscricaoHidrografica {

    data class Info(
        val sigla: String,
        val nome: String,
        val temComiteDeBacia: Boolean,
        val situacaoComite: String?,
        val areaKm2: Double?,
        val decreto: String?
    )

    private val gf = GeometryFactory()

    /** Null quando o ponto nao cai em nenhuma CH mapeada (fora de MG, ou perto da divisa). */
    fun localizar(pacote: File, lat: Double, lon: Double): Info? {
        GeoPacote(pacote).use { g ->
            val ponto = gf.createPoint(Coordinate(lon, lat))
            val candidatas = g.candidatas(
                "circunscricoes_hidrograficas", lon - 0.01, lat - 0.01, lon + 0.01, lat + 0.01
            )
            val achado = candidatas.firstOrNull { it.geometria.contains(ponto) } ?: return null
            val a = achado.atributos
            return Info(
                sigla = a["sigla"] ?: "?",
                nome = a["nome"] ?: "?",
                temComiteDeBacia = a["cbh"]?.equals("Sim", ignoreCase = true) == true,
                situacaoComite = a["situ_cbh"],
                areaKm2 = a["area_km2"]?.toDoubleOrNull(),
                decreto = a["decreto"]
            )
        }
    }
}
