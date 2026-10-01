import Foundation

/// Bundled on-device model catalog. Same two entries as the shared KMP
/// catalog (shared/.../ai/AiModels.kt) — the JSON lives in the app, there is
/// no remote catalog fetch. The GGUF files are downloaded once from the URLs
/// below and stored in the app's private Application Support directory.
struct AiModel: Identifiable, Hashable {
    let id: String
    let name: String
    let parameters: String
    let speedNote: String
    let downloadUrl: String
    let fileName: String
    let sizeBytes: Int64
    let contextWindow: Int
    let modelType: String
    let quant: String
    let supportsGpu: Bool
    let ramNote: String

    var displayName: String { "\(name) (\(parameters))" }

    static let faster = AiModel(
        id: "qwen2.5-0.5b-instruct-q4",
        name: "Qwen2.5 0.5B Instruct",
        parameters: "0.5B",
        speedNote: "Fastest",
        downloadUrl: "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        fileName: "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        sizeBytes: 400_000_000,
        contextWindow: 2048,
        modelType: "Qwen2.5-Instruct",
        quant: "Q4_K_M",
        supportsGpu: true,
        ramNote: "Runs on most 64-bit iPhones with 3 GB+ RAM"
    )

    static let balanced = AiModel(
        id: "qwen2.5-1.5b-instruct-q4",
        name: "Qwen2.5 1.5B Instruct",
        parameters: "1.5B",
        speedNote: "Balanced",
        downloadUrl: "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        fileName: "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sizeBytes: 1_100_000_000,
        contextWindow: 2048,
        modelType: "Qwen2.5-Instruct",
        quant: "Q4_K_M",
        supportsGpu: true,
        ramNote: "Best answers; needs a 64-bit iPhone with 4 GB+ RAM"
    )

    static let all: [AiModel] = [faster, balanced]
}

enum AiStore {
    /// Private on-device folder for downloaded models (never leaves the phone).
    static func modelsDir() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("tlhf/ai", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func file(for model: AiModel) -> URL { modelsDir().appendingPathComponent(model.fileName) }
    static func exists(_ model: AiModel) -> Bool {
        FileManager.default.fileExists(atPath: file(for: model).path)
    }
}

func formatBytes(_ n: Int64) -> String {
    if n <= 0 { return "0 B" }
    let units = ["B", "KB", "MB", "GB"]
    var v = Double(n); var u = 0
    while v >= 1024 && u < units.count - 1 { v /= 1024; u += 1 }
    return String(format: "%.1f %@", v, units[u])
}
