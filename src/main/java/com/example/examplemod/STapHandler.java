
package com.example.examplemod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = STapMod.MODID, value = Dist.CLIENT)
public class STapHandler {

    // ---------- S-tap ----------
    private static final int DURATION_TICKS = 2;
    private static final int STAP_FROM_HIT = 2;
    private static final int COMBO_TIMEOUT_TICKS = 40;

    // ---------- эффекты ----------
    private static final boolean PARTICLES = true;
    private static final boolean SOUND = true;

    // ---------- наведение камеры ----------
    private static final boolean AIM = true;
    private static final boolean AIM_WHEN_LOCKED = true;
    private static final int AIM_TICKS = 20;
    private static final float AIM_SMOOTH = 0.2f;
    private static final double AIM_HEIGHT = 0.8;

    // ---------- перл ----------
    private static final double PEARL_OFFSET = 1.5;
    private static final double PEARL_MAX_ERROR = 3.0;
    private static final boolean RESTORE_ROTATION = true;

    // ---------- автоматическая еда ----------
    private static final float FOOD_HEALTH_LIMIT = 6.0f;
    private static final int FOOD_TIMEOUT = 0.5;

    private static boolean usingFood = false;
    private static boolean foodUseStarted = false;

    private static int foodUseTicks = 0;
    private static int previousFoodSlot = -1;
    private static int foodInventorySlot = -1;

