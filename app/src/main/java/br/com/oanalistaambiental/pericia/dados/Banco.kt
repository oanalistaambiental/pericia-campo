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
class Banco(context: Context) : SQLiteOpenHelper(context, "pericia.db", null, 9) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sessao (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titulo TEXT NOT NULL,
                processo TEXT,
                criada_em INTEGER NOT NULL,
                fechada_em INTEGER,
                raiz_merkle TEXT,
                carimbo_tempo TEXT,
                carimbo_instante INTEGER,
                carimbo_autoridade TEXT,
                carimbo_credenciado INTEGER NOT NULL DEFAULT 0
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
                idade_fix_s INTEGER,
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
        db.execSQL("""
            CREATE TABLE ponto (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                instante INTEGER NOT NULL
            )""")
        db.execSQL("""
            CREATE TABLE caminhamento_ponto (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessao_id INTEGER NOT NULL,
                lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL NOT NULL,
                instante INTEGER NOT NULL,
                FOREIGN KEY(sessao_id) REFERENCES sessao(id)
            )""")
        db.execSQL("""
            CREATE TABLE audio (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessao_id INTEGER NOT NULL,
                arquivo TEXT NOT NULL,
                duracao_s INTEGER NOT NULL,
                instante_inicio INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                FOREIGN KEY(sessao_id) REFERENCES sessao(id)
            )""")
        db.execSQL("""
            CREATE TABLE ocorrencia_ambiental (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                instante INTEGER NOT NULL,
                descricao TEXT,
                transcricao_audio TEXT,
                foto_arquivo TEXT,
                foto_sha256 TEXT
            )""")
        db.execSQL("""
            CREATE TABLE ocorrencia_foto (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ocorrencia_id INTEGER NOT NULL,
                arquivo TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                FOREIGN KEY(ocorrencia_id) REFERENCES ocorrencia_ambiental(id)
            )""")
        db.execSQL("""
            CREATE TABLE registro_captacao (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                instante INTEGER NOT NULL,
                tipo_captacao TEXT NOT NULL,
                vazao_ou_volume REAL,
                unidade TEXT NOT NULL,
                com_bomba INTEGER,
                foto_arquivo TEXT,
                foto_sha256 TEXT,
                classificacao TEXT NOT NULL,
                base_legal TEXT NOT NULL
            )""")
        db.execSQL("""
            CREATE TABLE ficha_vistoria (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                modelo_id TEXT NOT NULL, modelo_nome TEXT NOT NULL,
                lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                instante INTEGER NOT NULL,
                respostas_json TEXT NOT NULL
            )""")
        db.execSQL("CREATE INDEX idx_foto_sessao ON foto(sessao_id)")
        db.execSQL("CREATE INDEX idx_restricao_foto ON restricao(foto_id)")
        db.execSQL("CREATE INDEX idx_caminhamento_sessao ON caminhamento_ponto(sessao_id)")
        db.execSQL("CREATE INDEX idx_audio_sessao ON audio(sessao_id)")
    }

    /**
     * Migracao aditiva, nunca destrutiva: a prova ja gravada e intocavel. Coluna nova entra
     * nula para as fotos antigas, e o laudo trata null como "idade nao registrada".
     */
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) db.execSQL("ALTER TABLE foto ADD COLUMN idade_fix_s INTEGER")
        if (old < 3) {
            db.execSQL("ALTER TABLE sessao ADD COLUMN carimbo_instante INTEGER")
            db.execSQL("ALTER TABLE sessao ADD COLUMN carimbo_autoridade TEXT")
            db.execSQL("ALTER TABLE sessao ADD COLUMN carimbo_credenciado INTEGER NOT NULL DEFAULT 0")
        }
        if (old < 4) {
            db.execSQL("""
                CREATE TABLE ponto (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome TEXT NOT NULL,
                    lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                    instante INTEGER NOT NULL
                )""")
        }
        if (old < 5) {
            db.execSQL("""
                CREATE TABLE caminhamento_ponto (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sessao_id INTEGER NOT NULL,
                    lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL NOT NULL,
                    instante INTEGER NOT NULL,
                    FOREIGN KEY(sessao_id) REFERENCES sessao(id)
                )""")
            db.execSQL("""
                CREATE TABLE audio (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sessao_id INTEGER NOT NULL,
                    arquivo TEXT NOT NULL,
                    duracao_s INTEGER NOT NULL,
                    instante_inicio INTEGER NOT NULL,
                    sha256 TEXT NOT NULL,
                    FOREIGN KEY(sessao_id) REFERENCES sessao(id)
                )""")
            db.execSQL("CREATE INDEX idx_caminhamento_sessao ON caminhamento_ponto(sessao_id)")
            db.execSQL("CREATE INDEX idx_audio_sessao ON audio(sessao_id)")
        }
        if (old < 6) {
            db.execSQL("""
                CREATE TABLE ocorrencia_ambiental (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                    instante INTEGER NOT NULL,
                    descricao TEXT,
                    transcricao_audio TEXT,
                    foto_arquivo TEXT,
                    foto_sha256 TEXT
                )""")
        }
        if (old < 7) {
            db.execSQL("""
                CREATE TABLE ocorrencia_foto (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ocorrencia_id INTEGER NOT NULL,
                    arquivo TEXT NOT NULL,
                    sha256 TEXT NOT NULL,
                    FOREIGN KEY(ocorrencia_id) REFERENCES ocorrencia_ambiental(id)
                )""")
            // Ocorrencias gravadas antes de existir mais de uma foto tinham a foto direto nas
            // colunas foto_arquivo/foto_sha256 (ainda no esquema, so nao usadas mais por codigo
            // novo) — migra para a tabela nova em vez de deixar essa foto orfa.
            db.execSQL("""
                INSERT INTO ocorrencia_foto (ocorrencia_id, arquivo, sha256)
                SELECT id, foto_arquivo, foto_sha256 FROM ocorrencia_ambiental
                WHERE foto_arquivo IS NOT NULL AND foto_sha256 IS NOT NULL
            """)
        }
        if (old < 8) {
            db.execSQL("""
                CREATE TABLE registro_captacao (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                    instante INTEGER NOT NULL,
                    tipo_captacao TEXT NOT NULL,
                    vazao_ou_volume REAL,
                    unidade TEXT NOT NULL,
                    com_bomba INTEGER,
                    foto_arquivo TEXT,
                    foto_sha256 TEXT,
                    classificacao TEXT NOT NULL,
                    base_legal TEXT NOT NULL
                )""")
        }
        if (old < 9) {
            db.execSQL("""
                CREATE TABLE ficha_vistoria (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    modelo_id TEXT NOT NULL, modelo_nome TEXT NOT NULL,
                    lat REAL NOT NULL, lon REAL NOT NULL, precisao_m REAL,
                    instante INTEGER NOT NULL,
                    respostas_json TEXT NOT NULL
                )""")
        }
    }

    fun criarSessao(titulo: String, processo: String?): Long =
        writableDatabase.insert("sessao", null, ContentValues().apply {
            put("titulo", titulo); put("processo", processo)
            put("criada_em", System.currentTimeMillis())
        })

    fun sessoes(): List<Sessao> {
        val out = mutableListOf<Sessao>()
        readableDatabase.rawQuery("""
            SELECT $COLUNAS_SESSAO
            FROM sessao s ORDER BY s.criada_em DESC""", null).use { c ->
            while (c.moveToNext()) out += lerSessao(c)
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
            put("idade_fix_s", f.idadeFixSegundos)
            put("tipo_ocorrencia", f.tipoOcorrencia); put("observacao", f.observacao)
            put("endereco_pendente", if (f.enderecoPendente) 1 else 0)
        })

    fun fotosDaSessao(sessaoId: Long): List<Foto> {
        val out = mutableListOf<Foto>()
        readableDatabase.rawQuery(
            // ORDER BY instante, id — e nao so instante. A ordem das folhas da arvore de
            // Merkle sai daqui. Se o Android sincronizar a hora pela rede no meio da vistoria
            // e recuar o relogio, ou se duas fotos cairem no mesmo milissegundo, o SQLite nao
            // garante desempate: a mesma sessao produziria raizes diferentes em leituras
            // diferentes, sem nenhum arquivo ter mudado. O id e monotonico e resolve.
            "SELECT $COLUNAS_FOTO FROM foto WHERE sessao_id=? ORDER BY instante, id",
            arrayOf(sessaoId.toString())
        ).use { c -> while (c.moveToNext()) out += lerFoto(c) }
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

    // ---- correcoes e complementos ----

    /** BUG corrigido: a copia com legenda era gerada e nunca gravada no registro. */
    fun atualizarLegenda(fotoId: Long, caminho: String) {
        writableDatabase.update("foto", ContentValues().apply { put("arquivo_legenda", caminho) },
            "id=?", arrayOf(fotoId.toString()))
    }

    fun atualizarEndereco(fotoId: Long, endereco: String) {
        writableDatabase.update("foto", ContentValues().apply {
            put("endereco", endereco); put("endereco_pendente", 0)
        }, "id=?", arrayOf(fotoId.toString()))
    }

    /**
     * Grava o carimbo INTEIRO de uma vez: token, instante declarado, autoridade e se ela foi
     * marcada como credenciada. Gravar so o token deixaria o laudo com um selo sem data e sem
     * origem — que e quase a mesma coisa que nao ter selo.
     */
    fun atualizarCarimbo(
        sessaoId: Long,
        token: String,
        instante: Long,
        autoridade: String,
        credenciada: Boolean
    ) {
        writableDatabase.update("sessao", ContentValues().apply {
            put("carimbo_tempo", token)
            put("carimbo_instante", instante)
            put("carimbo_autoridade", autoridade)
            put("carimbo_credenciado", if (credenciada) 1 else 0)
        }, "id=?", arrayOf(sessaoId.toString()))
    }

    /** Fotos sem endereco resolvido — a fila que completa quando houver conexao. */
    fun fotosComEnderecoPendente(limite: Int = 50): List<Foto> {
        val out = mutableListOf<Foto>()
        readableDatabase.rawQuery(
            "SELECT $COLUNAS_FOTO FROM foto WHERE endereco_pendente=1 AND (lat<>0 OR lon<>0)" +
                " ORDER BY instante DESC LIMIT ?", arrayOf(limite.toString())
        ).use { c -> while (c.moveToNext()) out += lerFoto(c) }
        return out
    }

    // ---- pontos avulsos ----

    fun salvarPonto(p: PontoSalvo): Long =
        writableDatabase.insert("ponto", null, ContentValues().apply {
            put("nome", p.nome); put("lat", p.lat); put("lon", p.lon)
            put("precisao_m", p.precisaoM); put("instante", p.instante)
        })

    fun pontosSalvos(): List<PontoSalvo> {
        val out = mutableListOf<PontoSalvo>()
        readableDatabase.rawQuery(
            "SELECT id, nome, lat, lon, precisao_m, instante FROM ponto ORDER BY instante DESC", null
        ).use { c ->
            while (c.moveToNext()) out += PontoSalvo(
                id = c.getLong(0), nome = c.getString(1), lat = c.getDouble(2), lon = c.getDouble(3),
                precisaoM = if (c.isNull(4)) null else c.getFloat(4), instante = c.getLong(5)
            )
        }
        return out
    }

    fun excluirPonto(id: Long) {
        writableDatabase.delete("ponto", "id=?", arrayOf(id.toString()))
    }

    // ---- modo vistoria: caminhamento georreferenciado ----

    fun inserirPontoCaminhamento(p: PontoCaminhamento): Long =
        writableDatabase.insert("caminhamento_ponto", null, ContentValues().apply {
            put("sessao_id", p.sessaoId); put("lat", p.lat); put("lon", p.lon)
            put("precisao_m", p.precisaoM); put("instante", p.instante)
        })

    fun pontosCaminhamento(sessaoId: Long): List<PontoCaminhamento> {
        val out = mutableListOf<PontoCaminhamento>()
        readableDatabase.rawQuery(
            "SELECT id, sessao_id, lat, lon, precisao_m, instante FROM caminhamento_ponto" +
                " WHERE sessao_id=? ORDER BY instante, id",
            arrayOf(sessaoId.toString())
        ).use { c ->
            while (c.moveToNext()) out += PontoCaminhamento(
                id = c.getLong(0), sessaoId = c.getLong(1), lat = c.getDouble(2), lon = c.getDouble(3),
                precisaoM = c.getFloat(4), instante = c.getLong(5)
            )
        }
        return out
    }

    // ---- modo vistoria: audio ----

    fun inserirAudio(a: AudioGravado): Long =
        writableDatabase.insert("audio", null, ContentValues().apply {
            put("sessao_id", a.sessaoId); put("arquivo", a.arquivo)
            put("duracao_s", a.duracaoSegundos); put("instante_inicio", a.instanteInicio)
            put("sha256", a.sha256)
        })

    fun audiosDaSessao(sessaoId: Long): List<AudioGravado> {
        val out = mutableListOf<AudioGravado>()
        readableDatabase.rawQuery(
            "SELECT id, sessao_id, arquivo, duracao_s, instante_inicio, sha256 FROM audio" +
                " WHERE sessao_id=? ORDER BY instante_inicio",
            arrayOf(sessaoId.toString())
        ).use { c ->
            while (c.moveToNext()) out += AudioGravado(
                id = c.getLong(0), sessaoId = c.getLong(1), arquivo = c.getString(2),
                duracaoSegundos = c.getInt(3), instanteInicio = c.getLong(4), sha256 = c.getString(5)
            )
        }
        return out
    }

    fun excluirAudio(id: Long) {
        writableDatabase.delete("audio", "id=?", arrayOf(id.toString()))
    }

    // ---- ocorrencia ambiental ----

    /** Grava a ocorrencia e as fotos dela (0 a 5) numa transacao so — ou entram as duas coisas, ou nenhuma. */
    fun inserirOcorrencia(o: OcorrenciaAmbiental): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert("ocorrencia_ambiental", null, ContentValues().apply {
                put("lat", o.lat); put("lon", o.lon); put("precisao_m", o.precisaoM)
                put("instante", o.instante); put("descricao", o.descricao)
                put("transcricao_audio", o.transcricaoAudio)
            })
            o.fotos.forEach { f ->
                db.insert("ocorrencia_foto", null, ContentValues().apply {
                    put("ocorrencia_id", id); put("arquivo", f.arquivo); put("sha256", f.sha256)
                })
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atualiza descrição/transcrição e ACRESCENTA fotos novas — nunca mexe em lat/lon/instante:
     * isso é o que foi observado e quando, não é editável depois. Fotos removidas se excluem à
     * parte, uma a uma, por [excluirFotoOcorrencia].
     */
    fun atualizarOcorrencia(id: Long, descricao: String?, transcricaoAudio: String?, fotosNovas: List<FotoOcorrencia>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("ocorrencia_ambiental", ContentValues().apply {
                put("descricao", descricao); put("transcricao_audio", transcricaoAudio)
            }, "id=?", arrayOf(id.toString()))
            fotosNovas.forEach { f ->
                db.insert("ocorrencia_foto", null, ContentValues().apply {
                    put("ocorrencia_id", id); put("arquivo", f.arquivo); put("sha256", f.sha256)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun excluirFotoOcorrencia(id: Long) {
        writableDatabase.delete("ocorrencia_foto", "id=?", arrayOf(id.toString()))
    }

    fun ocorrencias(): List<OcorrenciaAmbiental> {
        val fotosPorOcorrencia = mutableMapOf<Long, MutableList<FotoOcorrencia>>()
        readableDatabase.rawQuery(
            "SELECT id, ocorrencia_id, arquivo, sha256 FROM ocorrencia_foto ORDER BY id", null
        ).use { c ->
            while (c.moveToNext()) {
                val ocorrenciaId = c.getLong(1)
                fotosPorOcorrencia.getOrPut(ocorrenciaId) { mutableListOf() } += FotoOcorrencia(
                    id = c.getLong(0), ocorrenciaId = ocorrenciaId,
                    arquivo = c.getString(2), sha256 = c.getString(3)
                )
            }
        }
        val out = mutableListOf<OcorrenciaAmbiental>()
        readableDatabase.rawQuery(
            "SELECT id, lat, lon, precisao_m, instante, descricao, transcricao_audio" +
                " FROM ocorrencia_ambiental ORDER BY instante DESC", null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += OcorrenciaAmbiental(
                    id = id, lat = c.getDouble(1), lon = c.getDouble(2),
                    precisaoM = if (c.isNull(3)) null else c.getFloat(3), instante = c.getLong(4),
                    descricao = c.getString(5), transcricaoAudio = c.getString(6),
                    fotos = fotosPorOcorrencia[id] ?: emptyList()
                )
            }
        }
        return out
    }

    fun excluirOcorrencia(id: Long) {
        writableDatabase.delete("ocorrencia_foto", "ocorrencia_id=?", arrayOf(id.toString()))
        writableDatabase.delete("ocorrencia_ambiental", "id=?", arrayOf(id.toString()))
    }

    // ---- registro de captacao (recursos hidricos) ----

    fun inserirRegistroCaptacao(r: RegistroCaptacao): Long =
        writableDatabase.insert("registro_captacao", null, ContentValues().apply {
            put("lat", r.lat); put("lon", r.lon); put("precisao_m", r.precisaoM)
            put("instante", r.instante); put("tipo_captacao", r.tipoCaptacao)
            put("vazao_ou_volume", r.vazaoOuVolume); put("unidade", r.unidade)
            put("com_bomba", r.comBomba?.let { if (it) 1 else 0 })
            put("foto_arquivo", r.fotoArquivo); put("foto_sha256", r.fotoSha256)
            put("classificacao", r.classificacao); put("base_legal", r.baseLegal)
        })

    fun registrosCaptacao(): List<RegistroCaptacao> {
        val out = mutableListOf<RegistroCaptacao>()
        readableDatabase.rawQuery(
            "SELECT id, lat, lon, precisao_m, instante, tipo_captacao, vazao_ou_volume, unidade," +
                " com_bomba, foto_arquivo, foto_sha256, classificacao, base_legal" +
                " FROM registro_captacao ORDER BY instante DESC", null
        ).use { c ->
            while (c.moveToNext()) out += RegistroCaptacao(
                id = c.getLong(0), lat = c.getDouble(1), lon = c.getDouble(2),
                precisaoM = if (c.isNull(3)) null else c.getFloat(3), instante = c.getLong(4),
                tipoCaptacao = c.getString(5),
                vazaoOuVolume = if (c.isNull(6)) null else c.getDouble(6),
                unidade = c.getString(7),
                comBomba = if (c.isNull(8)) null else c.getInt(8) != 0,
                fotoArquivo = c.getString(9), fotoSha256 = c.getString(10),
                classificacao = c.getString(11), baseLegal = c.getString(12)
            )
        }
        return out
    }

    fun excluirRegistroCaptacao(id: Long) {
        writableDatabase.delete("registro_captacao", "id=?", arrayOf(id.toString()))
    }

    // ---- ficha de vistoria ----

    fun inserirRegistroFicha(r: RegistroFicha): Long =
        writableDatabase.insert("ficha_vistoria", null, ContentValues().apply {
            put("modelo_id", r.modeloId); put("modelo_nome", r.modeloNome)
            put("lat", r.lat); put("lon", r.lon); put("precisao_m", r.precisaoM)
            put("instante", r.instante); put("respostas_json", r.respostasJson)
        })

    fun registrosFicha(): List<RegistroFicha> {
        val out = mutableListOf<RegistroFicha>()
        readableDatabase.rawQuery(
            "SELECT id, modelo_id, modelo_nome, lat, lon, precisao_m, instante, respostas_json" +
                " FROM ficha_vistoria ORDER BY instante DESC", null
        ).use { c ->
            while (c.moveToNext()) out += RegistroFicha(
                id = c.getLong(0), modeloId = c.getString(1), modeloNome = c.getString(2),
                lat = c.getDouble(3), lon = c.getDouble(4),
                precisaoM = if (c.isNull(5)) null else c.getFloat(5),
                instante = c.getLong(6), respostasJson = c.getString(7)
            )
        }
        return out
    }

    fun excluirRegistroFicha(id: Long) {
        writableDatabase.delete("ficha_vistoria", "id=?", arrayOf(id.toString()))
    }

    fun foto(id: Long): Foto? =
        readableDatabase.rawQuery(
            "SELECT $COLUNAS_FOTO FROM foto WHERE id=?", arrayOf(id.toString())
        ).use { if (it.moveToFirst()) lerFoto(it) else null }

    fun sessao(id: Long): Sessao? =
        readableDatabase.rawQuery("""
            SELECT $COLUNAS_SESSAO
            FROM sessao s WHERE s.id=?""", arrayOf(id.toString())).use {
            if (it.moveToFirst()) lerSessao(it) else null
        }

    /** Argumentos NOMEADOS: campo novo no meio da data class nao pode reatribuir colunas. */
    private fun lerSessao(c: android.database.Cursor) = Sessao(
        id = c.getLong(0),
        titulo = c.getString(1),
        processo = c.getString(2),
        criadaEm = c.getLong(3),
        fechadaEm = if (c.isNull(4)) null else c.getLong(4),
        raizMerkle = c.getString(5),
        carimboTempo = c.getString(6),
        carimboInstante = if (c.isNull(7)) null else c.getLong(7),
        carimboAutoridade = c.getString(8),
        carimboCredenciado = c.getInt(9) == 1,
        qtdFotos = c.getInt(10)
    )

    private fun lerFoto(c: android.database.Cursor) = Foto(
        c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
        c.getDouble(5), c.getDouble(6), c.getFloat(7),
        if (c.isNull(8)) null else c.getDouble(8),
        if (c.isNull(9)) null else c.getFloat(9),
        if (c.isNull(10)) null else c.getFloat(10),
        c.getLong(11),
        if (c.isNull(12)) null else c.getLong(12),
        c.getString(13), c.getString(14),
        c.getInt(15) == 1, c.getString(16)
    )

    private companion object {
        /**
         * Uma lista so, usada pelas tres consultas e pelo leitor. Antes eram tres copias da
         * mesma lista com os indices repetidos a mao: acrescentar uma coluna significava
         * lembrar de mexer em quatro lugares, e esquecer um deles nao da erro de compilacao —
         * da leitura errada em silencio.
         */
        const val COLUNAS_SESSAO =
            "s.id, s.titulo, s.processo, s.criada_em, s.fechada_em, s.raiz_merkle, " +
                "s.carimbo_tempo, s.carimbo_instante, s.carimbo_autoridade, " +
                "s.carimbo_credenciado, " +
                "(SELECT COUNT(*) FROM foto f WHERE f.sessao_id = s.id)"

        const val COLUNAS_FOTO =
            "id, sessao_id, arquivo_original, arquivo_legenda, sha256, lat, lon, precisao_m, " +
                "altitude_m, azimute, inclinacao, instante, idade_fix_s, tipo_ocorrencia, " +
                "observacao, endereco_pendente, endereco"
    }
}
