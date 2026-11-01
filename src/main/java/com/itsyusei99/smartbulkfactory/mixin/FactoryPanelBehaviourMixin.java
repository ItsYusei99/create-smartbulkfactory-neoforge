package com.itsyusei99.smartbulkfactory.mixin;

import com.itsyusei99.smartbulkfactory.SbfConfig;
import com.itsyusei99.smartbulkfactory.SbfGaugeOptions;
import com.itsyusei99.smartbulkfactory.logic.BulkCraftPlanner;
import com.itsyusei99.smartbulkfactory.logic.RecipeAnalysis;
import com.itsyusei99.smartbulkfactory.logic.RequestGroup;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin implements SbfGaugeOptions {
    @Shadow public Map<FactoryPanelPosition, FactoryPanelConnection> targetedBy;
    @Shadow public List<ItemStack> activeCraftingArrangement;
    @Shadow public boolean satisfied;
    @Shadow public boolean promisedSatisfied;
    @Shadow public boolean waitingForNetwork;
    @Shadow public boolean redstonePowered;
    @Shadow public String recipeAddress;
    @Shadow public int recipeOutput;
    @Shadow private int timer;
    @Shadow public FactoryPanelBlock.PanelSlot slot;

    @Shadow protected abstract void sendEffect(FactoryPanelPosition fromPos, boolean success);
    @Shadow protected abstract int getConfigRequestIntervalInTicks();

    @Unique private boolean sbf$allowVariants = true;
    @Unique private int sbf$maxBatch = 64;

    @Override public boolean sbf$allowVariants() { return sbf$allowVariants; }
    @Override public void sbf$setAllowVariants(boolean value) { sbf$allowVariants = value; }
    @Override public int sbf$maxBatch() { return sbf$maxBatch; }
    @Override public void sbf$setMaxBatch(int value) { sbf$maxBatch = value; }

    @Inject(method = "write", at = @At("TAIL"))
    private void sbf$write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        nbt.putBoolean("SbfVariants_" + slot.name(), sbf$allowVariants);
        nbt.putInt("SbfMaxBatch_" + slot.name(), sbf$maxBatch);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void sbf$read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        String variantsKey = "SbfVariants_" + slot.name();
        String batchKey = "SbfMaxBatch_" + slot.name();
        sbf$allowVariants = !nbt.contains(variantsKey) || nbt.getBoolean(variantsKey);
        sbf$maxBatch = nbt.contains(batchKey) ? Math.max(1, Math.min(64, nbt.getInt(batchKey))) : 64;
    }

    @Inject(method = "tickRequests", at = @At("HEAD"), cancellable = true)
    private void sbf$tickRequests(CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        boolean crafting = !activeCraftingArrangement.isEmpty();
        boolean variants = sbf$allowVariants && SbfConfig.ENABLE_VARIANTS.get();
        if (panelBE.restocker || targetedBy.isEmpty() || (!crafting && !variants))
            return;
        ci.cancel();
        if (satisfied || promisedSatisfied || waitingForNetwork || redstonePowered)
            return;
        if (timer > 0) {
            timer = Math.min(timer, getConfigRequestIntervalInTicks());
            timer--;
            return;
        }
        self.resetTimer();
        if (recipeAddress.isBlank())
            return;
        sbf$requestCrafts(self, panelBE);
    }

    @Unique
    private void sbf$requestCrafts(FactoryPanelBehaviour self, FactoryPanelBlockEntity panelBE) {
        Level world = self.getWorld();
        ItemStack filter = self.getFilter();
        if (filter.isEmpty())
            return;
        boolean crafting = !activeCraftingArrangement.isEmpty();
        boolean variants = sbf$allowVariants && SbfConfig.ENABLE_VARIANTS.get();

        // Input connections grouped by (network, item), in connection order.
        Map<UUID, Map<ItemStack, RequestGroup>> byNetwork = new LinkedHashMap<>();
        List<RequestGroup> groups = new ArrayList<>();
        for (FactoryPanelConnection connection : targetedBy.values()) {
            FactoryPanelBehaviour source = FactoryPanelBehaviour.at(world, connection);
            if (source == null)
                return;
            ItemStack item = source.getFilter();
            Map<ItemStack, RequestGroup> items = byNetwork.computeIfAbsent(source.network,
                    $ -> new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG));
            RequestGroup group = items.get(item);
            if (group == null) {
                group = new RequestGroup(source.network, item);
                items.put(item, group);
                groups.add(group);
            }
            group.connections.add(connection);
            group.total += connection.amount;
        }

        // In crafting mode, connected gauges with amount 0 are not ingredients of their own: they are the
        // alternatives the user allowed for the real ingredients.
        List<RequestGroup> required = new ArrayList<>();
        List<RequestGroup> spare = new ArrayList<>();
        for (RequestGroup group : groups)
            (crafting && variants && group.total == 0 && !group.item.isEmpty() ? spare : required).add(group);

        int demand = self.getAmount() * (self.upTo ? 1 : filter.getMaxStackSize());
        int deficit = demand - self.getLevelInStorage() - self.getPromised();
        int perCraft = Math.max(1, recipeOutput);
        int batch = 1;
        RecipeHolder<CraftingRecipe> recipe = null;
        if (crafting) {
            int cap = Math.min(SbfConfig.MAX_BATCH_CRAFTS.get(), sbf$maxBatch);
            batch = Math.max(1, Math.min(cap, (deficit + perCraft - 1) / perCraft));
            batch = Math.min(batch, Math.max(1, filter.getMaxStackSize() / perCraft));
            if (!spare.isEmpty() && activeCraftingArrangement.size() <= 9)
                recipe = BulkCraftPlanner.findRecipe(world, activeCraftingArrangement).orElse(null);
        }

        boolean failed = false;
        List<ItemStack> pattern = new ArrayList<>(activeCraftingArrangement);
        Map<UUID, List<BigItemStack>> perNetwork = new LinkedHashMap<>();

        if (crafting) {
            for (RequestGroup group : required) {
                ItemStack chosen = group.item;
                UUID network = group.network;
                int stock = LogisticsManager.getSummaryOfNetwork(network, true).getCountOf(chosen);
                if (group.total != 0 && !group.item.isEmpty() && stock < group.total * batch && recipe != null) {
                    for (RequestGroup alternative : spare) {
                        int available = LogisticsManager.getSummaryOfNetwork(alternative.network, true).getCountOf(alternative.item);
                        if (available > stock && BulkCraftPlanner.acceptsSubstitution(world, activeCraftingArrangement, recipe,
                                group.item, alternative.item)) {
                            chosen = alternative.item;
                            network = alternative.network;
                            stock = available;
                        }
                    }
                }
                boolean ok = group.total != 0 && !chosen.isEmpty() && stock >= group.total;
                for (FactoryPanelConnection connection : group.connections)
                    sendEffect(connection.from, ok);
                if (!ok) {
                    failed = true;
                    continue;
                }
                batch = Math.min(batch, stock / group.total);
                if (!ItemStack.isSameItemSameComponents(chosen, group.item))
                    pattern = BulkCraftPlanner.substitute(pattern, group.item, chosen);
                perNetwork.computeIfAbsent(network, $ -> new ArrayList<>()).add(new BigItemStack(chosen, group.total));
            }
        } else {
            // Gauges whose items are interchangeable in some recipe producing this gauge's item form one pool:
            // the pool needs the largest requested amount, served from its members in connection order.
            List<List<RequestGroup>> pools = new ArrayList<>();
            for (RequestGroup group : required) {
                List<RequestGroup> home = null;
                if (variants)
                    for (List<RequestGroup> pool : pools)
                        if (RecipeAnalysis.areAlternatives(world, filter, pool.get(0).item, group.item)) {
                            home = pool;
                            break;
                        }
                if (home == null) {
                    home = new ArrayList<>();
                    pools.add(home);
                }
                home.add(group);
            }
            for (List<RequestGroup> pool : pools) {
                int need = 0;
                for (RequestGroup member : pool)
                    need = Math.max(need, member.total);
                List<BigItemStack> parts = new ArrayList<>();
                List<UUID> partNetworks = new ArrayList<>();
                int remaining = need;
                for (RequestGroup member : pool) {
                    if (remaining <= 0 || member.item.isEmpty())
                        continue;
                    int take = Math.min(remaining, LogisticsManager.getSummaryOfNetwork(member.network, true).getCountOf(member.item));
                    if (take > 0) {
                        parts.add(new BigItemStack(member.item, take));
                        partNetworks.add(member.network);
                        remaining -= take;
                    }
                }
                boolean ok = need != 0 && remaining <= 0;
                for (RequestGroup member : pool)
                    for (FactoryPanelConnection connection : member.connections)
                        sendEffect(connection.from, ok);
                if (!ok) {
                    failed = true;
                    continue;
                }
                for (int i = 0; i < parts.size(); i++)
                    perNetwork.computeIfAbsent(partNetworks.get(i), $ -> new ArrayList<>()).add(parts.get(i));
            }
        }
        if (failed)
            return;

        List<CraftingEntry> entries = List.of();
        if (crafting) {
            batch = Math.min(batch, BulkCraftPlanner.ingredientStackCap(pattern));
            List<BigItemStack> perCraftOrder = new ArrayList<>();
            perNetwork.values().forEach(perCraftOrder::addAll);
            batch = Math.min(batch, BulkCraftPlanner.fitToPackage(perCraftOrder, batch));
            batch = Math.max(1, batch);

            List<BigItemStack> patternStacks = new ArrayList<>();
            for (ItemStack slot : pattern)
                patternStacks.add(new BigItemStack(slot.copyWithCount(1)));
            entries = List.of(new CraftingEntry(new PackageOrder(patternStacks), batch));
        }

        List<Multimap<PackagerBlockEntity, PackagingRequest>> requests = new ArrayList<>();
        for (Map.Entry<UUID, List<BigItemStack>> network : perNetwork.entrySet()) {
            List<BigItemStack> scaled = new ArrayList<>();
            for (BigItemStack stack : network.getValue())
                scaled.add(new BigItemStack(stack.stack, stack.count * batch));
            PackageOrderWithCrafts order = new PackageOrderWithCrafts(new PackageOrder(scaled), entries);
            requests.add(LogisticsManager.findPackagersForRequest(network.getKey(), order, null, recipeAddress));
        }
        for (Multimap<PackagerBlockEntity, PackagingRequest> request : requests)
            for (PackagerBlockEntity packager : request.keySet())
                if (packager.isTooBusyFor(RequestType.RESTOCK))
                    return;
        for (Multimap<PackagerBlockEntity, PackagingRequest> request : requests)
            LogisticsManager.performPackageRequests(request);

        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(self.network);
        if (promises != null)
            promises.add(new RequestPromise(new BigItemStack(filter, recipeOutput * batch)));
        panelBE.advancements.awardPlayer(AllAdvancements.FACTORY_GAUGE);
    }
}
