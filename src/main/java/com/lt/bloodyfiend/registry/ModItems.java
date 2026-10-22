package com.lt.bloodyfiend.registry;

import com.lt.bloodyfiend.LtMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LtMod.MODID);
    // white tint so the add-on's own spawn-egg texture is shown untinted
    public static final RegistryObject<Item> BLOODY_FIEND_SPAWN_EGG = ITEMS.register("bloody_fiend_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BLOODY_FIEND, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));
}
