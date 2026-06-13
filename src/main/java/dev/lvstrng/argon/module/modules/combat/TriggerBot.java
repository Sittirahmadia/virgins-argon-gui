package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.MathUtils;
import dev.lvstrng.argon.utils.MouseSimulation;
import dev.lvstrng.argon.utils.TimerUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class TriggerBot extends Module implements TickListener, AttackListener {
    private final ModeSetting<ActivationMode> activationMode = new ModeSetting<>(EncryptedString.of("Mode"), ActivationMode.Hold, ActivationMode.class)
            .setDescription(EncryptedString.of("Hold, Toggle, or Always Active"));
    private final ModeSetting<AttackMode> attackMode = new ModeSetting<>(EncryptedString.of("Attack Mode"), AttackMode.Cooldown, AttackMode.class)
            .setDescription(EncryptedString.of("Cooldown waits for weapon cooldown; Delay uses randomized CPS timing"));
    private final MinMaxSetting attackDelay = new MinMaxSetting(EncryptedString.of("Delay Min/Max"), 0, 500, 1, 35, 85)
            .setDescription(EncryptedString.of("Combined randomized min/max delay between clicks"));
    private final NumberSetting cooldown = new NumberSetting(EncryptedString.of("Cooldown %"), 0, 100, 95, 1);
    private final BooleanSetting holdingWeapon = new BooleanSetting(EncryptedString.of("Only Weapon"), true)
            .setDescription(EncryptedString.of("Only triggers when holding a sword, axe, trident or mace"));
    private final BooleanSetting priorityCrits = new BooleanSetting(EncryptedString.of("Priority Crits"), true)
            .setDescription(EncryptedString.of("Waits for perfect crit windows when possible"));
    private final BooleanSetting realisticInput = new BooleanSetting(EncryptedString.of("Input Sim"), true)
            .setDescription(EncryptedString.of("Uses variable CPS click simulation with jitter"));
    private final BooleanSetting ignoreShield = new BooleanSetting(EncryptedString.of("Ignore Shield"), false);
    private final ModeSetting<TargetMode> targetMode = new ModeSetting<>(EncryptedString.of("Target"), TargetMode.Player, TargetMode.class);

    public enum AttackMode { Cooldown, Delay }
    public enum TargetMode { Player, All }
    public enum ActivationMode { Hold, Toggle, Always }

    private final TimerUtils hitTimer = new TimerUtils();
    private boolean attackedThisTick;
    private boolean toggleActive;
    private boolean lastMouseDown;

    public TriggerBot() {
        super(EncryptedString.of("Trigger Bot"), EncryptedString.of("Automatically attacks enemies in your crosshair"), -1, Category.COMBAT);
        addSettings(activationMode, attackMode, attackDelay, cooldown, holdingWeapon, priorityCrits, realisticInput, ignoreShield, targetMode);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(AttackListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(AttackListener.class, this);
        toggleActive = false;
        lastMouseDown = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        attackedThisTick = false;
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (!activationReady()) return;
        if (holdingWeapon.getValue() && !isWeapon(mc.player.getMainHandStack().getItem())) return;
        if (!timingReady()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult entityHitResult)) return;
        if (!(entityHitResult.getEntity() instanceof LivingEntity target)) return;
        if (targetMode.isMode(TargetMode.Player) && !(target instanceof PlayerEntity)) return;
        if (ignoreShield.getValue() && target instanceof PlayerEntity p && p.isBlocking() && p.isHolding(Items.SHIELD)) return;
        if (priorityCrits.getValue() && canAttemptCrit() && !isPerfectCrit()) return;
        if (attackedThisTick) return;

        attackedThisTick = true;
        if (realisticInput.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT, MathUtils.randomInt(18, 48));
        WorldUtils.hitEntity(target, true);
        hitTimer.reset();
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
        if (attackMode.isMode(AttackMode.Cooldown)) {
            return mc.player.getAttackCooldownProgress(0) >= cooldown.getValue() * 0.01f;
        }
        int baseDelay = MathUtils.randomInt(attackDelay.getMinInt(), attackDelay.getMaxInt());
        int jitter = realisticInput.getValue() ? MathUtils.randomInt(-8, 14) : 0;
        return hitTimer.hasReached(Math.max(0, baseDelay + jitter));
    }

    private boolean canAttemptCrit() {
        return !mc.player.isOnGround() || mc.player.getVelocity().y < -0.05 || mc.player.fallDistance > 0;
    }

    private boolean isPerfectCrit() {
        return mc.player.fallDistance > 0.08f && mc.player.getVelocity().y < -0.08 && !mc.player.isOnGround()
                && !mc.player.isClimbing() && !mc.player.isTouchingWater() && !mc.player.hasVehicle();
    }

    private boolean isWeapon(Item item) {
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem;
    }

    @Override
    public void onAttack(AttackListener.AttackEvent event) {
        // handled in tick for consistent randomized timing
    }
}
