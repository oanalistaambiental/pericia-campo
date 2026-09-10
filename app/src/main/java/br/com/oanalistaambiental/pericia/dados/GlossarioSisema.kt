package br.com.oanalistaambiental.pericia.dados

/**
 * Glossario de siglas do licenciamento ambiental em MG.
 *
 * So entram siglas conferidas — nenhuma aqui e chute nem lembranca vaga. Puramente educativo:
 * nao e citacao de dispositivo legal (isso e trabalho de `enquadramento/norma`), e o sentido
 * pode variar em detalhe entre normas — aqui fica o que a sigla significa em geral.
 */
object GlossarioSisema {

    data class Termo(val sigla: String, val significado: String, val descricao: String)

    val termos = listOf(
        Termo(
            "SISEMA", "Sistema Estadual de Meio Ambiente e Recursos Hídricos",
            "Reúne SEMAD, FEAM, IGAM e IEF — a estrutura inteira do licenciamento ambiental em MG."
        ),
        Termo(
            "SEMAD", "Secretaria de Estado de Meio Ambiente e Desenvolvimento Sustentável",
            "Coordena o SISEMA e define as diretrizes gerais."
        ),
        Termo(
            "FEAM", "Fundação Estadual do Meio Ambiente",
            "Licencia e fiscaliza a maior parte das atividades industriais e minerárias."
        ),
        Termo(
            "IGAM", "Instituto Mineiro de Gestão das Águas",
            "Cuida da outorga e dos demais instrumentos de recursos hídricos."
        ),
        Termo(
            "IEF", "Instituto Estadual de Florestas",
            "Flora, intervenção ambiental, unidades de conservação e fauna."
        ),
        Termo(
            "URA", "Unidade Regional de Regularização Ambiental",
            "As 10 regionais do SISEMA que decidem o licenciamento — a modalidade definitiva de " +
                "um enquadramento é sempre a da URA, nunca a de uma simulação."
        ),
        Termo(
            "COPAM", "Conselho Estadual de Política Ambiental",
            "Colegiado que edita as Deliberações Normativas (DN) — a DN COPAM 217/2017 é a base " +
                "do simulador de enquadramento deste app."
        ),
        Termo(
            "CERH-MG", "Conselho Estadual de Recursos Hídricos",
            "Equivalente ao COPAM para o tema de água — edita deliberações sobre outorga e uso " +
                "dos recursos hídricos."
        ),
        Termo(
            "DN", "Deliberação Normativa",
            "Norma editada pelo COPAM ou pelo CERH-MG."
        ),
        Termo(
            "PU", "Parecer Único",
            "O documento técnico que instrui a decisão de licenciamento — reúne a análise de " +
                "todas as áreas técnicas num só parecer."
        ),
        Termo(
            "LP / LI / LO", "Licença Prévia, de Instalação e de Operação",
            "As três fases do licenciamento trifásico, cada uma numa etapa do empreendimento."
        ),
        Termo(
            "LAS", "Licença Ambiental Simplificada",
            "Procedimento simplificado, para as classes menores previstas na DN 217."
        ),
        Termo(
            "RAS", "Relatório Ambiental Simplificado",
            "O estudo técnico que instrui o pedido de LAS."
        ),
        Termo(
            "EIA/RIMA", "Estudo de Impacto Ambiental / Relatório de Impacto Ambiental",
            "O estudo mais completo, exigido para empreendimentos de maior impacto."
        ),
        Termo(
            "DAIA", "Documento Autorizativo para Intervenção Ambiental",
            "Autoriza intervenção (supressão de vegetação, intervenção em APP etc.) fora de um " +
                "licenciamento — para atividade não licenciável ou das classes 1 e 2."
        ),
        Termo(
            "TAC", "Termo de Ajustamento de Conduta",
            "Acordo entre o órgão (ou o Ministério Público) e quem descumpriu a norma, para " +
                "regularizar a situação em prazo e condições definidos."
        ),
        Termo(
            "CAR", "Cadastro Ambiental Rural",
            "Registro público eletrônico do imóvel rural — obrigatório em nível federal."
        ),
        Termo(
            "MTR", "Manifesto de Transporte de Resíduos",
            "Sistema da DN COPAM 232/2019 que rastreia geradores, transportadores e " +
                "destinadores de resíduos — vem com obrigações periódicas (DMR, cadastro " +
                "anual) que costumam passar batidas."
        ),
        Termo(
            "DMR", "Declaração de Movimentação de Resíduos",
            "Relatório periódico (semestral) dentro do MTR, resumindo o que foi transportado " +
                "e destinado no período."
        ),
        Termo(
            "PRAD / PRADA", "Plano/Projeto de Recuperação de Área Degradada (ou Alterada)",
            "PRAD é o termo nacional; PRADA é como o IEF chama a mesma peça em MG quando " +
                "envolve Área de Preservação Permanente ou Reserva Legal. Detalha as ações " +
                "para recuperar uma área degradada."
        ),
        Termo(
            "PTRF", "Projeto Técnico de Reconstituição da Flora",
            "Documento do IEF/MG que acompanha pedido de intervenção em APP ou Reserva Legal, " +
                "detalhando como a vegetação suprimida será reposta."
        ),
        Termo(
            "CTF/APP", "Cadastro Técnico Federal de Atividades Potencialmente Poluidoras",
            "Cadastro do Ibama, em nível federal — pode ser exigido além do licenciamento " +
                "estadual. Ver o aviso na tela de resultado do enquadramento."
        ),
        Termo(
            "APP", "Área de Preservação Permanente",
            "Área protegida pela Lei 12.651/2012 (Código Florestal), por função ambiental — " +
                "margem de curso d'água, topo de morro, encosta íngreme, entre outras."
        ),
        Termo(
            "RL", "Reserva Legal",
            "Percentual do imóvel rural que deve manter cobertura vegetal nativa, também pela " +
                "Lei 12.651/2012."
        ),
        Termo(
            "UC", "Unidade de Conservação",
            "Espaço territorial protegido pelo SNUC (Lei 9.985/2000) — parque, reserva, área de " +
                "proteção ambiental, entre outras categorias."
        ),
        Termo(
            "ADA", "Área Diretamente Afetada",
            "A área do próprio empreendimento — o que muda fisicamente por causa dele."
        )
    )
}
