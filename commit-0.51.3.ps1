# Коммит и пуш 0.51.3: Android 118 + iOS 120. Запуск: ! powershell -File "D:/PROJECT/leaphouse-remote-ios/commit-0.51.3.ps1"
Set-Location D:\PROJECT\leaphouse-remote-ios
git add ElectroRemote/Model/CarViewModel.swift ElectroRemote/Resources/i18n.json ElectroRemote/UI/Screens/ConnectScreen.swift android/app/build.gradle android/app/src/main/assets/i18n.json android/app/src/main/java/uz/electro/remote/CarViewModel.kt android/app/src/main/java/uz/electro/remote/ui/ConnectScreen.kt docs/i18n/strings.json project.yml commit-0.51.3.ps1
git commit -m @'
android 0.51.3 (118) + ios 0.51.3 (120): подключение — отсчёт 0…20 с от нажатия, открываемся сразу

Экран подключения:
- Отсчёт идёт от нажатия «Подключиться», на экране «N / 20 с», «Обычно 10–20 секунд»
  и полоса прогресса. Раньше секунды не показывались вовсе, а 20 с ожидания начинались
  только после ответа /wake (сервер держит его до 30 с) — всего до 50 с.
- Побудка уходит параллельно с опросом машины; ответила машина раньше — экран
  открывается сразу, не дожидаясь ответа /wake. Опрос во время подключения — раз в 1,5 с
  (было 3 с).
- Ошибка побудки показывается только если её ответ уже пришёл к концу отсчёта.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git push origin main
