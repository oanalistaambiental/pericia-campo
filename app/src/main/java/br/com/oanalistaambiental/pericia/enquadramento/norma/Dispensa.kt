package br.com.oanalistaambiental.pericia.enquadramento.norma

/**
 * Dispensa de licenciamento ambiental estadual — Art. 10 da DN COPAM 217/2017.
 *
 * O TEXTO DA NORMA, que é o que sustenta tudo aqui:
 *
 *   Art. 10 – Ficam dispensados do licenciamento ambiental no âmbito estadual as atividades ou
 *   empreendimentos NÃO ENQUADRADOS EM NENHUMA DAS CLASSES ou NÃO RELACIONADOS na Listagem de
 *   Atividades do Anexo Único desta Deliberação Normativa.
 *
 *   Parágrafo único – A dispensa prevista no caput NÃO EXIME o empreendedor do dever de:
 *   I  – obter junto aos órgãos competentes os atos autorizativos para realizar intervenções
 *        ambientais bem como para intervir ou fazer uso de recurso hídrico, quando necessário;
 *   II – implantar e manter os controles ambientais para o exercício da atividade; e
 *   III– obter outras licenças, autorizações, alvarás, outorgas e certidões previstas em
 *        legislação específica.
 *
 * POR QUE ESTE ARQUIVO EXISTE, E O QUE ELE CORRIGE.
 *
 * O app tratava "valor abaixo do menor porte" como erro — uma exceção dizendo que não sabia
 * responder. Estava errado por dois motivos. Primeiro, a norma SABE responder: é dispensa, e
 * está escrita no Art. 10. Segundo, e mais importante, esse é justamente o caso que mais chega
 * ao consultor na vida real — quem pede "declaração de dispensa de licenciamento" quase sempre
 * é BANCO ou instituição financeira, para liberar crédito rural ou de investimento.
 *
 * O risco de comunicar isso mal é grande e assimétrico: "dispensado" lido sozinho vira "não
 * preciso de nada", e o empreendedor toca a obra sem DAIA, sem outorga, sem controle. Por isso
 * a dispensa aqui NUNCA aparece sem os três deveres do parágrafo único ao lado dela. É uma
 * dispensa de UMA coisa só — do processo de licenciamento estadual —, não de obrigação
 * ambiental.
 */
object Dispensa {

    /** Por que a atividade caiu em dispensa. As duas hipóteses do caput do Art. 10. */
    enum class Motivo {
        /** Valor do parâmetro abaixo do menor porte previsto para a atividade listada. */
        PORTE_INFERIOR,
        /** A atividade não consta da Listagem do Anexo Único. */
        NAO_LISTADA
    }

    data class Resultado(
        val motivo: Motivo,
        /** Null quando a atividade não é listada. */
        val atividade: Atividade?,
        val valorInformado: ValorInformado?,
        /** O texto do enquadramento, para a primeira linha do documento. */
        val titulo: String,
        val fundamento: String,
        /** Os três deveres do parágrafo único, que acompanham a dispensa sempre. */
        val deveres: List<Dever>,
        val avisos: List<String>
    )

    data class Dever(val inciso: String, val titulo: String, val texto: String, val exemplos: List<String>)

    /**
     * Os três deveres do parágrafo único do Art. 10, na ordem da norma.
     *
     * Os exemplos NÃO estão na norma — são a tradução para o que o consultor mineiro
     * efetivamente precisa providenciar. Ficam marcados como exemplo, e não como lista
     * exaustiva, porque "legislação específica" muda e varia por município e por atividade.
     */
    val DEVERES: List<Dever> = listOf(
        Dever(
            inciso = "I",
            titulo = "Atos autorizativos de intervenção ambiental e de recurso hídrico",
            texto = "A dispensa de licenciamento não substitui a autorização para intervir no " +
                "meio ambiente nem para usar água. Se o projeto mexe em vegetação, em APP ou em " +
                "curso d'água, o ato autorizativo continua obrigatório e é pedido à parte.",
            exemplos = listOf(
                "DAIA — Documento Autorizativo de Intervenção Ambiental",
                "AIA — Autorização para Intervenção Ambiental (supressão, corte de árvore isolada, intervenção em APP)",
                "Outorga de direito de uso de recursos hídricos (IGAM)",
                "Cadastro de uso insignificante, quando a vazão couber no limite da bacia",
                "Autorização para captação, barramento ou perfuração de poço"
            )
        ),
        Dever(
            inciso = "II",
            titulo = "Implantar e manter os controles ambientais",
            texto = "Continua obrigatório instalar e manter em funcionamento os controles da " +
                "atividade. Dispensa de licença não é dispensa de controle — e a falta dele " +
                "sujeita o empreendedor a auto de infração do mesmo jeito.",
            exemplos = listOf(
                "Tratamento de efluentes sanitários e industriais",
                "Destinação correta de resíduos, com MTR quando exigível",
                "Controle de emissões atmosféricas e de ruído",
                "Contenção de erosão e de sedimentos",
                "Caixa separadora de água e óleo, quando houver oficina ou abastecimento"
            )
        ),
        Dever(
            inciso = "III",
            titulo = "Outras licenças, autorizações, alvarás, outorgas e certidões",
            texto = "Permanece tudo o que outra legislação exigir — municipal, federal ou " +
                "setorial. O Art. 10 dispensa do licenciamento ESTADUAL, e de mais nada.",
            exemplos = listOf(
                "CAR — Cadastro Ambiental Rural, e regularização de Reserva Legal",
                "Cadastros e registros do IEF (consumidor de produtos da flora, comerciante de " +
                    "produtos da pesca, empacotador de carvão, entre outros)",
                "CTF/APP no IBAMA e Relatório Anual de Atividades Potencialmente Poluidoras",
                "Alvará e licença ambiental municipal, quando o município licencia",
                "Anuência de órgão gestor de unidade de conservação, quando a área incidir",
                "Registro na ANM, para atividade minerária"
            )
        )
    )

