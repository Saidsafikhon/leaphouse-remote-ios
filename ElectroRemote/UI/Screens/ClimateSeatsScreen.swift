import SwiftUI

/// Климат и сиденья одним экраном с двумя вкладками — по образцу GWM.
struct ClimateSeatsScreen: View {
    @Environment(\.palette) private var p
    let car: CarState
    let controls: [Int: String]
    let initialTab: HomeTab
    let send: (Int, String) -> Void
    let sendAll: ([VehicleCommand], String) -> Void
    let onClimateOn: (Int?) -> Void
    let onApplySeats: ([Int: Int], Int) -> Void
    let onClose: () -> Void

    private enum Tab { case climate, seats }
    @State private var tab: Tab? = nil

    var body: some View {
        let current = tab ?? (initialTab == .seats ? .seats : .climate)
        VStack(spacing: 0) {
            // шапка: две вкладки по центру и крестик справа
            ZStack {
                HStack(spacing: Space.x4) {
                    TopTab(text: "Климат", selected: current == .climate) { tab = .climate }
                    TopTab(text: "Сиденья", selected: current == .seats) { tab = .seats }
                }
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark").font(.system(size: 20, weight: .medium)).foregroundStyle(p.textSecondary)
                            .frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Закрыть")
                }
            }
            .padding(Space.x4)

            switch current {
            case .climate:
                ClimateTab(
                    car: car, controls: controls, send: send, sendAll: sendAll,
                    onToggle: {
                        if climateOn(controls) { sendAll(Cmd.climateOff(), "Выключить климат") } else { onClimateOn(nil) }
                    },
                    onOnWithTimer: { minutes in onClimateOn(minutes) }
                )
            case .seats:
                SeatsTab(controls: controls, onApply: onApplySeats)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
    }
}

// MARK: - вкладки шапки

private struct TopTab: View {
    @Environment(\.palette) private var p
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Text(text).font(.system(size: 20, weight: selected ? .bold : .regular))
                    .foregroundStyle(selected ? p.textPrimary : p.textMuted)
                RoundedRectangle(cornerRadius: 2).fill(selected ? p.accent : .clear).frame(width: 26, height: 3)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - КЛИМАТ

private struct ClimateTab: View {
    @Environment(\.palette) private var p
    let car: CarState
    let controls: [Int: String]
    let send: (Int, String) -> Void
    let sendAll: ([VehicleCommand], String) -> Void
    let onToggle: () -> Void
    let onOnWithTimer: (Int?) -> Void

    private let prefs = Settings.shared

    // Уставка выставляется локально и НЕ уходит на машину сразу — только при включении.
    @State private var setTemp: Int = 22
    @State private var runMin: Int = 15
    @State private var pickTime = false
    // Сцены и тумблеры-с-памятью: флаги держим сами (статусы на C10 ненадёжны).
    @State private var recircOn = false
    @State private var frontDefrostOn = false
    @State private var maxCoolOn = false
    @State private var maxHeatOn = false
    @State private var defogOn = false
    // Тумблер климата реагирует мгновенно: желаемое состояние показываем сразу.
    @State private var optimisticOn: Bool? = nil
    @State private var optimisticTask: Task<Void, Never>? = nil
    @State private var loaded = false

