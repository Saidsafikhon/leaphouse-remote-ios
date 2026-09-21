import SwiftUI
import AVFoundation

/// Сканер QR с экрана машины. Отдаёт первое распознанное значение и закрывается;
/// nil — пользователь отменил или камера недоступна.
struct QRScannerScreen: View {
    @Environment(\.palette) private var p
    let onResult: (String?) -> Void

    @State private var denied = false
    @State private var done = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if denied {
                VStack(spacing: Space.x4) {
                    Image(systemName: "camera.fill").font(.system(size: 40)).foregroundStyle(p.textMuted)
                    Text(L("Нет доступа к камере")).font(ElectroType.headline).foregroundStyle(.white)
                    Text(L("Разрешите камеру в Настройках iOS → LeapRemote, чтобы сканировать QR."))
                        .font(ElectroType.body).foregroundStyle(p.textSecondary).multilineTextAlignment(.center)
                    ElectroButton(text: L("Открыть настройки"), style: .secondary) {
                        if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
                    }
                }
                .padding(Space.x6)
            } else {
                QRCameraView { value in
                    guard !done else { return }
                    done = true
                    onResult(value)
                } onDenied: { denied = true }
                .ignoresSafeArea()
                // рамка видоискателя
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .stroke(p.accent, lineWidth: 2)
                    .frame(width: 240, height: 240)
                VStack {
                    Spacer()
                    Text(L("Наведите на QR на экране машины")).font(ElectroType.body).foregroundStyle(.white)
                        .padding(.horizontal, Space.x4).padding(.vertical, Space.x2)
                        .background(Color.black.opacity(0.5)).clipShape(Capsule())
                        .padding(.bottom, 120)
                }
            }
            VStack {
                HStack {
                    Spacer()
                    Button { onResult(nil) } label: {
                        Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white)
                            .frame(width: 44, height: 44).background(Color.black.opacity(0.5)).clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .padding(Space.x4)
                }
                Spacer()
            }
        }
    }
}

/// Обёртка AVCaptureSession с распознаванием QR.
struct QRCameraView: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    let onDenied: () -> Void

    func makeUIViewController(context: Context) -> QRCameraController {
        let c = QRCameraController()
        c.onCode = onCode
        c.onDenied = onDenied
        return c
    }

    func updateUIViewController(_ uiViewController: QRCameraController, context: Context) {}
}

final class QRCameraController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    var onDenied: (() -> Void)?

    private let session = AVCaptureSession()
    private var preview: AVCaptureVideoPreviewLayer?
    private let queue = DispatchQueue(label: "electro.qr")

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] ok in
                DispatchQueue.main.async { ok ? self?.configure() : self?.onDenied?() }
            }
        default: onDenied?()
        }
    }

    private func configure() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { onDenied?(); return }
        session.beginConfiguration()
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { session.commitConfiguration(); onDenied?(); return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
        session.commitConfiguration()

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        preview = layer
        queue.async { [session] in session.startRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        queue.async { [session] in if session.isRunning { session.stopRunning() } }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let obj = metadataObjects.compactMap({ $0 as? AVMetadataMachineReadableCodeObject }).first,
              let value = obj.stringValue, !value.isEmpty else { return }
        queue.async { [session] in if session.isRunning { session.stopRunning() } }
        onCode?(value)
    }
}
