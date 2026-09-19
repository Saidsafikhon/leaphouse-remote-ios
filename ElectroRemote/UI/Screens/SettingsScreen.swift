import SwiftUI

/// Полноэкранные настройки: вход в аккаунт, машины (добавить, выбрать,
/// переименовать, отвязать), выход. Переименование — локальное, только в
/// этом телефоне.
struct SettingsScreen: View {
    @Environment(\.palette) private var p
    @ObservedObject var vm: CarViewModel
    let onClose: () -> Void

    @State private var email = ""
    @State private var password = ""
    @State private var error: String? = nil
    @State private var renaming: VehicleDto? = nil
    @State private var renameText = ""
    @State private var dialog: DialogSpec? = nil
    @State private var scanning = false
    @State private var pairMsg: String? = nil
    @State private var deleting = false
    @State private var deletePassword = ""
    @State private var deleteError: String? = nil
    @State private var deleteBusy = false
    @State private var feedback = false

    var body: some View {
        ScreenScaffold(title: "Настройки", onBack: onClose) {
            if vm.loggedIn {
                SectionCard(title: "Аккаунт") {
                    Text("Вход выполнен: " + (vm.settings.email ?? "—")).font(.system(size: 14)).foregroundStyle(p.textPrimary)
                    Button { vm.logout() } label: {
                        Text("Выйти из аккаунта").font(ElectroType.body).foregroundStyle(p.danger)
                    }
                    .buttonStyle(.plain)
                    Button { deletePassword = ""; deleteError = nil; deleting = true } label: {
                        Text("Удалить аккаунт").font(ElectroType.caption).foregroundStyle(p.textMuted)
                    }
                    .buttonStyle(.plain)
                }

                SectionCard(title: "Мои машины", action: "Обновить", onAction: { vm.loadVehicles() }) {
                    if vm.vehicles.isEmpty {
                        Text(vm.parkNote ?? "Машин нет — добавьте по QR с экрана машины.")
                            .font(.system(size: 12)).foregroundStyle(p.textMuted)
                    }
                    ForEach(vm.vehicles) { v in
                        VehicleRow(
                            vehicle: v,
                            nick: vm.vehicleNick(v.vehicle_id),
                            selected: v.vehicle_id == vm.vehicleId,
                            onSelect: { vm.selectVehicle(v.vehicle_id) },
                            onRename: { renameText = vm.vehicleNick(v.vehicle_id) ?? v.name; renaming = v },
                            onDrop: {
                                let shown = vm.vehicleNick(v.vehicle_id) ?? v.name
                                dialog = DialogSpec(
                                    icon: "trash", accent: p.danger, title: "Отвязать машину?",
                                    message: "«\(shown)» перестанет быть доступной этому аккаунту. Сама машина останется — доступ вернёт новый QR с её экрана.",
                                    confirmText: "Отвязать", onConfirm: { vm.unlinkVehicle(v.vehicle_id) }
                                )
                            }
                        )
                        .id("\(v.vehicle_id)-\(vm.nickVersion)")
                        // цвет кузова — только у выбранной машины, чтобы список не разрастался
                        if v.vehicle_id == vm.vehicleId {
                            PaintPicker(model: v.model, current: vm.paint) { vm.setVehiclePaint(v.vehicle_id, $0) }
                        }
                    }
                    Button { scanning = true } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "plus.circle").font(.system(size: 16)).foregroundStyle(p.accent)
                            Text("Добавить машину").font(ElectroType.body).foregroundStyle(p.accent)
                        }
                    }
                    .buttonStyle(.plain)
                    if let pairMsg {
                        Text(pairMsg).font(.system(size: 12)).foregroundStyle(p.textSecondary)
                    }
                }
            } else {
                SectionCard(title: "Вход") {
                    AuthField(label: "ЛОГИН", value: $email, keyboard: .emailAddress, contentType: .username)
                    AuthField(label: "ПАРОЛЬ", value: $password, secure: true, contentType: .password)
                    if let error {
                        Text(error).font(.system(size: 13)).foregroundStyle(p.danger)
                    }
                    ElectroButton(
                        text: "Войти",
                        enabled: !vm.busy && !email.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty,
                        loading: vm.busy
                    ) {
                        error = nil
                        vm.login(email: email.trimmingCharacters(in: .whitespaces), password: password) { failure in error = failure }
                    }
                }
            }

            if vm.loggedIn {
                SectionCard(title: "Отзыв") {
                    Text("Замечания, идеи и предложения по приложению — разработчикам напрямую.")
                        .font(.system(size: 13)).foregroundStyle(p.textSecondary)
                    Button { feedback = true } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.pencil").font(.system(size: 15)).foregroundStyle(p.accent)
                            Text("Оставить отзыв").font(ElectroType.body).foregroundStyle(p.accent)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: Space.x4) {
                Link("Политика конфиденциальности", destination: Links.privacy)
                Link("Поддержка", destination: Links.support)
            }
            .font(ElectroType.caption).foregroundStyle(p.textMuted).frame(maxWidth: .infinity)
            Text(appVersion()).font(ElectroType.caption).foregroundStyle(p.textMuted)
                .frame(maxWidth: .infinity).padding(.top, Space.x1)
        }
        .sheet(isPresented: $feedback) {
            FeedbackView(vm: vm) { feedback = false }
                .presentationDetents([.large]).presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $deleting) {
            DeleteAccountSheet(password: $deletePassword, error: deleteError, busy: deleteBusy) {
                deleteBusy = true; deleteError = nil
                Task {
                    do { try await vm.deleteAccount(password: deletePassword); deleting = false }
                    catch { deleteError = (error as? RepoError)?.message ?? "Не удалось удалить аккаунт" }
                    deleteBusy = false
                }
            }
        }
        .onAppear {
            email = vm.settings.email ?? ""
            if vm.loggedIn { vm.loadVehicles() }
        }
        .electroDialog($dialog)
        .fullScreenCover(isPresented: $scanning) {
            QRScannerScreen { payload in
                scanning = false
                guard let payload else { return }
                pairMsg = nil
                Task {
                    do { _ = try await vm.claimPairing(payload); pairMsg = "Машина привязана" }
                    catch { pairMsg = (error as? RepoError)?.message ?? "Не удалось привязать машину" }
                }
            }
        }
        .sheet(item: $renaming) { v in
            RenameSheet(name: $renameText, serverName: v.name) { save in
                if save {
                    let trimmed = renameText.trimmingCharacters(in: .whitespaces)
                    vm.setVehicleNick(v.vehicle_id, (trimmed.isEmpty || trimmed == v.name) ? nil : trimmed)
                } else {
                    vm.setVehicleNick(v.vehicle_id, nil)
                }
                renaming = nil
            }
        }
    }
}

