package com.lt.bloodyfiend.network;

import com.lt.bloodyfiend.LtMod;
import com.lt.bloodyfiend.client.CameraShakeHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

/** Replacement for Bedrock's "/camerashake add". */
public class ModNetwork {
    private static final String V = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LtMod.MODID, "main"), () -> V, V::equals, V::equals);

    public static void register() {
        CHANNEL.registerMessage(0, ShakePacket.class, ShakePacket::encode, ShakePacket::decode, ShakePacket::handle);
    }

    public static void sendShake(ServerPlayer p, float intensity, float seconds) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ShakePacket(intensity, seconds));
    }

    public record ShakePacket(float intensity, float seconds) {
        void encode(FriendlyByteBuf b) { b.writeFloat(intensity); b.writeFloat(seconds); }
        static ShakePacket decode(FriendlyByteBuf b) { return new ShakePacket(b.readFloat(), b.readFloat()); }
        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> CameraShakeHandler.start(intensity, seconds)));
            c.get().setPacketHandled(true);
        }
    }
}
