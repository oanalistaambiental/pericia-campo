# Perícia Campo — app Android de câmera pericial georreferenciada

Projeto Android nativo em Kotlin. Nome "Perícia Campo" é provisório (a identidade do kit
ainda está em aberto). Pacote: `br.com.oanalistaambiental.pericia`.

## Estado deste código — leia antes

O projeto foi escrito por completo, mas **não foi compilado**: a máquina onde ele nasceu não
tem o Android SDK e não alcança os repositórios do Google/Maven. Então trate a primeira
compilação como parte do trabalho, não como formalidade. O que **foi** verificado:

- A projeção UTM SIRGAS 2000, escrita à mão, foi conferida contra uma implementação
  independente da série de Snyder: erro de ida e volta abaixo de **0,14 mm** em oito pontos de
  Minas, e easting exatamente 500.000 no meridiano central. Os testes em
  `app/src/test/.../UtmTest.kt` congelam esses valores.
- Os scripts em `ferramentas/` passam na checagem de sintaxe.

Se a primeira compilação falhar, o suspeito quase certo é alinhamento de versão (AGP, Kotlin,
Compose). Está tudo declarado em `build.gradle.kts` e `app/build.gradle.kts`.

## Como gerar o APK

### Caminho A — GitHub Actions (recomendado, não instala nada)

Você já usa GitHub para o site. Aqui é o mesmo movimento:

1. Crie um repositório **privado** e envie esta pasta para ele.
2. O arquivo `.github/workflows/build-apk.yml` já está pronto: ele compila sozinho a cada envio.
3. Aba **Actions** → a execução mais recente → baixe o artefato `pericia-campo-debug-apk`.
4. Transfira o `.apk` para o celular e instale (veja "Instalar no celular").

Primeira execução leva de 5 a 10 minutos porque ela baixa o SDK.

### Caminho B — Android Studio no seu computador

1. Instale o Android Studio (gratuito).
2. **Open** → escolha esta pasta. Ele baixa SDK e dependências sozinho (pode levar bem uns 20 min).
3. Ligue o celular por USB com "Depuração USB" ativada e clique em ▶ **Run**.
   O app instala e abre direto no aparelho — é o ciclo mais rápido para ajustar detalhe de tela.

## Instalar no celular (APK de fora da loja)

1. Copie o `.apk` para o telefone.
2. Abra o arquivo pelo gerenciador de arquivos.
3. O Android vai pedir para autorizar "instalar apps desconhecidos" para aquele aplicativo —
   autorize. É o procedimento normal de teste, e não exige root nem conta de desenvolvedor.

## Instalar o pacote de camadas

O alerta de restrição só liga quando o pacote geoespacial estiver no aparelho:

```
Android/data/br.com.oanalistaambiental.pericia/files/pacotes/mg-base.gpkg
```

Para gerar esse arquivo, veja `ferramentas/montar-pacote.sh`. Sem ele o app funciona
normalmente — câmera, GPS, legenda, hash, sessões e laudo — apenas sem o alerta locacional.

## O que já está implementado

| Recurso | Onde |
|---|---|
| Câmera com CameraX e obturador grande | `ui/Telas.kt` |
| GPS, bússola e altitude via `LocationManager` (sem Google Play Services) | `captura/EstadoCampo.kt` |
| Selo de qualidade do ponto (bom / aceitável / ruim) | `captura/EstadoCampo.kt` |
| SHA-256 no momento da captura, antes de qualquer processamento | `captura/Integridade.kt` |
| Árvore de Merkle da sessão + caminho de prova por foto | `captura/Integridade.kt` |
| Legenda técnica gravada sobre **cópia**, nunca sobre o original | `captura/Legenda.kt` |
| Conversão local para UTM SIRGAS 2000 (GRS80), sem serviço externo | `geo/Utm.kt` |
| Leitura de GeoPackage com índice R-tree, sem NDK | `geo/GeoPacote.kt` |
| Alerta locacional em três estados, ponderado pela precisão do GNSS | `geo/Restricao.kt` |
| Sessões, fotos e restrições em SQLite | `dados/Banco.kt` |
| Laudo fotográfico em PDF com relatório de integridade | `laudo/LaudoPdf.kt` |

## O que ainda não está

Mapa e imagem de satélite offline (PMTiles + MapLibre), exportação KMZ/CSV, geocodificação
reversa com fila offline, nota de voz, tracklog, ponto de retorno, carimbo do tempo RFC 3161 e
compartilhamento do PDF pelo seletor do Android. A estrutura já prevê todos: o campo
`carimbo_tempo` existe na tabela `sessao`, e a raiz de Merkle já é calculada ao fechar a sessão.

## Aviso de posicionamento

Ferramenta independente. Não é afiliada ao SISEMA/SEMAD/FEAM nem os substitui. Consome apenas
dados públicos, publicados em serviços abertos. As indicações de restrição são **indícios**
sujeitos à precisão do receptor GNSS e à data de extração das camadas — não substituem a
análise técnica do perito.
