# Коммит правки UI-теста скриншотов + перезапуск сборки 0.50.2 (116) в TestFlight.
# Запуск: ! powershell -File "D:/PROJECT/leaphouse-remote-ios/commit-uitest-fix.ps1"
Set-Location D:\PROJECT\leaphouse-remote-ios
git add ElectroRemoteUITests/ScreenshotTests.swift commit-uitest-fix.ps1
git commit -m @'
ios: скриншот-тест ждёт «Климат и сиденья» — заголовка «Панель быстрого доступа» на новой главной нет; повтор сборки 116 в TestFlight

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git push origin main
