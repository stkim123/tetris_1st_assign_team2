package kr.ac.jbnu.se.tetris.ui.seongeun.components;



import java.awt.BorderLayout;

import java.awt.Color;

import java.awt.Component;

import java.awt.Container;

import java.awt.FlowLayout;

import java.awt.Font;

import java.awt.FontFormatException;

import java.awt.GraphicsEnvironment;

import java.io.IOException;

import java.io.InputStream;

import javax.swing.AbstractButton;

import javax.swing.BorderFactory;

import javax.swing.Box;

import javax.swing.BoxLayout;

import javax.swing.JComponent;

import javax.swing.JLabel;

import javax.swing.JPanel;

import javax.swing.JScrollPane;

import javax.swing.JTextField;

import javax.swing.UIManager;

import javax.swing.UIDefaults;

import javax.swing.border.Border;

import javax.swing.border.EmptyBorder;

import javax.swing.plaf.ColorUIResource;

import javax.swing.plaf.FontUIResource;



/** 한국 캠퍼스의 콘크리트·청록 그림자·따뜻한 광석 빛을 공유하는 UI 팔레트. */

public final class UniversityPixelTheme {

    public static final Color BG = new Color(0xE7EBDE);

    public static final Color PANEL = new Color(0xFFF8EB);

    public static final Color PANEL_LIGHT = new Color(0xFFFCF4);

    public static final Color TEXT = new Color(0x182B40);

    public static final Color TEXT_SUB = new Color(0x486174);

    public static final Color TEXT_MUTED = new Color(0x73808B);

    public static final Color GOLD = new Color(0xE9A124);

    public static final Color MINT = new Color(0x158C6D);

    public static final Color CORAL = new Color(0xD7544A);

    public static final Color LINE = new Color(0xD0B992);

    public static final Color BLACK = new Color(0x241B1D);

    /** 어두운 보드·상태 배지에서만 쓰는 역상 글자색. */

    public static final Color TEXT_ON_DARK = new Color(0xF4F0E6);

    /** 화면 바깥 여백과 머리글 아래 간격. 모든 메뉴 화면이 같은 값을 쓴다. */

    public static final int GUTTER = 22, HEADER_GAP = 16;

    // Load the official Regular/Bold font files from the application's classpath.
    // If they are missing, preserve the existing system-font fallback.
    // Add the downloaded TTF as src/main/resources/ui/fonts/Jalnan.ttf.
    // If not supplied, the previous NanumSquareRound font remains active.
    private static final Font JALNAN = loadOptionalJalnan("/ui/fonts/Jalnan.ttf");
    private static final Font ROUND_REGULAR = loadRoundFont("/ui/fonts/NanumSquareRoundR.ttf");
    private static final Font ROUND_BOLD = loadRoundFont("/ui/fonts/NanumSquareRoundB.ttf");
    private static final String FONT_FAMILY = chooseFont();



    private UniversityPixelTheme() { }



    private static final Font LOBBY_FONT = loadRoundFont("/ui/fonts/Jalnan.otf");

    public static Font lobbyFont(int size, int style) {
        if (LOBBY_FONT != null) {
            return LOBBY_FONT.deriveFont(style, (float) size);
        }
        return font(size, style);
    }
    
    public static Font font(int size, int style) {
        // Jalnan ships as one heavy face; do not synthesize an extra bold on top.
        if (JALNAN != null) return JALNAN.deriveFont(Font.PLAIN, (float) size);
        Font preferred = (style & Font.BOLD) != 0
                ? (ROUND_BOLD != null ? ROUND_BOLD : ROUND_REGULAR)
                : (ROUND_REGULAR != null ? ROUND_REGULAR : ROUND_BOLD);
        if (preferred != null) return preferred.deriveFont(style, (float) size);
        return new Font(FONT_FAMILY, style, size);
    }

    private static Font loadOptionalJalnan(String resourcePath) {
        try (InputStream stream = UniversityPixelTheme.class.getResourceAsStream(resourcePath)) {
            if (stream == null) return null;
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (IOException | FontFormatException | SecurityException error) {
            System.err.println("[GameFont] Cannot load Jalnan: " + error.getMessage());
            return null;
        }
    }

    private static Font loadRoundFont(String resourcePath) {
        try (InputStream stream = UniversityPixelTheme.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[GameFont] Missing font: " + resourcePath);
                return null;
            }
            Font loaded = Font.createFont(Font.TRUETYPE_FONT, stream);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
            return loaded;
        } catch (IOException | FontFormatException | SecurityException error) {
            System.err.println("[GameFont] Cannot load font " + resourcePath + ": " + error.getMessage());
            return null;
        }
    }