    var body: some View {
        let on = climateOn(controls)
        let temp = controls[Cmd.TEMP_L].flatMap { Double($0) }.map { Int($0) } ?? 22
        let mirrorOn = ctlOn(controls, Cmd.MIRROR_HEAT)
        let shownOn = optimisticOn ?? on

        ScrollView {
            VStack(spacing: Space.x3) {
                // карточка функций: сцены и тумблеры
                VStack(alignment: .leading, spacing: Space.x3) {
                    Text("Функции").font(ElectroType.title).foregroundStyle(p.textPrimary)
                    HStack(spacing: Space.x3) {
                        ClimateButton(label: "Макс. охлаждение", icon: "snowflake", active: maxCoolOn) {
                            let next = !maxCoolOn
                            maxCoolOn = next
                            if next { maxHeatOn = false; prefs.setBool("sceneMaxHeat", false) }
                            prefs.setBool("sceneMaxCool", next)
                            sendAll(Cmd.maxCool(next), "Макс. охлаждение")
                        }
                        ClimateButton(label: "Макс. обогрев", icon: "flame", active: maxHeatOn) {
                            let next = !maxHeatOn
                            maxHeatOn = next
                            if next { maxCoolOn = false; prefs.setBool("sceneMaxCool", false) }
                            prefs.setBool("sceneMaxHeat", next)
                            sendAll(Cmd.maxHeat(next), "Макс. обогрев")
                        }
                        ClimateButton(label: "Обогрев стёкол", icon: "windshield.rear.and.heat.waves", active: defogOn) {
                            let next = !defogOn
                            defogOn = next
                            prefs.setBool("sceneDefog", next)
                            sendAll(Cmd.defogGlass(next), "Обогрев стёкол")
                        }
                    }
                    HStack(spacing: Space.x3) {
                        ClimateButton(label: "Циркуляция", icon: "arrow.triangle.2.circlepath", active: recircOn) {
                            let next = !recircOn
                            recircOn = next
                            prefs.setBool("sceneRecirc", next)
                            sendAll(Cmd.recircScene(next), "Циркуляция")
                        }
                        ClimateButton(label: "Обогрев зеркал", icon: "mirror.side.left.and.heat.waves", active: mirrorOn) {
                            send(Cmd.MIRROR_HEAT, mirrorOn ? "0" : "1")
                        }
                        ClimateButton(label: "Обдув лобового", icon: "windshield.front.and.heat.waves", active: frontDefrostOn) {
                            let next = !frontDefrostOn
                            frontDefrostOn = next
                            prefs.setBool("sceneDefrostF", next)
                            send(Cmd.DEFROST_FRONT, next ? "2" : "0")
                        }
                    }
                }
                .padding(Space.x4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(p.surface)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))

                // нижняя карточка: заголовок с тумблером, полоска температуры, время работы
                VStack(alignment: .leading, spacing: Space.x4) {
                    HStack {
                        Text("Температура (°C)").font(ElectroType.title).foregroundStyle(p.textPrimary)
                        Spacer()
                        ElectroToggle(isOn: shownOn) { desired in
                            setOptimistic(desired)
                            if on { onToggle() } else { turnOn(nil) }
                        }
                    }
                    // Полоска LO 18° … HI 32°: тянется пальцем, уставка применится при включении.
                    TemperatureBar(temp: shownOn ? temp : setTemp, cabin: car.cabinTemp, locked: shownOn) { v in setTemp = v }
                    Text(shownOn
                         ? "Чтобы изменить температуру, выключите климат — так бережётся компрессор."
                         : "Выбранная температура применится при включении климата.")
                        .font(ElectroType.caption).foregroundStyle(p.textMuted)
                    Divider().background(p.outline)
                    Button { pickTime.toggle() } label: {
                        Text("Настроить время работы").font(ElectroType.body).foregroundStyle(p.accent)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    if pickTime {
                        NumberCarousel(value: runMin, min: 5, max: 60, step: 5) { v in
                            runMin = v; prefs.setInt("climateRunMin", v)
                        }
                        if !shownOn {
                            ElectroButton(text: "Включить на \(runMin) мин") { turnOn(runMin) }
                        }
                    }
                }
                .padding(Space.x4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(p.surface)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
            }
            .padding(.horizontal, Space.x4)
            .padding(.vertical, Space.x4)
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            setTemp = temp
            runMin = prefs.int("climateRunMin", default: 15)
            recircOn = prefs.bool("sceneRecirc")
            frontDefrostOn = prefs.bool("sceneDefrostF")
            maxCoolOn = prefs.bool("sceneMaxCool")
            maxHeatOn = prefs.bool("sceneMaxHeat")
            defogOn = prefs.bool("sceneDefog")
        }
        .onChange(of: on) { _, real in
            // подмену снимаем, как только реальное состояние совпало
            if optimisticOn == real { optimisticOn = nil; optimisticTask?.cancel() }
        }
    }

    private func setOptimistic(_ desired: Bool) {
        optimisticOn = desired
        optimisticTask?.cancel()
        optimisticTask = Task {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if !Task.isCancelled { optimisticOn = nil }
        }
    }

    /// Включить климат. Без таймера — одной пачкой (температура + AC): один результат.
    /// С таймером — прежним путём (нужен серверный run_minutes).
    private func turnOn(_ minutes: Int?) {
        if minutes == nil {
            sendAll([
                VehicleCommand(type: Cmd.TEMP_L, value: String(setTemp)),
                VehicleCommand(type: Cmd.TEMP_R, value: String(setTemp)),
                VehicleCommand(type: Cmd.AC, value: "1"),
            ], "Включить климат")
        } else {
            send(Cmd.TEMP_L, String(setTemp)); send(Cmd.TEMP_R, String(setTemp))
            onOnWithTimer(minutes)
        }
    }
}

/// Тумблер «включён», если из /state пришло непустое ненулевое значение.
private func ctlOn(_ controls: [Int: String], _ type: Int) -> Bool {
    guard let v = controls[type] else { return false }
    return !v.isEmpty && v != "0" && v != "0.0"
}

/// Плитка-функция климата: иконка + подпись, подсвечивается когда включена.
private struct ClimateButton: View {
    @Environment(\.palette) private var p
    let label: String
    let icon: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        let bg = active ? p.accentSoft : p.surfaceElevated
        let fg = active ? p.accent : p.textSecondary
        Button(action: action) {
            VStack(spacing: Space.x1) {
                Image(systemName: icon).font(.system(size: 20)).foregroundStyle(fg).frame(height: 24)
                Text(label).font(ElectroType.caption).foregroundStyle(fg)
                    .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.8)
            }
            .padding(.horizontal, Space.x2)
            .frame(maxWidth: .infinity)
            .frame(height: 88)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// Карусель-число: значения листаются горизонтально, центральное — выбранное,
/// при остановке лента примагничивается к центру.
struct NumberCarousel: View {
    @Environment(\.palette) private var p
    let value: Int
    let min: Int
    let max: Int
    var step: Int = 1
    var accent: Color? = nil
    let onChange: (Int) -> Void

