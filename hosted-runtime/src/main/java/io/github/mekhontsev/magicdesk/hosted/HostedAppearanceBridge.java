package io.github.mekhontsev.magicdesk.hosted;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

/** Read-only preferences in the executor namespace. No guest configuration or graphics ownership. */
public final class HostedAppearanceBridge implements Closeable {
    private static final int MAX_PEERS = 16;
    private final LocalServerSocket listener;
    private final byte[] token;
    private final ArrayList<Peer> peers = new ArrayList<>();
    private volatile boolean closed;
    private int scheme;

    public static HostedAppearanceBridge fromEnvironment() {
        String endpoint = System.getenv("MAGICDESK_APPEARANCE_SOCKET");
        try { return endpoint == null ? null : new HostedAppearanceBridge(endpoint,
                System.getenv("MAGICDESK_APPEARANCE_TOKEN"),
                Integer.parseInt(System.getenv("MAGICDESK_COLOR_SCHEME"))); }
        catch (IOException | RuntimeException error) {
            android.util.Log.w("LinuxAppearance", "Settings bridge unavailable", error);
            return null;
        }
    }

    public HostedAppearanceBridge(String endpoint, String secret, int initial) throws IOException {
        scheme = HostedColorScheme.require(initial);
        if (secret == null || !secret.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid appearance authorization");
        token = secret.getBytes(StandardCharsets.US_ASCII);
        listener = new LocalServerSocket(endpoint);
        new Thread(this::accept, "LinuxAppearance-admission").start();
    }

    public synchronized void update(int value) {
        HostedColorScheme.require(value);
        if (closed || scheme == value) return;
        scheme = value;
        for (Peer peer : peers) peer.offer(value);
    }

    private void accept() {
        try {
            while (!closed) {
                LocalSocket socket = listener.accept();
                Peer peer = new Peer(socket);
                synchronized (this) {
                    if (closed || peers.size() == MAX_PEERS) { socket.close(); continue; }
                    peers.add(peer);
                    peer.offer(scheme);
                }
                peer.worker.start();
            }
        } catch (IOException error) {
            if (!closed) android.util.Log.w("LinuxAppearance", "Settings listener ended", error);
        }
    }

    private final class Peer implements Runnable {
        final LocalSocket socket;
        final ArrayBlockingQueue<Integer> pending = new ArrayBlockingQueue<>(1);
        final Thread worker = new Thread(this, "LinuxAppearance-peer");
        Peer(LocalSocket socket) { this.socket = socket; }
        void offer(int value) { pending.poll(); pending.offer(value); }
        @Override public void run() {
            try (socket) {
                // EVENT_WAIT: helper authorization and socket writes; expiry drops only this peer.
                socket.setSoTimeout(5_000);
                if (!MessageDigest.isEqual(token, socket.getInputStream().readNBytes(token.length))) return;
                socket.setSoTimeout(0);
                android.system.Os.setsockoptTimeval(socket.getFileDescriptor(), android.system.OsConstants.SOL_SOCKET,
                        android.system.OsConstants.SO_SNDTIMEO, android.system.StructTimeval.fromMillis(5_000));
                new Thread(() -> {
                    // EVENT_WAIT: helper/bus closure; release an idle subscription without polling.
                    try { socket.getInputStream().read(); } catch (IOException ignored) { }
                    finally { stop(); }
                }, "LinuxAppearance-lifetime").start();
                while (!closed) {
                    // EVENT_WAIT: a changed preference or session close; no periodic work.
                    int value = pending.take();
                    socket.getOutputStream().write(value);
                }
            } catch (IOException | android.system.ErrnoException ignored) {
                // The Linux bus/helper can end independently of its retained graphical server.
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { synchronized (HostedAppearanceBridge.this) { peers.remove(this); } }
        }
        void stop() {
            worker.interrupt();
            try { socket.shutdownInput(); } catch (IOException ignored) { }
            try { socket.shutdownOutput(); } catch (IOException ignored) { }
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    @Override public void close() {
        ArrayList<Peer> closing;
        synchronized (this) { if (closed) return; closed = true; closing = new ArrayList<>(peers); }
        try { listener.close(); } catch (IOException ignored) { }
        for (Peer peer : closing) peer.stop();
    }
}
