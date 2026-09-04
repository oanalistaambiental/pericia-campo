package br.com.oanalistaambiental.pericia.dados

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Persistencia local em SQLite puro, sem geracao de codigo.
 *
 * Escolha deliberada: menos pecas moveis significa menos motivo para a primeira compilacao
 * falhar, e a mesma API ja e usada para ler o GeoPackage das camadas.
 */
class Banco(context: Context) : SQLiteOpenHelper(context, "pericia.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sessao (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titulo TEXT NOT NULL,
                processo TEXT,
                criada_em INTEGER NOT NULL,
                fechada_em INTEGER,
                raiz_merkle TEXT,
                carimbo_tempo TEXT
            )""")
        db.execSQL("""
            CREATE TABLE foto (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessao_id INTEGER NOT NULL,
                arquivo_original TEXT NOT NULL,
                arquivo_legenda TEXT,
                sha256 TEXT NOT NULL,
                lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL NOT NULL,
                altitude_m REAL, azimute REAL, inclinacao REAL,
                instante INTEGER NOT NULL,
                tipo_ocorrencia TEXT, observacao TEXT,
                endereco_pendente INTEGER NOT NULL DEFAULT 1, endereco TEXT,
                FOREIGN KEY(sessao_id) REFERENCES sessao(id)
            )""")
        db.execSQL("""
            CREATE TABLE restricao (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                foto_id INTEGER NOT NULL,
                camada TEXT NOT NULL, fonte TEXT NOT NULL,
                situacao TEXT NOT NULL, distancia_m REAL NOT NULL,
                atributos TEXT, pacote_versao TEXT, uuid_metadado TEXT,
                data_extracao TEXT, tolerancia_m REAL,
                FOREIGN KEY(foto_id) REFERENCES foto(id)
            )""")
        db.execSQL("CREATE INDEX idx_foto_sessao ON foto(sessao_id)")
        db.execSQL("CREATE INDEX idx_restricao_foto ON restricao(foto_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { /* v1 */ }

    fun criarSessao(titulo: String, processo: String?): Long =
        writableDatabase.insert("sessao", null, ContentValues().apply {
            put("titulo", titulo); put("processo", processo)
            put("criada_em", System.currentTimeMillis())
        })

    fun sessoes(): List<Sessao> {
        val out = mutableListOf<Sessao>()
        readableDatabase.rawQuery("""
            SELECT s.id, s.titulo, s.processo, s.criada_em, s.fechada_em, s.raiz_merkle,
                   s.carimbo_tempo, (SELECT COUNT(*) FROM foto f WHERE f.sessao_id = s.id)
            FROM sessao s ORDER BY s.criada_em DESC""", null).use { c ->
            while (c.moveToNext()) out += Sessao(
                c.getLong(0), c.getString(1), c.getString(2), c.getLong(3),
                if (c.isNull(4)) null else c.getLong(4),
                c.getString(5), c.getString(6), c.getInt(7)
            )
        }
        return out
    }

    fun inserirFoto(f: Foto): Long =
        writableDatabase.insert("foto", null, ContentValues().apply {
            put("sessao_id", f.sessaoId)
            put("arquivo_original", f.arquivoOriginal)
            put("arquivo_legenda", f.arquivoComLegenda)
            put("sha256", f.sha256)
            put("lat", f.lat); put("lon", f.lon); put("precisao_m", f.precisaoM)
            put("altitude_m", f.altitudeM); put("azimute", f.azimuteGraus)
            put("inclinacao", f.inclinacaoGraus); put("instante", f.instante)
            put("tipo_ocorrencia", f.tipoOcorrencia); put("observacao", f.observacao)
            put("endereco_pendente", if (f.enderecoPendente) 1 else 0)
        })

    fun fotosDaSessao(sessaoId: Long): List<Foto> {
        val out = mutableListOf<Foto>()
        readableDatabase.rawQuery(
            "SELECT id, sessao_id, arquivo_original, arquivo_legenda, sha256, lat, lon, precisao_m," +
                " altitude_m, azimute, inclinacao, instante, tipo_ocorrencia, observacao," +
                " endereco_pendente, endereco FROM foto WHERE sessao_id=? ORDER BY instante",
            arrayOf(sessaoId.toString())
        ).use { c ->
            while (c.moveToNext()) out += Foto(
                c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                c.getDouble(5), c.getDouble(6), c.getFloat(7),
                if (c.isNull(8)) null else c.getDouble(8),
                if (c.isNull(9)) null else c.getFloat(9),
                if (c.isNull(10)) null else c.getFloat(10),
                c.getLong(11), c.getString(12), c.getString(13),
                c.getInt(14) == 1, c.getString(15)
            )
        }
        return out
    }

    fun inserirRestricao(r: RegistroRestricao) {
        writableDatabase.insert("restricao", null, ContentValues().apply {
            put("foto_id", r.fotoId); put("camada", r.camada); put("fonte", r.fonte)
            put("situacao", r.situacao); put("distancia_m", r.distanciaM)
            put("atributos", r.atributos); put("pacote_versao", r.pacoteVersao)
            put("uuid_metadado", r.uuidMetadado); put("data_extracao", r.dataExtracao)
            put("tolerancia_m", r.toleranciaM)
        })
    }

    fun restricoesDaFoto(fotoId: Long): List<RegistroRestricao> {
        val out = mutableListOf<RegistroRestricao>()
        readableDatabase.rawQuery(
            "SELECT id, foto_id, camada, fonte, situacao, distancia_m, atributos, pacote_versao," +
                " uuid_metadado, data_extracao, tolerancia_m FROM restricao WHERE foto_id=?",
            arrayOf(fotoId.toString())
        ).use { c ->
            while (c.moveToNext()) out += RegistroRestricao(
                c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                c.getDouble(5), c.getString(6) ?: "", c.getString(7) ?: "", c.getString(8),
                c.getString(9) ?: "", c.getDouble(10)
            )
        }
        return out
    }

    fun fecharSessao(sessaoId: Long, raizMerkle: String) {
        writableDatabase.update("sessao", ContentValues().apply {
            put("fechada_em", System.currentTimeMillis())
            put("raiz_merkle", raizMerkle)
        }, "id=?", arrayOf(sessaoId.toString()))
    }
}
