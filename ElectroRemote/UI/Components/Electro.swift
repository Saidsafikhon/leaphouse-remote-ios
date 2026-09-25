import SwiftUI

/// Библиотека контролов Electro Remote — код-двойник страницы Components в
/// Figma и `ui/components/Electro.kt` на Android. Активное состояние —
/// акцентная обводка и акцентные иконка с подписью, а не заливка.

/// Символы, у которых есть запасной вариант на случай, если системы нет нужного глифа.
enum Sym {
    /// Багажник: машина сбоку с открытой пятой дверью (SF Symbols 5); иначе обычная машина.
    static let trunk: String = UIImage(systemName: "car.side.rear.open") != nil ? "car.side.rear.open" : "car"
}

/// Состояние контрола. Active — подтверждено машиной, Pending — команда в пути.
enum ControlState { case normal, active, pending, disabled }

struct ControlTile: View {
    @Environment(\.palette) private var p
    let label: String
    let icon: String
    var state: ControlState = .normal
    let action: () -> Void

    var body: some View {
        let ink: Color = {
            switch state {
            case .active: return p.accent
            case .pending: return p.textSecondary
            case .disabled: return p.textDisabled
            case .normal: return p.textPrimary
            }
        }()
        let textColor: Color = {
            switch state {
            case .active: return p.accent
            case .disabled: return p.textDisabled
            default: return p.textSecondary
            }
        }()
        let border: Color = {
            switch state {
            case .active: return p.accent
            case .disabled: return p.outline
            default: return .clear
            }
        }()
        Button(action: action) {
            VStack(spacing: Space.x2) {
                if state == .pending {
                    ProgressView().tint(ink).frame(width: 24, height: 24)
                } else {
                    Image(systemName: icon).font(.system(size: 20, weight: .regular)).foregroundStyle(ink)
                        .frame(width: 24, height: 24)
                }
                Text(label)
                    .font(ElectroType.label)
                    .foregroundStyle(textColor)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
            .padding(.horizontal, Space.x1)
            .padding(.vertical, Space.x3)
            .frame(maxWidth: .infinity)
            .frame(height: ControlSize.tile)
            .background(state == .disabled ? p.surface : p.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(state == .disabled || state == .pending)
    }
}

/// Компактный выбор значения: уровень, ±, день недели.
struct ControlChip: View {
    @Environment(\.palette) private var p
    let text: String
    var selected = false
    var enabled = true
    var width: CGFloat? = nil
    let action: () -> Void

    var body: some View {
        let ink: Color = !enabled ? p.textDisabled : (selected ? p.accent : p.textPrimary)
        Button(action: action) {
            Text(text)
                .font(ElectroType.body)
                .foregroundStyle(ink)
                .frame(maxWidth: width == nil ? CGFloat.infinity : nil)
                .frame(width: width, height: ControlSize.chip)
                .background(enabled ? p.surfaceElevated : p.surface)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                    .stroke(selected ? p.accent : .clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Круглая кнопка ± возле крупного значения.
struct RoundAction: View {
    @Environment(\.palette) private var p
    var label: String = ""
    let icon: String
    var active = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        let ink: Color = !enabled ? p.textDisabled : (active ? p.accent : p.textPrimary)
        VStack(spacing: Space.x2) {
            Button(action: action) {
                Image(systemName: icon).font(.system(size: 18, weight: .medium)).foregroundStyle(ink)
                    .frame(width: ControlSize.round, height: ControlSize.round)
                    .background(p.surfaceElevated)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(active ? p.accent : .clear, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
            if !label.isEmpty {
                Text(label).font(ElectroType.caption).foregroundStyle(p.textSecondary)
            }
        }
    }
}

enum ElectroButtonStyle { case primary, secondary, ghost, danger }

struct ElectroButton: View {
    @Environment(\.palette) private var p
    let text: String
    var style: ElectroButtonStyle = .primary
    var enabled = true
    var loading = false
    let action: () -> Void

    var body: some View {
        let bg: Color = {
            if !enabled { return p.surface }
            switch style {
            case .primary: return p.accent
            case .secondary: return p.surfaceElevated
            default: return .clear
            }
        }()
        let ink: Color = {
            if !enabled { return p.textDisabled }
            switch style {
            case .primary: return p.onAccent
            case .secondary: return p.textPrimary
            case .ghost: return p.textSecondary
            case .danger: return p.danger
            }
        }()
        let border: Color = {
            if !enabled { return p.outline }
            switch style {
            case .danger: return p.danger
            case .primary: return .clear
            default: return p.outline
            }
        }()
        Button(action: action) {
            HStack(spacing: Space.x2) {
                if loading { ProgressView().tint(ink).scaleEffect(0.8) }
                Text(loading ? L("Отправка…") : text).font(ElectroType.body).foregroundStyle(ink)
            }
            .padding(.horizontal, Space.x5)
            .frame(maxWidth: .infinity)
            .frame(height: ControlSize.button)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous).stroke(border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled || loading)
    }
}

/// Состояние связи и исход команды.
enum BadgeKind { case online, cloud, offline, success, unsafe, failed, timeout, unconfirmed, info }

extension BadgeKind {
    func ink(_ p: ElectroPalette) -> Color {
        switch self {
        case .online, .success: return p.ok
        case .cloud, .unconfirmed: return p.warn
        case .offline: return p.textMuted
        case .unsafe, .failed, .timeout: return p.danger
        case .info: return p.info
        }
    }

    func tint(_ p: ElectroPalette) -> Color {
        switch self {
        case .online, .success: return p.okTint
        case .cloud, .unconfirmed: return p.warnTint
        case .offline: return p.surfaceElevated
        case .unsafe, .failed, .timeout: return p.dangerTint
        case .info: return p.infoTint
        }
    }
}

struct StatusBadge: View {
    @Environment(\.palette) private var p
    let kind: BadgeKind
    let text: String

    var body: some View {
        HStack(spacing: 6) {
            if kind == .online || kind == .cloud || kind == .offline {
                Circle().fill(kind.ink(p)).frame(width: 6, height: 6)
            }
            Text(text).font(ElectroType.overline).kerning(1.1).foregroundStyle(kind.ink(p))
        }
        .padding(.horizontal, Space.x3)
        .padding(.vertical, 6)
        .background(kind.tint(p))
        .clipShape(Capsule())
    }
}

/// Число с единицей и полоской заполнения. `fraction` nil — полоски нет.
struct Metric: Identifiable {
    let value: String
    let unit: String
    var fraction: Double? = nil
    var id: String { value + unit }
}

/// Полоса состояния: слева охрана с иконкой в цветном квадрате, справа метрики.
struct StatusStrip: View {
    @Environment(\.palette) private var p
    let icon: String
    let title: String
    let subtitle: String
    var accent: Color
    var accentTint: Color
    var metrics: [Metric] = []

    var body: some View {
        HStack(spacing: Space.x3) {
            Image(systemName: icon).font(.system(size: 17, weight: .medium)).foregroundStyle(accent)
                .frame(width: 36, height: 36)
                .background(accentTint)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(ElectroType.body).foregroundStyle(p.textPrimary)
                Text(subtitle).font(ElectroType.caption).foregroundStyle(p.textMuted)
            }
            Spacer(minLength: 0)
            if !metrics.isEmpty {
                VStack(alignment: .trailing, spacing: 6) {
                    ForEach(metrics) { m in MetricRow(metric: m) }
                }
            }
        }
        .padding(Space.x3)
        .frame(maxWidth: .infinity)
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
    }
}

private struct MetricRow: View {
    @Environment(\.palette) private var p
    let metric: Metric

    var body: some View {
        HStack(spacing: 3) {
            if let f = metric.fraction {
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3).fill(p.surfaceElevated).frame(width: 28, height: 8)
                    RoundedRectangle(cornerRadius: 3).fill(p.accent).frame(width: 28 * min(max(f, 0), 1), height: 8)
                }
                .padding(.trailing, 5)
            }
            Text(metric.value).font(ElectroType.value).foregroundStyle(p.accent)
            Text(metric.unit).font(ElectroType.unit).foregroundStyle(p.textMuted)
        }
    }
}

/// Заголовок секции: обычным текстом, а не капсом.
struct SectionTitle: View {
    @Environment(\.palette) private var p
    let text: String
    var body: some View {
        Text(text).font(ElectroType.body).foregroundStyle(p.textSecondary)
    }
}

struct SectionCard<Content: View>: View {
    @Environment(\.palette) private var p
    let title: String
    var action: String? = nil
    var onAction: (() -> Void)? = nil
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Space.x3) {
            HStack {
                Text(title).font(ElectroType.body).foregroundStyle(p.textSecondary)
                Spacer()
                if let action {
                    Text(action).font(ElectroType.body).foregroundStyle(p.accent)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { onAction?() }
            content()
        }
        .padding(Space.x4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
    }
}

/// Снекбар результата команды: заголовок — что произошло, подпись — с чем именно.
struct ElectroToast: View {
    @Environment(\.palette) private var p
    let kind: BadgeKind
    let title: String
    var message: String? = nil

    var body: some View {
        let icon: String = {
            switch kind {
            case .success: return "checkmark.circle"
            case .unsafe, .failed: return "exclamationmark.triangle"
            case .timeout, .unconfirmed: return "clock"
            case .offline: return "icloud.slash"
            default: return "info.circle"
            }
        }()
        HStack(spacing: Space.x3) {
            Image(systemName: icon).font(.system(size: 17, weight: .medium)).foregroundStyle(kind.ink(p))
                .frame(width: 36, height: 36)
                .background(kind.tint(p))
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(ElectroType.body)
                    .foregroundStyle(kind == .success || kind == .info ? p.textPrimary : kind.ink(p))
                if let message, !message.isEmpty {
                    Text(message).font(ElectroType.caption).foregroundStyle(p.textSecondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Space.x4)
        .padding(.vertical, Space.x3)
        .frame(maxWidth: .infinity)
        .background(p.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(kind.ink(p), lineWidth: 1))
    }
}

/// Подтверждение опасного действия — открыть двери, багажник.
struct DialogSpec: Identifiable {
    let id = UUID()
    let icon: String
    let accent: Color
    let title: String
    let message: String
    let confirmText: String
    /// nil — кнопки «Отмена» нет (у сообщения выбора нет).
    var dismissText: String? = L("Отмена")
    let onConfirm: () -> Void
}

/// Модификатор: рисует диалог поверх экрана, пока `item` не nil.
struct ElectroDialogModifier: ViewModifier {
    @Environment(\.palette) private var p
    @Binding var item: DialogSpec?

    func body(content: Content) -> some View {
        ZStack {
            content
            if let d = item {
                Color.black.opacity(0.55).ignoresSafeArea()
                    .onTapGesture { item = nil }
                VStack(spacing: Space.x3) {
                    Image(systemName: d.icon).font(.system(size: 30, weight: .regular)).foregroundStyle(d.accent)
                    Text(d.title).font(ElectroType.headline).foregroundStyle(p.textPrimary)
                        .multilineTextAlignment(.center)
                    Text(d.message).font(ElectroType.body).foregroundStyle(p.textSecondary)
                        .multilineTextAlignment(.center)
                    HStack(spacing: Space.x2) {
                        if let dismiss = d.dismissText {
                            ElectroButton(text: dismiss, style: .ghost) { item = nil }
                        }
                        ElectroButton(text: d.confirmText, style: .primary) {
                            let action = d.onConfirm
                            item = nil
                            action()
                        }
                    }
                    .padding(.top, Space.x2)
                }
                .padding(Space.x6)
                .background(p.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
                .padding(.horizontal, Space.x6)
                .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.15), value: item?.id)
    }
}

extension View {
    func electroDialog(_ item: Binding<DialogSpec?>) -> some View {
        modifier(ElectroDialogModifier(item: item))
    }
}

/// Круглая кнопка «Помощь» — кругляш с вопросом внутри.
struct HelpFab: View {
    @Environment(\.palette) private var p
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text("?").font(.system(size: 22, weight: .bold)).foregroundStyle(p.accent)
                .frame(width: 44, height: 44)
                .background(p.surfaceElevated)
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

/// Пустая подпись на экране второго уровня.
struct EmptyNote: View {
    @Environment(\.palette) private var p
    let text: String
    var body: some View {
        Text(text).font(ElectroType.body).foregroundStyle(p.textMuted).padding(Space.x4)
    }
}

/// Каркас экрана второго уровня: «назад», заголовок, прокручиваемое содержимое.
struct ScreenScaffold<Content: View>: View {
    @Environment(\.palette) private var p
    let title: String
    let onBack: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: Space.x3) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 20, weight: .medium)).foregroundStyle(p.textPrimary)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Назад"))
                Text(title).font(ElectroType.headline).foregroundStyle(p.textPrimary)
                Spacer()
            }
            .padding(Space.x4)
            ScrollView {
                // Ленивый стек: длинные ленты (новости, магазин) не собирают все карточки
                // с картинками разом при открытии — экран появляется сразу.
                LazyVStack(alignment: .leading, spacing: Space.x4) { content() }
                    .padding(.horizontal, Space.x4)
                    .padding(.bottom, Space.x5)
            }
        }
        .background(p.background)
    }
}

/// Тумблер в цветах Electro.
struct ElectroToggle: View {
    @Environment(\.palette) private var p
    let isOn: Bool
    var enabled = true
    var accent: Color? = nil
    let onChange: (Bool) -> Void

    var body: some View {
        Toggle("", isOn: Binding(get: { isOn }, set: { onChange($0) }))
            .labelsHidden()
            .tint(accent ?? p.accent)
            .disabled(!enabled)
    }
}

/// Картинка-обложка по URL: заполняет отведённый прямоугольник, НЕ расширяя раскладку.
/// `resizable().scaledToFill()` внутри VStack предлагает свою ширину и выталкивает
/// экран за край (так уехал экран товара на iPhone) — поэтому картинка кладётся
/// overlay-ем на прозрачный прямоугольник фиксированного размера.
struct CoverImage: View {
    @Environment(\.palette) private var p
    let url: URL?
    var height: CGFloat? = nil          // nil — квадрат по ширине
    var corner: CGFloat = Radius.sm
    var placeholder: String? = nil      // SF Symbol, если картинки нет

    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .modifier(SizeMod(height: height))
            .background(p.surfaceElevated)
            .overlay {
                if let url {
                    AsyncImage(url: url) { phase in
                        if let img = phase.image { img.resizable().scaledToFill() } else { Color.clear }
                    }
                } else if let ph = placeholder {
                    Image(systemName: ph).font(.system(size: 30)).foregroundStyle(p.textMuted)
                }
            }
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
    }

    private struct SizeMod: ViewModifier {
        let height: CGFloat?
        func body(content: Content) -> some View {
            if let h = height { content.frame(height: h) } else { content.aspectRatio(1, contentMode: .fit) }
        }
    }
}
