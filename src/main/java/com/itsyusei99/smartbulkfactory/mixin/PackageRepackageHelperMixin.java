package com.itsyusei99.smartbulkfactory.mixin;

import com.itsyusei99.smartbulkfactory.SbfConfig;
import com.itsyusei99.smartbulkfactory.logic.BulkCraftPlanner;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PackageRepackageHelper.class, remap = false)
public class PackageRepackageHelperMixin {
    /**
     * Create splits an N-craft order into N single-craft packages. Group them instead, so a crafter receives a whole
     * batch (up to what the ingredients and the result can stack to) and crafts it in one pass.
     */
    @Inject(method = "repackBasedOnRecipes", at = @At("RETURN"), cancellable = true)
    private void sbf$batchPackages(InventorySummary summary, PackageOrderWithCrafts order, String address, RandomSource random,
                                   CallbackInfoReturnable<List<BigItemStack>> cir) {
        List<BigItemStack> original = cir.getReturnValue();
        List<CraftingEntry> crafts = order.orderedCrafts();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || original == null || original.size() != crafts.size())
            return;
        ServerLevel level = server.overworld();
        int cap = SbfConfig.MAX_BATCH_CRAFTS.get();

        List<BigItemStack> grouped = new ArrayList<>();
        for (int i = 0; i < crafts.size(); i++) {
            BigItemStack packages = original.get(i);
            CraftingEntry entry = crafts.get(i);
            int batch = 1;
            if (packages.count > 1) {
                batch = Math.min(cap, BulkCraftPlanner.repackBatch(level, entry.pattern().stacks(), packages.count));
                List<BigItemStack> perCraft = new ArrayList<>();
                for (ItemStack total : sbf$totals(PackageItem.getContents(packages.stack)))
                    perCraft.add(new BigItemStack(total));
                batch = Math.min(batch, BulkCraftPlanner.fitToPackage(perCraft, batch));
            }
            if (batch <= 1) {
                grouped.add(packages);
                continue;
            }
            int full = packages.count / batch;
            int rest = packages.count % batch;
            if (full > 0)
                grouped.add(new BigItemStack(sbf$box(packages.stack, entry, batch, random), full));
            if (rest > 1)
                grouped.add(new BigItemStack(sbf$box(packages.stack, entry, rest, random), 1));
            else if (rest == 1)
                grouped.add(new BigItemStack(packages.stack.copy(), 1));
        }
        cir.setReturnValue(grouped);
    }

    /** Multiplies the contents of the single-craft box (whatever mod built it) by the batch, spreading over slots. */
    private static ItemStack sbf$box(ItemStack single, CraftingEntry entry, int batch, RandomSource random) {
        ItemStackHandler source = PackageItem.getContents(single);
        List<ItemStack> totals = sbf$totals(source);
        ItemStackHandler target = new ItemStackHandler(9);
        int slot = 0;
        for (ItemStack total : totals) {
            int remaining = total.getCount() * batch;
            while (remaining > 0 && slot < target.getSlots()) {
                int put = Math.min(remaining, total.getMaxStackSize());
                target.setStackInSlot(slot++, total.copyWithCount(put));
                remaining -= put;
            }
        }
        ItemStack box = PackageItem.containing(target);
        PackageItem.setOrder(box, random.nextInt(), 0, true, 0, true,
                new PackageOrderWithCrafts(PackageOrder.empty(), List.of(new CraftingEntry(entry.pattern(), batch))));
        return box;
    }

    private static List<ItemStack> sbf$totals(ItemStackHandler contents) {
        List<ItemStack> totals = new ArrayList<>();
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack stack = contents.getStackInSlot(i);
            if (stack.isEmpty())
                continue;
            boolean merged = false;
            for (ItemStack total : totals)
                if (ItemStack.isSameItemSameComponents(total, stack)) {
                    total.grow(stack.getCount());
                    merged = true;
                    break;
                }
            if (!merged)
                totals.add(stack.copy());
        }
        return totals;
    }
}
