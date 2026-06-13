package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.MovementPacketListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.RotationUtils;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Comparator;
import java.util.Random;

public final class Hitboxes extends Module implements TickListener, MovementPacketListener, GameRenderListener, ItemUseListener {
    private final BooleanSetting visualHitbox = new BooleanSetting(EncryptedString.of("Visual Hitbox"), true);
    private final BooleanSetting fakeRotation = new BooleanSetting(EncryptedString.of("Fake Rotation"), true);
    private final NumberSetting hitboxMultiplier = new NumberSetting(EncryptedString.of("Hitbox Multiplier"), 0.1, 3.0, 1.6, 0.1);
    private final ModeSetting<RotationMode> rotationMode = new ModeSetting<>(EncryptedString.of("Rotation Mode"), RotationMode.Silent, RotationMode.class);
    private final ModeSetting<TargetPart> targetPart = new ModeSetting<>(EncryptedString.of("Target Part"), TargetPart.Head, TargetPart.class);
    private final ModeSetting<TargetPriority> targetPriority = new ModeSetting<>(EncryptedString.of("Target Priority"), TargetPriority.Nearest, TargetPriority.class);
    private final NumberSetting smoothness = new NumberSetting(EncryptedString.of("Smoothness"), 0.1, 1.0, 0.35, 0.05);
    private final NumberSetting jitter = new NumberSetting(EncryptedString.of("Jitter"), 0.0, 6.0, 1.0, 0.1);
    private final NumberSetting maxAngle = new NumberSetting(EncryptedString.of("Max Angle"), 15.0, 180.0, 120.0, 1.0);
    private final NumberSetting red = new NumberSetting(EncryptedString.of("Neon Red"), 0.0, 255.0, 0.0, 1.0);
    private final NumberSetting green = new NumberSetting(EncryptedString.of("Neon Green"), 0.0, 255.0, 245.0, 1.0);
    private final NumberSetting blue = new NumberSetting(EncryptedString.of("Neon Blue"), 0.0, 255.0, 255.0, 1.0);

    private final Random random = new Random();
    private PlayerEntity target;
    private Rotation serverRotation;
    private int blockPlacePauseTicks;

    public enum RotationMode { Silent, Legit, Combined }
    public enum TargetPart { Head, Chest, Abdomen, Feet, Random }
    public enum TargetPriority { Nearest, Lowest_Health, Angle }

    public Hitboxes() {
        super(EncryptedString.of("Hitboxes"), EncryptedString.of("Expands player hitboxes with enhanced fake rotations"), -1, Category.COMBAT);
        addSettings(visualHitbox, fakeRotation, hitboxMultiplier, rotationMode, targetPart, targetPriority, smoothness, jitter, maxAngle, red, green, blue);
    }

    @Override public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(MovementPacketListener.class, this);
        eventManager.add(GameRenderListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        super.onEnable();
    }

    @Override public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(MovementPacketListener.class, this);
        eventManager.remove(GameRenderListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        target = null;
        serverRotation = null;
        super.onDisable();
    }

    @Override public void onTick() {
        if (!canRun()) { target = null; return; }
        if (blockPlacePauseTicks > 0) blockPlacePauseTicks--;
        target = findTarget();
        if (target == null) return;
        Rotation desired = RotationUtils.getDirection(mc.player, getAimPoint(target));
        desired = limitAngle(new Rotation(mc.player.getYaw(), mc.player.getPitch()), desired, maxAngle.getValueFloat());
        desired = addJitter(desired);
        serverRotation = serverRotation == null ? desired : RotationUtils.getSmoothRotation(serverRotation, desired, easedSpeed());

        if (fakeRotation.getValue() && (rotationMode.isMode(RotationMode.Legit) || rotationMode.isMode(RotationMode.Combined)) && blockPlacePauseTicks == 0) {
            mc.player.setYaw((float) serverRotation.yaw());
            mc.player.setPitch((float) MathHelper.clamp(serverRotation.pitch(), -90.0, 90.0));
        }
    }

