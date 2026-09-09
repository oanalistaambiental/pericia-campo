package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A Tabela 1 combina ar, água e solo num potencial poluidor geral. A norma não dá peso
 * diferente a cada variável, então PPG, PGP e GPP têm que dar o mesmo resultado.
 *
 * Este teste existe porque a base trazia `GPP: "P"` enquanto `PPG` e `PGP` traziam `"M"` — a
 * única assimetria em 27 células. Não dava erro em lugar nenhum: as telas usam o potencial já
 * pronto da listagem de atividades, e `Enquadramento.potencialGeral` não é chamado por
 * ninguém. A célula errada estava esperando alguém ligar a função que a usa.
 *
 * Lê o MESMO arquivo que vai dentro do aplicativo — não uma cópia.
 */
class Tabela1Test {

    private val t1 = Base.regras.tabela1

    @Test
    fun `a tabela cobre as 27 combinacoes`() {
        assertEquals(27, t1.size)
    }

    /**
     * A rede de segurança de verdade. Uma célula que destoa das suas permutações é erro de
     * transcrição, não regra da norma — e este teste encontra qualquer uma delas.
     */
    @Test
    fun `a ordem das tres variaveis nao muda o resultado`() {
        val problemas = mutableListOf<String>()
        for ((chave, valor) in t1) {
            for (p in permutacoes(chave)) {
                val outro = t1[p]
                if (outro != valor) problemas += "$chave=$valor mas $p=$outro"
            }
        }
        assertEquals("assimetrias na Tabela 1: $problemas", 0, problemas.size)
    }

    @Test
    fun `casos de referencia`() {
        assertEquals(Grau.P, t1["PPP"])
        assertEquals(Grau.G, t1["GGG"])
        assertEquals(Grau.M, t1["PPG"])
        assertEquals(Grau.M, t1["GPP"])
        assertEquals(Grau.G, t1["PGG"])
    }

    private fun permutacoes(s: String): Set<String> {
        if (s.length <= 1) return setOf(s)
        val saida = mutableSetOf<String>()
        for (i in s.indices) {
            for (p in permutacoes(s.removeRange(i, i + 1))) saida += s[i] + p
        }
        return saida
    }
}
