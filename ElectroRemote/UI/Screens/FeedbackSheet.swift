import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

/// Отзыв из приложения: вид + текст + до трёх вложений (скриншоты или записи
/// экрана из галереи через системный PhotosPicker — без разрешений на фото).
/// Уходит в админку; ответа в приложении не будет. Тестовая функция на время обкатки.
struct FeedbackView: View {
    @Environment(\.palette) private var p
    @ObservedObject var vm: CarViewModel
    let onClose: () -> Void

    @State private var kind = "bug"
    @State private var text = ""
    @State private var picked: [PhotosPickerItem] = []
    @State private var thumbs: [UIImage?] = []
    @State private var busy = false
    @State private var error: String? = nil
    @State private var sent = false

    private static let kinds: [(String, String)] = [("bug", "Ошибка"), ("idea", "Идея"), ("other", "Другое")]
    private static let maxFiles = 3

    var body: some View {
        VStack(alignment: .leading, spacing: Space.x3) {
            HStack {
                Text(sent ? "Спасибо!" : "Отзыв").font(ElectroType.headline).foregroundStyle(p.textPrimary)
                Spacer()
                Button { onClose() } label: {
                    Image(systemName: "xmark").foregroundStyle(p.textSecondary).frame(width: 32, height: 32)
                }
                .buttonStyle(.plain).disabled(busy)
            }
            if sent {
                Text("Отзыв отправлен. Мы читаем каждый.").font(.system(size: 14)).foregroundStyle(p.textSecondary)
                Spacer()
                ElectroButton(text: "Закрыть") { onClose() }
            } else {
                Text("Что не так, чего не хватает или что было бы удобнее — напишите, это уйдёт разработчикам.")
                    .font(.system(size: 13)).foregroundStyle(p.textSecondary)
                HStack(spacing: 6) {
                    ForEach(Self.kinds, id: \.0) { code, label in
                        let sel = kind == code
                        Button { kind = code } label: {
                            Text(label).font(.system(size: 12, weight: .medium))
                                .foregroundStyle(sel ? p.onAccent : p.textSecondary)
                                .frame(maxWidth: .infinity).frame(height: 34)
                                .background(sel ? p.accent : Color.clear)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(sel ? p.accent : p.outline, lineWidth: 1))
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .buttonStyle(.plain)
                    }
                }
                ZStack(alignment: .topLeading) {
                    TextEditor(text: $text)
                        .font(.system(size: 14)).foregroundStyle(p.textPrimary)
                        .scrollContentBackground(.hidden)
                        .padding(8)
                        .frame(minHeight: 110, maxHeight: 160)
                        .background(p.surface)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.outline, lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .disabled(busy)
                    if text.isEmpty {
                        Text("Например: климат включился сам в 8:10, машина C10")
                            .font(.system(size: 13)).foregroundStyle(p.textMuted)
                            .padding(.horizontal, 13).padding(.top, 16).allowsHitTesting(false)
                    }
                }
                // вложения: миниатюры + «добавить»; тап по миниатюре убирает файл
                HStack(spacing: 8) {
                    ForEach(Array(picked.enumerated()), id: \.offset) { i, _ in
                        Button { remove(i) } label: {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10).fill(p.surface)
                                if i < thumbs.count, let img = thumbs[i] {
                                    Image(uiImage: img).resizable().scaledToFill().frame(width: 64, height: 64)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                } else {
                                    Image(systemName: "photo").foregroundStyle(p.textMuted)
                                }
                            }
                            .frame(width: 64, height: 64)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(p.outline, lineWidth: 1))
                        }
                        .buttonStyle(.plain).disabled(busy)
                    }
                    if picked.count < Self.maxFiles {
                        PhotosPicker(selection: $picked, maxSelectionCount: Self.maxFiles, matching: .any(of: [.images, .videos])) {
                            Image(systemName: "plus.circle").font(.system(size: 26)).foregroundStyle(p.accent)
                                .frame(width: 64, height: 64)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(p.outline, lineWidth: 1))
                        }
                        .disabled(busy)
                    }
                }
                Text(picked.isEmpty ? "Можно приложить скриншот или запись экрана (до \(Self.maxFiles), по 40 МБ)." : "Нажмите на файл, чтобы убрать.")
                    .font(.system(size: 11)).foregroundStyle(p.textMuted)
                if let error { Text(error).font(.system(size: 13)).foregroundStyle(p.danger) }
                Spacer()
                HStack(spacing: Space.x2) {
                    ElectroButton(text: "Отмена", style: .ghost, enabled: !busy) { onClose() }
                    ElectroButton(text: "Отправить", enabled: !busy && text.trimmingCharacters(in: .whitespacesAndNewlines).count >= 3, loading: busy) { send() }
                }
            }
        }
        .padding(Space.x6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(p.surfaceElevated)
        .onChange(of: picked) { _, items in loadThumbs(items) }
    }

    private func remove(_ i: Int) {
        guard i < picked.count else { return }
        picked.remove(at: i)
        if i < thumbs.count { thumbs.remove(at: i) }
    }

    private func loadThumbs(_ items: [PhotosPickerItem]) {
        thumbs = Array(repeating: nil, count: items.count)
        for (i, item) in items.enumerated() {
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                    let side: CGFloat = 192
                    let k = side / max(img.size.width, img.size.height)
                    let size = CGSize(width: img.size.width * k, height: img.size.height * k)
                    let small = UIGraphicsImageRenderer(size: size).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
                    if i < thumbs.count { thumbs[i] = small }
                }
            }
        }
    }

    private func send() {
        busy = true; error = nil
        Task {
            let files = await loadFiles()
            let r = await vm.sendFeedback(kind: kind, text: text.trimmingCharacters(in: .whitespacesAndNewlines), files: files, requested: picked.count)
            busy = false
            if let r { error = r } else { sent = true }
        }
    }

    /// Читаем выбранное в память: тип — по UTType вложения, имя — по расширению.
    private func loadFiles() async -> [CarRepository.FeedbackFile] {
        var out: [CarRepository.FeedbackFile] = []
        for item in picked {
            guard let data = try? await item.loadTransferable(type: Data.self), data.count <= 40 * 1024 * 1024 else { continue }
            let type = item.supportedContentTypes.first
            let mime = type?.preferredMIMEType ?? (type?.conforms(to: .movie) == true ? "video/mp4" : "image/jpeg")
            let ext = type?.preferredFilenameExtension ?? "bin"
            out.append(.init(name: "attachment.\(ext)", mime: mime, data: data))
        }
        return out
    }
}
