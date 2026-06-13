package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.MathUtils;
import dev.lvstrng.argon.utils.MouseSimulation;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class MaceSwap extends Module implements AttackListener, TickListener {

    private final BooleanSetting swapSilently = new BooleanSetting(
            EncryptedString.of("Swap Silently"),
            false)
            .setDescription(EncryptedString.of("Swaps to mace via packet without visual change"));

    private final NumberSetting densityThreshold = new NumberSetting(
            EncryptedString.of("Density Threshold"), 0.0, 6.0, 1.5, 0.1)
            .setDescription(EncryptedString.of("Minimum fall distance needed before auto mace smash"));

    private final BooleanSetting autoStunSlam = new BooleanSetting(
            EncryptedString.of("Auto Stun Slam"), true)
            .setDescription(EncryptedString.of("Axe into mace one-tick combo for shield stun into smash"));

    private final BooleanSetting breachSwap = new BooleanSetting(
            EncryptedString.of("Breach Swap"), true)
            .setDescription(EncryptedString.of("Allows grounded breach-style mace swaps only while on ground"));

    private final BooleanSetting smartSwapBack = new BooleanSetting(
            EncryptedString.of("Smart Swap Back"), true)
            .setDescription(EncryptedString.of("Returns to the previous slot after the mace swing"));

    private final BooleanSetting inputSimulation = new BooleanSetting(
            EncryptedString.of("Input Simulation"), true)
            .setDescription(EncryptedString.of("Adds realistic attack input timing when swapping"));

    private int lastSlot = -1;
    private int swapBackTicks;

    public MaceSwap() {
        super(EncryptedString.of("AutoMace"),
                EncryptedString.of("Optimized mace swaps with stun slam, fall timing and smart swap back"),
                -1,
                Category.COMBAT);
        addSettings(swapSilently, densityThreshold, autoStunSlam, breachSwap, smartSwapBack, inputSimulation);
    }

    @Override
    public void onEnable() {
        eventManager.add(AttackListener.class, this);
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(TickListener.class, this);
        lastSlot = -1;
        swapBackTicks = 0;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || lastSlot == -1) return;
        if (swapBackTicks > 0) {
            swapBackTicks--;
            return;
        }
        if (smartSwapBack.getValue()) swap(lastSlot);
        lastSlot = -1;
    }

    @Override
    public void onAttack(AttackListener.AttackEvent event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult)) return;

        int slot = findMaceSlot();
        if (slot == -1 || lastSlot != -1 || slot == mc.player.getInventory().selectedSlot) return;
        if (!isOptimalMaceMoment()) return;

        lastSlot = mc.player.getInventory().selectedSlot;
        swap(slot);
        if (inputSimulation.getValue())
            MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT, MathUtils.randomInt(20, 45));
        swapBackTicks = 1;
    }

    private boolean isOptimalMaceMoment() {
        boolean falling = !mc.player.isOnGround() && mc.player.getVelocity().y < -0.08D;
        boolean densityReady = mc.player.fallDistance >= densityThreshold.getValueFloat();
        boolean stunSlam = autoStunSlam.getValue() && mc.player.getMainHandStack().getItem() instanceof AxeItem;
        boolean breachReady = breachSwap.getValue() && mc.player.isOnGround();
        return (falling && densityReady) || stunSlam || breachReady;
    }

    private void swap(int slot) {
        if (swapSilently.getValue()) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        } else {
            InventoryUtils.setInvSlot(slot);
        }
    }

    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof MaceItem) {
                return i;
            }
        }
        return -1;
    }
}
