import SwiftUI

/// Вкладки главного экрана.
enum HomeTab: Equatable { case car, climate, seats, map, settings, scenes, schedule, voice, news, shop }

/// Главный экран по обложке: марка и модель → фото машины → полоса состояния →
/// быстрые действия → климат и сиденья → входы в разделы.
struct PhoneControlScreen: View {
    @Environment(\.palette) private var p
    @Environment(\.scenePhase) private var scenePhase
    @ObservedObject var vm: CarViewModel

    @State private var tab: HomeTab = .car
    @State private var toast: CmdEvent? = nil
    @State private var toastTask: Task<Void, Never>? = nil

    var body: some View {
        if vm.connectStatus.phase != .connected {
            ConnectScreen(vm: vm, status: vm.connectStatus)
        } else {
            connected
        }
    }

    private var connected: some View {
        let controls = vm.controls
        let pending = vm.pending
        let stateOf: (Int) -> ControlState = { type in
            if pending.contains(type) { return .pending }
            if controls[type] == "1" { return .active }
            return .normal
        }
        return ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                Group {
                    switch tab {
                    case .map:
                        MapScreen(loc: vm.car.location)
                    case .settings:
                        SettingsScreen(vm: vm) { tab = .car }
                    case .climate, .seats:
                        ClimateSeatsScreen(
                            car: vm.car, controls: controls, initialTab: tab,
                            send: { t, v in vm.send(t, v) },
                            sendAll: { cmds, label in vm.send(cmds, label: label) },
                            climateProfile: vm.climateProfile(),
                            onClimateStart: { vm.climateStart() },
                            onSaveClimate: { temp, timer in vm.saveClimateProfile(temp: temp, timerMin: timer) },
                            seatProfileTimer: vm.seatPresetTimer(),
                            onApplySeats: { levels, timer in vm.applySeats(levels: levels, timerMin: timer, sendNow: seatsAnyOn(controls)) },
                            onClose: { tab = .car }
                        )
                    case .scenes:
                        ScenesScreen(
                            scenes: vm.scenes,
                            onRun: { vm.runScene($0) },
                            onDelete: { vm.deleteScene($0.template_id) },
                            onCreate: { name, steps in vm.createScene(name: name, steps: steps) },
                            onBack: { tab = .car }
                        )
                    case .schedule:
                        ScheduleScreen(
                            schedules: vm.schedules,
                            onCreate: { vm.createSchedule($0) },
                            onToggle: { s, on in vm.setScheduleEnabled(s.schedule_id, on) },
                            onDelete: { vm.deleteSchedule($0.schedule_id) },
                            onBack: { tab = .car }
                        )
                    case .voice:
                        VoiceScreen(intents: vm.voiceIntents, onRun: { vm.runVoice($0) }, onBack: { tab = .car })
                    case .shop:
                        ShopScreen(products: vm.products, loggedIn: true, model: vm.selectedVehicle?.model,
                                   onOrder: { id, qty, phone, comment, done in vm.order(id, qty: qty, phone: phone, comment: comment, done: done) },
                                   onBack: { tab = .car }, onRefresh: { vm.loadProducts() })
                    case .news:
                        NewsScreen(items: vm.news, isRead: { vm.isRead($0) }, onRead: { vm.markRead($0) },
                                   onReadAll: { vm.markAllRead() }, onBack: { tab = .car }, onRefresh: { vm.loadNews(force: true) })
                .onAppear { vm.loadNews() }
                    case .car:
                        HomeTabView(
                            car: vm.car,
                            model: vm.selectedVehicle?.model ?? "C16",
                            support: vm.support,
                            caps: vm.capabilities,
                            paint: vm.paint,
                            controls: controls,
                            stateOf: stateOf,
                            seatPresetSet: vm.seatPresetSet(),
                            unreadNews: vm.unreadNews,
                            onDisconnect: { vm.disconnect() },
                            onSendAll: { cmds, label in vm.send(cmds, label: label) },
                            onRefresh: { vm.refreshNow() },
                            onOpen: { tab = $0 },
                            onClimateStart: { vm.climateStart() },
                            onSeatsOn: { vm.seatsOn() },
                            onSeatsOff: { vm.seatsOff() },
                            feedbackVM: vm
                        )
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                BottomNav(tab: tab) { tab = $0 }
            }

            // результат команды: один снекбар на действие, вид зависит от исхода
            if let e = toast {
                ElectroToast(kind: badgeKind(e.kind), title: e.title, message: e.message)
                    .padding(Space.x4)
                    .padding(.bottom, 64)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .background(p.background)
        .animation(.easeOut(duration: 0.2), value: toast?.id)
        .onChange(of: vm.openNewsRequest) { _, _ in tab = .news }   // тап по push
        .onChange(of: vm.event) { _, e in
            guard let e else { return }
            toast = e
            toastTask?.cancel()
            toastTask = Task {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                if !Task.isCancelled { toast = nil }
            }
        }
        // опрос идёт только пока экран на переднем плане
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { vm.startPolling() } else { vm.stopPolling() }
        }
        .onAppear { vm.startPolling() }
        .onDisappear { vm.stopPolling() }
    }

    private func badgeKind(_ k: EventKind) -> BadgeKind {
        switch k {
        case .success: return .success
        case .failed: return .failed
        case .unsupported: return .unconfirmed
        case .offline: return .offline
        }
    }
}

// MARK: - главная вкладка

/// Подтверждение действия, которое снимает машину с охраны.
private struct HomeConfirm {
    let title: String
    let msg: String
    let action: String
    let cmds: [VehicleCommand]
    let label: String
}

/// Набор кнопок по умолчанию, пока сервер не ответил про возможности.
private let DEFAULT_QUICK = ["lock", "trunk", "climate", "windows"]

private struct HomeTabView: View {
    @Environment(\.palette) private var p
    let car: CarState
    let model: String
    let support: SupportDto?
    let caps: CapabilitiesDto?
    let paint: String?
    let controls: [Int: String]
    let stateOf: (Int) -> ControlState
    let seatPresetSet: Bool
    let unreadNews: Int
    let onDisconnect: () -> Void
    let onSendAll: ([VehicleCommand], String) -> Void
    let onRefresh: () -> Void
    let onOpen: (HomeTab) -> Void
    let onClimateStart: () -> Void
    let onSeatsOn: () -> Void
    let onSeatsOff: () -> Void
    var feedbackVM: CarViewModel? = nil

