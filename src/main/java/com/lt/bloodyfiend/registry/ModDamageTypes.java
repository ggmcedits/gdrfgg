package com.lt.bloodyfiend.registry;

import com.lt.bloodyfiend.LtMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

/** Equivalents of the Bedrock damage causes "selfDestruct", "override" and "contact". */
public class ModDamageTypes {
    public static final ResourceKey<DamageType> SELF_DESTRUCT = key("self_destruct");
    public static final ResourceKey<DamageType> OVERRIDE = key("override");
    public static final ResourceKey<DamageType> CONTACT = key("contact");

    private static ResourceKey<DamageType> key(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(LtMod.MODID, n));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> k, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(k), attacker);
    }
}
