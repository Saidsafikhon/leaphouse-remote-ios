import SwiftUI

/// Экран подключения — первое, что видно при запуске. По кнопке машину будят
/// через сервер, и только когда голова ответила, пускают внутрь.
struct ConnectScreen: View {
    @Environment(\.palette) private var p
    @ObservedObject var vm: CarViewModel
    let status: ConnectStatus

    @State private var showSettings = false
    @State private var showHelp = false
    @State private var showNews = false
    @State private var showShop = false

    private func start() {
        if vm.wakeConfigured { vm.connect() } else { showSettings = true }
    }

    var body: some View {
        if showSettings {
            SettingsScreen(vm: vm) { showSettings = false }
        } else if showShop {
            ShopScreen(products: vm.products, loggedIn: true, model: vm.selectedVehicle?.model,
                                   onOrder: { id, qty, phone, comment, done in vm.order(id, qty: qty, phone: phone, comment: comment, done: done) },
                                   onBack: { showShop = false }, onRefresh: { vm.loadProducts() })
        } else if showNews {
            NewsScreen(items: vm.news, isRead: { vm.isRead($0) }, onRead: { vm.markRead($0) },
                       onReadAll: { vm.markAllRead() }, onBack: { showNews = false }, onRefresh: { vm.loadNews() })
        } else {
            content
        }
    }

    private var content: some View {
        let chosen = vm.selectedVehicle
        return VStack(spacing: 0) {
            HStack(spacing: Space.x2) {
                LangPicker(compact: true)
                Spacer()
                ShopFab { showShop = true }
                NewsBell(unread: vm.unreadNews) { showNews = true }
                HelpFab { showHelp = true }
            }
            Spacer()

            BrandLockup(markSize: 26)
            Spacer().frame(height: Space.x2)
            Text(chosen?.model ?? "C16").font(ElectroType.display).foregroundStyle(p.textPrimary)
            if let name = chosen?.name, !name.isEmpty {
                Spacer().frame(height: Space.x1)
                Text(name).font(ElectroType.body).foregroundStyle(p.textMuted)
            }
            Spacer().frame(height: Space.x6)

            // Рендер — студийная вырезка по модели и цвету кузова, на карточке темы.
            ZStack(alignment: .topTrailing) {
                Image(CarArt.imageName(chosen?.model, vm.paint)).resizable().scaledToFit()
                    .frame(maxWidth: .infinity).frame(height: 200)
                    .padding(.horizontal, Space.x2).padding(.vertical, Space.x2)
                PhaseBadge(status: status).padding(Space.x3)
            }
            .frame(maxWidth: .infinity)
            .background(p.surfaceRaised)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))

            Spacer().frame(height: Space.x5)
            let failed = status.phase == .timeout || status.phase == .error
            Text(hint(status, vm.wakeConfigured)).font(ElectroType.body).foregroundStyle(failed ? p.danger : p.textSecondary)
                .multilineTextAlignment(.center)

            if status.phase == .waiting {
                Spacer().frame(height: Space.x4)
                ProgressView().progressViewStyle(.linear).tint(p.accent)
            }

            Spacer()

            VStack(spacing: Space.x2) {
                switch status.phase {
                case .idle:
                    ElectroButton(text: L("Подключиться")) { start() }
                    ElectroButton(text: L("Найти машину"), style: .secondary, enabled: !vm.busy) { vm.findCar() }
                case .sending, .waiting:
                    ElectroButton(text: L("Подключение"), loading: true) {}
                case .timeout:
                    ElectroButton(text: L("Разбудить ещё раз")) { start() }
                    ElectroButton(text: L("Всё равно открыть"), style: .secondary) { vm.enterAnyway() }
                case .error:
                    ElectroButton(text: L("Повторить")) { start() }
                    ElectroButton(text: L("Всё равно открыть"), style: .ghost) { vm.enterAnyway() }
                case .connected:
                    EmptyView()
                }
            }

            Spacer().frame(height: Space.x3)
            ElectroButton(text: vm.wakeConfigured ? L("Настройки") : L("Настроить подключение"), style: .ghost) { showSettings = true }
            Spacer().frame(height: Space.x3)
            Text(appVersion()).font(ElectroType.caption).foregroundStyle(p.textMuted)
            Spacer().frame(height: Space.x3)
        }
        .padding(.horizontal, Space.x5)
        .padding(.top, Space.x6)
        .padding(.bottom, Space.x5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
        .sheet(isPresented: $showHelp) { HelpSheet(support: vm.support, feedbackVM: vm.loggedIn ? vm : nil) }
        .onAppear { CarModels.prefetch(vm.selectedVehicle?.model) }   // 3D-модель для главной — заранее
    }
}

private struct PhaseBadge: View {
    let status: ConnectStatus
    var body: some View {
        switch status.phase {
        case .idle: StatusBadge(kind: .offline, text: L("СПИТ"))
        case .sending, .waiting: StatusBadge(kind: .info, text: L("ПОДКЛЮЧЕНИЕ"))
        case .timeout: StatusBadge(kind: .failed, text: L("ОШИБКА"))
        case .error: StatusBadge(kind: .failed, text: L("ОШИБКА"))
        case .connected: StatusBadge(kind: .online, text: L("НА СВЯЗИ"))
        }
    }
}

/// Коротко: без объяснений, что происходит под капотом — только статус.
private func hint(_ status: ConnectStatus, _ configured: Bool) -> String {
    if !configured && status.phase == .idle { return L("Войдите на сервер в настройках.") }
    switch status.phase {
    case .sending, .waiting: return L("Подключение…")
    case .timeout, .error: return L("Ошибка: подключение не удалось")
    case .idle, .connected: return ""
    }
}
