package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

/** 첫 화면. 왼쪽은 계정 없이 바로 시작, 오른쪽은 온라인 PvP 계정 로그인. */
public class LoginPanel extends kr.ac.jbnu.se.tetris.ui.seongeun.components.ScenePanel {
    private final JTextField idField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final GameButton loginButton = new GameButton("로컬 시작");
    private final GameButton signUpButton = new GameButton("회원가입");
    private final GameButton onlineLoginButton = new GameButton("온라인 로그인");
    private final PixelButton backButton = new PixelButton("취소");
    private final JLabel status = new JLabel();
    private JPanel localCard;
    private JPanel columns;

    public LoginPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(UniversityPixelTheme.BG);
        setBorder(new EmptyBorder(20, 20, 18, 20));

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        navigation.setOpaque(false);
        backButton.secondary();
        backButton.setPreferredSize(new Dimension(100, 36));
        navigation.add(backButton);
        add(navigation, BorderLayout.NORTH);

        JPanel title = new JPanel(); title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        JLabel game = label("TETRIS MONSTER", 13, UniversityPixelTheme.MINT);
        JLabel name = label("CAMPUS QUEST", 34, UniversityPixelTheme.GOLD);
        title.add(game); title.add(Box.createVerticalStrut(2)); title.add(name);

        // 제목과 두 카드를 한 덩어리로 화면 가운데에 둔다.
        JPanel content = new JPanel(new GridBagLayout()); content.setOpaque(false);
        JPanel block = new JPanel(new BorderLayout(0, 24)); block.setOpaque(false);
        columns = new JPanel(new GridLayout(1, 2, 16, 0)); columns.setOpaque(false);
        columns.setPreferredSize(new Dimension(700, 400));
        localCard = localCard();
        columns.add(localCard); columns.add(onlineCard());
        block.add(title, BorderLayout.NORTH);
        block.add(columns, BorderLayout.CENTER);
        content.add(block); add(content, BorderLayout.CENTER);

