package com.lt.bloodyfiend.client;

import com.lt.bloodyfiend.LtMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Random;

@Mod.EventBusSubscriber(modid = LtMod.MODID, value = Dist.CLIENT)
public class CameraShakeHandler {
    private static long start, end;
    private static float intensity;
    private static final Random RNG = new Random();

    public static void start(float in, float seconds) {
        long now = System.currentTimeMillis();
        if (now < end && in < intensity) return; // keep the stronger running shake
        intensity = in; start = now; end = now + (long) (seconds * 1000);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        long now = System.currentTimeMillis();
        if (now >= end || Minecraft.getInstance().isPaused()) return;
        float remain = (end - now) / (float) Math.max(1, end - start);
        float amp = intensity * Math.min(1F, remain * 3F);
        e.setYaw(e.getYaw() + (RNG.nextFloat() - 0.5F) * 2F * amp * 0.9F);
        e.setPitch(e.getPitch() + (RNG.nextFloat() - 0.5F) * 2F * amp * 0.9F);
        e.setRoll(e.getRoll() + (RNG.nextFloat() - 0.5F) * 2F * amp * 0.6F);
    }
}
