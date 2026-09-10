package br.com.oanalistaambiental.pericia.geo

/** Tipo de captação de água — determina qual limiar de uso insignificante se aplica. */
enum class TipoCaptacao {
    SUPERFICIAL,
    SUBTERRANEA_POCO_TUBULAR,
    SUBTERRANEA_OUTRA
}

enum class ClassificacaoUso { INSIGNIFICANTE, ACIMA_DO_LIMIAR }

data class ResultadoUsoInsignificante(
    val classificacao: ClassificacaoUso,
    val limiar: String,
    val baseLegal: String,
    val condicoesAdicionais: List<String> = emptyList()
)

/**
 * Classifica se uma captação de água cai no "uso insignificante" (Cadastro, hoje pelo módulo
 * SOUT) ou exige Outorga de Direito de Uso de Recursos Hídricos — normas do CERH-MG.
 *
 * Nome oficial correto é "Cadastro de Uso Insignificante", nunca "usuário significante" — abaixo
 * do limiar é o cadastro simples; acima, outorga de verdade.
 *
 * Fonte: DN CERH-MG 09/2004 (superficial), DN CERH 62/2019 (altera a acumulação nas UPGRHs do
 * norte, de 3.000 para 40.000 m³), DN CERH-MG 76/2022 (subterrânea, revoga e recria do zero o
 * art. 3º da DN 09/2004). Nenhum valor fabricado — pesquisado em 10/09/2026 direto no texto das
 * normas (siam.mg.gov.br) e cruzado com a página oficial do IGAM.
 *
 * NÃO cobre: o regime de outorga para captações ACIMA do limiar insignificante nas UPGRHs do
 * norte, que usa o cálculo de Recurso Potencial Explotável (Projeto Águas do Norte de Minas) —
 * não é um número simples, é um estudo por bacia sem tabela pública. Também não cobre valores
 * específicos que o IGAM pode fixar em Área de Restrição e Controle por superexplotação (DN
 * 76/2022, art. 5º §4º) — não há lista pública desses valores por Ottobacia.
 */
object UsoInsignificante {

    /**
     * UPGRHs (mesma sigla que [CircunscricaoHidrografica.Info.sigla] devolve) com limiar
     * SUPERFICIAL reduzido — DN 09/2004 art. 1º/2º, §1º, alterado pela DN 62/2019. Só vale para
     * captação/acumulação superficial; os limiares subterrâneos são iguais no estado inteiro,
     * sem essa distinção regional.
     */
    val UPGRH_LIMIAR_REDUZIDO = setOf(
        "SF6", "SF7", "SF8", "SF9", "SF10", "JQ1", "JQ2", "JQ3", "PA1", "MU1"
    )

    private const val VAZAO_SUPERFICIAL_PADRAO_LS = 1.0
    private const val VAZAO_SUPERFICIAL_RESTRITA_LS = 0.5
    private const val ACUMULACAO_PADRAO_M3 = 5_000.0
    private const val ACUMULACAO_RESTRITA_M3 = 40_000.0
    private const val SUBTERRANEA_OUTRA_L_DIA = 10_000.0
    private const val SUBTERRANEA_POCO_TUBULAR_L_DIA = 14_000.0

    /**
     * @param vazaoLs vazão de captação/derivação pretendida, em L/s — null quando ainda não
     * informada (a classificação então considera só a acumulação, e vice-versa).
     * @param acumulacaoM3 volume do barramento/açude pretendido, em m³.
     * @param siglaUpgrh a sigla da Circunscrição Hidrográfica do ponto (ex.: "SF5"), de
     * [CircunscricaoHidrografica.localizar] — null quando ainda não consultada.
     */
    fun classificarSuperficial(
        vazaoLs: Double?, acumulacaoM3: Double?, siglaUpgrh: String?
    ): ResultadoUsoInsignificante {
        val restrita = siglaUpgrh != null && siglaUpgrh.uppercase() in UPGRH_LIMIAR_REDUZIDO
        val limiarVazao = if (restrita) VAZAO_SUPERFICIAL_RESTRITA_LS else VAZAO_SUPERFICIAL_PADRAO_LS
        val limiarAcumulacao = if (restrita) ACUMULACAO_RESTRITA_M3 else ACUMULACAO_PADRAO_M3
        val dentroVazao = vazaoLs == null || vazaoLs <= limiarVazao
        val dentroAcumulacao = acumulacaoM3 == null || acumulacaoM3 <= limiarAcumulacao
        val texto = "captação ≤ %.1f L/s, acumulação ≤ %.0f m³%s".format(
            limiarVazao, limiarAcumulacao, if (restrita) " (UPGRH com limiar reduzido)" else ""
        )
        val base = "DN CERH-MG 09/2004, art. 1º e 2º" +
            if (restrita) " (§1º, alterado pela DN CERH 62/2019)" else ""
        return ResultadoUsoInsignificante(
            classificacao = if (dentroVazao && dentroAcumulacao) ClassificacaoUso.INSIGNIFICANTE
                else ClassificacaoUso.ACIMA_DO_LIMIAR,
            limiar = texto, baseLegal = base
        )
    }

    /**
     * @param vazaoLDia captação subterrânea pretendida, em litros por dia — null quando ainda
     * não informada.
     */
    fun classificarSubterranea(tipo: TipoCaptacao, vazaoLDia: Double?): ResultadoUsoInsignificante {
        require(tipo != TipoCaptacao.SUPERFICIAL) {
            "Use classificarSuperficial para captação superficial."
        }
        val poco = tipo == TipoCaptacao.SUBTERRANEA_POCO_TUBULAR
        val limiar = if (poco) SUBTERRANEA_POCO_TUBULAR_L_DIA else SUBTERRANEA_OUTRA_L_DIA
        val dentro = vazaoLDia == null || vazaoLDia <= limiar
        val condicoes = if (poco) listOf(
            "Estar em área rural",
            "Perfurado depois de obter a Autorização de Perfuração",
            "Fora de Área de Restrição e Controle (DN Copam/CERH 05/2017)",
            "Só 1 poço tubular classificado como insignificante por posse/propriedade"
        ) else emptyList()
        val base = "DN CERH-MG 76/2022, art. 5º" + if (poco) ", §1º a §3º" else ", caput"
        return ResultadoUsoInsignificante(
            classificacao = if (dentro) ClassificacaoUso.INSIGNIFICANTE else ClassificacaoUso.ACIMA_DO_LIMIAR,
            limiar = "captação ≤ %.0f L/dia".format(limiar),
            baseLegal = base, condicoesAdicionais = condicoes
        )
    }
}
