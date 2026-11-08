package kr.ac.jbnu.se.tetris.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.ImageIcon;

/** 자산 조회와 누락 이미지 대체 동작을 확인 */
public final class AssetTest {
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        List<String> warnings = new ArrayList<>();
        Logger logger = Logger.getLogger(AssetManager.class.getName());
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(capture);
        try {
            AssetManager assets = new AssetManager();
            ImageIcon original = assets.getIcon("character.default", 96, 96);
            check(original.getIconWidth() == 96 && original.getIconHeight() == 96, "classpath icon scaled");
            check(warnings.isEmpty(), "manifest and real placeholder loaded without warnings");
            check(original == assets.getIcon("character.default", 96, 96), "scaled icon cached");
            ImageIcon fallback = assets.getIcon("not.registered", 40, 60);
            assets.getIcon("not.registered", 20, 20);
            check(fallback.getIconWidth() == 40 && fallback.getIconHeight() == 60, "missing asset fallback");
            check(warnings.size() == 1, "missing ID warning emitted once");
            boolean rejected = false;
            try { assets.getIcon("character.default", 0, 10); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid dimensions rejected");
            System.out.println("PASS AssetTest: 6 checks");
        } finally { logger.removeHandler(capture); }
    }
}