    @State private var dialog: DialogSpec? = nil
    @State private var showHelp = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.x5) {
                HeaderLockup(car: car, model: model, unread: unreadNews, onHelp: { showHelp = true },
                             onNews: { onOpen(.news) }, onShop: { onOpen(.shop) }, onRefresh: onRefresh, onDisconnect: onDisconnect)
                Hero(model: model, paint: paint, car: car)
                CarStatusStrip(car: car)
                // замок и кузов: что заперто, что открыто — живой статус с головы 4.61+
                if car.online && car.notInPark { GearLockBanner() }
                if car.online && (car.bodyKnown || car.locked != nil) { BodyStatusCard(car: car) }

                QuickRow(
                    controls: controls, stateOf: stateOf, onSendAll: onSendAll,
                    quick: caps?.quick ?? DEFAULT_QUICK,
                    onClimateOn: onClimateStart,
                    onClimateOff: { onSendAll(Cmd.climateOff(), L("Выключить климат")) },
                    onConfirm: { c in
                        dialog = DialogSpec(
                            icon: "exclamationmark.triangle", accent: p.warn, title: c.title, message: c.msg,
                            confirmText: c.action, onConfirm: { onSendAll(c.cmds, c.label) }
                        )
                    }
                )

                // Климат и сиденья — двумя карточками в ряд, как на панели GWM.
                let hasClimate = caps?.quick.contains("climate") ?? true
                let hasSeats = caps?.groups.contains("seats") ?? true
                if hasClimate || hasSeats {
                    // заголовок: край карточек под кнопками читается как продолжение экрана
                    SectionTitle(text: L("Климат и сиденья"))
                    // одинаковая ширина (по половине) и высота (по большей плитке)
                    HStack(alignment: .top, spacing: Space.x3) {
                        if hasClimate {
                            GwmClimateCard(car: car, controls: controls, onToggle: { toggleClimate() }, onOpen: { onOpen(.climate) })
                        }
                        if hasSeats {
                            GwmSeatsCard(controls: controls, hasPreset: seatPresetSet, onOn: onSeatsOn, onOff: onSeatsOff, onOpen: { onOpen(.seats) })
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }

                EntryRow(caps: caps, onOpen: onOpen)
            }
            .padding(.horizontal, Space.x5)
            .padding(.top, Space.x5)
            .padding(.bottom, Space.x4)
        }
        .background(p.background)
        .electroDialog($dialog)
        .sheet(isPresented: $showHelp) { HelpSheet(support: support, feedbackVM: feedbackVM) }
    }

