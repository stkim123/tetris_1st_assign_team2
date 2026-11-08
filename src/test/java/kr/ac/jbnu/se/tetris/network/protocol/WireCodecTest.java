package kr.ac.jbnu.se.tetris.network.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RequestOutcome;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.RoomState;
import kr.ac.jbnu.se.tetris.support.SampleSnapshots;

/** 분할·연속 프레임과 엄격한 요청 및 상태 사본 왕복 검증 */
public final class WireCodecTest {
    public static void main(String[] args) throws Exception {
        requestsAndFrames();
        updatesAndSnapshots();
        malformedFramesAndFields();
    }

    private static void requestsAndFrames() throws Exception {
        WireRequest[] requests = {
                WireRequest.room(1, RoomCommand.createRoom(4)),
                WireRequest.room(2, RoomCommand.joinRoom("room-2")),
                WireRequest.room(3, RoomCommand.setReady(true)),
                WireRequest.room(4, RoomCommand.leaveRoom()),
                WireRequest.room(5, RoomCommand.requestRematch()),
                WireRequest.room(6, RoomCommand.getRoomState()),
                WireRequest.intent(7, "match-1", new PlayerIntent(GameAction.Type.HARD_DROP)),
                WireRequest.intent(8, "match-1", new PlayerIntent(GameAction.Type.USE_ITEM,
                        new GameAction.ItemUse("hp-item", "student-b", new GameAction.TargetCell(4, 6)))),
                WireRequest.snapshot(9, "match-1")
        };
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (WireRequest request : requests) WireCodec.writeRequest(frames, request);
        byte[] bytes = frames.toByteArray();
        int firstLength = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        check(firstLength > 0 && firstLength < WireCodec.MAX_FRAME_BYTES
                && bytes[4] == '{', "4바이트 big-endian 길이와 JSON 본문");
        InputStream split = new FragmentedInputStream(bytes);
        for (WireRequest expected : requests) {
            WireRequest actual = WireCodec.readRequest(split);
            check(actual.getRequestId() == expected.getRequestId()
                    && actual.getKind() == expected.getKind(), "연속 및 분할 프레임 요청 왕복");
            if (actual.getKind() == WireRequest.Kind.ROOM) {
                check(actual.getRoomCommand().getType() == expected.getRoomCommand().getType(),
                        "방 명령 종류 보존");
            } else check(actual.getMatchId().equals("match-1"), "경기 ID 보존");
            if (actual.getRequestId() == 1) check(actual.getRoomCommand().getMaxParticipants() == 4,
                    "방 최대 인원 보존");
            if (actual.getRequestId() == 3) check(actual.getRoomCommand().getReady(),
                    "준비 상태 보존");
            if (actual.getRequestId() == 8) {
                GameAction.ItemUse item = actual.getIntent().getItemUse();
                check(item.getItemId().equals("hp-item") && item.getTargetActorId().equals("student-b")
                        && item.getTargetCell().getX() == 4 && item.getTargetCell().getY() == 6,
                        "Item payload의 대상과 좌표 보존");
            }
        }
        check(WireCodec.readRequest(split) == null, "경계에서의 정상 EOF");
    }

