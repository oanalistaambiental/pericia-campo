package br.com.oanalistaambiental.pericia.geo

import android.database.sqlite.SQLiteDatabase
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Leitura de um pacote GeoPackage (.gpkg) embarcado.
 *
 * GeoPackage e um arquivo SQLite, entao abre com a API nativa do Android: sem NDK, sem
 * biblioteca geoespacial nativa. O indice espacial R-tree ja vem pronto dentro do arquivo.
 */
class GeoPacote(private val arquivo: File) : AutoCloseable {

    private val db: SQLiteDatabase =
        SQLiteDatabase.openDatabase(arquivo.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    /** Metadados de proveniencia das camadas, gravados pelo script de montagem do pacote. */
    fun camadas(): List<CamadaInfo> {
        val lista = mutableListOf<CamadaInfo>()
        db.rawQuery(
            "SELECT tabela, nome, fonte, uuid, data_extracao, tolerancia_m, tipo, raio_m " +
                "FROM pericia_camadas ORDER BY prioridade", null
        ).use { c ->
            while (c.moveToNext()) {
                lista += CamadaInfo(
                    tabela = c.getString(0),
                    nome = c.getString(1),
                    fonte = c.getString(2),
                    uuid = if (c.isNull(3)) null else c.getString(3),
                    dataExtracao = c.getString(4),
                    toleranciaM = c.getDouble(5),
                    tipo = c.getString(6),
                    raioM = if (c.isNull(7)) null else c.getDouble(7)
                )
            }
        }
        return lista
    }

    fun versaoPacote(): String =
        db.rawQuery("SELECT valor FROM pericia_pacote WHERE chave='versao'", null).use {
            if (it.moveToFirst()) it.getString(0) else "desconhecida"
        }

    /**
     * Busca as feicoes de [tabela] cuja caixa envolvente cruza o retangulo informado.
     * O R-tree derruba milhares de feicoes para um punhado antes de desserializar geometria.
     */
    fun candidatas(tabela: String, minX: Double, minY: Double, maxX: Double, maxY: Double): List<Feicao> {
        val colunas = colunasDescritivas(tabela)
        val selecao = (listOf("f.fid", "f.geom") + colunas.map { "f.\"$it\"" }).joinToString(", ")
        val sql = """
            SELECT $selecao FROM "$tabela" f
            JOIN "rtree_${tabela}_geom" r ON f.fid = r.id
            WHERE r.maxx >= ? AND r.minx <= ? AND r.maxy >= ? AND r.miny <= ?
        """.trimIndent()
        val args = arrayOf(minX.toString(), maxX.toString(), minY.toString(), maxY.toString())

        val resultado = mutableListOf<Feicao>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                val blob = c.getBlob(1) ?: continue
                val geom = lerGeometria(blob) ?: continue
                val atributos = LinkedHashMap<String, String>()
                colunas.forEachIndexed { i, nome ->
                    val v = c.getString(2 + i)
                    if (!v.isNullOrBlank()) atributos[nome] = v
                }
                resultado += Feicao(c.getLong(0), geom, atributos)
            }
        }
        return resultado
    }

    /** Colunas de texto da tabela, exceto geometria e id: viram os atributos exibidos no laudo. */
    private fun colunasDescritivas(tabela: String): List<String> {
        val ignorar = setOf("fid", "geom", "geometry")
        val nomes = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(\"$tabela\")", null).use { c ->
            while (c.moveToNext()) {
                val nome = c.getString(1)
                if (nome.lowercase() !in ignorar) nomes += nome
            }
        }
        return nomes
    }

    /**
     * O BLOB do GeoPackage NAO e WKB puro: e um cabecalho GPKG seguido do WKB padrao.
     * Cabecalho = magic "GP" (2) + versao (1) + flags (1) + srs_id (4) + envelope opcional.
     * O tamanho do envelope depende dos bits 1..3 das flags. Pular isso errado produz
     * "geometria invalida" que parece defeito do dado e nao e.
     */
    private fun lerGeometria(blob: ByteArray): Geometry? {
        if (blob.size < 8 || blob[0] != 'G'.code.toByte() || blob[1] != 'P'.code.toByte()) return null
        val flags = blob[3].toInt()
        val indicadorEnvelope = (flags shr 1) and 0x07
        val tamanhoEnvelope = when (indicadorEnvelope) {
            0 -> 0
            1 -> 32
            2, 3 -> 48
            4 -> 64
            else -> return null
        }
        val vazio = ((flags shr 4) and 0x01) == 1
        if (vazio) return null
        val inicio = 8 + tamanhoEnvelope
        if (blob.size <= inicio) return null
        val wkb = blob.copyOfRange(inicio, blob.size)
        return try { WKBReader().read(wkb) } catch (e: Exception) { null }
    }

    /** SRS declarado no cabecalho da geometria (esperado 4674 = SIRGAS 2000). */
    fun srsDoBlob(blob: ByteArray): Int? {
        if (blob.size < 8) return null
        val ordem = if ((blob[3].toInt() and 0x01) == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        return ByteBuffer.wrap(blob, 4, 4).order(ordem).int
    }

    override fun close() = db.close()
}

data class CamadaInfo(
    val tabela: String,
    val nome: String,
    val fonte: String,
    val uuid: String?,
    val dataExtracao: String,
    val toleranciaM: Double,
    val tipo: String,          // "poligono" ou "ponto"
    val raioM: Double?         // usado quando tipo == "ponto" (ex.: raio de influencia de cavidade)
)

data class Feicao(
    val fid: Long,
    val geometria: Geometry,
    val atributos: Map<String, String>
)
