package com.itsyusei99.smartbulkfactory.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/**
 * Scans every recipe (crafting, smelting, blasting, Create processing...) once per recipe manager and answers whether
 * two items can stand in for each other as the same ingredient of a recipe that produces a given item.
 */
public final class RecipeAnalysis {
    private static final Map<RecipeManager, Map<Item, List<NonNullList<Ingredient>>>> CACHE = new WeakHashMap<>();

    private RecipeAnalysis() {
    }

    public static boolean areAlternatives(Level level, ItemStack output, ItemStack a, ItemStack b) {
        if (output.isEmpty() || a.isEmpty() || b.isEmpty() || ItemStack.isSameItemSameComponents(a, b))
            return false;
        for (NonNullList<Ingredient> ingredients : recipesProducing(level, output.getItem()))
            for (Ingredient ingredient : ingredients)
                if (!ingredient.isEmpty() && ingredient.test(a) && ingredient.test(b))
                    return true;
        return false;
    }

    private static List<NonNullList<Ingredient>> recipesProducing(Level level, Item output) {
        RecipeManager manager = level.getRecipeManager();
        Map<Item, List<NonNullList<Ingredient>>> index;
        synchronized (CACHE) {
            index = CACHE.get(manager);
            if (index == null) {
                index = new HashMap<>();
                for (RecipeHolder<?> holder : manager.getRecipes()) {
                    try {
                        Recipe<?> recipe = holder.value();
                        ItemStack result = recipe.getResultItem(level.registryAccess());
                        if (!result.isEmpty())
                            index.computeIfAbsent(result.getItem(), $ -> new ArrayList<>()).add(recipe.getIngredients());
                    } catch (RuntimeException ignored) {
                        // some modded recipes can't report a result without extra context
                    }
                }
                CACHE.put(manager, index);
            }
        }
        return index.getOrDefault(output, List.of());
    }
}