    /** 현재 화면과 자식 위젯에 적용한다. 기존 레이아웃과 사용자 입력 설정은 건드리지 않는다. */

    public static void apply(Component component) {

        if (component == null) return;

        if (component instanceof JPanel && !isThemeColor(component.getBackground())) component.setBackground(BG);

        if (component instanceof JScrollPane) component.setBackground(BG);

        if (component instanceof JTextField) component.setBackground(PANEL);

        if (component instanceof JLabel || component instanceof AbstractButton

                || component instanceof JTextField) {

            if (!isThemeColor(component.getForeground())) component.setForeground(TEXT);

        }

        // All Swing widgets use the common font; keep each component's size and weight.
        if (component instanceof JComponent) {
            Font previous = component.getFont();
            if (previous != null) component.setFont(font(previous.getSize(), previous.getStyle()));
        }

        if (component instanceof AbstractButton && !(component instanceof PixelButton)) {

            AbstractButton button = (AbstractButton) component;

            button.setBackground(PANEL_LIGHT);

            button.setForeground(TEXT);

            button.setFocusPainted(false);

            button.setBorder(BorderFactory.createCompoundBorder(

                    BorderFactory.createLineBorder(BLACK, 3),

                    BorderFactory.createEmptyBorder(3, 7, 3, 7)));

        }

        if (component instanceof Container) {

            for (Component child : ((Container) component).getComponents()) apply(child);

        }

    }



    /** 대화상자·툴팁·체크박스처럼 Swing이 직접 만드는 위젯도 같은 색으로 보이게 한다. */

    public static void installDefaults() {
        // Swing-created dialogs, lists, combo boxes, tabs etc. also share the game font.
        // Take a snapshot of keys before writing back to UIManager.
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        for (Object key : new java.util.ArrayList<Object>(defaults.keySet())) {
            if (!(key instanceof String) || !((String) key).endsWith(".font")) continue;
            Object value = defaults.get(key);
            if (value instanceof Font) {
                Font previous = (Font) value;
                UIManager.put(key, new FontUIResource(font(previous.getSize(), previous.getStyle())));
            }
        }

        // 패널 기본색은 apply()가 칠하는 화면 배경과 같게 두어 기존 화면 색을 바꾸지 않는다.

        ColorUIResource bg = new ColorUIResource(BG), text = new ColorUIResource(TEXT);

        ColorUIResource light = new ColorUIResource(PANEL_LIGHT);

        FontUIResource body = new FontUIResource(font(14, Font.PLAIN));

        FontUIResource bold = new FontUIResource(font(13, Font.BOLD));

        UIManager.put("OptionPane.background", bg);

        UIManager.put("OptionPane.messageForeground", text);

        UIManager.put("OptionPane.messageFont", body);

        UIManager.put("OptionPane.buttonFont", bold);

        UIManager.put("Panel.background", bg);

        UIManager.put("Label.foreground", text);

        UIManager.put("Button.background", light);

        UIManager.put("Button.foreground", text);

        UIManager.put("Button.select", new ColorUIResource(LINE));

        UIManager.put("Button.focus", new ColorUIResource(GOLD));

        UIManager.put("Button.font", bold);

        UIManager.put("TextField.background", light);

        UIManager.put("TextField.foreground", text);

        UIManager.put("TextField.caretForeground", text);

        UIManager.put("TextField.selectionBackground", new ColorUIResource(LINE));

        UIManager.put("TextField.font", body);

        UIManager.put("ToolTip.background", light);

        UIManager.put("ToolTip.foreground", text);

        UIManager.put("ToolTip.font", new FontUIResource(font(12, Font.PLAIN)));

        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(LINE, 2));

        UIManager.put("CheckBox.background", bg);

        UIManager.put("CheckBox.foreground", text);

        UIManager.put("Slider.background", bg);

        UIManager.put("Slider.foreground", new ColorUIResource(GOLD));

        UIManager.put("Slider.tickColor", new ColorUIResource(TEXT_SUB));

