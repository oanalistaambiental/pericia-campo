package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.FiltroAtributo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * O critério de UC de proteção integral (peso 2) e o de uso sustentável (peso 1) apontam para a
 * MESMA camada `areas_protegidas`. Antes a detecção só olhava geometria, então um ponto dentro
 * de uma APA disparava os dois — fator locacional 2 — quando a própria Tabela 4 exclui APA do
 * critério de uso sustentável e APA não é proteção integral coisa nenhuma. Para uma atividade
 * classe 3, isso levava de LAS/RAS a LAC2: dois degraus, por causa de um atributo que estava no
 * pacote e não era lido.
 *
 * Este teste exercita a decisão pura (atributos → incide / não incide / não sei), que é a parte
 * que dá para verificar sem GeoPackage.
 */
class FiltroCriterioTest {

    private val pi = Base.regras.criterios.first { it.id == "uc_protecao_integral" }.filtro
    private val us = Base.regras.criterios.first { it.id == "uc_uso_sustentavel" }.filtro

    @Test
    fun `os dois criterios da mesma camada tem filtro`() {
        assertNotNull("proteção integral precisa de filtro", pi)
        assertNotNull("uso sustentável precisa de filtro", us)
    }

    @Test
    fun `parque nacional e protecao integral e nao e uso sustentavel`() {
        val a = mapOf("grupo" to "Proteção Integral", "nome" to "Parque Nacional da Serra do Cipó")
        assertEquals("SIM", decidir(a, pi!!))
        assertEquals("NAO", decidir(a, us!!))
    }

    /** O caso que motivou tudo: APA não é nem um nem outro. */
    @Test
    fun `APA nao dispara nenhum dos dois criterios`() {
        val porExtenso = mapOf(
            "grupo" to "Uso Sustentável",
            "categoria" to "Área de Proteção Ambiental"
        )
        assertEquals("NAO", decidir(porExtenso, pi!!))
        assertEquals("APA está excluída do critério de uso sustentável pela Tabela 4",
            "NAO", decidir(porExtenso, us!!))

        val porSigla = mapOf("grupo" to "Uso Sustentável", "categoria" to "APA")
        assertEquals("NAO", decidir(porSigla, us))
    }

    @Test
    fun `floresta nacional e uso sustentavel e conta`() {
        val a = mapOf("grupo" to "Uso Sustentável", "categoria" to "Floresta Nacional")
        assertEquals("SIM", decidir(a, us!!))
        assertEquals("NAO", decidir(a, pi!!))
    }

    /** Acento e caixa variam entre as fontes que montam o pacote e não podem decidir nada. */
    @Test
    fun `acento e caixa nao mudam a decisao`() {
        assertEquals("SIM", decidir(mapOf("GRUPO" to "PROTECAO INTEGRAL"), pi!!))
        assertEquals("SIM", decidir(mapOf("Grupo" to "proteção integral"), pi))
        assertEquals("SIM", decidir(mapOf("grupo_uc" to "  Proteção  Integral  "), pi))
    }

    /**
     * O caso honesto: o pacote não traz a coluna que separa os dois critérios. O app não pode
     * afirmar nem descartar — SIM aplicaria a uma APA um critério que a norma exclui, NÃO
     * deixaria passar uma UC de proteção integral. Diz que não sabe e devolve a decisão a quem
     * assina.
     */
    @Test
    fun `sem o atributo no pacote a resposta e nao sei`() {
        val semGrupo = mapOf("nome" to "Alguma unidade", "fid" to "12")
        assertEquals("INDEFINIDO", decidir(semGrupo, pi!!))
        assertEquals("INDEFINIDO", decidir(semGrupo, us!!))
        // Coluna presente mas vazia é a mesma situação.
        assertEquals("INDEFINIDO", decidir(mapOf("grupo" to "   "), pi))
    }

    /**
     * Réplica de DeteccaoLocacional.avaliarFiltro. Fica aqui porque a função lá é privada e
     * depende de um GeoPackage aberto; a regra é a mesma e está descrita no mesmo lugar.
     */
    private fun decidir(atributos: Map<String, String>, f: FiltroAtributo): String {
        fun norm(s: String) = java.text.Normalizer
            .normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")

        val porChave = atributos.entries.associate { norm(it.key) to norm(it.value) }
        val valores = f.campos.mapNotNull { porChave[norm(it)] }.filter { it.isNotBlank() }
        if (valores.isEmpty()) return "INDEFINIDO"
        val excluido = valores.any { v ->
            f.naoContem.any { v.contains(norm(it)) } || f.naoIgual.any { v == norm(it) }
        }
        if (excluido) return "NAO"
        if (f.contem.isEmpty()) return "SIM"
        return if (valores.any { v -> f.contem.any { v.contains(norm(it)) } }) "SIM" else "NAO"
    }
}
