package com.itsyusei99.smartbulkfactory.logic;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/** Input connections of a gauge that request the same item from the same logistics network. */
public final class RequestGroup {
    public final UUID network;
    public final ItemStack item;
    public int total;
    public final List<FactoryPanelConnection> connections = new ArrayList<>();

    public RequestGroup(UUID network, ItemStack item) {
        this.network = network;
        this.item = item;
    }
}
