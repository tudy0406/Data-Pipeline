package com.pipeline.tasks.visuals;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Transcoder implements Task {

    private static final int[][] RESOLUTIONS = {
        {3840, 2160},  // 4K
        {1920, 1080},  // 1080p
        {1280, 720}    // 720p
    };
    private static final String[] RES_NAMES = {"4k", "1080p", "720p"};

    @Override
    public void run(PipelineContext ctx) throws Exception {
        Path videoDir = ctx.getOutputDir().resolve("video");
        Path h264Dir = videoDir.resolve("h264");
        Path vp9Dir = videoDir.resolve("vp9");
        Path hevcDir = videoDir.resolve("hevc");

        Files.createDirectories(h264Dir);
        Files.createDirectories(vp9Dir);
        Files.createDirectories(hevcDir);

        // Read CRF values from context — set by SceneComplexity
        int h264Crf = ctx.hasResult("h264_crf") ? (int) ctx.getResult("h264_crf") : 20;
        int vp9Crf = ctx.hasResult("vp9_crf") ? (int) ctx.getResult("vp9_crf")  : 35;
        int hevcCrf = ctx.hasResult("hevc_crf") ? (int) ctx.getResult("hevc_crf") : 26;

        String input = ctx.getInputPath();

        List<String> h264Paths = new java.util.ArrayList<>();
        List<String> vp9Paths  = new java.util.ArrayList<>();
        List<String> hevcPaths = new java.util.ArrayList<>();

        for (int i = 0; i < RESOLUTIONS.length; i++) {
            int w = RESOLUTIONS[i][0];
            int h = RESOLUTIONS[i][1];
            String name  = RES_NAMES[i];
            String scale = "scale=" + w + ":" + h;

            // H264 / MP4
            Path h264Out = h264Dir.resolve(name + "_h264.mp4");
            System.out.println("    [H264] Encoding " + name + "...");
            ProcessRunner.run(List.of(
                "ffmpeg", "-y", "-i", input,
                "-vf", scale,
                "-c:v", "libx264", "-crf", String.valueOf(h264Crf), "-preset", "fast",
                "-c:a", "aac", "-b:a", "128k",
                h264Out.toString()
            ));
            h264Paths.add("video/h264/" + h264Out.getFileName());

            // VP9 / WebM
            Path vp9Out = vp9Dir.resolve(name + "_vp9.webm");
            System.out.println("    [VP9]  Encoding " + name + "...");
            ProcessRunner.run(List.of(
                "ffmpeg", "-y", "-i", input,
                "-vf", scale,
                "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", String.valueOf(vp9Crf),
                "-c:a", "libopus", "-b:a", "128k",
                vp9Out.toString()
            ));
            vp9Paths.add("video/vp9/" + vp9Out.getFileName());

            // HEVC / MKV
            Path hevcOut = hevcDir.resolve(name + "_hevc.mkv");
            System.out.println("    [HEVC] Encoding " + name + "...");
            ProcessRunner.run(List.of(
                "ffmpeg", "-y", "-i", input,
                "-vf", scale,
                "-c:v", "libx265", "-crf", String.valueOf(hevcCrf), "-preset", "fast",
                "-c:a", "aac", "-b:a", "128k",
                hevcOut.toString()
            ));
            hevcPaths.add("video/hevc/" + hevcOut.getFileName());
        }


        ctx.setResult("video_h264_paths", h264Paths);
        ctx.setResult("video_vp9_paths",  vp9Paths);
        ctx.setResult("video_hevc_paths", hevcPaths);

        System.out.println("    All 9 transcodes complete.");
    }

    @Override
    public String getName() { return "Transcoder"; }
}
