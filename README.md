# Áudio para Texto (Whisper offline)

App Android que transcreve áudios do WhatsApp 100% no celular, usando Whisper (via sherpa-onnx).

## Como gerar o APK
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
