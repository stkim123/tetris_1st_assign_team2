package kr.ac.jbnu.se.tetris.network.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RequestOutcome;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.RoomState;

/** 4바이트 길이와 UTF-8 JSON 버전 1의 엄격한 통신 경계 */
public final class WireCodec {
    public static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final int VERSION = 1;

    private WireCodec() { }

    /** WebSocket text message payload; TCP's four-byte length belongs only to TCP framing. */
    public static byte[] encodeRequestPayload(WireRequest request) throws IOException {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeRequest(framed, request);
        byte[] bytes = framed.toByteArray();
        return java.util.Arrays.copyOfRange(bytes, 4, bytes.length);
    }

    public static WireRequest decodeRequestPayload(byte[] payload) throws IOException {
        return readRequest(new ByteArrayInputStream(framePayload(payload)));
    }

    public static byte[] encodeUpdatePayload(NetworkUpdate update) throws IOException {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeUpdate(framed, update);
        byte[] bytes = framed.toByteArray();
        return java.util.Arrays.copyOfRange(bytes, 4, bytes.length);
    }

    public static NetworkUpdate decodeUpdatePayload(byte[] payload) throws IOException {
        return readUpdate(new ByteArrayInputStream(framePayload(payload)));
    }

    private static byte[] framePayload(byte[] payload) throws IOException {
        if (payload == null || payload.length < 1 || payload.length > MAX_FRAME_BYTES) {
            throw new IOException("Invalid WebSocket message size");
        }
        byte[] framed = new byte[payload.length + 4];
        int length = payload.length;
        framed[0] = (byte) (length >>> 24);
        framed[1] = (byte) (length >>> 16);
        framed[2] = (byte) (length >>> 8);
        framed[3] = (byte) length;
        System.arraycopy(payload, 0, framed, 4, length);
        return framed;
    }

    public static void writeRequest(OutputStream output, WireRequest request) throws IOException {
        if (request == null) throw new IOException("Wire request is required");
        Map<String, Object> value = base("request");
        value.put("requestId", request.getRequestId());
        value.put("kind", request.getKind().name());
        switch (request.getKind()) {
            case ROOM:
                value.put("room", encodeRoomCommand(request.getRoomCommand()));
                break;
            case INTENT:
                value.put("matchId", id(request.getMatchId()));
                value.put("intent", encodeIntent(request.getIntent()));
                break;
            case SNAPSHOT:
                value.put("matchId", id(request.getMatchId()));
                break;
            case AUTH_REFRESH:
                value.put("accessToken", text(request.getAccessToken(), 8192));
                break;
            default:
                throw new IOException("Unknown request kind");
        }
        writeFrame(output, value);
    }

