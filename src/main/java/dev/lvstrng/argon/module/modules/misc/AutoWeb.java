package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.MouseSimulation;
import dev.lvstrng.argon.utils.RotationUtils;
import dev.lvstrng.argon.utils.TimerUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;

public final class AutoWeb extends Module implements TickListener {

    private final MinMaxSetting range = new MinMaxSetting(
            EncryptedString.of("Range"), 0, 12, 0.1, 1.0, 3.5)
            .setDescription(EncryptedString.of("Min and max distance to place webs"));

    private final NumberSetting webDelay = new NumberSetting(
            EncryptedString.of("Web Delay (s)"), 0, 100, 10, 1)
            .setDescription(EncryptedString.of("Delay between web placements in seconds"));

    private final NumberSetting fov = new NumberSetting(
            EncryptedString.of("FOV"), 0, 360, 90, 1)
            .setDescription(EncryptedString.of("Field of view for target detection"));

    private final BooleanSetting includeHead = new BooleanSetting(
            EncryptedString.of("Include Head"), false)
            .setDescription(EncryptedString.of("Also block the enemy's head level"));

    private final BooleanSetting requireWeb = new BooleanSetting(
            EncryptedString.of("Hold Web"), false)
            .setDescription(EncryptedString.of("Only activates when holding cobweb"));

    private final BooleanSetting requireClick = new BooleanSetting(
            EncryptedString.of("Require Click"), false)
            .setDescription(EncryptedString.of("Only activates when holding right click"));

    private final BooleanSetting notAffectSelf = new BooleanSetting(
            EncryptedString.of("Not Affect Self"), true)
            .setDescription(EncryptedString.of("Don't place if web would affect the player"));

    private final TimerUtils waitTimer = new TimerUtils();
    private BlockPos targetPos = null;

    public AutoWeb() {
        super(EncryptedString.of("Auto Web"),
                EncryptedString.of("Automatically places webs on enemies"),
                -1,
                Category.MISC);
        addSettings(range, webDelay, fov, includeHead, requireWeb, requireClick, notAffectSelf);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        targetPos = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        targetPos = null;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Condition checks
        if (requireWeb.getValue() && mc.player.getMainHandStack().getItem() != Items.COBWEB) return;
        if (requireClick.getValue()
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS)
            return;

        PlayerEntity enemy = WorldUtils.findNearestPlayer(mc.player, (float) range.getMaxValue(), true, true);
        if (enemy == null) {
            targetPos = null;
            return;
        }

        double dist = enemy.distanceTo(mc.player);
        if (dist > range.getMaxValue() || dist < range.getMinValue()) {
            targetPos = null;
            return;
        }

        // FOV check
        Vec3d enemyEyePos = enemy.getPos().add(0, enemy.getEyeHeight(enemy.getPose()), 0);
        Vec3d playerEyePos = mc.player.getEyePos();
        Vec3d toEnemy = enemyEyePos.subtract(playerEyePos).normalize();
        Vec3d lookVec = mc.player.getRotationVecClient();
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, lookVec.dotProduct(toEnemy)))));
        if (angle > fov.getValue() / 2.0) {
            targetPos = null;
            return;
        }

        Vec3d feetSpot = getBestPlacementSpot(enemy, true);
        Vec3d eyeSpot = getBestPlacementSpot(enemy, false);

        Vec3d chosenSpot = (includeHead.getValue() && eyeSpot != null) ? eyeSpot : feetSpot;
        if (chosenSpot == null) {
            targetPos = null;
            return;
        }

        // Self-intersect check
        if (notAffectSelf.getValue()) {
            Box webBox = new Box(
                    chosenSpot.x - 0.5, chosenSpot.y - 0.5, chosenSpot.z - 0.5,
                    chosenSpot.x + 0.5, chosenSpot.y + 0.5, chosenSpot.z + 0.5);
            if (mc.player.getBoundingBox().intersects(webBox)) {
                targetPos = null;
                return;
            }
        }

        BlockPos placeBelow = BlockPos.ofFloored(chosenSpot).down();
        BlockPos placeAbove = placeBelow.up();

        if (!mc.world.getBlockState(placeAbove).isAir()) {
            targetPos = null;
            return;
        }

        targetPos = placeBelow;

        // Aim at placement block
        Vec3d aimTarget = Vec3d.ofCenter(placeBelow).add(0, 0.5, 0);
        Rotation rotation = RotationUtils.getDirection(mc.player, aimTarget);
        mc.player.setYaw((float) rotation.yaw());
        mc.player.setPitch((float) rotation.pitch());

        // Place if timer ready and crosshair is on the right block
        if (!waitTimer.hasReached(webDelay.getValue() * 1000L)) return;

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit) {
            if (blockHit.getBlockPos().equals(targetPos) && blockHit.getSide() == Direction.UP) {
                MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                waitTimer.reset();
            }
        }
    }

    private Vec3d getBestPlacementSpot(PlayerEntity player, boolean feet) {
        Vec3d origin = feet
                ? player.getPos()
                : player.getPos().add(0, player.getEyeHeight(player.getPose()), 0);

        Box bBox = player.getBoundingBox();
        LinkedHashSet<Vec3d> candidates = new LinkedHashSet<>();
        double offset = bBox.getAverageSideLength() / 2.0;

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos bp = BlockPos.ofFloored(origin.add(x, 0, z));
                Vec3d center = bp.toCenterPos();
                if (bBox.intersects(center.x - offset, center.y - offset, center.z - offset,
                        center.x + offset, center.y + offset, center.z + offset)) {
                    candidates.add(center);
                }
            }
        }

        Vec3d best = null;
        double closestDist = Double.MAX_VALUE;

        for (Vec3d pos : candidates) {
            BlockPos bp = BlockPos.ofFloored(pos);
            if (feet) {
                if (!mc.world.getBlockState(bp).isAir()) continue;
            } else {
                if (mc.world.getBlockState(bp.down()).isAir()) continue;
            }
            double d = pos.distanceTo(player.getPos());
            if (d < closestDist && d > 0.5) {
                closestDist = d;
                best = pos;
            }
        }

        return best;
    }
}
