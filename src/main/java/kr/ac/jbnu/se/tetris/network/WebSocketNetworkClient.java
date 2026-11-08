package kr.ac.jbnu.se.tetris.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLParameters;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.protocol.WireCodec;
import kr.ac.jbnu.se.tetris.network.protocol.WireRequest;

/** The same game protocol over authenticated WebSocket text messages. */
public final class WebSocketNetworkClient implements NetworkClient {
    private static final long COLD_START_RETRY_MILLIS = 75000;
    private final boolean retryColdStart;
    private final long retryDeadlineMillis;
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ArrayBlockingQueue<WireRequest> outgoing = new ArrayBlockingQueue<WireRequest>(256);
    private final ThreadPoolExecutor callbacks = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(256), task -> daemon(task, "tetris-ws-callback"));
    private final List<Observer> observers = new ArrayList<Observer>();
    private NetworkUpdate connectedUpdate;
    private NetworkUpdate roomUpdate;
    private NetworkUpdate startUpdate;
    private NetworkUpdate snapshotUpdate;
    private volatile NioEventLoopGroup ioGroup;
    private volatile Channel channel;
    private volatile String matchId;
    private volatile boolean connected;
    private volatile Attempt openingAttempt;

    public WebSocketNetworkClient() { this(false); }

    /** Retry only the first connection; room commands and gameplay are never replayed. */
    public WebSocketNetworkClient(boolean retryColdStart) {
        this(retryColdStart, COLD_START_RETRY_MILLIS);
    }

    WebSocketNetworkClient(boolean retryColdStart, long retryDeadlineMillis) {
        if (retryDeadlineMillis < 1) throw new IllegalArgumentException("retryDeadlineMillis");
        this.retryColdStart = retryColdStart;
        this.retryDeadlineMillis = retryDeadlineMillis;
    }

    @Override public long connect(ConnectionOptions options) {
        requireOpen();
        if (options == null) throw new IllegalArgumentException("WebSocket URI required");
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("ALREADY_CONNECTING");
        long id = nextId.incrementAndGet();
        daemon(() -> open(options), "tetris-ws-connect").start();
        return id;
    }

    private void open(ConnectionOptions options) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(retryDeadlineMillis);
        int attemptNumber = 0;
        while (!closed.get()) {
            Attempt result = openAttempt(options, deadline);
            if (closed.get() || result.success) return;
            if (!retryColdStart || !result.retryable || System.nanoTime() >= deadline) {
                terminate(result.reason, true);
                return;
            }
            attemptNumber++;
            long delay = Math.min(5000L, 500L << Math.min(attemptNumber, 3));
            delay = Math.min(delay, Math.max(0L,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            if (delay <= 0) { terminate(result.reason, true); return; }
            try { Thread.sleep(delay); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                terminate("CONNECTION_FAILED", true);
                return;
            }
        }
    }

    private Attempt openAttempt(ConnectionOptions options, long deadline) {
        Attempt attempt = new Attempt();
        openingAttempt = attempt;
        NioEventLoopGroup group = null;
        Channel opened = null;
        try {
            URI uri = options.getWebSocketUri();
            final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri,
                    WebSocketVersion.V13, null, false,
                    new DefaultHttpHeaders().set("Authorization", "Bearer " + options.getAccessToken()),
                    WireCodec.MAX_FRAME_BYTES);
            final SslContext ssl = "wss".equalsIgnoreCase(uri.getScheme())
                    ? SslContextBuilder.forClient().build() : null;
            group = new NioEventLoopGroup(1,
                    (java.util.concurrent.ThreadFactory) task -> daemon(task, "tetris-ws-io"));
            ioGroup = group;
            Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel ch) {
                            if (ssl != null) {
                                SslHandler handler = ssl.newHandler(ch.alloc(), options.getHost(), options.getPort());
                                SSLParameters parameters = handler.engine().getSSLParameters();
                                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                                handler.engine().setSSLParameters(parameters);
                                ch.pipeline().addLast(handler);
                            }
                            ch.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(8192),
                                    new IdleStateHandler(60, 0, 0),
                                    new WebSocketFrameAggregator(WireCodec.MAX_FRAME_BYTES),
                                    new SimpleChannelInboundHandler<Object>() {
                                @Override public void channelActive(ChannelHandlerContext ctx) {
                                    handshaker.handshake(ctx.channel());
                                }
                                @Override protected void channelRead0(ChannelHandlerContext ctx, Object value)
                                        throws Exception {
                                    if (!handshaker.isHandshakeComplete()) {
                                        if (!(value instanceof FullHttpResponse)) throw new IOException("Invalid handshake");
                                        FullHttpResponse response = (FullHttpResponse) value;
                                        if (!HttpResponseStatus.SWITCHING_PROTOCOLS.equals(response.status())) {
                                            String reason = response.status().code() == 401 ? "AUTH_INVALID"
                                                    : response.status().code() == 503 ? "SERVICE_UNAVAILABLE"
                                                    : "HANDSHAKE_FAILED";
                                            attempt.finish(reason, response.status().code() == 503, false);
                                            ctx.close();
                                            return;
                                        }
                                        handshaker.finishHandshake(ctx.channel(), response);
                                        if (closed.get() || openingAttempt != attempt
                                                || !attempt.finish(null, false, true)) {
                                            ctx.close();
                                            return;
                                        }
                                        connected = true;
                                        drain();
                                        return;
                                    }
                                    if (value instanceof TextWebSocketFrame) {
                                        byte[] bytes = ByteBufUtil.getBytes(((TextWebSocketFrame) value).content());
                                        publish(WireCodec.decodeUpdatePayload(bytes));
                                    } else if (value instanceof PingWebSocketFrame) {
                                        ctx.writeAndFlush(new PongWebSocketFrame(((PingWebSocketFrame) value).content().retain()));
                                    } else if (value instanceof CloseWebSocketFrame) {
                                        terminate("CONNECTION_LOST", false);
                                    } else if (!(value instanceof PongWebSocketFrame)) {
                                        throw new IOException("Unsupported WebSocket frame");
                                    }
                                }
                                @Override public void channelInactive(ChannelHandlerContext ctx) {
                                    if (!attempt.success) attempt.finish("CONNECTION_FAILED", true, false);
                                    else terminate("CONNECTION_LOST", false);
                                }
                                @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable error) {
                                    if (!attempt.success) {
                                        attempt.finish("CONNECTION_FAILED", true, false);
                                        ctx.close();
                                    } else terminate("CONNECTION_LOST", false);
                                }
                                @Override public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
                                    if (event instanceof IdleStateEvent
                                            && ((IdleStateEvent) event).state() == IdleState.READER_IDLE) {
                                        if (!attempt.success) {
                                            attempt.finish("HANDSHAKE_TIMEOUT", true, false);
                                            ctx.close();
                                        } else terminate("CONNECTION_IDLE_TIMEOUT", false);
                                    }
                                }
                            });
                        }
                    });
            io.netty.channel.ChannelFuture future = bootstrap.connect(options.getHost(), options.getPort());
            if (!future.await(6, TimeUnit.SECONDS) || !future.isSuccess()) {
                future.channel().close();
                attempt.finish("CONNECTION_FAILED", true, false);
                return attempt;
            }
            opened = future.channel();
            channel = opened;
            if (closed.get()) attempt.finish("CLIENT_CLOSED", false, false);
            long handshakeWaitMillis = retryColdStart
                    ? Math.min(20000L, Math.max(1L,
                            TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())))
                    : TimeUnit.SECONDS.toMillis(90);
            if (!attempt.done.await(handshakeWaitMillis, TimeUnit.MILLISECONDS)) {
                attempt.finish("HANDSHAKE_TIMEOUT", true, false);
            }
            if (attempt.success) drain();
        } catch (Exception error) {
            attempt.finish("CONNECTION_FAILED", true, false);
        } finally {
            if (!attempt.success) {
                if (opened != null) opened.close();
                if (group != null) group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
                if (channel == opened) channel = null;
                if (ioGroup == group) ioGroup = null;
            }
            if (openingAttempt == attempt) openingAttempt = null;
        }
        return attempt;
    }

    @Override public synchronized long send(RoomCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("Room command required");
        long id = nextId.incrementAndGet();
        enqueue(WireRequest.room(id, command));
        return id;
    }

    @Override public synchronized long send(PlayerIntent intent) {
        requireOpen();
        if (intent == null) throw new IllegalArgumentException("Player intent required");
        String current = matchId;
        if (current == null) throw new IllegalStateException("MATCH_NOT_STARTED");
        long id = nextId.incrementAndGet();
        enqueue(WireRequest.intent(id, current, intent));
        return id;
    }

    @Override public synchronized long requestSnapshot() {
        requireOpen();
        String current = matchId;
        if (current == null) throw new IllegalStateException("MATCH_NOT_STARTED");
        long id = nextId.incrementAndGet();
        enqueue(WireRequest.snapshot(id, current));
        return id;
    }

    @Override public synchronized long refreshAuthentication(String accessToken) {
        requireOpen();
        long id = nextId.incrementAndGet();
        enqueue(WireRequest.authRefresh(id, accessToken));
        return id;
    }

    private void enqueue(WireRequest request) {
        if (!connected) throw new IllegalStateException("NOT_CONNECTED");
        if (!outgoing.offer(request)) throw new IllegalStateException("SEND_QUEUE_FULL");
        drain();
    }

    private void drain() {
        Channel current = channel;
        if (current == null || !connected || !current.isActive()) return;
        current.eventLoop().execute(() -> {
            WireRequest request;
            while ((request = outgoing.poll()) != null) {
                try {
                    current.write(new TextWebSocketFrame(new String(WireCodec.encodeRequestPayload(request),
                            CharsetUtil.UTF_8)));
                } catch (IOException error) { terminate("SEND_FAILED", false); return; }
            }
            current.flush();
        });
    }

    @Override public NetworkSubscription subscribe(NetworkListener listener) {
        requireOpen();
        if (listener == null) throw new IllegalArgumentException("Listener required");
        Observer observer = new Observer(listener);
        dispatch(() -> {
            if (!observer.active.get()) return;
            observers.add(observer);
            if (connectedUpdate != null) observer.deliver(connectedUpdate);
            if (roomUpdate != null) observer.deliver(roomUpdate);
            if (startUpdate != null) observer.deliver(startUpdate);
            if (snapshotUpdate != null) observer.deliver(snapshotUpdate);
        });
        return () -> {
            observer.active.set(false);
            if (!closed.get()) dispatch(() -> observers.remove(observer));
        };
    }

    private void publish(NetworkUpdate update) {
        dispatch(() -> {
            switch (update.getType()) {
                case CONNECTED: connectedUpdate = update; break;
                case ROOM_STATE:
                    roomUpdate = update;
                    if (update.getRoomState().getPhase() == RoomState.Phase.CLOSED
                            || (update.getRoomState().getPhase() == RoomState.Phase.WAITING
                                && startUpdate != null && update.getRoomVersion() > startUpdate.getRoomVersion())) {
                        matchId = null; startUpdate = null; snapshotUpdate = null;
                    }
                    break;
                case MATCH_STARTED:
                    matchId = update.getMatchId(); startUpdate = update; snapshotUpdate = null; break;
                case SNAPSHOT: snapshotUpdate = update; break;
                default: break;
            }
            for (Observer observer : new ArrayList<Observer>(observers)) observer.deliver(update);
        });
    }

    private void dispatch(Runnable action) {
        if (closed.get()) return;
        try { callbacks.execute(action); }
        catch (RejectedExecutionException overflow) { terminate("RECEIVE_QUEUE_FULL", false); }
    }

    private void terminate(String reason, boolean connectionFailure) {
        if (!closed.compareAndSet(false, true)) return;
        Attempt pending = openingAttempt;
        if (pending != null) pending.finish(reason, false, false);
        connected = false;
        Channel current = channel;
        if (current != null) current.close();
        NioEventLoopGroup group = ioGroup;
        if (group != null) group.shutdownGracefully();
        outgoing.clear();
        Runnable terminal = () -> {
            NetworkUpdate update = connectionFailure ? NetworkUpdate.connectionFailed(reason)
                    : NetworkUpdate.closed(reason);
            for (Observer observer : new ArrayList<Observer>(observers)) observer.deliver(update);
            observers.clear();
        };
        try { callbacks.execute(terminal); }
        catch (RejectedExecutionException overflow) {
            callbacks.getQueue().poll();
            try { callbacks.execute(terminal); } catch (RejectedExecutionException ignored) { }
        }
        callbacks.shutdown();
    }

    private void requireOpen() { if (closed.get()) throw new IllegalStateException("CLOSED"); }
    @Override public void close() { terminate("CLIENT_CLOSED", false); }
    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
    }
    private static final class Observer {
        private final NetworkListener listener;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private Observer(NetworkListener listener) { this.listener = listener; }
        private void deliver(NetworkUpdate update) {
            if (!active.get()) return;
            try { listener.onUpdate(update); }
            catch (RuntimeException error) {
                java.util.logging.Logger.getLogger(WebSocketNetworkClient.class.getName())
                        .log(java.util.logging.Level.WARNING, "Network listener failed", error);
            }
        }
    }

    private static final class Attempt {
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicBoolean decided = new AtomicBoolean();
        private volatile String reason = "CONNECTION_FAILED";
        private volatile boolean retryable;
        private volatile boolean success;
        private boolean finish(String reason, boolean retryable, boolean success) {
            if (!decided.compareAndSet(false, true)) return false;
            this.reason = reason;
            this.retryable = retryable;
            this.success = success;
            done.countDown();
            return true;
        }
    }
}
