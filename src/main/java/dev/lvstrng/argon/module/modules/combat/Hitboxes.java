package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RotationUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class Hitboxes extends Module implements TickListener, ItemUseListener {

    private final ModeSetting<ExpandMode> mode = new ModeSetting<>(
            EncryptedString.of("Expand Mode"), ExpandMode.Blatant, ExpandMode.class)
            .setDescription(EncryptedString.of("Blatant uses the full multiplier, Legit scales it down"));

    private final NumberSetting hitboxMultiplier = new NumberSetting(
            EncryptedString.of("Player Multiplier"), 1.0, 4.0, 1.2, 0.05)
            .setDescription(EncryptedString.of("Multiplies player hitbox reach expansion while holding a weapon"));

    private final BooleanSetting onlyWeapon = new BooleanSetting(
            EncryptedString.of("Only Weapon"), true)
            .setDescription(EncryptedString.of("Only expands hitboxes while holding a weapon"));

    private final BooleanSetting fakeRotations = new BooleanSetting(
            EncryptedString.of("Fake Rotations"), true)
            .setDescription(EncryptedString.of("Smoothly tracks the nearest target with realistic head rotations"));

    private final NumberSetting rotationRange = new NumberSetting(
            EncryptedString.of("Rotation Range"), 2.0, 8.0, 4.5, 0.5)
            .setDescription(EncryptedString.of("Range for enhanced fake rotation target tracking"));

    private final NumberSetting rotationSpeed = new NumberSetting(
            EncryptedString.of("Rotation Speed"), 1.0, 20.0, 7.0, 0.5)
            .setDescription(EncryptedString.of("Smoothing speed for realistic fake head tracking"));

    private float fakeYaw;
    private float fakePitch;
    private int placementDisableTicks;
    private int rotationPacketTimer;

    public enum ExpandMode {
        Blatant, Legit
    }

    public Hitboxes() {
        super(EncryptedString.of("Hitboxes"),
                EncryptedString.of("Weapon-only player hitbox multiplier with smooth fake rotations"),
                -1,
                Category.COMBAT);
        addSettings(mode, hitboxMultiplier, onlyWeapon, fakeRotations, rotationRange, rotationSpeed);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        if (mc.player != null) {
            fakeYaw = mc.player.getYaw();
            fakePitch = mc.player.getPitch();
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        placementDisableTicks = 0;
        rotationPacketTimer = 0;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (placementDisableTicks > 0) placementDisableTicks--;

        if (!isHoldingWeapon() || isPlacementDisabled()) {
            fakeYaw = mc.player.getYaw();
            fakePitch = mc.player.getPitch();
            return;
        }

        updateFakeRotations();
    }

    @Override
    public void onItemUse(ItemUseListener.ItemUseEvent event) {
        if (mc.player == null) return;
        Item item = mc.player.getMainHandStack().getItem();
        if (item instanceof BlockItem) placementDisableTicks = 5;
    }

    private void updateFakeRotations() {
        if (!fakeRotations.getValue()) return;

        PlayerEntity target = findClosestTarget();
        if (target == null) {
            fakeYaw = smoothAngle(fakeYaw, mc.player.getYaw(), 0.25F);
            fakePitch = smoothAngle(fakePitch, mc.player.getPitch(), 0.25F);
            applyFakeHeadRotation();
            return;
        }

        Vec3d targetPos = target.getEyePos();
        Rotation rotation = RotationUtils.getDirection(mc.player, targetPos);
        float speed = MathHelper.clamp(rotationSpeed.getValueFloat() / 25.0F, 0.04F, 0.8F);
        fakeYaw = smoothAngle(fakeYaw, (float) rotation.yaw(), speed);
        fakePitch = smoothAngle(fakePitch, (float) rotation.pitch(), speed);
        applyFakeHeadRotation();
    }

    private PlayerEntity findClosestTarget() {
        Entity entity = WorldUtils.findNearestEntity(mc.player, rotationRange.getValueFloat(), true);
        return entity instanceof PlayerEntity player && player != mc.player ? player : null;
    }

    private void applyFakeHeadRotation() {
        mc.player.setHeadYaw(fakeYaw);
        mc.player.setBodyYaw(smoothAngle(mc.player.bodyYaw, fakeYaw, 0.15F));
        rotationPacketTimer++;
        if (rotationPacketTimer >= 3 && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    fakeYaw,
                    MathHelper.clamp(fakePitch, -89.0F, 89.0F),
                    mc.player.isOnGround(),
                    mc.player.horizontalCollision));
            rotationPacketTimer = 0;
        }
    }

    private float smoothAngle(float from, float to, float speed) {
        return from + MathHelper.wrapDegrees(to - from) * speed;
    }

    private boolean isPlacementDisabled() {
        return placementDisableTicks > 0;
    }

    private boolean isHoldingWeapon() {
        if (!onlyWeapon.getValue()) return true;
        if (mc.player == null) return false;
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem
                || item instanceof AxeItem
                || item instanceof TridentItem
                || item.getTranslationKey().toLowerCase().contains("mace");
    }

    public double getExpandAmount() {
        if (mc.player == null || !isHoldingWeapon() || isPlacementDisabled()) return 0.0;
        double base = Math.max(0.0, hitboxMultiplier.getValue() - 1.0);
        return mode.isMode(ExpandMode.Legit) ? base * 0.5 : base;
    }

    public boolean isBlatant() {
        return mode.isMode(ExpandMode.Blatant);
    }
}
