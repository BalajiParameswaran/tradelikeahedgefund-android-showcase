import Foundation
import Security

/// Encrypted on-device chat history for the AI Tutor.
/// Sessions live in the iOS Keychain (never plaintext on disk), using the same
/// JSON schema as the shared KMP store (`tlhf_ai_sessions_v1` on Android), and
/// the same limits: max 10 sessions, max 40 messages per session.
struct ChatMessage: Codable, Identifiable, Hashable {
    var id: String { role + text.prefix(32) }
    let role: String   // "user" | "assistant"
    let text: String
}

struct ChatSession: Codable, Identifiable {
    let id: String
    var title: String
    var updatedAt: Int64
    var messages: [ChatMessage]
}

private struct SessionsFile: Codable { var sessions: [ChatSession] }

private enum Keychain {
    static let service = "com.tradelikeahedgefund.ai"
    static let account = "tlhf_ai_sessions_v1"

    static func load() -> Data? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: account,
                                kSecReturnData as String: true,
                                kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    static func save(_ data: Data) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: account]
        let attrs: [String: Any] = [kSecValueData as String: data,
                                    kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        if SecItemCopyMatching(q as CFDictionary, nil) == errSecSuccess {
            SecItemUpdate(q as CFDictionary, attrs as CFDictionary)
        } else {
            var add = q; add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }
}

/// Thread-safe session store. Call `sessions` on the main actor.
final class AiChatStore {
    static let maxSessions = 10
    static let maxMessages = 40

    private let lock = NSLock()
    private var cached: [ChatSession] = []
    private var loaded = false

    private func read() -> [ChatSession] {
        lock.lock(); defer { lock.unlock() }
        if !loaded {
            if let data = Keychain.load(),
               let file = try? JSONDecoder().decode(SessionsFile.self, from: data) {
                cached = file.sessions
            }
            loaded = true
        }
        return cached
    }

    private func write(_ sessions: [ChatSession]) {
        lock.lock(); defer { lock.unlock() }
        cached = sessions
        if let data = try? JSONEncoder().encode(SessionsFile(sessions: sessions)) {
            Keychain.save(data)
        }
    }

    func sessions() -> [ChatSession] { read().sorted { $0.updatedAt > $1.updatedAt } }

    func session(_ id: String) -> ChatSession? { read().first { $0.id == id } }

    func newSession() -> ChatSession {
        var all = read()
        let s = ChatSession(id: UUID().uuidString, title: "New chat",
                            updatedAt: Int64(Date().timeIntervalSince1970 * 1000), messages: [])
        all.insert(s, at: 0)
        write(Array(all.prefix(Self.maxSessions)))
        return s
    }

    func addMessage(sessionId: String, role: String, text: String) {
        var all = read()
        guard let i = all.firstIndex(where: { $0.id == sessionId }) else { return }
        var s = all[i]
        s.messages.append(ChatMessage(role: role, text: text))
        if s.messages.count > Self.maxMessages { s.messages.removeFirst(s.messages.count - Self.maxMessages) }
        s.updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        if s.title == "New chat" {
            let first = s.messages.first { $0.role == "user" }?.text ?? ""
            if !first.isEmpty { s.title = String(first.prefix(40)) }
        }
        all.remove(at: i); all.insert(s, at: 0)
        write(Array(all.prefix(Self.maxSessions)))
    }

    func deleteSession(_ id: String) { write(read().filter { $0.id != id }) }

    func renameSession(_ id: String, title: String) {
        var all = read()
        if let i = all.firstIndex(where: { $0.id == id }) { all[i].title = title; write(all) }
    }

    func clearAll() { write([]) }
}
