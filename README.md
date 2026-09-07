# Perícia Campo

Câmera pericial georreferenciada para vistoria ambiental. Android nativo, Kotlin, sem
dependência de Google Play Services. Tudo fica no aparelho — nada é enviado para servidor.

**A documentação do projeto está em [`LEIA-ME.md`](LEIA-ME.md)**: como gerar o APK, como
instalar, como montar o pacote de camadas e o que está implementado.

## Estrutura

O projeto Gradle fica na **raiz** deste repositório:

```
settings.gradle.kts
build.gradle.kts
app/
.github/workflows/build-apk.yml
ferramentas/
```

`.github/` é só do workflow. Código do app nunca vai lá dentro.

## Gerar o APK

Aba **Actions** → **Gerar APK** → **Run workflow**. O workflow roda os testes de unidade antes
de compilar; se um teste quebrar, o APK não sai. Baixe o artefato `pericia-campo-apk`.
