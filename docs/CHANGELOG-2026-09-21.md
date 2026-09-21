# LeapRemote — журнал изменений 21.09.2026 (0.45.0 → 0.46.0, сборка 103)

Продолжение `CHANGELOG-2026-09-19.md`. Всё ниже сделано за одну сессию, коммиты `d009d0a` … `2fdb4a8`.

## 1. 3D-машина на главной (Android)

- Модели — настоящие 3D-модели Leapmotor из головного устройства: Unity-бандлы `assets/Android/car_c16|c10|c11|c01`
  из `CarControl.apk` (Unity 2020.3.27f1). Экспорт в GLB — `docs/3d/export_car.py` (UnityPy + trimesh): идём по
  иерархии префаба `<MODEL>_Car`, вершины в мировых координатах, Unity→glTF (x→−x, обратный обход, v→1−v), материалы
  упрощены до PBR (кузов `M_Paint` / у C01 `M_CarPaint`, стекло BLEND 0.55, фонари emissive, шины/номер текстуры ≤512 px).
  `docs/3d/preview.py` — софт-рендер для проверки без GPU.
- Хостинг: `https://leapmotor.evon.uz/models/{c16,c10,c11,c01}.glb` + `env_ibl.ktx` (IBL из Filament),
  на сервере `/opt/electro/electro_remote/web/models/`, отдаётся StaticFiles. Перевыпуск GLB → поднять
  `CarModels.VERSION` в `CarModelView.kt` (старый кеш на телефонах сотрётся).
- Рендер: Filament **1.56.0** (1.76 требует compileSdk 37), `ui/components/CarModelView.kt`:
  - `CarModels` — скачивание в `cacheDir/models/v<N>/`, `prefetch()` с экрана подключения;
  - `FilamentCarView` — SurfaceView, прозрачный swapchain (`UiHelper.isOpaque=false`, `setZOrderMediaOverlay`,
    `View.BlendMode.TRANSLUCENT`), камера `orbitHomePosition(-1.45, 0.45, -2.2)` на цель `(0,0,-4)` (куда
    `transformToUnitCube` ставит модель) — ракурс как у статичных рендеров; рендер только «пока что-то меняется»
    (загрузка, жест, перекраска), иначе Choreographer спит;
  - жест: палец забирается на DOWN (иначе Compose-скролл перехватит), горизонталь — yaw вокруг центра модели,
    вертикаль — наклон вокруг «правой» оси камеры, ограничен `[-20°, +52°]`;
  - перекраска: `MaterialInstance.name in ("M_Paint","M_CarPaint")` → `baseColorFactor` из swatch выбранного цвета;
  - `CarViewCache` — один живой view на процесс: ModelViewer вешает на view слушатель detach и по нему убивает
    Engine — слушатель перехватывается (`addOnAttachStateChangeListener` override), поэтому уход в настройки и
    возврат не перезагружают модель. **Не** вызывать `engine.destroy()` самим — двойной destroy → `flushAndWait()
    after shutdown` → SIGABRT (так и падало при входе в настройки).
  - на эмуляторе (SwiftShader) Filament вешает систему — `CarModels.supported` отдаёт статичную картинку.
- `PhoneControlScreen.Hero`: статичный рендер до первого реально отрисованного кадра (`onFirstFrame`), потом 3D.
- iOS-версия 3D **не сделана** (план: SceneKit/GLTFKit2 с теми же GLB).

## 2. Цвета кузова — по официальным сайтам

Источники и что выяснилось:
- **leapmotor.uz** (`/configurator/<model>/exterior/<colour>.png`, нужен браузерный UA, редирект на www) — при
  неизвестном цвете отдаёт картинку **другой модели**, сверять по md5. Несколько подписей врут: C10/B10 «terra-grey»
  = Tundra Grey, B10 «glacier-blue» = Galaxy Silver, C01 «walden-green» = светло-голубой, C01 «glacier-blue» = C11.
