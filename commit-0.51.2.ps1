# Коммит и пуш 0.51.2: Android 117 + iOS 119. Запуск: ! powershell -File "D:/PROJECT/leaphouse-remote-ios/commit-0.51.2.ps1"
Set-Location D:\PROJECT\leaphouse-remote-ios
git add -A
git commit -m @'
android 0.51.2 (117) + ios 0.51.2 (119): «Наши продукты» и контакты поддержки живут на устройстве

Разгрузка сервера: экран «Наши продукты» и «Помощь» показываются из кэша на диске
(в том числе без сети), с сервером сверяемся не чаще раза в сутки и с If-None-Match
(304 — только отметка о сверке).
- Наши продукты: список по языку — файлом (как было), иконки теперь качаются один
  раз и лежат файлами (evon-product-icons/), сверяются вместе со списком. «Потянуть
  вниз» — сверка сразу.
- Контакты поддержки: новый SupportStore (Android/iOS) — support.json на диске,
  ETag и отметка о сверке; раньше контакты тянулись при каждом запуске приложения.
  /api/v1/agent/support читается сырым телом с If-None-Match.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git push origin main
