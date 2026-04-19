package com.pipeline.tasks.packaging;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;

public class DRMWrapper implements Task {

    @Override
    public void run(PipelineContext ctx) throws Exception {
        // Stub: real DRM (Widevine, PlayReady, FairPlay) requires a licensed
        // key server. This stub simulates the encryption step.
        String keyId = "stub-key-" + System.currentTimeMillis();
        ctx.setResult("drm_status", "STUB_ENCRYPTED");
        ctx.setResult("drm_key_id", keyId);
        System.out.println("    DRM wrapper applied (stub). Key ID: " + keyId);
    }

    @Override
    public String getName() { return "DRMWrapper"; }
}