        UIManager.put("Slider.focus", bg);

    }



    public static JLabel label(String text, int size, int style, Color color) {

        JLabel label = new JLabel(text);

        label.setForeground(color);

        label.setFont(font(size, style));

        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        return label;

    }



    /** 패널 배경에 색 테두리와 안쪽 여백을 둔 기본 카드. */

    public static Border cardBorder(Color accent, int thickness, int padding) {

        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(accent, thickness),

                new EmptyBorder(padding, padding + 2, padding, padding + 2));

    }



    /** 작은 상태 표시. 배경은 강조색, 글자는 대비가 큰 색으로 고른다. */

    public static JLabel chip(String text, Color accent) {

        JLabel chip = new JLabel(text);

        chip.setOpaque(true);

        chip.setFont(font(11, Font.BOLD));

        setChip(chip, text, accent);

        return chip;

    }



    public static void setChip(JLabel chip, String text, Color accent) {

        chip.setText(text);

        chip.setBackground(accent);

        chip.setForeground(readableOn(accent));

        chip.setBorder(BorderFactory.createCompoundBorder(

                BorderFactory.createLineBorder(LINE, 1), new EmptyBorder(2, 6, 2, 6)));

    }



    /** 입력칸 공통 모양. 포커스가 있는 칸은 금색 테두리로 바뀐다. */

    public static void styleField(final JTextField field) {

        field.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 38));

        field.setPreferredSize(new java.awt.Dimension(260, 38));

        field.setFont(font(14, Font.PLAIN));

        field.setForeground(TEXT);

        field.setBackground(PANEL_LIGHT);

        field.setCaretColor(TEXT);

        field.setSelectionColor(LINE);

        field.setSelectedTextColor(TEXT);

        field.setBorder(fieldBorder(LINE));

        field.addFocusListener(new java.awt.event.FocusAdapter() {

            @Override public void focusGained(java.awt.event.FocusEvent event) { field.setBorder(fieldBorder(GOLD)); }

            @Override public void focusLost(java.awt.event.FocusEvent event) { field.setBorder(fieldBorder(LINE)); }

        });

    }



    private static Border fieldBorder(Color color) {

        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color, 2), new EmptyBorder(5, 9, 5, 9));

    }



    public static Color readableOn(Color background) {

        double base = luminance(background);

        double dark = luminance(BLACK), light = luminance(TEXT_ON_DARK);

        double darkContrast = (Math.max(base, dark) + 0.05) / (Math.min(base, dark) + 0.05);

        double lightContrast = (Math.max(base, light) + 0.05) / (Math.min(base, light) + 0.05);

        return darkContrast >= lightContrast ? BLACK : TEXT_ON_DARK;

    }



    private static double luminance(Color color) {

        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())

                + 0.0722 * linear(color.getBlue());

    }



    private static double linear(int channel) {

        double value = channel / 255d;

        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);

    }



    /**

     * 메뉴 화면 공통 머리글. 왼쪽에 작은 분류와 제목, 오른쪽에 행동 버튼을 둔다.

     * 모든 화면에서 같은 위치·크기를 사용해 화면을 옮겨도 시선이 흔들리지 않게 한다.

     */

    public static JPanel screenHeader(String eyebrow, String title, JComponent... actions) {

        JPanel header = new JPanel(new BorderLayout(16, 0));

        header.setOpaque(false);

        JPanel text = new JPanel();

        text.setOpaque(false);

        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        if (eyebrow != null) {

            text.add(label(eyebrow, 14, Font.BOLD, CampusArt.NAVY));

            text.add(Box.createVerticalStrut(3));

        }

        JLabel heading = label(title, 34, Font.BOLD, CampusArt.NAVY);

        heading.setName("screenTitle");

        text.add(heading);

        header.add(text, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        right.setOpaque(false);

        for (JComponent action : actions) right.add(action);

        JPanel align = new JPanel(new java.awt.GridBagLayout());

        align.setOpaque(false);

        align.add(right);

        header.add(align, BorderLayout.EAST);

        return header;

    }



    private static boolean isThemeColor(Color color) {

        return BG.equals(color) || PANEL.equals(color) || PANEL_LIGHT.equals(color)

                || TEXT.equals(color) || TEXT_SUB.equals(color) || TEXT_MUTED.equals(color)

                || GOLD.equals(color) || MINT.equals(color) || CORAL.equals(color)

                || LINE.equals(color) || BLACK.equals(color) || TEXT_ON_DARK.equals(color);

    }



    private static String chooseFont() {

        String[] preferred = { "NanumSquareRound", "NanumSquareRoundOTF", "NeoDunggeunmo", "DungGeunMo", "Galmuri11", "Malgun Gothic" };

        String[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment()

                .getAvailableFontFamilyNames();

        for (String candidate : preferred) {

            for (String name : installed) {

                if (candidate.equalsIgnoreCase(name)) return name;

            }

        }

        return Font.DIALOG;

    }

}
