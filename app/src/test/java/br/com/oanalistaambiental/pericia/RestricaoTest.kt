package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.ConsultaRestricao.Companion.classificar
import br.com.oanalistaambiental.pericia.geo.Situacao
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A classificacao em tres estados e o coracao do produto: e o que impede o app de afirmar
 * "voce esta dentro" quando a incerteza do GNSS nao permite afirmar isso.
 */
class ClassificacaoTest {

    @Test
    fun `bem dentro do poligono e DENTRO`() {
        assertEquals(Situacao.DENTRO, classificar(-120.0, 10f))
    }

    @Test
    fun `bem fora do poligono e FORA`() {
        assertEquals(Situacao.FORA, classificar(300.0, 10f))
    }

    @Test
    fun `dentro por menos que a incerteza do GNSS e INDEFINIDO`() {
        assertEquals(Situacao.PROXIMO_AO_LIMITE, classificar(-8.0, 15f))
    }

    @Test
    fun `fora por menos que a incerteza mais a folga e INDEFINIDO`() {
        assertEquals(Situacao.PROXIMO_AO_LIMITE, classificar(40.0, 15f))
    }

    @Test
    fun `GNSS ruim empurra tudo para INDEFINIDO`() {
        assertEquals(Situacao.PROXIMO_AO_LIMITE, classificar(-90.0, 100f))
    }

    @Test
    fun `GNSS bom permite afirmar perto do limite`() {
        assertEquals(Situacao.DENTRO, classificar(-6.0, 3f))
    }

    @Test
    fun `a folga de aviso alarga a faixa de indefinicao`() {
        assertEquals(Situacao.FORA, classificar(80.0, 10f, folgaAvisoM = 50.0))
        assertEquals(Situacao.PROXIMO_AO_LIMITE, classificar(80.0, 10f, folgaAvisoM = 100.0))
    }
}
