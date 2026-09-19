import SwiftUI

/// Экран блокировки: четыре точки, цифровая клавиатура и, если включено,
/// кнопка биометрии (системный диалог показывается сам при открытии).
struct LockScreen: View {
    @Environment(\.palette) private var p
    @ObservedObject var lock: AppLock

    @State private var pin = ""
    @State private var error: String? = nil
    @State private var cooldown = 0

    private var bio: Bool { lock.biometricEnabled && lock.biometricAvailable() }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            BrandLockup(markSize: 40)
            Spacer().frame(height: Space.x8)
            Text(cooldown > 0 ? "Подождите \(cooldown) с" : (error ?? "Введите код"))
                .font(.system(size: 15)).foregroundStyle(error != nil || cooldown > 0 ? p.danger : p.textSecondary)
            Spacer().frame(height: Space.x5)
            PinDots(filled: pin.count, error: error != nil)
            Spacer()
            Spacer()
            PinPad(enabled: cooldown == 0, onDigit: { d in if pin.count < AppLock.pinLength { error = nil; pin += d } },
                   onBackspace: { if !pin.isEmpty { pin.removeLast() } },
                   biometric: bio ? { lock.promptBiometric { ok in if ok { lock.locked = false } } } : nil)
            Spacer().frame(height: Space.x6)
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
        .onAppear {
            cooldown = lock.cooldownSec()
            if bio && cooldown == 0 { lock.promptBiometric { ok in if ok { lock.locked = false } } }
        }
        .onChange(of: pin) { _, v in
            guard v.count == AppLock.pinLength else { return }
            if lock.check(v) { lock.locked = false; pin = "" }
            else {
                error = "Неверный код"
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { pin = ""; cooldown = lock.cooldownSec() }
            }
        }
        .task(id: cooldown) {
            guard cooldown > 0 else { return }
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            cooldown = lock.cooldownSec()
        }
    }
}

struct PinDots: View {
    @Environment(\.palette) private var p
    let filled: Int
    let error: Bool
    var body: some View {
        HStack(spacing: 18) {
            ForEach(0..<AppLock.pinLength, id: \.self) { i in
                Circle().fill(error ? p.danger : (i < filled ? p.accent : p.surfaceElevated))
                    .overlay(Circle().stroke(i < filled || error ? .clear : p.outline, lineWidth: 1))
                    .frame(width: 16, height: 16)
            }
        }
    }
}

/// Клавиатура 3×4: цифры, слева биометрия (или пусто), справа стереть.
struct PinPad: View {
    @Environment(\.palette) private var p
    let enabled: Bool
    let onDigit: (String) -> Void
    let onBackspace: () -> Void
    var biometric: (() -> Void)? = nil

    private let rows: [[String]] = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["bio", "0", "back"]]

    var body: some View {
        VStack(spacing: 14) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 22) {
                    ForEach(row, id: \.self) { key in
                        switch key {
                        case "bio":
                            if let biometric {
                                Button(action: biometric) {
                                    Image(systemName: AppLock.shared.biometricName == "Face ID" ? "faceid" : "touchid")
                                        .font(.system(size: 30)).foregroundStyle(p.accent).frame(width: 72, height: 72)
                                }
                                .buttonStyle(.plain).disabled(!enabled)
                            } else {
                                Color.clear.frame(width: 72, height: 72)
                            }
                        case "back":
                            Button(action: onBackspace) {
                                Image(systemName: "delete.left").font(.system(size: 24)).foregroundStyle(p.textSecondary).frame(width: 72, height: 72)
                            }
                            .buttonStyle(.plain).disabled(!enabled)
                        default:
                            Button { onDigit(key) } label: {
                                Text(key).font(.system(size: 28, weight: .medium)).foregroundStyle(p.textPrimary)
                                    .frame(width: 72, height: 72).background(p.surface).clipShape(Circle())
                            }
                            .buttonStyle(.plain).disabled(!enabled)
                        }
                    }
                }
            }
        }
    }
}

/// Лист задания кода: ввести, повторить. `verifyFirst` — сперва спросить
/// текущий код (смена или отключение); `verifyOnly` — только проверить.
struct PinSetupSheet: View {
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    let lock: AppLock
    let title: String
    var verifyFirst = false
    var verifyOnly = false
    let onDone: (String?) -> Void

    @State private var stage = 1   // 0 текущий, 1 новый, 2 повтор
    @State private var pin = ""
    @State private var first = ""
    @State private var error: String? = nil

    var body: some View {
        VStack(spacing: Space.x4) {
            HStack {
                Text(title).font(ElectroType.headline).foregroundStyle(p.textPrimary)
                Spacer()
                Button { dismiss() } label: { Image(systemName: "xmark").foregroundStyle(p.textSecondary).frame(width: 32, height: 32) }.buttonStyle(.plain)
            }
            Text(error ?? (stage == 0 ? "Введите текущий код" : (stage == 1 ? "Придумайте код из 4 цифр" : "Повторите код")))
                .font(.system(size: 14)).foregroundStyle(error != nil ? p.danger : p.textSecondary)
            PinDots(filled: pin.count, error: error != nil)
            Spacer().frame(height: Space.x2)
            PinPad(enabled: true, onDigit: { d in if pin.count < AppLock.pinLength { error = nil; pin += d } },
                   onBackspace: { if !pin.isEmpty { pin.removeLast() } })
            Spacer()
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.surfaceElevated)
        .onAppear { stage = verifyFirst ? 0 : 1 }
        .onChange(of: pin) { _, v in
            guard v.count == AppLock.pinLength else { return }
            switch stage {
            case 0:
                if lock.check(v) { if verifyOnly { onDone(nil); dismiss() } else { stage = 1; pin = "" } }
                else { fail("Неверный код") }
            case 1:
                first = v; pin = ""; stage = 2
            default:
                if v == first { onDone(v); dismiss() } else { fail("Коды не совпадают"); first = ""; stage = 1 }
            }
        }
    }

    private func fail(_ msg: String) {
        error = msg
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { pin = "" }
    }
}
