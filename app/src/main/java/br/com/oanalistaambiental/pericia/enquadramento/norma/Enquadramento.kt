package br.com.oanalistaambiental.pericia.enquadramento.norma

/**
 * Motor de enquadramento da DN COPAM 217/2017.
 *
 * Funções puras, sem Android e sem estado: é o que permite testar a norma sozinha e é o que
 * torna o resultado auditável. Nada aqui adivinha — quando falta dado, o motor diz que falta,
 * em vez de devolver um número plausível.
 *
 * Cada resultado carrega a MEMÓRIA DE CÁLCULO: um profissional precisa poder conferir o
 * caminho, não só o destino.
 */
object Enquadramento {

    data class Passo(val rotulo: String, val valor: String, val fundamento: String)

    data class Resultado(
        val classe: Int,
        val porte: Grau,
        val potencialGeral: Grau,
        val fatorLocacional: Int,
        val criterioDeterminante: CriterioLocacional?,
        val criteriosIncidentes: List<CriterioLocacional>,
        val modalidade: Modalidade,
        val fatoresRestricao: List<FatorRestricao>,
        val prazoAnaliseDias: Int,
        val prazoAnaliseTexto: String,
        val passos: List<Passo>,
        val avisos: List<String>,
        /**
         * O valor que produziu o porte, para o PDF poder ser conferido.
         *
         * Sem isto o documento dizia "Porte: MEDIO — parametro: Producao bruta" e nada mais:
         * 500.000 t/ano e 5.000 t/ano geravam a mesma linha, e nao havia como refazer a conta
         * a partir do parecer.
         */
        val valorInformado: ValorInformado? = null,
        /** Coordenada usada na consulta de camadas, quando houve. */
        val coordenadaConsultada: Pair<Double, Double>? = null,
        /** Versao do pacote de camadas que sustentou a sugestao automatica. */
        val versaoPacote: String? = null,
        /** Ids dos criterios que vieram da sugestao automatica, e nao da mao de quem assina. */
        val criteriosAutomaticos: Set<String> = emptySet(),
        /** Código da atividade, para o link da consulta pública de decisões. */
        val codigoAtividade: String? = null,
        /** O que a Tabela 3 tinha indicado, quando o art. 19 ou 20 substituiu a modalidade. */
        val modalidadeDaTabela3: String? = null,
        /** A referência do artigo que proibiu o Cadastro, quando houve. */
        val restricaoCadastro: String? = null,
        /**
         * Caso do art. 18 que incide sobre esta atividade, quando existe.
         *
         * Anexado ao resultado, NUNCA aplicado sozinho: a condição depende de fato que só o
         * empreendedor confirma. A modalidade do resultado continua sendo a da Tabela 3
         * (corrigida pelos arts. 19 e 20) — o caso entra como alerta a conferir.
         */
        val casoArt18: CasoEspecial? = null
    )

    class DadoFaltante(mensagem: String) : Exception(mensagem)

    /**
     * O valor informado cai numa LACUNA DA NORMA: nenhuma faixa de porte o cobre.
     *
     * ISTO NÃO É ERRO DO APLICATIVO NEM DO USUÁRIO — é falha de redação da própria DN 217.
     * Em C-04-21-9 as três faixas foram escritas com desigualdade estrita ("< 2 ha : Pequeno",
     * "2 ha < Área Útil < 5 ha : Médio", "> 5 ha : Grande"), de modo que exatamente 2 ha e
     * exatamente 5 ha não pertencem a faixa nenhuma.
     *
     * O aplicativo podia calar e escolher um lado. Não escolhe: subestimar o porte reduz a
     * modalidade de licenciamento — o erro perigoso —, e superestimar faz o empreendedor
     * instruir processo mais pesado do que a norma exige. As duas leituras são defensáveis, e
     * quem decide entre elas é a Unidade Regional, não uma ferramenta independente.
     */
    class LacunaDeFaixa(
        val atividade: Atividade,
        val valor: Double,
        val unidade: String,
        val limiteP: Double,
        val limiteM: Double
    ) : Exception(
        "A DN 217 não define faixa de porte para ${atividade.parametro} igual a $valor $unidade " +
            "em ${atividade.codigo}."
    ) {
        /** "Área útil de 5 ha", para a frase da tela. */
        fun parametroFormatado(): String {
            val n = if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()
            return "${atividade.parametro} de $n $unidade".trim()
        }

        /** As duas leituras possíveis, na ordem em que devem ser apresentadas. */
        fun leituras(): List<Leitura> = when {
            kotlin.math.abs(valor - limiteP) < 1e-9 -> listOf(
                Leitura(Grau.P, "lendo o limite de Pequeno como \"≤ $limiteP $unidade\""),
                Leitura(Grau.M, "lendo a faixa Média como começando em $limiteP $unidade")
            )
            else -> listOf(
                Leitura(Grau.M, "lendo o teto da faixa Média como \"≤ $limiteM $unidade\""),
                Leitura(Grau.G, "lendo a faixa Grande como começando em $limiteM $unidade")
            )
        }

        data class Leitura(val porte: Grau, val fundamento: String)

        companion object {
            const val ORIENTACAO =
                "Informe um valor fora do ponto exato do limite para obter um enquadramento, ou " +
                    "leve as duas leituras à Unidade Regional (URA) antes de formalizar. Em " +
                    "decisão pública deferida já consultada sob a DN 217, uma área útil de " +
                    "exatamente 5 ha nesta atividade foi tratada como porte MÉDIO — é prática " +
                    "observada da Administração, não regra escrita, e não vincula ninguém."
        }
    }