    @State private var position: Int? = nil
    private let itemW: CGFloat = 64

    var body: some View {
        let values = Array(stride(from: min, through: max, by: step))
        let tint = accent ?? p.accent
        GeometryReader { geo in
            let sidePad = Swift.max(0, (geo.size.width - itemW) / 2)
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous).fill(tint.opacity(0.12)).frame(width: itemW, height: 54)
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 0) {
                        ForEach(values, id: \.self) { v in
                            let selected = (position ?? value) == v
                            Text("\(v)")
                                .font(.system(size: selected ? 32 : 18, weight: selected ? .bold : .regular))
                                .foregroundStyle(selected ? tint : p.textMuted)
                                .frame(width: itemW, height: 72)
                                .id(v)
                        }
                    }
                    .scrollTargetLayout()
                }
                .contentMargins(.horizontal, sidePad, for: .scrollContent)
                .scrollTargetBehavior(.viewAligned)
                .scrollPosition(id: $position, anchor: .center)
            }
        }
        .frame(height: 72)
        .onAppear { position = value }
        .onChange(of: position) { _, v in
            if let v, v != value { onChange(v) }
        }
    }
}

// MARK: - СИДЕНЬЯ

private enum SeatMode { case heat, vent }

private struct SeatsTab: View {
    @Environment(\.palette) private var p
    let controls: [Int: String]
    let onApply: ([Int: Int], Int) -> Void

    private let prefs = Settings.shared
    @State private var mode: SeatMode = .heat
    @State private var runMin: Int = 5
    @State private var selected: Int? = nil
    // уровни держим локально и применяем по «Активировать» — как у GWM
    @State private var heat: [Int] = [0, 0, 0, 0]
    @State private var vent: [Int] = [0, 0, 0, 0]
    @State private var loaded = false

    private let names = ["Водитель", "Пассажир", "Заднее левое", "Заднее правое"]

    var body: some View {
        let levels = mode == .heat ? heat : vent
        let accent = mode == .heat ? p.warn : p.info
        let icon = mode == .heat ? "flame" : "wind"

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: Space.x2)
                // подвкладки Подогрев/Вентиляция пилюлей
                HStack(spacing: 0) {
                    PillTab(text: "Подогрев", selected: mode == .heat) { mode = .heat; selected = nil }
                    PillTab(text: "Вентиляция", selected: mode == .vent) { mode = .vent; selected = nil }
                }
                .padding(4)
                .background(p.surfaceElevated)
                .clipShape(Capsule())
                .frame(maxWidth: .infinity)