    /**
     * Aviso sobre o uso mais comum deste resultado.
     *
     * Não é enfeite: é o contexto em que o documento vai ser lido. Quem recebe costuma ser um
     * analista de crédito, não um ambientalista, e a leitura apressada de "dispensado" é o
     * caminho curto para o empreendedor tocar a obra sem DAIA e sem outorga.
     */
    const val AVISO_FINANCEIRO =
        "Este é o resultado que bancos e instituições financeiras costumam pedir como " +
            "\"declaração de dispensa de licenciamento\" para liberar crédito. Ao apresentá-lo, " +
            "deixe explícitos os três deveres acima: a dispensa é do processo de licenciamento " +
            "estadual, e não das demais autorizações nem dos controles ambientais."

    const val AVISO_INDEPENDENTE =
        "Esta é uma SIMULAÇÃO de ferramenta independente, sem vínculo com o SISEMA/SEMAD/FEAM. " +
            "Não é declaração, certidão nem ato do órgão ambiental, e não vincula a " +
            "Administração Pública. A declaração de dispensa, quando exigida formalmente, é " +
            "obtida junto ao órgão competente."

    /** Dispensa por porte inferior ao menor porte previsto para a atividade listada. */
    fun porPorteInferior(atividade: Atividade, valor: Double, unidade: String): Resultado {
        val piso = atividade.pisoFaixa
        val comparador = if (atividade.pisoExclusivo) "menor ou igual a" else "menor que"
        return Resultado(
            motivo = Motivo.PORTE_INFERIOR,
            atividade = atividade,
            valorInformado = ValorInformado(valor, unidade, atividade.parametro),
            titulo = "PORTE INFERIOR — dispensado de licenciamento ambiental estadual",
            fundamento = "DN COPAM 217/2017, art. 10 — o valor informado " +
                "(${Numeros.porExtenso(valor)} $unidade) é $comparador o menor porte previsto " +
                "para ${atividade.codigo} na Listagem do Anexo Único, que começa em " +
                "${piso?.let { Numeros.porExtenso(it) } ?: "—"} $unidade. Não havendo " +
                "enquadramento em nenhuma das classes, a atividade fica dispensada do " +
                "licenciamento ambiental no âmbito estadual.",
            deveres = DEVERES,
            avisos = listOf(AVISO_FINANCEIRO, AVISO_INDEPENDENTE)
        )
    }

    /** Dispensa por a atividade não constar da Listagem do Anexo Único. */
    fun porNaoEstarListada(descricaoInformada: String): Resultado = Resultado(
        motivo = Motivo.NAO_LISTADA,
        atividade = null,
        valorInformado = null,
        titulo = "ATIVIDADE NÃO LISTADA — dispensada de licenciamento ambiental estadual",
        fundamento = "DN COPAM 217/2017, art. 10 — a atividade informada " +
            "(\"$descricaoInformada\") não consta da Listagem de Atividades do Anexo Único, " +
            "ficando dispensada do licenciamento ambiental no âmbito estadual.",
        deveres = DEVERES,
        avisos = listOf(
            "Confirme que a atividade realmente não se enquadra em nenhum código antes de " +
                "concluir pela dispensa. O art. 11 manda considerar TODAS as atividades " +
                "exercidas em áreas contíguas ou interdependentes — fragmentar o licenciamento " +
                "para caber na dispensa é passível de penalidade.",
            AVISO_FINANCEIRO,
            AVISO_INDEPENDENTE
        )
    )
}