/// Строка машины: выбор радиокнопкой, имя (локальное поверх серверного), правка и отвязка.
/// Кружки цветов кузова, на которые есть рендер модели; выбранный — крупнее,
/// с толстым акцентным кольцом и галочкой.
private struct PaintPicker: View {
    @Environment(\.palette) private var p
    let model: String?
    let current: String?
    let onPick: (String) -> Void

    var body: some View {
        let paints = CarArt.paints(model)
        if paints.count >= 2 {
            let chosen = paints.first { $0.code == current }?.code ?? paints[0].code
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 10) {
                    ForEach(paints) { pt in
                        let sel = pt.code == chosen
                        Button { onPick(pt.code) } label: {
                            ZStack {
                                Circle().stroke(sel ? p.accent : p.outline, lineWidth: sel ? 3 : 1)
                                Circle().fill(pt.swatch).padding(sel ? 5 : 3)
                                if sel {
                                    Text("✓").font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(pt.swatch.isLight ? Color(hex: 0x111111) : .white)
                                }
                            }
                            .frame(width: sel ? 34 : 26, height: sel ? 34 : 26)
                        }
                        .buttonStyle(.plain)
                    }
                }
                Text("Цвет кузова: " + (paints.first { $0.code == chosen }?.label ?? ""))
                    .font(.system(size: 11)).foregroundStyle(p.textMuted)
            }
            .padding(.leading, 44).padding(.top, 2).padding(.bottom, 8)
        }
    }
}

