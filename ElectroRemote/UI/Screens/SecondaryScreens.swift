import SwiftUI
import MapKit

// MARK: - Сцены

/// Заготовки шагов: проверенные команды, из которых человек собирает сцену.
private struct Ingredient: Identifiable, Equatable {
    let title: String
    let step: SceneStepDto
    var id: String { title }
}

private let INGREDIENTS: [Ingredient] = [
    Ingredient(title: L("Включить климат"), step: SceneStepDto(type: Cmd.AC, value: "1", title: L("климат"))),
    Ingredient(title: L("Выключить климат"), step: SceneStepDto(type: Cmd.AC, value: "0", title: L("климат выкл"))),
    Ingredient(title: L("Тепло, 24°"), step: SceneStepDto(type: Cmd.TEMP_L, value: "24", title: L("температура 24°"))),
    Ingredient(title: L("Прохладно, 20°"), step: SceneStepDto(type: Cmd.TEMP_L, value: "20", title: L("температура 20°"))),
    Ingredient(title: L("Обдув на максимум"), step: SceneStepDto(type: Cmd.FAN, value: "7", title: L("обдув 7"))),
    Ingredient(title: L("Открыть окна"), step: SceneStepDto(type: Cmd.WINDOW_FL, value: "100", title: L("окна открыть"))),
    Ingredient(title: L("Закрыть окна"), step: SceneStepDto(type: Cmd.WINDOW_FL, value: "0", title: L("окна закрыть"))),
]

/// Пользовательские сцены: свой набор команд под одной кнопкой. Живут на сервере.
struct ScenesScreen: View {
    let scenes: [SceneTemplateDto]
    let onRun: (SceneTemplateDto) -> Void
    let onDelete: (SceneTemplateDto) -> Void
    let onCreate: (String, [SceneStepDto]) -> Void
    let onBack: () -> Void

    @State private var building = false

    var body: some View {
        ScreenScaffold(title: L("Мои сцены"), onBack: onBack) {
            if scenes.isEmpty && !building {
                EmptyNote(text: L("Сцен пока нет. Соберите свою — например «остудить к выходу»."))
            }
            ForEach(scenes) { scene in
                SceneRow(scene: scene, onRun: { onRun(scene) }, onDelete: { onDelete(scene) })
            }
            if building {
                SceneBuilder(onCancel: { building = false }, onSave: { name, steps in onCreate(name, steps); building = false })
            } else {
                ElectroButton(text: L("Новая сцена"), style: .secondary) { building = true }
            }
        }
    }
}

private struct SceneRow: View {
    @Environment(\.palette) private var p
    let scene: SceneTemplateDto
    let onRun: () -> Void
    let onDelete: () -> Void

    var body: some View {
        SectionCard(title: scene.name) {
            Text(scene.steps.map { $0.title.isEmpty ? L("тип {0}", $0.type) : $0.title }.joined(separator: " · "))
                .font(ElectroType.caption).foregroundStyle(p.textMuted)
            HStack(spacing: Space.x2) {
                ControlTile(label: L("Выполнить"), icon: "play", state: .active, action: onRun)
                ControlTile(label: L("Удалить"), icon: "trash", action: onDelete)
            }
        }
    }
}

private struct SceneBuilder: View {
    @Environment(\.palette) private var p
    let onCancel: () -> Void
    let onSave: (String, [SceneStepDto]) -> Void

    @State private var name = ""
    @State private var chosen: [Ingredient] = []

