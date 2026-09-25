# Коммит и пуш 0.50.0: Android 109 + iOS 114. Запуск: ! powershell -File "D:/PROJECT/leaphouse-remote-ios/commit-0.50.0.ps1"
# После пуша ios.yml соберёт 0.50.0 (114) и зальёт в TestFlight.
Set-Location D:\PROJECT\leaphouse-remote-ios
git add -A
git commit -m @'
android 0.50.0 (109) + ios 0.50.0 (114): новости на диске + ETag/304 + фоновая подкачка; Mapbox; значок багажника

Экономия трафика: лента новостей и уведомлений хранится на телефоне
(NewsStore на обеих платформах), при открытии показывается с диска, сервер
опрашивается не чаще раза в 30 минут, по жесту обновления или по push,
с If-None-Match — неизменная лента стоит 304 без тела. Фоновая подкачка:
Android — WorkManager (раз в 6 ч при сети + сразу по data-push), iOS —
BGAppRefreshTask + push content-available (UIBackgroundModes fetch,
remote-notification). Личная лента стирается при выходе.

Android: карта — Mapbox Maps SDK 11.12 (ключ в android/mapbox.properties,
в git не попадает) вместо osmdroid; стиль карты идёт за темой приложения.
«Маршрут» всегда показывает выбор из всех навигаторов на телефоне
(известные — со своей ссылкой маршрута, остальные — обработчики geo:).
Списки новостей и магазина ленивые (LazyColumn / LazyVStack) — экран
открывается сразу; раздача файлом теперь release-сборкой (minify, без
отладочных проверок Compose). «Удалить аккаунт» — в самом низу настроек.
Магазин — через market.evon.uz (витрина сайта + тот же API, ETag/304).
Значок багажника — машина с поднятой пятой дверью (свой глиф на Android
и сайте, car.side.rear.open на iOS).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
git push origin main
