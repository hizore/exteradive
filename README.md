# ExteraDive

Форк [exteraless](https://github.com/exteraless/exteraless) — приложение Android.
Пакет `org.divelite.messenger`, бренд «exteradive». Добавляет функции DiveLite/DiveGram
(автоответ, быстрые ответы, погода, аудио-код/битрейт, премиум-эмодзи, @all и
split-mentions, защита номера телефона, значки и стрики, локация печати,
«Отключить анимации») в одном пункте **DiveLite** в настройках «Еще» → «Дополнительно».

API id/hash берутся из `local.properties` / переменных окружения
(`TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH`). Получи свои ключи на
https://my.telegram.org (API development tools) — значение по умолчанию в этом
репозитории оставлено пустым-заглушкой и не работает.

## Структура

- `TMessagesProj/` — основной модуль Android-приложения.
- `TMessagesProj_Modules/` — вспомогательные media3/экспериментальные модули.
- `local.properties` — путь к SDK и личные ключи (образец приведён ниже).
  В репозитории файла нет и он не коммитится.
- `APK/` — релизные артефакты (`ExteraDive-arm64-v8a.apk`,
  `ExteraDive-armeabi-v7a.apk`), в репозиторий не попадают.

В репозитории намеренно нет `.gitignore`, `.gitmodules` и `.github/`: локальные
исключения держим в `.git/info/exclude`, сабмодули подключены обычными
каталогами, CI не используется.

## Требования

- JDK **21** (Temurin/OpenJDK).
- Android SDK с `compileSdk 37` (minor 2), `buildTools 36.0.0`,
  `ndk 27.2.12479018`.
- Слушать Gradle через wrapper (`gradle/wrapper/gradle-wrapper.jar`).
- `local.properties` в корне со строками:
  ```
  sdk.dir=F:\\AndroidSdk
  TELEGRAM_APP_ID=YOUR_API_ID
  TELEGRAM_APP_HASH=YOUR_API_HASH
  MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
  GLYPH_API_KEY=test
  KEYSTORE_PASS=android
  ALIAS_NAME=androidkey
  ALIAS_PASS=android
  ```
- Подпись релиза: `TMessagesProj/config/release.keystore` (DN `CN=DiveLite`,
  алиас `androidkey`, пароль `android`). Файл в репозиторий не коммитится.

## Сборка

```powershell
$env:NATIVE_TARGET = "arm64-v8a"
java -cp "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain `
  "-Dorg.gradle.jvmargs=-Xmx3500m -XX:MaxMetaspaceSize=768m" :TMessagesProj:assembleRelease

# отдельный ABI:
$env:NATIVE_TARGET = "armeabi-v7a"  # повторить команду выше
```

На выходе: `TMessagesProj/build/outputs/apk/release/*.apk`. Сохрани их в
`APK/ExteraDive-<abi>.apk`.

`build.gradle` поддерживает локальные env-переменные: `BUILD_TIMESTAMP`,
`COMMIT_ID`, `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`, `TELEGRAM_APP_ID`,
`TELEGRAM_APP_HASH`, `MAPS_API_KEY`, `GLYPH_API_KEY`.

## Пуш-уведомления

ExteraDive использует отдельный Firebase-проект. Для работы пушей вайти:

1. Создать свой Firebase-проект, скачать `google-services.json` и положить в
   `TMessagesProj/`. Файл не коммитить.
2. В Console: *Project settings → Service accounts → Generate new private key*, сохранить JSON.
3. Загрузить JSON на [my.telegram.org](https://my.telegram.org) → *API development tools* → твоё приложение → *Push notifications*.
   Без этого сервер отвечает `APP_PUSH_APIKEY_MISSING`, и пуши не дойдут.
4. В настройках приложения: Настройки → ExteraDive → Основные → Уведомления
   выбрать тип сервиса (Google FCM или microG), перезапустить приложение.

Оба типа — Google FCM и microG — используют один и тот же провайдер
`GooglePushListenerServiceProvider`; настройка влияет только на обнаружение
Google Play Services / microG.

## Заметки для contribute'ров

- Держать сайд-меню и дизайн списка чатов нетронутыми.
- Все DiveLite/DiveGram-фичи должны жить в одном пункте `DiveLite` под
  разделом «Дополнительно» в «Еще».
- Чат «Избранное» (Saved Messages) не показывает DiveGram badge/стрик ни в
  списке, ни в шапке, ни в пузырях сообщений.
- Никогда не упоминайте значки (badges) в сообщениях коммита.
- Стиль коммита: `exteradive: <краткое описание>` (используется русский).
- Скрытые строки в настройках — индекс сентинел `-1`.