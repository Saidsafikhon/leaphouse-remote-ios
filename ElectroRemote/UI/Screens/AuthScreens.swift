import SwiftUI

let MIN_PASSWORD = 8

/// Поле формы входа: подпись сверху, тёмная заливка, радиус из системы.
struct AuthField: View {
    @Environment(\.palette) private var p
    let label: String
    @Binding var value: String
    var keyboard: UIKeyboardType = .default
    var secure = false
    var contentType: UITextContentType? = nil
    var autocapitalize = false

    @State private var show = false

    var body: some View {
        VStack(alignment: .leading, spacing: Space.x1) {
            Text(label).font(.system(size: 12, weight: .semibold)).foregroundStyle(p.textSecondary)
            HStack(spacing: Space.x2) {
                Group {
                    if secure && !show {
                        SecureField("", text: $value)
                    } else {
                        TextField("", text: $value)
                    }
                }
                .font(ElectroType.body)
                .foregroundStyle(p.textPrimary)
                .keyboardType(keyboard)
                .textContentType(contentType)
                .textInputAutocapitalization(autocapitalize ? .words : .never)
                .autocorrectionDisabled(true)
                .tint(p.accent)
                if secure {
                    Button { show.toggle() } label: {
                        Image(systemName: show ? "eye.slash" : "eye").foregroundStyle(p.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Space.x4)
            .frame(height: 52)
            .background(p.surface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous).stroke(p.outline, lineWidth: 1))
        }
    }
}

/// Пояснение под полем.
struct Hint: View {
    @Environment(\.palette) private var p
    let text: String
    var body: some View { Text(text).font(.system(size: 12)).foregroundStyle(p.textMuted) }
}

/// Общий каркас экранов входа: заголовок с «назад», форма, кнопка внизу.
struct AuthScaffold<Content: View>: View {
    @Environment(\.palette) private var p
    let title: String
    let subtitle: String
    let action: String
    let busy: Bool
    let enabled: Bool
    var error: String? = nil
    var note: String? = nil
    let onBack: () -> Void
    let onAction: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: Space.x8)
                Button(action: onBack) {
                    HStack(spacing: Space.x2) {
                        Image(systemName: "arrow.left").font(.system(size: 16)).foregroundStyle(p.textSecondary)
                        Text(L("Назад")).font(.system(size: 14)).foregroundStyle(p.textSecondary)
                    }
                    .padding(.vertical, Space.x2)
                }
                .buttonStyle(.plain)
                .disabled(busy)

                Spacer().frame(height: Space.x5)
                Text(title).font(.system(size: 26, weight: .bold)).foregroundStyle(p.textPrimary)
                Spacer().frame(height: Space.x2)
                Text(subtitle).font(.system(size: 14)).foregroundStyle(p.textSecondary)

                Spacer().frame(height: Space.x6)
                VStack(alignment: .leading, spacing: Space.x4) { content() }

                if let error {
                    Spacer().frame(height: Space.x3)
                    ElectroToast(kind: .failed, title: error)
                }
                if let note {
                    Spacer().frame(height: Space.x3)
                    ElectroToast(kind: .success, title: note)
                }

                Spacer().frame(height: Space.x6)
                ElectroButton(text: action, enabled: enabled && !busy, loading: busy, action: onAction)
                Spacer().frame(height: Space.x8)
            }
            .padding(.horizontal, Space.x6)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(p.background)
    }
}

/// Экран входа. Отсюда же уходят на регистрацию и восстановление пароля.
struct LoginScreen: View {
    @Environment(\.palette) private var p
    @ObservedObject var vm: CarViewModel
    let onLoggedIn: () -> Void
    let onRegister: () -> Void
    let onForgot: () -> Void

    @State private var email: String
    @State private var pass = ""
    @State private var error: String? = nil
    @State private var busy = false
    @State private var showHelp = false
    @State private var showNews = false
    @State private var showShop = false

    init(vm: CarViewModel, onLoggedIn: @escaping () -> Void, onRegister: @escaping () -> Void, onForgot: @escaping () -> Void) {
        self.vm = vm
        self.onLoggedIn = onLoggedIn
        self.onRegister = onRegister
        self.onForgot = onForgot
        // Почту помним между входами.
        _email = State(initialValue: vm.settings.email ?? "")
    }

