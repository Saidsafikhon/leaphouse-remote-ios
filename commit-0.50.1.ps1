# Коммит и пуш 0.50.1: Android 113 + iOS 115. Запуск: ! powershell -File "D:/PROJECT/leaphouse-remote-ios/commit-0.50.1.ps1"
# После пуша ios.yml соберёт 0.50.1 (115) и зальёт в TestFlight.
Set-Location D:\PROJECT\leaphouse-remote-ios
git add -A
git commit -m @'
android 0.50.1 (113) + ios 0.50.1 (115): экран загрузки по макету Figma; Google Play — подпись, бандл, символы

Экран загрузки в тёмной и светлой теме (Figma «loading-dark/light-theme»): иконка
в трёх пульсирующих кольцах с зелёным свечением, LeapRemote шрифтом Outfit,
«EV CONTROL SYSTEM», «Заряжается...» и полоса прогресса; один проход ~2.6 с при
холодном старте, затем плавный уход. Android — ui/SplashScreen.kt, iOS —
SplashView.swift (оверлей в RootView). Фон системной заставки под цвета макета.

Google Play: playSig = настоящий ключ подписи Google (3A24…, снят с установленного
из Play APK) + прежние; ABI-сплиты выключаются для bundleRelease (AGP 8.11);
ndk.debugSymbolLevel SYMBOL_TABLE для отчётов о сбоях.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
'@
git push origin main
