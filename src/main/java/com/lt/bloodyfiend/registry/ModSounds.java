package com.lt.bloodyfiend.registry;

import com.lt.bloodyfiend.LtMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LtMod.MODID);
    public static final RegistryObject<SoundEvent> AMBIENT = reg("mob.lt_blood_fiend.ambient");
    public static final RegistryObject<SoundEvent> HURT = reg("mob.lt_blood_fiend.hurt");
    public static final RegistryObject<SoundEvent> DEATH = reg("mob.lt_blood_fiend.death");
    public static final RegistryObject<SoundEvent> SMASH = reg("mob.lt_bloody_fiend.smash");

    private static RegistryObject<SoundEvent> reg(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LtMod.MODID, n)));
    }
}