    /// Тумблер климата: выключить или включить по сохранённому профилю
    /// (температура и таймер с экрана климата). Выключение — только климат.
    private func toggleClimate() {
        if climateOn(controls) {
            onSendAll(Cmd.climateOff(), L("Выключить климат"))
        } else {
            onClimateStart()
        }
    }
}

/// Климат считаем включённым только по сигналу машины, а не по эху нажатия.
/// Климат включён, если сигнал «ac» с машины не ноль: голова на части машин
/// отдаёт режим (2 — авто и т.п.), и строгое «== 1» гасило тумблер при работающем климате.
func climateOn(_ controls: [Int: String]) -> Bool { acOn(controls[Cmd.AC]) }

func acOn(_ v: String?) -> Bool {
    guard let s = v?.trimmingCharacters(in: .whitespaces).lowercased(), !s.isEmpty, s != "false", s != "null" else { return false }
    if let f = Double(s) { return f != 0 }
    return s == "true" || s == "on"
}

/// Уровень 0..3 по сигналу с машины или по последней команде.
func seatLevel(_ controls: [Int: String], _ type: Int) -> Int {
    guard let v = controls[type], let f = Double(v) else { return 0 }
    return min(max(Int(f), 0), SEAT_LEVELS)
}

let SEAT_LEVELS = 3

func seatSummary(_ controls: [Int: String]) -> String {
    let heat = Cmd.SEAT_HEATS.filter { seatLevel(controls, $0) > 0 }.count
    let vent = Cmd.SEAT_VENTS.filter { seatLevel(controls, $0) > 0 }.count
    // коротко — подпись живёт в узкой плитке на главной, ей нельзя переноситься
    switch (heat, vent) {
    case (0, 0): return L("Выключены")
    case (_, 0): return L("Обогрев · {0}", heat)
    case (0, _): return L("Обдув · {0}", vent)
    default: return L("Обогрев {0} · обдув {1}", heat, vent)
    }
}

func seatsAnyOn(_ controls: [Int: String]) -> Bool {
    Cmd.SEAT_TYPES.contains { seatLevel(controls, $0) > 0 }
}

/// Открытым считаем стекло, опущенное больше чем на пять процентов.
func windowsOpen(_ controls: [Int: String]) -> Bool {
    Cmd.WINDOWS.contains { (controls[$0].flatMap { Double($0) } ?? 0) > 5 }
}

private let DEFAULT_TEMP = 22

// MARK: - блоки главного экрана

/// Марка мелко, модель крупно и легко, под ней — когда обновлялись данные.
private struct HeaderLockup: View {
    @Environment(\.palette) private var p
    let car: CarState
    let model: String
    let unread: Int
    let onHelp: () -> Void
    let onNews: () -> Void
    let onShop: () -> Void
    let onRefresh: () -> Void
    let onDisconnect: () -> Void

