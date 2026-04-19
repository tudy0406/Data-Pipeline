package com.pipeline.tasks.ingest;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;

import java.io.File;
import java.io.FileInputStream;

public class IntegrityCheck implements Task {

    @Override
    public void run(PipelineContext ctx) throws Exception {
        File file = new File(ctx.getInputPath());

        if (!file.exists()) {
            throw new Exception("Input file does not exist: " + ctx.getInputPath());
        }

        if (!file.canRead()) {
            throw new Exception("Input file is not readable: " + ctx.getInputPath());
        }

        // Check MP4 magic bytes (ftyp box signature)
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            if (fis.read(header) < 12) {
                throw new Exception("File too small to be a valid MP4.");
            }
            // Bytes 4-7 should be "ftyp" in a valid MP4
            String marker = new String(header, 4, 4);
            if (!marker.equals("ftyp")) {
                throw new Exception("File does not appear to be a valid MP4 (missing ftyp box).");
            }
        }

        ctx.setResult("integrity_check", "PASSED");
        System.out.println("    Integrity check passed for: " + file.getName());
    }

    @Override
    public String getName() {
        return "IntegrityCheck";
    }
}
