package com.lt.bloodyfiend.client;

import com.lt.bloodyfiend.LtMod;
import com.lt.bloodyfiend.entity.BloodyFiendEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.lt.bloodyfiend.registry.ModEntities;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BloodyFiendRenderer extends GeoEntityRenderer<BloodyFiendEntity> {
    public BloodyFiendRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.withScale(BloodyFiendEntity.SCALE); // minecraft:scale
        this.shadowRadius = 1.5F;
    }

    static class Model extends GeoModel<BloodyFiendEntity> {
        @Override public ResourceLocation getModelResource(BloodyFiendEntity e) { return new ResourceLocation(LtMod.MODID, "geo/entity/bloody_fiend.geo.json"); }
        @Override public ResourceLocation getTextureResource(BloodyFiendEntity e) { return new ResourceLocation(LtMod.MODID, "textures/entity/bloody_fiend.png"); }
        @Override public ResourceLocation getAnimationResource(BloodyFiendEntity e) { return new ResourceLocation(LtMod.MODID, "animations/entity/bloody_fiend.animation.json"); }
    }

    @Mod.EventBusSubscriber(modid = LtMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Reg {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ModEntities.BLOODY_FIEND.get(), BloodyFiendRenderer::new);
        }
    }
}
