package com.lt.bloodyfiend;

import com.lt.bloodyfiend.entity.BloodyFiendEntity;
import com.lt.bloodyfiend.network.ModNetwork;
import com.lt.bloodyfiend.registry.*;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(LtMod.MODID)
public class LtMod {
    public static final String MODID = "lt";

    public LtMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::attributes);
        bus.addListener(this::creative);
    }

    private void commonSetup(FMLCommonSetupEvent e) { e.enqueueWork(ModNetwork::register); }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.BLOODY_FIEND.get(), BloodyFiendEntity.createAttributes().build());
    }

    private void creative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(ModItems.BLOODY_FIEND_SPAWN_EGG);
    }
}
