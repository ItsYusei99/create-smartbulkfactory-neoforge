package com.itsyusei99.smartbulkfactory;

import com.itsyusei99.smartbulkfactory.network.SetGaugeOptionsPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(SmartBulkFactory.MODID)
public class SmartBulkFactory {
    public static final String MODID = "createsmartbulkfactory";

    public SmartBulkFactory(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SbfConfig.SPEC);
        modBus.addListener(SmartBulkFactory::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SetGaugeOptionsPacket.TYPE, SetGaugeOptionsPacket.STREAM_CODEC, SetGaugeOptionsPacket::handle);
    }
}
