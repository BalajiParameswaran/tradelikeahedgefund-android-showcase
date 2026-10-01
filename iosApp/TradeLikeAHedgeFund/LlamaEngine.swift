import Foundation

#if canImport(llama)
import llama
#endif

/// On-device LLM inference for iOS, backed by llama.cpp.
///
/// SETUP (one-time, on your Mac — see README-IOS-AI.md):
///   1. Add llama.cpp to the Xcode project (Swift Package
///      `https://github.com/ggerganov/llama.cpp` or a prebuilt XCFramework).
///   2. Make sure the package exposes the `llama` C module
///      (`canImport(llama)` becomes true).
///   3. Rebuild — no code changes needed here.
///
/// Until then every call throws `LlamaError.notLinked` and the UI shows an
/// honest setup message instead of crashing.
enum LlamaError: Error, LocalizedError {
    case notLinked
    case loadFailed(String)
    case busy
    case cancelled

    var errorDescription: String? {
        switch self {
        case .notLinked:
            return "llama.cpp isn't linked yet. See README-IOS-AI.md for the one-time Xcode setup."
        case .loadFailed(let why): return "Could not load the model file. \(why)"
        case .busy: return "The model is already generating — wait for it to finish."
        case .cancelled: return "Generation stopped."
        }
    }
}

@MainActor
final class LlamaEngine: ObservableObject {
    static let maxTokens = 2048

    @Published private(set) var loadedModelId: String?
    var isLoaded: Bool { loadedModelId != nil }

    private var busy = false
    private var cancelFlag = false

    #if canImport(llama)
    private var model: OpaquePointer?
    private var ctx: OpaquePointer?
    #endif

    func load(model: AiModel) throws {
        #if canImport(llama)
        try realLoad(model: model)
        loadedModelId = model.id
        #else
        throw LlamaError.notLinked
        #endif
    }

    /// Streams tokens; calls onToken on the main actor as text arrives.
    func generate(system: String, prompt: String,
                 onToken: @escaping (String) -> Void,
                 onDone: @escaping (String?, Error?) -> Void) {
        #if canImport(llama)
        guard !busy else { onDone(nil, LlamaError.busy); return }
        busy = true
        cancelFlag = false
        Task.detached { [weak self] in
            let (text, err) = await self?.realGenerate(system: system, prompt: prompt, onToken: onToken) ?? ("", LlamaError.cancelled)
            await MainActor.run { [weak self] in
                self?.busy = false
                onDone(err == nil ? text : nil, err)
            }
        }
        #else
        onDone(nil, LlamaError.notLinked)
        #endif
    }

    func stop() { cancelFlag = true }

    func unload() {
        #if canImport(llama)
        if let c = ctx { llama_free(c); ctx = nil }
        if let m = model { llama_model_free(m); model = nil }
        #endif
        loadedModelId = nil
    }

    // MARK: - llama.cpp internals

    #if canImport(llama)

    private func realLoad(model: AiModel) throws {
        unload()
        llama_backend_init()
        var mparams = llama_model_default_params()
        mparams.n_gpu_layers = 99 // offload to Metal/ANE when available
        guard let m = llama_model_load_from_file(AiStore.file(for: model).path, mparams) else {
            throw LlamaError.loadFailed("llama_model_load_from_file returned nil.")
        }
        var cparams = llama_context_default_params()
        cparams.n_ctx = 2048
        cparams.n_threads = 4
        guard let c = llama_init_from_model(m, cparams) else {
            llama_model_free(m)
            throw LlamaError.loadFailed("llama_init_from_model returned nil.")
        }
        self.model = m
        self.ctx = c
    }

    private nonisolated func realGenerate(system: String, prompt: String,
                                          onToken: @escaping (String) -> Void) async -> (String, Error?) {
        guard let model = await MainActor.run(body: { self.model }),
              let ctx = await MainActor.run(body: { self.ctx }) else {
            return ("", LlamaError.loadFailed("Engine not loaded."))
        }
        let full = "<|im_start|>system\n\(system)<|im_end|>\n<|im_start|>user\n\(prompt)<|im_end|>\n<|im_start|>assistant\n"
        guard let fullData = full.data(using: .utf8) else { return ("", LlamaError.loadFailed("Bad prompt encoding.")) }

        let nPrompt = 1024
        let tokens = UnsafeMutablePointer<llama_token>.allocate(capacity: nPrompt)
        defer { tokens.deallocate() }
        let nTokens = fullData.withUnsafeBytes { raw -> Int32 in
            llama_tokenize(model, raw.bindMemory(to: CChar.self).baseAddress, Int32(fullData.count),
                           tokens, Int32(nPrompt), true, true)
        }
        guard nTokens > 0 else { return ("", LlamaError.loadFailed("Tokenization failed.")) }

        let sampler = llama_sampler_init_greedy()
        defer { llama_sampler_free(sampler) }

        var batch = llama_batch_get_one(tokens, nTokens)
        var nPast: Int32 = 0
        var out = ""
        var pieces = 0
        let eos = llama_token_eos(model)

        while pieces < Self.maxTokens {
            if await MainActor.run(body: { self.cancelFlag }) { return (out, LlamaError.cancelled) }
            if llama_decode(ctx, batch) != 0 { return (out, LlamaError.loadFailed("Decode error.")) }
            nPast += batch.n_tokens
            let tok = llama_sampler_sample(sampler, ctx, batch.n_tokens - 1)
            if tok == eos { break }
            let piece = String(cString: llama_token_to_piece(model, tok))
            out += piece
            let pieceCopy = piece
            await MainActor.run { onToken(pieceCopy) }
            pieces += 1
            var next = [tok]
            batch = next.withUnsafeMutableBufferPointer { buf in
                llama_batch_get_one(buf.baseAddress!, 1)
            }
            _ = nPast
        }
        return (out, nil)
    }
    #endif
}
