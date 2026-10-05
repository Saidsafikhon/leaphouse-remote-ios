import SwiftUI

/// «Наши продукты» — другие программы EvOn. Список с сервера AppsMarket
/// (apps.evon.uz, platform=phone): сначала сохранённая копия, потом обновление.
/// Тап — открыть ссылку продукта во внешнем браузере; без ссылки карточка неактивна.
/// Двойник `ui/OurProductsScreen.kt` на Android.
struct OurProductsScreen: View {
    @Environment(\.palette) private var p
    @Environment(\.openURL) private var openURL
    @ObservedObject private var lang = Lang.shared
    let onBack: () -> Void

    @State private var items: [EvonProduct] = []
    @State private var loading = true
    @State private var offline = false

    var body: some View {
        ScreenScaffold(title: L("Наши продукты"), onBack: onBack) {
            Text("EvOn").font(ElectroType.caption).foregroundStyle(p.textMuted)
            if offline {
                HStack(spacing: Space.x2) {
                    Image(systemName: "icloud.slash").font(.system(size: 13)).foregroundStyle(p.textMuted)
                    Text(items.isEmpty ? L("Нет связи — список продуктов не загрузился") : L("Нет связи — показан сохранённый список"))
                        .font(ElectroType.caption).foregroundStyle(p.textMuted)
                }
            }
            if items.isEmpty {
                if loading {
                    ProgressView().tint(p.accent).frame(maxWidth: .infinity).padding(Space.x6)
                } else if !offline {
                    EmptyNote(text: L("Список продуктов пока пуст."))
                }
            }
            ForEach(items) { item in
                EvonProductCard(product: item) { open(item) }
            }
        }
        // Обычный показ — из кэша (сервер не чаще раза в сутки); «потянуть вниз» — сверка сразу.
        .refreshable { await reload(force: true) }
        .task(id: lang.code) {
            items = EvonProducts.cached(lang: lang.code)
            await reload(force: false)
        }
    }

    private func reload(force: Bool) async {
        let code = lang.code
        loading = true
        do {
            items = try await EvonProducts.refresh(lang: code, force: force)
            offline = false
        } catch {
            offline = true
        }
        loading = false
    }

    private func open(_ item: EvonProduct) {
        guard !item.url.isEmpty, let url = URL(string: item.url) else { return }
        openURL(url)
    }
}

private struct EvonProductCard: View {
    @Environment(\.palette) private var p
    let product: EvonProduct
    let onOpen: () -> Void

    var body: some View {
        let canOpen = !product.url.isEmpty
        VStack(alignment: .leading, spacing: Space.x3) {
            HStack(alignment: .center, spacing: Space.x3) {
                CoverImage(url: product.iconUrl.flatMap { URL(string: $0) }, height: 56,
                           corner: Radius.sm, placeholder: "square.grid.2x2")
                    .frame(width: 56, height: 56)
                VStack(alignment: .leading, spacing: 2) {
                    Text(product.title).font(ElectroType.body.weight(.semibold)).foregroundStyle(p.textPrimary)
                        .lineLimit(2)
                    if !product.description.isEmpty {
                        Text(product.description).font(ElectroType.caption).foregroundStyle(p.textSecondary)
                            .lineLimit(4)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            ElectroButton(text: L("Открыть"), style: .secondary, enabled: canOpen, action: onOpen)
        }
        .padding(Space.x4)
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(p.outline, lineWidth: 1))
        .contentShape(Rectangle())
        .onTapGesture { if canOpen { onOpen() } }
    }
}
