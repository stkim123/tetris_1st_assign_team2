package kr.ac.jbnu.se.tetris.ui.seongeun;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import kr.ac.jbnu.se.tetris.core.PieceType;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.ConcreteBlockSkin;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;

/** 실제 게임 셀 크기에서 광석 표시가 다른 콘크리트 블록과 구분되는지 보는 렌더 도구. */
public final class OreBlockRenderSmoke {
    private OreBlockRenderSmoke() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Output PNG path required");
        BufferedImage preview = new BufferedImage(520, 330, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preview.createGraphics();
        try {
            g.setColor(new Color(0x101B21));
            g.fillRect(0, 0, 520, 330);
            drawBoard(g, 24, 32, 21);
            drawBoard(g, 285, 18, 28);
        } finally { g.dispose(); }
        ImageIO.write(preview, "png", new File(args[0]));
        System.out.println("PASS OreBlockRenderSmoke " + args[0]);
    }

    private static void drawBoard(Graphics2D g, int left, int top, int cell) {
        g.setFont(UniversityPixelTheme.font(14, Font.BOLD));
        g.setColor(UniversityPixelTheme.TEXT);
        g.drawString(cell + " px GAME CELL", left, top - 9);
        g.setColor(new Color(0x091E2B));
        g.fillRect(left, top, cell * 8, cell * 10);
        g.setColor(new Color(0x254558));
        for (int x = 0; x <= 8; x++) g.drawLine(left + x * cell, top, left + x * cell, top + cell * 10);
        for (int y = 0; y <= 10; y++) g.drawLine(left, top + y * cell, left + cell * 8, top + y * cell);
        PieceType[] types = { PieceType.Z, PieceType.Z, PieceType.J, PieceType.J,
                PieceType.O, PieceType.O, PieceType.S, PieceType.S };
        for (int row = 7; row < 10; row++) for (int x = 0; x < 8; x++) {
            if (row == 7 && (x == 1 || x == 4 || x == 6)) continue;
            ConcreteBlockSkin.paint(g, left + x * cell, top + row * cell, cell,
                    types[x], false, row == 8 && x == 4);
        }
        ConcreteBlockSkin.paint(g, left + 3 * cell, top + 2 * cell, cell, PieceType.T, false, false);
        ConcreteBlockSkin.paint(g, left + 4 * cell, top + 2 * cell, cell, PieceType.T, false, true);
        ConcreteBlockSkin.paint(g, left + 5 * cell, top + 2 * cell, cell, PieceType.T, false, false);
        ConcreteBlockSkin.paint(g, left + 4 * cell, top + 3 * cell, cell, PieceType.T, false, false);
    }
}
