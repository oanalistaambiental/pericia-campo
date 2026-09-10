package br.com.oanalistaambiental.pericia.ferramentas

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.oanalistaambiental.pericia.enquadramento.ui.TelaEnquadramento
import br.com.oanalistaambiental.pericia.ui.CapturaViewModel
import br.com.oanalistaambiental.pericia.ui.TelaAlturaTrigonometrica
import br.com.oanalistaambiental.pericia.ui.TelaBaciaHidrografica
import br.com.oanalistaambiental.pericia.ui.TelaBussola
import br.com.oanalistaambiental.pericia.ui.TelaGlossario
import br.com.oanalistaambiental.pericia.ui.TelaPontosSalvos
import br.com.oanalistaambiental.pericia.ui.TelaTaxaUfemg
import br.com.oanalistaambiental.pericia.ui.TelaClinometro
import br.com.oanalistaambiental.pericia.ui.TelaConfiguracoes
import br.com.oanalistaambiental.pericia.ui.TelaIrParaCoordenada
import br.com.oanalistaambiental.pericia.ui.TelaMedicao
import br.com.oanalistaambiental.pericia.ui.TelaPrazoRenovacao

/**
 * A gaveta, em um lugar so.
 *
 * Acrescentar ferramenta e acrescentar um item aqui. Nada em navegacao, permissao ou tela
 * inicial precisa mudar — e essa e a razao de existir do registro.
 *
 * A camera e as vistorias NAO estao aqui: elas dependem de estado que vive na Activity (a
 * sessao aberta, o alvo de retorno) e continuam onde estavam, aparecendo na gaveta por um
 * caminho proprio. Enquanto for so isso, forcar as duas neste contrato custaria mais do que
 * resolveria.
 */
object Registro {

