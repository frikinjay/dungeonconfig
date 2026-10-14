package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.mixin.client.ChannelAccessor;
import com.frikinjay.dungeonconfig.mixin.client.SoundEngineAccessor;
import com.frikinjay.dungeonconfig.mixin.client.SoundManagerAccessor;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTDirectChannels;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jetbrains.annotations.Nullable;

public final class DungeonsAudio {

    private DungeonsAudio() {}

    private static final class Instance extends AbstractSoundInstance implements TickableSoundInstance {
        private final Identifier stream;
        private boolean stopped;

        Instance(Identifier id, Identifier stream) {
            super(id, SoundSource.MASTER, RandomSource.create());
            this.stream = stream;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.looping = false;
            this.volume = 0;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            this.sound = new Sound(stream, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
            return new WeighedSoundEvents(this.identifier, null);
        }

        void setVolume(float v) {
            this.volume = v;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
            DungeonsMusic.tick(this);
        }
    }

    @Nullable
    static Object play(String namespace, String path, float volume) {
        Instance instance = new Instance(Identifier.fromNamespaceAndPath(namespace, "dungeonsui_music"),
                Identifier.fromNamespaceAndPath(namespace, path));
        instance.setVolume(volume);
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        sounds.play(instance);
        if (!sounds.isActive(instance)) return null;
        ChannelAccess.ChannelHandle handle = channel(instance);
        if (handle != null) handle.execute(DungeonsAudio::directChannels);
        return instance;
    }

    @Nullable
    private static ChannelAccess.ChannelHandle channel(Instance instance) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        return ((SoundEngineAccessor) ((SoundManagerAccessor) sounds).dungeonconfig$engine()).dungeonconfig$channels().get(instance);
    }

    private static void directChannels(Channel channel) {
        try {
            if (!AL10.alIsExtensionPresent("AL_SOFT_direct_channels")) return;
            int source = ((ChannelAccessor) (Object) channel).dungeonconfig$source();
            AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL10.AL_TRUE);
        } catch (Throwable ignored) {
        }
    }

    static void volume(Object instance, float volume) {
        if (!(instance instanceof Instance i)) return;
        i.setVolume(volume);
        ChannelAccess.ChannelHandle handle = channel(i);
        if (handle == null) return;
        float gain = Math.max(0, Math.min(1, volume)) * Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.MASTER);
        handle.execute(channel -> channel.setVolume(gain));
    }

    static void stop(Object instance) {
        if (!(instance instanceof Instance i)) return;
        i.stopped = true;
        Minecraft.getInstance().getSoundManager().stop(i);
    }

    static boolean playing(Object instance) {
        return instance instanceof Instance i && Minecraft.getInstance().getSoundManager().isActive(i);
    }

    static void stopVanillaMusic() {
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    static boolean paused() {
        return Minecraft.getInstance().isPaused();
    }
}
