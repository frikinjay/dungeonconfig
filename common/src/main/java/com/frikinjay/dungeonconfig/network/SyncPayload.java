package com.frikinjay.dungeonconfig.network;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record SyncPayload(String modId, String json) implements CustomPacketPayload {

    public static final Type<@NotNull SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DungeonConfigMod.MOD_ID, "sync"));

    public static final StreamCodec<ByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), SyncPayload::modId,
            ByteBufCodecs.stringUtf8(1 << 20), SyncPayload::json,
            SyncPayload::new);

    @Override
    public @NotNull Type<? extends @NotNull CustomPacketPayload> type() {
        return TYPE;
    }
}
