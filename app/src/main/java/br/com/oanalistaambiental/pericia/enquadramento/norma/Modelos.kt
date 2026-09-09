package br.com.oanalistaambiental.pericia.enquadramento.norma

enum class Grau { P, M, G;
    val extenso: String get() = when (this) { P -> "pequeno"; M -> "médio"; G -> "grande" }
}

/**
 * De onde veio o dado desta atividade, e quanto se pode confiar nele.
 *
 * OFICIAL é o nível mais alto e passou a ser o normal: em 07/09/2026 a listagem inteira foi
 * extraída campo a campo do texto oficial consolidado da DN 217 (com as alterações até a
 * DN 246/2022), publicado pelo SIAM. Os níveis antigos continuam existindo porque descrevem
 * como os primeiros dados foram levantados, antes de haver o texto oficial em mãos.
 *
 * DIVERGENTE mudou de sentido junto: hoje marca a atividade cuja REDAÇÃO DA PRÓPRIA NORMA tem
 * lacuna, sobreposição ou piso — casos em que não existe resposta certa a dar, só o aviso.
 */
enum class Conferencia { OFICIAL, CRUZADO, UNICO, DIVERGENTE;
    val aviso: String? get() = when (this) {
        OFICIAL -> null
        CRUZADO -> null
        UNICO -> "Este dado foi lido de uma única cópia da norma. Confira antes de usar em processo."
        DIVERGENTE -> "A redação da norma para esta atividade tem lacuna, sobreposição ou piso. Veja a nota."
    }
}

data class PotencialPoluidor(val ar: Grau, val agua: Grau, val solo: Grau, val geral: Grau)

data class FaixaCategorica(val rotulo: String, val porte: Grau)

data class Atividade(
    val codigo: String,
    val descricao: String,
    val pp: PotencialPoluidor,
    val tipo: String,                  // "numerico" | "categorico"
    val parametro: String,
    val unidade: String? = null,
    val limiteP: Double? = null,
    val limiteM: Double? = null,
    /**
     * A faixa Pequeno é estritamente menor que o limite, e não menor ou igual.
     *
     * NÃO É EXCEÇÃO — É A MAIORIA. A DN 217 não usa convenção única: em 185 das 226 atividades
     * numéricas a faixa Pequeno é escrita com "<" (o valor exatamente igual ao limite já é
     * MÉDIO) e nas outras 41 com "≤" (o valor igual ainda é PEQUENO). Assumir um padrão erra em
     * um dos dois grupos, e errar para baixo subestima a modalidade de licenciamento. Por isso
     * cada atividade traz este campo lido individualmente do texto oficial.
     */
    val limitePExclusivo: Boolean = false,
    /** Mesma ideia para o teto da faixa Média: com "<", o valor igual ao limite já é GRANDE. */
    val limiteMExclusivo: Boolean = false,
    /**
     * Valores em que a NORMA NÃO DEFINE faixa nenhuma. Lacuna de redação, não erro de leitura.
     *
     * O caso conhecido é C-04-21-9, cujas três faixas foram escritas todas com desigualdade
     * estrita: "Área útil < 2 ha : Pequeno", "2 ha < Área Útil < 5 ha : Médio",
     * "Área útil > 5 ha : Grande". Exatamente 2 ha e exatamente 5 ha não caem em faixa
     * alguma. Compare com a atividade imediatamente anterior no texto oficial, cujas faixas
     * fecham corretamente ("90.000 ≤ Capacidade ≤ 150.000"): não é convenção, é falha
     * pontual de redação.
     *
     * O aplicativo NÃO escolhe um lado em silêncio. Escolher Médio subestima a modalidade;
     * escolher Grande a superestima; e as duas leituras são defensáveis a partir do texto.
     * Nesses valores a simulação para e mostra as duas leituras, para quem assina decidir.
     */
    val valoresSemFaixa: List<Double> = emptyList(),
    /**
     * Piso da faixa Pequeno, quando a norma escreve a faixa com limite inferior — por exemplo
     * "2.400 t/ano < Matéria Prima Processada < 12.000 t/ano : Pequeno".
     *
     * Abaixo do piso a listagem NÃO PREVÊ FAIXA. Isso costuma significar que naquela escala a
     * atividade não é passível de licenciamento estadual, mas quem afirma isso é o órgão, não
     * este app: o motor devolve "abaixo da faixa prevista" e a tela manda conferir. Devolver
     * PEQUENO seria inventar um enquadramento que a norma não deu. 74 atividades têm piso.
     */
    val pisoFaixa: Double? = null,
    /** true quando o piso é escrito com "<" (o valor igual ao piso já está dentro da faixa). */
    val pisoExclusivo: Boolean = false,
    val unidadeAlternativa: String? = null,
    val limitePAlt: Double? = null,
    val limiteMAlt: Double? = null,
    val categorias: List<FaixaCategorica> = emptyList(),
    val conferencia: Conferencia = Conferencia.UNICO,
    val nota: String? = null
)

