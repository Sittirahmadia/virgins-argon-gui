package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.AdvancedInputSimulator;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.MathUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.RotationUtils;
import dev.lvstrng.argon.utils.TimerUtils;
import java.util.stream.StreamSupport;
import dev.lvstrng.argon.utils.WorldUtils;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Comparator;

public final class TriggerBot extends Module implements TickListener, AttackListener, GameRenderListener {
    private final ModeSetting<ActivationMode> activationMode = new ModeSetting<>(EncryptedString.of("Mode"), ActivationMode.Hold, ActivationMode.class)
            .setDescription(EncryptedString.of("Hold, Toggle, or Always Active"));
    private final ModeSetting<AttackMode> attackMode = new ModeSetting<>(EncryptedString.of("Attack Mode"), AttackMode.Cooldown, AttackMode.class)
            .setDescription(EncryptedString.of("Cooldown waits for weapon cooldown; Delay uses randomized timing"));
    private final ModeSetting<TargetMode> targetMode = new ModeSetting<>(EncryptedString.of("Target Filter"), TargetMode.Players, TargetMode.class);
    private final ModeSetting<PriorityMode> priorityMode = new ModeSetting<>(EncryptedString.of("Priority"), PriorityMode.Crosshair, PriorityMode.class);
    private final MinMaxSetting attackDelay = new MinMaxSetting(EncryptedString.of("Delay Min/Max"), 0, 500, 1, 35, 85)
            .setDescription(EncryptedString.of("Combined randomized min/max delay between clicks in ms"));
    private final NumberSetting cooldown = new NumberSetting(EncryptedString.of("Cooldown %"), 0, 100, 95, 1);
    private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 1.0, 6.0, 3.2, 0.1);
    private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0, 20, 4, 1);
    private final BooleanSetting holdingWeapon = new BooleanSetting(EncryptedString.of("Only Weapon"), true)
            .setDescription(EncryptedString.of("Only triggers when holding a sword, axe, trident, mace, crystal, or configured combat item"));
    private final BooleanSetting allowCrystals = new BooleanSetting(EncryptedString.of("Crystal Weapon"), true);
    private final BooleanSetting priorityCrits = new BooleanSetting(EncryptedString.of("Priority Crits"), true)
            .setDescription(EncryptedString.of("Waits for perfect crit windows when possible"));
    private final BooleanSetting realisticInput = new BooleanSetting(EncryptedString.of("Input Sim"), true)
            .setDescription(EncryptedString.of("Uses variable CPS click simulation with jitter"));
    private final BooleanSetting wallCheck = new BooleanSetting(EncryptedString.of("Wall Check"), true)
            .setDescription(EncryptedString.of("Requires raycast/visibility before attacking"));
    private final BooleanSetting ignoreShield = new BooleanSetting(EncryptedString.of("Ignore Shield"), false);
    private final BooleanSetting visualFeedback = new BooleanSetting(EncryptedString.of("Visual Feedback"), true);

    public enum AttackMode { Cooldown, Delay }
    public enum TargetMode { Players, Mobs, Both }
    public enum ActivationMode { Hold, Toggle, Always }
    public enum PriorityMode { Crosshair, Nearest, Lowest_Health, Angle }

    private final TimerUtils hitTimer = new TimerUtils();
    private LivingEntity currentTarget;
    private boolean attackedThisTick;
    private boolean toggleActive;
    private boolean lastMouseDown;
    private int nextDelay;
    private int targetSwitchTimer;

    public TriggerBot() {
        super(EncryptedString.of("Trigger Bot"), EncryptedString.of("Smart humanized triggerbot with perfect crit timing"), -1, Category.COMBAT);
        addSettings(activationMode, attackMode, targetMode, priorityMode, attackDelay, cooldown, range, switchDelay,
                holdingWeapon, allowCrystals, priorityCrits, realisticInput, wallCheck, ignoreShield, visualFeedback);
        nextDelay = attackDelay.getRandomValueInt();
    }

    @Override public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(AttackListener.class, this);
        eventManager.add(GameRenderListener.class, this);
        super.onEnable();
    }

    @Override public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(GameRenderListener.class, this);
        currentTarget = null;
        toggleActive = false;
        lastMouseDown = false;
        super.onDisable();
    }

    @Override public void onTick() {
        attackedThisTick = false;
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (!activationReady()) return;
        if (holdingWeapon.getValue() && !isWeapon(mc.player.getMainHandStack().getItem())) return;

        LivingEntity bestTarget = selectTarget();
        updateTarget(bestTarget);
        if (currentTarget == null || !timingReady() || attackedThisTick) return;
        if (ignoreShield.getValue() && currentTarget instanceof PlayerEntity p && p.isBlocking() && p.isHolding(Items.SHIELD)) return;
        if (priorityCrits.getValue() && shouldWaitForCrit() && !isPerfectCrit()) return;

        attackedThisTick = true;
        if (realisticInput.getValue()) AdvancedInputSimulator.leftClick(18, 52);
        WorldUtils.hitEntity(currentTarget, true);
        hitTimer.reset();
        nextDelay = Math.max(0, attackDelay.getRandomValueInt() + (realisticInput.getValue() ? MathUtils.randomInt(-8, 14) : 0));
    }

    @Override public void onGameRender(GameRenderEvent event) {
        if (!visualFeedback.getValue() || currentTarget == null || mc.player == null) return;
        if (mc.player.squaredDistanceTo(currentTarget) > range.getValue() * range.getValue()) return;
        Vec3d cam = RenderUtils.getCameraPos();
        Box box = currentTarget.getBoundingBox().expand(0.04).offset(-cam.x, -cam.y, -cam.z);
        RenderUtils.renderFilledBox(event.matrices, (float) box.minX, (float) box.minY, (float) box.minZ,
                (float) box.maxX, (float) box.maxY, (float) box.maxZ, new Color(255, 45, 105, 38));
        RenderUtils.renderLine(event.matrices, new Color(255, 65, 135, 190), new Vec3d(box.minX, box.maxY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ));
        RenderUtils.renderLine(event.matrices, new Color(80, 220, 255, 190), new Vec3d(box.maxX, box.maxY, box.minZ), new Vec3d(box.minX, box.maxY, box.maxZ));
    }

    private void updateTarget(LivingEntity target) {
        if (target == null) {
            currentTarget = null;
            targetSwitchTimer = 0;
            return;
        }
        if (currentTarget == null || currentTarget == target) {
            currentTarget = target;
            targetSwitchTimer = 0;
            return;
        }
        targetSwitchTimer++;
        if (targetSwitchTimer >= switchDelay.getValueInt()) {
            currentTarget = target;
            targetSwitchTimer = 0;
        }
    }

    private LivingEntity selectTarget() {
        if (priorityMode.isMode(PriorityMode.Crosshair) && mc.crosshairTarget instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof LivingEntity target && isValidTarget(target)) return target;

        Comparator<LivingEntity> comparator = switch (priorityMode.getMode()) {
            case Lowest_Health -> Comparator.comparingDouble(LivingEntity::getHealth);
            case Angle, Crosshair -> Comparator.comparingDouble(this::angleTo);
            case Nearest -> Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e));
        };
        return StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .filter(this::isValidTarget)
                .min(comparator)
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || entity.isDead() || !entity.isAlive() || entity.isSpectator()) return false;
        if (mc.player.squaredDistanceTo(entity) > range.getValue() * range.getValue()) return false;
        if (targetMode.isMode(TargetMode.Players) && !(entity instanceof PlayerEntity)) return false;
        if (targetMode.isMode(TargetMode.Mobs) && !(entity instanceof MobEntity)) return false;
        if (wallCheck.getValue() && !hasLineOfSight(entity)) return false;
        return true;
    }

    private boolean hasLineOfSight(Entity entity) {
        HitResult hit = WorldUtils.getHitResult(range.getValue());
        return mc.player.canSee(entity) || (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == entity);
    }

    private boolean activationReady() {
        boolean mouseDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (activationMode.isMode(ActivationMode.Always)) return true;
        if (activationMode.isMode(ActivationMode.Hold)) return mouseDown;
        if (mouseDown && !lastMouseDown) toggleActive = !toggleActive;
        lastMouseDown = mouseDown;
        return toggleActive;
    }

    private boolean timingReady() {
        if (attackMode.isMode(AttackMode.Cooldown)) return mc.player.getAttackCooldownProgress(0) >= cooldown.getValue() * 0.01f;
        return hitTimer.hasReached(nextDelay);
    }

    private boolean shouldWaitForCrit() {
        return !mc.player.isOnGround() || mc.player.getVelocity().y < -0.03 || mc.player.fallDistance > 0;
    }

    private boolean isPerfectCrit() {
        return mc.player.fallDistance > 0.08f && mc.player.getVelocity().y < -0.08 && !mc.player.isOnGround()
                && !mc.player.isClimbing() && !mc.player.isTouchingWater() && !mc.player.hasVehicle();
    }

    private double angleTo(LivingEntity entity) {
        Rotation rotation = RotationUtils.getDirection(mc.player, entity.getBoundingBox().getCenter());
        return RotationUtils.getAngleToRotation(rotation);
    }

    private boolean isWeapon(Item item) {
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem
                || (allowCrystals.getValue() && item == Items.END_CRYSTAL);
    }

    @Override public void onAttack(AttackListener.AttackEvent event) {
        // handled in tick for consistent randomized timing
    }
}