    public static WireRequest readRequest(InputStream input) throws IOException {
        Object raw = readFrame(input);
        if (raw == null) return null;
        try {
            Map<String, Object> value = object(raw);
            envelope(value, "request");
            WireRequest.Kind kind = enumeration(WireRequest.Kind.class, value.get("kind"));
            long requestId = positive(value.get("requestId"));
            switch (kind) {
                case ROOM:
                    keys(value, new String[] { "version", "message", "requestId", "kind", "room" },
                            new String[0]);
                    return WireRequest.room(requestId, decodeRoomCommand(value.get("room")));
                case INTENT:
                    keys(value, new String[] { "version", "message", "requestId", "kind", "matchId", "intent" },
                            new String[0]);
                    return WireRequest.intent(requestId, id(value.get("matchId")), decodeIntent(value.get("intent")));
                case SNAPSHOT:
                    keys(value, new String[] { "version", "message", "requestId", "kind", "matchId" },
                            new String[0]);
                    return WireRequest.snapshot(requestId, id(value.get("matchId")));
                case AUTH_REFRESH:
                    keys(value, new String[] { "version", "message", "requestId", "kind", "accessToken" },
                            new String[0]);
                    return WireRequest.authRefresh(requestId, text(value.get("accessToken"), 8192));
                default:
                    throw new IOException("Unknown request kind");
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid wire request", error);
        }
    }

    public static void writeUpdate(OutputStream output, NetworkUpdate update) throws IOException {
        if (update == null) throw new IOException("Network update is required");
        Map<String, Object> value = base("update");
        value.put("type", update.getType().name());
        switch (update.getType()) {
            case CONNECTED:
                break;
            case CONNECTION_FAILED:
            case ERROR:
            case CLOSED:
                value.put("reasonCode", text(update.getReasonCode(), 256));
                break;
            case ROOM_STATE:
                value.put("room", encodeRoomState(update.getRoomState()));
                break;
            case MATCH_STARTED:
                value.put("roomId", id(update.getRoomId()));
                value.put("roomVersion", update.getRoomVersion());
                value.put("matchId", id(update.getMatchId()));
                value.put("localParticipantId", id(update.getLocalParticipantId()));
                break;
            case SNAPSHOT:
                value.put("roomId", id(update.getRoomId()));
                value.put("matchId", id(update.getMatchId()));
                value.put("battle", WireSnapshots.encode(update.getBattleState()));
                break;
            case REQUEST_OUTCOME:
                value.put("outcome", encodeOutcome(update.getRequestOutcome()));
                break;
            case RANKED_SAVE_STATUS:
                value.put("roomId", id(update.getRoomId()));
                value.put("matchId", id(update.getMatchId()));
                value.put("reasonCode", text(update.getReasonCode(), 256));
                break;
            case EVENTS:
                value.put("roomId", id(update.getRoomId()));
                value.put("matchId", id(update.getMatchId()));
                value.put("events", WireItemEvents.encode(update.getEvents()));
                break;
            default:
                throw new IOException("Unknown network update type");
        }
        writeFrame(output, value);
    }

    public static NetworkUpdate readUpdate(InputStream input) throws IOException {
        Object raw = readFrame(input);
        if (raw == null) return null;
        try {
            Map<String, Object> value = object(raw);
            envelope(value, "update");
            NetworkUpdate.Type type = enumeration(NetworkUpdate.Type.class, value.get("type"));
            switch (type) {
                case CONNECTED:
                    keys(value, new String[] { "version", "message", "type" }, new String[0]);
                    return NetworkUpdate.connected();
                case CONNECTION_FAILED:
                case ERROR:
                case CLOSED:
                    keys(value, new String[] { "version", "message", "type", "reasonCode" }, new String[0]);
                    String reason = text(value.get("reasonCode"), 256);
                    return type == NetworkUpdate.Type.CONNECTION_FAILED ? NetworkUpdate.connectionFailed(reason)
                            : type == NetworkUpdate.Type.ERROR ? NetworkUpdate.error(reason)
                            : NetworkUpdate.closed(reason);
                case ROOM_STATE:
                    keys(value, new String[] { "version", "message", "type", "room" }, new String[0]);
                    return NetworkUpdate.roomState(decodeRoomState(value.get("room")));
                case MATCH_STARTED:
                    keys(value, new String[] { "version", "message", "type", "roomId", "roomVersion",
                            "matchId", "localParticipantId" }, new String[0]);
                    return NetworkUpdate.matchStarted(id(value.get("roomId")), nonnegative(value.get("roomVersion")),
                            id(value.get("matchId")), id(value.get("localParticipantId")));
                case SNAPSHOT:
                    keys(value, new String[] { "version", "message", "type", "roomId", "matchId", "battle" },
                            new String[0]);
                    return NetworkUpdate.snapshot(id(value.get("roomId")), id(value.get("matchId")),
                            WireSnapshots.decode(value.get("battle")));
                case REQUEST_OUTCOME:
                    keys(value, new String[] { "version", "message", "type", "outcome" }, new String[0]);
                    return NetworkUpdate.requestOutcome(decodeOutcome(value.get("outcome")));
                case RANKED_SAVE_STATUS:
                    keys(value, new String[] { "version", "message", "type", "roomId", "matchId", "reasonCode" },
                            new String[0]);
                    return NetworkUpdate.rankedSaveStatus(id(value.get("roomId")), id(value.get("matchId")),
                            text(value.get("reasonCode"), 256));
                case EVENTS:
                    keys(value, new String[] { "version", "message", "type", "roomId", "matchId", "events" },
                            new String[0]);
                    return NetworkUpdate.events(id(value.get("roomId")), id(value.get("matchId")),
                            WireItemEvents.decode(value.get("events")));
                default:
                    throw new IOException("Unknown network update type");
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid wire update", error);
        }
    }

    private static Map<String, Object> base(String message) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("version", VERSION);
        value.put("message", message);
        return value;
    }

    private static void envelope(Map<String, Object> value, String message) throws IOException {
        if (integer(value.get("version")) != VERSION || !message.equals(value.get("message"))) {
            throw new IOException("Unsupported wire envelope or protocol version");
        }
    }

    private static Map<String, Object> encodeRoomCommand(RoomCommand command) throws IOException {
        if (command == null) throw new IOException("Room command is required");
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("type", command.getType().name());
        switch (command.getType()) {
            case CREATE_ROOM:
                value.put("maxParticipants", command.getMaxParticipants());
                break;
            case JOIN_ROOM:
                value.put("roomId", id(command.getRoomId()));
                break;
            case SET_READY:
                value.put("ready", command.getReady());
                break;
            case LEAVE_ROOM:
            case REQUEST_REMATCH:
            case GET_ROOM_STATE:
                break;
            default:
                throw new IOException("Unknown room command");
        }
        return value;
    }

    private static RoomCommand decodeRoomCommand(Object raw) throws IOException {
        Map<String, Object> value = object(raw);
        RoomCommand.Type type = enumeration(RoomCommand.Type.class, value.get("type"));
        switch (type) {
            case CREATE_ROOM:
                keys(value, new String[] { "type", "maxParticipants" }, new String[0]);
                return RoomCommand.createRoom(integer(value.get("maxParticipants")));
            case JOIN_ROOM:
                keys(value, new String[] { "type", "roomId" }, new String[0]);
                return RoomCommand.joinRoom(id(value.get("roomId")));
            case SET_READY:
                keys(value, new String[] { "type", "ready" }, new String[0]);
                return RoomCommand.setReady(bool(value.get("ready")));
            case LEAVE_ROOM:
            case REQUEST_REMATCH:
            case GET_ROOM_STATE:
                keys(value, new String[] { "type" }, new String[0]);
                return type == RoomCommand.Type.LEAVE_ROOM ? RoomCommand.leaveRoom()
                        : type == RoomCommand.Type.REQUEST_REMATCH ? RoomCommand.requestRematch()
                        : RoomCommand.getRoomState();
            default:
                throw new IOException("Unknown room command");
        }
    }

    private static Map<String, Object> encodeIntent(PlayerIntent intent) throws IOException {
        if (intent == null) throw new IOException("Player intent is required");
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("type", intent.getType().name());
        if (intent.getItemUse() != null) {
            GameAction.ItemUse item = intent.getItemUse();
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("itemId", id(item.getItemId()));
            payload.put("targetActorId", id(item.getTargetActorId()));
            if (item.getTargetCell() != null) {
                Map<String, Object> cell = new LinkedHashMap<String, Object>();
                cell.put("x", item.getTargetCell().getX());
                cell.put("y", item.getTargetCell().getY());
                payload.put("targetCell", cell);
            }
            value.put("itemUse", payload);
        }
        return value;
    }

    private static PlayerIntent decodeIntent(Object raw) throws IOException {
        Map<String, Object> value = object(raw);
        GameAction.Type type = enumeration(GameAction.Type.class, value.get("type"));
        if (type != GameAction.Type.USE_ITEM) {
            keys(value, new String[] { "type" }, new String[0]);
            return new PlayerIntent(type);
        }
        keys(value, new String[] { "type", "itemUse" }, new String[0]);
        Map<String, Object> payload = object(value.get("itemUse"));
        keys(payload, new String[] { "itemId", "targetActorId" }, new String[] { "targetCell" });
        GameAction.TargetCell cell = null;
        if (payload.containsKey("targetCell")) {
            Map<String, Object> target = object(payload.get("targetCell"));
            keys(target, new String[] { "x", "y" }, new String[0]);
            cell = new GameAction.TargetCell(integer(target.get("x")), integer(target.get("y")));
        }
        return new PlayerIntent(type, new GameAction.ItemUse(id(payload.get("itemId")),
                id(payload.get("targetActorId")), cell));
    }

    private static Map<String, Object> encodeRoomState(RoomState state) throws IOException {
        if (state == null) throw new IOException("Room state is required");
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("roomId", id(state.getRoomId()));
        value.put("roomVersion", state.getRoomVersion());
        value.put("phase", state.getPhase().name());
        value.put("localParticipantId", id(state.getLocalParticipantId()));
        Map<String, Object> ready = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Boolean> entry : state.getReadyByParticipantId().entrySet()) {
            ready.put(id(entry.getKey()), entry.getValue());
        }
        value.put("ready", ready);
        return value;
    }