    var body: some View {
        // Логотип строкой; модель и справа один ряд одинаковых круглых кнопок
        // (отключение последней, красным значком) — на строке логотипа четыре не помещались.
        VStack(alignment: .leading, spacing: Space.x1) {
            BrandLockup(markSize: 22)
            HStack(spacing: Space.x2) {
                Text(model).font(ElectroType.display).foregroundStyle(p.textPrimary).lineLimit(1)
                Spacer(minLength: 0)
                ShopFab(action: onShop)
                NewsBell(unread: unread, action: onNews)
                HelpFab(action: onHelp)
                DisconnectFab(blocked: car.bodyKnown && car.doorOrTrunkOpen, action: onDisconnect)
            }
            Button(action: onRefresh) {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.clockwise").font(.system(size: 12)).foregroundStyle(p.textMuted)
                    Text(updatedText(car)).font(ElectroType.caption).foregroundStyle(p.textMuted)
                }
            }
            .buttonStyle(.plain)
        }
    }
}

/// Колокольчик «Новости» с точкой непрочитанных.
struct NewsBell: View {
    @Environment(\.palette) private var p
    let unread: Int
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "bell").font(.system(size: 18, weight: .medium)).foregroundStyle(unread > 0 ? p.accent : p.textSecondary)
                    .frame(width: 44, height: 44)
                    .background(p.surfaceElevated)
                    .clipShape(Circle())
                if unread > 0 {
                    Text(unread > 9 ? "9+" : "\(unread)").font(.system(size: 10, weight: .bold)).foregroundStyle(p.onAccent)
                        .padding(.horizontal, 5).frame(height: 16)
                        .background(p.accent).clipShape(Capsule())
                        .offset(x: 4, y: -2)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Новости"))
    }
}

