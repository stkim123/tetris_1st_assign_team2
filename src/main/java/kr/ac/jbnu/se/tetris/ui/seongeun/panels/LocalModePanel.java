package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusBackButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

public class LocalModePanel extends kr.ac.jbnu.se.tetris.ui.seongeun.components.ScenePanel {
    private final GameButton infiniteButton = new GameButton("시작하기");
    private final GameButton sprintButton = new GameButton("시작하기");
    private final GameButton tutorialButton = new GameButton("연습하기");
    private final CampusBackButton backButton = new CampusBackButton("돌아가기");

    public LocalModePanel() {
        infiniteButton.setName("infiniteModeStart");
        sprintButton.setName("sprintModeStart");
        tutorialButton.setName("tutorialModeStart");
        setLayout(new BorderLayout(0, UniversityPixelTheme.HEADER_GAP));
        setBackground(UniversityPixelTheme.BG);
        setBorder(new EmptyBorder(18, UniversityPixelTheme.GUTTER, 18, UniversityPixelTheme.GUTTER));

        backButton.secondary();
        backButton.setPreferredSize(new Dimension(190, 56));
        add(UniversityPixelTheme.screenHeader("SOLO PLAY", "로컬 모드", backButton), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout()); center.setOpaque(false);
        JPanel choices = new JPanel(new GridLayout(1, 3, 14, 0)); choices.setOpaque(false);
        choices.add(mode("∞", "무한 모드", infiniteButton, UniversityPixelTheme.MINT));
        choices.add(mode("40", "스프린트", sprintButton, UniversityPixelTheme.GOLD));
        choices.add(mode("?", "튜토리얼", tutorialButton, UniversityPixelTheme.TEXT_SUB));
        choices.setPreferredSize(new Dimension(780, 260));
        center.add(choices); add(center, BorderLayout.CENTER);
    }

    private JPanel mode(String symbol, String name, GameButton action, Color accent) {
        JPanel card = new JPanel(); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UniversityPixelTheme.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UniversityPixelTheme.LINE, 3),
                new EmptyBorder(18, 14, 20, 14)));
        JLabel icon = new JLabel(symbol, SwingConstants.CENTER);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setForeground(accent); icon.setFont(UniversityPixelTheme.font(46, Font.BOLD));
        JLabel heading = new JLabel(name, SwingConstants.CENTER);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        heading.setForeground(UniversityPixelTheme.TEXT);
        heading.setFont(UniversityPixelTheme.font(20, Font.BOLD));
        action.setAccent(accent);
        action.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        action.setAlignmentX(Component.CENTER_ALIGNMENT);
        action.setMaximumSize(new Dimension(170, 44));
        action.setPreferredSize(new Dimension(170, 44));
        card.add(icon); card.add(Box.createVerticalStrut(6)); card.add(heading);
        card.add(Box.createVerticalGlue()); card.add(action);
        return card;
    }

    public void setInfiniteAction(ActionListener listener) { infiniteButton.addActionListener(listener); }
    public void setSprintAction(ActionListener listener) { sprintButton.addActionListener(listener); }
    public void setTutorialAction(ActionListener listener) { tutorialButton.addActionListener(listener); }
    public void setBackAction(ActionListener listener) { backButton.addActionListener(listener); }
}
