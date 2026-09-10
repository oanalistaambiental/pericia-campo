#!/usr/bin/env python3
"""
Confere referencias e exaustividade de enum entre pacotes, SEM compilar.

Por que existe. A verificacao rapida deste projeto (kotlinc + stubs) compila so os pacotes
`norma` e `geo`, que nao dependem do Android. Os pacotes `ui` e `laudo` dependem do Compose e
so compilam no CI. Resultado pratico: tres commits seguidos quebraram o build por duas causas
que nao tem nada de sutil —

  1. um tipo novo em `norma` usado em `ui` sem o import correspondente
     (`Unresolved reference 'Decisoes'`), e
  2. uma constante nova num enum de `norma` deixando um `when` de `ui` nao exaustivo
     (`'when' expression must be exhaustive. Add the 'OFICIAL' branch`).

As duas so aparecem depois de um ciclo de CI inteiro. Este script as encontra em menos de um
segundo. Ele NAO substitui o compilador: e um filtro grosseiro para o erro que ja aconteceu.

Uso:  python3 ferramentas/confere_referencias.py
Saida: lista de problemas; codigo 1 se houver algum.
"""
import os
import re
import sys

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BASE = os.path.join(RAIZ, "app/src/main/java/br/com/oanalistaambiental/pericia")
PACOTE = "br.com.oanalistaambiental.pericia"

DECL = re.compile(
    r"^(?:@\w+\s+)*(?:public |internal |private |abstract |sealed |open |data |value )*"
    r"(class|object|interface|enum class|fun)\s+([A-Za-z_]\w*)", re.M)
CONST_ENUM = re.compile(r"^\s*enum class\s+(\w+)\s*(?:\([^)]*\))?\s*\{([^}]*)", re.M)


def arquivos(sub):
    caminho = os.path.join(BASE, sub)
    if not os.path.isdir(caminho):
        return []
    return [os.path.join(caminho, n) for n in sorted(os.listdir(caminho)) if n.endswith(".kt")]


def declaracoes(sub):
    """Nomes de topo declarados num pacote, e as constantes de cada enum."""
    nomes, enums = {}, {}
    for f in arquivos(sub):
        texto = open(f, encoding="utf-8").read()
        for tipo, nome in DECL.findall(texto):
            if tipo == "fun":
                continue
            nomes[nome] = os.path.basename(f)
        for nome, corpo in CONST_ENUM.findall(texto):
            consts = [c.strip() for c in corpo.split(";")[0].split(",")]
            enums[nome] = [c for c in consts if re.fullmatch(r"[A-Z][A-Z0-9_]*", c)]
    return nomes, enums


def blocos_when(texto):
    """Cada corpo de `when (...) { ... }` do arquivo, por contagem de chaves.

    Sem isto a checagem so olhava o arquivo inteiro, e um `else ->` em qualquer outro `when`
    silenciava a falta de ramo no `when` que importava.
    """
    saida = []
    for m in re.finditer(r"when\s*\([^)]*\)\s*\{", texto):
        i = texto.index("{", m.start())
        nivel, j = 0, i
        while j < len(texto):
            if texto[j] == "{":
                nivel += 1
            elif texto[j] == "}":
                nivel -= 1
                if nivel == 0:
                    break
            j += 1
        saida.append(texto[i:j])
    return saida


FUN_TELA = re.compile(r"^@Composable\s*\n\s*fun\s+(Tela\w*)\s*\(", re.M)


def checa_telas_sem_import(problemas):
    """
    Caso a parte — o DECL/`fornecedores` acima so rastreia class/object/interface/enum,
    nunca `fun` (de proposito: incluir toda funcao privada criaria ruido demais). Mas TODA
    tela deste projeto e uma @Composable `fun TelaAlgumaCoisa(...)`, convencao forte o
    bastante para checar sozinha, sem essa ambiguidade — foi assim que `TelaOcorrenciaAmbiental`
    usada em `ferramentas/Registro.kt` sem import passou batido pelo resto do script e so
    quebrou no CI.
    """
    fornecedoras = {}  # nome -> (pacote relativo, arquivo)
    for sub in ("ui", "enquadramento/ui"):
        for f in arquivos(sub):
            texto = open(f, encoding="utf-8").read()
            for nome in FUN_TELA.findall(texto):
                fornecedoras[nome] = (sub, os.path.basename(f))

    for sub in ("ferramentas", "ui", "enquadramento/ui", "enquadramento/norma", ""):
        for f in arquivos(sub):
            rel = os.path.relpath(f, RAIZ)
            texto = open(f, encoding="utf-8").read()
            importados = set(re.findall(
                rf"^import {re.escape(PACOTE)}\.[\w.]+\.(\w+)", texto, re.M))
            curinga = {m for m in re.findall(
                rf"^import {re.escape(PACOTE)}\.(\w+)\.\*", texto, re.M)}
            declara_aqui = set(FUN_TELA.findall(texto))
            usadas = set(re.findall(r"\b(Tela\w*)\s*\(", texto)) - declara_aqui
            for nome in sorted(usadas):
                if nome not in fornecedoras:
                    continue
                pac, orig = fornecedoras[nome]
                if pac == sub or nome in importados or pac.split("/")[-1] in curinga:
                    continue
                problemas.append(
                    f"{rel}: usa '{nome}' (de {pac}/{orig}) sem import — "
                    f"o CI falha com \"Unresolved reference '{nome}'\"")


