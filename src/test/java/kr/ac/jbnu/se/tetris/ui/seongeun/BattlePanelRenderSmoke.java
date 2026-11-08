package kr.ac.jbnu.se.tetris.ui.seongeun;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.JScrollPane;
import kr.ac.jbnu.se.tetris.battle.*;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.BoardState;
import kr.ac.jbnu.se.tetris.core.CoreSnapshots;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.BattlePanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelArena;

/** headless 구성의 실제 전투 스냅샷을 PNG로 확인하는 독립 렌더 도구. */
public final class BattlePanelRenderSmoke {
    private BattlePanelRenderSmoke() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3 && args.length != 4)
            throw new IllegalArgumentException("Output PNG path [width height [online|impact|sample]] required");
        Path output = Paths.get(args[0]);
        final int width = args.length >= 3 ? Integer.parseInt(args[1]) : 1160;
        final int height = args.length >= 3 ? Integer.parseInt(args[2]) : 780;
        final boolean online = args.length == 4 && "online".equalsIgnoreCase(args[3]);
        final boolean impact = args.length == 4 && "impact".equalsIgnoreCase(args[3]);
        final boolean sample = args.length == 4 && "sample".equalsIgnoreCase(args[3]);
        if (args.length == 4 && !online && !impact && !sample)
            throw new IllegalArgumentException("Unknown mode: " + args[3]);
        SwingUtilities.invokeAndWait(() -> {
            try {
                BattleManager manager = online ? BattleManager.pvp(Arrays.asList(
                        new ParticipantSpec("local", "플레이어", 100),
                        new ParticipantSpec("monster", "상대 플레이어", 100)), 17L)
                        : BattleManager.pve(Arrays.asList(
                        new ParticipantSpec("local", "플레이어", 100),
                        new ParticipantSpec("monster", "술", 30)), 17L, 450, 3500, 0);
                manager.start();
                BattlePanel panel = new BattlePanel();
                if (online) panel.setMode(true);
                else {
                    panel.setEncounter("university", 1, kr.ac.jbnu.se.tetris.story.MonsterTier.NORMAL, "university:0");
                    kr.ac.jbnu.se.tetris.ui.seongeun.components.GameArt.sprite("university:0");
                }
                BattleState initial = manager.getState();
                panel.setState(online ? withoutBoardOrigins(initial) : sample ? sampleInventory(initial) : initial, "local");
                JScrollPane viewport = new JScrollPane(panel);
                viewport.setBorder(null);
                UniversityPixelTheme.apply(viewport);
                viewport.setSize(width, height);
                layout(viewport);
                if (online) assertOnlineGeometry(panel);
                if (impact) {
                    PixelArena arena = null;
                    for (Component child : panel.getComponents())
                        if (child instanceof PixelArena) arena = (PixelArena) child;
                    if (arena == null) throw new AssertionError("Battle arena is missing");
                    arena.showEffect(PixelArena.Effect.ATTACK, 12, true);
                    arena.showEffect(PixelArena.Effect.DAMAGE, 12, true);
                    panel.getPlayerBoard().showAttack();
                    panel.getEnemyBoard().showHit();
                    Thread.sleep(PixelArena.IMPACT_MS + 70);
                }
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                viewport.printAll(graphics);
                graphics.dispose();
                ImageIO.write(image, "png", output.toFile());
                if (online) assertOnlinePlacement(panel, manager);
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.out.println("PASS BattlePanelRenderSmoke " + output);
    }
    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }
    private static void assertOnlineGeometry(BattlePanel panel) {
        Rectangle player = SwingUtilities.convertRectangle(panel.getPlayerBoard().getParent(),
                panel.getPlayerBoard().getBounds(), panel);
        Rectangle enemy = SwingUtilities.convertRectangle(panel.getEnemyBoard().getParent(),
                panel.getEnemyBoard().getBounds(), panel);
        if (player.width != enemy.width || player.height != enemy.height || player.intersects(enemy))
            throw new AssertionError("Online boards must be equal-sized and separate");
        PixelArena arena = null;
        for (Component child : panel.getComponents())
            if (child instanceof PixelArena) arena = (PixelArena) child;
        if (arena == null || enemy.x + enemy.width >= arena.getX()
                || arena.getX() + arena.getWidth() >= player.x
                || player.x + player.width > panel.getWidth())
            throw new AssertionError("Online battle lane must fit between the boards");
        Container opponent = panel.getEnemyBoard().getParent();
        for (Component child : opponent.getComponents())
            if (child.getY() < 0 || child.getY() + child.getHeight() > opponent.getHeight())
                throw new AssertionError("Online opponent HUD is clipped");
    }
    private static void assertOnlinePlacement(BattlePanel panel, BattleManager manager) throws Exception {
        if (!manager.submit("local", GameAction.Type.HARD_DROP).isAccepted())
            throw new AssertionError("Online placement command was rejected");
        panel.setState(withoutBoardOrigins(manager.getState()), "local");
        for (Component child : panel.getComponents()) if (child instanceof PixelArena) {
            Field started = PixelArena.class.getDeclaredField("playerPlacementAt");
            started.setAccessible(true);
            if (started.getLong(child) <= 0)
                throw new AssertionError("Online snapshot did not trigger placement feedback");
            return;
        }
        throw new AssertionError("Online arena is missing");
    }
    /** 실제 슬롯 글자와 광석 칸이 최소 창에서 잘리지 않는지 보는 표시 전용 예제. */
    private static BattleState sampleInventory(BattleState state) {
        Map<String, ParticipantState> participants = new LinkedHashMap<>(state.getParticipants());
        ParticipantState local = participants.get("local");
        GameState game = local.getGameState();
        PieceType[] cells = new PieceType[220];
        Arrays.fill(cells, PieceType.EMPTY);
        String[] rows = { "Z.........", "ZZ......S.", "ZZ.J....SS", "JJJOO...SS", "JJJOO.OOOO" };
        for (int row = 0; row < rows.length; row++) {
            int y = rows.length - 1 - row;
            for (int x = 0; x < 10; x++) {
                char cell = rows[row].charAt(x);
                if (cell != '.') cells[y * 10 + x] = PieceType.valueOf(String.valueOf(cell));
            }
        }
        String[] itemIds = new String[220];
        itemIds[1 * 10 + 4] = "heal";
        GameState example = CoreSnapshots.game(game.getActorId(), game.getVersion(), game.getTick(),
                game.getStatus(), CoreSnapshots.board(10, 22, cells, itemIds), null, 0, 0,
                game.getLinesCleared(), true, game.getHoldPiece(), game.canHold(),
                game.getNextPieces(), -1, game.getCombo(), game.getPendingGarbageLines(), game.getHoldItemId());
        participants.put("local", BattleSnapshots.participant(local.getId(), local.getName(),
                local.getHp(), local.getMaxHp(), example, false, local.getCharacterId(),
                local.getItemSlots(), Arrays.asList("line_cleaner", "damage_boost", "heal"),
                Arrays.asList(2, 1, 2), local.getFever(), local.getFeverRemainingMillis(),
                local.getTimeWarpRemainingMillis(), local.getPendingGarbageLines(),
                local.getGarbageWaitRemainingMillis(), local.getGravityMillis(),
                local.getMaxCombo(), local.getTotalDamage()));
        return BattleSnapshots.battle(state.getStatus(), state.getVersion(), participants,
                state.getWinnerId(), state.getReason(), state.getElapsedMillis());
    }
    /** 현 운영 서버와 같은 boardOrigins 없는 스냅샷을 재현한다. */
    private static BattleState withoutBoardOrigins(BattleState state) {
        Map<String, ParticipantState> participants = new LinkedHashMap<>();
        for (ParticipantState participant : state.getParticipants().values()) {
            GameState game = participant.getGameState();
            PieceType[] cells = new PieceType[220];
            BoardState board = game.getBoard();
            for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++)
                cells[y * 10 + x] = board.getCell(x, y);
            GameState oldWire = CoreSnapshots.game(game.getActorId(), game.getVersion(), game.getTick(),
                    game.getStatus(), CoreSnapshots.board(10, 22, cells), game.getActivePiece(),
                    game.getPieceX(), game.getPieceY(), game.getLinesCleared(), game.isAwaitingSpawn(),
                    game.getHoldPiece(), game.canHold(), game.getNextPieces(), game.getGhostY(),
                    game.getCombo(), game.getPendingGarbageLines(), game.getHoldItemId());
            participants.put(participant.getId(), BattleSnapshots.participant(participant.getId(),
                    participant.getName(), participant.getHp(), participant.getMaxHp(), oldWire,
                    participant.isEliminated(), participant.getCharacterId(), participant.getItemSlots(),
                    participant.getItems(), participant.getItemCharges(), participant.getFever(),
                    participant.getFeverRemainingMillis(), participant.getTimeWarpRemainingMillis(),
                    participant.getPendingGarbageLines(), participant.getGarbageWaitRemainingMillis(),
                    participant.getGravityMillis(), participant.getMaxCombo(), participant.getTotalDamage()));
        }
        return BattleSnapshots.battle(state.getStatus(), state.getVersion(), participants,
                state.getWinnerId(), state.getReason(), state.getElapsedMillis());
    }
}
