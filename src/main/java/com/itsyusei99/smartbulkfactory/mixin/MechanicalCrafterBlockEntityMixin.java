package com.itsyusei99.smartbulkfactory.mixin;

import com.itsyusei99.smartbulkfactory.logic.BatchState;
import com.itsyusei99.smartbulkfactory.logic.CrafterRecovery;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.createmod.catnip.math.Pointing;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class MechanicalCrafterBlockEntityMixin {
    @Shadow
    protected abstract void continueIfAllPrecedingFinished();

    /** Re-sends the handshake Create only sends once, so a chunk unload mid-craft cannot freeze the group. */
    @Inject(method = "lazyTick", at = @At("TAIL"))
    private void sbf$resumeMissedHandshake(CallbackInfo ci) {
        if (CrafterRecovery.hasMissedHandshake((MechanicalCrafterBlockEntity) (Object) this))
            continueIfAllPrecedingFinished();
    }

    /** Crafting remainders (buckets, bottles...) come back once per craft, so scale them with the batch. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;mergeOnto(Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;Lnet/createmod/catnip/math/Pointing;)V", ordinal = 0))
    private void sbf$scaleContainers(RecipeGridHandler.GroupedItems container, RecipeGridHandler.GroupedItems target,
                                     Pointing pointing, Operation<Void> original) {
        int batch = BatchState.lastBatch;
        if (batch > 1)
            for (ItemStack stack : ((GroupedItemsAccessor) container).sbf$grid().values())
                stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() * batch));
        original.call(container, target, pointing);
    }
}
