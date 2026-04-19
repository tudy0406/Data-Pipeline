package com.pipeline.tasks.visuals;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SpriteGenerator implements Task {

    @Override
    public void run(PipelineContext ctx) throws Exception {
        Path imagesDir = ctx.getOutputDir().resolve("images");
        Path thumbnailsDir = imagesDir.resolve("thumbnails");
        Files.createDirectories(thumbnailsDir);

        String input = ctx.getInputPath();

        // Sprite map: 1 frame every 5 seconds, scaled to 160x90, tiled 10 columns wide
        System.out.println("    Generating sprite_map.jpg...");
        ProcessRunner.run(List.of(
            "ffmpeg", "-y", "-i", input,
            "-vf", "fps=1/5,scale=160:90,tile=10x10",
            imagesDir.resolve("sprite_map.jpg").toString()
        ));

        // Individual thumbnails: 1 frame every 15 seconds, scaled to 320x180
        System.out.println("    Generating thumbnails...");
        ProcessRunner.run(List.of(
            "ffmpeg", "-y", "-i", input,
            "-vf", "fps=1/15,scale=320:180",
            thumbnailsDir.resolve("thumb_%04d.jpg").toString()
        ));

        System.out.println("    Sprite map and thumbnails generated.");
    }

    @Override
    public String getName() { return "SpriteGenerator"; }
}