data class CriterioLocacional(
    val id: String,
    val peso: Int,
    val texto: String,
    val camada: String?,
    val automatico: Boolean,
    val nota: String? = null,
    /**
     * Como separar dois criterios que dividem a MESMA camada.
     *
     * Bug real: `uc_protecao_integral` (peso 2) e `uc_uso_sustentavel` (peso 1) apontam ambos
     * para a camada `areas_protegidas`, e a deteccao so olhava geometria. Um ponto dentro de
     * uma APA disparava os dois — fator locacional 2 — quando a propria Tabela 4 exclui APA do
     * criterio de uso sustentavel e APA nem sequer e protecao integral. Para uma atividade
     * classe 3, isso levava de LAS/RAS a LAC2. Dois degraus, por causa de um atributo que
     * estava no pacote e nao era lido.
     */
    val filtro: FiltroAtributo? = null
)

/**
 * Filtro pelos atributos da feicao. Comparacao sem acento e sem caixa, porque o nome da coluna
 * e o texto do valor variam entre as fontes que montam o pacote.
 */
data class FiltroAtributo(
    /** Colunas onde procurar, em ordem de preferencia. */
    val campos: List<String>,
    /** Aceita quando o valor CONTEM qualquer um destes. Vazio = aceita qualquer valor. */
    val contem: List<String> = emptyList(),
    /** Recusa quando o valor contem qualquer um destes. */
    val naoContem: List<String> = emptyList(),
    /** Recusa quando o valor inteiro e exatamente um destes (util para siglas como "APA"). */
    val naoIgual: List<String> = emptyList()
)

data class FatorRestricao(
    val id: String,
    val nome: String,
    val texto: String,
    val camada: String?,
    val automatico: Boolean
)

data class Modalidade(
    val sigla: String,
    val nome: String,
    val descricao: String,
    val licencas: List<String>,
    val etapas: Int,
    val estudos: List<String>,
    val validadeAnos: Int,
    val validadeTexto: String,
    val validadePorLicenca: Map<String, Int> = emptyMap(),
    val audienciaPublica: Boolean = false,
    val audienciaTexto: String? = null
)

/**
 * Restrições ao LAS/Cadastro dos arts. 19 e 20 da DN 217.
 *
 * A Tabela 3 pode indicar Cadastro para as classes 1 e 2, mas estes dois artigos proíbem essa
 * modalidade para uma lista fechada de atividades — e o app precisa aplicá-los DEPOIS de
 * consultar a tabela. Sem isso, sai Cadastro onde a norma exige RAS: erro na direção perigosa,
 * porque reduz a exigência de licenciamento.
 */
data class RestricoesCadastro(
    /** Códigos do art. 19: nunca Cadastro nas classes 1 e 2. */
    val art19: Map<String, ItemRestricao>,
    /** Códigos do parágrafo único do art. 20: a Listagem A é proibida, MENOS estes cinco. */
    val art20Excecoes: Map<String, ItemRestricao>,
    val modalidadeSubstituta: String,
    val classesAtingidas: Set<Int>
) {
    /**
     * A modalidade Cadastro é proibida para esta atividade nesta classe?
     *
     * Duas regras, de sentidos opostos. O art. 19 é uma LISTA DO QUE É PROIBIDO. O art. 20 é
     * uma proibição de toda a Listagem A com uma lista do que é PERMITIDO — inverter os dois
     * por descuido derruba metade das atividades minerárias para o lado errado.
     */
    fun cadastroProibido(codigo: String, classe: Int): Motivo? {
        if (classe !in classesAtingidas) return null
        art19[codigo]?.let { return Motivo(19, it, "art. 19, ${it.referencia}") }
        if (codigo.startsWith("A-") && codigo !in art20Excecoes) {
            return Motivo(20, null, "art. 20, caput — atividade minerária sem exceção no parágrafo único")
        }
        return null
    }

    data class Motivo(val artigo: Int, val item: ItemRestricao?, val referencia: String)
}

