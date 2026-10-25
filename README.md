# Notazas (app Android)

App Android complementaria de **Notazas**, unha aplicación de xestión escolar e avaliación académica para Educación Primaria. Permite consultar o caderno de notas, a actitude e as probas de destrezas, tamén sen conexión, e sincronízase con BoxAbalar (Nextcloud da Xunta de Galicia).

## Licenza

[PolyForm Noncommercial 1.0.0](LICENSE): **uso non comercial con atribución**.

- Podes usar, copiar, modificar e distribuír o código para calquera fin non comercial (persoal, educativo, investigación…).
- Ao distribuílo, con ou sen cambios, tes que manter o ficheiro [`LICENSE`](LICENSE) (ou o enlace á licenza) e a liña `Required Notice:` coa atribución do autor.
- O uso comercial **non** está permitido.

Isto é *source-available*, non código aberto no sentido estrito da OSI.

Copyright (c) 2026 Carlos Lombao Gómez ([@carloslombao](https://github.com/carloslombao)).

### Licenzas de terceiros

Ver [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). Esta app usa, entre outras, a librería [AutoUpdater](https://github.com/CSAbhiOnline/AutoUpdater) de Abhinaba Kundu (MIT) e compoñentes de AndroidX e Jetpack Compose (Apache 2.0). Cada unha mantense baixo a súa propia licenza.

## Privacidade

Este repositorio **non contén datos de alumnado**. As datas de nacemento e as notas só existen no servidor do centro, en BoxAbalar (cifrados) e, cifrados, no móbil de cada docente. Se atopas algún dato persoal no repositorio, avisa para retiralo.

## Compilar

1. Abre o proxecto en Android Studio (JDK 17) e sincroniza Gradle.
2. `./gradlew assembleRelease` (a firma usa `~/.android/debug.keystore`).

O workflow [`build-apk.yml`](.github/workflows/build-apk.yml) compila e publica un APK en cada `push` a `main`; a app comproba as novas versións desde Axustes → *Buscar actualizacións*.
