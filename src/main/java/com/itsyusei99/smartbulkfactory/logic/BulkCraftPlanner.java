package com.itsyusei99.smartbulkfactory.logic;

import com.simibubi.create.content.logistics.BigItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Pure helpers for bulk crafting requests: batch sizing and tag-variant selection. */
public final class BulkCraftPlanner {
    private static final int PACKAGE_SLOTS = 9;

    private BulkCraftPlanner() {
    }

    public static Optional<RecipeHolder<CraftingRecipe>> findRecipe(Level level, List<ItemStack> arrangement) {
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, toInput(arrangement), level);
    }

    public static CraftingInput toInput(List<ItemStack> arrangement) {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++)
            grid.add(i < arrangement.size() ? arrangement.get(i).copyWithCount(1) : ItemStack.EMPTY);
        return CraftingInput.of(3, 3, grid);
    }

    /** True when the recipe found for {@code arrangement} still matches after swapping {@code original} for {@code replacement}. */
    public static boolean acceptsSubstitution(Level level, List<ItemStack> arrangement, RecipeHolder<CraftingRecipe> recipe,
                                              ItemStack original, ItemStack replacement) {
        List<ItemStack> swapped = substitute(arrangement, original, replacement);
        CraftingInput input = toInput(swapped);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .filter(found -> found.id().equals(recipe.id())).isPresent();
    }

    public static List<ItemStack> substitute(List<ItemStack> arrangement, ItemStack original, ItemStack replacement) {
        List<ItemStack> out = new ArrayList<>(arrangement.size());
        for (ItemStack slot : arrangement)
            out.add(!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, original) ? replacement.copyWithCount(1) : slot);
        return out;
    }

    /**
     * Largest craft count (at most {@code cap}) for which the whole order still fits in a single package, since only
     * the first package of a request carries the crafting context.
     */
    public static int fitToPackage(List<BigItemStack> perCraft, int cap) {
        for (int n = Math.max(1, cap); n > 1; n--) {
            int slots = 0;
            for (BigItemStack stack : perCraft)
                slots += (int) Math.ceil((double) stack.count * n / Math.max(1, stack.stack.getMaxStackSize()));
            if (slots <= PACKAGE_SLOTS)
                return n;
        }
        return 1;
    }

    /** Each crafter slot holds a single stack, so a batch can't exceed the smallest ingredient stack size. */
    public static int ingredientStackCap(List<ItemStack> pattern) {
        int cap = 64;
        for (ItemStack stack : pattern)
            if (!stack.isEmpty())
                cap = Math.min(cap, stack.getMaxStackSize());
        return cap;
    }

    /**
     * How many of {@code crafts} pending crafts a repackager may put into one package: bounded by the ingredient
     * stack sizes and, for standard crafting recipes, by the result fitting one stack.
     */
    public static int repackBatch(Level level, List<BigItemStack> pattern, int crafts) {
        List<ItemStack> arrangement = new ArrayList<>(pattern.size());
        for (BigItemStack stack : pattern)
            arrangement.add(stack.stack);
        int batch = Math.min(crafts, ingredientStackCap(arrangement));
        if (arrangement.size() <= 9) {
            Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(level, arrangement);
            if (recipe.isPresent()) {
                ItemStack result = recipe.get().value().assemble(toInput(arrangement), level.registryAccess());
                if (!result.isEmpty())
                    batch = Math.min(batch, Math.max(1, result.getMaxStackSize() / result.getCount()));
            }
        }
        // No standard recipe (e.g. a mechanical crafting recipe): the gauge already capped the batch by its output.
        return Math.max(1, batch);
    }
}
