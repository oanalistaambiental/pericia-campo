package br.com.oanalistaambiental.pericia.geo

/**
 * Faixa de Área de Preservação Permanente (APP) e percentual de Reserva Legal, direto do texto
 * consolidado da Lei 12.651/2012 (Código Florestal), arts. 4º e 12 — conferido linha a linha em
 * `planalto.gov.br/ccivil_03/_ato2011-2014/2012/lei/l12651.htm` antes de codificar, mesma
 * disciplina do catálogo da DN 217. É regra GERAL federal: não decide área consolidada, não
 * confere se o imóvel já tem CAR ou PRA, e não substitui o que o órgão estadual (aqui, SEMAD/
 * IGAM/IEF em MG) já tiver regulamentado à parte para o caso concreto.
 */
object AppReservaLegal {

    /**
     * Art. 4º, I, "a" a "e" — faixa marginal mínima ao longo de curso d'água natural perene ou
     * intermitente (exclui efêmero), a partir da borda da calha do leito regular.
     *
     * A lei escreve as faixas como "de X a Y metros", sem marcar se a fronteira em si pertence
     * ao grupo de cima ou de baixo — aqui o valor EXATO na fronteira (10, 50, 200, 600) entra no
     * grupo de faixa MAIOR, mesma convenção usual de leitura destes incisos. Em caso de dúvida
     * na fronteira exata, confirme com o órgão ambiental.
     */
    fun faixaCursoDaguaM(larguraCursoM: Double): Double = when {
        larguraCursoM < 10.0 -> 30.0
        larguraCursoM < 50.0 -> 50.0
        larguraCursoM < 200.0 -> 100.0
        larguraCursoM < 600.0 -> 200.0
        else -> 500.0
    }

    /** Art. 4º, IV — raio mínimo ao redor de nascente ou olho d'água perene, qualquer topografia. */
    const val RAIO_NASCENTE_OLHO_DAGUA_M = 50.0

    /**
     * Art. 4º, II, "a" e "b" — faixa mínima no entorno de lago ou lagoa NATURAL (não reservatório
     * artificial, que segue o art. 4º, III, e a licença do próprio empreendimento).
     *
     * Em área urbana a faixa é sempre 30 m. Em área rural, 100 m — exceto quando o corpo d'água
     * tem até 20 ha de superfície, caso em que a faixa cai para 50 m.
     */
    fun faixaLagoLagoaNaturalM(zonaUrbana: Boolean, areaCorpoDaguaHa: Double? = null): Double = when {
        zonaUrbana -> 30.0
        areaCorpoDaguaHa != null && areaCorpoDaguaHa <= 20.0 -> 50.0
        else -> 100.0
    }

    /**
     * Art. 4º, §4º — acumulação natural ou artificial de água com superfície MENOR que 1 ha fica
     * dispensada da faixa de proteção dos incisos II e III (lago/lagoa e reservatório artificial)
     * — mas a lei veda nova supressão de vegetação nativa ali sem autorização do órgão ambiental,
     * então "dispensada" não é o mesmo que "sem restrição nenhuma".
     */
    fun dispensaFaixaPorTamanho(areaCorpoDaguaHa: Double): Boolean = areaCorpoDaguaHa < 1.0

    enum class RegiaoReservaLegal(val titulo: String) {
        AMAZONIA_LEGAL_FLORESTA("Amazônia Legal — área de florestas"),
        AMAZONIA_LEGAL_CERRADO("Amazônia Legal — área de cerrado"),
        AMAZONIA_LEGAL_CAMPOS_GERAIS("Amazônia Legal — área de campos gerais"),
        DEMAIS_REGIOES("Demais regiões do país (inclui todo o estado de MG)")
    }

    /** Art. 12, I e II — percentual mínimo de Reserva Legal em relação à área do imóvel rural. */
    fun percentualReservaLegal(regiao: RegiaoReservaLegal): Double = when (regiao) {
        RegiaoReservaLegal.AMAZONIA_LEGAL_FLORESTA -> 80.0
        RegiaoReservaLegal.AMAZONIA_LEGAL_CERRADO -> 35.0
        RegiaoReservaLegal.AMAZONIA_LEGAL_CAMPOS_GERAIS -> 20.0
        RegiaoReservaLegal.DEMAIS_REGIOES -> 20.0
    }
}