    var body: some View {
        if showShop {
            ShopScreen(products: vm.products, loggedIn: false, model: nil,
                                   onOrder: { id, qty, phone, comment, done in vm.order(id, qty: qty, phone: phone, comment: comment, done: done) },
                                   onBack: { showShop = false }, onRefresh: { vm.loadProducts() })
        } else if showNews {
            NewsScreen(items: vm.news, isRead: { vm.isRead($0) }, onRead: { vm.markRead($0) },
                       onReadAll: { vm.markAllRead() }, onBack: { showNews = false }, onRefresh: { vm.loadNews() })
        } else {
            form
        }
    }

    private var form: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer().frame(height: Space.x8)
                // Новости «для всех» видны и до входа
                HStack(spacing: Space.x2) { Spacer(); LangPicker(compact: true); ShopFab { showShop = true }; NewsBell(unread: vm.unreadNews) { showNews = true }; HelpFab { showHelp = true } }
                Spacer().frame(height: Space.x6)
                BrandLockup(markSize: 44).frame(maxWidth: .infinity)
                Spacer().frame(height: Space.x1)
                Text(L("Управление вашим электромобилем")).font(.system(size: 14)).foregroundStyle(p.textSecondary)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)

                Spacer().frame(height: Space.x8)
                AuthField(label: "EMAIL", value: $email, keyboard: .emailAddress, contentType: .username)
                Spacer().frame(height: Space.x4)
                AuthField(label: L("ПАРОЛЬ"), value: $pass, secure: true, contentType: .password)

                if let error {
                    Spacer().frame(height: Space.x3)
                    ElectroToast(kind: .failed, title: error)
                }

                Spacer().frame(height: Space.x4)
                HStack {
                    Button(action: onForgot) {
                        Text(L("Забыли пароль?")).font(.system(size: 14)).foregroundStyle(p.textSecondary)
                    }
                    .buttonStyle(.plain).disabled(busy)
                    Spacer()
                    Button(action: onRegister) {
                        Text(L("Регистрация")).font(.system(size: 14, weight: .semibold)).foregroundStyle(p.accent)
                    }
                    .buttonStyle(.plain).disabled(busy)
                }
                .padding(.vertical, Space.x1)

                Spacer().frame(height: Space.x8)
                ElectroButton(
                    text: busy ? L("Входим…") : L("Войти"),
                    enabled: !email.trimmingCharacters(in: .whitespaces).isEmpty && !pass.isEmpty,
                    loading: busy
                ) {
                    busy = true; error = nil
                    Task {
                        do {
                            try await vm.signIn(email: email.trimmingCharacters(in: .whitespaces), password: pass)
                            onLoggedIn()
                        } catch {
                            self.error = vm.authError(error, fallback: L("Неверный email или пароль"))
                        }
                        busy = false
                    }
                }
                Spacer().frame(height: Space.x6)
                Link(L("Политика конфиденциальности"), destination: Links.privacy)
                    .font(ElectroType.caption).foregroundStyle(p.textMuted).frame(maxWidth: .infinity)
                Spacer().frame(height: Space.x4)
            }
            .padding(.horizontal, Space.x6)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(p.background)
        .sheet(isPresented: $showHelp) { HelpSheet(support: vm.support, feedbackVM: nil) }
        .onAppear { vm.loadNews() }
    }
}

/// Регистрация. Учётка общая с сайтом. Машину регистрация не открывает —
/// её даёт только привязка по QR.
struct RegisterScreen: View {
    @ObservedObject var vm: CarViewModel
    let onRegistered: () -> Void
    let onBack: () -> Void

    @State private var email = ""
    @State private var name = ""
    @State private var phone = ""
    @State private var pass = ""
    @State private var repeatPass = ""
    @State private var error: String? = nil
    @State private var busy = false

    var body: some View {
        AuthScaffold(
            title: L("Регистрация"),
            subtitle: L("Один аккаунт для приложения и для сайта."),
            action: busy ? L("Создаём…") : L("Создать аккаунт"),
            busy: busy,
            enabled: !email.trimmingCharacters(in: .whitespaces).isEmpty && pass.count >= MIN_PASSWORD && !repeatPass.isEmpty,
            error: error,
            onBack: onBack,
            onAction: {
                if pass != repeatPass { error = L("Пароли не совпадают"); return }
                busy = true; error = nil
                Task {
                    do {
                        try await vm.register(
                            email: email.trimmingCharacters(in: .whitespaces), password: pass,
                            name: name.trimmingCharacters(in: .whitespaces), phone: phone.trimmingCharacters(in: .whitespaces)
                        )
                        onRegistered()
                    } catch {
                        self.error = vm.authError(error, fallback: L("Не получилось создать аккаунт"))
                    }
                    busy = false
                }
            }
        ) {
            AuthField(label: "EMAIL", value: $email, keyboard: .emailAddress, contentType: .username)
            AuthField(label: L("ИМЯ (необязательно)"), value: $name, contentType: .name, autocapitalize: true)
            AuthField(label: L("ТЕЛЕФОН (необязательно)"), value: $phone, keyboard: .phonePad, contentType: .telephoneNumber)
            VStack(alignment: .leading, spacing: Space.x1) {
                AuthField(label: L("ПАРОЛЬ"), value: $pass, secure: true, contentType: .newPassword)
                Hint(text: L("Не короче {0} знаков", MIN_PASSWORD))
            }
            AuthField(label: L("ПАРОЛЬ ЕЩЁ РАЗ"), value: $repeatPass, secure: true, contentType: .newPassword)
        }
    }
}

