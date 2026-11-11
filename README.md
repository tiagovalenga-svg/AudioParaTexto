# Áudio para Texto (Whisper offline)

App Android que transcreve áudios do WhatsApp 100% no celular, usando Whisper (via sherpa-onnx).

## Como gerar o APK
> Atenção: as bibliotecas nativas (`jniLibs`) NÃO estão na pasta (são grandes). Elas são baixadas automaticamente pelo GitHub Actions. Para compilar no Android Studio, extraia `jniLibs/arm64-v8a` e `jniLibs/armeabi-v7a` de https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-android.tar.bz2 para `app/src/main/jniLibs/`.
1. Instale o **Android Studio** (última versão) e abra esta pasta (`File > Open`).
2. Aguarde o "Gradle Sync" terminar (baixa dependências na primeira vez).
3. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   - Ou, pelo terminal: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
4. Passe o APK para o celular e instale (permita "instalar apps desconhecidos").

## Como usar
- No WhatsApp: segure o áudio → **Compartilhar** (ou ⋮ → Compartilhar) → **Áudio para Texto**.
- Na primeira transcrição o app baixa o modelo escolhido (small ≈ 360 MB). Depois disso funciona sem internet.
- Modelos: `tiny` (rápido, qualidade menor), `base`, `small` (recomendado para português).
- Dá para escolher o idioma ou deixar em detecção automática.

## Detalhes técnicos
- Decodifica opus/ogg com `MediaCodec` e reamostra para 16 kHz mono.
- Áudios longos são fatiados em trechos de ~28 s (limite do Whisper), cortando em pontos de silêncio.
- Bibliotecas nativas (`jniLibs`) e API Kotlin vêm do sherpa-onnx v2.0.1 (Apache-2.0), apenas arm64-v8a e armeabi-v7a.
- Os modelos são baixados de `huggingface.co/csukuangfj/sherpa-onnx-whisper-<modelo>`.

## Gerar o APK sem Android Studio (GitHub Actions)
1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta (incluindo a pasta oculta `.github`).
2. Abra a aba **Actions** → workflow **Build APK** → **Run workflow** (também roda sozinho a cada push).
3. Quando terminar (uns 5–10 min), abra a execução e baixe o artefato **AudioParaTexto-apk** (é um .zip com o `app-debug.apk`).
