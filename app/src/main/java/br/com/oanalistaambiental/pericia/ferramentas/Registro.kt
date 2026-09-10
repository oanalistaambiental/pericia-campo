package br.com.oanalistaambiental.pericia.ferramentas

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.oanalistaambiental.pericia.enquadramento.ui.TelaEnquadramento
import br.com.oanalistaambiental.pericia.ui.CapturaViewModel
import br.com.oanalistaambiental.pericia.ui.TelaAlturaTrigonometrica
import br.com.oanalistaambiental.pericia.ui.TelaBaciaHidrografica
import br.com.oanalistaambiental.pericia.ui.TelaBussola
import br.com.oanalistaambiental.pericia.ui.TelaCadastrosIef
import br.com.oanalistaambiental.pericia.ui.TelaGlossario
import br.com.oanalistaambiental.pericia.ui.TelaPontosSalvos
import br.com.oanalistaambiental.pericia.ui.TelaTaxaUfemg
import br.com.oanalistaambiental.pericia.ui.TelaClinometro
import br.com.oanalistaambiental.pericia.ui.TelaConfiguracoes
import br.com.oanalistaambiental.pericia.ui.TelaIrParaCoordenada
import br.com.oanalistaambiental.pericia.ui.TelaMedicao
import br.com.oanalistaambiental.pericia.ui.TelaOcorrenciaAmbiental
import br.com.oanalistaambiental.pericia.ui.TelaPrazoRenovacao
import br.com.oanalistaambiental.pericia.ui.TelaRecursosHidricos
import br.com.oanalistaambiental.pericia.ui.TelaCondicionantes
import br.com.oanalistaambiental.pericia.ui.TelaConversorUnidades
import br.com.oanalistaambiental.pericia.ui.TelaFichaVistoria
import br.com.oanalistaambiental.pericia.ui.TelaRelatorioPonto

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
            resumo = "Digite, cole ou importe de um arquivo (KML, GPX, GeoJSON), e guia até o ponto.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.ALVO,
            exige = setOf(Recurso.GNSS),
            limite = "Guia por rumo e distância em linha reta — não é navegação por rota e " +
                "não conhece estrada, cerca nem barreira do terreno. A importação de arquivo " +
                "lê só o PRIMEIRO ponto encontrado (de uma linha ou polígono, o primeiro " +
                "vértice) — não abre Shapefile (.shp), formato binário de vários arquivos.",
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
            resumo = "Mire a base, mire o topo — altura com incerteza declarada, câmera à vista.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.ALTURA,
            exige = setOf(Recurso.CAMERA),
            limite = "O visor é só para mirar — nada aqui vira arquivo nem entra em sessão. O " +
                "erro de um grau no ângulo vira erro grande em distâncias longas ou ângulos " +
                "perto de 90° — a incerteza mostrada é estimada, não medida. Não substitui um " +
                "clinômetro dedicado.",
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
            id = "recursos_hidricos",
            nome = "Recursos hídricos",
            resumo = "Cadastro de Uso Insignificante × Outorga pela captação, e as normas do CERH-MG.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.AGUA,
            exige = setOf(Recurso.GNSS),
            limite = "É sugestão a partir do valor que você digita, não decisão do IGAM. Não " +
                "cobre o regime de outorga acima do limiar nas UPGRHs do norte (usa cálculo de " +
                "Recurso Potencial Explotável, sem tabela pública) nem valores próprios de Área " +
                "de Restrição e Controle por superexplotação.",
            tela = { nav -> TelaRecursosHidricos(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "relatorio_ponto",
            nome = "Relatório do ponto",
            resumo = "Bacia, todas as camadas de restrição e vedação — tudo que o app sabe daqui.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.RELATORIO,
            exige = setOf(Recurso.GNSS),
            limite = "Consulta o pacote de camadas instalado (offline) e, quando há sinal, um " +
                "complemento online — camada ausente do pacote e sem alcance do complemento não " +
                "aparece aqui, e silêncio não é o mesmo que ausência de restrição real.",
            tela = { nav -> TelaRelatorioPonto(vmCaptura(), nav.voltar) }
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
            id = "condicionantes",
            nome = "Condicionantes e prazos",
            resumo = "Cadastre um prazo — de uma foto do parecer ou à mão — e acompanhe até vencer.",
            grupo = Grupo.ENQUADRAR,
            icone = IconeGaveta.SINO,
            limite = "O reconhecimento de texto da foto só localiza linhas — não entende o " +
                "parecer nem decide qual linha é a condicionante ou qual data é o prazo. " +
                "Descrição, prazo e forma de cumprir são sempre confirmados por quem cadastra, " +
                "nunca preenchidos sozinhos.",
            tela = { nav -> TelaCondicionantes(vmCaptura(), nav.voltar) }
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
            id = "conversor_unidades",
            nome = "Conversor de unidades",
            resumo = "Vazão, área, volume, massa e taxa de produção — L/s, ha, m³, t/dia e mais.",
            grupo = Grupo.ENQUADRAR,
            icone = IconeGaveta.CONVERSOR,
            limite = "Só aritmética de conversão — não decide qual unidade a norma ou o " +
                "condicionante exige. Taxa de produção assume mês de 30 dias e ano de 365 dias, " +
                "convenção deste conversor, não de nenhum documento.",
            tela = { nav -> TelaConversorUnidades(nav.voltar) }
        ),
        Ferramenta(
            id = "cadastros_ief",
            nome = "Cadastros do IEF",
            resumo = "Categorias de registro de flora e fauna aquática, com base legal.",
            grupo = Grupo.ENQUADRAR,
            icone = IconeGaveta.CARTEIRA,
            limite = "Não é o cadastro em si — o registro se faz no Portal EcoSistemas do " +
                "Sisema. Valor de taxa de cadastro inicial não foi confirmado em fonte " +
                "atual: consulte o IEF antes de pagar qualquer valor.",
            tela = { nav -> TelaCadastrosIef(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "ocorrencia_ambiental",
            nome = "Ocorrência ambiental",
            resumo = "Registra coordenada, foto e descrição (com ditado por voz) — e mostra os canais oficiais de denúncia.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.ALERTA,
            exige = setOf(Recurso.GNSS),
            limite = "O app não envia denúncia nenhuma sozinho — só documenta e mostra para " +
                "onde levar. A transcrição por voz é feita pelo reconhecimento do próprio " +
                "aparelho e pode errar: revise antes de salvar.",
            tela = { nav -> TelaOcorrenciaAmbiental(vmCaptura(), nav.voltar) }
        ),
        Ferramenta(
            id = "ficha_vistoria",
            nome = "Ficha de vistoria",
            resumo = "Checklist por tipo de empreendimento — mineração, indústria, agropecuária e mais.",
            grupo = Grupo.CAMPO,
            icone = IconeGaveta.CHECKLIST,
            exige = setOf(Recurso.GNSS),
            limite = "É roteiro de apoio, não a vistoria em si — os itens são de prática geral, " +
                "não citação de condicionante específica de um processo. Confirme sempre contra a " +
                "licença e o parecer daquele empreendimento.",
            tela = { nav -> TelaFichaVistoria(vmCaptura(), nav.voltar) }
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
            resumo = "Pacote de camadas do IDE-Sisema, marca d'água e outros ajustes.",
            grupo = Grupo.APOIO,
            icone = IconeGaveta.CAMADAS,
            limite = "As camadas são um retrato baixado numa data, não a base viva do órgão. " +
                "A data de extração aparece no laudo justamente porque importa. A marca d'água " +
                "entra só na CÓPIA com legenda, nunca no arquivo original com hash calculado.",
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