    private static void updatesAndSnapshots() throws Exception {
        Map<String, Boolean> ready = new LinkedHashMap<String, Boolean>();
        ready.put("student-a", true);
        ready.put("student-b", false);
        RoomState room = new RoomState("room-2", 3, RoomState.Phase.WAITING, "student-a", ready);
        BattleState battle = SampleSnapshots.running("student-a", "student-b");
        NetworkUpdate[] updates = {
                NetworkUpdate.connected(),
                NetworkUpdate.connectionFailed("CONNECT_FAILED"),
                NetworkUpdate.roomState(room),
                NetworkUpdate.matchStarted("room-2", 4, "match-1", "student-a"),
                NetworkUpdate.snapshot("room-2", "match-1", battle),
                NetworkUpdate.requestOutcome(new RequestOutcome(21, true, null,
                        "room-2", "match-1", 0L)),
                NetworkUpdate.requestOutcome(new RequestOutcome(22, false, "INVALID_ACTION",
                        "room-2", "match-1", null)),
                NetworkUpdate.error("PROTOCOL_ERROR"),
                NetworkUpdate.closed("REMOTE_CLOSED")
        };
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (NetworkUpdate update : updates) WireCodec.writeUpdate(frames, update);
        InputStream source = new FragmentedInputStream(frames.toByteArray());
        for (NetworkUpdate expected : updates) {
            NetworkUpdate actual = WireCodec.readUpdate(source);
            check(actual.getType() == expected.getType(), "연속 및 분할 프레임 갱신 왕복");
            if (actual.getType() == NetworkUpdate.Type.ROOM_STATE) {
                check(actual.getRoomState().getRoomVersion() == 3
                        && !actual.getRoomState().getReadyByParticipantId().get("student-b"),
                        "방 버전과 참가자별 준비 상태 보존");
            }
            if (actual.getType() == NetworkUpdate.Type.SNAPSHOT) {
                compareBattle(battle, actual.getBattleState());
            }
            if (actual.getType() == NetworkUpdate.Type.REQUEST_OUTCOME) {
                check(actual.getRequestOutcome().getRequestId()
                        == expected.getRequestOutcome().getRequestId()
                        && actual.getRequestOutcome().isAccepted()
                        == expected.getRequestOutcome().isAccepted(), "요청 결과 보존");
            }
        }
        check(WireCodec.readUpdate(source) == null, "업데이트 프레임 정상 EOF");
        ByteArrayOutputStream finished = new ByteArrayOutputStream();
        BattleState result = SampleSnapshots.finished("student-a", "student-b", "student-a");
        WireCodec.writeUpdate(finished, NetworkUpdate.snapshot("room-2", "match-1", result));
        compareBattle(result, WireCodec.readUpdate(new ByteArrayInputStream(finished.toByteArray()))
                .getBattleState());
        BattleEvent pickup = BattleEvent.fromWire(BattleEvent.Type.ITEM_ACQUIRED, 41,
                "student-a", "student-a", 1, "heal", null, 4, 8, 2);
        ByteArrayOutputStream mined = new ByteArrayOutputStream();
        WireCodec.writeUpdate(mined, NetworkUpdate.events("room-2", "match-1",
                Arrays.asList(pickup)));
        NetworkUpdate received = WireCodec.readUpdate(new ByteArrayInputStream(mined.toByteArray()));
        BattleEvent restored = received.getEvents().get(0);
        check(received.getType() == NetworkUpdate.Type.EVENTS
                && restored.getEventId() == 41 && restored.getReason().equals("heal")
                && restored.getItemSourceX() == 4 && restored.getItemSourceY() == 8
                && restored.getItemSlotIndex() == 2, "광석 획득 출발 칸과 도착 슬롯 왕복");
        expectIo(() -> WireCodec.writeUpdate(new ByteArrayOutputStream(),
                NetworkUpdate.events("room-2", "match-1", Arrays.asList(
                        BattleEvent.fromWire(BattleEvent.Type.ITEM_ACQUIRED, 42,
                                "student-a", "student-a", 1, "heal", null, -1, -1, -1)))));
    }

