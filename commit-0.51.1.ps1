# Коммит и пуш 0.51.1: Android 116 + iOS 118. Запуск: ! powershell -File "D:/PROJECT/leaphouse-remote-ios/commit-0.51.1.ps1"
Set-Location D:\PROJECT\leaphouse-remote-ios
git add -A
git commit -m @'
android 0.51.1 (116) + ios 0.51.1 (118): «Помощь» показывает все номера поддержки

Сервер с 05.10.2026 отдаёт до трёх номеров (`phones`, `phone2`, `phone3` в
/api/v1/agent/support). Экран помощи на входе выводит строку «Телефон» на каждый
номер, главный первым, по тапу — звонок. Старый сервер без `phones` даёт тот же
результат по полю `phone`.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git push origin main