- **leapmotor.net** (Leapmotor International, Nuxt SPA) — контент из CMS `website-adminapi.leapmotor-international.com/api`
  (`/web/qryCountryList`, `/web/qryTemplateByWeb?countryRemarks=33&languageId=38` (UK), `/web/qryTemplateDetailByWeb?id=`),
  картинки только через CDN `lpwebsite-prod-s3cdn.leapmotor-international.com` (S3 напрямую — 403). В линейке лишь
  T03/C10/B10/B05/B03X. Официальные палитры: C10 — Light/Pearly White, Glazed Green, Canopy/Tundra Grey, Metallic Black;
  B10 — Starry Night Blue, Dawn Purple, Tundra Grey, Metallic Black, Galaxy Silver, Pearly White.
- **cn.leapmotor.com** (Vue SPA, чанки `js/<n>.chunk.<hash>.js` из `runtime.js`): галереи цветов по моделям
  (`Leapmotor-Chinese-web/<MODEL>-NEW/PC/screenN-M.jpg` — студийные фото на цветном фоне, вырезаны `rembg`
  isnet-general-use + крупнейшая компонента); C01 — 360-вьюер `aroundshowc01.leapmotor.com/images/img/<colour>/<1..36>.png`
  (прозрачные кадры, кадр 32 = наш ракурс, диски отдельным слоем `lungu1/32.png` — компоновать!).
- Итог в приложении (`CarArt.kt` / `CarArt.swift`, ассеты `car_<model>_<colour>` webp 800 px / PNG 1x):
  C16 5, C10 5, C11 8 (+облачное золото, сосновый серый, бежевый иней = бывший «жемчужный» кремовый),
  C01 8 (7 официальных с 360 + голубой), **B01 — новая модель**, 8 цветов (добавлен и в `carModels()` админки),
  B10 7 (+звёздная ночь, рассветный фиолетовый, сакура), A10 6, D19 4. Кружки цветов переносятся на 2-ю строку
  (`FlowRow` / `LazyVGrid`). Подпись цвета — `Paint.label` считается при чтении (перевод).

## 3. Локализация RU / EN / UZ

- Таблица — `docs/i18n/strings.json`: ключ = русский текст из кода с плейсхолдерами `{0}`, `{1}`; `en` и `uz`.
  `python docs/i18n/sync.py` копирует в `android/app/src/main/assets/i18n.json` и `ElectroRemote/Resources/i18n.json`
  и проверяет покрытие ключей из кода. Русский — ключи, перевода нет → показывается ключ.
- Код: все русские литералы обёрнуты `S("…", args)` (Kotlin, `uz.electro.remote.i18n`) / `L("…", args)` (Swift,
  `Data/I18n.swift`); шаблоны `"Обогрев $heat"` → `S("Обогрев {0}", heat)`. Делалось скриптом (`i18n_rewrite.py`,
  в scratchpad) — при добавлении новых строк писать сразу через `S()`/`L()` и дописывать перевод в таблицу.
- Константы, которые вычисляются один раз (`DAY_LABELS`, `Paint.label`, статические списки), переведены в геттеры,
  иначе смена языка их не обновляет. Голосовые шаблоны `VoiceScreen` остаются русскими (распознавание русское).
- Переключение: Android `Lang.current` — Compose-state (всё перерисовывается); iOS `Lang.shared` ObservableObject +
  `.id(lang.code)` на RootView. По умолчанию — язык системы, если он ru/en/uz, иначе русский; хранится в prefs `lang`.
- UI: выпадающий список (`LangPicker`: globe + «RU ▾» на логине и экране подключения, полное название в
  Настройки → «Язык»). На логине добавлены «Помощь» (контакты поддержки) и колокольчик новостей: сервер получил
  `GET /api/v1/news/public` без авторизации (только `audience=all`; `infrastructure/news.py: list_public`,
  `api/routes_news.py: news_public`, бэкапы `backups/*.bak-public`), клиенты до входа грузят его (`loadNews()`).
- Узбекский: «аккаунт» переводим как *akkaunt* (Akkauntdan chiqish, Akkauntni o‘chirish), не *hisob* — просьба владельца.

## 4. Новости: карточки по макету Figma и автолента

