package com.tradelikeahedgefund.app;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.ProgressListener;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * OnDeviceAI: downloads a .task LLM once, then runs inference fully on-device.
 * No prompt or response ever leaves the phone.
 *
 * Events: "aiProgress" {bytesDownloaded,totalBytes,done,error?}
 *         "aiToken"    {text,done}
 *         "aiError"    {message}
 */
@CapacitorPlugin(name = "OnDeviceAI")
public class OnDeviceAIPlugin extends Plugin {
    private volatile Thread downloadThread;
    private volatile boolean pauseRequested = false;
    private volatile boolean cancelRequested = false;
    private volatile String lastDownloadUrl = null;
    private volatile long bytesDownloaded = 0;
    private volatile long totalBytes = -1;
    private LlmInference llmInference = null;

    private File modelFile() {
        File dir = new File(getContext().getFilesDir(), "ai");
        dir.mkdirs();
        return new File(dir, "model.task");
    }

    private File partFile() {
        return new File(modelFile().getAbsolutePath() + ".part");
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("downloaded", modelFile().exists());
        ret.put("bytesDownloaded", bytesDownloaded);
        ret.put("totalBytes", totalBytes);
        ret.put("ready", llmInference != null);
        call.resolve(ret);
    }

    @PluginMethod
    public void deviceCheck(PluginCall call) {
        ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        double gb = mi.totalMem / 1073741824.0;
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        boolean is64 = abi.contains("64");
        JSObject ret = new JSObject();
        ret.put("totalRamGB", Math.round(gb * 10) / 10.0);
        ret.put("abi", abi);
        boolean ok = is64 && gb >= 4.0;
        ret.put("supported", ok);
        if (!ok) {
            ret.put("reason", !is64 ? "needs a 64-bit phone" : "needs at least 4 GB of RAM");
        }
        call.resolve(ret);
    }

    private boolean isWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    @PluginMethod
    public void startDownload(PluginCall call) {
        String url = call.getString("url");
        boolean wifiOnly = call.getBoolean("wifiOnly", true);
        if (url == null || url.isEmpty()) { call.reject("url required"); return; }
        if (downloadThread != null && downloadThread.isAlive()) { call.reject("download already running"); return; }
        if (wifiOnly && !isWifi()) { call.reject("wifi_required"); return; }
        pauseRequested = false;
        cancelRequested = false;
        downloadThread = new Thread(() -> downloadLoop(url));
        downloadThread.start();
        JSObject ret = new JSObject();
        ret.put("started", true);
        call.resolve(ret);
    }

    private void downloadLoop(String urlStr) {
        lastDownloadUrl = urlStr;
        int attempt = 0;
        while (true) {
            attempt++;
            if (cancelRequested) { emitProgress(true, "cancelled"); break; }
            try {
                downloadOnce(urlStr);
                if (cancelRequested) emitProgress(true, "cancelled");
                break; // completed
            } catch (Exception e) {
                if (cancelRequested) { emitProgress(true, "cancelled"); break; }
                if (attempt >= 5) {
                    JSObject d = new JSObject();
                    d.put("bytesDownloaded", bytesDownloaded);
                    d.put("totalBytes", totalBytes);
                    d.put("done", false);
                    d.put("error", e.getMessage());
                    notifyListeners("aiProgress", d);
                    break;
                }
                // transient failure (e.g. read timeout on a stalled connection):
                // wait briefly, then retry — the Range header resumes from the .part file
                try { Thread.sleep(Math.min(4000L * attempt, 15000L)); } catch (InterruptedException ignored) {}
            }
        }
        downloadThread = null;
    }