    var body: some View {
        SectionCard(title: L("Новая сцена")) {
            TextField(L("Название"), text: $name)
                .font(ElectroType.body).foregroundStyle(p.textPrimary).tint(p.accent)
                .padding(.horizontal, Space.x4).frame(height: 52)
                .background(p.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
            Text(L("Шаги")).font(ElectroType.caption).foregroundStyle(p.textSecondary)
            ForEach(INGREDIENTS) { ing in
                let on = chosen.contains(ing)
                Button {
                    if on { chosen.removeAll { $0 == ing } } else { chosen.append(ing) }
                } label: {
                    HStack {
                        Text(ing.title).font(ElectroType.body).foregroundStyle(on ? p.accent : p.textPrimary)
                        Spacer()
                        if on { Image(systemName: "plus").font(.system(size: 14)).foregroundStyle(p.accent) }
                    }
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            HStack(spacing: Space.x2) {
                ElectroButton(text: L("Отмена"), style: .ghost, action: onCancel)
                ElectroButton(
                    text: L("Сохранить"), style: .primary,
                    enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty && !chosen.isEmpty
                ) { onSave(name.trimmingCharacters(in: .whitespaces), chosen.map { $0.step }) }
            }
        }
    }
}

// MARK: - Расписание климата

private var DAY_LABELS: [String] { [L("Пн"), L("Вт"), L("Ср"), L("Чт"), L("Пт"), L("Сб"), L("Вс")] }  // геттер: язык может смениться

/// Расписание пред-климата: будильник живёт на сервере, тут — только редактор.
struct ScheduleScreen: View {
    let schedules: [ClimateScheduleDto]
    let onCreate: (ClimateScheduleRequest) -> Void
    let onToggle: (ClimateScheduleDto, Bool) -> Void
    let onDelete: (ClimateScheduleDto) -> Void
    let onBack: () -> Void

    @State private var adding = false

    var body: some View {
        ScreenScaffold(title: L("Климат по расписанию"), onBack: onBack) {
            if schedules.isEmpty && !adding {
                EmptyNote(text: L("Расписаний нет. Задайте время — сервер прогреет или остудит салон к нему."))
            }
            ForEach(schedules) { s in
                ScheduleRow(s: s, onToggle: { on in onToggle(s, on) }, onDelete: { onDelete(s) })
            }
            if adding {
                ScheduleEditor(onCancel: { adding = false }, onSave: { req in onCreate(req); adding = false })
            } else {
                ElectroButton(text: L("Новое расписание"), style: .secondary) { adding = true }
            }
        }
    }
}

private struct ScheduleRow: View {
    @Environment(\.palette) private var p
    let s: ClimateScheduleDto
    let onToggle: (Bool) -> Void
    let onDelete: () -> Void

    var body: some View {
        SectionCard(title: String(format: "%02d:%02d", s.hour, s.minute)) {
            HStack(spacing: Space.x2) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.weekdays.isEmpty ? L("Каждый день") : s.weekdays.sorted().map { DAY_LABELS[$0 % 7] }.joined(separator: " "))
                        .font(ElectroType.body).foregroundStyle(p.textPrimary)
                    Text(L("до {0}°", s.temp_c)).font(ElectroType.caption).foregroundStyle(p.textMuted)
                }
                Spacer()
                ElectroToggle(isOn: s.enabled, onChange: onToggle)
                Button(action: onDelete) {
                    Image(systemName: "trash").font(.system(size: 18)).foregroundStyle(p.textMuted).frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct ScheduleEditor: View {
    @Environment(\.palette) private var p
    let onCancel: () -> Void
    let onSave: (ClimateScheduleRequest) -> Void

    @State private var hour = 8
    @State private var minute = 0
    @State private var temp = 22
    @State private var days: Set<Int> = []

    var body: some View {
        SectionCard(title: L("Новое расписание")) {
            // Время: часы и минуты крупными ± — попасть пальцем в плюс проще, чем в поле.
            StepperRow(label: L("Час"), value: String(format: "%02d", hour)) { hour = ((hour + $0) % 24 + 24) % 24 }
            StepperRow(label: L("Минуты"), value: String(format: "%02d", minute)) { minute = ((minute + $0 * 5) % 60 + 60) % 60 }
            StepperRow(label: L("Температура"), value: "\(temp)°") { temp = min(max(temp + $0, Cmd.TEMP_MIN), Cmd.TEMP_MAX) }

            Text(L("Дни")).font(ElectroType.caption).foregroundStyle(p.textSecondary)
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { i in
                    ControlChip(text: DAY_LABELS[i], selected: days.contains(i)) {
                        if days.contains(i) { days.remove(i) } else { days.insert(i) }
                    }
                }
            }
            if days.isEmpty {
                Text(L("Ни один день не выбран — сработает каждый день")).font(ElectroType.caption).foregroundStyle(p.textMuted)
            }
            HStack(spacing: Space.x2) {
                ElectroButton(text: L("Отмена"), style: .ghost, action: onCancel)
                ElectroButton(text: L("Сохранить"), style: .primary) {
                    onSave(ClimateScheduleRequest(hour: hour, minute: minute, weekdays: days.sorted(), temp_c: temp, enabled: true))
                }
            }
        }
    }
}

private struct StepperRow: View {
    @Environment(\.palette) private var p
    let label: String
    let value: String
    let onStep: (Int) -> Void

    var body: some View {
        HStack {
            Text(label).font(ElectroType.body).foregroundStyle(p.textPrimary)
            Spacer()
            ControlChip(text: "−", width: 52) { onStep(-1) }
            Text(value).font(ElectroType.value).foregroundStyle(p.accent).padding(.horizontal, Space.x3)
            ControlChip(text: "+", width: 52) { onStep(1) }
        }
    }
}

// MARK: - Голос

/// Голосовые намерения штатного ассистента головы: список готовых намерений.
struct VoiceScreen: View {
    @Environment(\.palette) private var p
    let intents: [VoiceIntentDto]
    let onRun: (VoiceIntentDto) -> Void
    let onBack: () -> Void

    var body: some View {
        ScreenScaffold(title: L("Голосовые команды"), onBack: onBack) {
            if intents.isEmpty {
                EmptyNote(text: L("У этой машины голосовых команд нет."))
            } else {
                SectionCard(title: L("Скажите или нажмите")) {
                    ForEach(intents) { intent in
                        Button { onRun(intent) } label: {
                            HStack(spacing: Space.x3) {
                                Image(systemName: "waveform").font(.system(size: 20)).foregroundStyle(p.accent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(intent.phrases.first.map { $0.prefix(1).uppercased() + $0.dropFirst() } ?? intent.intent)
                                        .font(ElectroType.body).foregroundStyle(p.textPrimary)
                                    if intent.phrases.count > 1 {
                                        Text(intent.phrases.dropFirst().joined(separator: " · "))
                                            .font(ElectroType.caption).foregroundStyle(p.textMuted)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.vertical, Space.x2)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

// MARK: - Новости и уведомления

/// Лента из админки: уведомления, новости, важное. Непрочитанное — с точкой,
/// тап отмечает прочитанным, «Прочитать всё» гасит бейдж.
struct NewsScreen: View {
    @Environment(\.palette) private var p
    let items: [NewsItem]
    let isRead: (NewsItem) -> Bool
    let onRead: (NewsItem) -> Void
    let onReadAll: () -> Void
    let onBack: () -> Void
    let onRefresh: () -> Void

    @State private var filter: String = "all"
    @State private var selected: NewsItem? = nil
    private var filters: [(String, String)] { [("all", L("Все")), ("info", L("Уведомления")), ("news", L("Новости")), ("alert", L("Важное"))] }

    var body: some View {
        let shown = items.filter { filter == "all" || $0.kind == filter }
        let unread = items.filter { !isRead($0) }.count
        ScreenScaffold(title: L("Новости"), onBack: onBack) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(filters, id: \.0) { f in
                        Button { filter = f.0 } label: {
                            Text(f.1).font(ElectroType.body).foregroundStyle(filter == f.0 ? p.accent : p.textPrimary)
                                .padding(.horizontal, Space.x4).frame(height: ControlSize.chip)
                                .background(p.surfaceElevated)
                                .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous).stroke(filter == f.0 ? p.accent : .clear, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            if unread > 0 {
                ElectroButton(text: L("Прочитать всё ({0})", unread), style: .secondary, action: onReadAll)
            }
            if shown.isEmpty {
                EmptyNote(text: items.isEmpty ? L("Пока ничего нет. Здесь появятся новости и уведомления от оператора.") : L("В этом разделе пусто."))
            }
            ForEach(shown) { n in
                let st = style(n.kind)
                let read = isRead(n)
                Button { onRead(n); selected = n } label: {
                    HStack(alignment: .top, spacing: Space.x3) {
                        Image(systemName: st.0).font(.system(size: 17, weight: .medium)).foregroundStyle(st.1)
                            .frame(width: 36, height: 36).background(st.1.opacity(0.14)).clipShape(Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Text(n.title).font(ElectroType.body).foregroundStyle(p.textPrimary)
                            if !n.body.isEmpty {
                                Text(n.body).font(ElectroType.caption).foregroundStyle(p.textSecondary)
                            }
                            Text(newsDate(n.created_at) + (read ? "" : L(" · не прочитано"))).font(ElectroType.unit).foregroundStyle(read ? p.textMuted : p.accent)
                        }
                        Spacer(minLength: 0)
                        if !read { Circle().fill(p.accent).frame(width: 8, height: 8).padding(.top, 6) }
                    }
                    .padding(Space.x4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(p.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(read ? .clear : p.accent.opacity(0.35), lineWidth: 1))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .refreshable { onRefresh() }
        .onAppear(perform: onRefresh)
        .fullScreenCover(item: $selected) { n in
            NewsDetailScreen(item: n) { selected = nil }
        }
    }

    private func style(_ kind: String) -> (String, Color) {
        switch kind {
        case "alert": return ("exclamationmark.triangle", p.warn)
        case "news": return ("newspaper", p.info)
        default: return ("bell", p.accent)
        }
    }
}

/// Одна запись на весь экран: заголовок, тип, дата, полный текст.
struct NewsDetailScreen: View {
    @Environment(\.palette) private var p
    let item: NewsItem
    let onClose: () -> Void

    var body: some View {
        let st = style(item.kind)
        VStack(spacing: 0) {
            HStack(spacing: Space.x3) {
                Button(action: onClose) {
                    Image(systemName: "chevron.left").font(.system(size: 20, weight: .medium)).foregroundStyle(p.textPrimary)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Назад"))
                Text(kindTitle(item.kind)).font(ElectroType.headline).foregroundStyle(p.textPrimary)
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .medium)).foregroundStyle(p.textSecondary)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Закрыть"))
            }
            .padding(Space.x4)
            ScrollView {
                VStack(alignment: .leading, spacing: Space.x4) {
                    HStack(spacing: Space.x3) {
                        Image(systemName: st.0).font(.system(size: 20, weight: .medium)).foregroundStyle(st.1)
                            .frame(width: 44, height: 44).background(st.1.opacity(0.14)).clipShape(Circle())
                        Text(newsDate(item.created_at)).font(ElectroType.caption).foregroundStyle(p.textMuted)
                    }
                    Text(item.title).font(ElectroType.title).foregroundStyle(p.textPrimary)
                    if !item.body.isEmpty {
                        Text(item.body).font(ElectroType.body).foregroundStyle(p.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(.horizontal, Space.x5)
                .padding(.bottom, Space.x6)
            }
            ElectroButton(text: L("Закрыть"), style: .secondary, action: onClose)
                .padding(.horizontal, Space.x5)
                .padding(.bottom, Space.x4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
    }

    private func style(_ kind: String) -> (String, Color) {
        switch kind {
        case "alert": return ("exclamationmark.triangle", p.warn)
        case "news": return ("newspaper", p.info)
        default: return ("bell", p.accent)
        }
    }
}

private func kindTitle(_ kind: String) -> String {
    switch kind {
    case "alert": return L("Важное")
    case "news": return L("Новость")
    default: return L("Уведомление")
    }
}

private func newsDate(_ iso: String) -> String {
    let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let f2 = ISO8601DateFormatter(); f2.formatOptions = [.withInternetDateTime]
    guard let d = f.date(from: iso) ?? f2.date(from: iso) else { return "" }
    let out = DateFormatter(); out.locale = Locale(identifier: "ru_RU"); out.dateFormat = "d MMMM, HH:mm"
    return out.string(from: d)
}

// MARK: - Карта

/// Карта с локацией машины: MapKit вместо статичной картинки, «Маршрут» — в Apple Maps.
struct MapScreen: View {
    @Environment(\.palette) private var p
    let loc: GeoPoint?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: Space.x2) {
                Text(L("Карта")).font(ElectroType.headline).foregroundStyle(p.textPrimary)
                Text("· Leapmotor C16").font(ElectroType.body).foregroundStyle(p.textMuted)
                Spacer()
            }
            .padding(Space.x4)

            if let loc {
                HStack(spacing: Space.x3) {
                    Image(systemName: "location").font(.system(size: 20)).foregroundStyle(p.accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("Положение автомобиля")).font(ElectroType.caption).foregroundStyle(p.textMuted)
                        Text(String(format: "%.5f, %.5f", loc.lat, loc.lon)).font(ElectroType.body).foregroundStyle(p.textPrimary)
                        if let b = loc.bearing {
                            Text(L("Курс: {0} {1}°", compass(b), Int(b))).font(ElectroType.caption).foregroundStyle(p.textMuted)
                        }
                    }
                    Spacer()
                }
                .padding(Space.x3)
                .background(p.surface)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
                .padding(.horizontal, Space.x4)

                Spacer().frame(height: Space.x3)

                let coord = CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
                Map(initialPosition: .region(MKCoordinateRegion(center: coord, latitudinalMeters: 600, longitudinalMeters: 600))) {
                    Marker("Leapmotor C16", systemImage: "car.fill", coordinate: coord).tint(p.accent)
                }
                .mapStyle(.standard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
                .padding(.horizontal, Space.x4)

                Spacer().frame(height: Space.x3)
                Button { openRoute(loc) } label: {
                    HStack(spacing: Space.x2) {
                        Image(systemName: "arrow.triangle.turn.up.right.diamond").font(.system(size: 18)).foregroundStyle(p.onAccent)
                        Text(L("Маршрут")).font(ElectroType.body).foregroundStyle(p.onAccent)
                    }
                    .frame(maxWidth: .infinity).frame(height: ControlSize.button)
                    .background(p.accent)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, Space.x4)
                Spacer().frame(height: Space.x4)
            } else {
                Spacer()
                VStack(spacing: Space.x1) {
                    Image(systemName: "location").font(.system(size: 36)).foregroundStyle(p.textMuted)
                    Spacer().frame(height: Space.x3)
                    Text(L("Нет координат")).font(ElectroType.body).foregroundStyle(p.textSecondary)
                    Text(L("Положение приходит с головы. Разбудите машину и дождитесь связи."))
                        .font(ElectroType.caption).foregroundStyle(p.textMuted).multilineTextAlignment(.center)
                        .padding(.horizontal, Space.x6)
                }
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
    }

    /// Открыть точку авто в Apple Maps (маршрут «как доехать»).
    private func openRoute(_ point: GeoPoint) {
        let coord = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lon)
        let item = MKMapItem(placemark: MKPlacemark(coordinate: coord))
        item.name = "Leapmotor C16"
        item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
    }
}

private func compass(_ bearing: Double) -> String {
    let dirs = [L("С"), L("СВ"), L("В"), L("ЮВ"), L("Ю"), L("ЮЗ"), L("З"), L("СЗ")]
    let normalized = (bearing.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
    return dirs[Int((normalized + 22.5) / 45) % 8]
}