    private static void compareBattle(BattleState expected, BattleState actual) {
        check(expected.getStatus() == actual.getStatus()
                && expected.getVersion() == actual.getVersion()
                && same(expected.getWinnerId(), actual.getWinnerId())
                && same(expected.getReason(), actual.getReason())
                && expected.getParticipants().keySet().equals(actual.getParticipants().keySet()),
                "전투 상태와 참가자 ID 보존");
        for (String id : expected.getParticipants().keySet()) {
            check(expected.getParticipant(id).getHp() == actual.getParticipant(id).getHp()
                    && expected.getParticipant(id).isEliminated()
                    == actual.getParticipant(id).isEliminated(), "참가자 HP와 탈락 보존");
            GameState before = expected.getParticipant(id).getGameState();
            GameState after = actual.getParticipant(id).getGameState();
            check(before.getStatus() == after.getStatus() && before.getVersion() == after.getVersion()
                    && before.getNextPieces().equals(after.getNextPieces())
                    && before.getGhostY() == after.getGhostY(), "보드 진행과 다음 블록 보존");
            for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++) {
                check(before.getBoard().getCell(x, y) == after.getBoard().getCell(x, y),
                        "220칸 보드 사본 보존");
            }
        }
    }

    private static void malformedFramesAndFields() throws Exception {
        check(WireCodec.readRequest(new ByteArrayInputStream(new byte[0])) == null,
                "빈 스트림의 정상 EOF");
        expectIo(() -> WireCodec.readRequest(new ByteArrayInputStream(new byte[] { 0, 0 })));
        expectIo(() -> WireCodec.readRequest(new ByteArrayInputStream(new byte[] { 0, 0, 0, 0 })));
        expectIo(() -> WireCodec.readRequest(new ByteArrayInputStream(new byte[] { -1, -1, -1, -1 })));
        int oversized = WireCodec.MAX_FRAME_BYTES + 1;
        expectIo(() -> WireCodec.readRequest(new ByteArrayInputStream(new byte[] {
                (byte) (oversized >>> 24), (byte) (oversized >>> 16),
                (byte) (oversized >>> 8), (byte) oversized })));
        expectIo(() -> WireCodec.readRequest(new ByteArrayInputStream(new byte[] { 0, 0, 0, 3, '{' })));
        expectIo(() -> WireCodec.readRequest(new ByteArrayInputStream(frame(new byte[] { (byte) 0xc3, 0x28 }))));
        String valid = "{\"version\":1,\"message\":\"request\",\"requestId\":1,"
                + "\"kind\":\"SNAPSHOT\",\"matchId\":\"m\"}";
        expectIo(() -> request(valid.replace("\"version\":1", "\"version\":2")));
        expectIo(() -> request(valid.replace("\"requestId\":1", "\"requestId\":1,\"actorId\":\"p\"")));
        expectIo(() -> request(valid.replace("\"matchId\":\"m\"",
                "\"matchId\":\"m\",\"matchId\":\"m\"")));
        expectIo(() -> request("{\"version\":1,\"message\":\"request\",\"kind\":\"SNAPSHOT\","
                + "\"requestId\":1,\"matchId\":\"m\",\"extra\":null,\"extra\":null}"));
        expectIo(() -> request(valid.replace("\"requestId\":1", "\"requestId\":1.5")));
        expectIo(() -> request(valid.replace("\"requestId\":1", "\"requestId\":01")));
        String invalidEscape = valid.replace("\"matchId\":\"m\"",
                "\"matchId\":\"" + (char) 92 + "u+123\"");
        expectIo(() -> request(invalidEscape));
        expectIo(() -> request(valid.replace("\"message\":\"request\"", "\"message\":\"update\"")));
        expectIo(() -> request("{\"version\":1,\"message\":\"request\",\"requestId\":1,"
                + "\"kind\":\"INTENT\",\"matchId\":\"m\","
                + "\"intent\":{\"type\":\"MOVE_LEFT\",\"damage\":999}}"));
        expectIo(() -> update("{\"version\":1,\"message\":\"update\",\"type\":\"EVENTS\"}"));
        expectIo(() -> update("{\"version\":1,\"message\":\"update\",\"type\":\"CONNECTED\","
                + "\"winnerId\":\"p\"}"));
        StringBuilder deep = new StringBuilder();
        for (int index = 0; index < 18; index++) deep.append('[');
        deep.append('0');
        for (int index = 0; index < 18; index++) deep.append(']');
        expectIo(() -> request(deep.toString()));
        ByteArrayOutputStream validSnapshot = new ByteArrayOutputStream();
        WireCodec.writeUpdate(validSnapshot, NetworkUpdate.snapshot("room", "match",
                SampleSnapshots.running("student-a", "student-b")));
        byte[] encoded = validSnapshot.toByteArray();
        String forged = new String(encoded, 4, encoded.length - 4, StandardCharsets.UTF_8)
                .replace("\"actorId\":\"student-a\"", "\"actorId\":\"other\"");
        expectIo(() -> update(forged));
        expectIo(() -> WireCodec.writeRequest(new ByteArrayOutputStream(),
                WireRequest.snapshot(1, "한글-허용안함")));
    }

    private static void request(String json) throws IOException {
        WireCodec.readRequest(new ByteArrayInputStream(frame(json.getBytes(StandardCharsets.UTF_8))));
    }

    private static void update(String json) throws IOException {
        WireCodec.readUpdate(new ByteArrayInputStream(frame(json.getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] frame(byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = payload.length;
        out.write((length >>> 24) & 0xff);
        out.write((length >>> 16) & 0xff);
        out.write((length >>> 8) & 0xff);
        out.write(length & 0xff);
        out.write(payload, 0, length);
        return out.toByteArray();
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void expectIo(IoAction action) {
        try { action.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("잘못된 통신 입력의 IOException 거부 필요");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private interface IoAction { void run() throws IOException; }

    private static final class FragmentedInputStream extends InputStream {
        private final byte[] bytes;
        private int offset;
        private FragmentedInputStream(byte[] bytes) { this.bytes = bytes; }
        @Override public int read() { return offset == bytes.length ? -1 : bytes[offset++] & 0xff; }
        @Override public int read(byte[] buffer, int start, int length) {
            if (offset == bytes.length) return -1;
            if (length == 0) return 0;
            buffer[start] = bytes[offset++];
            return 1;
        }
    }
}