                Spacer().frame(height: Space.x4)

                // схема салона: два ряда кресел
                VStack(spacing: Space.x3) {
                    HStack(spacing: Space.x4) {
                        SeatTile(name: names[0], level: levels[0], selected: selected == 0, accent: accent, icon: icon) { toggleSelect(0) }
                        SeatTile(name: names[1], level: levels[1], selected: selected == 1, accent: accent, icon: icon) { toggleSelect(1) }
                    }
                    HStack(spacing: Space.x4) {
                        SeatTile(name: names[2], level: levels[2], selected: selected == 2, accent: accent, icon: icon) { toggleSelect(2) }
                        SeatTile(name: names[3], level: levels[3], selected: selected == 3, accent: accent, icon: icon) { toggleSelect(3) }
                    }
                }
                .padding(Space.x4)
                .frame(maxWidth: .infinity)
                .background(p.surface)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))

                // Ползунок уровня выбранного места — под карточкой, всегда виден.
                if let s = selected {
                    Spacer().frame(height: Space.x3)
                    Text("Уровень — \(names[s])").font(ElectroType.caption).foregroundStyle(p.textMuted)
                    LevelSlider(level: levels[s], accent: accent) { setLevel(s, $0) }
                }

                Spacer().frame(height: Space.x4)
                Text("Время работы (мин.)").font(ElectroType.body).foregroundStyle(p.textPrimary)
                Spacer().frame(height: Space.x2)
                NumberCarousel(value: runMin, min: 1, max: 60, accent: accent) { v in
                    runMin = v; prefs.setInt("seatRunMin", v)
                }
                Spacer().frame(height: Space.x4)
                ElectroButton(text: "Активировать") {
                    // Профиль = обогрев И обдув всех мест + таймер.
                    var map: [Int: Int] = [:]
                    for (i, t) in Cmd.SEAT_HEATS.enumerated() { map[t] = heat[i] }
                    for (i, t) in Cmd.SEAT_VENTS.enumerated() { map[t] = vent[i] }
                    onApply(map, runMin)
                }
                Spacer().frame(height: Space.x4)
            }
            .padding(.horizontal, Space.x4)
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            runMin = prefs.int("seatRunMin", default: 5)
            syncFromControls()
        }
        .onChange(of: controls) { _, _ in syncFromControls() }
    }

    private func syncFromControls() {
        heat = Cmd.SEAT_HEATS.map { seatLevel(controls, $0) }
        vent = Cmd.SEAT_VENTS.map { seatLevel(controls, $0) }
    }

    private func toggleSelect(_ i: Int) { selected = selected == i ? nil : i }

    /// Обогрев и обдув одного места взаимоисключающие.
    private func setLevel(_ seat: Int, _ value: Int) {
        if mode == .heat {
            heat[seat] = value
            if value > 0 { vent[seat] = 0 }
        } else {
            vent[seat] = value
            if value > 0 { heat[seat] = 0 }
        }
    }
}

private struct PillTab: View {
    @Environment(\.palette) private var p
    let text: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text).font(ElectroType.body)
                .foregroundStyle(selected ? p.textPrimary : p.textSecondary)
                .padding(.horizontal, Space.x5).padding(.vertical, Space.x2)
                .background(selected ? p.surface : .clear)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Кресло: подпись сверху и плитка с иконкой и уровнем в центре спинки.
private struct SeatTile: View {
    @Environment(\.palette) private var p
    let name: String
    let level: Int
    let selected: Bool
    let accent: Color
    let icon: String
    let action: () -> Void