/// «Отключиться»: отпускает машину в сон и закрывает сеанс. Круглая, как соседние
/// кнопки шапки, но с красным значком и обводкой — видно, что действие «опасное»,
/// и при этом взгляд не уходит с кнопок управления.
private struct DisconnectFab: View {
    @Environment(\.palette) private var p
    /// Открыта дверь или багажник — серая; нажатие показывает ошибку с перечнем.
    var blocked = false
    let action: () -> Void
    var body: some View {
        let tint = blocked ? p.textDisabled : p.danger
        Button(action: action) {
            Image(systemName: "power").font(.system(size: 18, weight: .medium)).foregroundStyle(tint)
                .frame(width: 44, height: 44)
                .background(p.surfaceElevated)
                .clipShape(Circle())
                .overlay(Circle().stroke(tint.opacity(0.6), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Отключиться"))
    }
}

private func updatedText(_ car: CarState) -> String {
    if car.updatedAt == 0 { return L("Обновление…") }
    if car.link == .none { return L("Нет данных с машины") }
    let f = DateFormatter(); f.dateFormat = "HH:mm"
    return L("Обновлено ") + f.string(from: Date(timeIntervalSince1970: Double(car.updatedAt) / 1000))
}

/// Рендер — студийная вырезка модели в цвете кузова; состояние дверей
/// показывает полоса статуса ниже.
/// 3D-машина (крутится пальцем), пока модель не загрузилась — студийный рендер.
private struct Hero: View {
    let model: String
    let paint: String?
    let car: CarState
    @State private var ready = false
    /// «3D» или «картинка» — выбор человека в настройках
    @AppStorage("carView") private var carView = "3d"
    var body: some View {
        // что открыто — 3D-модель открывает эти детали на петлях
        let pose = BodyPose(doorFL: car.doors.frontLeft, doorFR: car.doors.frontRight,
                            doorRL: car.doors.rearLeft, doorRR: car.doors.rearRight,
                            trunk: car.trunkOpen == true, hood: car.hoodOpen)
        // в «картинке» открытое — готовым рендером (tools/render_open); нет его — 3D
        let openArt = carView != "3d" && pose.anyOpen ? CarArt.openImage(model, paint, pose) : nil
        let show3d = carView == "3d" || (pose.anyOpen && openArt == nil)
        let swatch = CarArt.paints(model).first { $0.code == paint }?.swatch ?? CarArt.paints(model).first?.swatch ?? Color(hex: 0xE9EAEC)
        ZStack {
            if let openArt, !show3d {
                Image(uiImage: openArt)
                    .resizable().scaledToFit()
                    .frame(maxWidth: .infinity).frame(height: 160)
            } else if !ready || !show3d {
                Image(CarArt.imageName(model, paint))
                    .resizable().scaledToFit()
                    .frame(maxWidth: .infinity).frame(height: 160)
            }
            // В режиме скриншотов (-screenshots) 3D не грузим: на сайте и в сторе машина
            // должна быть одинаковой плоской картинкой в обеих темах, а не зависеть от
            // того, успела ли скачаться модель.
            if !Demo.enabled && show3d {
                CarModelView(model: model, paint: swatch, body3d: pose, onReady: { ready = $0 })
                    .opacity(ready ? 1 : 0)
            }
        }
        // 170: машина без пустого поля вокруг, восемь кнопок и начало карточек — без прокрутки
        .frame(maxWidth: .infinity).frame(height: 170)
    }
}

/// Одна полоса вместо четырёх карточек: охрана слева, запас и заряд справа.
/// Полоса под машиной: запас хода и заряд. Охрану и замки на главной не показываем —
/// по просьбе владельца; остаются только предупреждения: нет связи и открыта дверь/багажник/капот.
private struct CarStatusStrip: View {
    @Environment(\.palette) private var p
    let car: CarState

    var body: some View {
        let open = car.doors.anyOpen || car.trunkOpen == true || car.hoodOpen
        var metrics: [Metric] = []
        if let r = car.rangeKm { metrics.append(Metric(value: "\(r)", unit: L("км"), fraction: Double(r) / 500)) }
        if let s = car.soc { metrics.append(Metric(value: "\(s)", unit: "%", fraction: Double(s) / 100)) }
        return Group {
            if car.link == .none {
                StatusStrip(icon: "icloud.slash", title: L("Нет связи с машиной"), subtitle: L("Показаны последние данные"),
                            accent: p.textMuted, accentTint: p.surfaceElevated, metrics: metrics)
            } else if open || car.charging {
                // открыто и заряжается одновременно — показываем обе полосы (просьба 08.10.2026)
                VStack(spacing: Space.x3) {
                    if open {
                        StatusStrip(icon: "exclamationmark.triangle", title: L("Автомобиль открыт"), subtitle: openDetail(car),
                                    accent: p.danger, accentTint: p.dangerTint, metrics: metrics)
                    }
                    if car.charging {
                        StatusStrip(icon: "bolt.fill", title: L("Заряжается"), subtitle: chargeEta(car),
                                    accent: p.ok, accentTint: p.okTint, metrics: open ? [] : metrics)
                    }
                }
            } else if !metrics.isEmpty {
                MetricsStrip(metrics: [
                    car.rangeKm.map { Metric(value: "\($0)", unit: L("км"), caption: L("запас хода")) },
                    car.soc.map { Metric(value: "\($0)", unit: "%", caption: L("заряд")) },
                    // температуры — только те, что машина отдаёт (на C16 2025 салона нет)
                    car.cabinTemp.map { Metric(value: $0.asTemp, unit: "°", caption: L("в салоне")) },
                    car.outsideTemp.map { Metric(value: $0.asTemp, unit: "°", caption: L("на улице")) },
                ].compactMap { $0 })
            }
        }
    }
}

/// «До полной: 1 ч 25 мин» — время от машины; нет времени — так и говорим.
private func chargeEta(_ car: CarState) -> String {
    guard let m = car.chargeMinutes else { return L("Время до полной зарядки уточняется") }
    let h = m / 60, min = m % 60
    let t: String
    if h > 0 && min > 0 { t = L("{0} ч {1} мин", h, min) }
    else if h > 0 { t = L("{0} ч", h) }
    else { t = L("{0} мин", min) }
    return L("До полной: {0}", t)
}

private func openDetail(_ car: CarState) -> String {
    let s = car.openParts().joined(separator: ", ")
    return s.isEmpty ? L("Проверьте автомобиль") : s.prefix(1).uppercased() + s.dropFirst()
}

/// Четыре действия, ради которых открывают приложение: замки, багажник, климат, окна.
/// У каждого две отдельные кнопки: сверху «открыть/включить», под ней «закрыть/выключить».
/// Один переключатель слал не ту команду, когда машина не успевала отдать статус.
/// Подсветки состояния нет; крутилка — только на нажатой кнопке, пока команда идёт в машину.
/// Кнопки главного экрана срабатывают только после удержания (просьба 08.10.2026):
/// случайное касание не откроет машину. Удержание заменяет и прежние диалоги
/// «Открыть двери? / багажник?» — двойное подтверждение.
private let homeHoldSeconds: Double = 0.7

private struct QuickRow: View {
    @Environment(\.palette) private var p
    let controls: [Int: String]
    let stateOf: (Int) -> ControlState
    let onSendAll: ([VehicleCommand], String) -> Void
    let quick: [String]
    let onClimateOn: () -> Void
    let onClimateOff: () -> Void
    let onConfirm: (HomeConfirm) -> Void

    var body: some View {
        let keys = quick.filter { ["lock", "trunk", "climate", "windows"].contains($0) }

        // По четыре столбца: верхний ряд — открыть, под ним — закрыть.
        VStack(spacing: Space.x2) {
            ForEach(Array(stride(from: 0, to: keys.count, by: 4)), id: \.self) { start in
                let group = Array(keys[start..<min(start + 4, keys.count)])
                ForEach([true, false], id: \.self) { top in
                    HStack(spacing: Space.x2) {
                        ForEach(group, id: \.self) { key in
                            tile(key, top: top)
                        }
                        ForEach(0..<(4 - group.count), id: \.self) { _ in
                            Color.clear.frame(maxWidth: .infinity).frame(height: ControlSize.tileCompact)
                        }
                    }
                }
            }
        }
    }

    /// Пока команда в пути, крутилка только на нажатой кнопке пары. Какая нажата —
    /// видно по эху команды в controls: «0» — закрыть/выключить, иначе — открыть.
    private func state(_ type: Int, open: Bool) -> ControlState {
        guard stateOf(type) == .pending else { return .normal }
        let sentOpen = controls[type].map { $0.trimmingCharacters(in: .whitespaces) != "0" } ?? false
        return sentOpen == open ? .pending : .normal
    }

    @ViewBuilder
    private func tile(_ key: String, top: Bool) -> some View {
        switch (key, top) {
        case ("lock", true):
            ControlTile(label: L("Открыть двери"), icon: "lock.open", state: state(Cmd.LOCK, open: true), compact: true, iconTint: p.accent, holdSeconds: homeHoldSeconds) {
                onSendAll([VehicleCommand(type: Cmd.LOCK, value: "1")], L("Открыть двери"))
            }
        case ("lock", false):
            ControlTile(label: L("Закрыть двери"), icon: "lock", state: state(Cmd.LOCK, open: false), compact: true, holdSeconds: homeHoldSeconds) {
                onSendAll([VehicleCommand(type: Cmd.LOCK, value: "0")], L("Закрыть двери"))
            }
        case ("trunk", true):
            ControlTile(label: L("Открыть багажник"), icon: Sym.trunk, state: state(Cmd.TRUNK, open: true), compact: true, iconTint: p.accent, holdSeconds: homeHoldSeconds) {
                onSendAll([VehicleCommand(type: Cmd.TRUNK, value: "1")], L("Открыть багажник"))
            }
        case ("trunk", false):
            ControlTile(label: L("Закрыть багажник"), icon: Sym.trunkClosed, state: state(Cmd.TRUNK, open: false), compact: true, holdSeconds: homeHoldSeconds) {
                onSendAll([VehicleCommand(type: Cmd.TRUNK, value: "0")], L("Закрыть багажник"))
            }
        case ("climate", true):
            ControlTile(label: L("Включить климат"), icon: "snowflake", state: state(Cmd.AC, open: true), compact: true, iconTint: p.accent, holdSeconds: homeHoldSeconds, action: onClimateOn)
        case ("climate", false):
            ControlTile(label: L("Выключить климат"), icon: "power", state: state(Cmd.AC, open: false), compact: true, holdSeconds: homeHoldSeconds, action: onClimateOff)
        case ("windows", true):
            ControlTile(label: L("Открыть окна"), icon: Sym.windowOpen, state: state(Cmd.WINDOW_FL, open: true), compact: true, iconTint: p.accent, holdSeconds: homeHoldSeconds) {
                onSendAll(Cmd.WINDOWS.map { VehicleCommand(type: $0, value: "100") }, L("Открыть все окна"))
            }
        case ("windows", false):
            ControlTile(label: L("Закрыть окна"), icon: Sym.windowClosed, state: state(Cmd.WINDOW_FL, open: false), compact: true, holdSeconds: homeHoldSeconds) {
                onSendAll(Cmd.WINDOWS.map { VehicleCommand(type: $0, value: "0") }, L("Закрыть все окна"))
            }
        default:
            EmptyView()
        }
    }
}

/// Входы в разделы под панелью — по возможностям машины.
private struct EntryRow: View {
    let caps: CapabilitiesDto?
    let onOpen: (HomeTab) -> Void

    var body: some View {
        var entries: [(HomeTab, String, String)] = []
        if caps?.scenes ?? true { entries.append((.scenes, L("Мои сцены"), "square.grid.2x2")) }
        if caps?.climate_schedule ?? true { entries.append((.schedule, L("Расписание"), "clock")) }
        if caps?.voice ?? true { entries.append((.voice, L("Голос"), "waveform")) }
        return Group {
            if !entries.isEmpty {
                HStack(spacing: Space.x2) {
                    ForEach(entries, id: \.1) { e in
                        ControlTile(label: e.1, icon: e.2) { onOpen(e.0) }
                    }
                    ForEach(0..<(3 - entries.count), id: \.self) { _ in
                        Color.clear.frame(maxWidth: .infinity).frame(height: ControlSize.tile)
                    }
                }
            }
        }
    }
}

/// Карточка климата в стиле GWM: заголовок с тумблером справа и крупная уставка.
private struct GwmClimateCard: View {
    @Environment(\.palette) private var p
    let car: CarState
    let controls: [Int: String]
    let onToggle: () -> Void
    let onOpen: () -> Void

    var body: some View {
        let on = climateOn(controls)
        let temp = controls[Cmd.TEMP_L].flatMap { Double($0) }.map { Int($0) } ?? DEFAULT_TEMP
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(L("Климат")).font(ElectroType.body).foregroundStyle(p.textPrimary)
                Spacer()
                ElectroToggle(isOn: on) { _ in onToggle() }
            }
            Spacer(minLength: Space.x3)
            // Как у «Сидений»: блок значков той же высоты (66 = два ряда кресел)
            // и одна строка подписи — карточки рядом одинаковые по размеру и виду.
            HStack(spacing: Space.x2) {
                Image(systemName: "snowflake").font(.system(size: 26, weight: .regular))
                    .foregroundStyle(on ? p.accent : p.textDisabled)
                Text(Cmd.tempLabel(temp)).font(ElectroType.title).foregroundStyle(on ? p.textPrimary : p.textMuted)
            }
            .frame(height: 66)
            Spacer().frame(height: Space.x2)
            Text(car.cabinTemp.map { L("в салоне {0}°", $0.asTemp) } ?? (on ? L("включён") : L("выключен")))
                .font(ElectroType.caption).foregroundStyle(p.textMuted).lineLimit(1)
        }
        .padding(Space.x4)
        .frame(maxWidth: .infinity, minHeight: 132, maxHeight: .infinity, alignment: .leading)
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
    }
}

/// Карточка сидений в стиле GWM: тумблер-мастер и четыре кресла сеткой.
private struct GwmSeatsCard: View {
    @Environment(\.palette) private var p
    let controls: [Int: String]
    let hasPreset: Bool
    let onOn: () -> Void
    let onOff: () -> Void
    let onOpen: () -> Void

