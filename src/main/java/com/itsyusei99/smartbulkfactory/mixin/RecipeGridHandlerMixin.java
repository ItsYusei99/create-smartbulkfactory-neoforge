package com.itsyusei99.smartbulkfactory.mixin;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.itsyusei99.smartbulkfactory.logic.BatchState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeGridHandler.class, remap = false)
public class RecipeGridHandlerMixin {
    @Inject(method = "tryToApplyRecipe", at = @At("RETURN"), cancellable = true)
    private static void sbf$scaleResult(Level world, RecipeGridHandler.GroupedItems items, CallbackInfoReturnable<ItemStack> cir) {
        BatchState.lastBatch = 1;
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty())
            return;
        int batch = Integer.MAX_VALUE;
        for (ItemStack stack : ((GroupedItemsAccessor) items).sbf$grid().values())
            if (!stack.isEmpty())
                batch = Math.min(batch, stack.getCount());
        if (batch == Integer.MAX_VALUE || batch <= 1)
            return;
        BatchState.lastBatch = batch;
        cir.setReturnValue(result.copyWithCount(result.getCount() * batch));
    }
}
