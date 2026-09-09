package br.com.oanalistaambiental.pericia.enquadramento.norma

import java.net.URLEncoder

/**
 * Link para as decisões PÚBLICAS de licenciamento, filtradas por CÓDIGO DE ATIVIDADE.
 *
 * REGRA QUE GOVERNA ESTE ARQUIVO — leia antes de mexer.
 *
 * O app NUNCA aponta para um processo específico, e NUNCA cita nome de empreendimento,
 * empreendedor, CNPJ ou CPF — mesmo sendo dado público na origem. O que ele entrega é a BUSCA
 * filtrada pelo código da atividade, para que quem consulta leia várias decisões deferidas e
 * tire o padrão por conta própria.
 *
 * Isso não é excesso de zelo: é a regra que o projeto adotou depois de uma primeira versão da
 * ferramenta do site ter nomeado quatro processos reais e ter sido corrigida no mesmo dia. Um
 * dado ser público na origem não autoriza reempacotá-lo num aplicativo apontando para uma
 * empresa. Filtrar por atividade dá a mesma utilidade técnica sem expor ninguém.
 *
 * Há também um motivo de conflito de interesse: o autor é servidor de carreira do SISEMA, e o
 * app se sustenta em fonte 100% pública, sem qualquer acesso funcional.
 */
object Decisoes {

    /** Consulta pública de decisões de licenciamento do SISEMA. */
    private const val BASE =
        "https://sistemas.meioambiente.mg.gov.br/licenciamento/site/consulta-licenca"

    /** Portal Ecossistemas, em modo visitante. */
    const val ECOSSISTEMAS_VISITANTE =
        "https://ecosistemas.meioambiente.mg.gov.br/sla/#/acesso-visitante"

    /**
     * Busca de decisões DEFERIDAS, filtrada pelo código exato da atividade.
     *
     * Dois filtros, e os dois importam. `atividade_id` aceita o código exato ou uma
     * palavra-chave — passamos o código, que é o filtro mais restrito e o que menos depende de
     * como a atividade foi descrita no processo. E `decisao=Deferida` porque o padrão útil está
     * no que foi DEFERIDO: processo indeferido ou arquivado ensina o contrário do que se
     * procura, e misturar os dois na mesma lista é o caminho curto para tirar a conclusão
     * errada.
     */
    fun porAtividade(codigo: String, somenteDeferidas: Boolean = true): String {
        val c = URLEncoder.encode(codigo, "UTF-8")
        val filtroDecisao = if (somenteDeferidas) "&LicencaSearch%5Bdecisao%5D=Deferida" else ""
        return "$BASE?LicencaSearch%5Batividade_id%5D=$c$filtroDecisao"
    }

    /**
     * O que fazer com o resultado — texto exibido junto do link, para a ferramenta não virar
     * um atalho para copiar decisão alheia.
     */
    const val COMO_USAR =
        "A busca já vem filtrada por decisões deferidas. Abra três ou mais e observe o que se repete: " +
            "quais estudos foram exigidos, quais condicionantes voltaram em todas, e qual " +
            "critério locacional apareceu. O padrão recorrente é o que serve de referência — " +
            "um processo isolado não é padrão, e o enquadramento de um empreendimento não " +
            "vincula o do outro."

    /**
     * Armadilha real, e das que passam despercebidas: a consulta pública devolve decisões de
     * antes de 2017, proferidas sob a DN COPAM 74/2004, hoje revogada. A classe citada num
     * parecer daqueles NÃO é comparável com a que este aplicativo calcula — as tabelas
     * mudaram. Quem usa um parecer antigo como referência numérica conclui que a ferramenta
     * está errada, ou pior, instrui o processo pela tabela revogada.
     */
    const val CUIDADO_NORMA_ANTIGA =
        "Confira a DATA e a norma citada no próprio parecer antes de usá-lo como referência. " +
            "Decisões anteriores a 2017 foram enquadradas pela DN COPAM 74/2004, revogada pela " +
            "DN 217/2017: a classe que elas citam vem de outra tabela e não bate com o cálculo " +
            "desta simulação. Para conferir número, use só decisões já sob a DN 217."
}
