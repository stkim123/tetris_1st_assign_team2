package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import kr.ac.jbnu.se.tetris.ui.seongeun.Board;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.MiniPiecePreview;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelMeter;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusArt;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PieceType;

/** 혼자 하는 무한·스프린트·튜토리얼 화면. 보드 옆에 기록과 다음 블록을 모아 둔다. */
public class LocalGamePanel extends kr.ac.jbnu.se.tetris.ui.seongeun.components.ScenePanel {
    private static final int SPRINT_GOAL = 40;

    private final JLabel titleLabel = UniversityPixelTheme.label("무한 모드", 22, Font.BOLD, UniversityPixelTheme.TEXT);
    private final JLabel modeEyebrow = UniversityPixelTheme.label("LOCAL MODE", 12, Font.BOLD, UniversityPixelTheme.MINT);
    private final JLabel statusLabel;
    private final Board playerBoard;
    private final GameButton backButton = new GameButton("돌아가기 [ESC]");
    private final MiniPiecePreview holdPreview = new MiniPiecePreview("HOLD");
    private final MiniPiecePreview[] nextPreviews = {
        new MiniPiecePreview("1"), new MiniPiecePreview("2"), new MiniPiecePreview("3")
    };
    private final JPanel tutorialGuide = new JPanel(new BorderLayout(12, 2));
    private final JLabel tutorialStep = new JLabel("STEP 01 / 05");
    private final JLabel tutorialInstruction = new JLabel("블록을 직접 움직여 보세요.");
    private final JLabel timeValue = statValue("00:00.0");
    private final JLabel linesValue = statValue("0");
    private final JLabel linesCaption = statCaption("LINES");
    private final JLabel comboValue = statValue("0");
    private final PixelMeter sprintBar = new PixelMeter(SPRINT_GOAL, UniversityPixelTheme.GOLD);
    private final Timer clock = new Timer(100, event -> refreshClock());
    private final JPanel standardScreen = new JPanel(new BorderLayout(0, 10));
    private final JPanel standardBoardArea = new JPanel(new GridBagLayout());
    private JPanel standardQueue;
    private TutorialScenePanel tutorialScreen;
    private final CardLayout screens = new CardLayout();
    private String mode = "Infinite";
    private long elapsedMillis, runningSince;

