package com.itsyusei99.smartbulkfactory;

/** Duck interface implemented on FactoryPanelBehaviour by the mixin. */
public interface SbfGaugeOptions {
    boolean sbf$allowVariants();

    void sbf$setAllowVariants(boolean value);

    int sbf$maxBatch();

    void sbf$setMaxBatch(int value);
}
