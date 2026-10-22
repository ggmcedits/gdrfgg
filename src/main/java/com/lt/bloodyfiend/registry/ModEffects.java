package com.lt.bloodyfiend.registry;

import com.lt.bloodyfiend.LtMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Java-native versions of the add-on's dynamic-property effects "blood_curse" and "anti_healing". */
public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, LtMod.MODID);
    public static final RegistryObject<MobEffect> BLOOD_CURSE = EFFECTS.register("blood_curse", () -> new Simple(0x6E000A));
    public static final RegistryObject<MobEffect> ANTI_HEALING = EFFECTS.register("anti_healing", () -> new Simple(0x3C3C3C));

    static class Simple extends MobEffect {
        Simple(int color) { super(MobEffectCategory.HARMFUL, color); }
    }
}