- Макет: https://www.figma.com/design/T0jd7XOiSDyZJhuHOTZKuO/Untitled?node-id=3-2747 — карточка с обложкой, тег
  источника/раздела на картинке, бейдж NEW, подпись раздела + дата, заголовок, «Подробнее →». Фильтры: Все /
  Обновления / Инструкции / События / Новости / Важное. Реализовано в `NewsScreen.kt` (Coil для картинок) и
  `SecondaryScreens.swift` (`NewsCard`, AsyncImage). Тап по карточке — полный текст, «Подробнее»/«Открыть источник» —
  ссылка в браузере.
- Сервер: у `announcements` новые колонки `image_url, link, source, category (update|guide|event|news|''), lang,
  external_id` (ALTER TABLE вручную — `create_all` колонки не добавляет). `NewsItem`/`AnnouncementResponse` отдают их;
  админка → «Новая рассылка» получила раздел, картинку и ссылку. Патч: `lp/electro/docs/server-patches-2026-09/patch_newsfeed.py`.
- **Автоновости** — `services/newsfeed.py` (`NewsFeed`, запускается из `Container.startup`, настройки
  `ELECTRO_NEWSFEED_ENABLED/INTERVAL_SECONDS/KEEP`, по умолчанию раз в 30 мин, хранить 150): RSS CnEVPost и
  CarNewsChina (Китай, электромобили — берём всё, до 15 за проход) и Spot.uz / Gazeta.uz / Podrobno.uz (по ключевым
  словам: электромоб*, зарядн*, Leapmotor, BYD, Zeekr, гибрид…). Дедуп по ссылке (`external_id`), обложка —
  media:content/enclosure/первая картинка, иначе og:image статьи; `created_at` = дата публикации; push не шлём.
  Новости с `source != null` подрезаются до `keep` самых свежих. Английские источники показываются как есть (без перевода).
- Дата в карточках форматируется локалью текущего языка приложения.

## 5. Магазин аксессуаров

- Кнопка «сумка» рядом с колокольчиком на логине, экране подключения и главной. Витрина 2 в ряд с фото, ценой
  (и зачёркнутой старой — бейдж «-N%»), фильтр по категориям (аксессуары / оборудование / уход / тюнинг / другое);
  товары для своей модели идут первыми. Карточка товара: фото, цена, «Подходит: C16, C10», описание, ссылка на сайт,
  «Заказать» → форма (количество, телефон, комментарий) → заявка на сервер; до входа витрина видна, заказ — нет.
  Оплаты в приложении нет — менеджер перезванивает. `ShopScreen.kt` / `ShopScreen.swift`.
- Сервер: таблицы `shop_products` и `shop_orders` (`infrastructure/orm.py`, репозиторий `infrastructure/shop.py`,
  роуты `api/routes_shop.py`): `GET /api/v1/shop/products` (публично, только active), `POST /api/v1/shop/orders`
  (CurrentUser), `GET /shop/orders/my`; админка: `GET/POST/PATCH/DELETE /master/shop/products`, `GET /master/shop/orders`,
  `POST /master/shop/orders/{id}/status` (new | contacted | done | cancelled). Аудит `SHOP_ORDER`, `SHOP_PRODUCT_*`.
  Патч `lp/electro/docs/server-patches-2026-09/patch_shop.py`.
- Админка → вкладка «Магазин»: форма товара (название, описание, цена, старая цена, категория, модели, картинка, ссылка,
  порядок, «показывать»), список товаров с правкой/удалением, список заявок со статусом и телефоном (tel:), счётчик
  новых заявок на вкладке, удаление заявок. Для примера заведены 3 товара — заменить своими.
- В приложении — поиск по товарам (название, описание, модели). В админке — «Скачать XLSX» / «Загрузить XLSX»:
  `GET /master/shop/products.xlsx` (openpyxl, файл = шаблон) и `POST /master/shop/products/import` (сырое тело;
  строки с ID обновляются, без ID — создаются; ответ: created/updated/skipped/errors). Колонки:
  id, title, description, price, old_price, currency, category, models, image_url, link, sort, active.

## 6. Версии и сборки

- Android 0.46.0 (103) и iOS 0.46.0 (103). APK на рабочем столе `LeapRemote-0.46.0-103-android.apk`; iOS собирает CI
  (`.github/workflows/ios.yml`, релиз `latest`).
- Samsung SM-G960F (adb `27e48d88bc1c7ece`) — стоит 0.46.0.
