package com.lt.bloodyfiend.event;

import com.lt.bloodyfiend.LtMod;
import com.lt.bloodyfiend.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LtMod.MODID)
public class CommonEvents {
    /** Bedrock's applyDamage() does no knockback; the boss applies its own via impulse(). */
    public static boolean suppressKnockback = false;

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        if (suppressKnockback) e.setCanceled(true);
    }

    /** anti_healing: every heal instance is reduced by the effect level (misc.js entityHealthChanged). */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        MobEffectInstance i = e.getEntity().getEffect(ModEffects.ANTI_HEALING.get());
        if (i == null) return;
        float v = e.getAmount() - (i.getAmplifier() + 1);
        if (v <= 0) e.setCanceled(true); else e.setAmount(v);
    }
}
