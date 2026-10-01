import Foundation

/// Resumable model downloader with progress, pause/resume/cancel/delete.
/// Mirrors the Android ModelDownloader (shared/.../ai/AiDownload.kt state).
/// Wi-Fi-only is enforced by the caller (Network path monitor) before start().
@MainActor
final class ModelDownloader: NSObject, ObservableObject, URLSessionDownloadDelegate {
    enum State: Equatable { case idle, downloading, paused, done, failed(String) }

    @Published private(set) var state: State = .idle
    @Published private(set) var downloadedBytes: Int64 = 0
    @Published private(set) var totalBytes: Int64 = 0
    @Published private(set) var model: AiModel?

    var progress: Double {
        guard totalBytes > 0 else { return 0 }
        return Double(downloadedBytes) / Double(totalBytes)
    }

    private var session: URLSession!
    private var task: URLSessionDownloadTask?
    private var resumeData: Data?
    private var destURL: URL?

    override init() {
        super.init()
        session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    }

    func start(model: AiModel) {
        cancelTaskOnly()
        self.model = model
        downloadedBytes = 0
        resumeData = nil
        let dest = AiStore.file(for: model)
        destURL = dest
        if FileManager.default.fileExists(atPath: dest.path) {
            totalBytes = (try? FileManager.default.attributesOfItem(atPath: dest.path)[.size] as? Int64) ?? 0
            downloadedBytes = totalBytes
            state = .done
            return
        }
        totalBytes = model.sizeBytes
        state = .downloading
        let url = URL(string: model.downloadUrl)!
        task = session.downloadTask(with: url)
        task?.resume()
    }

    func pause() {
        guard case .downloading = state else { return }
        task?.cancel(byProducingResumeData: { [weak self] data in
            Task { @MainActor in
                self?.resumeData = data
                self?.state = .paused
            }
        })
        task = nil
    }

    func resume() {
        guard case .paused = state, let model else { return }
        state = .downloading
        if let data = resumeData {
            task = session.downloadTask(withResumeData: data)
        } else {
            task = session.downloadTask(with: URL(string: model.downloadUrl)!)
        }
        task?.resume()
    }

    func cancel() {
        cancelTaskOnly()
        resumeData = nil
        if let dest = destURL { try? FileManager.default.removeItem(at: dest) }
        downloadedBytes = 0
        state = .idle
    }

    func delete(model: AiModel) {
        cancelTaskOnly()
        try? FileManager.default.removeItem(at: AiStore.file(for: model))
        resumeData = nil
        downloadedBytes = 0
        self.model = nil
        state = .idle
    }

    private func cancelTaskOnly() { task?.cancel(); task = nil }

    // MARK: URLSessionDownloadDelegate

    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                               didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                               totalBytesExpectedToWrite: Int64) {
        Task { @MainActor in
            self.downloadedBytes = totalBytesWritten
            if totalBytesExpectedToWrite > 0 { self.totalBytes = totalBytesExpectedToWrite }
        }
    }

    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                               didFinishDownloadingTo location: URL) {
        Task { @MainActor in
            guard let dest = self.destURL else { return }
            try? FileManager.default.removeItem(at: dest)
            do {
                try FileManager.default.moveItem(at: location, to: dest)
                self.downloadedBytes = self.totalBytes
                self.state = .done
            } catch {
                self.state = .failed(error.localizedDescription)
            }
        }
    }

    nonisolated func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error, (error as NSError).code != NSURLErrorCancelled else { return }
        Task { @MainActor in
            if case .downloading = self.state { self.state = .failed(error.localizedDescription) }
        }
    }
}