    var body: some View {
        let anyOn = seatsAnyOn(controls)
        let heatOn = Cmd.SEAT_HEATS.contains { seatLevel(controls, $0) > 0 }
        let ventOn = Cmd.SEAT_VENTS.contains { seatLevel(controls, $0) > 0 }
        let accent: Color = heatOn ? p.warn : (ventOn ? p.info : p.accent)
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(L("Сиденья")).font(ElectroType.body).foregroundStyle(p.textPrimary)
                Spacer()
                // Вкл — по сохранённому профилю, выкл — гасит все места.
                ElectroToggle(isOn: anyOn, enabled: anyOn || hasPreset, accent: accent) { on in on ? onOn() : onOff() }
            }
            Spacer(minLength: Space.x3)
            SeatGlyphs(controls: controls)
            Spacer().frame(height: Space.x2)
            Text(seatSummary(controls)).font(ElectroType.caption).foregroundStyle(p.textMuted).lineLimit(1)
        }
        .padding(Space.x4)
        .frame(maxWidth: .infinity, minHeight: 132, maxHeight: .infinity, alignment: .leading)
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
    }
}

/// Четыре кресла сеткой 2×2: горит то, что включено (обогрев или вентиляция).
private struct SeatGlyphs: View {
    @Environment(\.palette) private var p
    let controls: [Int: String]

