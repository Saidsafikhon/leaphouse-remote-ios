import SwiftUI

/// Кузов: схема машины сверху (нос вверху) и статус замка — зеркало `BodyDiagram.kt`.
///
/// Открытая дверь рисуется красной и отведённой наружу, багажник и капот —
/// красной заливкой своей части. Данные — живые, от головы 4.61+ (у каждого
/// поколения машины свой источник, голова приводит их к одним полям). Если
/// машина о дверях не сообщает, карточка показывает только замок.
struct BodyStatusCard: View {
    @Environment(\.palette) private var p
    let car: CarState

    var body: some View {
        let open = car.openParts()
        HStack(spacing: Space.x4) {
            if car.bodyKnown {
                CarTopView(car: car).frame(width: 64, height: 112)
            }
            VStack(alignment: .leading, spacing: Space.x2) {
                HStack(spacing: Space.x2) {
                    Image(systemName: car.locked == true ? "lock.fill" : "lock.open")
                        .font(.system(size: 15, weight: .medium)).foregroundStyle(lockColor)
                        .frame(width: 32, height: 32)
                        .background(lockTint)
                        .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                    Text(lockText).font(ElectroType.body).foregroundStyle(p.textPrimary)
                }
                if !car.bodyKnown {
                    Text(L("Двери: машина не сообщает")).font(ElectroType.caption).foregroundStyle(p.textMuted)
                } else if open.isEmpty {
                    Text(car.hoodKnown ? L("Двери, багажник и капот закрыты") : L("Двери и багажник закрыты")).font(ElectroType.caption).foregroundStyle(p.textMuted)
                } else {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("Не закрыто:")).font(ElectroType.caption).foregroundStyle(p.danger)
                        ForEach(open, id: \.self) { part in
                            Text("• " + part.prefix(1).uppercased() + part.dropFirst())
                                .font(ElectroType.caption).foregroundStyle(p.textPrimary)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Space.x3)
        .frame(maxWidth: .infinity)
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
    }

    private var lockText: String {
        switch car.locked {
        case .some(true): return L("Закрыто")
        case .some(false): return L("Открыто")
        case .none: return L("Замок: нет данных")
        }
    }
    private var lockColor: Color {
        switch car.locked {
        case .some(true): return p.ok
        case .some(false): return p.warn
        case .none: return p.textMuted
        }
    }
    private var lockTint: Color {
        switch car.locked {
        case .some(true): return p.okTint
        case .some(false): return p.warnTint
        case .none: return p.surfaceElevated
        }
    }
}

/// Машина сверху: корпус, капот, багажник, четыре двери. Открытое — красным.
struct CarTopView: View {
    @Environment(\.palette) private var p
    let car: CarState

    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            let bodyW = w * 0.62, left = (w - bodyW) / 2, right = left + bodyW
            let top = h * 0.04, bottom = h * 0.96, len = bottom - top
            let r = bodyW * 0.32
            let hoodEnd = top + len * 0.24, trunkStart = top + len * 0.80

            let shell = Path(roundedRect: CGRect(x: left, y: top, width: bodyW, height: len), cornerRadius: r)
            ctx.fill(shell, with: .color(p.surfaceElevated))
            if car.hoodOpen {
                ctx.fill(Path(roundedRect: CGRect(x: left, y: top, width: bodyW, height: hoodEnd - top), cornerRadius: r),
                         with: .color(p.danger.opacity(0.85)))
            }
            if car.trunkOpen == true {
                ctx.fill(Path(roundedRect: CGRect(x: left, y: trunkStart, width: bodyW, height: bottom - trunkStart), cornerRadius: r),
                         with: .color(p.danger.opacity(0.85)))
            }
            // стёкла
            ctx.fill(Path(roundedRect: CGRect(x: left + bodyW * 0.14, y: hoodEnd + 2, width: bodyW * 0.72, height: len * 0.10),
                          cornerRadius: 3), with: .color(p.outline))
            ctx.fill(Path(roundedRect: CGRect(x: left + bodyW * 0.16, y: trunkStart - len * 0.09, width: bodyW * 0.68, height: len * 0.07),
                          cornerRadius: 3), with: .color(p.outline))
            ctx.stroke(shell, with: .color(p.textMuted), lineWidth: 1)

            // двери: передние от конца капота, задние до начала багажника
            let doorLen = (trunkStart - hoodEnd) / 2
            let frontY = hoodEnd + doorLen * 0.05, rearY = hoodEnd + doorLen * 1.02
            door(&ctx, x: left, y: frontY, len: doorLen * 0.93, leftSide: true, open: car.doors.frontLeft)
            door(&ctx, x: right, y: frontY, len: doorLen * 0.93, leftSide: false, open: car.doors.frontRight)
            door(&ctx, x: left, y: rearY, len: doorLen * 0.93, leftSide: true, open: car.doors.rearLeft)
            door(&ctx, x: right, y: rearY, len: doorLen * 0.93, leftSide: false, open: car.doors.rearRight)
        }
        .accessibilityHidden(true)
    }

    /// Дверь — отрезок по борту; открытая поворачивается наружу на петле спереди.
    private func door(_ ctx: inout GraphicsContext, x: CGFloat, y: CGFloat, len: CGFloat, leftSide: Bool, open: Bool) {
        let angle = open ? (leftSide ? 35.0 : -35.0) * .pi / 180 : 0
        // поворот (0, len) вокруг петли (x, y); по часовой при y вниз
        let dx = -sin(angle) * len, dy = cos(angle) * len
        var path = Path()
        path.move(to: CGPoint(x: x, y: y))
        path.addLine(to: CGPoint(x: x + dx, y: y + dy))
        ctx.stroke(path, with: .color(open ? p.danger : p.textMuted), lineWidth: open ? 2.5 : 1.5)
    }
}

/// Передача не P: красная плашка над управлением — команды с телефона отключены.
struct GearLockBanner: View {
    @Environment(\.palette) private var p

    var body: some View {
        VStack(alignment: .leading, spacing: Space.x1) {
            Text(L("Машина не на паркинге")).font(ElectroType.body).foregroundStyle(p.danger)
            Text(L("Управление с телефона отключено, пока передача не P."))
                .font(ElectroType.caption).foregroundStyle(p.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Space.x4).padding(.vertical, Space.x3)
        .background(p.dangerTint)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
    }
}
