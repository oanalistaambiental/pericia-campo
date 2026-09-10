package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.Conferencia
import br.com.oanalistaambiental.pericia.enquadramento.norma.Dispensa
import br.com.oanalistaambiental.pericia.enquadramento.norma.Enquadramento
import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guarda a listagem completa de atividades extraída do texto oficial da DN 217 em 07/09/2026.
 *
 * Lê o MESMO arquivo que vai dentro do APK. Se alguém reimportar o Anexo e a conversão perder
 * campo, quebrar faixa ou trocar operador de fronteira, o build para aqui — e não no celular de
 * um consultor prestes a protocolar.
 */
class CatalogoDN217Test {

    private val r = Base.regras

    @Test
    fun `a listagem cobre as sete listagens do Anexo Unico`() {
        val porListagem = r.atividades.groupBy { it.codigo.first() }.mapValues { it.value.size }
        assertEquals("faltou listagem", setOf('A', 'B', 'C', 'D', 'E', 'F', 'G'), porListagem.keys)
        assertTrue("catálogo pequeno demais: ${r.atividades.size}", r.atividades.size >= 220)
    }

    @Test
    fun `nenhum codigo repetido`() {
        val dup = r.atividades.groupBy { it.codigo }.filter { it.value.size > 1 }.keys
        assertEquals("códigos repetidos: $dup", 0, dup.size)
    }

    /**
     * A DN 217 NÃO tem convenção única de fronteira. Este teste trava os dois lados: se alguém
     * "padronizar" os operadores achando que é inconsistência de digitação, quebra aqui.
     */
    @Test
    fun `as duas convencoes de fronteira convivem na norma`() {
        val num = r.atividades.filter { it.tipo == "numerico" }
        val estritas = num.count { it.limitePExclusivo }
        val inclusivas = num.count { !it.limitePExclusivo }
        assertTrue("esperava muitas faixas estritas, achei $estritas", estritas > 150)
        assertTrue("esperava faixas inclusivas também, achei $inclusivas", inclusivas > 20)
    }

    /** O caso concreto de cada convenção, conferido no texto oficial. */
    @Test
    fun `o operador da fronteira muda o porte no valor exato do limite`() {
        // A-02-03-8: "Produção Bruta ≤ 300.000 t/ano : Pequeno" — 300.000 exatos é PEQUENO.
        val inclusiva = r.atividade("A-02-03-8")!!
        assertTrue(!inclusiva.limitePExclusivo)
        assertEquals(Grau.P, Enquadramento.porteDe(inclusiva, 300_000.0))
        assertEquals(Grau.M, Enquadramento.porteDe(inclusiva, 300_000.01))

        // A-03-01-8: "Produção Bruta < 10.000 m³/ano : Pequeno" — 10.000 exatos já é MÉDIO.
        val estrita = r.atividade("A-03-01-8")!!
        assertTrue(estrita.limitePExclusivo)
        assertEquals(Grau.P, Enquadramento.porteDe(estrita, 9_999.99))
        assertEquals(Grau.M, Enquadramento.porteDe(estrita, 10_000.0))
    }

    /**
     * Abaixo do menor porte a atividade não se enquadra em nenhuma classe — e o art. 10 diz o
     * que acontece: dispensa do licenciamento estadual. Devolver PEQUENO seria inventar
     * enquadramento que a norma não deu.
     */
    @Test
    fun `abaixo do menor porte o app aponta porte inferior, nao pequeno`() {
        val a = r.atividade("B-01-03-1")!!   // piso 2.400 t/ano, faixa P até 12.000
        assertNotNull("esta atividade precisa ter piso", a.pisoFaixa)
        assertEquals(2_400.0, a.pisoFaixa!!, 0.001)

        assertEquals(Grau.P, Enquadramento.porteDe(a, 5_000.0))
        assertEquals(Grau.M, Enquadramento.porteDe(a, 20_000.0))

        var apontou = false
        try { Enquadramento.porteDe(a, 1_000.0) }
        catch (e: Enquadramento.PorteInferior) {
            apontou = true
            assertTrue("a mensagem precisa citar o piso", e.message!!.contains("2.400"))
        }
        assertTrue("valor abaixo do menor porte não pode virar porte pequeno", apontou)
    }

