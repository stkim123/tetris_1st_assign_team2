package kr.ac.jbnu.se.tetris.network.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.ac.jbnu.se.tetris.auth.AuthIdentity;
import kr.ac.jbnu.se.tetris.auth.AuthException;
import kr.ac.jbnu.se.tetris.auth.TokenVerifier;
import kr.ac.jbnu.se.tetris.auth.SupabaseTokenVerifier;
import kr.ac.jbnu.se.tetris.firebase.server.FirebaseServerContext;
import kr.ac.jbnu.se.tetris.firebase.server.FirebaseTokenVerifier;
import kr.ac.jbnu.se.tetris.firebase.server.FirestoreRankedMatchStore;
import kr.ac.jbnu.se.tetris.battle.BattleManager;
import kr.ac.jbnu.se.tetris.battle.BattleResult;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.ParticipantSpec;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RequestOutcome;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.RoomState;
import kr.ac.jbnu.se.tetris.network.protocol.WireCodec;
import kr.ac.jbnu.se.tetris.network.protocol.WireRequest;
import kr.ac.jbnu.se.tetris.ranking.MatchRecord;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;
import kr.ac.jbnu.se.tetris.ranking.RankedMatchStore;
import kr.ac.jbnu.se.tetris.ranking.RunLease;
import kr.ac.jbnu.se.tetris.ranking.SupabaseRankedMatchStore;
import kr.ac.jbnu.se.tetris.supabase.SupabaseConfig;

