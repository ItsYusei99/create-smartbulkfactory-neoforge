package com.itsyusei99.smartbulkfactory.network;

import com.itsyusei99.smartbulkfactory.SbfGaugeOptions;
import com.itsyusei99.smartbulkfactory.SmartBulkFactory;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetGaugeOptionsPacket(BlockPos pos, int slot, boolean allowVariants, int maxBatch) implements CustomPacketPayload {
    public static final Type<SetGaugeOptionsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SmartBulkFactory.MODID, "set_gauge_options"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGaugeOptionsPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetGaugeOptionsPacket::pos,
            ByteBufCodecs.VAR_INT, SetGaugeOptionsPacket::slot,
            ByteBufCodecs.BOOL, SetGaugeOptionsPacket::allowVariants,
            ByteBufCodecs.VAR_INT, SetGaugeOptionsPacket::maxBatch,
            SetGaugeOptionsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetGaugeOptionsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.level().isLoaded(packet.pos) || player.distanceToSqr(packet.pos.getCenter()) > 64 * 64) return;
            if (!(player.level().getBlockEntity(packet.pos) instanceof FactoryPanelBlockEntity be)) return;
            PanelSlot[] slots = PanelSlot.values();
            if (packet.slot < 0 || packet.slot >= slots.length) return;
            FactoryPanelBehaviour behaviour = be.panels.get(slots[packet.slot]);
            if (behaviour == null) return;
            SbfGaugeOptions options = (SbfGaugeOptions) behaviour;
            options.sbf$setAllowVariants(packet.allowVariants);
            options.sbf$setMaxBatch(Math.max(1, Math.min(64, packet.maxBatch)));
            be.notifyUpdate();
        });
    }
}
