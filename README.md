# Gemma WebGPU

Aplicação web local para executar Gemma com LiteRT-LM e WebGPU, preparada para Android via Capacitor.

## Desenvolvimento web

```bash
npm install
npm run dev
```

## Gerar o projeto Android

```bash
npm run cap:sync
npx cap open android
```

O build Android exige Android Studio, Android SDK e Java configurados. Para gerar um APK diretamente:

```bash
cd android
./gradlew assembleDebug
```

O app ainda usa WebGPU dentro do WebView. O acesso à NPU exigirá uma etapa posterior com um plugin nativo de inferência; Capacitor não habilita a NPU automaticamente.

## Build automático no GitHub

O workflow `Build Android APK` gera um APK debug automaticamente a cada push na branch `main`. Também é possível executá-lo manualmente em **Actions → Build Android APK → Run workflow**. Ao terminar, baixe o APK na seção **Artifacts** da execução.
