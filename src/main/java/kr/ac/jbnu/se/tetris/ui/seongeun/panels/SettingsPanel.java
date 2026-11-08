package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.PixelButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusBackButton;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.CampusArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

/** 게임 중에는 숨기는 오디오·온라인 계정·조작 학습 설정 화면. */
public final class SettingsPanel extends kr.ac.jbnu.se.tetris.ui.seongeun.components.ScenePanel {
    private final PixelButton back = new CampusBackButton("돌아가기");
    private final PixelButton tutorial = new PixelButton("튜토리얼 시작");
    private final PixelButton onlineAccount = new PixelButton("로그인 / 가입");
    private final JLabel onlineAccountStatus = text("로컬 플레이 · 로그인 필요", 13, UniversityPixelTheme.TEXT_SUB);
    private final JCheckBox skipTutorial = new JCheckBox("다시 보지 않기");
    private final JCheckBox bgmMute = new JCheckBox("BGM 끄기");
    private final JCheckBox sfxMute = new JCheckBox("효과음 끄기");
    private final JCheckBox reduceTransitions = new JCheckBox("화면 전환 효과 끄기");
    private final JSlider bgmVolume = slider();
    private final JSlider sfxVolume = slider();
    private boolean loading;

    public SettingsPanel() {
        setLayout(new BorderLayout(0, 16));
        setBackground(UniversityPixelTheme.BG);
        setBorder(new EmptyBorder(20, 42, 26, 42));
        back.secondary();
        back.setPreferredSize(new Dimension(190, 56));
        add(UniversityPixelTheme.screenHeader("CAMPUS GUIDE", "설정", back), BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 24, 0)); columns.setOpaque(false);
        JPanel audio = section("소리");
        row(audio, "BGM", bgmMute, bgmVolume);
        audio.add(Box.createVerticalStrut(35));
        row(audio, "효과음", sfxMute, sfxVolume);
        audio.add(Box.createVerticalStrut(25));
        JLabel accountHeading = text("🌐 온라인 계정", 20, CampusArt.BROWN);
        accountHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        audio.add(accountHeading);
        audio.add(Box.createVerticalStrut(6));
        onlineAccountStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        audio.add(onlineAccountStatus);
        audio.add(Box.createVerticalStrut(10));
        onlineAccount.secondary();
        onlineAccount.setAlignmentX(Component.LEFT_ALIGNMENT);
        onlineAccount.setMaximumSize(new Dimension(210, 52));
        audio.add(onlineAccount);
        audio.add(Box.createVerticalGlue());
        columns.add(audio);