/// «Забыли пароль»: заявка уходит мастеру, писем сервер не шлёт.
struct ForgotPasswordScreen: View {
    @ObservedObject var vm: CarViewModel
    let onBack: () -> Void

    @State private var email: String
    @State private var contact = ""
    @State private var error: String? = nil
    @State private var sent = false
    @State private var busy = false

    init(vm: CarViewModel, onBack: @escaping () -> Void) {
        self.vm = vm
        self.onBack = onBack
        _email = State(initialValue: vm.settings.email ?? "")
    }

    var body: some View {
        AuthScaffold(
            title: L("Забыли пароль"),
            subtitle: L("Заявка уйдёт мастеру: он выдаст новый пароль и сообщит его вам."),
            action: busy ? L("Отправляем…") : (sent ? L("Вернуться ко входу") : L("Отправить заявку")),
            busy: busy,
            enabled: sent || !email.trimmingCharacters(in: .whitespaces).isEmpty,
            error: error,
            note: sent ? L("Заявка принята. Мастер сбросит пароль и сообщит новый — если такая учётная запись существует.") : nil,
            onBack: onBack,
            onAction: {
                if sent { onBack(); return }
                busy = true; error = nil
                Task {
                    do {
                        try await vm.requestPasswordReset(
                            email: email.trimmingCharacters(in: .whitespaces),
                            contact: contact.trimmingCharacters(in: .whitespaces)
                        )
                        sent = true
                    } catch {
                        self.error = vm.authError(error, fallback: L("Не получилось отправить заявку"))
                    }
                    busy = false
                }
            }
        ) {
            AuthField(label: "EMAIL", value: $email, keyboard: .emailAddress, contentType: .username)
            VStack(alignment: .leading, spacing: Space.x1) {
                AuthField(label: L("ТЕЛЕФОН ИЛИ TELEGRAM"), value: $contact)
                Hint(text: L("Куда сообщить новый пароль. Необязательно, но так быстрее."))
            }
        }
    }
}

/// Ожидание списка машин: спиннер, пока сервер не ответил; причина и кнопки — если отказал.
struct ParkGateScreen: View {
    @Environment(\.palette) private var p
    let note: String?
    let onRetry: () -> Void
    let onLogout: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            if let note {
                Text(note).font(.system(size: 15)).foregroundStyle(p.textPrimary).multilineTextAlignment(.center)
                Spacer().frame(height: Space.x6)
                ElectroButton(text: L("Повторить"), action: onRetry)
                Spacer().frame(height: Space.x2)
                ElectroButton(text: L("Выйти из аккаунта"), style: .ghost, action: onLogout)
            } else {
                ProgressView().tint(p.accent).scaleEffect(1.4)
                Spacer().frame(height: Space.x4)
                Text(L("Спрашиваем сервер о ваших машинах…")).font(.system(size: 14)).foregroundStyle(p.textSecondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
    }
}

/// Экран-ворота: пока у аккаунта нет машины, доступен только сканер QR.
struct PairScreen: View {
    @Environment(\.palette) private var p
    @ObservedObject var vm: CarViewModel
    let onPaired: () -> Void

