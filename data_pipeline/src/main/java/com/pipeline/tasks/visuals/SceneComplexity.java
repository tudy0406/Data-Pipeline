package com.pipeline.tasks.visuals;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SceneComplexity implements Task {

    @Override
    public void run(PipelineContext ctx) throws Exception {
        Path metadataDir = ctx.getOutputDir().resolve("metadata");
        Files.createDirectories(metadataDir);

        // Run ffprobe to get video stream info
        String ffprobeOutput = ProcessRunner.run(List.of(
            "ffprobe", "-v", "quiet",
            "-print_format", "json",
            "-show_streams",
            "-select_streams", "v:0",
            ctx.getInputPath()
        ));

        // Parse key fields from ffprobe JSON output
        String codecName  = extractJsonValue(ffprobeOutput, "codec_name");
        String width = extractJsonValue(ffprobeOutput, "width");
        String height = extractJsonValue(ffprobeOutput, "height");
        String bitRate = extractJsonValue(ffprobeOutput, "bit_rate");
        String frameRate = extractJsonValue(ffprobeOutput, "r_frame_rate");

        // Determine complexity profile and CRF values based on bitrate
        long bitrateVal = 0;
        try { bitrateVal = Long.parseLong(bitRate); } catch (NumberFormatException ignored) {}

        String complexityProfile;
        int h264Crf, vp9Crf, hevcCrf;

        if (bitrateVal > 8_000_000) {
            complexityProfile = "high";
            h264Crf = 18; vp9Crf = 33; hevcCrf = 24;
        } else if (bitrateVal > 2_000_000) {
            complexityProfile = "medium";
            h264Crf = 20; vp9Crf = 35; hevcCrf = 26;
        } else {
            complexityProfile = "low";
            h264Crf = 23; vp9Crf = 37; hevcCrf = 28;
        }

        // Pull scene index from context if SceneIndexer already ran
        @SuppressWarnings("unchecked")
        Map<String, String> sceneIndex = ctx.hasResult("scene_index")
            ? (Map<String, String>) ctx.getResult("scene_index")
            : Map.of();

        // Write scene_analysis.json
        String analysisJson = buildJson(
            ctx.getInputPath(), codecName, width, height,
            bitRate, frameRate, complexityProfile,
            h264Crf, vp9Crf, hevcCrf, sceneIndex
        );
        try (FileWriter fw = new FileWriter(metadataDir.resolve("scene_analysis.json").toFile())) {
            fw.write(analysisJson);
        }

        // Store encoding recommendations in context for Transcoder to read
        ctx.setResult("complexity_profile", complexityProfile);
        ctx.setResult("h264_crf", h264Crf);
        ctx.setResult("vp9_crf",  vp9Crf);
        ctx.setResult("hevc_crf", hevcCrf);

        System.out.println("    Complexity: " + complexityProfile +
            " | bitrate: " + bitrateVal / 1000 + " kbps" +
            " | resolution: " + width + "x" + height);
        System.out.println("    scene_analysis.json written.");
    }

    //JSON field extractor
    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return "unknown";
        int colon = json.indexOf(":", idx);
        if (colon == -1) return "unknown";
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() &&
               json.charAt(end) != '"' && json.charAt(end) != ',' &&
               json.charAt(end) != '\n' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }

    private String buildJson(String inputFile, String codec, String width, String height,
                             String bitRate, String frameRate, String profile,
                             int h264Crf, int vp9Crf, int hevcCrf,
                             Map<String, String> sceneIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"source_file\": \"").append(inputFile).append("\",\n");
        sb.append("  \"analyzed_at\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"video_stream\": {\n");
        sb.append("    \"codec_name\": \"").append(codec).append("\",\n");
        sb.append("    \"width\": ").append(width).append(",\n");
        sb.append("    \"height\": ").append(height).append(",\n");
        sb.append("    \"bit_rate\": \"").append(bitRate).append("\",\n");
        sb.append("    \"r_frame_rate\": \"").append(frameRate).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"complexity_profile\": \"").append(profile).append("\",\n");
        sb.append("  \"encoding_recommendations\": {\n");
        sb.append("    \"h264_crf\": ").append(h264Crf).append(",\n");
        sb.append("    \"vp9_crf\": ").append(vp9Crf).append(",\n");
        sb.append("    \"hevc_crf\": ").append(hevcCrf).append("\n");
        sb.append("  },\n");
        sb.append("  \"scene_index\": {\n");
        int i = 0;
        for (Map.Entry<String, String> e : sceneIndex.entrySet()) {
            sb.append("    \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"");
            if (++i < sceneIndex.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String getName() { return "SceneComplexity"; }
}
