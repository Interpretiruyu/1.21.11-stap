package com.example.examplemod;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = STapMod.MODID, value = Dist.CLIENT)
public class STapHandler {

    private static final int DURATION_TICKS = 2;      // 1 тик = 50 мс
    private static final boolean PARTICLES = true;
    private static final boolean SOUND = true;

    private static final SoundEvent HIT_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(STapMod.MODID, "hit"));
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF0000, 1.0f);

    private static int ticksLeft = 0;

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getEntity() != mc.player) return;

        ticksLeft = DURATION_TICKS;
        Entity target = event.getTarget();

        if (PARTICLES) {
            RandomSource rnd = mc.level.getRandom();
            for (int i = 0; i < 12; i++) {
                double x = target.getX() + (rnd.nextDouble() - 0.5) * target.getBbWidth();
                double y = target.getY() + rnd.nextDouble() * target.getBbHeight();
                double z = target.getZ() + (rnd.nextDouble() - 0.5) * target.getBbWidth();
                mc.level.addParticle(RED, x, y, z, 0.0, 0.05, 0.0);
            }
        }
        if (SOUND) {
            mc.level.playLocalSound(target.getX(), target.getY(), target.getZ(),
                    HIT_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f, false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            ticksLeft = 0;
            return;
        }
        if (ticksLeft > 0) {
            mc.options.keyDown.setDown(true);   // S
            mc.options.keyUp.setDown(false);    // W
            ticksLeft--;
            if (ticksLeft == 0) {
                KeyMapping.setAll();            // вернуть реальное состояние клавиш
            }
        }
    }
}