/** One-instance Render HTTP/WebSocket game server. All BattleManager calls run on the room queue. */
public final class RenderGameServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(RenderGameServer.class.getName());
    private static final int DEFAULT_MAX_WS_CONNECTIONS = 128;
    private static final int DEFAULT_MAX_PENDING_AUTH = 16;
    private static final int CONTROL_SOCKET_RESERVE = 8;
    private static final int MAX_PENDING_REQUESTS = 32;
    private static final int MAX_PENDING_WRITES = 128;
    private static final int AUTH_TIMEOUT_SECONDS = 18;
    /** 서버가 방 시계를 깨우는 주기. 낙하 속도와 무관하며 BattleManager.advance(실제 경과 ms)로 처리한다. */
    private static final long TICK_MILLIS = 100;
    /** 중력 이동이나 이벤트가 없어도 이 간격마다는 스냅샷을 보낸다(남은 시간 표시 등 동기화용). */
    private static final long HEARTBEAT_NANOS = TimeUnit.MILLISECONDS.toNanos(400);
    /** 한 번에 처리할 최대 경과 시간. 서버가 잠시 멈췄다 깨어나도 게임이 한꺼번에 빨리 감기지 않게 한다. */
    private static final long MAX_ADVANCE_MILLIS = 1000;
    private static final long PING_EVERY_TICKS = 30000 / TICK_MILLIS;
    private static final long LEASE_REFRESH_SECONDS = 5;
    private static final String RULES_VERSION = "pvp-v1";

    private final TokenVerifier verifier;
    private final RankedMatchStore store;
    private final byte[] adminToken;
    private final int requestedPort;
    private final int maxWebSocketConnections;
    private final int maxPendingAuth;
    private final String runId = UUID.randomUUID().toString();
    private final ScheduledThreadPoolExecutor roomsExecutor = new ScheduledThreadPoolExecutor(1,
            task -> daemon(task, "render-room"));
    private final ScheduledThreadPoolExecutor leaseExecutor = new ScheduledThreadPoolExecutor(1,
            task -> daemon(task, "render-lease"));
    private final ThreadPoolExecutor authExecutor;
    private final ThreadPoolExecutor persistenceExecutor = new ThreadPoolExecutor(2, 2, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16), task -> daemon(task, "render-store"));
    private final Map<String, Room> rooms = new LinkedHashMap<String, Room>();
    /** Immutable match IDs retain their own settlement state after a room is reused. Room-queue owned. */
    private final Map<String, MatchAttempt> unresolvedMatches = new LinkedHashMap<String, MatchAttempt>();
    private final ConcurrentHashMap<String, Peer> users = new ConcurrentHashMap<String, Peer>();
    private final ConcurrentHashMap<Channel, Boolean> sockets = new ConcurrentHashMap<Channel, Boolean>();
    private final AtomicInteger socketCount = new AtomicInteger();
    private final Semaphore webSocketSlots;
    private final AtomicInteger unauthenticated = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile boolean admissionOpen;
    private volatile boolean draining;
    private volatile boolean leaseOwned;
    private volatile boolean everOwnedLease;
    private volatile NioEventLoopGroup boss;
    private volatile NioEventLoopGroup workers;
    private volatile Channel listener;
    private int tickCounter;

    public RenderGameServer(TokenVerifier verifier, RankedMatchStore store, String adminToken, int port) {
        this(verifier, store, adminToken, port, DEFAULT_MAX_WS_CONNECTIONS, DEFAULT_MAX_PENDING_AUTH);
    }

    public RenderGameServer(TokenVerifier verifier, RankedMatchStore store, String adminToken, int port,
                            int maxWebSocketConnections, int maxPendingAuth) {
        if (verifier == null || store == null || adminToken == null || adminToken.length() < 24
                || port < 0 || port > 65535 || maxWebSocketConnections < 1
                || maxWebSocketConnections > 4096 || maxPendingAuth < 1 || maxPendingAuth > 4096) {
            throw new IllegalArgumentException("Invalid server settings");
        }
        this.verifier = verifier;
        this.store = store;
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
        this.requestedPort = port;
        this.maxWebSocketConnections = maxWebSocketConnections;
        this.maxPendingAuth = maxPendingAuth;
        this.webSocketSlots = new Semaphore(maxWebSocketConnections);
        this.authExecutor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(maxPendingAuth), task -> daemon(task, "render-auth"));
        roomsExecutor.setRemoveOnCancelPolicy(true);
        leaseExecutor.setRemoveOnCancelPolicy(true);
    }

    public synchronized void start() throws InterruptedException {
        if (closed.get()) throw new IllegalStateException("Server closed");
        if (listener != null) return;
        boss = new NioEventLoopGroup(1,
                (java.util.concurrent.ThreadFactory) task -> daemon(task, "render-accept"));
        workers = new NioEventLoopGroup(2,
                (java.util.concurrent.ThreadFactory) task -> daemon(task, "render-io"));
        try {
            listener = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec(4096, 8192, 8192),
                                    new HttpObjectAggregator(4096),
                                    new WebSocketFrameAggregator(WireCodec.MAX_FRAME_BYTES),
                                    new FrontHandler());
                        }
                    }).bind("0.0.0.0", requestedPort).sync().channel();
            roomsExecutor.scheduleAtFixedRate(this::tickRooms, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
            leaseExecutor.scheduleWithFixedDelay(this::refreshLease, 0, LEASE_REFRESH_SECONDS,
                    TimeUnit.SECONDS);
        } catch (InterruptedException | RuntimeException error) {
            close();
            throw error;
        }
    }

    public int getPort() {
        Channel current = listener;
        return current == null ? requestedPort : ((InetSocketAddress) current.localAddress()).getPort();
    }

    public String getRunId() { return runId; }
    public boolean isAdmissionOpen() { return admissionOpen; }

    private void refreshLease() {
        if (closed.get()) return;
        try {
            RunLease lease = leaseOwned ? store.renewRun(runId) : store.claimRun(runId);
            applyLease(lease);
        } catch (Exception unavailable) {
            // A failed RPC does not prove the lease expired. Freeze play and retry;
            // a confirmed rejected claim/renew will close this fenced process.
            admissionOpen = false;
            leaseOwned = false;
        }
    }

    private void applyLease(RunLease lease) {
        if (closed.get()) return;
        leaseOwned = lease.isOwned();
        if (leaseOwned) everOwnedLease = true;
        admissionOpen = leaseOwned && lease.isEnabled() && !draining;
        if (!leaseOwned && everOwnedLease) {
            // This run was fenced; it must never resume under the same run ID.
            admissionOpen = false;
            close();
        }
    }

    private final class FrontHandler extends SimpleChannelInboundHandler<Object> {
        private Peer peer;
        private Peer pendingPeer;
        private boolean upgrading;
        private boolean reservedWebSocketSlot;
        private boolean socketRegistered;

        @Override public void channelActive(ChannelHandlerContext ctx) {
            int count = socketCount.incrementAndGet();
            if (closed.get() || count > maxWebSocketConnections + CONTROL_SOCKET_RESERVE) {
                socketCount.decrementAndGet();
                ctx.close(); return;
            }
            socketRegistered = true;
            sockets.put(ctx.channel(), Boolean.TRUE);
        }

        @Override protected void channelRead0(ChannelHandlerContext ctx, Object message) throws Exception {
            if (message instanceof FullHttpRequest) {
                handleHttp(ctx, (FullHttpRequest) message);
                return;
            }
            if (!(message instanceof WebSocketFrame) || peer == null || peer.disconnected.get()) {
                ctx.close(); return;
            }
            if (message instanceof TextWebSocketFrame) {
                if (!peer.allowRequest() || !peer.pendingRequests.tryAcquire()) { peer.disconnect(); return; }
                byte[] payload = ByteBufUtil.getBytes(((TextWebSocketFrame) message).content());
                WireRequest request;
                try { request = WireCodec.decodeRequestPayload(payload); }
                catch (IOException invalid) { peer.pendingRequests.release(); peer.disconnect(); return; }
                try {
                    roomsExecutor.execute(() -> {
                        try { handle(peer, request); }
                        finally { peer.pendingRequests.release(); }
                    });
                } catch (RejectedExecutionException stopped) {
                    peer.pendingRequests.release(); peer.disconnect();
                }
            } else if (message instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(((PingWebSocketFrame) message).content().retain()));
            } else if (message instanceof PongWebSocketFrame) {
                peer.lastPongNanos = System.nanoTime();
            } else if (message instanceof CloseWebSocketFrame) {
                peer.disconnect();
            } else {
                peer.disconnect();
            }
        }

        private void handleHttp(ChannelHandlerContext ctx, FullHttpRequest request) {
            String path = request.uri();
            if ("GET".equals(request.method().name())
                    && ("/health".equals(path) || "/healthz".equals(path))) {
                respond(ctx, HttpResponseStatus.OK, "ok"); return;
            }
            if ("GET".equals(request.method().name())
                    && ("/ready".equals(path) || "/readyz".equals(path))) {
                respond(ctx, admissionOpen && leaseOwned && !draining && !closed.get()
                        ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE,
                        admissionOpen && leaseOwned && !draining && !closed.get() ? "ready" : "not ready");
                return;
            }
            if ("GET".equals(request.method().name()) && "/".equals(path)) {
                respond(ctx, HttpResponseStatus.OK, "Tetris Ranked PvP server\n"
                        + "Desktop game endpoint: /ws\n"
                        + "Match admission: " + (admissionOpen && !draining ? "open" : "closed"));
                return;
            }
            if (path.startsWith("/admin/")) { handleAdmin(ctx, request); return; }
            if ("GET".equals(request.method().name()) && "/api/leaderboard".equals(path)) {
                handleLeaderboard(ctx, request); return;
            }
            if (!"GET".equals(request.method().name()) || !"/ws".equals(path) || upgrading
                    || !request.decoderResult().isSuccess()) {
                respond(ctx, HttpResponseStatus.NOT_FOUND, "not found"); return;
            }
            if (!admissionOpen || draining) {
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "admission closed"); return;
            }
            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || authorization.length() > 8200 || authorization.length() < 15) {
                respond(ctx, HttpResponseStatus.UNAUTHORIZED, "authentication required"); return;
            }
            if (unauthenticated.incrementAndGet() > maxPendingAuth) {
                unauthenticated.decrementAndGet();
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "server busy"); return;
            }
            if (!webSocketSlots.tryAcquire()) {
                unauthenticated.decrementAndGet();
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "game sockets full"); return;
            }
            reservedWebSocketSlot = true;
            upgrading = true;
            request.retain();
            ctx.executor().schedule(() -> {
                if (peer == null && upgrading) ctx.close();
            }, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                authExecutor.execute(() -> {
                    AuthIdentity identity = null;
                    HttpResponseStatus failure = null;
                    try { identity = verifier.verify(authorization.substring(7)); }
                    catch (AuthException invalid) { failure = HttpResponseStatus.UNAUTHORIZED; }
                    catch (IOException unavailable) { failure = HttpResponseStatus.SERVICE_UNAVAILABLE; }
                    catch (RuntimeException unavailable) { failure = HttpResponseStatus.SERVICE_UNAVAILABLE; }
                    final AuthIdentity checked = identity;
                    final HttpResponseStatus failureStatus = failure;
                    ctx.executor().execute(() -> {
                        try {
                            if (!ctx.channel().isActive() || !upgrading) return;
                            if (failureStatus != null) {
                                respond(ctx, failureStatus, failureStatus == HttpResponseStatus.UNAUTHORIZED
                                        ? "authentication failed" : "authentication unavailable"); return;
                            }
                            if (checked == null || checked.getExpiresAtEpochSecond()
                                    <= System.currentTimeMillis() / 1000L) {
                                respond(ctx, HttpResponseStatus.UNAUTHORIZED, "authentication failed"); return;
                            }
                            if (!admissionOpen || draining) {
                                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "admission closed"); return;
                            }
                            WebSocketServerHandshaker handshaker = new WebSocketServerHandshakerFactory(
                                    "ws://localhost/ws", null, false, WireCodec.MAX_FRAME_BYTES).newHandshaker(request);
                            if (handshaker == null) {
                                respond(ctx, HttpResponseStatus.BAD_REQUEST, "unsupported websocket"); return;
                            }
                            Peer accepted = new Peer(ctx.channel(), checked);
                            if (users.putIfAbsent(accepted.id, accepted) != null) {
                                respond(ctx, HttpResponseStatus.CONFLICT, "account already connected"); return;
                            }
                            pendingPeer = accepted;
                            handshaker.handshake(ctx.channel(), request).addListener((ChannelFutureListener) result -> {
                                if (!result.isSuccess()) { accepted.disconnect(); return; }
                                peer = accepted;
                                pendingPeer = null;
                                upgrading = false;
                                unauthenticated.decrementAndGet();
                                accepted.send(NetworkUpdate.connected());
                            });
                        } finally { request.release(); }
                    });
                });
            } catch (RejectedExecutionException full) {
                request.release();
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "server busy");
            }
        }

        private void handleLeaderboard(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (!(store instanceof FirestoreRankedMatchStore)) {
                respond(ctx, HttpResponseStatus.NOT_FOUND, "not found"); return;
            }
            if (!admissionOpen || !leaseOwned || draining) {
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "admission closed"); return;
            }
            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || authorization.length() > 8200 || authorization.length() < 15) {
                respond(ctx, HttpResponseStatus.UNAUTHORIZED, "authentication required"); return;
            }
            final String token = authorization.substring(7);
            try {
                authExecutor.execute(() -> {
                    HttpResponseStatus failure = null;
                    try {
                        AuthIdentity identity = verifier.verify(token);
                        if (identity == null || identity.getExpiresAtEpochSecond()
                                <= System.currentTimeMillis() / 1000L) failure = HttpResponseStatus.UNAUTHORIZED;
                    } catch (AuthException invalid) { failure = HttpResponseStatus.UNAUTHORIZED; }
                    catch (Exception unavailable) { failure = HttpResponseStatus.SERVICE_UNAVAILABLE; }
                    if (failure != null) {
                        final HttpResponseStatus status = failure;
                        respondOnEventLoop(ctx, status, status == HttpResponseStatus.UNAUTHORIZED
                                ? "authentication failed" : "authentication unavailable");
                        return;
                    }
                    try {
                        persistenceExecutor.execute(() -> {
                            try {
                                if (!admissionOpen || !leaseOwned || draining) {
                                    respondOnEventLoop(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                                            "admission closed"); return;
                                }
                                JsonArray array = new JsonArray();
                                for (LeaderboardEntry entry : ((FirestoreRankedMatchStore) store).top100()) {
                                    JsonObject row = new JsonObject();
                                    row.addProperty("rank", entry.getRank());
                                    row.addProperty("user_id", entry.getUserId());
                                    row.addProperty("display_name", entry.getDisplayName());
                                    row.addProperty("rating", entry.getRating());
                                    row.addProperty("wins", entry.getWins());
                                    row.addProperty("losses", entry.getLosses());
                                    row.addProperty("games", entry.getGames());
                                    array.add(row);
                                }
                                respondOnEventLoop(ctx, HttpResponseStatus.OK, array.toString(),
                                        "application/json; charset=utf-8");
                            } catch (Exception unavailable) {
                                LOG.log(Level.WARNING, "Leaderboard query failed", unavailable);
                                respondOnEventLoop(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                                        "leaderboard unavailable");
                            }
                        });
                    } catch (RejectedExecutionException full) {
                        respondOnEventLoop(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "server busy");
                    }
                });
            } catch (RejectedExecutionException full) {
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "server busy");
            }
        }

        private void handleAdmin(ChannelHandlerContext ctx, FullHttpRequest request) {
            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            byte[] supplied = authorization != null && authorization.startsWith("Bearer ")
                    ? authorization.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
            if (!MessageDigest.isEqual(adminToken, supplied)) {
                respond(ctx, HttpResponseStatus.UNAUTHORIZED, "unauthorized"); return;
            }
            String path = request.uri();
            String method = request.method().name();
            if ("GET".equals(method) && "/admin/status".equals(path)) {
                try {
                    roomsExecutor.execute(() -> {
                        int active = 0;
                        for (MatchAttempt match : unresolvedMatches.values()) {
                            if (match.battle != null && match.battle.getState().getStatus()
                                    == BattleState.Status.RUNNING) active++;
                        }
                        StringBuilder ids = new StringBuilder();
                        int shown = 0;
                        for (String id : unresolvedMatches.keySet()) {
                            if (shown++ == 16) break;
                            if (ids.length() != 0) ids.append(',');
                            ids.append(id);
                        }
                        String status = "runId=" + runId + " admission=" + admissionOpen
                                + " draining=" + draining + " users=" + users.size()
                                + " rooms=" + rooms.size() + " active=" + active
                                + " unresolved=" + unresolvedMatches.size() + " unresolvedIds=" + ids;
                        ctx.executor().execute(() -> respond(ctx, HttpResponseStatus.OK, status));
                    });
                } catch (RejectedExecutionException stopped) {
                    respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "server stopping");
                }
            } else if ("POST".equals(method) && "/admin/drain".equals(path)) {
                draining = true; admissionOpen = false;
                changeAdmission(ctx, false);
            } else if ("POST".equals(method) && "/admin/open".equals(path)) {
                changeAdmission(ctx, true);
            } else if ("POST".equals(method) && path.startsWith("/admin/recover-stopped-run?runId=")) {
                String stoppedRun = path.substring("/admin/recover-stopped-run?runId=".length());
                if (!stoppedRun.matches("[A-Za-z0-9-]{1,128}") || runId.equals(stoppedRun)) {
                    respond(ctx, HttpResponseStatus.BAD_REQUEST, "invalid run id"); return;
                }
                if (admissionOpen) { respond(ctx, HttpResponseStatus.CONFLICT, "close admission first"); return; }
                try {
                    persistenceExecutor.execute(() -> {
                        try {
                            int settled = store.voidStoppedRun(runId, stoppedRun);
                            ctx.executor().execute(() -> respond(ctx, HttpResponseStatus.OK, "voided=" + settled));
                        } catch (Exception failure) {
                            ctx.executor().execute(() -> respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                                    "recovery failed"));
                        }
                    });
                } catch (RejectedExecutionException full) {
                    respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "server busy");
                }
            } else respond(ctx, HttpResponseStatus.NOT_FOUND, "not found");
        }

        private void changeAdmission(ChannelHandlerContext ctx, boolean enabled) {
            try {
                leaseExecutor.execute(() -> {
                    try {
                        RunLease lease = store.setAdmission(runId, enabled);
                        if (!lease.isOwned() || lease.isEnabled() != enabled) {
                            ctx.executor().execute(() -> respond(ctx, HttpResponseStatus.CONFLICT,
                                    "run does not own admission"));
                            return;
                        }
                        draining = !enabled;
                        applyLease(lease);
                        ctx.executor().execute(() -> respond(ctx, HttpResponseStatus.OK,
                                enabled ? "open" : "draining"));
                    } catch (Exception failure) {
                        admissionOpen = false;
                        ctx.executor().execute(() -> respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                                "admission update failed"));
                    }
                });
            } catch (RejectedExecutionException stopped) {
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "server stopping");
            }
        }

        @Override public void channelInactive(ChannelHandlerContext ctx) {
            if (socketRegistered) {
                sockets.remove(ctx.channel());
                socketCount.decrementAndGet();
                socketRegistered = false;
            }
            if (peer != null) peer.disconnect();
            else if (pendingPeer != null) pendingPeer.disconnect();
            if (reservedWebSocketSlot) { reservedWebSocketSlot = false; webSocketSlots.release(); }
            if (upgrading) { upgrading = false; unauthenticated.decrementAndGet(); }
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable failure) {
            ctx.close();
        }
    }

    private static void respond(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        respond(ctx, status, body, "text/plain; charset=utf-8");
    }

    private static void respondOnEventLoop(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        respondOnEventLoop(ctx, status, body, "text/plain; charset=utf-8");
    }

    private static void respondOnEventLoop(ChannelHandlerContext ctx, HttpResponseStatus status, String body,
                                           String contentType) {
        try { ctx.executor().execute(() -> respond(ctx, status, body, contentType)); }
        catch (RejectedExecutionException stopped) { ctx.channel().close(); }
    }

    private static void respond(ChannelHandlerContext ctx, HttpResponseStatus status, String body,
                                String contentType) {
        if (!ctx.channel().isActive()) return;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void tickRooms() {
        if (leaseOwned) {
            long nowNanos = System.nanoTime();
            for (Room room : new ArrayList<Room>(rooms.values())) {
                if (room.battle == null || room.battle.getState().getStatus() != BattleState.Status.RUNNING) {
                    room.lastTickNanos = 0; // 일시정지·종료 후 재개할 때 쌓인 시간이 한꺼번에 흐르지 않게 한다.
                    continue;
                }
                if (room.lastTickNanos == 0) { room.lastTickNanos = nowNanos; continue; }
                long rawMillis = (nowNanos - room.lastTickNanos) / 1_000_000L;
                if (rawMillis <= 0) continue;
                long deltaMillis = Math.min(MAX_ADVANCE_MILLIS, rawMillis);
                // 정상일 때는 1ms 미만 나머지를 남겨 둬서 게임 시계가 실제 시간보다 느려지지 않게 한다.
                // 너무 오래 멈췄다 깨어난 경우에만 남은 시간을 버린다.
                room.lastTickNanos = rawMillis > MAX_ADVANCE_MILLIS
                        ? nowNanos : room.lastTickNanos + deltaMillis * 1_000_000L;
                try {
                    long stepsBefore = room.battle.getGravityStepCount();
                    BattleResult tick = room.battle.advance(deltaMillis);
                    boolean moved = room.battle.getGravityStepCount() != stepsBefore
                            || !tick.getEvents().isEmpty();
                    if (moved || nowNanos - room.lastBroadcastNanos >= HEARTBEAT_NANOS) {
                        room.lastBroadcastNanos = nowNanos;
                        broadcastSnapshot(room);
                    }
                    broadcastPickups(room, tick);
                    finishIfNeeded(room);
                } catch (RuntimeException failure) {
                    invalidate(room, "SERVER_TICK_FAILED");
                }
            }
        }
        if (++tickCounter % PING_EVERY_TICKS == 0) {
            long now = System.nanoTime();
            for (Peer peer : users.values()) {
                if (now - peer.lastPongNanos > TimeUnit.SECONDS.toNanos(90)) peer.disconnect();
                else peer.channel.writeAndFlush(new PingWebSocketFrame());
            }
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        for (Peer peer : users.values()) {
            if (peer.expiresAtEpochSecond <= nowSeconds) peer.disconnect();
        }
    }

    private void handle(Peer peer, WireRequest request) {
        if (peer.disconnected.get()) return;
        if (request.getRequestId() <= peer.lastRequestId) {
            outcome(peer, request.getRequestId(), false, "REQUEST_ID_NOT_INCREASING"); return;
        }
        peer.lastRequestId = request.getRequestId();
        switch (request.getKind()) {
            case ROOM: handleRoom(peer, request.getRequestId(), request.getRoomCommand()); break;
            case INTENT: handleIntent(peer, request); break;
            case SNAPSHOT: handleSnapshot(peer, request); break;
            case AUTH_REFRESH: refreshAuthentication(peer, request); break;
            default: outcome(peer, request.getRequestId(), false, "UNKNOWN_REQUEST"); break;
        }
    }

    private void refreshAuthentication(Peer peer, WireRequest request) {
        if (!peer.refreshInProgress.compareAndSet(false, true)) {
            outcome(peer, request.getRequestId(), false, "AUTH_REFRESH_BUSY");
            return;
        }
        try {
            authExecutor.execute(() -> {
                AuthIdentity verified = null;
                String failure = null;
                try { verified = verifier.verify(request.getAccessToken()); }
                catch (AuthException invalid) { failure = "AUTH_REFRESH_FAILED"; }
                catch (IOException unavailable) { failure = "AUTH_SERVICE_UNAVAILABLE"; }
                catch (RuntimeException unavailable) { failure = "AUTH_SERVICE_UNAVAILABLE"; }
                final AuthIdentity fresh = verified;
                final String reason = failure;
                try {
                    roomsExecutor.execute(() -> {
                        try {
                            if (peer.disconnected.get()) return;
                            if (reason != null || fresh == null || !peer.id.equals(fresh.getUserId())
                                    || fresh.getExpiresAtEpochSecond() <= System.currentTimeMillis() / 1000L) {
                                outcome(peer, request.getRequestId(), false,
                                        reason == null ? "AUTH_REFRESH_FAILED" : reason);
                            } else {
                                peer.expiresAtEpochSecond = fresh.getExpiresAtEpochSecond();
                                outcome(peer, request.getRequestId(), true, null);
                            }
                        } finally { peer.refreshInProgress.set(false); }
                    });
                } catch (RejectedExecutionException stopped) { peer.refreshInProgress.set(false); }
            });
        } catch (RejectedExecutionException full) {
            peer.refreshInProgress.set(false);
            outcome(peer, request.getRequestId(), false, "AUTH_SERVICE_UNAVAILABLE");
        }
    }

    private void handleRoom(Peer peer, long requestId, RoomCommand command) {
        if (command == null) { outcome(peer, requestId, false, "INVALID_ROOM_COMMAND"); return; }
        switch (command.getType()) {
            case CREATE_ROOM: createRoom(peer, requestId, command); break;
            case JOIN_ROOM: joinRoom(peer, requestId, command.getRoomId()); break;
            case GET_ROOM_STATE:
                if (peer.room == null) outcome(peer, requestId, false, "NOT_IN_ROOM");
                else { outcome(peer, requestId, true, null); sendRoomState(peer); }
                break;
            case SET_READY: setReady(peer, requestId, command.getReady()); break;
            case REQUEST_REMATCH: requestRematch(peer, requestId); break;
            case LEAVE_ROOM: leaveRoom(peer, requestId); break;
            default: outcome(peer, requestId, false, "UNKNOWN_ROOM_COMMAND"); break;
        }
    }

    /** 헷갈리는 문자(0/O, 1/I/L)를 뺀 31자. 6자리면 약 8.9억 가지라 방 수가 적은 서버에서는 충돌이 사실상 없다. */
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;
    private final java.security.SecureRandom roomCodeRandom = new java.security.SecureRandom();

    /** 현재 열려 있는 방과 겹치지 않는 짧은 방 코드를 만든다. 20번 시도해도 겹치면 null. */
    private String newRoomCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
            for (int i = 0; i < ROOM_CODE_LENGTH; i++)
                code.append(ROOM_CODE_ALPHABET.charAt(roomCodeRandom.nextInt(ROOM_CODE_ALPHABET.length())));
            if (!rooms.containsKey(code.toString())) return code.toString();
        }
        return null;
    }

    private void createRoom(Peer peer, long requestId, RoomCommand command) {
        if (!admissionOpen || draining) { outcome(peer, requestId, false, "ADMISSION_CLOSED"); return; }
        if (peer.room != null) { outcome(peer, requestId, false, "ALREADY_IN_ROOM"); return; }
        if (command.getMaxParticipants() == null || command.getMaxParticipants().intValue() != 2) {
            outcome(peer, requestId, false, "ROOM_SIZE_NOT_SUPPORTED"); return;
        }
        String code = newRoomCode();
        if (code == null) { outcome(peer, requestId, false, "ROOM_CODE_EXHAUSTED"); return; }
        Room room = new Room(code);
        rooms.put(room.id, room);
        room.members.put(peer.id, peer);
        room.ready.put(peer.id, Boolean.FALSE);
        room.version++;
        peer.room = room;
        outcome(peer, requestId, true, null);
        sendRoomState(peer);
    }

    private void joinRoom(Peer peer, long requestId, String roomId) {
        if (!admissionOpen || draining) { outcome(peer, requestId, false, "ADMISSION_CLOSED"); return; }
        if (peer.room != null) { outcome(peer, requestId, false, "ALREADY_IN_ROOM"); return; }
        Room room = rooms.get(roomId == null ? null : roomId.trim().toUpperCase(java.util.Locale.ROOT));
        if (room == null) { outcome(peer, requestId, false, "ROOM_NOT_FOUND"); return; }
        if (room.members.size() >= 2) { outcome(peer, requestId, false, "ROOM_FULL"); return; }
        if (room.starting || room.saveState == SaveState.PENDING || room.saveState == SaveState.FAILED
                || (room.battle != null && room.battle.getState().getStatus() == BattleState.Status.RUNNING)) {
            outcome(peer, requestId, false, "MATCH_BUSY"); return;
        }
        room.battle = null;
        room.matchId = null;
        room.saveState = SaveState.NONE;
        for (String id : room.ready.keySet()) room.ready.put(id, Boolean.FALSE);
        room.members.put(peer.id, peer);
        room.ready.put(peer.id, Boolean.FALSE);
        room.version++;
        peer.room = room;
        outcome(peer, requestId, true, null);
        broadcastRoomState(room);
    }

    private void setReady(Peer peer, long requestId, Boolean ready) {
        Room room = peer.room;
        if (room == null) { outcome(peer, requestId, false, "NOT_IN_ROOM"); return; }
        if (ready == null) { outcome(peer, requestId, false, "INVALID_READY"); return; }
        if (room.starting || room.saveState == SaveState.PENDING || room.saveState == SaveState.FAILED) {
            outcome(peer, requestId, false, "RESULT_UNRESOLVED"); return;
        }
        if (room.battle != null && room.battle.getState().getStatus() == BattleState.Status.RUNNING) {
            outcome(peer, requestId, false, "MATCH_RUNNING"); return;
        }
        room.ready.put(peer.id, ready);
        room.version++;
        outcome(peer, requestId, true, null);
        broadcastRoomState(room);
        if (allReady(room)) startMatch(room);
    }

    private void requestRematch(Peer peer, long requestId) {
        Room room = peer.room;
        if (room == null) { outcome(peer, requestId, false, "NOT_IN_ROOM"); return; }
        if (room.battle == null || room.battle.getState().getStatus() != BattleState.Status.FINISHED) {
            outcome(peer, requestId, false, "MATCH_NOT_FINISHED"); return;
        }
        setReady(peer, requestId, Boolean.TRUE);
    }

    private static boolean allReady(Room room) {
        return room.members.size() == 2 && room.ready.size() == 2
                && !room.ready.containsValue(Boolean.FALSE);
    }

    private void startMatch(Room room) {
        if (!admissionOpen || draining || room.starting || room.saveState == SaveState.PENDING
                || room.saveState == SaveState.FAILED) return;
        List<Peer> pair = new ArrayList<Peer>(room.members.values());
        if (pair.get(0).id.equals(pair.get(1).id)) { invalidate(room, "SELF_MATCH"); return; }
        room.starting = true;
        room.matchId = UUID.randomUUID().toString();
        room.startEpoch++;
        final int epoch = room.startEpoch;
        final String matchId = room.matchId;
        final String firstId = pair.get(0).id, secondId = pair.get(1).id;
        unresolvedMatches.put(matchId, new MatchAttempt(matchId, room, firstId, secondId));
        for (Peer member : pair) room.ready.put(member.id, Boolean.FALSE);
        room.version++;
        broadcastRoomState(room);
        try { persistenceExecutor.execute(() -> registerMatch(room, pair, epoch, matchId, firstId, secondId, 0)); }
        catch (RejectedExecutionException full) {
            unresolvedMatches.remove(matchId);
            room.starting = false;
            room.matchId = null;
            for (Peer peer : pair) peer.send(NetworkUpdate.error("RANKING_BUSY"));
        }
    }

    private void registerMatch(Room room, List<Peer> pair, int epoch, String matchId,
                               String firstId, String secondId, int attempt) {
        MatchRecord record = null;
        try { record = store.beginMatch(matchId, runId, RULES_VERSION, firstId, secondId); }
        catch (Exception uncertain) {
            try {
                record = store.getMatch(matchId);
            } catch (Exception stillUncertain) { }
        }
        final MatchRecord saved = record;
        try {
            roomsExecutor.execute(() -> {
                if (room.startEpoch != epoch || !matchId.equals(room.matchId) || !room.starting) {
                    if (saved != null && saved.getStatus() == MatchRecord.Status.RUNNING) compensateStart(matchId);
                    else if (saved == null) reconcileAbandonedStart(matchId, 0);
                    else unresolvedMatches.remove(matchId);
                    return;
                }
                if (saved == null && attempt < 5) {
                    roomsExecutor.schedule(() -> {
                        try { persistenceExecutor.execute(() -> registerMatch(room, pair, epoch, matchId,
                                firstId, secondId, attempt + 1)); }
                        catch (RejectedExecutionException ignored) { }
                    }, Math.min(10, 1 << attempt), TimeUnit.SECONDS);
                } else if (saved == null) {
                    // A null lookup cannot prove an in-flight RPC will never commit later.
                    room.starting = false;
                    room.saveState = SaveState.FAILED;
                    for (Peer peer : pair) peer.send(NetworkUpdate.error("MATCH_REGISTRATION_UNCERTAIN"));
                } else started(room, pair, epoch, matchId, saved);
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private void reconcileAbandonedStart(String matchId, int attempt) {
        try {
            persistenceExecutor.execute(() -> {
                MatchRecord record = null;
                try { record = store.getMatch(matchId); }
                catch (Exception uncertain) { }
                if (record != null && record.getStatus() == MatchRecord.Status.RUNNING) {
                    settleVoid(matchId, "START_CANCELLED", 0);
                } else if (record != null) {
                    try { roomsExecutor.execute(() -> unresolvedMatches.remove(matchId)); }
                    catch (RejectedExecutionException ignored) { }
                } else if (attempt < 5 && !closed.get()) {
                    try { roomsExecutor.schedule(() -> reconcileAbandonedStart(matchId, attempt + 1),
                            Math.min(10, 1 << attempt), TimeUnit.SECONDS); }
                    catch (RejectedExecutionException ignored) { }
                }
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private void started(Room room, List<Peer> pair, int epoch, String matchId, MatchRecord record) {
        if (record == null) {
            if (room.startEpoch == epoch) {
                room.starting = false;
                room.saveState = SaveState.FAILED;
                for (Peer peer : pair) peer.send(NetworkUpdate.error("MATCH_REGISTRATION_UNCERTAIN"));
            }
            return;
        }
        boolean valid = record.getStatus() == MatchRecord.Status.RUNNING && room.startEpoch == epoch
                && room.starting && matchId.equals(room.matchId) && allPresent(room, pair)
                && admissionOpen && !draining;
        if (!valid) {
            if (record.getStatus() == MatchRecord.Status.RUNNING) compensateStart(matchId);
            else unresolvedMatches.remove(matchId);
            if (room.startEpoch == epoch) {
                room.starting = false;
                room.matchId = null;
                for (Peer peer : pair) if (!peer.disconnected.get()) peer.send(NetworkUpdate.error("MATCH_START_CANCELLED"));
            }
            return;
        }
        List<ParticipantSpec> specs = Arrays.asList(new ParticipantSpec(pair.get(0).id,
                        safeName(pair.get(0).identity.getDisplayName(), "Player 1"), 100),
                new ParticipantSpec(pair.get(1).id,
                        safeName(pair.get(1).identity.getDisplayName(), "Player 2"), 100));
        BattleManager battle = BattleManager.pvp(specs, System.nanoTime());
        BattleResult start = battle.start();
        if (!start.isAccepted()) {
            room.starting = false;
            compensateStart(matchId);
            for (Peer peer : pair) peer.send(NetworkUpdate.error("MATCH_START_FAILED"));
            return;
        }
        room.battle = battle;
        room.lastTickNanos = System.nanoTime();
        room.lastBroadcastNanos = room.lastTickNanos;
        MatchAttempt registered = unresolvedMatches.get(matchId);
        if (registered != null) registered.battle = battle;
        room.starting = false;
        room.saveState = SaveState.NONE;
        room.version++;
        broadcastRoomState(room);
        for (Peer member : pair) member.send(NetworkUpdate.matchStarted(room.id, room.version,
                room.matchId, member.id));
        broadcastSnapshot(room);
    }

    private static String safeName(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static boolean allPresent(Room room, List<Peer> pair) {
        return room.members.size() == 2 && room.members.get(pair.get(0).id) == pair.get(0)
                && room.members.get(pair.get(1).id) == pair.get(1)
                && !pair.get(0).disconnected.get() && !pair.get(1).disconnected.get();
    }

    private void compensateStart(String matchId) {
        try { persistenceExecutor.execute(() -> settleVoid(matchId, "START_CANCELLED", 0)); }
        catch (RejectedExecutionException full) { /* DB retains active match; operator recovery is required. */ }
    }

    private void handleIntent(Peer peer, WireRequest request) {
        if (!leaseOwned) { outcome(peer, request.getRequestId(), false, "ADMISSION_CLOSED"); return; }
        Room room = peer.room;
        if (room == null) { outcome(peer, request.getRequestId(), false, "NOT_IN_ROOM"); return; }
        if (room.matchId == null || !room.matchId.equals(request.getMatchId())) {
            outcome(peer, request.getRequestId(), false, "STALE_MATCH"); return;
        }
        if (room.battle == null || room.battle.getState().getStatus() != BattleState.Status.RUNNING) {
            outcome(peer, request.getRequestId(), false, "MATCH_NOT_RUNNING"); return;
        }
        PlayerIntent intent = request.getIntent();
        if (intent == null) { outcome(peer, request.getRequestId(), false, "INVALID_INTENT"); return; }
        long version = room.battle.getState().getVersion();
        BattleResult result = intent.getType() == GameAction.Type.USE_ITEM
                ? room.battle.submitItem(peer.id, intent.getItemUse())
                : room.battle.submit(peer.id, intent.getType());
        outcome(peer, request.getRequestId(), result.isAccepted(),
                result.isAccepted() ? null : result.getReason());
        if (result.getState().getVersion() != version) broadcastSnapshot(room);
        broadcastPickups(room, result);
        finishIfNeeded(room);
    }

    private void handleSnapshot(Peer peer, WireRequest request) {
        Room room = peer.room;
        if (room == null) { outcome(peer, request.getRequestId(), false, "NOT_IN_ROOM"); return; }
        if (room.matchId == null || !room.matchId.equals(request.getMatchId())) {
            outcome(peer, request.getRequestId(), false, "STALE_MATCH"); return;
        }
        if (room.battle == null) { outcome(peer, request.getRequestId(), false, "MATCH_NOT_STARTED"); return; }
        outcome(peer, request.getRequestId(), true, null);
        peer.send(NetworkUpdate.snapshot(room.id, room.matchId, room.battle.getState()));
    }

    private void finishIfNeeded(Room room) {
        if (room.battle == null || room.matchId == null || room.saveState != SaveState.NONE
                || room.battle.getState().getStatus() != BattleState.Status.FINISHED) return;
        BattleState state = room.battle.getState();
        if (state.getWinnerId() == null) { invalidate(room, "INDETERMINATE_RESULT"); return; }
        room.saveState = SaveState.PENDING;
        room.finalWinner = state.getWinnerId();
        room.finalReason = state.getReason() == null ? "BATTLE_FINISHED" : state.getReason();
        MatchAttempt pending = unresolvedMatches.get(room.matchId);
        if (pending != null) {
            pending.winnerId = room.finalWinner;
            pending.reason = room.finalReason;
        }
        broadcastSave(room, "SAVE_PENDING");
        settleFinal(room, 0);
    }

    private void settleFinal(Room room, int attempt) {
        final String matchId = room.matchId, winner = room.finalWinner, reason = room.finalReason;
        try {
            persistenceExecutor.execute(() -> {
                MatchRecord saved = null;
                try { saved = store.finishMatch(matchId, runId, winner, reason); }
                catch (Exception uncertain) {
                    try { saved = store.getMatch(matchId); } catch (Exception unknown) { }
                }
                final MatchRecord record = saved;
                roomsExecutor.execute(() -> {
                    if (!matchId.equals(room.matchId) || room.saveState != SaveState.PENDING) return;
                    if (record != null && record.getStatus() == MatchRecord.Status.FINALIZED
                            && winner.equals(record.getWinnerUserId())) {
                        room.saveState = SaveState.SAVED;
                        unresolvedMatches.remove(matchId);
                        broadcastSave(room, "SAVED");
                    } else if (record != null && record.getStatus() == MatchRecord.Status.VOID) {
                        room.saveState = SaveState.VOID;
                        unresolvedMatches.remove(matchId);
                        broadcastSave(room, "VOIDED");
                        broadcastTerminalError(room, "MATCH_VOIDED");
                    } else if (attempt < 5) {
                        roomsExecutor.schedule(() -> settleFinal(room, attempt + 1),
                                Math.min(10, 1 << attempt), TimeUnit.SECONDS);
                    } else {
                        room.saveState = SaveState.FAILED;
                        broadcastSave(room, "SAVE_FAILED");
                        broadcastTerminalError(room, "MATCH_RESULT_UNRESOLVED");
                    }
                });
            });
        } catch (RejectedExecutionException full) {
            room.saveState = SaveState.FAILED;
            broadcastSave(room, "SAVE_FAILED");
            broadcastTerminalError(room, "MATCH_RESULT_UNRESOLVED");
        }
    }

    private void invalidate(Room room, String reason) {
        if (room.matchId == null || room.saveState == SaveState.VOID || room.saveState == SaveState.SAVED) return;
        room.saveState = SaveState.PENDING;
        broadcastSave(room, "SAVE_PENDING");
        String matchId = room.matchId;
        try { persistenceExecutor.execute(() -> settleVoid(matchId, reason, 0)); }
        catch (RejectedExecutionException full) {
            room.saveState = SaveState.FAILED;
            broadcastSave(room, "SAVE_FAILED");
            broadcastTerminalError(room, "MATCH_RESULT_UNRESOLVED");
        }
        room.battle = null;
        MatchAttempt pending = unresolvedMatches.get(matchId);
        if (pending != null) pending.battle = null;
    }

    private void settleVoid(String matchId, String reason, int attempt) {
        MatchRecord saved = null;
        try { saved = store.voidMatch(matchId, runId, reason); }
        catch (Exception uncertain) {
            try { saved = store.getMatch(matchId); } catch (Exception unknown) { }
        }
        final MatchRecord record = saved;
        try {
            roomsExecutor.execute(() -> {
                Room room = findCurrentRoom(matchId);
                if (record != null && record.getStatus() == MatchRecord.Status.VOID) {
                    unresolvedMatches.remove(matchId);
                    if (room != null) {
                        room.saveState = SaveState.VOID;
                        broadcastSave(room, "VOIDED");
                        broadcastTerminalError(room, "MATCH_VOIDED");
                    }
                } else if (record != null && record.getStatus() == MatchRecord.Status.FINALIZED) {
                    unresolvedMatches.remove(matchId);
                } else if (record == null || record.getStatus() == MatchRecord.Status.RUNNING) {
                    if (attempt < 5) roomsExecutor.schedule(() -> {
                        try { persistenceExecutor.execute(() -> settleVoid(matchId, reason, attempt + 1)); }
                        catch (RejectedExecutionException ignored) { }
                    }, Math.min(10, 1 << attempt), TimeUnit.SECONDS);
                    else if (room != null) {
                        room.saveState = SaveState.FAILED;
                        broadcastSave(room, "SAVE_FAILED");
                        broadcastTerminalError(room, "MATCH_RESULT_UNRESOLVED");
                    }
                }
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private Room findCurrentRoom(String matchId) {
        MatchAttempt attempt = unresolvedMatches.get(matchId);
        return attempt != null && matchId.equals(attempt.room.matchId) ? attempt.room : null;
    }

    private void broadcastSave(Room room, String state) {
        NetworkUpdate update = NetworkUpdate.rankedSaveStatus(room.id, room.matchId, state);
        for (Peer member : room.members.values()) member.send(update);
    }

    private void broadcastTerminalError(Room room, String reason) {
        NetworkUpdate update = NetworkUpdate.error(reason);
        for (Peer member : room.members.values()) member.send(update);
    }

    private void leaveRoom(Peer peer, long requestId) {
        if (peer.room == null) { outcome(peer, requestId, false, "NOT_IN_ROOM"); return; }
        outcome(peer, requestId, true, null);
        Room room = peer.room;
        peer.send(NetworkUpdate.roomState(new RoomState(room.id, room.version + 1,
                RoomState.Phase.CLOSED, peer.id, room.ready)));
        removeFromRoom(peer, true);
    }

    private void removeFromRoom(Peer peer, boolean explicit) {
        Room room = peer.room;
        if (room == null) return;
        peer.room = null;
        if (room.starting) {
            room.startEpoch++;
            room.starting = false;
            // beginMatch callback compensates any successful late registration.
        }
        if (room.battle != null && room.battle.getState().getStatus() == BattleState.Status.RUNNING) {
            if (explicit) {
                room.battle.forfeit(peer.id);
                broadcastSnapshot(room);
                finishIfNeeded(room);
            } else {
                String participantId = peer.id;
                int captured = room.startEpoch;
                roomsExecutor.schedule(() -> adjudicateDisconnect(room, participantId, captured),
                        5, TimeUnit.SECONDS);
            }
        }
        if (explicit || room.battle == null || room.battle.getState().getStatus() != BattleState.Status.RUNNING) {
            room.members.remove(peer.id);
            room.ready.remove(peer.id);
            room.version++;
            if (room.members.isEmpty()) rooms.remove(room.id);
            else broadcastRoomState(room);
        }
    }

    private void adjudicateDisconnect(Room room, String participantId, int epoch) {
        if (room.startEpoch != epoch || room.battle == null
                || room.battle.getState().getStatus() != BattleState.Status.RUNNING) return;
        boolean survivor = false;
        for (Peer member : room.members.values()) {
            if (!member.id.equals(participantId) && !member.disconnected.get()) survivor = true;
        }
        if (survivor && !closed.get()) {
            room.battle.forfeit(participantId);
            broadcastSnapshot(room);
            finishIfNeeded(room);
        } else invalidate(room, "CONNECTION_OUTCOME_UNKNOWN");
        for (Peer member : new ArrayList<Peer>(room.members.values())) {
            if (member.disconnected.get()) {
                room.members.remove(member.id);
                room.ready.remove(member.id);
            }
        }
        room.version++;
        if (room.members.isEmpty()) rooms.remove(room.id);
        else broadcastRoomState(room);
    }

    private void outcome(Peer peer, long requestId, boolean accepted, String reason) {
        Room room = peer.room;
        String roomId = room == null ? null : room.id;
        String matchId = room == null ? null : room.matchId;
        Long version = room == null || room.battle == null ? null
                : Long.valueOf(room.battle.getState().getVersion());
        peer.send(NetworkUpdate.requestOutcome(new RequestOutcome(requestId, accepted,
                reason, roomId, matchId, version)));
    }

    private void broadcastRoomState(Room room) {
        for (Peer member : room.members.values()) sendRoomState(member);
    }

    private void sendRoomState(Peer member) {
        Room room = member.room;
        if (room == null || member.disconnected.get()) return;
        RoomState.Phase phase = room.battle == null ? RoomState.Phase.WAITING : RoomState.Phase.IN_MATCH;
        member.send(NetworkUpdate.roomState(new RoomState(room.id, room.version, phase,
                member.id, room.ready)));
    }

    private void broadcastSnapshot(Room room) {
        if (room.battle == null || room.matchId == null) return;
        NetworkUpdate snapshot = NetworkUpdate.snapshot(room.id, room.matchId, room.battle.getState());
        for (Peer member : room.members.values()) member.send(snapshot);
    }

    private void broadcastPickups(Room room, BattleResult result) {
        if (result == null || room.matchId == null) return;
        List<kr.ac.jbnu.se.tetris.battle.BattleEvent> pickups =
                new ArrayList<kr.ac.jbnu.se.tetris.battle.BattleEvent>();
        for (kr.ac.jbnu.se.tetris.battle.BattleEvent event : result.getEvents())
            if (event.getType() == kr.ac.jbnu.se.tetris.battle.BattleEvent.Type.ITEM_ACQUIRED)
                pickups.add(event);
        if (pickups.isEmpty()) return;
        NetworkUpdate update = NetworkUpdate.events(room.id, room.matchId, pickups);
        for (Peer member : room.members.values()) member.send(update);
    }

    private final class Peer {
        private final String id;
        private final AuthIdentity identity;
        private final Channel channel;
        private final Semaphore pendingRequests = new Semaphore(MAX_PENDING_REQUESTS);
        private final AtomicInteger pendingWrites = new AtomicInteger();
        private final AtomicBoolean disconnected = new AtomicBoolean();
        private final AtomicBoolean refreshInProgress = new AtomicBoolean();
        private volatile long lastPongNanos = System.nanoTime();
        private volatile long expiresAtEpochSecond;
        private volatile long windowStartNanos = System.nanoTime();
        private volatile int windowCount;
        private volatile Room room;
        private long lastRequestId;

        private Peer(Channel channel, AuthIdentity identity) {
            this.id = identity.getUserId();
            this.channel = channel;
            this.identity = identity;
            this.expiresAtEpochSecond = identity.getExpiresAtEpochSecond();
        }

        private synchronized boolean allowRequest() {
            long now = System.nanoTime();
            if (now - windowStartNanos > TimeUnit.SECONDS.toNanos(1)) {
                windowStartNanos = now;
                windowCount = 0;
            }
            return ++windowCount <= 40;
        }

        private void send(NetworkUpdate update) {
            if (disconnected.get() || !channel.isActive() || !channel.isWritable()
                    || pendingWrites.incrementAndGet() > MAX_PENDING_WRITES) { disconnect(); return; }
            try {
                byte[] payload = WireCodec.encodeUpdatePayload(update);
                channel.writeAndFlush(new TextWebSocketFrame(new String(payload, CharsetUtil.UTF_8)))
                        .addListener(result -> {
                            pendingWrites.decrementAndGet();
                            if (!result.isSuccess()) disconnect();
                        });
            } catch (IOException failure) {
                pendingWrites.decrementAndGet();
                disconnect();
            }
        }

        private void disconnect() {
            if (!disconnected.compareAndSet(false, true)) return;
            users.remove(id, this);
            channel.close();
            if (!closed.get()) {
                try { roomsExecutor.execute(() -> removeFromRoom(this, false)); }
                catch (RejectedExecutionException ignored) { }
            }
        }

    }

    private enum SaveState { NONE, PENDING, SAVED, FAILED, VOID }

    private static final class Room {
        private final String id;
        private final Map<String, Peer> members = new LinkedHashMap<String, Peer>();
        private final Map<String, Boolean> ready = new LinkedHashMap<String, Boolean>();
        private long version;
        private int startEpoch;
        private boolean starting;
        private String matchId;
        private String finalWinner;
        private String finalReason;
        private BattleManager battle;
        private long lastTickNanos;
        private long lastBroadcastNanos;
        private SaveState saveState = SaveState.NONE;
        private Room(String id) { this.id = id; }
    }

    private static final class MatchAttempt {
        private final String matchId;
        private final Room room;
        private final String firstUserId;
        private final String secondUserId;
        private BattleManager battle;
        private String winnerId;
        private String reason;
        private MatchAttempt(String matchId, Room room, String firstUserId, String secondUserId) {
            this.matchId = matchId;
            this.room = room;
            this.firstUserId = firstUserId;
            this.secondUserId = secondUserId;
        }
    }

    private static final class CloseSettlement {
        private final String matchId;
        private final String winnerId;
        private final String reason;
        private CloseSettlement(String matchId, String winnerId, String reason) {
            this.matchId = matchId;
            this.winnerId = winnerId;
            this.reason = reason;
        }
    }

    @Override public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        admissionOpen = false;
        draining = true;
        leaseExecutor.shutdownNow();
        Channel serverChannel = listener;
        if (serverChannel != null) serverChannel.close();
        for (Channel channel : sockets.keySet()) channel.close();
        List<String> pendingIds = new ArrayList<String>();
        try {
            java.util.concurrent.Future<List<CloseSettlement>> pending = roomsExecutor.submit(() -> {
                List<CloseSettlement> settlements = new ArrayList<CloseSettlement>();
                for (MatchAttempt attempt : unresolvedMatches.values()) {
                    settlements.add(new CloseSettlement(attempt.matchId, attempt.winnerId,
                            attempt.reason == null ? "SERVER_STOPPED" : attempt.reason));
                }
                return settlements;
            });
            for (CloseSettlement settlement : pending.get(1, TimeUnit.SECONDS)) {
                pendingIds.add(settlement.matchId);
                try { persistenceExecutor.execute(() -> {
                    try {
                        if (settlement.winnerId == null) {
                            store.voidMatch(settlement.matchId, runId, "SERVER_STOPPED");
                        } else {
                            store.finishMatch(settlement.matchId, runId, settlement.winnerId, settlement.reason);
                        }
                    } catch (Exception failure) {
                        // The next lease owner reconciles persistent RUNNING rows.
                        LOG.log(Level.WARNING, "Shutdown settlement pending for match "
                                + settlement.matchId, failure);
                    }
                }); }
                catch (RejectedExecutionException full) {
                    LOG.warning("Shutdown settlement queue full for match " + settlement.matchId);
                }
            }
        } catch (Exception unavailable) {
            LOG.log(Level.WARNING, "Could not enumerate shutdown settlements; lease takeover must reconcile", unavailable);
        }
        roomsExecutor.shutdown();
        authExecutor.shutdown();
        persistenceExecutor.shutdown();
        try {
            long remaining = Math.max(0L, deadline - System.nanoTime());
            if (!persistenceExecutor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                LOG.warning("Shutdown deadline reached with pending persistence; next lease owner must reconcile"
                        + " matchIds=" + pendingIds);
                persistenceExecutor.shutdownNow();
            }
        }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (boss != null) boss.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        if (workers != null) workers.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        stopped.countDown();
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Usage: RenderGameServer");
        String configured = System.getenv("PORT");
        int port = configured == null || configured.trim().isEmpty() ? 10000 : Integer.parseInt(configured);
        String backend = System.getenv("TETRIS_BACKEND");
        if (backend == null || backend.trim().isEmpty()) backend = "supabase";
        backend = backend.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"firebase".equals(backend) && !"supabase".equals(backend)) {
            throw new IllegalArgumentException("TETRIS_BACKEND must be firebase or supabase");
        }
        int maxWebSocketConnections = environmentInt("TETRIS_MAX_WS_CONNECTIONS",
                DEFAULT_MAX_WS_CONNECTIONS);
        int maxPendingAuth = environmentInt("TETRIS_MAX_PENDING_AUTH", DEFAULT_MAX_PENDING_AUTH);
        String adminToken = System.getenv("TETRIS_ADMIN_TOKEN");
        if (adminToken == null || adminToken.isEmpty()) adminToken = System.getenv("RENDER_ADMIN_TOKEN");
        FirebaseServerContext firebase = null;
        try {
            TokenVerifier verifier;
            RankedMatchStore store;
            if ("firebase".equals(backend)) {
                firebase = FirebaseServerContext.fromEnvironment();
                verifier = new FirebaseTokenVerifier(firebase.getAuth(), firebase.getFirestore());
                store = new FirestoreRankedMatchStore(firebase.getFirestore(), firebase.getAuth());
            } else {
                SupabaseConfig config = SupabaseConfig.fromEnvironment();
                verifier = new SupabaseTokenVerifier(config);
                store = new SupabaseRankedMatchStore(config);
            }
            RenderGameServer server = new RenderGameServer(verifier, store, adminToken, port,
                    maxWebSocketConnections, maxPendingAuth);
            FirebaseServerContext context = firebase;
            AtomicBoolean cleaned = new AtomicBoolean();
            Runnable cleanup = () -> {
                server.close();
                if (context != null && cleaned.compareAndSet(false, true)) {
                    try { context.close(); }
                    catch (Exception failure) { LOG.log(Level.WARNING, "Firebase shutdown failed", failure); }
                }
            };
            Runtime.getRuntime().addShutdownHook(new Thread(cleanup, "ranked-server-shutdown"));
            try {
                server.start();
                System.out.println("Ranked PvP HTTP/WebSocket server listening on 0.0.0.0:" + server.getPort()
                        + " backend=" + backend + " runId=" + server.getRunId()
                        + " admission=" + server.isAdmissionOpen());
                server.stopped.await();
            } finally { cleanup.run(); }
        } catch (Exception startupFailure) {
            if (firebase != null) {
                try { firebase.close(); }
                catch (Exception cleanupFailure) { startupFailure.addSuppressed(cleanupFailure); }
            }
            throw startupFailure;
        }
    }

    private static int environmentInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : Integer.parseInt(value.trim());
    }
}
