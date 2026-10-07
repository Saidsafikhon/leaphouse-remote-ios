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

/// Что открыто на машине — 3D-модель открывает эти детали на петлях (зеркало BodyPose.kt).
struct BodyPose: Equatable {
    var doorFL = false, doorFR = false, doorRL = false, doorRR = false
    var trunk = false, hood = false
    var anyOpen: Bool { doorFL || doorFR || doorRL || doorRR || trunk || hood }
}

struct CarModelView: View {
    let model: String?
    let paint: Color
    var body3d = BodyPose()
    var onReady: (Bool) -> Void = { _ in }

    @State private var scene: SCNScene? = nil
    @State private var yaw: Float = 0
    @State private var pitch: Float = 0
    @State private var dragStart: (Float, Float)? = nil

    var body: some View {
        Group {
            if let s = scene {
                CarSceneView(scene: s, paint: UIColor(paint), yaw: yaw, pitch: pitch, pose: body3d)
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
    var pose = BodyPose()

    /// Камера — как на Android: цель в центре модели (единичный куб), чистый вид сбоку, нос влево, чуть сверху.
    static let eye = SCNVector3(0, 0.3, 2.0)
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
        buildHinges(content)
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
        // 31° по горизонтали: по фото 22.09 при 40° машина была на треть мельче, чем на Android
        cam.camera!.fieldOfView = 31; cam.camera!.projectionDirection = .horizontal
        cam.camera!.zNear = 0.05; cam.camera!.zFar = 50; cam.camera!.wantsHDR = false
        cam.position = eye; cam.look(at: SCNVector3Zero)
        scene.rootNode.addChildNode(cam)
    }

    /// Группа деталей по имени узла. C16/C11/C01: Door_LF_…, Body_M_Bonnet, Body_M_Trunk…;
    /// C10: l_frontdoor…, bonnet, trunk. Ось у всех моделей: нос −X, верх +Y, левый борт +Z.
    static func group(_ name: String) -> String? {
        let n = name.lowercased()
        if n.hasPrefix("door_lf") || n.hasPrefix("interactive_lf") || n.hasPrefix("l_frontdoor") { return "fl" }
        if n.hasPrefix("door_rf") || n.hasPrefix("interactive_rf") || n.hasPrefix("r_frontdoor") || n.hasPrefix("door_front_r") { return "fr" }
        if n.hasPrefix("door_lr") || n.hasPrefix("interactive_lr") || n.hasPrefix("l_reardoor") { return "rl" }
        if n.hasPrefix("door_rr") || n.hasPrefix("interactive_rr") || n.hasPrefix("r_reardoor") { return "rr" }
        if n.contains("bonnet") { return "hood" }
        if n.hasPrefix("body_m_trunk") || n.hasPrefix("trunk") { return "trunk" }
        return nil
    }

    /// Петель в GLB нет (иерархия плоская) — каждую группу кладём в узел «hinge_<группа>»,
    /// стоящий в точке петли, посчитанной по габаритам группы. Открытие = поворот этого узла.
    static func buildHinges(_ content: SCNNode) {
        var groups: [String: [SCNNode]] = [:]
        content.enumerateHierarchy { n, stop in
            guard let name = n.name, let g = group(name) else { return }
            // узел, чей предок уже в группе, не берём — он поедет вместе с предком
            var p = n.parent; while let q = p, q !== content { if let qn = q.name, group(qn) != nil { return }; p = q.parent }
            groups[g, default: []].append(n)
        }
        for (g, nodes) in groups {
            var lo = SCNVector3(1e9, 1e9, 1e9), hi = SCNVector3(-1e9, -1e9, -1e9)
            for n in nodes {
                let (a, b) = n.boundingBox
                for c in [content.convertPosition(a, from: n), content.convertPosition(b, from: n)] {
                    lo = SCNVector3(min(lo.x, c.x), min(lo.y, c.y), min(lo.z, c.z))
                    hi = SCNVector3(max(hi.x, c.x), max(hi.y, c.y), max(hi.z, c.z))
                }
            }
            let midY = (lo.y + hi.y) / 2
            let pivot: SCNVector3
            switch g {
            case "fl", "rl": pivot = SCNVector3(lo.x, midY, hi.z)   // передний край, наружная сторона
            case "fr", "rr": pivot = SCNVector3(lo.x, midY, lo.z)
            case "hood": pivot = SCNVector3(hi.x, hi.y, 0)           // у лобового стекла
            default: pivot = SCNVector3(lo.x, hi.y, 0)               // багажник: сверху у крыши
            }
            let hinge = SCNNode(); hinge.name = "hinge_" + g; hinge.position = pivot
            content.addChildNode(hinge)
            for n in nodes {
                let t = content.convertTransform(n.transform, from: n.parent)
                n.removeFromParentNode()
                hinge.addChildNode(n)
                n.transform = hinge.convertTransform(t, from: content)
            }
        }
    }

    /// Угол открытия группы, радианы: двери наружу 55°, капот вверх 40°, багажник вверх 65°.
    static func openAngles(_ g: String) -> SCNVector3 {
        let d: Float = .pi / 180
        switch g {
        case "fl", "rl": return SCNVector3(0, -55 * d, 0)
        case "fr", "rr": return SCNVector3(0, 55 * d, 0)
        case "hood": return SCNVector3(0, 0, -40 * d)
        default: return SCNVector3(0, 0, 65 * d)
        }
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
        // двери/капот/багажник — плавно, 0,6 с
        let open: [String: Bool] = ["fl": pose.doorFL, "fr": pose.doorFR, "rl": pose.doorRL,
                                    "rr": pose.doorRR, "trunk": pose.trunk, "hood": pose.hood]
        SCNTransaction.begin(); SCNTransaction.animationDuration = 0.6
        SCNTransaction.animationTimingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        for (g, isOpen) in open {
            guard let h = yawNode.childNode(withName: "hinge_" + g, recursively: true) else { continue }
            let target = isOpen ? Self.openAngles(g) : SCNVector3Zero
            if h.eulerAngles.x != target.x || h.eulerAngles.y != target.y || h.eulerAngles.z != target.z { h.eulerAngles = target }
        }
        SCNTransaction.commit()
        scene.rootNode.enumerateChildNodes { n, _ in
            for m in n.geometry?.materials ?? [] where m.name == "M_Paint" || m.name == "M_CarPaint" {
                m.diffuse.contents = paint
                m.metalness.contents = 0.55 as NSNumber
                m.roughness.contents = 0.28 as NSNumber
            }
        }
    }
}