    private void downloadOnce(String urlStr) throws Exception {
        File part = partFile();
        long existing = part.exists() ? part.length() : 0;
        bytesDownloaded = existing;
        HttpURLConnection conn = openDownloadConnection(urlStr, existing);
        int code = conn.getResponseCode();
        long contentLen = conn.getHeaderFieldLong("Content-Length", -1);
        if (code == 416) {
            // range not satisfiable — restart from scratch
            conn.disconnect();
            part.delete();
            existing = 0;
            bytesDownloaded = 0;
            conn = openDownloadConnection(urlStr, 0);
            code = conn.getResponseCode();
            contentLen = conn.getHeaderFieldLong("Content-Length", -1);
        }
        if (code == 206) {
            totalBytes = existing + contentLen;
        } else if (code == 200) {
            totalBytes = contentLen;
            if (existing > 0) { part.delete(); existing = 0; bytesDownloaded = 0; }
        } else {
            conn.disconnect();
            throw new Exception("download failed (http " + code + ")");
        }
        try (InputStream in = conn.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
            raf.seek(existing);
            byte[] buf = new byte[65536];
            int n;
            long lastEmit = 0;
            while ((n = in.read(buf)) != -1) {
                if (cancelRequested) return;
                while (pauseRequested && !cancelRequested) {
                    Thread.sleep(200);
                }
                if (cancelRequested) return;
                raf.write(buf, 0, n);
                bytesDownloaded += n;
                long now = System.currentTimeMillis();
                if (now - lastEmit > 300) { lastEmit = now; emitProgress(false, null); }
            }
        } finally {
            conn.disconnect();
        }
        File model = modelFile();
        if (model.exists()) model.delete();
        if (!part.renameTo(model)) throw new Exception("could not save model file");
        bytesDownloaded = model.length();
        totalBytes = bytesDownloaded;
        emitProgress(true, null);
    }

    private HttpURLConnection openDownloadConnection(String urlStr, long existing) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
        conn.connect();
        return conn;
    }

    private void emitProgress(boolean done, String error) {
        JSObject d = new JSObject();
        d.put("bytesDownloaded", bytesDownloaded);
        d.put("totalBytes", totalBytes);
        d.put("done", done);
        if (error != null) d.put("error", error);
        notifyListeners("aiProgress", d);
    }

    @PluginMethod
    public void pauseDownload(PluginCall call) {
        pauseRequested = true;
        call.resolve();
    }

    @PluginMethod
    public void resumeDownload(PluginCall call) {
        pauseRequested = false;
        // If the worker thread died (e.g. after a network error) but the partial
        // file remains, restart the loop — the Range header resumes where it stopped.
        if ((downloadThread == null || !downloadThread.isAlive())
                && !cancelRequested
                && !modelFile().exists()
                && partFile().exists()
                && lastDownloadUrl != null) {
            cancelRequested = false;
            downloadThread = new Thread(() -> downloadLoop(lastDownloadUrl));
            downloadThread.start();
        }
        call.resolve();
    }

    @PluginMethod
    public void cancelDownload(PluginCall call) {
        cancelRequested = true;
        pauseRequested = false;
        call.resolve();
    }

    @PluginMethod
    public void deleteModel(PluginCall call) {
        closeModelInternal();
        File m = modelFile();
        if (m.exists()) m.delete();
        File p = partFile();
        if (p.exists()) p.delete();
        bytesDownloaded = 0;
        totalBytes = -1;
        call.resolve();
    }

    @PluginMethod
    public void initModel(PluginCall call) {
        new Thread(() -> {
            try {
                File m = modelFile();
                if (!m.exists()) { call.reject("model not downloaded yet"); return; }
                closeModelInternal();
                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(m.getAbsolutePath())
                        .setMaxTokens(2048)
                        .build();
                llmInference = LlmInference.createFromOptions(getContext(), options);
                call.resolve();
            } catch (Exception e) {
                call.reject("could not load model: " + e.getMessage());
            }
        }).start();
    }

    private final Object genLock = new Object();
    private volatile boolean generating = false;

    @PluginMethod
    public void generate(PluginCall call) {
        String prompt = call.getString("prompt");
        if (prompt == null || prompt.isEmpty()) { call.reject("prompt required"); return; }
        if (llmInference == null) { call.reject("model not loaded"); return; }
        synchronized (genLock) {
            if (generating) { call.reject("already_generating"); return; }
            generating = true;
        }
        new Thread(() -> {
            try {
                llmInference.generateResponseAsync(prompt, new ProgressListener<String>() {
                    @Override
                    public void run(String partialResult, boolean done) {
                        JSObject d = new JSObject();
                        d.put("text", partialResult);
                        d.put("done", done);
                        notifyListeners("aiToken", d);
                        if (done) {
                            synchronized (genLock) { generating = false; }
                            call.resolve();
                        }
                    }
                });
            } catch (Exception e) {
                synchronized (genLock) { generating = false; }
                JSObject err = new JSObject();
                err.put("message", e.getMessage());
                notifyListeners("aiError", err);
                call.reject(e.getMessage());
            }
        }).start();
    }

    private void closeModelInternal() {
        if (llmInference != null) {
            try { llmInference.close(); } catch (Exception ignored) {}
            llmInference = null;
        }
    }

    @PluginMethod
    public void closeModel(PluginCall call) {
        closeModelInternal();
        call.resolve();
    }
}
