package com.pipeline.tasks.analysis;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SceneIndexer implements Task {

    private static final int    SAMPLE_RATE  = 8000;  // Hz for audio energy extraction
    private static final int    THUMB_W      = 160;   // frame thumbnail width for motion computation
    private static final int    THUMB_H      = 90;    // frame thumbnail height for motion computation

    // Classification thresholds (normalised 0–1 motion; RMS audio energy)
    private static final double MOTION_HIGH  = 0.06;  // above → action candidate
    private static final double MOTION_LOW   = 0.03;  // below → static scene
    private static final double AUDIO_HIGH   = 0.04;  // above → energetic audio
    private static final double AUDIO_SILENT = 0.008; // below → near-silence

    private final int intervalSeconds;

    public SceneIndexer()                    { this(5); }
    public SceneIndexer(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }

    // ── Task entry point ─────────────────────────────────────────────────────

    @Override
    public void run(PipelineContext ctx) throws Exception {
        double duration = getDuration(ctx.getInputPath());
        System.out.printf("    Duration=%.1fs  interval=%ds%n", duration, intervalSeconds);

        System.out.println("    Extracting audio energy (pipe)...");
        float[] energyPerSec = extractEnergyPerSecond(ctx.getInputPath());

        // Load speech timestamps from transcript if available
        Set<Integer> speechSeconds = loadSpeechTimes(ctx);

        // Segment boundaries
        int segCount = (int) (duration / intervalSeconds);
        List<Map<String, String>> segments = new ArrayList<>();
        Map<String, String> flatIndex = new LinkedHashMap<>();

        Path tmpDir = Files.createTempDirectory("scene_idx_");
        try {
            int[][] prevGray = null;

            for (int i = 0; i < segCount; i++) {
                double start  = (double) i * intervalSeconds;
                double end    = Math.min(start + intervalSeconds, duration);
                double midPt  = start + intervalSeconds / 2.0;

                // Extract one frame at the segment midpoint
                Path framePath = tmpDir.resolve(String.format("f_%05d.jpg", i));
                int[][] curGray = extractGrayFrame(ctx.getInputPath(), midPt, framePath.toString());

                double motion = (curGray != null && prevGray != null)
                        ? computeMotion(prevGray, curGray) : 0.0;

                double audio  = segmentEnergy(energyPerSec, start, end);
                boolean speech = hasSpeech(speechSeconds, start, end);

                String label = classify(motion, audio, speech);

                Map<String, String> seg = new LinkedHashMap<>();
                seg.put("start", fmt((int) start));
                seg.put("end", fmt((int) end));
                seg.put("type", label);
                segments.add(seg);
                flatIndex.put(fmt((int) start), label);

                System.out.printf("    [%s – %s]  %-20s motion=%.4f  audio=%.4f  speech=%s%n",
                        fmt((int) start), fmt((int) end), label, motion, audio, speech);

                if (curGray != null) prevGray = curGray;
            }
        } finally {
            Files.walk(tmpDir)
                 .sorted(Comparator.reverseOrder())
                 .forEach(p -> p.toFile().delete());
        }

        ctx.setResult("scene_index",    flatIndex);  // used by SceneComplexity
        ctx.setResult("scene_segments", segments);   // full detail for ManifestBuilder
        System.out.println("    Scene index: " + segments.size() + " segments classified.");
    }

    // Classification

    private String classify(double motion, double audio, boolean speech) {
        if (motion > MOTION_HIGH && audio > AUDIO_HIGH)      return "action";
        if (speech || (motion < MOTION_LOW && audio > AUDIO_SILENT)) return "dialogue";
        return "establishing_shot";
    }

    // Audio energy

    private float[] extractEnergyPerSecond(String inputPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-i", inputPath,
            "-ac", "1", "-ar", String.valueOf(SAMPLE_RATE),
            "-f", "f32le", "-vn", "pipe:1"
        );
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process proc = pb.start();
        byte[] raw = proc.getInputStream().readAllBytes();
        proc.waitFor();

        int n = raw.length / 4;
        float[] samples = new float[n];
        ByteBuffer.wrap(raw, 0, n * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples);

        int seconds = n / SAMPLE_RATE;
        float[] energy = new float[seconds];
        for (int i = 0; i < seconds; i++) {
            double sum = 0;
            int base = i * SAMPLE_RATE;
            for (int j = base; j < base + SAMPLE_RATE; j++) sum += samples[j] * samples[j];
            energy[i] = (float) Math.sqrt(sum / SAMPLE_RATE);
        }
        return energy;
    }

    private double segmentEnergy(float[] energyPerSec, double start, double end) {
        int lo = (int) start, hi = Math.min((int) end, energyPerSec.length);
        if (lo >= hi) return 0.0;
        double sum = 0;
        for (int i = lo; i < hi; i++) sum += energyPerSec[i];
        return sum / (hi - lo);
    }

    // Frame extraction & motion

    /**
     * Seeks to timestamp, grabs one frame, returns a THUMB_W×THUMB_H grayscale array.
     * Returns null if extraction fails (first segment has no previous frame anyway).
     */
    private int[][] extractGrayFrame(String inputPath, double timestamp, String outPath) {
        try {
            new ProcessBuilder(
                "ffmpeg", "-y",
                "-ss", String.valueOf(timestamp),
                "-i", inputPath,
                "-frames:v", "1",
                "-q:v", "6",
                outPath
            ).redirectError(ProcessBuilder.Redirect.DISCARD)
             .start().waitFor();

            File f = new File(outPath);
            if (!f.exists()) return null;
            BufferedImage img = ImageIO.read(f);
            return img != null ? toGray(img) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Normalised mean absolute difference between two grayscale frames. */
    private double computeMotion(int[][] a, int[][] b) {
        long sum = 0;
        for (int y = 0; y < THUMB_H; y++)
            for (int x = 0; x < THUMB_W; x++)
                sum += Math.abs(a[y][x] - b[y][x]);
        return (double) sum / (THUMB_W * THUMB_H * 255.0);
    }

    /** Nearest-neighbour downsample to THUMB_W×THUMB_H grayscale. */
    private int[][] toGray(BufferedImage src) {
        int[][] out = new int[THUMB_H][THUMB_W];
        double sx = (double) src.getWidth()  / THUMB_W;
        double sy = (double) src.getHeight() / THUMB_H;
        for (int y = 0; y < THUMB_H; y++) {
            for (int x = 0; x < THUMB_W; x++) {
                int argb = src.getRGB((int) (x * sx), (int) (y * sy));
                out[y][x] = (int) (0.299 * ((argb >> 16) & 0xFF)
                                 + 0.587 * ((argb >>  8) & 0xFF)
                                 + 0.114 * ( argb        & 0xFF));
            }
        }
        return out;
    }

    // Speech map

    private Set<Integer> loadSpeechTimes(PipelineContext ctx) {
        Set<Integer> times = new HashSet<>();
        if (!ctx.hasResult("transcript_path")) return times;
        try {
            String path = (String) ctx.getResult("transcript_path");
            for (String line : Files.readAllLines(Path.of(path))) {
                if (line.startsWith("[") && line.length() >= 9) {
                    String[] parts = line.substring(1, 9).split(":");
                    if (parts.length == 3)
                        times.add(Integer.parseInt(parts[0]) * 3600
                                + Integer.parseInt(parts[1]) * 60
                                + Integer.parseInt(parts[2]));
                }
            }
        } catch (Exception ignored) {}
        return times;
    }

    private boolean hasSpeech(Set<Integer> speechSecs, double start, double end) {
        for (int t : speechSecs) if (t >= start && t < end) return true;
        return false;
    }

    // Utilities

    private double getDuration(String inputPath) throws Exception {
        String out = ProcessRunner.run(List.of(
            "ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", inputPath
        ));
        int idx = out.indexOf("\"duration\"");
        int col = out.indexOf(":", idx) + 1;
        int s = col;
        while (s < out.length() && (out.charAt(s) == ' ' || out.charAt(s) == '"')) s++;
        int e = s;
        while (e < out.length() && out.charAt(e) != '"' && out.charAt(e) != ',') e++;
        return Double.parseDouble(out.substring(s, e).trim());
    }

    private String fmt(int seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    @Override
    public String getName() { return "SceneIndexer"; }
}