    public LocalGamePanel() {
        setLayout(screens);
        setBackground(UniversityPixelTheme.BG);
        setBorder(null);
        standardScreen.setOpaque(false);
        standardScreen.setBorder(new EmptyBorder(18, 26, 20, 26));

        backButton.secondary();
        backButton.setPreferredSize(new Dimension(172, 52));
        backButton.setFocusable(false);
        backButton.setToolTipText("게임을 끝내고 이전 화면으로 돌아갑니다.");
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        JPanel heading = new JPanel(); heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(modeEyebrow); heading.add(Box.createVerticalStrut(2)); heading.add(titleLabel);
        topPanel.add(CampusArt.icon("graduation_cap",76,75), BorderLayout.WEST);
        topPanel.add(heading, BorderLayout.CENTER);
        topPanel.add(backButton, BorderLayout.EAST);

        tutorialGuide.setBackground(new Color(0xFFF7DD));
        tutorialGuide.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CampusArt.BROWN, 3, true),
                new EmptyBorder(10, 17, 10, 17)));
        tutorialStep.setForeground(UniversityPixelTheme.GOLD);
        tutorialStep.setFont(UniversityPixelTheme.font(20, Font.BOLD));
        tutorialInstruction.setForeground(UniversityPixelTheme.TEXT);
        tutorialInstruction.setFont(UniversityPixelTheme.font(19, Font.BOLD));
        tutorialGuide.add(tutorialStep, BorderLayout.WEST);
        tutorialGuide.add(tutorialInstruction, BorderLayout.CENTER);
        tutorialGuide.setVisible(false);
        JPanel header = new JPanel(new BorderLayout(0, 10)); header.setOpaque(false);
        header.add(topPanel, BorderLayout.NORTH);
        header.add(tutorialGuide, BorderLayout.CENTER);
        standardScreen.add(header, BorderLayout.NORTH);

        statusLabel = UniversityPixelTheme.label(" ", 12, Font.BOLD, UniversityPixelTheme.TEXT_SUB);
        playerBoard = new Board(statusLabel, true);
        playerBoard.setPreferredSize(new Dimension(280, 616));
        playerBoard.setMinimumSize(new Dimension(160, 352));

        standardBoardArea.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.weighty = 1;
        c.fill = GridBagConstraints.BOTH; c.insets = new Insets(0, 0, 0, 18);
        standardBoardArea.add(playerBoard, c);
        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.VERTICAL; c.insets = new Insets(0, 0, 0, 0);
        standardBoardArea.add(sidePanel(), c);
        standardScreen.add(standardBoardArea, BorderLayout.CENTER);
        tutorialScreen = new TutorialScenePanel(playerBoard, holdPreview, nextPreviews,
                tutorialStep, tutorialInstruction, timeValue, linesValue, comboValue,
                () -> backButton.doClick());
        add(standardScreen, "standard");
        add(tutorialScreen, "tutorial");
    }

    private JPanel sidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(new Color(0xFFF8EB));
        side.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CampusArt.BROWN, 3, true), new EmptyBorder(16,16,16,16)));
        side.setPreferredSize(new Dimension(290, 10));

        JPanel stats = new JPanel(new GridLayout(3, 2, 6, 6));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        stats.add(statCaption("TIME")); stats.add(timeValue);
        stats.add(linesCaption); stats.add(linesValue);
        stats.add(statCaption("COMBO")); stats.add(comboValue);
        side.add(stats);
        side.add(Box.createVerticalStrut(8));
        sprintBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(sprintBar);
        side.add(Box.createVerticalStrut(6));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(statusLabel);
        side.add(Box.createVerticalStrut(12));

        side.add(UniversityPixelTheme.label("HOLD  ·  NEXT", 12, Font.BOLD, UniversityPixelTheme.GOLD));
        side.add(Box.createVerticalStrut(6));
        standardQueue = new JPanel(new GridLayout(2, 2, 6, 6));
        standardQueue.setOpaque(false);
        standardQueue.setAlignmentX(Component.LEFT_ALIGNMENT);
        standardQueue.setMaximumSize(new Dimension(150, 152));
        standardQueue.setPreferredSize(new Dimension(150, 152));
        standardQueue.setMinimumSize(new Dimension(150, 152));
        standardQueue.add(holdPreview);
        for (MiniPiecePreview preview : nextPreviews) standardQueue.add(preview);
        side.add(standardQueue);
        side.add(Box.createVerticalGlue());

        side.add(UniversityPixelTheme.label("조작", 12, Font.BOLD, UniversityPixelTheme.GOLD));
        side.add(Box.createVerticalStrut(6));
        JPanel keys = new JPanel(new GridLayout(0, 1, 0, 4));
        keys.setOpaque(false);
        keys.setAlignmentX(Component.LEFT_ALIGNMENT);
        keys.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        String[][] guide = { {"← →", "이동"}, {"↑ ↓", "회전"}, {"D", "한 칸 낙하"}, {"SPACE", "즉시 낙하"},
                {"C", "HOLD"}, {"P", "일시정지"} };
        for (String[] key : guide) keys.add(keyRow(key[0], key[1]));
        side.add(keys);
        return side;
    }

    private static JPanel keyRow(String key, String action) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        row.add(UniversityPixelTheme.chip(key, UniversityPixelTheme.PANEL_LIGHT));
        row.add(UniversityPixelTheme.label(action, 11, Font.PLAIN, UniversityPixelTheme.TEXT_SUB));
        return row;
    }

    private static JLabel statCaption(String text) {
        return UniversityPixelTheme.label(text, 12, Font.BOLD, UniversityPixelTheme.TEXT_SUB);
    }

    private static JLabel statValue(String text) {
        JLabel value = UniversityPixelTheme.label(text, 18, Font.BOLD, UniversityPixelTheme.TEXT);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        return value;
    }

    public void startMode(String modeName) {
        mode = modeName;
        boolean sprint = "Sprint".equals(modeName), tutorial = "Tutorial".equals(modeName);
        modeEyebrow.setText(tutorial ? "CAMPUS GUIDE · TUTORIAL" : "LOCAL MODE");
        titleLabel.setText(sprint ? "40줄 스프린트" : tutorial ? "튜토리얼" : "무한 모드");
        linesCaption.setText(sprint ? "LINES / 40" : "LINES");
        sprintBar.setVisible(sprint);
        sprintBar.setValue(0);
        tutorialGuide.setVisible(false);
        // All three local modes now use one consistent illustrated play scene.
        // The existing Board, HUD and input bindings are shared, not recreated.
        tutorialScreen.setDisplayMode(modeName);
        tutorialScreen.mountBoardAndPreviews();
        screens.show(this, "tutorial");
        revalidate(); repaint();
        statusLabel.setForeground(UniversityPixelTheme.TEXT_SUB);
        elapsedMillis = 0; runningSince = 0;
        refreshClock();
        playerBoard.setOverlayText(null);
        playerBoard.resetEffects();

        playerBoard.start();

        SwingUtilities.invokeLater(() -> playerBoard.requestFocusInWindow());
    }

    public void setBackAction(ActionListener listener) {
        backButton.addActionListener(listener);
    }

    public Board getPlayerBoard() {
        return playerBoard;
    }

    public void setState(GameState state) {
        playerBoard.setState(state);
        holdPreview.setPiece(state.getHoldPiece(), state.getHoldItemId() != null,
                state.getHoldOreCellIndex());
        for (int index = 0; index < nextPreviews.length; index++)
            nextPreviews[index].setPiece(index < state.getNextPieces().size()
                    ? state.getNextPieces().get(index) : PieceType.EMPTY, false);
        boolean running = state.getStatus() == GameState.Status.RUNNING;
        long now = System.currentTimeMillis();
        if (running && runningSince == 0) runningSince = now;
        else if (!running && runningSince != 0) { elapsedMillis += now - runningSince; runningSince = 0; }
        if (running && isShowing()) clock.start(); else clock.stop();
        int lines = state.getLinesCleared();
        linesValue.setText("Sprint".equals(mode) ? Math.min(lines, SPRINT_GOAL) + "" : Integer.toString(lines));
        sprintBar.setValue(Math.min(lines, SPRINT_GOAL));
        comboValue.setText(Integer.toString(Math.max(0, state.getCombo())));
        // 줄 수는 위 기록 칸에 있으므로 상태 줄은 지금 할 수 있는 일만 알려 준다.
        if (running) statusLabel.setText("플레이 중 · P 일시정지");
        else if (state.getStatus() == GameState.Status.PAUSED) statusLabel.setText("일시정지 · P 키로 계속");
        refreshClock();
    }

    private void refreshClock() {
        long total = elapsedMillis + (runningSince == 0 ? 0 : System.currentTimeMillis() - runningSince);
        timeValue.setText(String.format("%02d:%02d.%d", total / 60000, total / 1000 % 60, total / 100 % 10));
        if (tutorialScreen != null) tutorialScreen.repaint();
        if (!isShowing()) clock.stop();
    }

    public void setStatusText(String text) {
        statusLabel.setText(text);
    }
    public void setTutorialInstruction(int step, String instruction) {
        tutorialStep.setText(String.format("STEP %02d / 05", Math.min(5, step + 1)));
        // 세션 안내문 앞의 "1/5 ·" 표기는 왼쪽 STEP 표시와 겹치므로 뺀다.
        tutorialInstruction.setText(instruction == null ? "" : instruction.replaceFirst("^\\d+/\\d+\\s*·\\s*", ""));
        tutorialScreen.repaint();
    }
    public void setCompleted(boolean completed) {
        playerBoard.setOverlayText(completed ? "SPRINT CLEAR" : null);
        if (completed) {
            clock.stop();
            statusLabel.setText("기록 " + timeValue.getText() + " · ESC로 종료");
            statusLabel.setForeground(UniversityPixelTheme.MINT);
        } else statusLabel.setForeground(UniversityPixelTheme.TEXT_SUB);
    }
}
