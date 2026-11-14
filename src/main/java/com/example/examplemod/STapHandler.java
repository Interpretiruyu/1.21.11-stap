package com.example.examplemod;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = STapMod.MODID, value = Dist.CLIENT)
public class STapHandler {

    // --- S-tap ---
    private static final int DURATION_TICKS = 2;       // 1 тик = 50 мс
    private static final boolean PARTICLES = true;
    private static final boolean SOUND = true;

    // --- наведение камеры ---
    private static final boolean AIM = true;
    private static final int AIM_TICKS = 20;           // 20 тиков = 1 секунда
    private static final float AIM_SMOOTH = 0.2f;      // доля поворота за тик: 0.05 медленно, 0.5 резко
    private static final double AIM_HEIGHT = 0.8;      // куда целиться: 0 ноги, 0.5 центр, 0.8 грудь/голова

    private static final SoundEvent HIT_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(STapMod.MODID, "hit"));
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF0000, 1.0f);

    private static int ticksLeft = 0;
    private static Entity aimTarget = null;
    private static int aimTicksLeft = 0;

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getEntity() != mc.player) return;

        ticksLeft = DURATION_TICKS;
        Entity target = event.getTarget();

        if (AIM) {
            aimTarget = target;
            aimTicksLeft = AIM_TICKS;
        }

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

    @SubscribeEvent
    public static void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        if (aimTicksLeft <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || aimTarget == null || !aimTarget.isAlive()) {
            aimTicksLeft = 0;
            aimTarget = null;
            return;
        }
        aimTicksLeft--;

        Vec3 eye = player.getEyePosition();
        double dx = aimTarget.getX() - eye.x;
        double dy = aimTarget.getY() + aimTarget.getBbHeight() * AIM_HEIGHT - eye.y;
        double dz = aimTarget.getZ() - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float wantYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float wantPitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0 / Math.PI)));

        float yaw = player.getYRot();
        float pitch = player.getXRot();

        // wrapDegrees выбирает кратчайший путь (без разворота «в обход»)
        float newYaw = yaw + Mth.wrapDegrees(wantYaw - yaw) * AIM_SMOOTH;
        float newPitch = Mth.clamp(pitch + (wantPitch - pitch) * AIM_SMOOTH, -90.0f, 90.0f);

        player.setYRot(newYaw);
        player.setXRot(newPitch);

        if (aimTicksLeft == 0) {
            aimTarget = null;
        }
    }
}
