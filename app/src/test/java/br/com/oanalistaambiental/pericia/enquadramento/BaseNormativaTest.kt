package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.BaseNormativa
import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import br.com.oanalistaambiental.pericia.enquadramento.norma.Regras
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Estes testes leem os MESMOS arquivos que vão dentro do aplicativo. Se alguém editar a base
 * normativa e quebrar uma célula, o build para aqui — e não no celular de um perito.
 */
object Base {
    val regras: Regras by lazy {
        BaseNormativa.carregar { nome -> File("src/main/assets/norma/$nome").inputStream() }
    }
}

class BaseNormativaTest {

    private val r = Base.regras

    @Test
    fun `a base carrega inteira`() {
        assertEquals("Tabela 1 deve ter as 27 combinações de ar, água e solo", 27, r.tabela1.size)
        assertEquals("Tabela 2 deve ter as 9 combinações de porte e potencial", 9, r.tabela2.size)
        assertEquals("Tabela 3 deve ter as 18 combinações de classe e fator", 18, r.tabela3.size)
        assertEquals("Tabela 4 tem 11 critérios locacionais", 11, r.criterios.size)
        assertEquals("Tabela 5 tem 9 fatores de restrição ou vedação", 9, r.fatores.size)
        assertEquals("5 modalidades de licenciamento", 5, r.modalidades.size)
        assertTrue("catálogo de atividades não pode estar vazio", r.atividades.isNotEmpty())
    }

    @Test
    fun `toda modalidade citada na Tabela 3 existe na base`() {
        r.tabela3.forEach { (chave, sigla) ->
            assertNotNull("modalidade \"$sigla\" (célula $chave) não está descrita", r.modalidade(sigla))
        }
    }

    @Test
    fun `a Tabela 3 cobre todas as classes e todos os fatores`() {
        for (classe in 1..6) for (fator in 0..2) {
            assertNotNull("falta a célula classe=$classe fator=$fator", r.tabela3["$classe|$fator"])
        }
    }

    @Test
    fun `a Tabela 2 cobre todas as combinações e devolve classe de 1 a 6`() {
        for (porte in Grau.entries) for (pp in Grau.entries) {
            val classe = r.tabela2["${porte.name}${pp.name}"]
            assertNotNull("falta porte=$porte potencial=$pp", classe)
            assertTrue("classe fora da faixa 1..6", classe!! in 1..6)
        }
    }

    @Test
    fun `os pesos dos critérios locacionais são apenas 1 ou 2`() {
        r.criterios.forEach { assertTrue("${it.id} com peso ${it.peso}", it.peso == 1 || it.peso == 2) }
    }

    @Test
    fun `toda atividade numérica tem faixas coerentes`() {
        r.atividades.filter { it.tipo == "numerico" }.forEach { a ->
            assertNotNull("${a.codigo} sem limite pequeno", a.limiteP)
            assertNotNull("${a.codigo} sem limite médio", a.limiteM)
            assertTrue("${a.codigo}: limite pequeno deve ser menor que o médio", a.limiteP!! < a.limiteM!!)
        }
    }

    @Test
    fun `toda atividade categórica descreve suas categorias`() {
        r.atividades.filter { it.tipo == "categorico" }.forEach { a ->
            assertTrue("${a.codigo} sem categorias", a.categorias.isNotEmpty())
        }
    }

    @Test
    fun `a procedência da base está declarada`() {
        assertTrue(r.procedencia["norma"]!!.contains("217"))
        val aviso = r.procedencia["aviso"]!!
        // A garantia que este teste protege é que o app NUNCA se apresente como ato oficial.
        // A redação mudou em 07/09/2026 — a Listagem de Atividades passou a vir do texto
        // oficial do SIAM —, então o teste passou a conferir a substância e não a frase.
        assertTrue("a base precisa avisar que não é a publicação oficial",
            aviso.contains("NÃO são a publicação oficial") || aviso.contains("NÃO é a publicação oficial"))
        assertTrue("a base precisa dizer que não vincula a Administração",
            aviso.contains("não vincula a Administração Pública"))
        assertTrue("a base precisa se declarar simulação",
            aviso.contains("SIMULAÇÃO"))
        assertTrue("a base precisa citar a fonte oficial da listagem",
            r.procedencia["fonte_oficial"]?.contains("siam.mg.gov.br") == true)
    }
}
