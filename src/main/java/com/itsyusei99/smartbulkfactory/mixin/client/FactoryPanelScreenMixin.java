package com.itsyusei99.smartbulkfactory.mixin.client;

import com.itsyusei99.smartbulkfactory.SbfGaugeOptions;
import com.itsyusei99.smartbulkfactory.network.SetGaugeOptionsPacket;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.util.List;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the "variants" toggle and "max crafts per batch" button to the gauge's crafting configuration screen. */
@Mixin(value = FactoryPanelScreen.class, remap = false)
public abstract class FactoryPanelScreenMixin {
    @Unique private static final int[] SBF_BATCH_STEPS = {1, 2, 4, 8, 16, 32, 64};

    @Shadow private FactoryPanelBehaviour behaviour;
    @Shadow private boolean restocker;
    @Shadow private boolean craftingActive;
    @Shadow private BigItemStack outputConfig;
    @Shadow private List<BigItemStack> inputConfig;
    @Shadow private CraftingRecipe availableCraftingRecipe;

    @Unique private IconButton sbf$variantsButton;
    @Unique private IconButton sbf$batchButton;
    @Unique private boolean sbf$variants = true;
    @Unique private int sbf$batch = 64;
    @Unique private boolean sbf$loaded;

    @Inject(method = "init", at = @At("TAIL"))
    private void sbf$init(CallbackInfo ci) {
        if (restocker)
            return;
        SbfGaugeOptions options = (SbfGaugeOptions) behaviour;
        // Create calls init() again on many interactions, so only read the saved options the first time.
        if (!sbf$loaded) {
            sbf$loaded = true;
            sbf$variants = options.sbf$allowVariants();
            sbf$batch = options.sbf$maxBatch();
        }
        AbstractSimiScreen screen = (AbstractSimiScreen) (Object) this;
        ScreenAccess access = (ScreenAccess) screen;
        int x = access.sbf$guiLeft();
        int y = access.sbf$guiTop() + 27;

        sbf$variantsButton = new IconButton(x + 9, y, AllIcons.I_TUNNEL_RANDOMIZE);
        sbf$variantsButton.withCallback(() -> {
            sbf$variants = !sbf$variants;
            sbf$refreshButtons();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.25F));
        });
        sbf$batchButton = new IconButton(x + 9, y + 20, AllIcons.I_PRIORITY_HIGH);
        sbf$batchButton.withCallback(() -> {
            int next = SBF_BATCH_STEPS[0];
            for (int step : SBF_BATCH_STEPS)
                if (step > sbf$batch) {
                    next = step;
                    break;
                }
            sbf$batch = next;
            sbf$refreshButtons();
        });
        ScreenInvoker invoker = (ScreenInvoker) (Object) this;
        invoker.sbf$addWidget(sbf$variantsButton);
        invoker.sbf$addWidget(sbf$batchButton);
        sbf$refreshButtons();
    }

    @Unique
    private void sbf$refreshButtons() {
        sbf$variantsButton.green = sbf$variants;
        sbf$variantsButton.setToolTip(Component.translatable("createsmartbulkfactory.gui.variants")
                .append(Component.translatable(sbf$variants ? "createsmartbulkfactory.gui.on" : "createsmartbulkfactory.gui.off")));
        sbf$variantsButton.getToolTip().add(Component.translatable("createsmartbulkfactory.gui.variants.tip").withStyle(ChatFormatting.GRAY));
        sbf$batchButton.setToolTip(Component.translatable("createsmartbulkfactory.gui.batch", sbf$batch));
        sbf$batchButton.getToolTip().add(Component.translatable("createsmartbulkfactory.gui.batch.tip").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Create only offers crafting mode when every connected input gauge is the first match of some ingredient, so a
     * second gauge for another kind of planks hides the button. When nothing was found (and after other mods such as
     * Extra Gauges had their say, so their recipe types keep working), retry accepting extra inputs as alternatives.
     */
    @Inject(method = "searchForCraftingRecipe", at = @At("TAIL"))
    private void sbf$searchWithAlternatives(CallbackInfo ci) {
        if (restocker || availableCraftingRecipe != null || !((SbfGaugeOptions) behaviour).sbf$allowVariants())
            return;
        ItemStack output = outputConfig.stack;
        if (output.isEmpty() || behaviour.targetedBy.isEmpty())
            return;
        ClientLevel level = Minecraft.getInstance().level;
        availableCraftingRecipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(holder -> holder.value().getResultItem(level.registryAccess()).getItem() == output.getItem())
                .filter(holder -> !AllRecipeTypes.shouldIgnoreInAutomation(holder))
                .filter(holder -> sbf$usesEveryInput(holder.value()))
                .findAny().map(RecipeHolder::value).orElse(null);
    }

    @Unique
    private boolean sbf$usesEveryInput(CraftingRecipe recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty())
                continue;
            boolean available = false;
            for (BigItemStack input : inputConfig)
                if (!input.stack.isEmpty() && ingredient.test(input.stack)) {
                    available = true;
                    break;
                }
            if (!available)
                return false;
        }
        for (BigItemStack input : inputConfig) {
            if (input.stack.isEmpty())
                continue;
            boolean used = false;
            for (Ingredient ingredient : recipe.getIngredients())
                if (!ingredient.isEmpty() && ingredient.test(input.stack)) {
                    used = true;
                    break;
                }
            if (!used)
                return false;
        }
        return true;
    }

    @Inject(method = "renderWindow", at = @At("TAIL"))
    private void sbf$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (sbf$variantsButton == null)
            return;
        sbf$variantsButton.visible = true;
        sbf$variantsButton.active = true;
        sbf$batchButton.visible = craftingActive;
        sbf$batchButton.active = craftingActive;
    }

    @Inject(method = "sendIt", at = @At("HEAD"))
    private void sbf$send(FactoryPanelPosition toRemove, boolean clearPromises, CallbackInfo ci) {
        if (restocker || sbf$variantsButton == null)
            return;
        SbfGaugeOptions options = (SbfGaugeOptions) behaviour;
        options.sbf$setAllowVariants(sbf$variants);
        options.sbf$setMaxBatch(sbf$batch);
        FactoryPanelPosition position = behaviour.getPanelPosition();
        PacketDistributor.sendToServer(new SetGaugeOptionsPacket(position.pos(), position.slot().ordinal(), sbf$variants, sbf$batch));
    }
}
