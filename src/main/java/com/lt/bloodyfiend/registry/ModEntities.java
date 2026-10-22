package com.lt.bloodyfiend.registry;

import com.lt.bloodyfiend.LtMod;
import com.lt.bloodyfiend.entity.BloodyFiendEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LtMod.MODID);
    public static final TagKey<EntityType<?>> FIENDS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(LtMod.MODID, "fiends"));

    // collision_box_slumber (1.25 x 3.55) multiplied by minecraft:scale 1.7638
    public static final RegistryObject<EntityType<BloodyFiendEntity>> BLOODY_FIEND = ENTITIES.register("bloody_fiend",
            () -> EntityType.Builder.of(BloodyFiendEntity::new, MobCategory.MONSTER)
                    .sized(1.25F * BloodyFiendEntity.SCALE, 3.55F * BloodyFiendEntity.SCALE)
                    .fireImmune().clientTrackingRange(10).updateInterval(1)
                    .build(LtMod.MODID + ":bloody_fiend"));
}
