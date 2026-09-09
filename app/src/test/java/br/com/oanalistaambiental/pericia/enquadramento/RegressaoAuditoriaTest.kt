package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.geo.Utm
import br.com.oanalistaambiental.pericia.enquadramento.norma.Numeros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rede de seguranca dos defeitos encontrados na auditoria de 07/09/2026.
 *
 * Cada teste existe porque um bug real passou por baixo dos testes anteriores.
 */
class RegressaoAuditoriaTest {

    // ---------------------------------------------------------------- decimal

    /**
     * O bug de dez vezes. `"3.5".replace(".","")` dava 35. Na A-03-01-9 (limites 3,0 / 5,0 ha)
     * isso levava porte MEDIO — classe 3, LAS/RAS — para porte GRANDE — classe 4, LAC1.
     */
    @Test
    fun `ponto decimal nao vira separador de milhar`() {
        assertEquals(3.5, Numeros.ler("3.5")!!, 1e-9)
        assertEquals(3.5, Numeros.ler("3,5")!!, 1e-9)
        assertEquals(0.5, Numeros.ler("0,5")!!, 1e-9)
        assertEquals(1500000.0, Numeros.ler("1500000")!!, 1e-9)
    }

    /**
     * O campo aceita UM separador decimal e nenhum de milhar, e normaliza tudo para virgula.
     *
     * A escolha de nao adivinhar e deliberada: "1.500" e ambiguo entre mil e quinhentos e um e
     * meio, e chutar errado muda a modalidade de licenciamento. Quem digitar "1.500.000"
     * pensando em milhar recebe 1,5 — e ve isso escrito por extenso logo abaixo do campo,
     * antes de seguir. A tela devolve o que entendeu; a alternativa era decidir no escuro.
     */
    @Test
    fun `o campo aceita um separador decimal e nenhum de milhar`() {
        assertEquals("3,5", Numeros.filtrar("3.5"))
        assertEquals("3,5", Numeros.filtrar("3,5"))
        assertEquals("1500000", Numeros.filtrar("1500000"))
        // Separador extra e descartado; o primeiro manda. O texto do campo passa a mostrar
        // "1,500000", que ja denuncia visualmente que nao foi lido como milhar.
        assertEquals("1,500000", Numeros.filtrar("1.500.000"))
        assertEquals("3,55", Numeros.filtrar("3.5.5"))
        // sinal, letra e espaco nao entram
        assertEquals("35", Numeros.filtrar("-3 5 ha"))
        // separador solto no inicio nao vira ",5"
        assertEquals("5", Numeros.filtrar(".5"))
    }

    /** Vazio, separador solto e zero nao podem virar porte pequeno em silencio. */
    @Test
    fun `entrada invalida ou zero devolve null em vez de porte`() {
        assertNull(Numeros.ler(""))
        assertNull(Numeros.ler("   "))
        assertNull(Numeros.ler(","))
        assertNull(Numeros.ler("abc"))
        assertNull("producao de zero nao e empreendimento", Numeros.ler("0"))
        assertNull(Numeros.ler("0,0"))
        assertNotNull(Numeros.ler("0,1"))
    }

    // ---------------------------------------------------------------- UTM

    @Test
    fun `zona nunca sai da faixa de 1 a 60`() {
        assertEquals(60, Utm.zonaDe(180.0))
        assertEquals(1, Utm.zonaDe(-180.0))
        assertEquals(23, Utm.zonaDe(-45.0))
    }

    @Test
    fun `hemisferio forcado muda o northing em dez milhoes`() {
        val norte = Utm.projetar(0.001, -51.0, 22, hemisferioSulForcado = false)
        val sul = Utm.projetar(0.001, -51.0, 22, hemisferioSulForcado = true)
        assertEquals(10_000_000.0, sul.northing - norte.northing, 0.001)
        assertTrue(sul.hemisferioSul)
        assertFalse(norte.hemisferioSul)
    }
}
