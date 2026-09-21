import SwiftUI

/// Магазин аксессуаров и доп. оборудования: витрина карточками, карточка товара с
/// описанием и заявкой (телефон + комментарий). Оплаты в приложении нет — менеджер
/// перезванивает. Без входа витрину видно, заказать — только после входа.
struct ShopScreen: View {
    @Environment(\.palette) private var p
    let products: [ProductDto]
    let loggedIn: Bool
    let model: String?
    let onOrder: (_ productId: String, _ qty: Int, _ phone: String, _ comment: String, _ done: @escaping (String?) -> Void) -> Void
    let onBack: () -> Void
    let onRefresh: () -> Void

    @State private var category = "all"
    @State private var selected: ProductDto? = nil

    private var categories: [(String, String)] {
        [("all", L("Все")), ("accessory", L("Аксессуары")), ("equipment", L("Оборудование")),
         ("care", L("Уход")), ("tuning", L("Тюнинг")), ("other", L("Другое"))]
    }

    var body: some View {
        // сначала товары для своей модели, потом общие, потом остальные
        let m = (model ?? "").uppercased()
        let shown = products
            .filter { category == "all" || $0.category == category }
            .sorted { a, b in rank(a, m) < rank(b, m) }
        let columns = [GridItem(.flexible(), spacing: Space.x3), GridItem(.flexible(), spacing: Space.x3)]
        ScreenScaffold(title: L("Магазин"), onBack: onBack) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(categories, id: \.0) { c in
                        let on = category == c.0
                        Button { category = c.0 } label: {
                            Text(c.1).font(ElectroType.body).foregroundStyle(on ? p.accent : p.textPrimary)
                                .padding(.horizontal, Space.x4).frame(height: ControlSize.chip)
                                .background(on ? p.accent.opacity(0.14) : p.surfaceElevated)
                                .clipShape(Capsule())
                                .overlay(Capsule().stroke(on ? p.accent : .clear, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            if shown.isEmpty {
                EmptyNote(text: products.isEmpty ? L("Товары скоро появятся.") : L("В этом разделе пусто."))
            }
            LazyVGrid(columns: columns, spacing: Space.x3) {
                ForEach(shown) { pr in
                    ProductCard(product: pr) { selected = pr }
                }
            }
        }
        .refreshable { onRefresh() }
        .onAppear(perform: onRefresh)
        .fullScreenCover(item: $selected) { pr in
            ProductScreen(product: pr, loggedIn: loggedIn, onOrder: onOrder) { selected = nil }
        }
    }

    private func rank(_ pr: ProductDto, _ m: String) -> Int {
        if pr.models.isEmpty { return 1 }
        if !m.isEmpty && pr.models.uppercased().contains(m) { return 0 }
        return 2
    }
}

func formatPrice(_ price: Int64, _ currency: String) -> String {
    let f = NumberFormatter(); f.numberStyle = .decimal; f.groupingSeparator = " "; f.usesGroupingSeparator = true
    let n = f.string(from: NSNumber(value: price)) ?? "\(price)"
    return currency == "USD" ? "$\(n)" : L("{0} сум", n)
}

private struct ProductCard: View {
    @Environment(\.palette) private var p
    let product: ProductDto
    let onTap: () -> Void

    var body: some View {
        let discount = (product.old_price ?? 0) > product.price
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                ZStack(alignment: .topLeading) {
                    Group {
                        if let s = product.image_url, let u = URL(string: s) {
                            AsyncImage(url: u) { phase in
                                if let img = phase.image { img.resizable().scaledToFill() } else { p.surfaceElevated }
                            }
                        } else {
                            ZStack { p.surfaceElevated; Image(systemName: "bag").font(.system(size: 30)).foregroundStyle(p.textMuted) }
                        }
                    }
                    .aspectRatio(1, contentMode: .fill).frame(maxWidth: .infinity).clipped()
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                    if discount, let old = product.old_price {
                        Text("-\(100 - Int(product.price * 100 / old))%")
                            .font(.system(size: 10, weight: .bold)).foregroundStyle(p.onAccent)
                            .padding(.horizontal, 7).padding(.vertical, 2).background(p.accent).clipShape(Capsule())
                            .padding(8)
                    }
                }
                Text(product.title).font(ElectroType.body.weight(.semibold)).foregroundStyle(p.textPrimary)
                    .lineLimit(2).frame(minHeight: 40, alignment: .topLeading).padding(.top, 4)
                Text(formatPrice(product.price, product.currency)).font(ElectroType.body.weight(.bold)).foregroundStyle(p.accent)
                if discount, let old = product.old_price {
                    Text(formatPrice(old, product.currency)).font(ElectroType.unit).foregroundStyle(p.textMuted).strikethrough()
                }
            }
            .padding(Space.x3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(p.surface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(p.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Карточка товара: фото, цена, описание, заявка.
private struct ProductScreen: View {
    @Environment(\.palette) private var p
    @Environment(\.openURL) private var openURL
    let product: ProductDto
    let loggedIn: Bool
    let onOrder: (String, Int, String, String, @escaping (String?) -> Void) -> Void
    let onClose: () -> Void

    @State private var qty = 1
    @State private var phone = ""
    @State private var comment = ""
    @State private var busy = false
    @State private var sent = false
    @State private var error: String? = nil
    @State private var showForm = false

    var body: some View {
        let discount = (product.old_price ?? 0) > product.price
        VStack(spacing: 0) {
            HStack(spacing: Space.x3) {
                Button(action: onClose) {
                    Image(systemName: "chevron.left").font(.system(size: 20, weight: .medium)).foregroundStyle(p.textPrimary).frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                Text(L("Товар")).font(ElectroType.headline).foregroundStyle(p.textPrimary)
                Spacer()
            }
            .padding(Space.x4)
            ScrollView {
                VStack(alignment: .leading, spacing: Space.x4) {
                    if let s = product.image_url, let u = URL(string: s) {
                        AsyncImage(url: u) { phase in
                            if let img = phase.image { img.resizable().scaledToFill() } else { p.surfaceElevated }
                        }
                        .frame(maxWidth: .infinity).frame(height: 240).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
                    }
                    Text(product.title).font(ElectroType.title).foregroundStyle(p.textPrimary)
                    HStack(alignment: .lastTextBaseline, spacing: Space.x2) {
                        Text(formatPrice(product.price, product.currency)).font(ElectroType.headline).foregroundStyle(p.accent)
                        if discount, let old = product.old_price {
                            Text(formatPrice(old, product.currency)).font(ElectroType.caption).foregroundStyle(p.textMuted).strikethrough()
                        }
                    }
                    if !product.models.isEmpty {
                        Text(L("Подходит: {0}", product.models)).font(ElectroType.caption).foregroundStyle(p.textSecondary)
                    }
                    if !product.description.isEmpty {
                        Text(product.description).font(ElectroType.body).foregroundStyle(p.textSecondary).fixedSize(horizontal: false, vertical: true)
                    }
                    if let l = product.link, let u = URL(string: l) {
                        Button { openURL(u) } label: {
                            Text(L("Подробнее на сайте")).font(ElectroType.body.weight(.semibold)).foregroundStyle(p.accent)
                        }
                        .buttonStyle(.plain)
                    }
                    if sent {
                        ElectroToast(kind: .success, title: L("Заявка отправлена"), message: L("Менеджер свяжется с вами по указанному телефону."))
                    } else if showForm {
                        VStack(alignment: .leading, spacing: Space.x3) {
                            Text(L("Заявка на покупку")).font(ElectroType.body.weight(.semibold)).foregroundStyle(p.textPrimary)
                            HStack {
                                Text(L("Количество")).font(ElectroType.body).foregroundStyle(p.textSecondary)
                                Spacer()
                                qtyButton("minus") { if qty > 1 { qty -= 1 } }
                                Text("\(qty)").font(ElectroType.headline).foregroundStyle(p.textPrimary).padding(.horizontal, Space.x3)
                                qtyButton("plus") { if qty < 99 { qty += 1 } }
                            }
                            AuthField(label: L("ТЕЛЕФОН"), value: $phone, keyboard: .phonePad, contentType: .telephoneNumber)
                            AuthField(label: L("КОММЕНТАРИЙ (необязательно)"), value: $comment, autocapitalize: true)
                            if let e = error { Text(e).font(ElectroType.caption).foregroundStyle(p.danger) }
                            ElectroButton(text: busy ? L("Отправляем…") : L("Отправить заявку"), enabled: !busy && phone.trimmingCharacters(in: .whitespaces).count >= 5) {
                                busy = true; error = nil
                                onOrder(product.id, qty, phone.trimmingCharacters(in: .whitespaces), comment.trimmingCharacters(in: .whitespaces)) { err in
                                    busy = false
                                    if let err { error = err } else { sent = true }
                                }
                            }
                        }
                        .padding(Space.x4)
                        .background(p.surface)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(p.outline, lineWidth: 1))
                    }
                }
                .padding(.horizontal, Space.x5)
                .padding(.bottom, Space.x6)
            }
            .scrollDismissesKeyboard(.interactively)
            if !sent && !showForm {
                if loggedIn {
                    ElectroButton(text: L("Заказать")) { showForm = true }
                        .padding(.horizontal, Space.x5).padding(.bottom, Space.x4)
                } else {
                    Text(L("Войдите в аккаунт, чтобы оставить заявку")).font(ElectroType.caption).foregroundStyle(p.textMuted)
                        .frame(maxWidth: .infinity).padding(Space.x5)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background)
    }

    private func qtyButton(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 15, weight: .semibold)).foregroundStyle(p.textPrimary)
                .frame(width: 36, height: 36).background(p.surfaceElevated).clipShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

/// Кнопка «Магазин» — сумка в кругляше, рядом с колокольчиком.
struct ShopFab: View {
    @Environment(\.palette) private var p
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: "bag").font(.system(size: 18, weight: .medium)).foregroundStyle(p.textSecondary)
                .frame(width: 44, height: 44)
                .background(p.surfaceElevated)
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Магазин"))
    }
}