    private static RoomState decodeRoomState(Object raw) throws IOException {
        Map<String, Object> value = object(raw);
        keys(value, new String[] { "roomId", "roomVersion", "phase", "localParticipantId", "ready" },
                new String[0]);
        Map<String, Object> rawReady = object(value.get("ready"));
        Map<String, Boolean> ready = new LinkedHashMap<String, Boolean>();
        for (Map.Entry<String, Object> entry : rawReady.entrySet()) {
            ready.put(id(entry.getKey()), bool(entry.getValue()));
        }
        return new RoomState(id(value.get("roomId")), nonnegative(value.get("roomVersion")),
                enumeration(RoomState.Phase.class, value.get("phase")),
                id(value.get("localParticipantId")), ready);
    }

    private static Map<String, Object> encodeOutcome(RequestOutcome outcome) throws IOException {
        if (outcome == null) throw new IOException("Request outcome is required");
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("requestId", outcome.getRequestId());
        value.put("accepted", outcome.isAccepted());
        if (outcome.getReasonCode() != null) value.put("reasonCode", text(outcome.getReasonCode(), 256));
        if (outcome.getRoomId() != null) value.put("roomId", id(outcome.getRoomId()));
        if (outcome.getMatchId() != null) value.put("matchId", id(outcome.getMatchId()));
        if (outcome.getBattleVersion() != null) value.put("battleVersion", outcome.getBattleVersion());
        return value;
    }