    /**
     * O valor informado está ABAIXO do menor porte previsto para a atividade.
     *
     * ISTO NÃO É ERRO — É RESULTADO, e a norma o prevê expressamente. A DN 217 escreve 74
     * atividades com faixa inferior explícita ("2.400 t/ano < Matéria Prima Processada <
     * 12.000 t/ano : Pequeno"); abaixo desse piso o empreendimento não se enquadra em nenhuma
     * das classes, e o art. 10 diz o que acontece: fica DISPENSADO do licenciamento ambiental
     * estadual — sem ficar dispensado dos controles nem das demais autorizações.
     *
     * Uma versão anterior deste app tratava o caso como exceção, dizendo que não sabia
     * responder. Sabia: bastava ler o art. 10. Ver `Dispensa.kt`, que monta o resultado com os
     * três deveres do parágrafo único sempre ao lado da palavra "dispensado".
     */
    class PorteInferior(
        val atividade: Atividade,
        val valor: Double,
        val piso: Double,
        val unidade: String
    ) : Exception(
        "Porte inferior: ${Numeros.porExtenso(valor)} $unidade fica abaixo do menor porte " +
            "previsto para ${atividade.codigo}, que começa em ${Numeros.porExtenso(piso)} " +
            "$unidade. Dispensado de licenciamento estadual pelo art. 10 da DN 217."
    )

    // ------------------------------------------------------------------ porte

    /**
     * Porte a partir do valor do parâmetro, seguindo a redação da atividade ao pé da letra.
     *
     * Os três operadores vêm do texto oficial, atividade por atividade — não há convenção única
     * na DN 217. Ver a documentação de `limitePExclusivo` e `pisoFaixa` em Modelos.kt.
     *
     * @throws PorteInferior quando o valor fica abaixo do menor porte previsto — que não é
     *   erro, e sim a hipótese de dispensa do art. 10. Ver `Dispensa.kt`.
     */
    fun porteDe(atividade: Atividade, valor: Double, usarAlternativa: Boolean = false): Grau {
        if (atividade.tipo != "numerico") {
            throw DadoFaltante("A atividade ${atividade.codigo} usa categoria, não valor numérico.")
        }
        val limiteP = (if (usarAlternativa) atividade.limitePAlt else atividade.limiteP)
            ?: throw DadoFaltante("Faixa de porte não carregada para ${atividade.codigo}.")
        val limiteM = (if (usarAlternativa) atividade.limiteMAlt else atividade.limiteM)
            ?: throw DadoFaltante("Faixa de porte não carregada para ${atividade.codigo}.")

        // O piso só vale para a unidade principal: a norma não repete faixa inferior na
        // unidade alternativa, e supor uma equivalente seria inventar número.
        val piso = atividade.pisoFaixa
        if (!usarAlternativa && piso != null) {
            val dentro = if (atividade.pisoExclusivo) valor > piso else valor >= piso
            if (!dentro) throw PorteInferior(atividade, valor, piso, atividade.unidade ?: "")
        }

        // Lacuna de redacao: valor para o qual a norma nao define faixa alguma.
        // Vem ANTES da decisao de porte, porque qualquer porte devolvido aqui seria escolha
        // do aplicativo, nao da norma.
        if (!usarAlternativa && atividade.valoresSemFaixa.any { kotlin.math.abs(valor - it) < 1e-9 }) {
            throw LacunaDeFaixa(atividade, valor, atividade.unidade ?: "", limiteP, limiteM)
        }

        val pequeno = if (atividade.limitePExclusivo) valor < limiteP else valor <= limiteP
        val medio = if (atividade.limiteMExclusivo) valor < limiteM else valor <= limiteM
        return when {
            pequeno -> Grau.P
            medio -> Grau.M
            else -> Grau.G
        }
    }

