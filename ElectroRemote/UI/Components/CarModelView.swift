import SwiftUI
import SceneKit
import GLTFKit2

/// Трёхмерная машина на главной — паритет с Android (`CarModelView.kt`):
/// GLB с сервера (`/models/<model>.glb`, из 3D-моделей головы Leapmotor), SceneKit через
/// GLTFKit2. Палец крутит (yaw) и наклоняет (pitch, ограничен); цвет кузова — материал
/// «M_Paint» (у C01 «M_CarPaint») из настроек. Модель качается один раз в Caches.
enum CarModels {
    static let base = "https://leapmotor.evon.uz/models/"
    /// Поднимать при перевыпуске GLB на сервере — старый кеш сотрётся.
    static let version = 2

    static func fileFor(_ model: String?) -> String? {
        let m = (model ?? "").uppercased()
        return ["C16", "C10", "C11", "C01"].first { m.contains($0) }.map { $0.lowercased() + ".glb" }
    }

    static func cacheURL(_ name: String) -> URL {
        let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("models")
        if let items = try? FileManager.default.contentsOfDirectory(atPath: root.path) {
            for it in items where it != "v\(version)" { try? FileManager.default.removeItem(at: root.appendingPathComponent(it)) }
        }
        let dir = root.appendingPathComponent("v\(version)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(name)
    }

    /// Скачать, если ещё нет; nil — не вышло (сеть).
    static func ensure(_ name: String) async -> URL? {
        let f = cacheURL(name)
        if let a = try? FileManager.default.attributesOfItem(atPath: f.path), (a[.size] as? Int ?? 0) > 1024 { return f }
        guard let url = URL(string: base + name), let (tmp, resp) = try? await URLSession.shared.download(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        try? FileManager.default.removeItem(at: f)
        return (try? FileManager.default.moveItem(at: tmp, to: f)) == nil ? nil : f
    }

    static func prefetch(_ model: String?) { if let n = fileFor(model) { Task { _ = await ensure(n) } } }
}

/// Загруженная сцена живёт в процессе — возврат на главную не перезагружает модель.
final class CarSceneCache {
    static let shared = CarSceneCache()
    var name: String?
    var scene: SCNScene?
    var root: SCNNode?
}

struct CarModelView: View {
    let model: String?
    let paint: Color
    var onReady: (Bool) -> Void = { _ in }

    @State private var scene: SCNScene? = nil
    @State private var yaw: Float = 0
    @State private var pitch: Float = 0
    @State private var dragStart: (Float, Float)? = nil

    var body: some View {
        Group {
            if let s = scene {
                CarSceneView(scene: s, paint: UIColor(paint), yaw: yaw, pitch: pitch)
                    .highPriorityGesture(
                        DragGesture(minimumDistance: 4)
                            .onChanged { g in
                                if dragStart == nil { dragStart = (yaw, pitch) }
                                let (y0, p0) = dragStart!
                                yaw = y0 + Float(g.translation.width) * 0.01
                                pitch = min(0.9, max(-0.35, p0 + Float(g.translation.height) * 0.006))
                            }
                            .onEnded { _ in dragStart = nil }
                    )
            } else {
                Color.clear
            }
        }
        .task(id: CarModels.fileFor(model) ?? "") {
            guard let name = CarModels.fileFor(model) else { onReady(false); return }
            if CarSceneCache.shared.name == name, let s = CarSceneCache.shared.scene { scene = s; onReady(true); return }
            guard let url = await CarModels.ensure(name) else { onReady(false); return }
            let loaded: SCNScene? = await withCheckedContinuation { cont in
                GLTFAsset.load(with: url, options: [:]) { _, status, asset, _, _ in
                    if status == .complete, let asset { cont.resume(returning: GLTFSCNSceneSource(asset: asset).defaultScene) }
                    else if status == .error { cont.resume(returning: nil) }
                }
            }
            guard let s = loaded else { onReady(false); return }
            CarSceneView.prepare(s)
            CarSceneCache.shared.name = name; CarSceneCache.shared.scene = s
            scene = s
            // кадр успевает отрисоваться — только потом убираем статичную картинку
            try? await Task.sleep(nanoseconds: 150_000_000)
            onReady(true)
        }
    }
}

/// SCNView с прозрачным фоном, камерой в три четверти и студийным окружением.
struct CarSceneView: UIViewRepresentable {
    let scene: SCNScene
    let paint: UIColor
    let yaw: Float
    let pitch: Float

    /// Камера — как на Android: цель в центре модели (единичный куб), взгляд спереди-слева чуть сверху.
    static let eye = SCNVector3(-1.45, 0.45, 1.8)
    /// «Правая» ось камеры — вокруг неё наклон, чтобы вертикальный свайп работал как орбита.
    static var rightAxis: SCNVector3 {
        let f = SCNVector3(-eye.x, 0, -eye.z)          // forward без y
        let r = SCNVector3(-f.z, 0, f.x)               // forward × up
        let n = sqrt(r.x * r.x + r.z * r.z); return SCNVector3(r.x / n, 0, r.z / n)
    }

    /// Один раз после загрузки: масштаб в единичный куб, центр в начале координат, окружение, узлы-повороты.
    static func prepare(_ scene: SCNScene) {
        let content = SCNNode()
        for n in scene.rootNode.childNodes { n.removeFromParentNode(); content.addChildNode(n) }
        let (mn, mx) = content.boundingBox
        let size = max(mx.x - mn.x, max(mx.y - mn.y, mx.z - mn.z))
        let s = size > 0 ? 1 / size : 1
        content.scale = SCNVector3(s, s, s)
        content.position = SCNVector3(-(mn.x + mx.x) / 2 * s, -(mn.y + mx.y) / 2 * s, -(mn.z + mx.z) / 2 * s)
        let yawNode = SCNNode(); yawNode.name = "yaw"; yawNode.addChildNode(content)
        let pitchNode = SCNNode(); pitchNode.name = "pitch"; pitchNode.addChildNode(yawNode)
        scene.rootNode.addChildNode(pitchNode)

        scene.background.contents = UIColor.clear
        scene.lightingEnvironment.contents = studioEnvironment()
        scene.lightingEnvironment.intensity = 1.6
        let key = SCNNode(); key.light = SCNLight(); key.light!.type = .directional; key.light!.intensity = 700
        key.light!.castsShadow = false; key.position = SCNVector3(-2, 4, 3); key.look(at: SCNVector3Zero)
        scene.rootNode.addChildNode(key)
        let cam = SCNNode(); cam.name = "cam"; cam.camera = SCNCamera()
        cam.camera!.fieldOfView = 46; cam.camera!.projectionDirection = .vertical
        cam.camera!.zNear = 0.05; cam.camera!.zFar = 50; cam.camera!.wantsHDR = false
        cam.position = eye; cam.look(at: SCNVector3Zero)
        scene.rootNode.addChildNode(cam)
    }

    /// Мягкая студия: светлое небо, серый пол — равномерные блики на металлике.
    static func studioEnvironment() -> UIImage {
        let w = 256, h = 128
        let r = UIGraphicsImageRenderer(size: CGSize(width: w, height: h))
        return r.image { ctx in
            let colors = [UIColor(white: 0.95, alpha: 1).cgColor, UIColor(white: 0.80, alpha: 1).cgColor,
                          UIColor(white: 0.55, alpha: 1).cgColor, UIColor(white: 0.30, alpha: 1).cgColor]
            let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 0.45, 0.55, 1])!
            ctx.cgContext.drawLinearGradient(g, start: .zero, end: CGPoint(x: 0, y: h), options: [])
        }
    }

