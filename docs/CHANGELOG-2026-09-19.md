# LeapRemote — что сделано 19–20 сентября 2026

Клиенты: Android (`android/`, пакет `uz.electro.remote`) и iOS (`ElectroRemote/`, bundle `uz.electro.remote`).
Сервер: `/opt/electro` на 62.171.159.63 (`leapmotor.evon.uz`), ssh-хост `electro`. Версии обоих клиентов идут одной линией
(`android/app/build.gradle` и `project.yml`). Сборки iOS делает GitHub Actions (`.github/workflows/ios.yml`) — неподписанный
`.ipa` публикуется в релиз `latest`; Android собирается локально (JDK 17 в `D:\PROJECT\Tools\jdk17`,
`sh gradlew --no-daemon assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease`).

**Актуальная база телефона — ТОЛЬКО этот репозиторий.** `D:\PROJECT\Archive\electro-clients\phone-android` (0.38.19) устарел:
сборка оттуда встаёт поверх и «съедает» новые функции (проверено на горьком опыте — потребовалось `pm uninstall`).

## Хронология версий

| Версия | Коммит | Что |
|---|---|---|
| 0.39.1 (78) | `5c2032f`… | Отзывы из приложения с вложениями |
| 0.40.0 (82) | `5c2032f` | Бренд: эмблема владельца + шрифт Unbounded, зелёная иконка |
| 0.41.0 (84–94) | `7a7c182`…`d387c14`, iOS `d89442b`, `2ef3e2f`, `b391946` | Рендеры по модели и цвету, силуэты кресел, плитки одной высоты, «Выйти из аккаунта» на экране QR, iOS-паритет |
| 0.42.0 (96–98) | `789c721`, `978bda1`, `e65ed0c` | Защита входа (код + биометрия), предложение кода после логина, фикс тумблера климата |
| 0.43.0 (99) | `d4588e4` | «Обогрев всех стёкол», выбор темы авто/светлая/тёмная |

Отдельно (сервер, 19.09): пропущены `patch_*` той же даты — см. `OneDrive\Desktop\LeapRemote-документация\README.md`.

## 1. Отзывы из приложения

**Сервер** (`electro_remote/api/routes_feedback.py`, `infrastructure/feedback.py`, таблицы `feedback`, `feedback_attachments`):
- `POST /api/v1/feedback` `{kind: bug|idea|other, text, vehicle_id?, app: phone|head, app_version?}` → 201 `{id}`; аудит `FEEDBACK_SENT`.
- `POST /api/v1/feedback/{id}/attachments?name=` — **сырой файл телом**, тип в `Content-Type` (png/jpeg/webp/mp4/webm/3gp/mov,
  ≤ 40 МБ, ≤ 3 на отзыв). Multipart нарочно не используется — на сервере нет python-multipart. Файлы в `/opt/electro/data/feedback/<fid>/`.
  nginx: отдельный `location ~ ^/api/v1/feedback/[^/]+/attachments$ { client_max_body_size 42m; }` (остальное — 1 МБ).
- `GET /api/v1/master/feedback`, `POST …/{id}/status {new|done}`, `DELETE …/{id}`, `GET …/{fid}/attachments/{aid}` (только с Bearer).
- Админка → вкладка **«Отзывы»**: счётчик новых, фильтр Новые/Все, «Разобрано», удалить; миниатюры картинок и ссылки на видео
  через `fetch` + blob URL (`<img src>` без Bearer не пройдёт). Следы скрытой учётки видит только скрытый зритель.
- Настройки `config.py`: `feedback_dir`, `feedback_attachment_max_mb=40`, `feedback_attachments_per_item=3`.
  Тесты `tests/test_feedback.py`; в `conftest.py` добавлен `feedback_dir=tmp`.
  Бэкапы `backups/code-feedback-20260919-230507`, `code-feedback2-20260919-232916`.

