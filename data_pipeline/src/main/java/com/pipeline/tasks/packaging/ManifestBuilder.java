package com.pipeline.tasks.packaging;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;

import java.io.FileWriter;
import java.time.Instant;
import java.util.List;

public class ManifestBuilder implements Task {

    @Override
    public void run(PipelineContext ctx) throws Exception {
        String manifest = buildManifest(ctx);
        try (FileWriter fw = new FileWriter(ctx.getOutputDir().resolve("manifest.json").toFile())) {
            fw.write(manifest);
        }
        System.out.println("    manifest.json written.");
    }

    private String buildManifest(PipelineContext ctx) {
        // Safely read results from context, providing defaults where needed
        String drmStatus = safeGet(ctx, "drm_status",   "UNKNOWN");
        String drmKeyId = safeGet(ctx, "drm_key_id",   "UNKNOWN");
        String safetyStatus = safeGet(ctx, "safety_status","UNKNOWN");
        String complexity = safeGet(ctx, "complexity_profile", "UNKNOWN");
        String introEnd = safeGet(ctx, "intro_end_timestamp", "UNKNOWN");
        String outroStart = safeGet(ctx, "outro_start_timestamp", "UNKNOWN");
        String creditsStart = safeGet(ctx, "credits_start_timestamp", "UNKNOWN");

        @SuppressWarnings("unchecked")
        List<String> regions = ctx.hasResult("branded_regions")
            ? (List<String>) ctx.getResult("branded_regions")
            : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"pipeline_version\": \"1.0\",\n");
        sb.append("  \"generated_at\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"source\": \"").append(ctx.getInputPath()).append("\",\n");
        sb.append("  \"drm\": {\n");
        sb.append("    \"status\": \"").append(drmStatus).append("\",\n");
        sb.append("    \"key_id\": \"").append(drmKeyId).append("\"\n");
        sb.append("  },\n");
        // Read actual paths recorded by Transcoder — avoids hardcoding assumptions
        @SuppressWarnings("unchecked")
        List<String> h264 = ctx.hasResult("video_h264_paths")
            ? (List<String>) ctx.getResult("video_h264_paths") : List.of();
        @SuppressWarnings("unchecked")
        List<String> vp9 = ctx.hasResult("video_vp9_paths")
            ? (List<String>) ctx.getResult("video_vp9_paths")  : List.of();
        @SuppressWarnings("unchecked")
        List<String> hevc = ctx.hasResult("video_hevc_paths")
            ? (List<String>) ctx.getResult("video_hevc_paths") : List.of();

        sb.append("  \"video\": {\n");
        sb.append("    \"h264\": ").append(toJsonArray(h264)).append(",\n");
        sb.append("    \"vp9\":  ").append(toJsonArray(vp9)).append(",\n");
        sb.append("    \"hevc\": ").append(toJsonArray(hevc)).append("\n");
        sb.append("  },\n");
        sb.append("  \"images\": {\n");
        sb.append("    \"sprite_map\": \"images/sprite_map.jpg\",\n");
        sb.append("    \"thumbnails\": \"images/thumbnails/\"\n");
        sb.append("  },\n");
        sb.append("  \"text\": {\n");
        sb.append("    \"source_transcript\": \"text/source_transcript.txt\",\n");
        sb.append("    \"ro_translation\": \"text/ro_translation.txt\"\n");
        sb.append("  },\n");
        sb.append("  \"audio\": {\n");
        sb.append("    \"ro_dub_synthetic\": \"audio/ro_dub_synthetic.aac\"\n");
        sb.append("  },\n");
        sb.append("  \"metadata\": {\n");
        sb.append("    \"scene_analysis\": \"metadata/scene_analysis.json\"\n");
        sb.append("  },\n");
        sb.append("  \"analysis\": {\n");
        sb.append("    \"intro_end\": \"").append(introEnd).append("\",\n");
        sb.append("    \"outro_start\": \"").append(outroStart).append("\",\n");
        sb.append("    \"credits_start\": \"").append(creditsStart).append("\",\n");
        sb.append("    \"complexity_profile\": \"").append(complexity).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"compliance\": {\n");
        sb.append("    \"safety_status\": \"").append(safetyStatus).append("\",\n");
        sb.append("    \"branded_regions\": ").append(toJsonArray(regions)).append("\n");
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    private String safeGet(PipelineContext ctx, String key, String defaultVal) {
        return ctx.hasResult(key) ? String.valueOf(ctx.getResult(key)) : defaultVal;
    }

    private String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append("\"").append(items.get(i)).append("\"");
            if (i < items.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String getName() { return "ManifestBuilder"; }
}
