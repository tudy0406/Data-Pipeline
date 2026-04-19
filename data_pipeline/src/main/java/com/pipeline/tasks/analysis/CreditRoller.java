package com.pipeline.tasks.analysis;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class CreditRoller implements Task {

    private static final int    THUMB_W              = 320;   // analysis frame width (pixels)
    private static final int    THUMB_H              = 180;   // analysis frame height (pixels)
    private static final double THRESHOLD            = 0.45;  // minimum credit score to count as credits
    private static final int    CONSECUTIVE          = 3;     // consecutive frames needed to confirm credits

    // Portion of the video to search (credits only appear near the end)
    private static final double SEARCH_START_FRAC    = 0.70;  // start scanning at 70 % of duration
    private static final int    FALLBACK_SECS_BEFORE_END = 300; // fallback: 5 min before end

    // Score metric normalisation denominators (tuned to typical credit frame characteristics)
    private static final double CONTRAST_NORM        = 80.0;  // std-dev of ~80 typical for credit frames
    private static final double EDGE_DENSITY_NORM    = 30.0;  // gradient mean of ~30 for text-heavy frames
    private static final double BRIGHT_RATIO_NORM    = 0.15;  // ~15 % bright pixels for white text
    private static final int    BRIGHT_PIXEL_FLOOR   = 200;   // pixel intensity threshold for "bright"

    // Score weights — must sum to 1.0
    private static final double WEIGHT_CONTRAST      = 0.40;
    private static final double WEIGHT_EDGE_DENSITY  = 0.40;
    private static final double WEIGHT_BRIGHT_RATIO  = 0.20;

    // Task entry point

    @Override
    public void run(PipelineContext ctx) throws Exception {
        double duration  = getDuration(ctx.getInputPath());
        double startTime = duration * SEARCH_START_FRAC;
        System.out.printf("    Duration=%.1fs  scanning credits from t=%.1fs%n", duration, startTime);

        Path tmpDir = Files.createTempDirectory("credits_");
        try {
            ProcessRunner.run(List.of(
                "ffmpeg", "-y",
                "-ss", String.valueOf(startTime),
                "-i", ctx.getInputPath(),
                "-vf", "fps=1",
                "-q:v", "4",
                tmpDir.resolve("frame_%05d.jpg").toString()
            ));

            File[] files = tmpDir.toFile().listFiles((d, n) -> n.endsWith(".jpg"));
            if (files == null || files.length == 0) {
                throw new Exception("No frames extracted for credit detection.");
            }
            Arrays.sort(files, Comparator.comparing(File::getName));
            System.out.println("    " + files.length + " frames extracted");

            double creditsTs = findCredits(files, startTime);
            if (creditsTs < 0) {
                creditsTs = Math.max(0, duration - FALLBACK_SECS_BEFORE_END);
                System.out.println("    No credits detected — defaulting to " + fmt(creditsTs));
            }

            ctx.setResult("credits_start_timestamp", fmt(creditsTs));
            System.out.println("    credits_start=" + fmt(creditsTs));

        } finally {
            Files.walk(tmpDir)
                 .sorted(Comparator.reverseOrder())
                 .forEach(p -> p.toFile().delete());
        }
    }

    // Credit detection

    private double findCredits(File[] frames, double startTime) throws Exception {
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < frames.length; i++) {
            double score = creditScore(frames[i]);
            scores.add(score);
            System.out.printf("    t=%6.1fs  score=%.3f%n", startTime + i, score);
        }

        for (int i = 0; i <= scores.size() - CONSECUTIVE; i++) {
            boolean run = true;
            for (int j = i; j < i + CONSECUTIVE; j++) {
                if (scores.get(j) < THRESHOLD) { run = false; break; }
            }
            if (run) {
                double ts = startTime + i;
                System.out.printf("    Credits detected at t=%.1fs%n", ts);
                return ts;
            }
        }
        return -1;
    }

    // Frame metrics

    /**
     * Loads a frame, converts to grayscale, and computes:
     *   contrast = std-dev of pixel intensities
     *   edgeDensity = mean gradient magnitude (Sobel-like)
     *   brightRatio = fraction of pixels > 200  (white text on dark)
     */
    private double creditScore(File f) throws Exception {
        BufferedImage src = ImageIO.read(f);
        if (src == null) return 0.0;

        int[][] gray = toGray(src, THUMB_W, THUMB_H);
        int n = THUMB_W * THUMB_H;

        // Contrast
        double mean = 0;
        for (int[] row : gray) for (int p : row) mean += p;
        mean /= n;
        double var = 0;
        for (int[] row : gray) for (int p : row) { double d = p - mean; var += d * d; }
        double contrast = Math.sqrt(var / n);

        // Edge density (Sobel horizontal + vertical, inner pixels only)
        double edgeSum = 0;
        for (int y = 1; y < THUMB_H - 1; y++) {
            for (int x = 1; x < THUMB_W - 1; x++) {
                int gx = gray[y][x + 1] - gray[y][x - 1];
                int gy = gray[y + 1][x] - gray[y - 1][x];
                edgeSum += Math.sqrt(gx * gx + gy * gy);
            }
        }
        double edgeDensity = edgeSum / n;

        // Bright pixel ratio
        long bright = 0;
        for (int[] row : gray) for (int p : row) if (p > BRIGHT_PIXEL_FLOOR) bright++;
        double brightRatio = (double) bright / n;

        double c = Math.min(contrast    / CONTRAST_NORM,     1.0);
        double e = Math.min(edgeDensity / EDGE_DENSITY_NORM, 1.0);
        double b = Math.min(brightRatio / BRIGHT_RATIO_NORM, 1.0);
        return c * WEIGHT_CONTRAST + e * WEIGHT_EDGE_DENSITY + b * WEIGHT_BRIGHT_RATIO;
    }

    // Utilities

    /** Samples a BufferedImage into a w×h grayscale int[][] using nearest-neighbour. */
    private int[][] toGray(BufferedImage src, int w, int h) {
        int[][] out = new int[h][w];
        double sx = (double) src.getWidth()  / w;
        double sy = (double) src.getHeight() / h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB((int) (x * sx), (int) (y * sy));
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                out[y][x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return out;
    }

    private double getDuration(String inputPath) throws Exception {
        String out = ProcessRunner.run(List.of(
            "ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", inputPath
        ));
        int idx = out.indexOf("\"duration\"");
        int col = out.indexOf(":", idx) + 1;
        int start = col;
        while (start < out.length() && (out.charAt(start) == ' ' || out.charAt(start) == '"')) start++;
        int end = start;
        while (end < out.length() && out.charAt(end) != '"' && out.charAt(end) != ',') end++;
        return Double.parseDouble(out.substring(start, end).trim());
    }

    private String fmt(double seconds) {
        int s = (int) seconds;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    @Override
    public String getName() { return "CreditRoller"; }
}
