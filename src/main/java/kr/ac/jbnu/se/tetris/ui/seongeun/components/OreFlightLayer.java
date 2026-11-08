package kr.ac.jbnu.se.tetris.ui.seongeun.components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** 광석이 깨진 실제 보드 칸에서 획득 슬롯까지 날아가는 비차단 화면 효과. */
public final class OreFlightLayer extends JComponent {
    private static final int DURATION = 780;
    private final List<Flight> flights = new ArrayList<Flight>();
    private final Timer timer = new Timer(16, event -> advance());

    public OreFlightLayer() {
        setOpaque(false);
        timer.setCoalesce(true);
    }

    /** 덮개가 버튼 입력을 가로채지 않는다. */
    @Override public boolean contains(int x, int y) { return false; }
    @Override public void removeNotify() { timer.stop(); flights.clear(); super.removeNotify(); }

    public void launch(Point source, Point target, int targetWidth, int targetHeight) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> launch(source, target, targetWidth, targetHeight));
            return;
        }
        if (source == null || target == null) return;
        if (flights.size() >= 8) flights.remove(0);
        flights.add(new Flight(new Point(source), new Point(target),
                Math.max(0, targetWidth), Math.max(0, targetHeight), System.currentTimeMillis()));
        if (!timer.isRunning()) timer.start();
        repaint();
    }

    public void reset() { flights.clear(); timer.stop(); repaint(); }

    private void advance() {
        long now = System.currentTimeMillis();
        for (Iterator<Flight> iterator = flights.iterator(); iterator.hasNext(); )
            if (now - iterator.next().started >= DURATION) iterator.remove();
        if (flights.isEmpty()) timer.stop();
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            long now = System.currentTimeMillis();
            for (Flight flight : flights) paintFlight(g, flight, now - flight.started);
        } finally { g.dispose(); }
    }

    private static void paintFlight(Graphics2D g, Flight flight, long age) {
        if (age < 0 || age > DURATION) return;
        int sx = flight.source.x, sy = flight.source.y;
        if (age < 220) {
            float fade = 1f - age / 220f;
            int radius = 5 + Math.round(age / 9f);
            g.setColor(new Color(255, 183, 60, Math.round(210 * fade)));
            g.drawOval(sx - radius, sy - radius, radius * 2, radius * 2);
            g.setColor(new Color(255, 247, 185, Math.round(240 * fade)));
            g.drawOval(sx - Math.max(2, radius / 2), sy - Math.max(2, radius / 2),
                    Math.max(4, radius), Math.max(4, radius));
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2 * i / 12 + i * 0.15;
                int px = sx + (int) Math.round(Math.cos(angle) * radius);
                int py = sy + (int) Math.round(Math.sin(angle) * radius);
                g.setColor(i % 3 == 0 ? new Color(255, 248, 196, Math.round(240 * fade))
                        : new Color(235, 165, 71, Math.round(200 * fade)));
                g.fillRect(px, py, i % 3 == 0 ? 5 : 3, i % 3 == 0 ? 5 : 3);
            }
            if (age < 90) {
                g.setColor(new Color(255, 252, 221, Math.round(235 * (1f - age / 90f))));
                g.fillRect(sx - 5, sy - 5, 11, 11);
            }
        }
        if (age >= 70 && age < 560) {
            float t = Math.max(0f, Math.min(1f, (age - 70f) / 490f));
            float eased = 1f - (1f - t) * (1f - t) * (1f - t);
            int x = Math.round(sx + (flight.target.x - sx) * eased);
            int y = Math.round(sy + (flight.target.y - sy) * eased
                    - (float) Math.sin(t * Math.PI) * Math.min(70, Math.abs(flight.target.x - sx) / 3 + 20));
            int trailX = Math.round(sx + (flight.target.x - sx) * Math.max(0f, eased - 0.07f));
            int trailY = Math.round(sy + (flight.target.y - sy) * Math.max(0f, eased - 0.07f)
                    - (float) Math.sin(Math.max(0f, t - 0.07f) * Math.PI) * 40);
            g.setColor(new Color(255, 176, 60, 110));
            g.drawLine(trailX - 2, trailY - 2, x - 2, y - 2);
            g.setColor(new Color(255, 237, 144, 200));
            g.drawLine(trailX, trailY, x, y);
            g.setColor(new Color(255, 205, 90, 112));
            g.fillOval(x - 14, y - 14, 28, 28);
            g.setColor(new Color(255, 251, 214));
            g.fillPolygon(new int[] {x, x + 7, x, x - 7}, new int[] {y - 9, y, y + 9, y}, 4);
            g.setColor(new Color(156, 91, 36));
            g.drawPolygon(new int[] {x, x + 7, x, x - 7}, new int[] {y - 9, y, y + 9, y}, 4);
        }
        if (age >= 500) {
            float t = Math.min(1f, (age - 500f) / 280f);
            int radius = 8 + Math.round(25 * t);
            g.setColor(new Color(255, 214, 102, Math.round(210 * (1f - t))));
            g.drawRect(flight.target.x - radius, flight.target.y - radius,
                    radius * 2, radius * 2);
            g.drawRect(flight.target.x - Math.max(4, radius / 2),
                    flight.target.y - Math.max(4, radius / 2),
                    Math.max(8, radius), Math.max(8, radius));
            if (t < 0.35f) {
                g.setColor(new Color(255, 244, 180, Math.round(130 * (1f - t / 0.35f))));
                g.fillRect(flight.target.x - 8, flight.target.y - 8, 16, 16);
            }
            if (flight.targetWidth > 0 && flight.targetHeight > 0) {
                int inset = Math.round(5 * t);
                g.setColor(new Color(255, 251, 208, Math.round(245 * (1f - t))));
                g.drawRect(flight.target.x - flight.targetWidth / 2 - inset,
                        flight.target.y - flight.targetHeight / 2 - inset,
                        flight.targetWidth + inset * 2, flight.targetHeight + inset * 2);
            }
        }
    }

    private static final class Flight {
        final Point source, target;
        final int targetWidth, targetHeight;
        final long started;
        Flight(Point source, Point target, int targetWidth, int targetHeight, long started) {
            this.source = source; this.target = target;
            this.targetWidth = targetWidth; this.targetHeight = targetHeight;
            this.started = started;
        }
    }
}
