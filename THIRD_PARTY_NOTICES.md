# Licenzas de terceiros

Notazas (app Android) inclúe as seguintes librerías de terceiros, cada unha baixo a súa propia licenza. A licenza do propio proxecto está en [`LICENSE`](LICENSE) e só cobre o código de Notazas.

## AutoUpdater

- Autor: Abhinaba Kundu (CSAbhiOnline)
- Fonte: https://github.com/CSAbhiOnline/AutoUpdater
- Uso: comprobar e descargar actualizacións da app (Axustes → *Buscar actualizacións*).
- Licenza: MIT

```
MIT License

Copyright (c) 2024 Abhinaba Kundu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## AndroidX, Jetpack Compose e Material 3

- Autor: Google / Android Open Source Project
- Licenza: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)

## Outras dependencias

As dependencias restantes (por exemplo Retrofit, OkHttp, kotlinx.serialization, Coil ou o cifrado de AndroidX Security) tamén se publican con licenzas permisivas (Apache 2.0 ou MIT). A lista exacta das versións usadas está en `app/build.gradle.kts` e `gradle/libs.versions.toml`.