    private static final SoundEvent HIT_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(STapMod.MODID, "hit"));

    private static final DustParticleOptions RED =
            new DustParticleOptions(0xFF0000, 1.0f);

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(STapMod.MODID, "main"));

    private static final KeyMapping PEARL_KEY =
            new KeyMapping("key.stap.pearl", InputConstants.KEY_Z, CATEGORY);

    private static final KeyMapping LOCK_KEY =
            new KeyMapping("key.stap.lock", InputConstants.KEY_X, CATEGORY);

    private static int ticksLeft = 0;

    private static Entity comboTarget = null;
    private static int comboHits = 0;
    private static int comboTimer = 0;

    private static Entity aimTarget = null;
    private static int aimTicksLeft = 0;

    // ---------- блокировка камеры ----------
    private static boolean cameraLocked = false;
    private static float lockYaw = 0f;
    private static float lockPitch = 0f;
    private static LocalPlayer lockOwner = null;

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(PEARL_KEY);
        event.register(LOCK_KEY);
    }

    // ================= удар =================
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || event.getEntity() != mc.player) {
            return;
        }

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
            mc.level.playLocalSound(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    HIT_SOUND,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f,
                    false
            );
        }

        // Наведение камеры
        if (AIM) {
            aimTarget = target;
            aimTicksLeft = AIM_TICKS;
        }

        // S-tap
        if (isHitSuccessful(target)) {
            if (target == comboTarget && comboTimer > 0) {
                comboHits++;
            } else {
                comboTarget = target;
                comboHits = 1;
            }

            comboTimer = COMBO_TIMEOUT_TICKS;

            if (comboHits >= STAP_FROM_HIT) {
                ticksLeft = DURATION_TICKS;
            }
        }
    }

    private static boolean isHitSuccessful(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return true;
        }

        return living.isAlive() && living.hurtTime == 0;
    }

    // ================= автоматическая еда =================

    private static void handleAutoFood(Minecraft mc) {
        LocalPlayer player = mc.player;

        if (player == null || mc.gameMode == null) {
            return;
        }

        if (usingFood) {
            foodUseTicks++;

            if (player.isUsingItem()) {
                foodUseStarted = true;
            }

            if ((foodUseStarted && !player.isUsingItem())
                    || foodUseTicks >= FOOD_TIMEOUT) {
                stopAutoFood(mc);
            }

            return;
        }

        if (player.getHealth() >= FOOD_HEALTH_LIMIT || player.isUsingItem()) {
            return;
        }

        Inventory inv = player.getInventory();

        int foundSlot = -1;

        // Поиск еды во всём инвентаре
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);

            if (!stack.isEmpty() && stack.getFoodProperties(player) != null) {
                foundSlot = i;
                break;
            }
        }

        if (foundSlot == -1) {
            return;
        }

        previousFoodSlot = inv.getSelectedSlot();
        foodInventorySlot = foundSlot;

        // Если еда находится в основном инвентаре,
        // временно меняем её местами с предметом в руке
        if (foundSlot >= 9) {
            ItemStack foodStack = inv.getItem(foundSlot);
            ItemStack handStack = inv.getItem(previousFoodSlot);

            inv.setItem(foundSlot, handStack);
            inv.setItem(previousFoodSlot, foodStack);
        } else {
            inv.setSelectedSlot(foundSlot);
        }

        usingFood = true;
        foodUseStarted = false;
        foodUseTicks = 0;

        // Зажимаем ПКМ
        mc.options.keyUse.setDown(true);
    }

    private static void stopAutoFood(Minecraft mc) {
        mc.options.keyUse.setDown(false);

        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();

            // Возвращаем предметы на прежние места
            if (foodInventorySlot >= 9 && previousFoodSlot >= 0) {
                ItemStack foodStack = inv.getItem(previousFoodSlot);
                ItemStack handStack = inv.getItem(foodInventorySlot);

                inv.setItem(foodInventorySlot, foodStack);
                inv.setItem(previousFoodSlot, handStack);
            }

            if (previousFoodSlot >= 0) {
                inv.setSelectedSlot(previousFoodSlot);
            }
        }

        usingFood = false;
        foodUseStarted = false;
        foodUseTicks = 0;

        previousFoodSlot = -1;
        foodInventorySlot = -1;
    }

    // ================= тик (до) =================
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();

        if (comboTimer > 0 && --comboTimer == 0) {
            comboTarget = null;
            comboHits = 0;
        }

        // Нет игрока
        if (mc.player == null || (cameraLocked && mc.player != lockOwner)) {
            if (usingFood) {
                mc.options.keyUse.setDown(false);
                usingFood = false;
                foodUseStarted = false;
                foodUseTicks = 0;
                previousFoodSlot = -1;
                foodInventorySlot = -1;
            }

            cameraLocked = false;
            lockOwner = null;
        }

        if (mc.player == null) {
            ticksLeft = 0;
            return;
        }

        if (mc.screen != null) {
            if (usingFood) {
                stopAutoFood(mc);
            }

            ticksLeft = 0;
            return;
        }

        // Автоматическое использование еды
        handleAutoFood(mc);

        while (LOCK_KEY.consumeClick()) {
            cameraLocked = !cameraLocked;

            if (cameraLocked) {
                lockYaw = mc.player.getYRot();
                lockPitch = mc.player.getXRot();
                lockOwner = mc.player;
            } else {
                lockOwner = null;
            }

            mc.player.displayClientMessage(
                    Component.literal(
                            cameraLocked ? "Камера заблокирована" : "Камера разблокирована"
                    ),
                    true
            );
        }

        while (PEARL_KEY.consumeClick()) {
            throwPearlAtNearestPlayer(mc);
        }

        if (cameraLocked) {
            mc.player.setYRot(lockYaw);
            mc.player.setXRot(lockPitch);
        }

        if (ticksLeft > 0) {
            mc.options.keyDown.setDown(true);
            mc.options.keyUp.setDown(false);

            ticksLeft--;

            if (ticksLeft == 0) {
                KeyMapping.setAll();
            }
        }
    }

    // ================= тик (после): наведение камеры =================
    @SubscribeEvent
    public static void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        if (aimTicksLeft <= 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.screen != null || aimTarget == null
                || !aimTarget.isAlive()
                || (cameraLocked && !AIM_WHEN_LOCKED)) {

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

        player.setYRot(yaw + Mth.wrapDegrees(wantYaw - yaw) * AIM_SMOOTH);

        player.setXRot(
                Mth.clamp(pitch + (wantPitch - pitch) * AIM_SMOOTH, -90.0f, 90.0f)
        );

        syncLock(player);

        if (aimTicksLeft == 0) {
            aimTarget = null;
        }
    }

    // ================= блокировка камеры =================

    private static void syncLock(LocalPlayer player) {
        if (cameraLocked) {
            lockYaw = player.getYRot();
            lockPitch = player.getXRot();
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!cameraLocked) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.screen != null) {
            return;
        }

        player.setYRot(lockYaw);
        player.setXRot(lockPitch);

        if (!event.getCamera().isDetached()) {
            event.setYaw(lockYaw);
            event.setPitch(lockPitch);
        }
    }

    // ================= перл =================

    private static void throwPearlAtNearestPlayer(Minecraft mc) {
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        Inventory inv = player.getInventory();

        int pearlSlot = -1;

        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(Items.ENDER_PEARL)) {
                pearlSlot = i;
                break;
            }
        }

        if (pearlSlot == -1) {
            boolean inBackpack = false;

            for (int i = 9; i < 36; i++) {
                if (inv.getItem(i).is(Items.ENDER_PEARL)) {
                    inBackpack = true;
                    break;
                }
            }

            player.displayClientMessage(
                    Component.literal(
                            inBackpack ? "Перл есть, но не в хотбаре" : "Перлов нет"
                    ),
                    true
            );

            return;
        }

        AbstractClientPlayer nearest = findNearestPlayer(mc);

        if (nearest == null) {
            player.displayClientMessage(
                    Component.literal("Рядом нет игроков"),
                    true
            );
            return;
        }

        float[] aim = computePearlAim(player, nearest);

        if (aim == null) {
            player.displayClientMessage(
                    Component.literal("Игрок слишком далеко для броска"),
                    true
            );
            return;
        }

        int prevSlot = inv.getSelectedSlot();

        float prevYaw = player.getYRot();
        float prevPitch = player.getXRot();

        inv.setSelectedSlot(pearlSlot);

        player.setYRot(aim[0]);
        player.setXRot(aim[1]);

        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);

        inv.setSelectedSlot(prevSlot);

        if (RESTORE_ROTATION) {
            player.setYRot(prevYaw);
            player.setXRot(prevPitch);
        }

        syncLock(player);
    }

    private static AbstractClientPlayer findNearestPlayer(Minecraft mc) {
        AbstractClientPlayer best = null;
        double bestDist = Double.MAX_VALUE;

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) {
                continue;
            }

            double d = mc.player.distanceToSqr(p);

            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }

        return best;
    }

    private static float[] computePearlAim(LocalPlayer player, Entity target) {
        Vec3 start = new Vec3(
                player.getX(),
                player.getEyeY() - 0.1,
                player.getZ()
        );

        double hx = target.getX() - player.getX();
        double hz = target.getZ() - player.getZ();

        double hd = Math.sqrt(hx * hx + hz * hz);

        double destX = target.getX();
        double destZ = target.getZ();

        if (hd > 1.0e-4) {
            destX -= hx / hd * PEARL_OFFSET;
            destZ -= hz / hd * PEARL_OFFSET;
        }

        double destY = target.getY();

        double dx = destX - start.x;
        double dz = destZ - start.z;

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        double yawRad = yaw * (Math.PI / 180.0);

        Vec3 pv = player.getDeltaMovement();

        double vx0 = pv.x;
        double vz0 = pv.z;
        double vy0 = player.onGround() ? 0.0 : pv.y;

        float bestPitch = 0f;
        double bestErr = Double.MAX_VALUE;

        for (float pitch = 45f; pitch >= -89f; pitch -= 0.25f) {
            double pr = pitch * (Math.PI / 180.0);

            double vx = -Math.sin(yawRad) * Math.cos(pr) * 1.5 + vx0;
            double vy = -Math.sin(pr) * 1.5 + vy0;
            double vz = Math.cos(yawRad) * Math.cos(pr) * 1.5 + vz0;

            double x = start.x;
            double y = start.y;
            double z = start.z;

            for (int t = 0; t < 300; t++) {
                double nx = x + vx;
                double ny = y + vy;
                double nz = z + vz;

                if (y >= destY && ny < destY) {
                    double k = (y - destY) / (y - ny);

                    double cx = x + (nx - x) * k;
                    double cz = z + (nz - z) * k;

                    double err = Math.hypot(cx - destX, cz - destZ);

                    if (err < bestErr - 0.01) {
                        bestErr = err;
                        bestPitch = pitch;
                    }

                    break;
                }

                x = nx;
                y = ny;
                z = nz;

                vx *= 0.99;
                vy = vy * 0.99 - 0.03;
                vz *= 0.99;

                if (y < destY - 64) {
                    break;
                }
            }
        }

        if (bestErr > PEARL_MAX_ERROR) {
            return null;
        }

        return new float[]{yaw, bestPitch};
    }
}
