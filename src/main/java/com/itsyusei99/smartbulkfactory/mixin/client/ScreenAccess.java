package com.itsyusei99.smartbulkfactory.mixin.client;

import net.createmod.catnip.gui.AbstractSimiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractSimiScreen.class, remap = false)
public interface ScreenAccess {
    @Accessor("guiLeft")
    int sbf$guiLeft();

    @Accessor("guiTop")
    int sbf$guiTop();

    @Accessor("windowHeight")
    int sbf$windowHeight();
}
