package com.itsyusei99.smartbulkfactory;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SbfConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_BATCH_CRAFTS;
    public static final ModConfigSpec.BooleanValue ENABLE_VARIANTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MAX_BATCH_CRAFTS = b.comment("Maximum crafts a Factory Gauge may request at once (1 = vanilla behaviour).")
                .defineInRange("maxBatchCrafts", 64, 1, 64);
        ENABLE_VARIANTS = b.comment("Allow gauges to use extra connected input gauges as alternatives for each other.")
                .define("enableVariants", true);
        SPEC = b.build();
    }
}