    var body: some View {
        let active = level > 0
        VStack(spacing: 6) {
            Text(name).font(ElectroType.caption).foregroundStyle(p.textSecondary).lineLimit(1)
            // кресло — силуэт с эскиза владельца: выбранное обведено акцентом,
            // активное окрашено в цвет режима; уровень — строкой под креслом
            Button(action: action) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(selected ? accent : .clear, lineWidth: 2)
                    Image("seat_front").resizable().renderingMode(.template).scaledToFit()
                        .foregroundStyle(active ? accent.opacity(0.55) : p.textDisabled.opacity(0.55))
                        .padding(.vertical, 8)
                    ZStack {
                        Circle().fill(active ? accent : p.surface)
                        if !active { Circle().stroke(p.outline, lineWidth: 1) }
                        Image(systemName: icon).font(.system(size: 14)).foregroundStyle(active ? p.onAccent : p.textMuted)
                    }
                    .frame(width: 30, height: 30).offset(y: -6)
                }
                .frame(height: 112)
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            HStack(spacing: 3) {
                ForEach(0..<SEAT_LEVELS, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 3).fill(i < level ? accent : p.surfaceElevated).frame(width: 14, height: 5)
                }
                Spacer().frame(width: 4)
                Text(active ? "\(level)/\(SEAT_LEVELS)" : "выкл").font(ElectroType.caption).foregroundStyle(active ? accent : p.textMuted)
            }
        }
    }
}

/// Слайдер уровня 0..3 с подписью N/3.
private struct LevelSlider: View {
    @Environment(\.palette) private var p
    let level: Int
    let accent: Color
    let onChange: (Int) -> Void

    var body: some View {
        HStack(spacing: Space.x3) {
            Slider(
                value: Binding(get: { Double(level) }, set: { onChange(Int($0.rounded())) }),
                in: 0...Double(SEAT_LEVELS), step: 1
            )
            .tint(accent)
            Text("\(level)/\(SEAT_LEVELS)").font(ElectroType.title).foregroundStyle(p.textPrimary)
        }
        .padding(.horizontal, Space.x4).padding(.vertical, Space.x2)
        .background(p.surfaceElevated)
        .clipShape(Capsule())
        .padding(.top, Space.x2)
    }
}

// MARK: - полоска температуры (LO 18° … HI 32°)

/// Полоска уставки: градиент от морозно-синего к огненно-оранжевому, тянется
/// пальцем. Заблокировано (климат включён) — тусклая и жесты не ловятся.
private struct TemperatureBar: View {
    @Environment(\.palette) private var p
    let temp: Int
    let cabin: Double?
    var locked = false
    let onSet: (Int) -> Void

    @State private var dragTemp: Int? = nil

    var body: some View {
        let shown = dragTemp ?? temp
        let frac = Double(shown - Cmd.TEMP_MIN) / Double(Cmd.TEMP_MAX - Cmd.TEMP_MIN)
        let alpha = locked ? 0.4 : 1.0
        VStack(spacing: 0) {
            Text(Cmd.tempLabel(shown)).font(ElectroType.display).foregroundStyle(p.textPrimary)
            Text(cabin.map { "В салоне \($0.asTemp)°" } ?? "В салоне —").font(ElectroType.caption).foregroundStyle(p.textMuted)
            Spacer().frame(height: Space.x3)
            GeometryReader { geo in
                let w = geo.size.width
                let h: CGFloat = 28
                let r = h / 2
                let knobX = Swift.min(Swift.max(frac * w, r), w - r)
                ZStack(alignment: .leading) {
                    Capsule().fill(LinearGradient(colors: [p.info.opacity(alpha), p.warn.opacity(alpha)], startPoint: .leading, endPoint: .trailing))
                        .frame(height: h)
                    Circle().fill(p.background).frame(width: h + 6, height: h + 6).offset(x: knobX - r - 3)
                    Circle().fill(locked ? p.textDisabled : p.textPrimary).frame(width: h - 4, height: h - 4).offset(x: knobX - r + 2)
                }
                .frame(width: w, height: 48)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { g in if !locked { dragTemp = tempAtX(g.location.x, w) } }
                        .onEnded { g in
                            if !locked { onSet(tempAtX(g.location.x, w)) }
                            dragTemp = nil
                        }
                )
            }
            .frame(height: 48)
            Spacer().frame(height: Space.x1)
            HStack {
                Text("LO · 18°").font(ElectroType.label).foregroundStyle(p.info.opacity(alpha))
                Spacer()
                Text("32° · HI").font(ElectroType.label).foregroundStyle(p.warn.opacity(alpha))
            }
        }
    }

    private func tempAtX(_ x: CGFloat, _ width: CGFloat) -> Int {
        let frac = Swift.min(Swift.max(x / Swift.max(width, 1), 0), 1)
        return Cmd.TEMP_MIN + Int((frac * CGFloat(Cmd.TEMP_MAX - Cmd.TEMP_MIN)).rounded())
    }
}
