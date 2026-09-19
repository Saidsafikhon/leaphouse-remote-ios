import SwiftUI

/// Экран блокировки: системный диалог (Face ID / Touch ID / код-пароль iPhone)
/// показывается сам при открытии; кнопка — повторить, если отменили.
struct LockScreen: View {
    @Environment(\.palette) private var p
    @ObservedObject var lock: AppLock
    @State private var failed = false

    private func ask() {
        lock.prompt { ok in if ok { lock.locked = false } else { failed = true } }
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            BrandLockup(markSize: 44)
            Spacer().frame(height: Space.x6)
            Text(failed ? "Не удалось подтвердить. Попробуйте ещё раз." : "Подтвердите, что это вы")
                .font(.system(size: 15)).foregroundStyle(failed ? p.danger : p.textSecondary)
                .multilineTextAlignment(.center)
            Spacer()
            ElectroButton(text: "Разблокировать") { failed = false; ask() }
            Spacer().frame(height: Space.x6)
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
        .onAppear { ask() }
    }
}