    func makeUIView(context: Context) -> SCNView {
        let v = SCNView()
        v.backgroundColor = .clear
        v.isOpaque = false
        v.antialiasingMode = .multisampling4X
        v.allowsCameraControl = false
        v.autoenablesDefaultLighting = false
        v.preferredFramesPerSecond = 30
        v.scene = scene
        v.pointOfView = scene.rootNode.childNode(withName: "cam", recursively: false)
        apply(v)
        return v
    }

    func updateUIView(_ v: SCNView, context: Context) { apply(v) }

    private func apply(_ v: SCNView) {
        guard let pitchNode = scene.rootNode.childNode(withName: "pitch", recursively: false),
              let yawNode = pitchNode.childNode(withName: "yaw", recursively: false) else { return }
        yawNode.eulerAngles.y = yaw
        let a = Self.rightAxis
        pitchNode.rotation = SCNVector4(a.x, a.y, a.z, pitch)
        var c: [CGFloat] = [0, 0, 0, 0]; paint.getRed(&c[0], green: &c[1], blue: &c[2], alpha: &c[3])
        scene.rootNode.enumerateChildNodes { n, _ in
            for m in n.geometry?.materials ?? [] where m.name == "M_Paint" || m.name == "M_CarPaint" {
                m.diffuse.contents = paint
                m.metalness.contents = 0.55 as NSNumber
                m.roughness.contents = 0.28 as NSNumber
            }
        }
    }
}
