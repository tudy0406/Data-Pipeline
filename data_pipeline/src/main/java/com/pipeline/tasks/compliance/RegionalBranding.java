package com.pipeline.tasks.compliance;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;

import java.util.List;

public class RegionalBranding implements Task {

    @Override
    public void run(PipelineContext ctx) throws Exception {
        // Stub
        List<String> regions = List.of("RO", "US", "EU");
        ctx.setResult("branded_regions", regions);
        System.out.println("    Regional branding applied for: " + regions);
    }

    @Override
    public String getName() { return "RegionalBranding"; }
}
