package com.itsyusei99.smartbulkfactory.logic;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Detects a crafter whose "all preceding crafters finished" notification was lost (typically because a chunk of the
 * crafter array unloaded mid-craft). Create sends that notification exactly once and an ACCEPTING crafter ignores its
 * own tick, so without this the whole group stays frozen holding the items of the last craft.
 */
public final class CrafterRecovery {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Field PHASE = findPhaseField();

    private CrafterRecovery() {
    }

    /** True when Create itself would immediately advance this crafter to ASSEMBLING, i.e. the handshake was missed. */
    public static boolean hasMissedHandshake(MechanicalCrafterBlockEntity crafter) {
        if (PHASE == null || crafter.getLevel() == null || crafter.getLevel().isClientSide)
            return false;
        if (!"ACCEPTING".equals(phaseName(crafter)))
            return false;
        List<MechanicalCrafterBlockEntity> preceding = RecipeGridHandler.getPrecedingCrafters(crafter);
        if (preceding == null || preceding.isEmpty())
            return false;
        for (MechanicalCrafterBlockEntity other : preceding)
            if (!"WAITING".equals(phaseName(other)))
                return false;
        LOGGER.info("Resuming stalled mechanical crafter group at {}", crafter.getBlockPos());
        return true;
    }

    private static String phaseName(MechanicalCrafterBlockEntity crafter) {
        try {
            return ((Enum<?>) PHASE.get(crafter)).name();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    private static Field findPhaseField() {
        try {
            Field field = MechanicalCrafterBlockEntity.class.getDeclaredField("phase");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Crafter recovery disabled: cannot access MechanicalCrafterBlockEntity.phase", e);
            return null;
        }
    }
}
