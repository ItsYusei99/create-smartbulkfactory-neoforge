package com.itsyusei99.smartbulkfactory.mixin;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RecipeGridHandler.GroupedItems.class, remap = false)
public interface GroupedItemsAccessor {
    @Accessor("grid")
    Map<Pair<Integer, Integer>, ItemStack> sbf$grid();
}