        JPanel guide = section("튜토리얼");
        tutorial.primary();
        tutorial.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        tutorial.setAlignmentX(Component.LEFT_ALIGNMENT);
        tutorial.setMaximumSize(new Dimension(440, 68)); guide.add(tutorial);
        guide.add(Box.createVerticalStrut(10));
        skipTutorial.setOpaque(false); skipTutorial.setForeground(UniversityPixelTheme.TEXT);
        skipTutorial.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        skipTutorial.setAlignmentX(Component.LEFT_ALIGNMENT); guide.add(skipTutorial);
        guide.add(Box.createVerticalStrut(37));
        guide.add(text("▣ 화면", 24, CampusArt.BROWN));
        reduceTransitions.setOpaque(false); reduceTransitions.setForeground(UniversityPixelTheme.TEXT_SUB);
        reduceTransitions.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        reduceTransitions.setAlignmentX(Component.LEFT_ALIGNMENT); guide.add(reduceTransitions);
        guide.add(Box.createVerticalGlue());
        columns.add(guide);
        columns.setPreferredSize(new Dimension(1100, 590));
        JPanel center = new JPanel(new java.awt.GridBagLayout()); center.setOpaque(false);
        center.add(columns);
        add(center, BorderLayout.CENTER);

    }

    private static JSlider slider() {
        JSlider slider = new JSlider(0, 100, 65);
        slider.setOpaque(false);
        slider.setForeground(CampusArt.GREEN);
        slider.setMaximumSize(new Dimension(420, 58));
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMajorTickSpacing(25); slider.setPaintTicks(true);
        return slider;
    }
    private static JLabel text(String value, int size, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(UniversityPixelTheme.font(size, Font.BOLD));
        label.setForeground(color);
        return label;
    }
    private static JPanel section(String heading) {
        CampusArt.Card panel = CampusArt.card(null, CampusArt.BROWN, 24);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = text(heading, 28, CampusArt.BROWN);
        title.setAlignmentX(Component.LEFT_ALIGNMENT); panel.add(title);
        panel.add(Box.createVerticalStrut(22));
        return panel;
    }
    private static void row(JPanel parent, String label, JCheckBox mute, JSlider volume) {
        JPanel headingRow = new JPanel(new BorderLayout()); headingRow.setOpaque(false);
        headingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headingRow.setMaximumSize(new Dimension(420, 24));
        headingRow.add(text(label, 17, UniversityPixelTheme.TEXT), BorderLayout.WEST);
        JLabel percent = text(volume.getValue() + "%", 14, UniversityPixelTheme.GOLD);
        headingRow.add(percent, BorderLayout.EAST);
        // 음소거 중에는 숫자를 흐리게 보여 지금 소리가 나지 않는다는 것을 알린다.
        Runnable refresh = () -> {
            percent.setText(mute.isSelected() ? "음소거" : volume.getValue() + "%");
            percent.setForeground(mute.isSelected() ? UniversityPixelTheme.TEXT_MUTED : UniversityPixelTheme.GOLD);
        };
        volume.addChangeListener(event -> refresh.run());
        mute.addItemListener(event -> refresh.run());
        parent.add(headingRow);
        parent.add(Box.createVerticalStrut(6));
        volume.setAlignmentX(Component.LEFT_ALIGNMENT); parent.add(volume);
        mute.setOpaque(false); mute.setForeground(UniversityPixelTheme.TEXT_SUB);
        mute.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        mute.setAlignmentX(Component.LEFT_ALIGNMENT); parent.add(mute);
    }
    public void update(boolean bgmOff, float bgmLevel, boolean sfxOff,
            float sfxLevel, boolean tutorialSkipped) {
        update(bgmOff, bgmLevel, sfxOff, sfxLevel, tutorialSkipped, false);
    }
    public void update(boolean bgmOff, float bgmLevel, boolean sfxOff,
            float sfxLevel, boolean tutorialSkipped, boolean transitionsOff) {
        loading = true;
        try {
            bgmMute.setSelected(bgmOff); bgmVolume.setValue(Math.round(bgmLevel * 100));
            sfxMute.setSelected(sfxOff); sfxVolume.setValue(Math.round(sfxLevel * 100));
            skipTutorial.setSelected(tutorialSkipped);
            reduceTransitions.setSelected(transitionsOff);
        } finally { loading = false; }
    }
    public void setBackAction(ActionListener listener) { back.addActionListener(listener); }
    public void setAccountAction(ActionListener listener) { onlineAccount.addActionListener(listener); }
    public void setAccountStatus(String status, boolean signedIn, boolean available, boolean busy) {
        String shortStatus = busy ? "계정 처리 중..." : signedIn
                ? status.replaceFirst("^온라인 PvP 로그인 완료\\s*·\\s*", "접속: ")
                : available ? status : "온라인 설정 없음 · 로컬 플레이 가능";
        onlineAccountStatus.setText(shortStatus);
        onlineAccountStatus.setToolTipText(status);
        onlineAccountStatus.setForeground(signedIn ? UniversityPixelTheme.MINT : UniversityPixelTheme.TEXT_SUB);
        onlineAccount.setText(signedIn ? "로그아웃" : "로그인 / 가입");
        onlineAccount.setEnabled(available && !busy);
    }
    public void setTutorialAction(ActionListener listener) { tutorial.addActionListener(listener); }
    public void setBgmMuteAction(Consumer<Boolean> action) {
        bgmMute.addActionListener(event -> { if (!loading) action.accept(bgmMute.isSelected()); });
    }
    public void setSfxMuteAction(Consumer<Boolean> action) {
        sfxMute.addActionListener(event -> { if (!loading) action.accept(sfxMute.isSelected()); });
    }
    public void setBgmVolumeAction(Consumer<Float> action) {
        bgmVolume.addChangeListener(event -> { if (!loading) action.accept(bgmVolume.getValue() / 100f); });
    }
    public void setSfxVolumeAction(Consumer<Float> action) {
        sfxVolume.addChangeListener(event -> { if (!loading) action.accept(sfxVolume.getValue() / 100f); });
    }
    public void setSkipTutorialAction(Consumer<Boolean> action) {
        skipTutorial.addActionListener(event -> { if (!loading) action.accept(skipTutorial.isSelected()); });
    }
    public void setReduceTransitionsAction(Consumer<Boolean> action) {
        reduceTransitions.addActionListener(event -> { if (!loading) action.accept(reduceTransitions.isSelected()); });
    }
}