    /**
     * A dispensa do art. 10 NUNCA pode chegar ao usuário sem os três deveres do parágrafo
     * único. É o ponto inteiro do desenho: quem lê o documento costuma ser um analista de
     * crédito, e "dispensado" sozinho vira "não preciso de nada".
     */
    @Test
    fun `a dispensa vem sempre acompanhada dos tres deveres do art 10`() {
        val a = r.atividade("B-01-03-1")!!
        val d = Dispensa.porPorteInferior(a, 1_000.0, a.unidade ?: "")

        assertEquals(Dispensa.Motivo.PORTE_INFERIOR, d.motivo)
        assertEquals("os três incisos do parágrafo único", 3, d.deveres.size)
        assertEquals(listOf("I", "II", "III"), d.deveres.map { it.inciso })
        assertTrue("o fundamento precisa citar o art. 10", d.fundamento.contains("art. 10"))
        d.deveres.forEach {
            assertTrue("${it.inciso} sem exemplos práticos", it.exemplos.isNotEmpty())
        }
        // As autorizações que o consultor mais precisa lembrar precisam estar citadas.
        val tudo = d.deveres.flatMap { it.exemplos }.joinToString(" ").uppercase()
        listOf("DAIA", "AIA", "OUTORGA", "USO INSIGNIFICANTE", "IEF", "CAR").forEach {
            assertTrue("faltou citar $it nos deveres", tudo.contains(it))
        }
        assertTrue("precisa avisar sobre o uso bancário",
            d.avisos.any { it.contains("banco", ignoreCase = true) })
        assertTrue("precisa se declarar ferramenta independente",
            d.avisos.any { it.contains("independente", ignoreCase = true) })
    }

    /** A outra hipótese do caput: atividade que não consta da Listagem. */
    @Test
    fun `atividade nao listada tambem cai em dispensa do art 10`() {
        val d = Dispensa.porNaoEstarListada("Escritório de contabilidade")
        assertEquals(Dispensa.Motivo.NAO_LISTADA, d.motivo)
        assertEquals(null, d.atividade)
        assertEquals(3, d.deveres.size)
        assertTrue(d.fundamento.contains("art. 10"))
        // Precisa alertar contra fragmentar o licenciamento para caber na dispensa (art. 11).
        assertTrue("precisa alertar sobre fragmentação",
            d.avisos.any { it.contains("fragmentar", ignoreCase = true) })
    }

    @Test
    fun `toda atividade numerica tem faixa coerente e unidade`() {
        val ruins = r.atividades.filter { it.tipo == "numerico" }.filter {
            val p = it.limiteP; val m = it.limiteM
            p == null || m == null || p >= m || it.unidade.isNullOrBlank() || it.parametro.isBlank()
        }.map { it.codigo }
        assertEquals("atividades numéricas mal formadas: $ruins", 0, ruins.size)
    }

    @Test
    fun `unidade alternativa, quando existe, vem completa`() {
        val ruins = r.atividades.filter { it.unidadeAlternativa != null }.filter {
            val p = it.limitePAlt; val m = it.limiteMAlt
            p == null || m == null || p >= m
        }.map { it.codigo }
        assertEquals("unidade alternativa incompleta: $ruins", 0, ruins.size)
        assertTrue(r.atividades.count { it.unidadeAlternativa != null } >= 4)
    }

    @Test
    fun `toda atividade categorica descreve suas categorias`() {
        val cat = r.atividades.filter { it.tipo == "categorico" }
        assertTrue("esperava as barragens por classe", cat.size >= 2)
        cat.forEach { assertTrue("${it.codigo} sem categorias", it.categorias.size >= 3) }
    }

    /** Atividade cuja redação tem lacuna, sobreposição ou piso precisa carregar a nota. */
    @Test
    fun `atividade marcada como divergente explica o porque`() {
        val semNota = r.atividades
            .filter { it.conferencia == Conferencia.DIVERGENTE && it.nota.isNullOrBlank() }
            .map { it.codigo }
        assertEquals("divergente sem nota: $semNota", 0, semNota.size)
    }

