package com.pipeline.tasks.analysis;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class IntroOutroDetector implements Task {

    private static final int    SAMPLE_RATE         = 8000;  // Hz — low rate is enough for energy analysis
    private static final int    HALF_WIN            = 5;     // rolling window half-width in seconds

    // Intro detection thresholds
    private static final double INTRO_SEARCH_FRAC   = 0.25;  // only search first 25 % of video
    private static final double INTRO_DROP_VS_BASE  = 1.4;   // energy must drop below baseline * this
    private static final double INTRO_DROP_VS_START = 0.55;  // or drop below start energy * this
    private static final double INTRO_SPIKE_MULT    = 2.5;   // variance must spike beyond baseline * this
    private static final int    INTRO_FALLBACK_SECS = 90;    // default intro length if no transition found

    // Outro detection thresholds
    private static final double OUTRO_SEARCH_FRAC   = 0.70;  // search the last 30 % of the video
    private static final double OUTRO_SILENCE_FLOOR = 0.005; // RMS below this is considered silence
    private static final double OUTRO_VAR_MULT      = 0.80;  // std-dev must be below global * this
    private static final int    OUTRO_MIN_SPAN_SECS = 10;    // sustained region must last at least this long
    private static final int    OUTRO_FALLBACK_SECS = 300;   // default: 5 min before end of video

    // Task entry point

    @Override
    public void run(PipelineContext ctx) throws Exception {
        System.out.println("    Piping raw audio from FFmpeg...");
        float[] energy = extractEnergyPerSecond(ctx.getInputPath());
        System.out.println("    Energy profile: " + energy.length + " seconds");

        int introEndSec   = detectIntroEnd(energy);
        int outroStartSec = detectOutroStart(energy);

        ctx.setResult("intro_end_timestamp",   fmt(introEndSec));
        ctx.setResult("outro_start_timestamp", fmt(outroStartSec));

        System.out.println("    intro_end=" + fmt(introEndSec)
                         + "  outro_start=" + fmt(outroStartSec));
    }

    // Audio extraction

    /**
     * Streams raw 32-bit float PCM directly from FFmpeg via stdout pipe.
     * stderr is discarded to prevent log contamination of the PCM data.
     * Returns one RMS value per second of audio.
     */
    private float[] extractEnergyPerSecond(String inputPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-i", inputPath,
            "-ac", "1", "-ar", String.valueOf(SAMPLE_RATE),
            "-f", "f32le", "-vn", "pipe:1"
        );
        pb.redirectError(ProcessBuilder.Redirect.DISCARD); // keep stdout clean for PCM
        Process proc = pb.start();
        byte[] raw = proc.getInputStream().readAllBytes();
        proc.waitFor();

        int sampleCount = raw.length / 4;
        float[] samples = new float[sampleCount];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples);

        int seconds = sampleCount / SAMPLE_RATE;
        float[] energy = new float[seconds];
        for (int i = 0; i < seconds; i++) {
            double sum = 0;
            int base = i * SAMPLE_RATE;
            for (int j = base; j < base + SAMPLE_RATE; j++) sum += samples[j] * samples[j];
            energy[i] = (float) Math.sqrt(sum / SAMPLE_RATE);
        }
        return energy;
    }

    // Rolling statistics

    private double[] rollingMean(float[] v) {
        int n = v.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - HALF_WIN), hi = Math.min(n, i + HALF_WIN + 1);
            double s = 0;
            for (int j = lo; j < hi; j++) s += v[j];
            out[i] = s / (hi - lo);
        }
        return out;
    }

    private double[] rollingStd(float[] v, double[] mean) {
        int n = v.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - HALF_WIN), hi = Math.min(n, i + HALF_WIN + 1);
            double s = 0;
            for (int j = lo; j < hi; j++) { double d = v[j] - mean[i]; s += d * d; }
            out[i] = Math.sqrt(s / (hi - lo));
        }
        return out;
    }

    // Detection logic

    /**
     * Intro ends where rolling energy drops or variance spikes relative to
     * the baseline (2nd quarter of the video = post-intro dialogue region).
     */
    private int detectIntroEnd(float[] energy) {
        int n = energy.length;
        double[] means = rollingMean(energy);
        double[] stds  = rollingStd(energy, means);

        int searchEnd = Math.max(HALF_WIN * 2 + 1, (int) (n * INTRO_SEARCH_FRAC));
        int bStart = (int) (n * INTRO_SEARCH_FRAC), bEnd = n / 2;
        double baseMean = 0, baseStd = 0;
        for (int i = bStart; i < bEnd; i++) { baseMean += energy[i]; baseStd += stds[i]; }
        baseMean /= (bEnd - bStart);
        baseStd  /= (bEnd - bStart);

        System.out.printf("    [INTRO] baseline energy=%.4f std=%.4f search_end=%ds%n",
                          baseMean, baseStd, searchEnd);

        for (int i = HALF_WIN; i < searchEnd; i++) {
            boolean dropped = means[i] < Math.max(baseMean * INTRO_DROP_VS_BASE, means[0] * INTRO_DROP_VS_START);
            boolean spiked  = stds[i] > baseStd * INTRO_SPIKE_MULT && i > HALF_WIN * 2;
            if (dropped || spiked) {
                System.out.println("    [INTRO] Transition at t=" + i + "s");
                return i;
            }
        }
        int fb = Math.min(INTRO_FALLBACK_SECS, searchEnd);
        System.out.println("    [INTRO] No clear transition — defaulting to " + fb + "s");
        return fb;
    }

    /**
     * Outro starts at the first sustained region (≥10 s) of non-silent,
     * low-variance energy in the last 30 % of the video.
     */
    private int detectOutroStart(float[] energy) {
        int n = energy.length;
        double[] means = rollingMean(energy);
        double[] stds  = rollingStd(energy, means);

        double globalStd = 0;
        for (double s : stds) globalStd += s;
        globalStd /= stds.length;

        int searchStart = (int) (n * OUTRO_SEARCH_FRAC);

        System.out.printf("    [OUTRO] Searching from t=%ds  globalStd=%.4f%n", searchStart, globalStd);

        for (int i = searchStart; i < n - OUTRO_MIN_SPAN_SECS; i++) {
            double segMean = 0, segStd = 0;
            for (int j = i; j < i + OUTRO_MIN_SPAN_SECS; j++) segMean += energy[j];
            segMean /= OUTRO_MIN_SPAN_SECS;
            for (int j = i; j < i + OUTRO_MIN_SPAN_SECS; j++) { double d = energy[j] - segMean; segStd += d * d; }
            segStd = Math.sqrt(segStd / OUTRO_MIN_SPAN_SECS);
            if (segMean > OUTRO_SILENCE_FLOOR && segStd < globalStd * OUTRO_VAR_MULT) {
                System.out.println("    [OUTRO] Music-like region at t=" + i + "s");
                return i;
            }
        }
        int fb = Math.max(0, n - OUTRO_FALLBACK_SECS);
        System.out.println("    [OUTRO] No clear outro — defaulting to " + fb + "s");
        return fb;
    }

    // Formatting

    private String fmt(int seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    @Override
    public String getName() { return "IntroOutroDetector"; }
}