    private static RequestOutcome decodeOutcome(Object raw) throws IOException {
        Map<String, Object> value = object(raw);
        keys(value, new String[] { "requestId", "accepted" },
                new String[] { "reasonCode", "roomId", "matchId", "battleVersion" });
        Long battleVersion = value.containsKey("battleVersion")
                ? Long.valueOf(nonnegative(value.get("battleVersion"))) : null;
        return new RequestOutcome(positive(value.get("requestId")), bool(value.get("accepted")),
                optionalText(value, "reasonCode", 256), optionalId(value, "roomId"),
                optionalId(value, "matchId"), battleVersion);
    }

    private static void writeFrame(OutputStream output, Object value) throws IOException {
        if (output == null) throw new IOException("Output stream is required");
        byte[] payload = StrictJson.stringify(value);
        int length = payload.length;
        if (length < 1 || length > MAX_FRAME_BYTES) throw new IOException("Wire frame length exceeds limit");
        output.write((length >>> 24) & 0xff);
        output.write((length >>> 16) & 0xff);
        output.write((length >>> 8) & 0xff);
        output.write(length & 0xff);
        output.write(payload);
        output.flush();
    }

    private static Object readFrame(InputStream input) throws IOException {
        if (input == null) throw new IOException("Input stream is required");
        int first = input.read();
        if (first == -1) return null;
        int second = input.read();
        int third = input.read();
        int fourth = input.read();
        if (second == -1 || third == -1 || fourth == -1) throw new EOFException("Truncated wire header");
        long length = ((long) first << 24) | ((long) second << 16) | ((long) third << 8) | fourth;
        if (length < 1 || length > MAX_FRAME_BYTES) throw new IOException("Invalid wire frame length");
        byte[] payload = new byte[(int) length];
        int offset = 0;
        while (offset < payload.length) {
            int count = input.read(payload, offset, payload.length - offset);
            if (count == -1) throw new EOFException("Truncated wire payload");
            if (count == 0) {
                int next = input.read();
                if (next == -1) throw new EOFException("Truncated wire payload");
                payload[offset++] = (byte) next;
            } else offset += count;
        }
        return StrictJson.parse(payload);
    }

