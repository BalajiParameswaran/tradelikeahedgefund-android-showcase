import Combine
import Foundation
import Network

/// Wi-Fi / wired reachability (for the Wi-Fi-only download switch).
final class NetMonitor: ObservableObject {
    private let monitor = NWPathMonitor()
    @Published var isWifi = true

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                self?.isWifi = path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet)
            }
        }
        monitor.start(queue: DispatchQueue.global(qos: .background))
    }
}

/// One shared owner for the on-device AI stack on iOS: downloader, llama.cpp
/// engine, and encrypted chat store. Created once in LearnView and passed to
/// the AI Tutor and AI Topics views (two loaded models would blow the RAM
/// budget, so they share one engine).
@MainActor
final class AiController: ObservableObject {
    let downloader = ModelDownloader()
    let engine = LlamaEngine()
    let chat = AiChatStore()
    let net = NetMonitor()

    @Published var selectedModel: AiModel = .balanced
    @Published var wifiOnly = true
    @Published var engineLoaded = false
    @Published var loadError: String?

    private var idleTimer: Timer?

    var canDownload: Bool { !wifiOnly || net.isWifi }

    /// Reset the 30-minute idle timer that unloads the model from RAM.
    func touch() {
        idleTimer?.invalidate()
        idleTimer = Timer.scheduledTimer(withTimeInterval: 30 * 60, repeats: false) { [weak self] _ in
            Task { @MainActor in self?.engine.unload(); self?.engineLoaded = false }
        }
    }

    func loadSelected() {
        guard AiStore.exists(selectedModel) else { return }
        loadError = nil
        touch()
        do {
            try engine.load(model: selectedModel)
            engineLoaded = true
        } catch {
            loadError = error.localizedDescription
            engineLoaded = false
        }
    }

    func unloadEngine() {
        engine.unload()
        engineLoaded = false
        idleTimer?.invalidate()
    }
}
