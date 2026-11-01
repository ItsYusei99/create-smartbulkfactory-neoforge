package com.itsyusei99.smartbulkfactory.logic;

/** Hands the batch size of the craft being assembled from RecipeGridHandler to the crafter (server thread only). */
public final class BatchState {
    public static int lastBatch = 1;

    private BatchState() {
    }
}
