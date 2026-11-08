package kr.ac.jbnu.se.tetris.network.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.jbnu.se.tetris.battle.BattleSnapshots;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.ParticipantState;
import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.CoreSnapshots;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.Piece;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 검증된 전투 사본과 JSON 값의 양방향 변환 */
final class WireSnapshots {
    private WireSnapshots() { }

    static Map<String, Object> encode(BattleState battle) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("status", battle.getStatus().name());
        value.put("version", battle.getVersion());
        value.put("elapsedMillis", battle.getElapsedMillis());
        if (battle.getWinnerId() != null) value.put("winnerId", battle.getWinnerId());
        if (battle.getReason() != null) value.put("reason", battle.getReason());
        List<Object> participants = new ArrayList<Object>();
        for (ParticipantState participant : battle.getParticipants().values()) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("id", participant.getId());
            entry.put("name", participant.getName());
            entry.put("hp", participant.getHp());
            entry.put("maxHp", participant.getMaxHp());
            entry.put("eliminated", participant.isEliminated());
            entry.put("characterId", participant.getCharacterId());
            entry.put("itemSlots", participant.getItemSlots());
            entry.put("items", new ArrayList<String>(participant.getItems()));
            // 기존 온라인 클라이언트는 알 수 없는 필드를 거부하므로, 기본 1회 아이템만
            // 있는 공식 PvP 스냅샷에는 확장 필드를 보내지 않는다.
            boolean hasMultipleCharges = false;
            for (Integer charge : participant.getItemCharges())
                if (charge != null && charge.intValue() != 1) hasMultipleCharges = true;
            if (hasMultipleCharges)
                entry.put("itemCharges", new ArrayList<Integer>(participant.getItemCharges()));
            entry.put("fever", participant.getFever());
            entry.put("feverRemainingMillis", participant.getFeverRemainingMillis());
            entry.put("timeWarpRemainingMillis", participant.getTimeWarpRemainingMillis());
            entry.put("pendingGarbageLines", participant.getPendingGarbageLines());
            entry.put("garbageWaitRemainingMillis", participant.getGarbageWaitRemainingMillis());
            entry.put("gravityMillis", participant.getGravityMillis());
            entry.put("maxCombo", participant.getMaxCombo());
            entry.put("totalDamage", participant.getTotalDamage());
            entry.put("game", encodeGame(participant.getGameState()));
            participants.add(entry);
        }
        value.put("participants", participants);
        return value;
    }

    static BattleState decode(Object raw) throws IOException {
        Map<String, Object> value = WireCodec.object(raw);
        WireCodec.keys(value, new String[] { "status", "version", "participants" },
                new String[] { "winnerId", "reason", "elapsedMillis" });
        BattleState.Status status = WireCodec.enumeration(BattleState.Status.class, value.get("status"));
        long version = WireCodec.nonnegative(value.get("version"));
        String winnerId = WireCodec.optionalId(value, "winnerId");
        String reason = WireCodec.optionalText(value, "reason", 256);
        List<Object> entries = WireCodec.array(value.get("participants"));
        if (entries.size() < 2 || entries.size() > 4) throw new IOException("Invalid participant count");
        Map<String, ParticipantState> participants = new LinkedHashMap<String, ParticipantState>();
        for (Object rawEntry : entries) {
            Map<String, Object> entry = WireCodec.object(rawEntry);
            WireCodec.keys(entry, new String[] { "id", "name", "hp", "maxHp", "eliminated", "game" },
                    new String[] { "characterId", "itemSlots", "items", "itemCharges", "fever", "feverRemainingMillis",
                            "timeWarpRemainingMillis", "pendingGarbageLines", "garbageWaitRemainingMillis",
                            "gravityMillis", "maxCombo", "totalDamage" });
            String id = WireCodec.id(entry.get("id"));
            String name = WireCodec.text(entry.get("name"), 128);
            int hp = WireCodec.integer(entry.get("hp"));
            int maxHp = WireCodec.integer(entry.get("maxHp"));
            boolean eliminated = WireCodec.bool(entry.get("eliminated"));
            GameState game = decodeGame(entry.get("game"));
            if (participants.containsKey(id)) throw new IOException("Duplicate participant ID");
            List<String> items = new ArrayList<String>();
            if (entry.containsKey("items")) {
                List<Object> inventory = WireCodec.array(entry.get("items"));
                if (inventory.size() > 4) throw new IOException("Too many inventory items");
                for (Object item : inventory) items.add(itemId(item));
            }
            List<Integer> itemCharges = new ArrayList<Integer>();
            if (entry.containsKey("itemCharges")) {
                List<Object> charges = WireCodec.array(entry.get("itemCharges"));
                if (charges.size() != items.size()) throw new IOException("Item charge count mismatch");
                for (Object charge : charges) {
                    int amount = WireCodec.integer(charge);
                    if (amount < 1 || amount > 2) throw new IOException("Invalid item charge");
                    itemCharges.add(amount);
                }
            } else for (int i = 0; i < items.size(); i++) itemCharges.add(1);
            participants.put(id, BattleSnapshots.participant(id, name, hp, maxHp, game, eliminated,
                    entry.containsKey("characterId") ? WireCodec.id(entry.get("characterId")) : "student",
                    optionalInt(entry, "itemSlots", 3), items, itemCharges, optionalInt(entry, "fever", 0),
                    optionalLong(entry, "feverRemainingMillis"), optionalLong(entry, "timeWarpRemainingMillis"),
                    optionalInt(entry, "pendingGarbageLines", game.getPendingGarbageLines()),
                    optionalLong(entry, "garbageWaitRemainingMillis"), optionalInt(entry, "gravityMillis", 400),
                    optionalInt(entry, "maxCombo", 0), optionalInt(entry, "totalDamage", 0)));
        }
        return BattleSnapshots.battle(status, version, participants, winnerId, reason,
                optionalLong(value, "elapsedMillis"));
    }

    private static Map<String, Object> encodeGame(GameState game) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("actorId", game.getActorId());
        value.put("version", game.getVersion());
        value.put("tick", game.getTick());
        value.put("status", game.getStatus().name());
        value.put("board", encodeBoard(game.getBoard()));
        List<Object> boardItems = new ArrayList<Object>();
        List<Object> boardOrigins = new ArrayList<Object>();
        for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++) {
            boardItems.add(game.getBoard().getItemId(x, y));
            boardOrigins.add(game.getBoard().getOriginId(x, y));
        }
        value.put("boardItems", boardItems);
        value.put("boardOrigins", boardOrigins);
        if (game.getActivePiece() != null) {
            Map<String, Object> piece = new LinkedHashMap<String, Object>();
            piece.put("type", game.getActivePiece().getType().name());
            piece.put("rotation", game.getActivePiece().getRotation());
            if (game.getActivePiece().getItemId() != null) {
                piece.put("itemId", game.getActivePiece().getItemId());
                piece.put("identity", game.getActivePiece().getIdentity());
                piece.put("oreCellIndex", game.getActivePiece().getOreCellIndex());
            }
            value.put("activePiece", piece);
        }
        value.put("pieceX", game.getPieceX());
        value.put("pieceY", game.getPieceY());
        value.put("linesCleared", game.getLinesCleared());
        value.put("awaitingSpawn", game.isAwaitingSpawn());
        value.put("holdPiece", game.getHoldPiece().name());
        if (game.getHoldItemId() != null) {
            value.put("holdItemId", game.getHoldItemId());
            value.put("holdOreCellIndex", game.getHoldOreCellIndex());
        }
        value.put("canHold", game.canHold());
        List<Object> next = new ArrayList<Object>();
        for (PieceType piece : game.getNextPieces()) next.add(piece.name());
        value.put("nextPieces", next);
        value.put("ghostY", game.getGhostY());
        value.put("combo", game.getCombo());
        value.put("pendingGarbageLines", game.getPendingGarbageLines());
        return value;
    }

    private static GameState decodeGame(Object raw) throws IOException {
        Map<String, Object> value = WireCodec.object(raw);
        WireCodec.keys(value, new String[] { "actorId", "version", "tick", "status", "board",
                "pieceX", "pieceY", "linesCleared", "awaitingSpawn", "holdPiece", "canHold",
                "nextPieces", "ghostY", "combo", "pendingGarbageLines" },
                new String[] { "activePiece", "boardItems", "boardOrigins", "holdItemId", "holdOreCellIndex" });
        String actorId = WireCodec.id(value.get("actorId"));
        long version = WireCodec.nonnegative(value.get("version"));
        long tick = WireCodec.nonnegative(value.get("tick"));
        GameState.Status status = WireCodec.enumeration(GameState.Status.class, value.get("status"));
        BoardState board = decodeBoard(value.get("board"));
        if (value.containsKey("boardItems")) {
            List<Object> metadata = WireCodec.array(value.get("boardItems"));
            if (metadata.size() != 220) throw new IOException("Board item metadata requires 220 cells");
            String[] itemIds = new String[220];
            long[] origins = new long[220];
            List<Object> rawOrigins = value.containsKey("boardOrigins")
                    ? WireCodec.array(value.get("boardOrigins")) : null;
            if (rawOrigins != null && rawOrigins.size() != 220) throw new IOException("Invalid board provenance");
            PieceType[] cells = new PieceType[220];
            for (int index = 0; index < 220; index++) {
                cells[index] = board.getCell(index % 10, index / 10);
                if (metadata.get(index) != null) itemIds[index] = itemId(metadata.get(index));
                if (rawOrigins != null) origins[index] = WireCodec.nonnegative(rawOrigins.get(index));
            }
            board = CoreSnapshots.board(10, 22, cells, origins, itemIds);
        }
        Piece active = null;
        if (value.containsKey("activePiece")) {
            Map<String, Object> piece = WireCodec.object(value.get("activePiece"));
            WireCodec.keys(piece, new String[] { "type", "rotation" },
                    new String[] { "itemId", "identity", "oreCellIndex" });
            PieceType type = WireCodec.enumeration(PieceType.class, piece.get("type"));
            int rotation = WireCodec.integer(piece.get("rotation"));
            if (rotation < 0 || rotation > 3) throw new IOException("Invalid piece rotation");
            active = new Piece(type);
            if (piece.containsKey("itemId")) active = Piece.withItem(type, itemId(piece.get("itemId")),
                    piece.containsKey("identity") ? WireCodec.positive(piece.get("identity")) : 1L,
                    optionalInt(piece, "oreCellIndex", 0));
            else if (piece.containsKey("oreCellIndex")) throw new IOException("Ore index without item");
            for (int index = 0; index < rotation; index++) active = active.rotateRight();
            if (active.getRotation() != rotation) throw new IOException("Invalid piece rotation");
        }
        List<Object> rawNext = WireCodec.array(value.get("nextPieces"));
        if (rawNext.size() > 3) throw new IOException("Too many next pieces");
        List<PieceType> next = new ArrayList<PieceType>();
        for (Object rawPiece : rawNext) next.add(WireCodec.enumeration(PieceType.class, rawPiece));
        return CoreSnapshots.game(actorId, version, tick, status, board, active,
                WireCodec.integer(value.get("pieceX")), WireCodec.integer(value.get("pieceY")),
                WireCodec.integer(value.get("linesCleared")), WireCodec.bool(value.get("awaitingSpawn")),
                WireCodec.enumeration(PieceType.class, value.get("holdPiece")),
                WireCodec.bool(value.get("canHold")), next, WireCodec.integer(value.get("ghostY")),
                WireCodec.integer(value.get("combo")), WireCodec.integer(value.get("pendingGarbageLines")),
                value.containsKey("holdItemId") ? itemId(value.get("holdItemId")) : null,
                optionalInt(value, "holdOreCellIndex", value.containsKey("holdItemId") ? 0 : -1));
    }

    private static int optionalInt(Map<String, Object> value, String key, int fallback) throws IOException {
        return value.containsKey(key) ? WireCodec.integer(value.get(key)) : fallback;
    }

    private static long optionalLong(Map<String, Object> value, String key) throws IOException {
        return value.containsKey(key) ? WireCodec.nonnegative(value.get(key)) : 0L;
    }

    private static String itemId(Object value) throws IOException {
        String id = WireCodec.id(value);
        if (!id.equals("damage_boost") && !id.equals("garbage_bomb") && !id.equals("heal")
                && !id.equals("shield") && !id.equals("line_cleaner") && !id.equals("fever_charge")
                && !id.equals("time_warp") && !id.equals("nullify")) {
            throw new IOException("Unknown item identifier");
        }
        return id;
    }

    private static String encodeBoard(BoardState board) {
        StringBuilder cells = new StringBuilder(220);
        for (int y = 0; y < 22; y++) {
            for (int x = 0; x < 10; x++) {
                PieceType cell = board.getCell(x, y);
                cells.append(cell == PieceType.EMPTY ? '.' : cell == PieceType.GARBAGE ? '#'
                        : cell.name().charAt(0));
            }
        }
        return cells.toString();
    }

    private static BoardState decodeBoard(Object raw) throws IOException {
        String cells = WireCodec.text(raw, 220);
        if (cells.length() != 220) throw new IOException("Board requires 220 cells");
        PieceType[] result = new PieceType[220];
        for (int index = 0; index < cells.length(); index++) {
            char cell = cells.charAt(index);
            if (cell == '.') result[index] = PieceType.EMPTY;
            else if (cell == '#') result[index] = PieceType.GARBAGE;
            else {
                try { result[index] = PieceType.valueOf(String.valueOf(cell)); }
                catch (IllegalArgumentException error) { throw new IOException("Invalid board cell", error); }
                if (result[index] == PieceType.EMPTY || result[index] == PieceType.GARBAGE) {
                    throw new IOException("Invalid board cell");
                }
            }
        }
        return CoreSnapshots.board(10, 22, result);
    }
}