    @State private var busy = false
    @State private var note: String? = nil
    @State private var error: String? = nil
    @State private var scanning = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("Подключение авто")).font(.system(size: 18, weight: .semibold)).foregroundStyle(p.textPrimary)
                .padding(.top, Space.x2)
            Spacer()
            VStack(spacing: 0) {
                Image(systemName: "qrcode").font(.system(size: 64, weight: .regular)).foregroundStyle(p.accent)
                    .frame(width: 132, height: 132)
                    .background(p.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Radius.xl, style: .continuous).stroke(p.accent, lineWidth: 2))
                Spacer().frame(height: Space.x6)
                Text(L("Подключите машину")).font(.system(size: 22, weight: .bold)).foregroundStyle(p.textPrimary)
                Spacer().frame(height: Space.x2)
                Text(L("Откройте на экране машины «Привязать телефон» и отсканируйте QR"))
                    .font(.system(size: 14)).foregroundStyle(p.textSecondary).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)

            if let error {
                Spacer().frame(height: Space.x4)
                ElectroToast(kind: .failed, title: error)
            }
            if let note {
                Spacer().frame(height: Space.x4)
                ElectroToast(kind: .success, title: note)
            }

            Spacer()
            Spacer().frame(height: Space.x8)
            ElectroButton(text: busy ? L("Привязываем…") : L("Сканировать QR машины"), enabled: !busy, loading: busy) {
                scanning = true
            }
            // Выход отсюда же: зашли не в тот аккаунт или QR пока негде взять.
            Spacer().frame(height: Space.x2)
            ElectroButton(text: L("Выйти из аккаунта"), style: .ghost, enabled: !busy) { vm.logout() }
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
        .fullScreenCover(isPresented: $scanning) {
            QRScannerScreen { payload in
                scanning = false
                guard let payload else { return }
                claim(payload)
            }
        }
    }

    private func claim(_ payload: String) {
        busy = true; error = nil; note = nil
        Task {
            do {
                note = try await vm.claimPairing(payload)
                onPaired()
            } catch {
                self.error = (error as? RepoError)?.message ?? L("Не удалось привязать машину")
            }
            busy = false
        }
    }
}

// MARK: - Помощь

/// Диалог «Помощь» — те же контакты поддержки, что на голове.
struct HelpSheet: View {
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let support: SupportDto?
    /// Отзыв доступен только под аккаунтом: без ключа серверу его не принять.
    var feedbackVM: CarViewModel? = nil
    @State private var feedback = false

    var body: some View {
        if feedback, let vm = feedbackVM {
            FeedbackView(vm: vm) { dismiss() }
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        } else {
            help
        }
    }

    private var help: some View {
        VStack(alignment: .leading, spacing: Space.x3) {
            HStack {
                Text(L("Помощь")).font(ElectroType.headline).foregroundStyle(p.textPrimary)
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").foregroundStyle(p.textSecondary).frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
            }
            if let s = support, s.any {
                Text(L("Свяжитесь с поддержкой удобным способом:")).font(.system(size: 13)).foregroundStyle(p.textSecondary)
                if !s.phone.isEmpty {
                    helpLine(L("Телефон"), s.phone) {
                        let digits = s.phone.filter { $0 == "+" || $0.isNumber }
                        if let u = URL(string: "tel:" + digits) { openURL(u) }
                    }
                }
                if !s.telegram.isEmpty { helpLine("Telegram", s.telegram) { if let u = URL(string: tgLink(s.telegram)) { openURL(u) } } }
                if !s.instagram.isEmpty { helpLine("Instagram", s.instagram) { if let u = URL(string: igLink(s.instagram)) { openURL(u) } } }
                if !s.site.isEmpty { helpLine(L("Сайт"), s.site) { if let u = URL(string: webLink(s.site)) { openURL(u) } } }
            } else {
                Text(L("Контакты поддержки пока не заданы.")).font(.system(size: 13)).foregroundStyle(p.textMuted)
            }
            if feedbackVM != nil {
                Spacer().frame(height: Space.x2)
                Text(L("Нашли ошибку или есть идея — напишите нам прямо отсюда.")).font(.system(size: 12)).foregroundStyle(p.textMuted)
                ElectroButton(text: L("Оставить отзыв"), style: .secondary) { feedback = true }
            }
            Spacer()
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(p.surfaceElevated)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    private func helpLine(_ label: String, _ value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).font(.system(size: 13)).foregroundStyle(p.textMuted).frame(width: 96, alignment: .leading)
                Text(value).font(.system(size: 15)).foregroundStyle(p.accent).lineLimit(1)
                Spacer()
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

func tgLink(_ v: String) -> String {
    if v.hasPrefix("http") { return v }
    var s = v.trimmingCharacters(in: .whitespaces)
    if s.hasPrefix("@") { s.removeFirst() }
    if s.hasPrefix("https://t.me/") { s = String(s.dropFirst("https://t.me/".count)) }
    return "https://t.me/" + s
}

func igLink(_ v: String) -> String {
    if v.hasPrefix("http") { return v }
    var s = v.trimmingCharacters(in: .whitespaces)
    if s.hasPrefix("@") { s.removeFirst() }
    return "https://instagram.com/" + s
}

func webLink(_ v: String) -> String {
    v.hasPrefix("http") ? v : "https://" + v.trimmingCharacters(in: .whitespaces)
}
