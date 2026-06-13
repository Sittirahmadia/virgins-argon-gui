package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class TriggerBot extends Module implements TickListener, AttackListener {

    private final ModeSetting<ActivationMode> activationMode = new ModeSetting<>(
            EncryptedString.of("Mode"), ActivationMode.Hold, ActivationMode.class)
            .setDescription(EncryptedString.of("Hold requires mouse, Toggle flips on click, Always runs when valid"));

    private final MinMaxSetting attackDelay = new MinMaxSetting(
            EncryptedString.of("Delay Randomizer"), 0, 500, 1, 45, 95)
            .setDescription(EncryptedString.of("Combined cooldown/random delay range in milliseconds"));

    private final NumberSetting cooldown = new NumberSetting(
            EncryptedString.of("Cooldown %"), 0, 100, 92, 1)
            .setDescription(EncryptedString.of("Minimum vanilla attack cooldown percentage"));

    private final BooleanSetting holdingWeapon = new BooleanSetting(
            EncryptedString.of("Only Weapon"), true)
            .setDescription(EncryptedString.of("Only triggers when holding a sword, axe, mace or trident"));

    private final BooleanSetting priorityCrits = new BooleanSetting(
            EncryptedString.of("Priority Crits"), true)
            .setDescription(EncryptedString.of("Waits for perfect crit windows when falling"));

    private final BooleanSetting inputSimulation = new BooleanSetting(
            EncryptedString.of("Input Simulation"), true)
            .setDescription(EncryptedString.of("Adds realistic CPS click simulation and jitter before attacking"));

    private final NumberSetting jitter = new NumberSetting(
            EncryptedString.of("Jitter"), 0, 50, 12, 1)
            .setDescription(EncryptedString.of("Extra random input jitter in milliseconds"));

    private final BooleanSetting ignoreShield = new BooleanSetting(
            EncryptedString.of("Ignore Shield"), false)
            .setDescription(EncryptedString.of("Skip attacking shielding players"));

    private final ModeSetting<TargetMode> targetMode = new ModeSetting<>(
            EncryptedString.of("Target"), TargetMode.Player, TargetMode.class);

    public enum ActivationMode { Hold, Toggle, Always }
    public enum TargetMode { Player, All }

    private final TimerUtils hitTimer = new TimerUtils();
    private boolean toggleActive;
    private boolean lastMouseDown;
    private int nextDelay;

    public TriggerBot() {
        super(EncryptedString.of("Trigger Bot"),
                EncryptedString.of("Weapon-only triggerbot with crit priority, random cooldowns and input simulation"),
                -1,
                Category.COMBAT);
        addSettings(activationMode, attackDelay, cooldown, holdingWeapon, priorityCrits, inputSimulation, jitter, ignoreShield, targetMode);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(AttackListener.class, this);
        nextDelay = randomDelay();
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
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        updateToggleState();
        if (!isActivationSatisfied()) return;
        if (holdingWeapon.getValue() && !isHoldingWeapon()) return;
        if (!hitTimer.hasReached(nextDelay)) return;
        if (mc.player.getAttackCooldownProgress(0) < cooldown.getValueFloat() * 0.01F) return;

        if (!(mc.crosshairTarget instanceof EntityHitResult entityHitResult)) return;
        if (!(entityHitResult.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;
        if (targetMode.isMode(TargetMode.Player) && !(target instanceof PlayerEntity)) return;
        if (ignoreShield.getValue() && target instanceof PlayerEntity p && p.isBlocking() && p.isHolding(Items.SHIELD)) return;
        if (priorityCrits.getValue() && shouldWaitForCrit()) return;

        if (inputSimulation.getValue())
            MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT, MathUtils.randomInt(18, 42));

        WorldUtils.hitEntity(target, true);
        hitTimer.reset();
        nextDelay = randomDelay() + MathUtils.randomInt(0, jitter.getValueInt());
    }

    @Override
    public void onAttack(AttackListener.AttackEvent event) {
        // External attack events are intentionally ignored; this module controls its own timing.
    }

    private void updateToggleState() {
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (activationMode.isMode(ActivationMode.Toggle) && down && !lastMouseDown)
            toggleActive = !toggleActive;
        lastMouseDown = down;
    }

    private boolean isActivationSatisfied() {
        if (activationMode.isMode(ActivationMode.Always)) return true;
        if (activationMode.isMode(ActivationMode.Toggle)) return toggleActive;
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private boolean shouldWaitForCrit() {
        if (mc.player.isOnGround()) return false;
        return mc.player.fallDistance <= 0.0F || mc.player.getVelocity().y >= -0.08D;
    }

    private int randomDelay() {
        return MathUtils.randomInt(attackDelay.getMinInt(), attackDelay.getMaxInt());
    }

    private boolean isHoldingWeapon() {
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem || item instanceof TridentItem;
    }
}