private extension Color {
    /// Светлый ли цвет — для контрастной галочки на образце.
    var isLight: Bool {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.55
    }
}

private struct VehicleRow: View {
    @Environment(\.palette) private var p
    let vehicle: VehicleDto
    let nick: String?
    let selected: Bool
    let onSelect: () -> Void
    let onRename: () -> Void
    let onDrop: () -> Void

    var body: some View {
        HStack(spacing: Space.x2) {
            Button(action: onSelect) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 20)).foregroundStyle(selected ? p.accent : p.textMuted)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                Text(nick ?? vehicle.name)
                    .font(.system(size: 14, weight: selected ? .bold : .regular))
                    .foregroundStyle(selected ? p.textPrimary : p.textSecondary)
                Text([vehicle.model, vehicle.vin.isEmpty ? nil : vehicle.vin].compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 11)).foregroundStyle(p.textMuted)
            }
            .contentShape(Rectangle())
            .onTapGesture(perform: onSelect)
            Spacer()
            Button(action: onRename) {
                Image(systemName: "pencil").font(.system(size: 17)).foregroundStyle(p.textSecondary).frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            Button(action: onDrop) {
                Image(systemName: "trash").font(.system(size: 17)).foregroundStyle(p.danger).frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 4)
    }
}

/// Удаление аккаунта: предупреждение, пароль, красная кнопка.
private struct DeleteAccountSheet: View {
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    @Binding var password: String
    let error: String?
    let busy: Bool
    let onConfirm: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.x3) {
            Text("Удалить аккаунт?").font(ElectroType.headline).foregroundStyle(p.textPrimary)
            Text("Учётная запись, доступ к машинам, сцены и расписания будут удалены с сервера без возможности восстановления. Сами машины останутся в парке.")
                .font(ElectroType.body).foregroundStyle(p.textSecondary)
            AuthField(label: "ПАРОЛЬ ДЛЯ ПОДТВЕРЖДЕНИЯ", value: $password, secure: true, contentType: .password)
            if let error { ElectroToast(kind: .failed, title: error) }
            HStack(spacing: Space.x2) {
                ElectroButton(text: "Отмена", style: .ghost, enabled: !busy) { dismiss() }
                ElectroButton(text: "Удалить навсегда", style: .danger, enabled: password.count >= MIN_PASSWORD && !busy, loading: busy, action: onConfirm)
            }
            Spacer()
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(p.surfaceElevated)
        .presentationDetents([.medium, .large])
    }
}

/// Переименование (локальное имя).
private struct RenameSheet: View {
    @Environment(\.palette) private var p
    @Binding var name: String
    let serverName: String
    /// true — сохранить, false — сбросить к серверному имени.
    let onDone: (Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.x3) {
            Text("Имя машины").font(ElectroType.headline).foregroundStyle(p.textPrimary)
            Text("Показывается только в этом телефоне.").font(.system(size: 12)).foregroundStyle(p.textMuted)
            TextField(serverName, text: $name)
                .font(ElectroType.body).foregroundStyle(p.textPrimary).tint(p.accent)
                .padding(.horizontal, Space.x4).frame(height: 52)
                .background(p.surface)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous).stroke(p.outline, lineWidth: 1))
            HStack(spacing: Space.x2) {
                ElectroButton(text: "Сбросить", style: .ghost) { onDone(false) }
                ElectroButton(text: "Сохранить", style: .primary) { onDone(true) }
            }
            Spacer()
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(p.surfaceElevated)
        .presentationDetents([.medium])
    }
}
