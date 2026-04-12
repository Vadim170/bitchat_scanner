# BitChat Scanner

Android-приложение для BLE-сканирования устройств BitChat и отображения истории обнаружений на карте.

## Публичная версия репозитория

В публичный репозиторий не включаются локальные IDE-файлы и приватные сервисные конфиги, включая:

- `.idea/`
- `local.properties`
- `app/google-services.json`

Если вам нужен собственный Firebase-проект для локальной разработки, добавьте `app/google-services.json` только у себя локально. В публичной версии репозитория Firebase не требуется.

## GitHub Releases

Workflow релиза публикует APK только по тегам вида `v*` или при ручном запуске.

Для подписанного release APK в настройках GitHub Actions нужны secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
