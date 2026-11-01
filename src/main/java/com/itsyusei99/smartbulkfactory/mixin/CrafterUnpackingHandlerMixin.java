package com.itsyusei99.smartbulkfactory.mixin;

import com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler.ConnectedInput;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.impl.unpacking.CrafterUnpackingHandler;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CrafterUnpackingHandler.class, remap = false)
public class CrafterUnpackingHandlerMixin {
    /** Accepts a package holding N crafts of a recipe and puts N items of each ingredient into its crafter. */
    @Inject(method = "unpack", at = @At("HEAD"), cancellable = true)
    private void sbf$unpackBatch(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items,
                                 @Nullable PackageOrderWithCrafts orderContext, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (!PackageOrderWithCrafts.hasCraftingInformation(orderContext))
            return;
        int batch = orderContext.orderedCrafts().get(0).count();
        if (batch <= 1)
            return;
        if (!(level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity crafter)) {
            cir.setReturnValue(false);
            return;
        }
        List<BigItemStack> pattern = orderContext.getCraftingInformation();
        ConnectedInput input = crafter.getInput();
        List<MechanicalCrafterBlockEntity.Inventory> inventories = input.getInventories(level, pos);
        if (inventories.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        int max = Math.min(inventories.size(), pattern.size());
        ItemStack[] toInsert = new ItemStack[max];
        for (int i = 0; i < max; i++) {
            toInsert[i] = ItemStack.EMPTY;
            ItemStack wanted = pattern.get(i).stack;
            if (wanted.isEmpty())
                continue;
            MechanicalCrafterBlockEntity.Inventory inventory = inventories.get(i);
            if (!inventory.getStackInSlot(0).isEmpty() || !inventory.insertItem(0, wanted.copyWithCount(1), true).isEmpty()) {
                cir.setReturnValue(false);
                return;
            }
            int missing = Math.min(batch, wanted.getMaxStackSize());
            for (ItemStack stack : items) {
                if (missing > 0 && ItemStack.isSameItemSameComponents(stack, wanted)) {
                    int taken = Math.min(missing, stack.getCount());
                    stack.shrink(taken);
                    missing -= taken;
                }
            }
            if (missing > 0) {
                cir.setReturnValue(false);
                return;
            }
            toInsert[i] = wanted.copyWithCount(Math.min(batch, wanted.getMaxStackSize()));
        }
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                cir.setReturnValue(false);
                return;
            }
        }
        if (!simulate) {
            for (int i = 0; i < max; i++)
                if (!toInsert[i].isEmpty())
                    inventories.get(i).setStackInSlot(0, toInsert[i]);
            crafter.checkCompletedRecipe(true);
        }
        cir.setReturnValue(true);
    }
}
