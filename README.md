# LeapRemote — iOS

Порт Android-клиента `phone-android` 0.38.19 (репозиторий leojkee/electro) на SwiftUI один в один: те же
экраны, тот же backend (`https://leapmotor.evon.uz`), та же карта команд.

## Что внутри

```
ElectroRemote/
  App/          ElectroRemoteApp + RootView (ворота: вход → парк → привязка → пульт)
  Data/         Settings (UserDefaults), Api (URLSession + Codable), CarState,
                Commands (карта команд C16), CarRepository (+ Pairing)
  Model/        CarViewModel (@MainActor ObservableObject) — подключение, опрос,
                команды, оптимистичное состояние, профиль сидений
  UI/Theme      палитра (dark/light как в Figma), типографика, отступы
  UI/Components плитки, чипы, кнопки, бейджи, тосты, диалог, каркасы экранов
  UI/Screens    Login/Register/Forgot/ParkGate/Pair(+QR), Connect, PhoneControl
                (главная), ClimateSeats, Scenes/Schedule/Voice/Map, Settings
  Resources/    Assets.xcassets: AppIcon, car_<model>_<colour>, seat_front, brand_glyph; Fonts/Unbounded-*.ttf
project.yml     xcodegen — .xcodeproj в git не хранится
```

Отличия от Android, продиктованные платформой:
- карта — MapKit, «Маршрут» открывает Apple Maps (на Android была статичная
  картинка Яндекса и chooser приложений);
- QR — AVFoundation, разрешение камеры спрашивается на месте;
- иконки — SF Symbols (в т.ч. штатные `windshield.*.and.heat.waves`,
  `mirror.side.left.and.heat.waves`, `carseat.right`);
- тема: Авто (за системой) / Светлая / Тёмная — выбор в Настройках → «Оформление» (с 0.43.0, на обеих платформах).

Подробный журнал изменений 19–20.09.2026 (отзывы, бренд, рендеры, защита входа, климат):
`docs/CHANGELOG-2026-09-19.md`.
Журнал 21.09.2026 (3D-машина, цвета по официальным сайтам, локализация RU/EN/UZ, новости-карточки и
автолента, магазин аксессуаров, размер APK): `docs/CHANGELOG-2026-09-21.md`. Переводы — `docs/i18n/strings.json`
(`python docs/i18n/sync.py` после правок), экспорт 3D — `docs/3d/`.

Не переносилось намеренно: `SignatureGuard` (anti-tamper по подписи APK —
на iOS подпись проверяет система) и мёртвый код (`SeatsScreen.kt`,
`SeatControls.kt`, `SeatCell` — в 0.38.19 не вызываются).

## Сборка

Локально (нужен Mac с Xcode 15+):

```sh
brew install xcodegen
xcodegen generate
open ElectroRemote.xcodeproj
```

В CI — `.github/workflows/ios.yml` (macOS-раннер): собирает под симулятор
(проверка компиляции) и под устройство **без подписи**, артефакт
`ElectroRemote-<версия>-<код>-unsigned.ipa`.

## Установка на iPhone

Пока нет аккаунта Apple Developer:
- **Sideloadly / AltStore** на компьютере: подписывают неподписанный .ipa
  бесплатным Apple ID, приложение живёт 7 дней, потом переподписать (AltStore
  делает это сам, пока компьютер в той же сети).
- Или Xcode на Mac с бесплатным Apple ID — тот же 7-дневный сертификат.

С аккаунтом Apple Developer ($99/год): в workflow добавить подпись
(сертификат + provisioning profile в secrets) и выгрузку в TestFlight.

## Версия

`MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` в `project.yml` — та же линия,
что `versionName` / `versionCode` Android (0.38.19 / 75).
