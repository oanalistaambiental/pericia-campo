# Perícia Campo — câmera pericial georreferenciada (Android)

App Android nativo em Kotlin. Nome provisório. Pacote `br.com.oanalistaambiental.pericia`.

---

## Gerar o APK

### GitHub Actions (não instala nada)

1. Repositório privado no GitHub, envie esta pasta.
2. **Atenção:** o upload por arrastar do navegador ignora pastas que começam com ponto, então
   a `.github` costuma ficar para trás. Se a aba **Actions** disser "Get started with GitHub
   Actions", clique em **"set up a workflow yourself"**, apague o exemplo, cole o conteúdo de
   `.github/workflows/build-apk.yml` deste projeto e salve com esse mesmo nome.
3. Aba **Actions** → aguarde o ✅ → baixe o artefato **`pericia-campo-apk`**.

O workflow **roda os testes antes de compilar**. Se um teste quebrar, o APK não é gerado — é de
propósito: melhor descobrir ali do que em campo.

### Android Studio

**Open** nesta pasta, celular no cabo com Depuração USB, botão ▶ **Run**.

## Instalar no celular

Abra o `.apk` pelo gerenciador de arquivos e autorize "instalar apps desconhecidos" para aquele
aplicativo. Não precisa de root nem de conta de desenvolvedor.

Na primeira abertura, conceda **câmera** e **localização precisa**. Confira nas configurações do
Android que a *precisão de localização* está ligada — sem ela o GNSS entrega posição grosseira.

## Instalar o pacote de camadas

Sem ele o app funciona inteiro, menos o alerta de restrição. Para ligar, gere o arquivo com
`ferramentas/montar-pacote.sh` (precisa de conexão no Brasil) e copie para:

```
Android/data/br.com.oanalistaambiental.pericia/files/pacotes/mg-base.gpkg
```

---

## O que está implementado

| Recurso | Onde |
|---|---|
| Câmera CameraX, obturador de 84 dp | `ui/TelaCamera.kt` |
| GNSS, bússola e altímetro via `LocationManager` (sem Google Play Services) | `captura/EstadoCampo.kt` |
| Selo de qualidade do ponto (bom / aceitável / ruim) | `captura/EstadoCampo.kt` |
| SHA-256 no instante da captura, antes de qualquer processamento | `captura/Integridade.kt` |
| Árvore de Merkle da sessão e caminho de prova por foto | `captura/Integridade.kt` |
| Verificador: reconfere os arquivos contra os hashes | `captura/Integridade.kt` |
| Legenda técnica sobre **cópia**, com orientação EXIF corrigida | `captura/Legenda.kt` |
| UTM SIRGAS 2000 (GRS80) calculado localmente | `geo/Utm.kt` |
| GeoPackage com índice R-tree, sem NDK | `geo/GeoPacote.kt` |
| Alerta locacional em três estados, ponderado pela precisão | `geo/Restricao.kt` |
| **Ponto de retorno** — volta ao mesmo enquadramento | `geo/PontoRetorno.kt` |
| Fila de geocodificação reversa (completa offline depois) | `captura/Enderecos.kt` |
| Laudo PDF com relatório de integridade paginado | `laudo/LaudoPdf.kt` |
| Exportação CSV e KMZ + compartilhamento | `exportacao/Exportador.kt` |
| Bússola e altímetro autônomos | `ui/TelaFerramentas.kt` |
| Ícone adaptativo | `res/mipmap-anydpi-v26/` |

## Testes automáticos

`app/src/test/` — 23 testes cobrindo o que erra em silêncio:

- **`UtmTest`** — projeção conferida contra implementação independente da série de Snyder;
  easting exatamente 500.000 no meridiano central; as três zonas de Minas.
- **`IntegridadeTest`** — vetores conhecidos de SHA-256; raiz de Merkle estável e sensível a
  qualquer alteração; caminho de prova.
- **`ClassificacaoTest`** — os três estados do alerta, inclusive o caso em que o GNSS ruim
  obriga o app a dizer "indefinido".
- **`PontoRetornoTest`** — distância, rumo, giro pelo lado mais curto atravessando o norte, e
  a recusa de inventar enquadramento sem bússola.

## Bugs corrigidos nesta versão

Auditoria completa, com revisão independente. Os que mais importam:

1. **O pacote de camadas nunca seria encontrado** — o app procurava no armazenamento interno e
   as instruções mandavam copiar para o externo. O alerta locacional ficaria desligado para
   sempre, em silêncio.
2. **O GNSS não ligava na primeira instalação** — o serviço era iniciado antes da permissão
   existir e nada religava depois. Toda foto sairia sem coordenada até fechar e reabrir o app.
3. **A coordenada saía formatada errada** (`23ZS` em vez de `23S`) — e essa string vai para a
   legenda queimada na foto, para o laudo, para o CSV e para o KML.
4. **`AbstractMethodError` em Android 8 a 10** — `LocationListener` sem os métodos que só
   viraram opcionais na API 30. O app fecharia em campo, no aparelho de quem tem celular antigo.
5. **Fotos deitadas** — a orientação EXIF era ignorada na legenda e no laudo.
6. Cópia com legenda gerada e nunca gravada no registro; laudo truncado em sessão longa;
   GeoPackage reaberto a cada foto e nunca fechado; consultas ao banco na thread da UI;
   barômetro registrado sem uso; pico de memória na legenda; `when` com tipos incompatíveis
   que impedia a compilação.

## O que ainda não está

Mapa e imagem de satélite offline (PMTiles + MapLibre), carimbo do tempo RFC 3161, C2PA,
tracklog. A estrutura já os prevê: o campo `carimbo_tempo` existe na tabela `sessao` e a raiz
de Merkle já é calculada ao fechar a sessão.

## Aviso de posicionamento

Ferramenta independente. Não é afiliada ao SISEMA/SEMAD/FEAM nem os substitui. Consome apenas
dados públicos, publicados em serviços abertos. As indicações de restrição são **indícios**
sujeitos à precisão do receptor GNSS e à data de extração das camadas — não substituem a
análise técnica do perito.