    fun porteDe(atividade: Atividade, rotuloCategoria: String): Grau =
        atividade.categorias.firstOrNull { it.rotulo.equals(rotuloCategoria, ignoreCase = true) }?.porte
            ?: throw DadoFaltante("Categoria \"$rotuloCategoria\" não existe em ${atividade.codigo}.")

    // ------------------------------------------------- potencial poluidor geral

    /** Tabela 1. Só é necessária quando não se tem o geral pronto da listagem. */
    fun potencialGeral(regras: Regras, ar: Grau, agua: Grau, solo: Grau): Grau =
        regras.tabela1["${ar.name}${agua.name}${solo.name}"]
            ?: throw DadoFaltante("Combinação ${ar.name}${agua.name}${solo.name} ausente na Tabela 1.")

    // ------------------------------------------------------------------ classe

    /** Tabela 2. */
    fun classeDe(regras: Regras, porte: Grau, potencialGeral: Grau): Int =
        regras.tabela2["${porte.name}${potencialGeral.name}"]
            ?: throw DadoFaltante("Combinação porte ${porte.name} + potencial ${potencialGeral.name} ausente na Tabela 2.")

    /**
     * Art. 5º, parágrafo único: regularizando duas ou mais atividades ao mesmo tempo,
     * vale o enquadramento da atividade de MAIOR classe.
     */
    fun classeDoConjunto(classes: List<Int>): Int =
        classes.maxOrNull() ?: throw DadoFaltante("Nenhuma atividade informada.")

    // --------------------------------------------------------- critério locacional

    /**
     * Art. 6º, §2º e §3º: sem nenhum critério, peso 0. Incidindo mais de um, prevalece o de
     * MAIOR peso — os pesos NÃO se somam. É o erro mais comum de quem faz isso na mão.
     */
    fun fatorLocacional(criteriosIncidentes: List<CriterioLocacional>): Int =
        criteriosIncidentes.maxOfOrNull { it.peso } ?: 0

    fun criterioDeterminante(criteriosIncidentes: List<CriterioLocacional>): CriterioLocacional? =
        criteriosIncidentes.maxByOrNull { it.peso }

    // ------------------------------------------------------------- modalidade

    /** Tabela 3. */
    fun modalidadeDe(regras: Regras, classe: Int, fatorLocacional: Int): Modalidade {
        val sigla = regras.tabela3["$classe|$fatorLocacional"]
            ?: throw DadoFaltante("Combinação classe $classe + fator $fatorLocacional ausente na Tabela 3.")
        return regras.modalidade(sigla)
            ?: throw DadoFaltante("Modalidade \"$sigla\" não descrita na base.")
    }

    /** O que a Tabela 3 deu, e o que os arts. 19 e 20 fizeram com aquilo. */
    data class ModalidadeAplicada(
        val modalidade: Modalidade,
        /** A modalidade que a Tabela 3 tinha indicado, quando foi substituída. */
        val substituiu: Modalidade?,
        val motivo: RestricoesCadastro.Motivo?
    )