**Клиенты:** кнопка «Оставить отзыв» в диалоге «?» (только под аккаунтом; и на главной, и на экране «машина спит») и секция
«Отзыв» в Настройках. Диалог: чипсы Ошибка/Идея/Другое, текст, до 3 вложений через системный выбор фото/видео
(Android `PickMultipleVisualMedia`, iOS `PhotosPicker` — без разрешений на хранилище). Запись экрана делается штатной
шторкой телефона и прикладывается из галереи. Android: `ui/FeedbackUi.kt`, `CarViewModel.sendFeedback` читает файлы через
contentResolver; iOS: `UI/Screens/FeedbackSheet.swift`, `CloudClient.attachToFeedback` (URLSession.upload).

## 2. Бренд LeapRemote

- Figma «Electro Remote — Design System» → страница **«Brand · LeapRemote»** (id 109:2): знак-вариант «дуга + молния»,
  лок-апы, иконка 1024, доска 8 шрифтов. Сравнение шрифтов — `Desktop\LeapRemote-шрифты-и-лого.pdf`.
- Решение владельца: **знак — его эмблема** из `LeapRemote-play-icon-512.png` («L + машина + сигнал»), **шрифт — Unbounded**
  (ExtraBold «Leap» + Light «Remote»). Плашка знака — цвет кнопок темы (`Accent`), глиф — `OnAccent`.
- Android: `res/font/unbounded_*.ttf`, `theme/Type.kt` (`BrandFont`, `Display` = Unbounded Light), `ui/components/Brand.kt`
  (`BrandMark`/`BrandWordmark`/`BrandLockup`), `drawable-nodpi/brand_glyph.png` (вырезан из иконки, tint).
  Лаунчер: mipmap-* перегенерированы (зелёный `#2F7D14`, белая эмблема + слово), adaptive-фон `#2F7D14`.
- iOS: `Resources/Fonts/Unbounded-*.ttf` + `UIAppFonts`, `UI/Components/Brand.swift`, `brand_glyph` (template),
  `AppIcon` 1024 без альфы. Исходник иконки: `Desktop\LeapRemote-play-icon-green-1024.png`.
- ⚠️ В asset-каталоге iOS картинку класть в слот **1x** (`"scale": "1x"`); дублирование слота 3x ломает actool молча —
  плашка логотипа была пустой (`2ef3e2f`).

## 3. Рендеры машин по модели и цвету

- Источник: конфигуратор **leapmotor.uz** — `https://www.leapmotor.uz/configurator/<model>/exterior/<colour>.png`
  (1511×1080, прозрачный фон; нужен браузерный `User-Agent`). Цвета — из `interior-pairs/<model>-<colour>__…` на
  `/uz/models/<m>/configure`. Для несуществующих сочетаний сайт отдаёт рендер C10 — проверять md5 (так отсеяны
  C16 terra-grey и C01 pearl-white).
- Набор: C16 (5), C10 (5), C11 (5), C01 (4), B10 (4), A10 (1), D19 (1) — 25 рендеров. Обзор: `Desktop\LeapRemote-рендеры-машин.pdf`
  (скрипт `Desktop\LeapRemote-документация\make_fonts_pdf.py` — по образцу).
- Android: `drawable-nodpi/car_<model>_<colour>.webp` (800 px, ~40 КБ), `ui/components/CarArt.kt` (`image(model, paint)`,
  `paints(model)`). iOS: `car_<model>_<colour>.imageset` (PNG 800 px, палитра), `UI/Components/CarArt.swift`.
- Цвет кузова — локальная настройка телефона (`Settings.vehiclePaint(id)`, `CarViewModel.paint`), выбор кружками в
  Настройки → Мои машины под выбранной машиной; выбранный кружок крупнее, с кольцом и галочкой. Рендер лежит на светлой
  карточке темы; старые 25 тёмных рендеров C16 по состоянию дверей удалены (`heroRes`/`heroName` убраны) — двери показывает полоса статуса.

## 4. Кресла и плитки

- Силуэт кресла — из картинки владельца (`ChatGPT Image 20 сент. 2026 г., 00_59_08.png`): вырезан один экземпляр,
  фон → альфа, тонируется (`drawable-nodpi/seat_front.png`, iOS `seat_front` template). Используется на главной (2×2 в плитке
  «Сиденья») и в Климат → Сиденья (кресла 112 dp, без «▲ перёд», под креслом уровень тремя делениями + «2/3»/«выкл»).