    /** A atividade revogada pela DN 246/2022 não pode estar na listagem. */
    @Test
    fun `atividade revogada nao entra`() {
        assertEquals(null, r.atividade("A-07-01-1"))
    }

    /**
     * Curadoria de 10/09/2026: uma nova extração independente do texto oficial (script à parte,
     * não incluído no app) achou 3 atividades do Anexo Único que nunca tinham entrado no
     * catálogo. O catálogo foi de 228 para 231 códigos.
     */
    @Test
    fun `curadoria de 10-09-2026 — tres atividades que faltavam agora existem`() {
        val b = r.atividade("B-10-06-5")
        assertNotNull("Fabricação de móveis de metal com tratamento químico", b)
        assertEquals(1000.0, b!!.limiteP, 0.001)
        assertEquals(10000.0, b.limiteM, 0.001)

        val d = r.atividade("D-02-01-1")
        assertNotNull("Fabricação de vinhos", d)
        assertEquals(50000.0, d!!.pisoFaixa)
        assertEquals(125000.0, d.limiteP, 0.001)
        assertEquals(250000.0, d.limiteM, 0.001)

        val e = r.atividade("E-03-07-7")
        assertNotNull("Aterro sanitário (CAF)", e)
        assertEquals(110000.0, e!!.limiteP, 0.001)
        assertEquals(2700000.0, e.limiteM, 0.001)
    }

    /**
     * E-03-07-7 já era citado pelo art. 19 (restricoes-cadastro.json, alínea II.a) antes de
     * existir no catálogo — a referência ficava órfã e ninguém conseguia, na prática, chegar a
     * essa atividade para ver a restrição de LAS/Cadastro se aplicar. Trava o cruzamento.
     */
    @Test
    fun `toda atividade citada nas restricoes do art 19 e 20 existe no catalogo`() {
        val restricoes = r.restricoesCadastro
        val citados = restricoes.art19.keys + restricoes.art20Excecoes.keys
        val orfaos = citados.filter { r.atividade(it) == null }
        assertEquals("citado nas restrições mas ausente do catálogo: $orfaos", emptyList<String>(), orfaos)
    }

    /**
     * Curadoria de 10/09/2026: a nova extração achou 6 atividades cuja faixa Pequeno tem piso no
     * texto oficial (abaixo do piso, porte inferior — art. 10), mas o `pisoFaixa` do catálogo
     * estava nulo. G-02-04-6 é o caso mais sutil: já tinha `conferencia: divergente` e a nota
     * certa, só faltava o campo que `Enquadramento.porteDe` de fato lê — a nota sozinha não
     * protegia ninguém.
     */
    @Test
    fun `curadoria de 10-09-2026 — pisos que faltavam agora barram porte inferior`() {
        val comPisoNovo = listOf(
            "B-10-03-0" to 0.1, "E-02-06-2" to 5.0, "E-05-01-1" to 1.0,
            "F-05-16-0" to 8.0, "F-06-03-3" to 0.02, "G-02-04-6" to 200.0
        )
        comPisoNovo.forEach { (codigo, piso) ->
            val a = r.atividade(codigo)!!
            assertEquals("$codigo sem piso", piso, a.pisoFaixa)
        }

        // F-05-16-0 é o único desses com piso INCLUSIVO ("8 veículos/dia <=" : Pequeno) —
        // no valor exato do piso ainda é Pequeno, só abaixo dele vira porte inferior.
        val f = r.atividade("F-05-16-0")!!
        assertFalse(f.pisoExclusivo)
        assertEquals(Grau.P, Enquadramento.porteDe(f, 8.0))
        assertThrows(Enquadramento.PorteInferior::class.java) { Enquadramento.porteDe(f, 7.99) }

        // E-05-01-1 é piso EXCLUSIVO ("1 ha <" : Pequeno) — no valor exato ainda não entra.
        val e = r.atividade("E-05-01-1")!!
        assertTrue(e.pisoExclusivo)
        assertThrows(Enquadramento.PorteInferior::class.java) { Enquadramento.porteDe(e, 1.0) }
        assertEquals(Grau.P, Enquadramento.porteDe(e, 1.01))
    }
}
