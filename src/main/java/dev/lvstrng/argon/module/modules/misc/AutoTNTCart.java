package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.TimerUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class AutoTNTCart extends Module implements TickListener, AttackListener {

    public enum ActivationMode { Auto, OnHit }
    public enum IgniteMode     { Bow, Crossbow, FlintAndSteel }

    private final ModeSetting<ActivationMode> activation = new ModeSetting<>(
            EncryptedString.of("Activation"), ActivationMode.OnHit, ActivationMode.class)
            .setDescription(EncryptedString.of("Auto fires every cooldown; OnHit fires on attack"));

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 8.0, 4.0, 0.5)
            .setDescription(EncryptedString.of("Range to search for the nearest enemy"));

    private final NumberSetting cartAmount = new NumberSetting(
            EncryptedString.of("Cart Amount"), 1.0, 5.0, 1.0, 1.0)
            .setDescription(EncryptedString.of("Number of TNT minecarts to place per use"));

    private final NumberSetting cooldown = new NumberSetting(
            EncryptedString.of("Cooldown"), 0.0, 5000.0, 500.0, 100.0);

    private final BooleanSetting placeRail = new BooleanSetting(
            EncryptedString.of("Place Rail"), true)
            .setDescription(EncryptedString.of("Auto place a rail before the cart if none exists"));

    private final BooleanSetting ignite = new BooleanSetting(
            EncryptedString.of("Ignite"), true)
            .setDescription(EncryptedString.of("Ignite the carts after placement"));

    private final ModeSetting<IgniteMode> igniteMode = new ModeSetting<>(
            EncryptedString.of("Ignite Mode"), IgniteMode.FlintAndSteel, IgniteMode.class)
            .setDescription(EncryptedString.of("Method to ignite the TNT carts"));

    private final NumberSetting igniteDelay = new NumberSetting(
            EncryptedString.of("Ignite Delay"), 0.0, 1000.0, 150.0, 25.0)
            .setDescription(EncryptedString.of("Milliseconds after cart placement before igniting"));

    // Bow charge state
    private boolean  charging       = false;
    private int      pendingBowShots = 0;
    private long     chargeStart    = 0;

    // Sequencing timers
    private final TimerUtils cooldownTimer = new TimerUtils();
    private final TimerUtils igniteTimer   = new TimerUtils();
    private BlockPos pendingIgnitePos = null;
    private int      pendingIgniteCount = 0;

    public AutoTNTCart() {
        super(EncryptedString.of("Auto TNT Cart"),
                EncryptedString.of("Places a rail, TNT minecart, then ignites it near enemies"),
                -1, Category.MISC);
        addSettings(activation, range, cartAmount, cooldown,
                placeRail, ignite, igniteMode, igniteDelay);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(AttackListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(AttackListener.class, this);
        if (charging && mc.options != null) mc.options.useKey.setPressed(false);
        reset();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Tick bow charge/release cycle
        handleBowCharge();

        // Fire ignite sequence after delay
        if (pendingIgnitePos != null && igniteTimer.hasReached(igniteDelay.getValue())) {
            triggerIgnite(pendingIgnitePos, pendingIgniteCount);
            pendingIgnitePos = null;
        }

        if (activation.isMode(ActivationMode.Auto)) execute();
    }

    @Override
    public void onAttack(AttackListener.AttackEvent event) {
        if (activation.isMode(ActivationMode.OnHit)) execute();
    }

    // ── Main sequence ────────────────────────────────────────────────────────────

    private void execute() {
        if (!cooldownTimer.hasReached(cooldown.getValue())) return;

        PlayerEntity target = WorldUtils.findNearestPlayer(mc.player, range.getValueFloat(), true, true);
        if (target == null) return;

        BlockPos targetPos = target.getBlockPos();
        int count = (int) cartAmount.getValue();

        // Step 1: optionally place rail
        if (placeRail.getValue() && !isRailAt(targetPos)) {
            int railSlot = findRailSlot();
            if (railSlot != -1 && mc.world.getBlockState(targetPos.down()).isSolidBlock(mc.world, targetPos.down())) {
                placeBlockAt(targetPos, railSlot);
            }
        }

        // Step 2: place TNT carts
        placeTNTCarts(targetPos, count);

        // Step 3: schedule ignite
        if (ignite.getValue()) {
            pendingIgnitePos   = targetPos;
            pendingIgniteCount = count;
            igniteTimer.reset();
        }

        cooldownTimer.reset();
    }

    // ── Rail ─────────────────────────────────────────────────────────────────────

    private boolean isRailAt(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() instanceof AbstractRailBlock;
    }

    private int findRailSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.RAIL) || s.isOf(Items.POWERED_RAIL) || s.isOf(Items.DETECTOR_RAIL)) return i;
        }
        return -1;
    }

    // ── TNT Cart ─────────────────────────────────────────────────────────────────

    private void placeTNTCarts(BlockPos pos, int count) {
        if (!InventoryUtils.selectItemFromHotbar(Items.TNT_MINECART)) return;
        for (int i = 0; i < count; i++) {
            BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (r.isAccepted() && r.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ── Ignite ───────────────────────────────────────────────────────────────────

    private void triggerIgnite(BlockPos pos, int times) {
        switch (igniteMode.getMode()) {
            case FlintAndSteel -> igniteWithFnS(pos, times);
            case Bow           -> startBowSequence(times);
            case Crossbow      -> startCrossbowSequence(times);
        }
    }

    private void igniteWithFnS(BlockPos pos, int times) {
        if (!InventoryUtils.selectItemFromHotbar(Items.FLINT_AND_STEEL)) return;
        for (int i = 0; i < times; i++) {
            BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (r.isAccepted() && r.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ── Bow charge cycle ─────────────────────────────────────────────────────────

    private void startBowSequence(int times) {
        int slot = findBowSlot();
        if (slot == -1) return;
        InventoryUtils.setInvSlot(slot);
        pendingBowShots = times;
        startBowCharge();
    }

    private void startBowCharge() {
        if (mc.player == null) return;
        charging    = true;
        chargeStart = System.currentTimeMillis();
        mc.options.useKey.setPressed(true);
    }

    private void handleBowCharge() {
        if (!charging || mc.player == null) { charging = false; pendingBowShots = 0; return; }
        if (System.currentTimeMillis() - chargeStart >= 300) releaseBow();
    }

    private void releaseBow() {
        charging = false;
        mc.options.useKey.setPressed(false);
        mc.interactionManager.stopUsingItem(mc.player);
        pendingBowShots--;
        // schedule next shot with a short gap using the igniteTimer trick
        if (pendingBowShots > 0) {
            new Thread(() -> {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                startBowCharge();
            }, "bow-shot-delay").start();
        }
    }

    // ── Crossbow sequence ────────────────────────────────────────────────────────

    private void startCrossbowSequence(int times) {
        int slot = findCrossbowSlot();
        if (slot == -1) return;
        InventoryUtils.setInvSlot(slot);

        for (int i = 0; i < times; i++) {
            final int delay = i * 1400;
            new Thread(() -> {
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                fireCrossbowOnce();
            }, "crossbow-shot-" + i).start();
        }
    }

    private void fireCrossbowOnce() {
        if (mc.player == null || mc.interactionManager == null) return;
        int slot = findCrossbowSlot();
        if (slot == -1) return;
        InventoryUtils.setInvSlot(slot);
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (!(stack.getItem() instanceof CrossbowItem)) return;

        if (!CrossbowItem.isCharged(stack)) {
            mc.options.useKey.setPressed(true);
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            mc.options.useKey.setPressed(false);
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        if (mc.interactionManager != null) mc.interactionManager.stopUsingItem(mc.player);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void placeBlockAt(BlockPos pos, int slot) {
        InventoryUtils.setInvSlot(slot);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (r.isAccepted() && r.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findBowSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() instanceof BowItem) return i;
        return -1;
    }

    private int findCrossbowSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() instanceof CrossbowItem) return i;
        return -1;
    }

    private void reset() {
        charging          = false;
        pendingBowShots   = 0;
        pendingIgnitePos  = null;
        pendingIgniteCount = 0;
    }
}