data class ItemRestricao(
    val codigo: String,
    /** Alínea (art. 19) ou inciso (art. 20) onde o código aparece. */
    val referencia: String,
    val nome: String,
    val nota: String? = null
)

/**
 * Caso especial do art. 18: uma regra que a Tabela 3 sozinha não resolve.
 *
 * São quatro situações (F-02-01-1, E-01-09-0, E-02-01-1 e E-02-01-2) em que a modalidade pode
 * ser outra, ou em que o cadastro exige documento adicional — mas SEMPRE condicionadas a fatos
 * que o aplicativo não tem como saber: se a obra é ampliação dentro do sítio aeroportuário, se
 * é recapacitação e não usina nova, se a quantidade transportada é limitada na forma da ANTT.
 *
 * Por isso [Efeito] não troca nada sozinho. A simulação segue pela Tabela 3 e o caso é
 * ANEXADO ao resultado como aviso, com a condição escrita e o texto do parágrafo. Trocar a
 * modalidade a partir de um fato presumido seria pior que não avisar: o usuário levaria para o
 * processo uma modalidade que o app escolheu por ele com base num "se" que ninguém confirmou.
 */
data class CasoEspecial(
    val codigo: String,
    val nome: String,
    /** "art. 18, §2º" — o que se cita no parecer. */
    val referencia: String,
    val efeito: Efeito,
    /** O que precisa ser verdade para o caso incidir. Vai inteira para a tela e para o PDF. */
    val condicao: String,
    val resumo: String,
    /** Texto do parágrafo, literal, para quem confere. */
    val texto: String,
    /** Sigla da modalidade alternativa, quando [efeito] é [Efeito.MODALIDADE_ALTERNATIVA]. */
    val modalidade: String? = null,
    val segundaHipotese: String? = null,
    val condicaoExtra: String? = null,
    val armadilha: String? = null
) {
    enum class Efeito {
        /** A modalidade pode ser outra, se a condição for verdadeira. */
        MODALIDADE_ALTERNATIVA,
        /** A modalidade é a mesma, mas o processo exige documento a mais. */
        EXIGENCIA_ADICIONAL
    }
}

/** Os casos do art. 18, indexados por código de atividade. */
data class CasosArt18(val porCodigo: Map<String, CasoEspecial>) {
    fun para(codigo: String): CasoEspecial? = porCodigo[codigo]
    companion object { val VAZIO = CasosArt18(emptyMap()) }
}

data class RegrasGerais(
    val prazoAnaliseDias: Int,
    val prazoAnaliseTexto: String,
    val prazoAnaliseEiaDias: Int,
    val prazoAnaliseEiaTexto: String,
    val renovacaoAntecedenciaDias: Int,
    val renovacaoTexto: String,
    val observacaoPrazo: String
)

/** Toda a norma carregada — a regra é dado, não código, para sobreviver a alterações da DN. */
data class Regras(
    val tabela1: Map<String, Grau>,
    val tabela2: Map<String, Int>,
    val tabela3: Map<String, String>,
    val criterios: List<CriterioLocacional>,
    val fatores: List<FatorRestricao>,
    val modalidades: List<Modalidade>,
    val gerais: RegrasGerais,
    val atividades: List<Atividade>,
    val restricoesCadastro: RestricoesCadastro,
    val casosArt18: CasosArt18 = CasosArt18.VAZIO,
    val procedencia: Map<String, String> = emptyMap()
) {
    fun modalidade(sigla: String): Modalidade? = modalidades.firstOrNull { it.sigla == sigla }
    fun atividade(codigo: String): Atividade? = atividades.firstOrNull { it.codigo == codigo }
    fun criterio(id: String): CriterioLocacional? = criterios.firstOrNull { it.id == id }
}

/**
 * O que o usuario informou para chegar ao porte.
 *
 * Sem isto, o PDF dizia "Porte: MEDIO — parametro: Producao bruta" e ponto. Nao havia como
 * refazer a conta a partir do documento: 500.000 t/ano e 5.000 t/ano produzem a mesma linha.
 * Um parecer que nao permite refazer a conta nao instrui processo nenhum.
 */
data class ValorInformado(
    /** Null quando o porte veio de uma categoria nominal em vez de um numero. */
    val valor: Double?,
    val unidade: String,
    val parametro: String,
    val categoria: String? = null
) {
    fun descricao(): String = when {
        categoria != null -> "$parametro: $categoria"
        valor != null -> "$parametro: ${Numeros.porExtenso(valor)} $unidade".trim()
        else -> parametro
    }
}