    /**
     * Modalidade da Tabela 3, COM as restrições dos arts. 19 e 20 aplicadas depois.
     *
     * A Tabela 3 indica LAS/Cadastro para várias combinações de classe 1 e 2, mas os arts. 19 e
     * 20 proíbem essa modalidade para uma lista fechada de atividades — 14 códigos nominais mais
     * TODA a Listagem A, salvo cinco exceções. Nesses casos a modalidade sobe para LAS/RAS.
     *
     * O app rodou sem estes dois artigos até 07/09/2026 e devolvia Cadastro onde a norma exige
     * RAS. É erro na direção perigosa: reduz a exigência, e quem seguisse o resultado
     * protocolaria a modalidade errada.
     */
    fun modalidadeAplicada(
        regras: Regras,
        atividade: Atividade,
        classe: Int,
        fatorLocacional: Int
    ): ModalidadeAplicada {
        val daTabela = modalidadeDe(regras, classe, fatorLocacional)
        if (!daTabela.sigla.contains("CADASTRO", ignoreCase = true)) {
            return ModalidadeAplicada(daTabela, null, null)
        }
        val motivo = regras.restricoesCadastro.cadastroProibido(atividade.codigo, classe)
            ?: return ModalidadeAplicada(daTabela, null, null)
        val substituta = regras.modalidade(regras.restricoesCadastro.modalidadeSubstituta)
            ?: throw DadoFaltante(
                "A modalidade substituta \"${regras.restricoesCadastro.modalidadeSubstituta}\" " +
                    "não está descrita na base."
            )
        return ModalidadeAplicada(substituta, daTabela, motivo)
    }

    // ------------------------------------------------------------------ simulação