def checa_comentarios_aninhados(problemas):
    """
    Kotlin, ao contrario de Java/C, aceita `/* */` ANINHADO. Um texto de comentario que contenha
    a sequencia literal "/*" (por exemplo, tentando escrever "*\\/*" para descrever um MIME
    coringa "*/*" sem fechar o bloco de verdade) abre um comentario DENTRO do comentario — o
    `*/` que fecharia o bloco de fora so fecha o de dentro, e tudo que vem depois (a funcao
    inteira, as vezes) vira comentario silenciosamente. Foi exatamente assim que
    `exportacao/Exportador.kt` quebrou o CI sem nenhum erro visivel numa revisao manual: dois
    "*\\/*" dentro de um KDoc.
    """
    for sub in ("captura", "dados", "geo", "taxas", "ui", "laudo", "ferramentas",
                "enquadramento/norma", "enquadramento/ui", "enquadramento/laudo",
                "enquadramento/geo", ""):
        for f in arquivos(sub):
            rel = os.path.relpath(f, RAIZ)
            texto = open(f, encoding="utf-8").read()
            i, n = 0, len(texto)
            profundidade = 0
            em_linha = False
            em_string = False
            tripla = False
            while i < n:
                c = texto[i]
                if em_linha:
                    if c == "\n":
                        em_linha = False
                    i += 1
                    continue
                if profundidade > 0:
                    if texto[i:i + 2] == "/*":
                        profundidade += 1
                        i += 2
                        continue
                    if texto[i:i + 2] == "*/":
                        profundidade -= 1
                        i += 2
                        continue
                    i += 1
                    continue
                if em_string:
                    if c == "\\" and not tripla:
                        i += 2
                        continue
                    if (not tripla and c == '"') or (tripla and texto[i:i + 3] == '"""'):
                        em_string = False
                        i += 3 if tripla else 1
                        continue
                    i += 1
                    continue
                if texto[i:i + 2] == "//":
                    em_linha = True
                    i += 2
                    continue
                if texto[i:i + 2] == "/*":
                    profundidade = 1
                    i += 2
                    continue
                if texto[i:i + 3] == '"""':
                    em_string = True
                    tripla = True
                    i += 3
                    continue
                if c == '"':
                    em_string = True
                    tripla = False
                    i += 1
                    continue
                i += 1
            if profundidade != 0:
                problemas.append(
                    f"{rel}: comentario de bloco '/* */' nao fecha direito (profundidade "
                    f"{profundidade} no fim do arquivo) — procure \"/*\" dentro do TEXTO de um "
                    f"comentario, provavelmente tentando escrever \"*/*\" escapado; o CI falha "
                    f"engolindo codigo real como comentario, sem nenhum erro obvio")


def main():
    problemas = []
    fornecedores = {}
    enums_todos = {}
    for sub in ("captura", "dados", "geo", "taxas"):
        nomes, enums = declaracoes(sub)
        for n, orig in nomes.items():
            fornecedores[n] = (sub, orig)
        enums_todos.update({n: (sub, c) for n, c in enums.items()})

    for sub in ("ui", "laudo", "ferramentas", "enquadramento/norma", "enquadramento/ui", "enquadramento/laudo", "enquadramento/geo", ""):
        for f in arquivos(sub):
            rel = os.path.relpath(f, RAIZ)
            texto = open(f, encoding="utf-8").read()
            if f"package {PACOTE}.{sub}" not in texto and sub:
                pass
            curinga = {m for m in re.findall(
                rf"^import {re.escape(PACOTE)}\.(\w+)\.\*", texto, re.M)}
            importados = set(re.findall(
                rf"^import {re.escape(PACOTE)}\.\w+\.(\w+)", texto, re.M))
            proprio = {n for n, _ in DECL.findall(texto)}

            # 1) referencia a tipo de outro pacote sem import
            for nome, (pac, orig) in sorted(fornecedores.items()):
                if pac in curinga or nome in importados or nome in proprio:
                    continue
                if sub == pac:
                    continue
                if re.search(rf"(?<![\w.]){re.escape(nome)}\s*[.(<]", texto):
                    problemas.append(
                        f"{rel}: usa '{nome}' (de {pac}/{orig}) sem import — "
                        f"o CI falha com \"Unresolved reference '{nome}'\"")

            # 2) when sobre enum de outro pacote sem cobrir todas as constantes
            #
            # Precisa ser por BLOCO, nao por arquivo: procurar "else ->" no arquivo inteiro
            # dava falso negativo — Telas.kt tem varios `when`, um deles com else, e o `when`
            # sobre Conferencia (o que quebrou o CI) passava batido.
            for nome, (pac, consts) in sorted(enums_todos.items()):
                if not consts or sub == pac:
                    continue
                for bloco in blocos_when(texto):
                    usadas = set(re.findall(rf"{re.escape(nome)}\.([A-Z][A-Z0-9_]*)\s*->", bloco))
                    if not usadas:
                        continue
                    if re.search(r"(^|\n)\s*else\s*->", bloco):
                        continue
                    faltando = [c for c in consts if c not in usadas]
                    if faltando:
                        problemas.append(
                            f"{rel}: 'when' sobre {nome} nao cobre "
                            f"{', '.join(faltando)} e nao tem 'else' — o CI falha com "
                            f"\"'when' expression must be exhaustive\"")

    checa_telas_sem_import(problemas)
    checa_comentarios_aninhados(problemas)

    if problemas:
        print("PROBLEMAS ENCONTRADOS:\n")
        for p in dict.fromkeys(problemas):
            print("  •", p)
        print(f"\n{len(set(problemas))} problema(s).")
        return 1
    print("Nenhuma referencia sem import nem 'when' incompleto entre pacotes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
