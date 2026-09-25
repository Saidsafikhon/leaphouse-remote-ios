import SwiftUI

/// Экран загрузки по макету Figma «loading-dark-theme / loading-light-theme» — двойник
/// `ui/SplashScreen.kt` на Android: иконка в трёх пульсирующих кольцах с зелёным
/// свечением, «LeapRemote», «EV CONTROL SYSTEM», «Заряжается...» и полоса прогресса.
/// Таймлайн макета (5 с, петля) сжат до одного прохода `total`, затем экран гаснет.
struct SplashView: View {
    let dark: Bool
    let onDone: () -> Void

    private static let total: Double = 2.6
    private static let fade: Double = 0.35
    private let green = Color(red: 0x3E / 255, green: 0x8A / 255, blue: 0x2E / 255)
    private let greenText = Color(red: 0x2E / 255, green: 0x8A / 255, blue: 0x37 / 255)
    @State private var start = Date()

    // --- кривые из макета ---
    private func expoOut(_ x: Double) -> Double { x >= 1 ? 1 : 1 - pow(2, -10 * x) }
    private func springOut(_ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        let c = 1.9, c3 = c + 1
        return 1 + c3 * pow(x - 1, 3) + c * pow(x - 1, 2)
    }
    private func easeInOut(_ x: Double) -> Double { 0.5 - 0.5 * cos(.pi * min(max(x, 0), 1)) }
    private func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    private func pulse(_ t: Double, _ s: Double, _ p: Double, _ lo: Double, _ hi: Double) -> Double {
        guard t > s else { return hi }
        let k = (t - s).truncatingRemainder(dividingBy: p) / p
        return lo + (hi - lo) * (0.5 + 0.5 * cos(2 * .pi * k))
    }

    var body: some View {
        let bg = dark ? Color(red: 0x0B / 255, green: 0x0E / 255, blue: 0x14 / 255) : Color(red: 0xF4 / 255, green: 0xF6 / 255, blue: 0xF8 / 255)
        let title = dark ? Color.white : Color(red: 0x12 / 255, green: 0x16 / 255, blue: 0x1A / 255)
        let sub = dark ? Color(red: 0x8E / 255, green: 0x9A / 255, blue: 0xA8 / 255) : Color(red: 0x5C / 255, green: 0x64 / 255, blue: 0x70 / 255)
        let charging = dark ? Color(red: 0x99 / 255, green: 0xA6 / 255, blue: 0x99 / 255) : Color(white: 0.4)
        let track = dark ? Color(red: 0x26 / 255, green: 0x33 / 255, blue: 0x26 / 255) : Color(red: 0xE5 / 255, green: 0xEB / 255, blue: 0xE5 / 255)
        let glowA = dark ? 0.6 : 0.35
        let ringA: [Double] = dark ? [0.5, 0.3, 0.15] : [0.4, 0.2, 0.08]

        TimelineView(.animation) { ctx in
            let t = ctx.date.timeIntervalSince(start)
            ZStack {
                bg.ignoresSafeArea()
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    // --- иконка в кольцах ---
                    ZStack {
                        ForEach(0..<3, id: \.self) { i in
                            let d: [Double] = [150, 180, 220], w: [Double] = [2, 1.5, 1]
                            let st: [Double] = [0.5, 0.6, 0.8], from: [Double] = [0.6, 0.5, 0.4], amp: [Double] = [0.08, 0.12, 0.15]
                            let k = seg(t, st[i], st[i] + 0.5)
                            let scale = k < 1 ? from[i] + (1 - from[i]) * springOut(k) : pulse(t, st[i] + 0.5, 1.8, 1 - amp[i] / 2, 1 + amp[i] / 2)
                            let op = expoOut(k) * (k >= 1 ? pulse(t, st[i] + 0.5, 1.8, 0.45, 1) : 1)
                            Circle().stroke(green.opacity(ringA[i] * op), lineWidth: w[i])
                                .frame(width: d[i], height: d[i]).scaleEffect(scale)
                        }
                        let kLogo = seg(t, 0, 0.7)
                        let logoScale = kLogo < 1 ? 0.3 + 0.7 * springOut(kLogo) : pulse(t, 0.7, 2.0, 1, 1.04)
                        let glow = kLogo >= 1 ? pulse(t, 0.7, 2.0, 25, 40) : 5 + 25 * expoOut(kLogo)
                        Image("splash_logo").resizable().frame(width: 120, height: 120)
                            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                            .shadow(color: green.opacity(0.2), radius: 10, x: 0, y: 8)
                            .shadow(color: green.opacity(glowA), radius: glow / 2)
                            .scaleEffect(logoScale)
                            .opacity(expoOut(seg(t, 0, 0.5)))
                    }
                    .frame(width: 220, height: 220)
                    // --- названия ---
                    let kT = expoOut(seg(t, 0.5, 1.0))
                    (Text("Leap").font(.custom("Outfit-Bold", size: 36)).foregroundColor(title)
                     + Text("Remote").font(.custom("Outfit-Light", size: 36)).foregroundColor(greenText))
                        .opacity(kT).offset(y: (1 - kT) * 20)
                    let kS = expoOut(seg(t, 0.7, 1.2))
                    Text("EV CONTROL SYSTEM").font(.custom("Outfit-Medium", size: 14)).foregroundStyle(sub)
                        .padding(.top, 8).opacity(kS).offset(y: (1 - kS) * 15)
                    Spacer(minLength: 0)
                    // --- «Заряжается...» и полоса ---
                    VStack(spacing: 16) {
                        let kC = expoOut(seg(t, 0.8, 1.2))
                        Text(L("Заряжается...")).font(.system(size: 14, weight: .medium)).tracking(1.5).foregroundStyle(charging)
                            .opacity(kC).offset(y: (1 - kC) * 10)
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(track)
                                Capsule().fill(green).frame(width: g.size.width * easeInOut(seg(t, 0.8, Self.total - 0.2)))
                            }
                        }
                        .frame(height: 6)
                        .opacity(expoOut(seg(t, 0.7, 1.0)))
                    }
                    .frame(height: 80).padding(.horizontal, 60)
                    Spacer(minLength: 0)
                }
                .padding(.bottom, 40)
            }
            .opacity(1 - seg(t, Self.total, Self.total + Self.fade))
        }
        .onAppear {
            start = Date()
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.total + Self.fade) { onDone() }
        }
    }
}