- Плитки «Климат» и «Сиденья» на главной одной ширины и высоты (Android `IntrinsicSize.Max` + `fillMaxHeight`, iOS
  `fixedSize(horizontal:false, vertical:true)` + `maxHeight: .infinity`); подпись сидений короткая («Выключены», «Обогрев · 2»).

## 5. Защита входа (0.42.0)

- 4-значный код: хранится только SHA-256(соль + код), соль случайная; после 5 промахов пауза 30 с. Смена/отключение — через
  текущий код. Биометрия — надстройка над кодом (без кода не включить, код всегда запасной).
- Блокировка при запуске и при каждом возврате из фона (Android `ON_STOP`, iOS `scenePhase == .background`), только для вошедшего.
- После первого успешного входа один раз предлагается «Защитить вход?» («Не сейчас» запоминается — `lock_offer_declined`).
- Android: `security/AppLock.kt` (`androidx.biometric:biometric:1.1.0`, `MainActivity` теперь `FragmentActivity`),
  `ui/LockScreen.kt` (`LockScreen`, `PinPad`, `PinSetupDialog`), секция «Защита входа» в настройках.
- iOS: `Data/AppLock.swift` (CryptoKit + LocalAuthentication, `NSFaceIDUsageDescription`), `UI/Screens/LockScreen.swift`
  (`LockScreen`, `PinPad`, `PinSetupSheet`), `RootView` показывает `LockScreen` поверх ворот.
- Рисунок (pattern) не делался — при необходимости отдельный экран с сеткой 3×3.

## 6. Климат

- **Тумблер климата гас при работающем климате**: телефон и сервер считали «включён» только при `ac == "1"`, а голова на части
  машин отдаёт режим (2 = авто и т.п.). Теперь «включён» = любое ненулевое значение (`acOn()` на клиентах; сервер
  `providers/head/provider.py`: `climate_on=(_int(ac) or 0) != 0`, бэкап `backups/provider.py.bak-acflag`).
- **«Обогрев всех стёкол»** (бывш. «Обогрев стёкол»): вкл = зеркала + обдув лобового (2) + обогрев заднего; климат
  включается, уставка не трогается. Повторное нажатие гасит только эти три, климат остаётся. `Cmd.defogGlass` на обеих платформах.
  Если заднее стекло на реальной машине всё равно не греется — смотреть карту команд головы для конкретной модели
  (`c16-climate-agent/CommandDispatcher.kt`: C10 rear 2=вкл/1=выкл через `defrostR10`, C16-2025 `defrostV`).

## 7. Прочее

- Экран «Подключите машину» (QR): под «Сканировать QR машины» добавлена «Выйти из аккаунта» (`vm.logout()`).
- Оформление: Настройки → «Оформление» — Авто / Светлая / Тёмная, применяется сразу (Android `ThemePref` в `theme/Theme.kt`
  + `MainActivity` загружает при старте; iOS `@AppStorage("themeMode")` + `preferredColorScheme`). «Авто» = как в системе;
  на Samsung с расписанием ночного режима система может быть светлой при выбранном «Тёмный» — это не приложение.
- Аккаунты: ROOT/ADMIN управляют любой машиной парка **по роли**, не по привязке (`AccessPolicy.resolve_vehicle`), поэтому в
  карточке машины они не показываются. Yusuf (ADMIN) реально управлял машинами Sarvar-а и Joe.
- Тестовая C10-голова с VIN `00000000000000000` (serial 821d1ecb) не регистрируется: `CarStateReader.vin()` отбрасывает нули.
  Пока не решено (по просьбе владельца).

## Демо / проверка

Аккаунт App Review: `review@leapremote.app` (пароль в памяти сессии / у владельца), машина-симулятор `veh-c16-demo01`.
На Samsung SM-G960F (adb `27e48d88bc1c7ece`) стоит последняя сборка; на нём включён код входа — код знает владелец.