    @Override public void onSendMovementPackets() {
        if (!canRun() || !fakeRotation.getValue() || blockPlacePauseTicks > 0 || serverRotation == null) return;
        if (rotationMode.isMode(RotationMode.Silent) || rotationMode.isMode(RotationMode.Combined)) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float) serverRotation.yaw(), (float) serverRotation.pitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
        }
    }

    @Override public void onItemUse(ItemUseEvent event) {
        if (mc.player == null || mc.crosshairTarget == null) return;
        if (mc.player.getStackInHand(Hand.MAIN_HAND).getItem() instanceof BlockItem) blockPlacePauseTicks = 4;
    }

    @Override public void onGameRender(GameRenderEvent event) {
        if (!canRun() || !visualHitbox.getValue()) return;
        Color fill = new Color(red.getValueInt(), green.getValueInt(), blue.getValueInt(), 45);
        Color outline = new Color(red.getValueInt(), green.getValueInt(), blue.getValueInt(), 210);
        Vec3d cam = RenderUtils.getCameraPos();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player != mc.player && !player.isRemoved() && player.isAlive()) {
                Box box = getExpandedBox(player).offset(-cam.x, -cam.y, -cam.z);
                RenderUtils.renderFilledBox(event.matrices, (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, fill);
                drawBoxOutline(event, box, outline);
            }
        }
    }

    private void drawBoxOutline(GameRenderEvent event, Box b, Color c) {
        Vec3d[] p = {new Vec3d(b.minX,b.minY,b.minZ),new Vec3d(b.maxX,b.minY,b.minZ),new Vec3d(b.maxX,b.minY,b.maxZ),new Vec3d(b.minX,b.minY,b.maxZ),new Vec3d(b.minX,b.maxY,b.minZ),new Vec3d(b.maxX,b.maxY,b.minZ),new Vec3d(b.maxX,b.maxY,b.maxZ),new Vec3d(b.minX,b.maxY,b.maxZ)};
        int[][] e = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : e) RenderUtils.renderLine(event.matrices, c, p[edge[0]], p[edge[1]]);
    }

    private boolean canRun() { return mc.player != null && mc.world != null && isHoldingWeapon(mc.player); }
    private boolean isHoldingWeapon(ClientPlayerEntity player) {
        Item item = player.getMainHandStack().getItem();
        Item off = player.getOffHandStack().getItem();
        return isWeapon(item) || isWeapon(off);
    }
    private boolean isWeapon(Item item) { return item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem || item == Items.END_CRYSTAL || item == Items.RESPAWN_ANCHOR || item == Items.OBSIDIAN; }

    private PlayerEntity findTarget() {
        Comparator<PlayerEntity> comparator = switch (targetPriority.getMode()) {
            case Lowest_Health -> Comparator.comparingDouble(PlayerEntity::getHealth);
            case Angle -> Comparator.comparingDouble(p -> RotationUtils.getAngleToRotation(RotationUtils.getDirection(mc.player, getAimPoint(p))));
            case Nearest -> Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p));
        };
        return mc.world.getPlayers().stream().filter(p -> p != mc.player && p.isAlive() && !p.isSpectator()).filter(p -> RotationUtils.getAngleToRotation(RotationUtils.getDirection(mc.player, getAimPoint(p))) <= maxAngle.getValue()).min(comparator).orElse(null);
    }

    private Vec3d getAimPoint(PlayerEntity player) {
        Box box = player.getBoundingBox();
        double y = switch (targetPart.getMode()) { case Head -> box.maxY - 0.12; case Chest -> box.minY + player.getHeight() * 0.72; case Abdomen -> box.minY + player.getHeight() * 0.48; case Feet -> box.minY + 0.15; case Random -> box.minY + player.getHeight() * (0.15 + random.nextDouble() * 0.75); };
        return new Vec3d(player.getX(), y, player.getZ());
    }

    private Rotation addJitter(Rotation rotation) {
        double amount = jitter.getValue();
        if (amount <= 0) return rotation;
        return new Rotation(rotation.yaw() + (random.nextDouble() - 0.5) * amount, MathHelper.clamp(rotation.pitch() + (random.nextDouble() - 0.5) * amount, -90, 90));
    }

    private Rotation limitAngle(Rotation from, Rotation to, float max) {
        float yawDelta = MathHelper.clamp(MathHelper.wrapDegrees((float) (to.yaw() - from.yaw())), -max, max);
        float pitchDelta = MathHelper.clamp(MathHelper.wrapDegrees((float) (to.pitch() - from.pitch())), -max, max);
        return new Rotation(from.yaw() + yawDelta, MathHelper.clamp(from.pitch() + pitchDelta, -90, 90));
    }

    private double easedSpeed() { double v = smoothness.getValue(); return 1.0 - Math.pow(1.0 - v, 2.0); }
    public boolean shouldExpandEntity(Entity entity) { return canRun() && entity instanceof PlayerEntity && entity != mc.player; }
    public Box getExpandedBox(Entity entity) { double extra = (hitboxMultiplier.getValue() - 1.0) * entity.getWidth() * 0.5; return entity.getBoundingBox().expand(Math.max(0, extra), 0, Math.max(0, extra)); }
    public float getTargetingMargin(Entity entity, float original) { return shouldExpandEntity(entity) ? (float) (original + Math.max(0, (hitboxMultiplier.getValue() - 1.0) * entity.getWidth() * 0.5)) : original; }
}