        setStatus("");
    }

    private JPanel localCard() {
        JPanel card = card(UniversityPixelTheme.GOLD);
        card.add(label("바로 플레이", 13, UniversityPixelTheme.GOLD));
        card.add(Box.createVerticalStrut(4));
        card.add(label("스토리 · 로컬 모드", 21, UniversityPixelTheme.TEXT));
        card.add(Box.createVerticalStrut(6));
        card.add(Box.createVerticalGlue());
        JPanel bosses = new JPanel(new GridLayout(1, 3, 8, 0)); bosses.setOpaque(false);
        String[] names = { "교수", "캡스톤", "기업" };
        for (int index = 0; index < names.length; index++) {
            JPanel boss = new JPanel(new BorderLayout(0, 2)); boss.setOpaque(true);
            boss.setBackground(UniversityPixelTheme.PANEL_LIGHT);
            boss.setBorder(BorderFactory.createLineBorder(UniversityPixelTheme.LINE, 2));
            JLabel art = new JLabel(GameArt.icon(GameArt.keyForLevel((index + 1) * 3), 64, 66), SwingConstants.CENTER);
            boss.add(art, BorderLayout.CENTER);
            JLabel caption = new JLabel("BOSS · " + names[index], SwingConstants.CENTER);
            caption.setFont(UniversityPixelTheme.font(11, Font.BOLD));
            caption.setForeground(UniversityPixelTheme.TEXT_SUB);
            caption.setBorder(new EmptyBorder(0, 0, 4, 0));
            boss.add(caption, BorderLayout.SOUTH);
            bosses.add(boss);
        }
        bosses.setAlignmentX(Component.CENTER_ALIGNMENT);
        bosses.setMaximumSize(new Dimension(300, 96));
        card.add(bosses);
        card.add(Box.createVerticalGlue());
        loginButton.primary();
        loginButton.setFont(UniversityPixelTheme.font(15, Font.BOLD));
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(240, 46));
        loginButton.setPreferredSize(new Dimension(240, 46));
        card.add(loginButton);
        return card;
    }

    private JPanel onlineCard() {
        JPanel card = card(UniversityPixelTheme.LINE);
        card.add(UniversityPixelTheme.label("RANKED PvP", 13, Font.BOLD, UniversityPixelTheme.MINT));
        card.add(Box.createVerticalStrut(4));
        card.add(UniversityPixelTheme.label("온라인 계정", 21, Font.BOLD, UniversityPixelTheme.TEXT));
        card.add(Box.createVerticalStrut(14));
        card.add(fieldLabel("아이디"));
        card.add(Box.createVerticalStrut(4));
        UniversityPixelTheme.styleField(idField); card.add(idField);
        card.add(Box.createVerticalStrut(10));
        card.add(fieldLabel("비밀번호"));
        card.add(Box.createVerticalStrut(4));
        UniversityPixelTheme.styleField(passwordField); card.add(passwordField);
        // 아이디에서 Enter는 비밀번호 칸으로, 비밀번호에서 Enter는 로그인으로 이어진다.
        idField.addActionListener(event -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(event -> { if (onlineLoginButton.isEnabled()) onlineLoginButton.doClick(); });
        card.add(Box.createVerticalStrut(14));
        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0)); buttons.setOpaque(false);
        onlineLoginButton.positive();
        signUpButton.secondary();
        buttons.add(onlineLoginButton); buttons.add(signUpButton);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        card.add(buttons);
        card.add(Box.createVerticalStrut(10));
        status.setForeground(UniversityPixelTheme.TEXT_SUB);
        status.setFont(UniversityPixelTheme.font(12, Font.PLAIN));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(status);
        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel card(Color accent) {
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UniversityPixelTheme.PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent, 3), new EmptyBorder(20, 20, 18, 20)));
        return panel;
    }
    private JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(UniversityPixelTheme.font(size, Font.BOLD)); label.setForeground(color);
        return label;
    }
    private static JLabel fieldLabel(String text) {
        return UniversityPixelTheme.label(text, 12, Font.BOLD, UniversityPixelTheme.TEXT_SUB);
    }

    public void setLoginAction(ActionListener listener) { loginButton.addActionListener(listener); }
    public void setBackAction(ActionListener listener) { backButton.addActionListener(listener); }
    public void setBackLabel(String text) { backButton.setText(text); }
    public void focusUsername() { idField.requestFocusInWindow(); }
    /** 온라인 기능에서 왔을 때는 계정 작업만 보여 원래 선택한 행동에 집중시킨다. */
    public void setOnlineOnly(boolean onlineOnly) {
        if (onlineOnly && localCard.getParent() == columns) columns.remove(localCard);
        else if (!onlineOnly && localCard.getParent() != columns) columns.add(localCard, 0);
        columns.setPreferredSize(new Dimension(onlineOnly ? 380 : 700, onlineOnly ? 340 : 400));
        columns.revalidate();
        columns.repaint();
    }
    public void setSignUpAction(ActionListener listener) { signUpButton.addActionListener(listener); }
    public String getUsername() { return idField.getText().trim(); }
    public char[] getPassword() { return passwordField.getPassword(); }
    public void clearPassword() { passwordField.setText(""); }
    public void setOnlineLoginAction(ActionListener listener) { onlineLoginButton.addActionListener(listener); }
    public void setOnlineAvailable(boolean available) {
        onlineLoginButton.setEnabled(available); signUpButton.setEnabled(available);
        idField.setEnabled(available); passwordField.setEnabled(available);
        if (!available) setStatus("온라인 설정이 없어 지금은 로컬 플레이만 가능합니다.");
    }
    public void setBusy(boolean busy) { onlineLoginButton.setEnabled(!busy); signUpButton.setEnabled(!busy); }
    /** 긴 안내도 카드 폭 안에서 줄바꿈되도록 HTML로 감싼다. */
    public void setStatus(String text) {
        String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        status.setText("<html><div style='width:250px'>" + safe + "</div></html>");
        status.setToolTipText(text);
    }
}