    fun simular(
        regras: Regras,
        atividade: Atividade,
        porte: Grau,
        criteriosIncidentes: List<CriterioLocacional>,
        fatoresIncidentes: List<FatorRestricao> = emptyList(),
        comEiaOuAudiencia: Boolean = false,
        valorInformado: ValorInformado? = null,
        coordenadaConsultada: Pair<Double, Double>? = null,
        versaoPacote: String? = null,
        criteriosAutomaticos: Set<String> = emptySet()
    ): Resultado {
        val ppGeral = atividade.pp.geral
        val classe = classeDe(regras, porte, ppGeral)
        val fator = fatorLocacional(criteriosIncidentes)
        val determinante = criterioDeterminante(criteriosIncidentes)
        // Tabela 3 primeiro, arts. 19 e 20 depois. A ordem importa: os artigos SUBSTITUEM o
        // que a tabela indicou, e o resultado precisa registrar as duas coisas.
        val aplicada = modalidadeAplicada(regras, atividade, classe, fator)
        val modalidade = aplicada.modalidade

        // Nao se infere EIA a partir da modalidade: o trifasico so exige EIA quando ha
        // significativo impacto. Quem sabe disso e quem esta simulando, entao o prazo dobrado
        // depende de uma afirmacao explicita, nao de um palpite do aplicativo.
        val exigeEia = comEiaOuAudiencia
        val prazoDias = if (exigeEia) regras.gerais.prazoAnaliseEiaDias else regras.gerais.prazoAnaliseDias
        val prazoTexto = if (exigeEia) regras.gerais.prazoAnaliseEiaTexto else regras.gerais.prazoAnaliseTexto

        val passos = buildList {
            add(Passo("Atividade", "${atividade.codigo} — ${atividade.descricao}",
                "DN 217/2017, Anexo Único, Listagem de Atividades"))
            add(Passo("Porte", porte.extenso.uppercase(),
                if (atividade.tipo == "manual") "DN 217/2017, art. 4º — informado por quem simulou"
                else "DN 217/2017, art. 4º — " +
                    (valorInformado?.descricao() ?: "parâmetro: ${atividade.parametro}")))
            add(Passo("Potencial poluidor/degradador geral", ppGeral.extenso.uppercase(),
                if (atividade.tipo == "manual") "DN 217/2017, art. 3º — informado por quem simulou"
                else "DN 217/2017, art. 3º — ar ${atividade.pp.ar}, água ${atividade.pp.agua}, solo ${atividade.pp.solo}"))
            add(Passo("Classe", classe.toString(),
                "DN 217/2017, art. 5º e Tabela 2 — porte ${porte.name} × potencial ${ppGeral.name}"))
            add(Passo("Critério locacional", "peso $fator" +
                (determinante?.let { " — ${it.texto}" } ?: " — nenhum critério incidente"),
                if (criteriosIncidentes.size > 1)
                    "DN 217/2017, art. 6º, §3º — incidindo mais de um critério, prevalece o de maior peso"
                else "DN 217/2017, art. 6º, §1º e §2º, e Tabela 4"))
            if (aplicada.substituiu == null) {
                add(Passo("Modalidade", modalidade.sigla,
                    "DN 217/2017, art. 6º e Tabela 3 — classe $classe × fator $fator"))
            } else {
                add(Passo("Modalidade pela Tabela 3", aplicada.substituiu.sigla,
                    "DN 217/2017, art. 6º e Tabela 3 — classe $classe × fator $fator"))
                add(Passo("Modalidade aplicada", modalidade.sigla,
                    "DN 217/2017, ${aplicada.motivo?.referencia} — não se admite LAS/Cadastro " +
                        "para esta atividade na classe $classe" +
                        (aplicada.motivo?.item?.nota?.let { " ($it)" } ?: "")))
            }
            add(Passo("Prazo de análise", "$prazoDias dias", prazoTexto))
            // No trifásico a LP vale 5 anos e a LI 6. Dizer "10 anos" aqui seria falso — e este
            // passo vai inteiro para o PDF, que pode ser anexado a um parecer.
            add(Passo("Validade",
                if (modalidade.validadePorLicenca.isEmpty()) "${modalidade.validadeAnos} anos"
                else modalidade.validadePorLicenca.entries
                    .sortedBy { it.value }
                    .joinToString(", ") { "${it.key} ${it.value} anos" },
                modalidade.validadeTexto))
            if (modalidade.audienciaPublica && !exigeEia) add(Passo(
                "Atenção", "prazo sujeito a mudança",
                "No trifásico, havendo significativo impacto ambiental incide EIA-Rima e cabe " +
                    "audiência pública — e aí o prazo de análise passa a 12 meses."
            ))
        }

        val avisos = buildList {
            atividade.conferencia.aviso?.let { add(it) }
            atividade.nota?.let { add(it) }
            // O aviso mais consequente da lista: a Tabela 3 dizia Cadastro e a norma nao deixa.
            aplicada.substituiu?.let { antes ->
                add(
                    "A Tabela 3 indicaria ${antes.sigla} para classe $classe com fator $fator, mas a " +
                        "DN 217 não admite LAS/Cadastro para esta atividade nessa classe " +
                        "(${aplicada.motivo?.referencia}). A modalidade sobe para ${modalidade.sigla}." +
                        (aplicada.motivo?.item?.nota?.let { " $it" } ?: "")
                )
            }
            // Art. 18: condicional, entra como alerta a conferir e nunca troca a modalidade.
            regras.casosArt18.para(atividade.codigo)?.let { caso ->
                add(when (caso.efeito) {
                    CasoEspecial.Efeito.MODALIDADE_ALTERNATIVA ->
                        "ATENÇÃO — ${caso.referencia}: a modalidade pode ser ${caso.modalidade} em vez " +
                            "de ${modalidade.sigla}, mas só se for verdade que: ${caso.condicao} " +
                            "Este aplicativo NÃO decide isso por você; confirme a condição antes de " +
                            "formalizar."
                    CasoEspecial.Efeito.EXIGENCIA_ADICIONAL ->
                        "ATENÇÃO — ${caso.referencia}: ${caso.resumo}"
                })
                caso.segundaHipotese?.let { add("${caso.referencia}: $it") }
                caso.condicaoExtra?.let { add("${caso.referencia} — condição associada: $it") }
            }
            if (criteriosIncidentes.size > 1) add(
                "Incidiram ${criteriosIncidentes.size} critérios locacionais. Os pesos não se somam: " +
                    "prevaleceu o de maior peso (art. 6º, §3º)."
            )
            if (fatoresIncidentes.isNotEmpty()) add(
                "Há ${fatoresIncidentes.size} fator(es) de restrição ou vedação incidindo. Eles não " +
                    "mudam o enquadramento (art. 6º, §4º), mas precisam ser tratados nos estudos — e " +
                    "alguns vedam a atividade no local."
            )
        }

        return Resultado(
            classe = classe,
            porte = porte,
            potencialGeral = ppGeral,
            fatorLocacional = fator,
            criterioDeterminante = determinante,
            criteriosIncidentes = criteriosIncidentes,
            modalidade = modalidade,
            fatoresRestricao = fatoresIncidentes,
            prazoAnaliseDias = prazoDias,
            prazoAnaliseTexto = prazoTexto,
            passos = passos,
            avisos = avisos,
            valorInformado = valorInformado,
            coordenadaConsultada = coordenadaConsultada,
            versaoPacote = versaoPacote,
            criteriosAutomaticos = criteriosAutomaticos,
            codigoAtividade = atividade.codigo,
            modalidadeDaTabela3 = aplicada.substituiu?.sigla,
            restricaoCadastro = aplicada.motivo?.referencia,
            casoArt18 = regras.casosArt18.para(atividade.codigo)
        )
    }
}