    val ferramentas: List<Ferramenta> = listOf(
        Ferramenta(
            id = "bussola",
            nome = "Bússola",
            resumo = "Azimute para onde a câmera aponta, com rosa dos ventos.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.BUSSOLA,
            limite = "O azimute vem do magnetômetro do aparelho: metal, veículo, cerca e " +
                "estrutura de concreto armado desviam a leitura, e o sensor precisa de " +
                "calibração. Confira em campo aberto antes de usar em laudo.",
            tela = { nav -> TelaBussola(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "clinometro",
            nome = "Clinômetro e declividade",
            resumo = "Inclinação de talude e rampa, em graus e em porcentagem.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.CLINOMETRO,
            limite = "Mede o plano das costas do aparelho, não o do terreno: o resultado é o " +
                "da superfície onde ele está encostado. Acima de 80° a declividade em " +
                "porcentagem deixa de ser informada, porque a tangente explode e o número " +
                "perde sentido prático.",
            tela = { nav -> TelaClinometro(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "medicao",
            nome = "Medição de área e distância",
            resumo = "Perímetro caminhado, com a incerteza declarada.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.REGUA,
            exige = setOf(Recurso.GNSS),
            limite = "A área herda a precisão do GNSS de cada vértice, e a incerteza vem " +
                "junto do resultado. Não substitui levantamento topográfico nem memorial " +
                "descritivo.",
            tela = { nav -> TelaMedicao(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "coordenada",
            nome = "Ir para uma coordenada",
            resumo = "Converte entre UTM, graus decimais e GMS, e guia até o ponto.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.ALVO,
            exige = setOf(Recurso.GNSS),
            limite = "Guia por rumo e distância em linha reta — não é navegação por rota e " +
                "não conhece estrada, cerca nem barreira do terreno.",
            tela = { nav -> TelaIrParaCoordenada(vmCaptura(), voltar = nav.voltar) }
        ),
        Ferramenta(
            id = "enquadramento",
            nome = "Enquadramento — DN COPAM 217/2017",
            resumo = "Porte, classe, critério locacional e modalidade, com memória de cálculo.",
            grupo = Grupo.ENQUADRAR,
            icone = IconeGaveta.ENQUADRAMENTO,
            limite = "É SIMULAÇÃO de ferramenta independente, sem vínculo com o SISEMA. Não " +
                "substitui o enquadramento do órgão ambiental e não vincula a Administração " +
                "Pública. A modalidade definitiva é a da Unidade Regional.",
            tela = { nav -> TelaEnquadramento(nav.voltar) }
        ),
        Ferramenta(
            id = "pontos_salvos",
            nome = "Pontos salvos",
            resumo = "Marca e guarda a posição atual — não presa a foto nem a medição.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.PINO,
            exige = setOf(Recurso.GNSS),
            limite = "Guarda só coordenada e nome — sem hash nem cadeia de custódia. Para prova " +
                "de campo, use a câmera de perícia.",
            tela = { nav -> TelaPontosSalvos(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "altura_trigonometrica",
            nome = "Altura por trigonometria",
            resumo = "Distância até a base + ângulo até o topo — altura com incerteza declarada.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.ALTURA,
            limite = "O erro de um grau no ângulo vira erro grande em distâncias longas ou " +
                "ângulos perto de 90° — a incerteza mostrada é estimada, não medida. Não " +
                "substitui um clinômetro dedicado.",
            tela = { nav -> TelaAlturaTrigonometrica(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "bacia_hidrografica",
            nome = "Bacia hidrográfica",
            resumo = "Em que Circunscrição Hidrográfica (CH) o ponto atual cai — dado do IGAM.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.BACIA,
            exige = setOf(Recurso.GNSS),
            limite = "Fronteira simplificada a partir da base do IGAM — pode variar alguns " +
                "metros do limite oficial. Não é indício de restrição, só contexto para " +
                "outorga.",
            tela = { nav -> TelaBaciaHidrografica(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "prazo_renovacao",
            nome = "Prazo de renovação",
            resumo = "Data-limite para protocolar a renovação, 120 dias antes do vencimento.",
            grupo = Grupo.ENQUADRAR,
            icone = IconeGaveta.CALENDARIO,
            limite = "Faz só a conta do art. 12 da DN COPAM 217/2017. Não conhece condicionante " +
                "nem prazo específico do processo — confirme com a Unidade Regional.",
            tela = { nav -> TelaPrazoRenovacao(nav.voltar) }
        ),
        Ferramenta(
            id = "taxa_ufemg",
            nome = "Taxa em UFEMG",
            resumo = "Taxa de expediente (intervenção/DAIA) e taxa florestal, em UFEMG.",
            grupo = Grupo.ENQUADRAR,
            icone = IconeGaveta.MOEDA,
            limite = "Estimativa a partir da planilha de custos vigente para o exercício " +
                "citado na tela — o valor da UFEMG muda todo ano. Confirme a taxa final com a " +
                "Unidade Regional antes de pagar.",
            tela = { nav -> TelaTaxaUfemg(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "glossario",
            nome = "Glossário do SISEMA",
            resumo = "Siglas do licenciamento ambiental em MG — SEMAD, FEAM, IGAM, IEF e mais.",
            grupo = Grupo.ENQUADRAR,
            icone = IconeGaveta.GLOSSARIO,
            limite = "É referência educativa, não citação de dispositivo legal — o sentido " +
                "pode variar em detalhe entre normas.",
            tela = { nav -> TelaGlossario(nav.voltar) }
        ),
        Ferramenta(
            id = "configuracoes",
            nome = "Camadas e ajustes",
            resumo = "Pacote de camadas do IDE-Sisema, versão e procedência.",
            grupo = Grupo.APOIO,
            icone = IconeGaveta.CAMADAS,
            limite = "As camadas são um retrato baixado numa data, não a base viva do órgão. " +
                "A data de extração aparece no laudo justamente porque importa.",
            tela = { nav -> TelaConfiguracoes(vmCaptura(), nav.voltar) }
        )
    )

    fun porId(id: String): Ferramenta? = ferramentas.firstOrNull { it.id == id }
}

/**
 * O ViewModel de captura, obtido dentro da composicao.
 *
 * Fica aqui para o registro nao precisar receber o ViewModel como parametro — como o dono do
 * ViewModelStore e a Activity, esta e a MESMA instancia que a camera usa, e nao uma copia.
 */
@Composable
private fun vmCaptura(): CapturaViewModel = viewModel()
