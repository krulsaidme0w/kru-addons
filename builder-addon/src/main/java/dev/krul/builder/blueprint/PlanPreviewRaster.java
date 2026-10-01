package dev.krul.builder.blueprint;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;

/** Small deterministic isometric schematic using exact block inventory-model thumbnails as appearance cues. */
public final class PlanPreviewRaster {
    public static final int WIDTH = 256, HEIGHT = 224, MAX_BYTES = 128 * 1024;
    private PlanPreviewRaster() {}

    public static byte[] render(PlanPreviewModel.Projection projection, Map<String, BufferedImage> inventoryModels) throws IOException {
        Objects.requireNonNull(projection); Objects.requireNonNull(inventoryModels);
        var ids = new LinkedHashSet<String>();
        projection.cells().forEach(cell -> ids.add(cell.state().id()));
        if (ids.size() > 32) throw new IOException("Preview has more than 32 distinct block models");
        for (String id : ids) if (!inventoryModels.containsKey(id)) throw new IOException("Missing inventory model for " + id);

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(18, 23, 29)); g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(new Color(50, 60, 72)); g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);
            int sx = projection.bounds().max().x() - projection.bounds().min().x() + 1;
            int sy = projection.bounds().max().y() - projection.bounds().min().y() + 1;
            int sz = projection.bounds().max().z() - projection.bounds().min().z() + 1;
            double fitX = 220d / (sx + sz + 1d);
            double fitY = 158d / ((sx + sz) * 0.5d + sy + 1d);
            double unit = Math.min(12d, Math.min(fitX, fitY));
            if (unit < 0.65) throw new IOException("Selected bounds are too large for a readable preview");
            double centerX = WIDTH / 2d;
            double floor = 174d - ((sx + sz) * unit * 0.25d);
            int side = Math.max(1, (int) Math.round(unit * 0.72));
            var colors = new HashMap<String, Color>();
            for (String id : ids) colors.put(id, average(inventoryModels.get(id)));
            for (var cell : projection.cells()) {
                int x = cell.relativePosition().x(), y = cell.relativePosition().y(), z = cell.relativePosition().z();
                double px = centerX + (x - z) * unit * 0.5d;
                double py = floor - (x + z) * unit * 0.25d - y * unit * 0.68d;
                Color base = colors.get(cell.state().id());
                int half = Math.max(1, side / 2);
                Polygon top = new Polygon(new int[]{(int) px, (int) px + half, (int) px, (int) px - half},
                    new int[]{(int) py - half, (int) py, (int) py + half, (int) py}, 4);
                g.setColor(base.brighter()); g.fillPolygon(top);
                g.setColor(base.darker()); g.fillPolygon(new Polygon(new int[]{(int) px, (int) px + half, (int) px + half, (int) px},
                    new int[]{(int) py + half, (int) py, (int) py + side, (int) py + side + half}, 4));
                g.setColor(base.darker().darker()); g.fillPolygon(new Polygon(new int[]{(int) px, (int) px - half, (int) px - half, (int) px},
                    new int[]{(int) py + half, (int) py, (int) py + side, (int) py + side + half}, 4));
                BufferedImage icon = inventoryModels.get(cell.state().id());
                int iconSize = Math.max(2, Math.min(16, (int) Math.round(unit * 1.25)));
                g.drawImage(icon, (int) px - iconSize / 2, (int) py - iconSize / 2, iconSize, iconSize, null);
            }
            g.setColor(new Color(10, 14, 18, 235)); g.fillRect(0, 190, WIDTH, 34);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            int x = 5;
            for (String id : ids.stream().limit(6).toList()) {
                BufferedImage icon = inventoryModels.get(id);
                g.drawImage(icon, x, 192, 20, 20, null);
                x += 22;
                g.setColor(Color.WHITE); g.drawString(shortId(id, 17), x, 205);
                x += 60;
                if (x > WIDTH - 45) break;
            }
            g.setColor(new Color(185, 198, 210)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
            g.drawString("ISOMETRIC SCHEMATIC · inventory models · not a live render", 5, 219);
        } finally { g.dispose(); }
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG encoder unavailable");
        byte[] result = bytes.toByteArray();
        if (result.length > MAX_BYTES) throw new IOException("Preview image exceeds the byte limit");
        return result;
    }

    private static Color average(BufferedImage image) {
        long red = 0, green = 0, blue = 0, count = 0;
        int stepX = Math.max(1, image.getWidth() / 16), stepY = Math.max(1, image.getHeight() / 16);
        for (int y = 0; y < image.getHeight(); y += stepY) for (int x = 0; x < image.getWidth(); x += stepX) {
            int argb = image.getRGB(x, y); if ((argb >>> 24) < 24) continue;
            red += (argb >>> 16) & 255; green += (argb >>> 8) & 255; blue += argb & 255; count++;
        }
        return count == 0 ? Color.GRAY : new Color((int) (red / count), (int) (green / count), (int) (blue / count));
    }
    private static String shortId(String id, int max) { return id.length() <= max ? id : "…" + id.substring(id.length() - max + 1); }
}