    var body: some View {
        let seats: [(Int, Int)] = [
            (Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_VENT_DRIVER),
            (Cmd.SEAT_HEAT_PASSENGER, Cmd.SEAT_VENT_PASSENGER),
            (Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_VENT_REAR_L),
            (Cmd.SEAT_HEAT_REAR_R, Cmd.SEAT_VENT_REAR_R),
        ]
        VStack(alignment: .leading, spacing: 6) {
            ForEach(0..<2, id: \.self) { row in
                HStack(spacing: 6) {
                    ForEach(0..<2, id: \.self) { col in
                        let pair = seats[row * 2 + col]
                        let tint: Color = seatLevel(controls, pair.0) > 0 ? p.warn : (seatLevel(controls, pair.1) > 0 ? p.info : p.textDisabled)
                        // силуэт кресла (эскиз владельца), тонируется по состоянию
                        Image("seat_front").resizable().renderingMode(.template).scaledToFit()
                            .foregroundStyle(tint).frame(width: 20, height: 30)
                    }
                }
            }
        }
    }
}

// MARK: - нижняя навигация

private struct BottomNav: View {
    @Environment(\.palette) private var p
    let tab: HomeTab
    let onSelect: (HomeTab) -> Void

    var body: some View {
        HStack {
            navItem("house", L("Главная"), tab == .car) { onSelect(.car) }
            navItem("snowflake", L("Климат"), tab == .climate || tab == .seats) { onSelect(.climate) }
            navItem("map", L("Карта"), tab == .map) { onSelect(.map) }
            navItem("gearshape", L("Настройки"), tab == .settings) { onSelect(.settings) }
        }
        .padding(.top, Space.x3)
        .padding(.bottom, Space.x2)
        .frame(maxWidth: .infinity)
        .background(p.surface)
    }

    private func navItem(_ icon: String, _ label: String, _ active: Bool, action: @escaping () -> Void) -> some View {
        let c = active ? p.accent : p.textMuted
        return Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 20)).foregroundStyle(c)
                Text(label).font(ElectroType.unit).foregroundStyle(c)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }
}
