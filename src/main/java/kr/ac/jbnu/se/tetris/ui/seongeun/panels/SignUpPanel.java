package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

public class SignUpPanel extends kr.ac.jbnu.se.tetris.ui.seongeun.components.ScenePanel {
    private final JTextField idField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JPasswordField passwordConfirmField = new JPasswordField(20);
    private final GameButton registerButton = new GameButton("가입");
    private final GameButton backButton = new GameButton("돌아가기");

    public SignUpPanel() {
        setLayout(new GridBagLayout());
        setBackground(UniversityPixelTheme.BG);
        setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel box = new JPanel(); box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(UniversityPixelTheme.PANEL);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UniversityPixelTheme.LINE, 3),
                new EmptyBorder(22, 28, 22, 28)));
        box.setPreferredSize(new Dimension(420, 440));

        box.add(UniversityPixelTheme.label("RANKED PvP  ·  계정 만들기", 12, Font.BOLD, UniversityPixelTheme.MINT));
        box.add(Box.createVerticalStrut(4));
        box.add(UniversityPixelTheme.label("회원가입", 24, Font.BOLD, UniversityPixelTheme.TEXT));
        box.add(Box.createVerticalStrut(18));
        addField(box, "아이디", idField, "영문 시작 · 3~20자");
        addField(box, "비밀번호", passwordField, "8자 이상");
        addField(box, "비밀번호 확인", passwordConfirmField, null);
        idField.addActionListener(event -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(event -> passwordConfirmField.requestFocusInWindow());
        passwordConfirmField.addActionListener(event -> { if (registerButton.isEnabled()) registerButton.doClick(); });
        box.add(Box.createVerticalGlue());
        JPanel actions = new JPanel(new GridLayout(1, 2, 10, 0)); actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        backButton.secondary();
        registerButton.positive();
        actions.add(backButton); actions.add(registerButton); box.add(actions);
        add(box);
    }

    private static void addField(JPanel box, String caption, JTextField field, String hint) {
        JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.add(UniversityPixelTheme.label(caption, 12, Font.BOLD, UniversityPixelTheme.TEXT_SUB), BorderLayout.WEST);
        if (hint != null)
            row.add(UniversityPixelTheme.label(hint, 11, Font.PLAIN, UniversityPixelTheme.TEXT_MUTED), BorderLayout.EAST);
        box.add(row);
        box.add(Box.createVerticalStrut(4));
        UniversityPixelTheme.styleField(field);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(field);
        box.add(Box.createVerticalStrut(12));
    }

    public void setRegisterAction(ActionListener listener) { registerButton.addActionListener(listener); }
    public void setBackAction(ActionListener listener) { backButton.addActionListener(listener); }
    public String getUsername() { return idField.getText().trim(); }
    public char[] getPassword() { return passwordField.getPassword(); }
    public char[] getPasswordConfirmation() { return passwordConfirmField.getPassword(); }
    public void clearPasswords() { passwordField.setText(""); passwordConfirmField.setText(""); }
    public void setBusy(boolean busy) { registerButton.setEnabled(!busy); }
}
