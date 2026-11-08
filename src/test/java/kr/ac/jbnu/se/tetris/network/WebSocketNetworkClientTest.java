package kr.ac.jbnu.se.tetris.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.ac.jbnu.se.tetris.network.protocol.WireCodec;
import kr.ac.jbnu.se.tetris.network.protocol.WireRequest;

/** 로컬 WebSocket 서버와 실제 마스킹·프레임·요청 및 결과 통지를 왕복 검증한다. */
public final class WebSocketNetworkClientTest {
    public static void main(String[] args) throws Exception {
        try { new ConnectionOptions(URI.create("ws://127.0.0.1/ws"), "safe\r\nInjected: yes");
            throw new AssertionError("Header injection accepted");
        } catch (IllegalArgumentException expected) { /* expected */ }
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) { serve(socket); }
                catch (Throwable error) { failure.set(error); }
            }, "ranked-ws-fixture");
            peer.setDaemon(true); peer.start();
            CountDownLatch connected = new CountDownLatch(1);
            CountDownLatch room = new CountDownLatch(1);
            CountDownLatch saved = new CountDownLatch(1);
            WebSocketNetworkClient client = new WebSocketNetworkClient();
            NetworkSubscription subscription = client.subscribe(update -> {
                if (update.getType() == NetworkUpdate.Type.CONNECTED) connected.countDown();
                if (update.getType() == NetworkUpdate.Type.ROOM_STATE) room.countDown();
                if (update.getType() == NetworkUpdate.Type.RANKED_SAVE_STATUS
                        && "SAVED".equals(update.getReasonCode())) saved.countDown();
            });
            client.connect(new ConnectionOptions(URI.create("ws://127.0.0.1:" + server.getLocalPort() + "/ws"),
                    "public-test-token"));
            check(connected.await(5, TimeUnit.SECONDS), "Handshake and connected update");
            client.send(RoomCommand.createRoom(2));
            check(room.await(5, TimeUnit.SECONDS), "Room response");
            check(saved.await(5, TimeUnit.SECONDS), "Ranked persistence response");
            subscription.close(); client.close();
            peer.join(1000);
            if (failure.get() != null) throw new AssertionError("WebSocket peer failure", failure.get());
        }
        coldStart503RetriesBeforeFirstGameCommand();
        closingDuringBackoffCancelsRetry();
    }

    private static void coldStart503RetriesBeforeFirstGameCommand() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))) {
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            Thread peer = new Thread(() -> {
                try (Socket first = server.accept()) { respond503(first); }
                catch (Throwable error) { failure.set(error); return; }
                try (Socket second = server.accept()) { serve(second); }
                catch (Throwable error) { failure.set(error); }
            }, "ranked-ws-cold-start-fixture");
            peer.setDaemon(true); peer.start();
            CountDownLatch connected = new CountDownLatch(1);
            CountDownLatch room = new CountDownLatch(1);
            AtomicReference<String> failureReason = new AtomicReference<String>();
            WebSocketNetworkClient client = new WebSocketNetworkClient(true, 5000);
            try {
                client.subscribe(update -> {
                    if (update.getType() == NetworkUpdate.Type.CONNECTED) connected.countDown();
                    if (update.getType() == NetworkUpdate.Type.ROOM_STATE) room.countDown();
                    if (update.getType() == NetworkUpdate.Type.CONNECTION_FAILED)
                        failureReason.set(update.getReasonCode());
                });
                client.connect(new ConnectionOptions(URI.create("ws://127.0.0.1:"
                        + server.getLocalPort() + "/ws"), "public-test-token"));
                check(connected.await(5, TimeUnit.SECONDS), "503 should retry during initial connection");
                check(failureReason.get() == null, "Transient 503 surfaced as terminal failure");
                client.send(RoomCommand.createRoom(2));
                check(room.await(5, TimeUnit.SECONDS), "Game command works after retry");
            } finally { client.close(); }
            peer.join(1000);
            if (failure.get() != null) throw new AssertionError("Cold start fixture failed", failure.get());
        }
    }

    private static void closingDuringBackoffCancelsRetry() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))) {
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            AtomicBoolean secondConnection = new AtomicBoolean();
            CountDownLatch firstRejected = new CountDownLatch(1);
            Thread peer = new Thread(() -> {
                try (Socket first = server.accept()) { respond503(first); firstRejected.countDown(); }
                catch (Throwable error) { failure.set(error); return; }
                try {
                    server.setSoTimeout(1500);
                    try (Socket ignored = server.accept()) { secondConnection.set(true); }
                } catch (SocketTimeoutException expected) { /* no further connection */ }
                catch (Throwable error) { failure.set(error); }
            }, "ranked-ws-cancel-retry-fixture");
            peer.setDaemon(true); peer.start();
            WebSocketNetworkClient client = new WebSocketNetworkClient(true, 5000);
            client.connect(new ConnectionOptions(URI.create("ws://127.0.0.1:"
                    + server.getLocalPort() + "/ws"), "public-test-token"));
            check(firstRejected.await(3, TimeUnit.SECONDS), "First 503 was not sent");
            client.close();
            peer.join(2500);
            check(!secondConnection.get(), "Closed client retried a connection");
            if (failure.get() != null) throw new AssertionError("Cancellation fixture failed", failure.get());
        }
    }

    private static void respond503(Socket socket) throws IOException {
        socket.setSoTimeout(3000);
        InputStream input = socket.getInputStream();
        StringBuilder request = new StringBuilder();
        while (request.length() < 8192 && !request.toString().endsWith("\r\n\r\n")) {
            int value = input.read();
            if (value < 0) throw new IOException("No WebSocket request");
            request.append((char) value);
        }
        if (!request.toString().contains("Authorization: Bearer public-test-token\r\n"))
            throw new IOException("Missing authentication header");
        socket.getOutputStream().write(("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static void serve(Socket socket) throws Exception {
        socket.setSoTimeout(5000);
        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (head.size() < 8192) {
            int read = input.read();
            if (read < 0) throw new IOException("No handshake");
            head.write(read);
            byte[] bytes = head.toByteArray(); int length = bytes.length;
            if (length >= 4 && bytes[length - 4] == '\r' && bytes[length - 3] == '\n'
                    && bytes[length - 2] == '\r' && bytes[length - 1] == '\n') break;
        }
        String request = new String(head.toByteArray(), StandardCharsets.US_ASCII);
        check(request.contains("Authorization: Bearer public-test-token\r\n"), "Bearer header");
        String key = null;
        for (String line : request.split("\r\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-key:"))
                key = line.substring(line.indexOf(':') + 1).trim();
        }
        check(key != null, "WebSocket key");
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
        send(output, NetworkUpdate.connected());
        int first = input.read(), second = input.read();
        check(first == 0x81 && (second & 0x80) != 0, "Client text frame is masked");
        int length = second & 0x7f;
        check(length < 126, "Fixture request size");
        byte[] mask = new byte[4]; readFully(input, mask);
        byte[] requestPayload = new byte[length]; readFully(input, requestPayload);
        for (int index = 0; index < length; index++) requestPayload[index] ^= mask[index & 3];
        WireRequest decoded = WireCodec.decodeRequestPayload(requestPayload);
        check(decoded.getKind() == WireRequest.Kind.ROOM
                && decoded.getRoomCommand().getType() == RoomCommand.Type.CREATE_ROOM, "Room command");
        send(output, NetworkUpdate.roomState(new RoomState("room-1", 1,
                RoomState.Phase.WAITING, "local", Collections.singletonMap("local", false))));
        send(output, NetworkUpdate.rankedSaveStatus("room-1", "match-1", "SAVED"));
    }

    private static void send(OutputStream output, NetworkUpdate update) throws IOException {
        byte[] bytes = WireCodec.encodeUpdatePayload(update);
        output.write(0x81);
        if (bytes.length < 126) output.write(bytes.length);
        else { output.write(126); output.write((bytes.length >>> 8) & 0xff); output.write(bytes.length & 0xff); }
        output.write(bytes); output.flush();
    }

    private static void readFully(InputStream input, byte[] bytes) throws IOException {
        int read = 0;
        while (read < bytes.length) {
            int count = input.read(bytes, read, bytes.length - read);
            if (count < 0) throw new IOException("Truncated frame");
            read += count;
        }
    }

    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
