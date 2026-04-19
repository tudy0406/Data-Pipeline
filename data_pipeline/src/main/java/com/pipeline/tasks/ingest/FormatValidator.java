package com.pipeline.tasks.ingest;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;

import java.io.File;

public class FormatValidator implements Task {

    private static final long MAX_FILE_SIZE_GB = 110L * 1024 * 1024 * 1024; // 110 GB ceiling
    private static final long MIN_FILE_SIZE_KB = 10L * 1024;                 // at least 10 KB

    @Override
    public void run(PipelineContext ctx) throws Exception {
        File file = new File(ctx.getInputPath());

        // Check extension
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".mp4")) {
            throw new Exception("Format validation failed: expected .mp4, got: " + name);
        }

        // Check file size is within expected studio bounds
        long size = file.length();
        if (size < MIN_FILE_SIZE_KB) {
            throw new Exception("Format validation failed: file is suspiciously small (" + size + " bytes).");
        }
        if (size > MAX_FILE_SIZE_GB) {
            throw new Exception("Format validation failed: file exceeds maximum allowed size.");
        }

        ctx.setResult("format_validation", "PASSED");
        ctx.setResult("file_size_bytes", size);
        System.out.println("    Format validation passed. File size: " + (size / 1024) + " KB");
    }

    @Override
    public String getName() {
        return "FormatValidator";
    }
}
