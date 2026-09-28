import SwiftUI

/// Wall Street palette, extracted from the web app's CSS variables.
extension Color {
    static let navyBg = Color(hex: "0A0E1A")
    static let navySurface = Color(hex: "131A2E")
    static let bronzeGold = Color(hex: "D4AF37")
    static let goldDeep = Color(hex: "8A6D2B")
    static let tabActive = Color(hex: "E9C766")
    static let tabIdle = Color(hex: "7D8DB0")
    static let line = Color(hex: "26314F")
    static let ink = Color(hex: "F1F5F9")
    static let muted = Color(hex: "8B94AD")
    static let bullGreen = Color(hex: "22C55E")
    static let bearRed = Color(hex: "EF4444")
    static let infoBlue = Color(hex: "3B82F6")
    static let amber = Color(hex: "F59E0B")

    init(hex: String) {
        var h = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if h.hasPrefix("#") { h.removeFirst() }
        var rgb: UInt64 = 0
        Scanner(string: h).scanHexInt64(&rgb)
        let r = Double((rgb >> 16) & 0xFF) / 255
        let g = Double((rgb >> 8) & 0xFF) / 255
        let b = Double(rgb & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}
