package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.CrystalUtils;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.MathUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class ClosetCrystal extends Module implements PlayerTickListener, ItemUseListener {

    // ── Place settings ────────────────────────────────────────────────────────
    private final NumberSetting placeInterval = new NumberSetting(
            EncryptedString.of("Place Interval"), 0, 20, 2, 1)
            .setDescription(EncryptedString.of("Ticks between crystal placements"));

    private final MinMaxSetting placeChance = new MinMaxSetting(
            EncryptedString.of("Place Chance"), 0, 100, 1, 80, 100)
            .setDescription(EncryptedString.of("Random chance % range to place a crystal"));

    // ── Break settings ────────────────────────────────────────────────────────
    private final NumberSetting breakInterval = new NumberSetting(
            EncryptedString.of("Break Interval"), 0, 20, 2, 1)
            .setDescription(EncryptedString.of("Ticks between crystal breaks"));

    private final MinMaxSetting breakChance = new MinMaxSetting(
            EncryptedString.of("Break Chance"), 0, 100, 1, 80, 100)
            .setDescription(EncryptedString.of("Random chance % range to break a crystal"));

    // ── Behaviour settings ────────────────────────────────────────────────────
    private final BooleanSetting onRMB = new BooleanSetting(
            EncryptedString.of("On RMB"), false)
            .setDescription(EncryptedString.of("Only runs while right-click is held"));

    private final BooleanSetting stopOnKill = new BooleanSetting(
            EncryptedString.of("Stop On Kill"), false)
            .setDescription(EncryptedString.of("Pauses when a nearby enemy dies to avoid wasting crystals"));

    private final BooleanSetting requireCrystal = new BooleanSetting(
            EncryptedString.of("Require Crystal"), true)
            .setDescription(EncryptedString.of("Only activates when holding an end crystal"));

    private final BooleanSetting antiSelfDamage = new BooleanSetting(
            EncryptedString.of("Anti Self Damage"), true)
            .setDescription(EncryptedString.of("Cancels placement if the crystal would hit your own feet"));

    private final BooleanSetting noSelfPlace = new BooleanSetting(
            EncryptedString.of("No Obsidian Cancel"), true)
            .setDescription(EncryptedString.of("Cancels right-click use event when aiming at obsidian with a crystal to prevent accidental placement"));

    // ── Timers ────────────────────────────────────────────────────────────────
    private int placeClock = 0;
    private int breakClock = 0;

    public ClosetCrystal() {
        super(EncryptedString.of("Closet Crystal"),
                EncryptedString.of("Semi-manual crystal PvP: auto-places and breaks crystals at your crosshair"),
                -1, Category.COMBAT);
        addSettings(placeInterval, placeChance, breakInterval, breakChance,
                onRMB, stopOnKill, requireCrystal, antiSelfDamage, noSelfPlace);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        placeClock = 0;
        breakClock = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        super.onDisable();
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.world == null) return;

        // Tick down both clocks
        if (placeClock > 0) placeClock--;
        if (breakClock > 0) breakClock--;

        // RMB gate
        if (onRMB.getValue()
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS)
            return;

        // Must be holding crystal
        ItemStack held = mc.player.getMainHandStack();
        if (requireCrystal.getValue() && !held.isOf(Items.END_CRYSTAL)) return;

        // Stop-on-kill
        if (stopOnKill.getValue() && WorldUtils.isDeadBodyNearby()) return;

        // ── Break crystal at crosshair ─────────────────────────────────────────
        if (breakClock == 0
                && mc.crosshairTarget instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystalEntity crystal) {

            if (rollChance(breakChance)) {
                breakClock = breakInterval.getValueInt();
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }

        // ── Place crystal at crosshair ─────────────────────────────────────────
        if (placeClock == 0
                && mc.crosshairTarget instanceof BlockHitResult blockHit
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {

            // Only place on obsidian / bedrock surface
            if (!CrystalUtils.canPlaceCrystalServer(blockHit.getBlockPos())) return;

            // Anti self-damage: don't place directly under feet
            if (antiSelfDamage.getValue()
                    && blockHit.getBlockPos().equals(mc.player.getBlockPos().down())) return;

            if (rollChance(placeChance)) {
                placeClock = placeInterval.getValueInt();
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                if (result.isAccepted() && result.shouldSwingHand())
                    mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // ── Cancel accidental obsidian right-click ────────────────────────────────

    @Override
    public void onItemUse(ItemUseListener.ItemUseEvent event) {
        if (!noSelfPlace.getValue()) return;
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return;

        if (mc.crosshairTarget instanceof BlockHitResult blockHit
                && mc.world.getBlockState(blockHit.getBlockPos()).isOf(Blocks.OBSIDIAN)) {
            // Allow only if it's a valid crystal placement spot
            if (!CrystalUtils.canPlaceCrystalServer(blockHit.getBlockPos()))
                event.cancel();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private boolean rollChance(MinMaxSetting setting) {
        int roll = MathUtils.randomInt(0, 100);
        int threshold = MathUtils.randomInt(setting.getMinInt(), setting.getMaxInt());
        return roll <= threshold;
    }
}
