package br.com.oanalistaambiental.pericia.enquadramento.norma

import org.json.JSONObject
import java.io.InputStream

/**
 * Carrega a norma dos arquivos JSON. A regra é DADO, não código: quando a DN for alterada,
 * troca-se o arquivo, não o aplicativo.
 */
object BaseNormativa {

    fun carregar(abrir: (String) -> InputStream): Regras {
        fun ler(nome: String) = abrir(nome).bufferedReader().use { it.readText() }

        val t1 = JSONObject(ler("tabela1.json"))
        val t2 = JSONObject(ler("tabela2.json"))
        val t3 = JSONObject(ler("tabela3.json"))
        val t4 = JSONObject(ler("tabela4.json"))
        val t5 = JSONObject(ler("tabela5.json"))
        val mods = JSONObject(ler("modalidades.json"))
        val ativ = JSONObject(ler("atividades.json"))
        val proc = JSONObject(ler("procedencia.json"))
        val rest = JSONObject(ler("restricoes-cadastro.json"))

        val tabela1 = t1.getJSONObject("combinacoes").let { o ->
            o.keys().asSequence().associateWith { Grau.valueOf(o.getString(it)) }
        }
        val tabela2 = t2.getJSONObject("classes").let { o ->
            o.keys().asSequence().associateWith { o.getInt(it) }
        }
        val tabela3 = t3.getJSONObject("modalidades").let { o ->
            o.keys().asSequence().associateWith { o.getString(it) }
        }

        val criterios = t4.getJSONArray("criterios").let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CriterioLocacional(
                    id = c.getString("id"),
                    peso = c.getInt("peso"),
                    texto = c.getString("texto"),
                    camada = if (c.isNull("camada")) null else c.getString("camada"),
                    automatico = c.optBoolean("automatico", false),
                    nota = if (c.isNull("nota")) null else c.optString("nota").ifBlank { null },
                    filtro = c.optJSONObject("filtro")?.let { f ->
                        fun lista(chave: String): List<String> =
                            f.optJSONArray(chave)?.let { a -> (0 until a.length()).map { a.getString(it) } }
                                ?: emptyList()
                        FiltroAtributo(
                            campos = lista("campos"),
                            contem = lista("contem"),
                            naoContem = lista("naoContem"),
                            naoIgual = lista("naoIgual")
                        )
                    }
                )
            }
        }

        val fatores = t5.getJSONArray("fatores").let { arr ->
            (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                FatorRestricao(
                    id = f.getString("id"),
                    nome = f.getString("nome"),
                    texto = f.getString("texto"),
                    camada = if (f.isNull("camada")) null else f.getString("camada"),
                    automatico = f.optBoolean("automatico", false)
                )
            }
        }

        val g = mods.getJSONObject("regras_gerais")
        val gerais = RegrasGerais(
            prazoAnaliseDias = g.getInt("prazo_analise_dias"),
            prazoAnaliseTexto = g.getString("prazo_analise_texto"),
            prazoAnaliseEiaDias = g.getInt("prazo_analise_eia_dias"),
            prazoAnaliseEiaTexto = g.getString("prazo_analise_eia_texto"),
            renovacaoAntecedenciaDias = g.getInt("renovacao_antecedencia_dias"),
            renovacaoTexto = g.getString("renovacao_texto"),
            observacaoPrazo = g.getString("observacao_prazo")
        )

        val modalidades = mods.getJSONArray("modalidades").let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                val porLicenca = m.optJSONObject("validade_por_licenca")?.let { v ->
                    v.keys().asSequence().associateWith { v.getInt(it) }
                } ?: emptyMap()
                Modalidade(
                    sigla = m.getString("sigla"),
                    nome = m.getString("nome"),
                    descricao = m.getString("descricao"),
                    licencas = m.getJSONArray("licencas").let { a -> (0 until a.length()).map { a.getString(it) } },
                    etapas = m.getInt("etapas"),
                    estudos = m.getJSONArray("estudos").let { a -> (0 until a.length()).map { a.getString(it) } },
                    validadeAnos = m.getInt("validade_anos"),
                    validadeTexto = m.getString("validade_texto"),
                    validadePorLicenca = porLicenca,
                    audienciaPublica = m.optBoolean("audiencia_publica", false),
                    audienciaTexto = m.optString("audiencia_texto", "").ifBlank { null }
                )
            }
        }

        val atividades = ativ.getJSONArray("atividades").let { arr ->
            (0 until arr.length()).map { i ->
                val a = arr.getJSONObject(i)
                val pp = a.getJSONObject("pp")
                Atividade(
                    codigo = a.getString("codigo"),
                    descricao = a.getString("descricao"),
                    pp = PotencialPoluidor(
                        Grau.valueOf(pp.getString("ar")),
                        Grau.valueOf(pp.getString("agua")),
                        Grau.valueOf(pp.getString("solo")),
                        Grau.valueOf(pp.getString("geral"))
                    ),
                    tipo = a.getString("tipo"),
                    parametro = a.getString("parametro"),
                    unidade = a.optString("unidade", "").ifBlank { null },
                    limiteP = if (a.has("limiteP")) a.getDouble("limiteP") else null,
                    limiteM = if (a.has("limiteM")) a.getDouble("limiteM") else null,
                    limitePExclusivo = a.optBoolean("limitePExclusivo", false),
                    limiteMExclusivo = a.optBoolean("limiteMExclusivo", false),
                    pisoFaixa = if (a.isNull("pisoFaixa")) null else a.optDouble("pisoFaixa"),
                    valoresSemFaixa = a.optJSONArray("valoresSemFaixa")?.let { arr ->
                        (0 until arr.length()).map { arr.getDouble(it) }
                    } ?: emptyList(),
                    pisoExclusivo = a.optBoolean("pisoExclusivo", false),
                    unidadeAlternativa = a.optString("unidadeAlternativa", "").ifBlank { null },
                    limitePAlt = if (a.has("limitePAlt")) a.getDouble("limitePAlt") else null,
                    limiteMAlt = if (a.has("limiteMAlt")) a.getDouble("limiteMAlt") else null,
                    categorias = a.optJSONArray("categorias")?.let { c ->
                        (0 until c.length()).map { j ->
                            val cat = c.getJSONObject(j)
                            FaixaCategorica(cat.getString("rotulo"), Grau.valueOf(cat.getString("porte")))
                        }
                    } ?: emptyList(),
                    conferencia = Conferencia.valueOf(a.optString("conferencia", "unico").uppercase()),
                    nota = a.optString("nota", "").ifBlank { null }
                )
            }
        }

        // Arts. 19 e 20 — restrições ao LAS/Cadastro. Aplicadas DEPOIS da Tabela 3.
        fun itens(arr: org.json.JSONArray?, campoRef: String): Map<String, ItemRestricao> {
            if (arr == null) return emptyMap()
            return (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                val c = o.getString("codigo")
                c to ItemRestricao(
                    codigo = c,
                    referencia = o.optString(campoRef),
                    nome = o.optString("nome"),
                    nota = o.optString("nota").ifBlank { null }
                )
            }
        }
        val a19 = rest.getJSONObject("art19")
        val a20 = rest.getJSONObject("art20")
        val classes = a19.optJSONArray("aplica_a_classes")?.let { arr ->
            (0 until arr.length()).map { arr.getInt(it) }.toSet()
        } ?: setOf(1, 2)
        val restricoes = RestricoesCadastro(
            art19 = itens(a19.optJSONArray("codigos"), "alinea"),
            art20Excecoes = itens(a20.optJSONArray("excecoes"), "inciso"),
            modalidadeSubstituta = rest.optString("modalidade_substituta", "RAS"),
            classesAtingidas = classes
        )

        // Art. 18 — casos condicionais. Carregados como AVISO, nunca como troca automatica.
        val casos18 = rest.optJSONObject("art18")?.optJSONArray("casos")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val efeito = when (o.optString("efeito")) {
                    "MODALIDADE_ALTERNATIVA" -> CasoEspecial.Efeito.MODALIDADE_ALTERNATIVA
                    "EXIGENCIA_ADICIONAL" -> CasoEspecial.Efeito.EXIGENCIA_ADICIONAL
                    else -> return@mapNotNull null
                }
                val c = o.getString("codigo")
                c to CasoEspecial(
                    codigo = c,
                    nome = o.optString("nome"),
                    referencia = o.optString("referencia"),
                    efeito = efeito,
                    condicao = o.optString("condicao"),
                    resumo = o.optString("resumo"),
                    texto = o.optString("texto"),
                    modalidade = o.optString("modalidade").ifBlank { null },
                    segundaHipotese = o.optString("segunda_hipotese").ifBlank { null },
                    condicaoExtra = o.optString("condicao_extra").ifBlank { null },
                    armadilha = o.optString("armadilha").ifBlank { null }
                )
            }.toMap()
        }?.let { CasosArt18(it) } ?: CasosArt18.VAZIO

        val procedencia = mapOf(
            "norma" to proc.getString("norma"),
            "extraido_em" to proc.getString("extraido_em"),
            "aviso" to proc.getString("aviso"),
            "fonte_oficial" to proc.optString("fonte_oficial"),
            "cobertura" to ativ.getString("cobertura")
        )

        return Regras(tabela1, tabela2, tabela3, criterios, fatores, modalidades, gerais,
            atividades, restricoes, casos18, procedencia)
    }
}