    static Map<String, Object> object(Object raw) throws IOException {
        if (!(raw instanceof Map)) throw new IOException("JSON object is required");
        @SuppressWarnings("unchecked") Map<String, Object> value = (Map<String, Object>) raw;
        return value;
    }

    static List<Object> array(Object raw) throws IOException {
        if (!(raw instanceof List)) throw new IOException("JSON array is required");
        @SuppressWarnings("unchecked") List<Object> value = (List<Object>) raw;
        return value;
    }

    static void keys(Map<String, Object> value, String[] required, String[] optional) throws IOException {
        for (String key : required) if (!value.containsKey(key)) throw new IOException("Missing field: " + key);
        for (String key : value.keySet()) {
            boolean known = false;
            for (String expected : required) if (key.equals(expected)) known = true;
            for (String expected : optional) if (key.equals(expected)) known = true;
            if (!known) throw new IOException("Unknown wire field: " + key);
        }
    }

    static String text(Object raw, int maxChars) throws IOException {
        if (!(raw instanceof String)) throw new IOException("JSON text is required");
        String value = (String) raw;
        if (value.trim().isEmpty() || value.length() > maxChars) throw new IOException("Invalid wire text length");
        return value;
    }

    static String id(Object raw) throws IOException {
        String value = text(raw, 128);
        if (!value.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IOException("Invalid wire identifier");
        return value;
    }

    static String optionalText(Map<String, Object> value, String key, int maxChars) throws IOException {
        return !value.containsKey(key) || value.get(key) == null ? null : text(value.get(key), maxChars);
    }

    static String optionalId(Map<String, Object> value, String key) throws IOException {
        return !value.containsKey(key) || value.get(key) == null ? null : id(value.get(key));
    }

    static boolean bool(Object raw) throws IOException {
        if (!(raw instanceof Boolean)) throw new IOException("JSON boolean is required");
        return ((Boolean) raw).booleanValue();
    }

    static long nonnegative(Object raw) throws IOException {
        if (!(raw instanceof Long) || ((Long) raw).longValue() < 0) {
            throw new IOException("Nonnegative integer is required");
        }
        return ((Long) raw).longValue();
    }

    static long positive(Object raw) throws IOException {
        long value = nonnegative(raw);
        if (value == 0) throw new IOException("Positive integer is required");
        return value;
    }

    static int integer(Object raw) throws IOException {
        if (!(raw instanceof Long)) throw new IOException("JSON integer is required");
        long value = ((Long) raw).longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException("Integer outside supported range");
        }
        return (int) value;
    }

    static <E extends Enum<E>> E enumeration(Class<E> enumClass, Object raw) throws IOException {
        String name = text(raw, 64);
        try { return Enum.valueOf(enumClass, name); }
        catch (IllegalArgumentException error) { throw new IOException("Unknown wire enum value: " + name, error); }
    }
}
