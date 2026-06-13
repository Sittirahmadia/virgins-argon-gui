package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AnchorMacro extends Module implements TickListener, ItemUseListener {
    private enum RotationMode { SILENT, LEGIT }

    private enum SafeStage { PLACE, CHARGE, PROTECT, EXPLODE }

    private record TrackedAnchor(BlockPos pos, UUID owner, int lastSeenTick) {}

    // --- Feature toggles ---
    private final BooleanSetting placer = new BooleanSetting(
            EncryptedString.of("Placer"), true)
            .setDescription(EncryptedString.of("Places respawn anchors from your hotbar"));

    private final BooleanSetting charger = new BooleanSetting(
            EncryptedString.of("Charger"), true)
            .setDescription(EncryptedString.of("Charges an uncharged anchor with glowstone"));

    private final BooleanSetting exploder = new BooleanSetting(
            EncryptedString.of("Exploder"), true)
            .setDescription(EncryptedString.of("Detonates a charged anchor"));

    private final BooleanSetting safeAnchor = new BooleanSetting(
            EncryptedString.of("Safe Anchor"), true)
            .setDescription(EncryptedString.of("Runs a protected place -> charge -> sneak -> explode sequence"));

    private final BooleanSetting onlyOwnAnchor = new BooleanSetting(
            EncryptedString.of("Only Own Anchor"), true)
            .setDescription(EncryptedString.of("Only charges or explodes anchors tracked as placed by you"));

    // --- Timings ---
    private final NumberSetting explodeCooldown = new NumberSetting(
            EncryptedString.of("Explode Cooldown"), 0, 40, 8, 1)
            .setDescription(EncryptedString.of("Minimum ticks between anchor detonations"));

    private final MinMaxSetting randomDelay = new MinMaxSetting(
            EncryptedString.of("Random Delay"), 0, 20, 1, 2, 5)
            .setDescription(EncryptedString.of("Randomized humanized delay between macro actions"));

    private final NumberSetting protectTicks = new NumberSetting(
            EncryptedString.of("Protect Ticks"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("Ticks to hold sneak before exploding in safe anchor mode"));

    private final NumberSetting scanRange = new NumberSetting(
            EncryptedString.of("Scan Range"), 2, 6, 4, 0.5)
            .setDescription(EncryptedString.of("Range used to detect nearby player-placed anchors"));

    private final ModeSetting<RotationMode> rotationMode = new ModeSetting<>(
            EncryptedString.of("Rotations"), RotationMode.SILENT, RotationMode.class)
            .setDescription(EncryptedString.of("Silent keeps camera still, Legit smoothly turns to the anchor"));

    // --- Safety & misc ---
    private final BooleanSetting safeExplode = new BooleanSetting(
            EncryptedString.of("Safe Explode"), true)
            .setDescription(EncryptedString.of("Sneaks before exploding so you don't take full damage"));

    private final BooleanSetting useGate = new BooleanSetting(
            EncryptedString.of("Use Gate"), true)
            .setDescription(EncryptedString.of("Cancels if you're using a bow, shield, or eating"));

    private final BooleanSetting clickSimulation = new BooleanSetting(
            EncryptedString.of("Click Simulation"), true)
            .setDescription(EncryptedString.of("Simulates real mouse clicks for more realistic input"));

    private final NumberSetting explodeSlot = new NumberSetting(
            EncryptedString.of("Explode Slot"), 1, 9, 9, 1)
            .setDescription(EncryptedString.of("Hotbar slot to switch to before exploding (1-9)"));

    private final Map<BlockPos, TrackedAnchor> trackedAnchors = new HashMap<>();
    private int actionTimer;
    private int actionDelay;
    private int explodeTimer;
    private int worldTicks;
    private int protectTimer;
    private boolean sneaking;
    private SafeStage safeStage = SafeStage.PLACE;
    private BlockPos safeTarget;

    public AnchorMacro() {
        super(EncryptedString.of("Anchor Macro"),
                EncryptedString.of("Single-key safe anchor macro with own-anchor tracking, cooldowns, randomization, and rotations"),
                -1,
                Category.COMBAT);
        addSettings(placer, charger, exploder, safeAnchor, onlyOwnAnchor,
                explodeCooldown, randomDelay, protectTicks, scanRange, rotationMode,
                safeExplode, useGate, clickSimulation, explodeSlot);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        releaseSneak();
        resetState();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.getNetworkHandler() == null) return;

        worldTicks++;
        actionTimer++;
        explodeTimer++;
        pruneTrackedAnchors();
        monitorNearbyOwnAnchors();

        if (sneaking && safeStage != SafeStage.PROTECT && protectTimer <= 0) releaseSneak();
        if (!isMacroKeyHeld() || isUsingBlockedItem()) return;

        if (safeAnchor.getValue()) runSafeAnchor();
        else runLegacyAnchor();
    }

    @Override
    public void onItemUse(ItemUseListener.ItemUseEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isHolding(Items.RESPAWN_ANCHOR) && mc.crosshairTarget instanceof BlockHitResult blockHit)
            trackAnchor(getPlacementPos(blockHit));

        if (safeExplode.getValue()
                && mc.player.isHolding(Items.GLOWSTONE)
                && mc.crosshairTarget instanceof BlockHitResult blockHit
                && isTrackedOrAllowed(blockHit.getBlockPos())
                && BlockUtils.isBlock(blockHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                && BlockUtils.isAnchorCharged(blockHit.getBlockPos())) {
            pressSneak();
            protectTimer = protectTicks.getValueInt();
        }
    }

    private void runSafeAnchor() {
        if (!InventoryUtils.hasItemInHotbar(i -> i == Items.RESPAWN_ANCHOR)
                || !InventoryUtils.hasItemInHotbar(i -> i == Items.GLOWSTONE)) return;

        if (safeTarget == null || !isValidAnchorTarget(safeTarget)) {
            safeTarget = findAnchorTarget().orElse(null);
            safeStage = safeTarget == null ? SafeStage.PLACE : stageFor(safeTarget);
        }

        if (!isActionReady()) return;

        if (safeTarget == null) {
            placeLookedAnchor();
            return;
        }

        aimAt(safeTarget);
        safeStage = stageFor(safeTarget);
        switch (safeStage) {
            case PLACE -> placeLookedAnchor();
            case CHARGE -> chargeAnchor(safeTarget);
            case PROTECT -> protectBeforeExplode();
            case EXPLODE -> explodeAnchor(safeTarget);
        }
    }

    private void runLegacyAnchor() {
        HitResult hit = WorldUtils.getHitResult(4.5);
        if (!(hit instanceof BlockHitResult blockHit)) return;

        BlockPos pos = blockHit.getBlockPos();
        if (placer.getValue() && isActionReady() && InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) {
            performUse(blockHit, true);
            trackAnchor(getPlacementPos(blockHit));
            resetActionDelay();
            return;
        }

        if (!isTrackedOrAllowed(pos)) return;

        if (charger.getValue() && BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR) && BlockUtils.isAnchorNotCharged(pos) && isActionReady())
            chargeAnchor(pos);
        else if (exploder.getValue() && BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR) && BlockUtils.isAnchorCharged(pos) && isActionReady())
            explodeAnchor(pos);
    }

    private void placeLookedAnchor() {
        if (!placer.getValue()) return;
        HitResult hit = WorldUtils.getHitResult(4.5);
        if (!(hit instanceof BlockHitResult blockHit)) return;
        BlockPos placedPos = getPlacementPos(blockHit);
        if (!canPlaceAnchorAt(placedPos) || !InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) return;

        performUse(blockHit, true);
        trackAnchor(placedPos);
        safeTarget = placedPos;
        safeStage = SafeStage.CHARGE;
        resetActionDelay();
    }

    private void chargeAnchor(BlockPos pos) {
        if (!charger.getValue() || !InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) return;
        performUse(centerHit(pos), true);
        trackAnchor(pos);
        safeStage = SafeStage.PROTECT;
        resetActionDelay();
    }

    private void protectBeforeExplode() {
        if (!safeExplode.getValue()) {
            safeStage = SafeStage.EXPLODE;
            return;
        }

        pressSneak();
        protectTimer++;
        if (protectTimer >= protectTicks.getValueInt()) {
            protectTimer = 0;
            safeStage = SafeStage.EXPLODE;
        }
    }

    private void explodeAnchor(BlockPos pos) {
        if (!exploder.getValue() || explodeTimer < explodeCooldown.getValueInt()) return;

        int slot = MathHelper.clamp(explodeSlot.getValueInt() - 1, 0, 8);
        InventoryUtils.setInvSlot(slot);
        if (safeExplode.getValue()) pressSneak();
        performUse(centerHit(pos), false);

        explodeTimer = 0;
        safeTarget = null;
        safeStage = SafeStage.PLACE;
        resetActionDelay();
    }

    private void performUse(BlockHitResult hit, boolean useInteractionManager) {
        mc.options.useKey.setPressed(false);
        if (clickSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT, MathUtils.randomInt(28, 54));

        if (useInteractionManager) WorldUtils.placeBlock(hit, true);
        else mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
    }

    private boolean isActionReady() {
        return actionTimer >= actionDelay;
    }

    private void resetActionDelay() {
        actionTimer = 0;
        actionDelay = MathUtils.randomInt(randomDelay.getMinInt(), randomDelay.getMaxInt());
    }

    private boolean isMacroKeyHeld() {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    private boolean isUsingBlockedItem() {
        if (!useGate.getValue()) return false;
        UseAction action = mc.player.getMainHandStack().getUseAction();
        return action == UseAction.BLOCK || action == UseAction.SPEAR
                || action == UseAction.BOW || action == UseAction.CROSSBOW
                || action == UseAction.EAT || action == UseAction.DRINK;
    }

    private Optional<BlockPos> findAnchorTarget() {
        HitResult hit = WorldUtils.getHitResult(4.5);
        if (hit instanceof BlockHitResult blockHit && isValidAnchorTarget(blockHit.getBlockPos()))
            return Optional.of(blockHit.getBlockPos());

        return trackedAnchors.keySet().stream()
                .filter(this::isValidAnchorTarget)
                .filter(pos -> mc.player.squaredDistanceTo(pos.toCenterPos()) <= scanRange.getValue() * scanRange.getValue())
                .min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(pos.toCenterPos())));
    }

    private SafeStage stageFor(BlockPos pos) {
        if (!BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR)) return SafeStage.PLACE;
        if (BlockUtils.isAnchorNotCharged(pos)) return SafeStage.CHARGE;
        if (safeExplode.getValue() && !sneaking) return SafeStage.PROTECT;
        return SafeStage.EXPLODE;
    }

    private boolean isValidAnchorTarget(BlockPos pos) {
        return pos != null
                && BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR)
                && isTrackedOrAllowed(pos);
    }

    private boolean isTrackedOrAllowed(BlockPos pos) {
        return !onlyOwnAnchor.getValue() || trackedAnchors.containsKey(pos);
    }

    private void monitorNearbyOwnAnchors() {
        if (!onlyOwnAnchor.getValue() || mc.player == null) return;
        int range = (int) Math.ceil(scanRange.getValue());
        BlockPos playerPos = mc.player.getBlockPos();
        BlockUtils.getAllInBoxStream(playerPos.add(-range, -range, -range), playerPos.add(range, range, range))
                .filter(pos -> trackedAnchors.containsKey(pos) && BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR))
                .forEach(this::trackAnchor);
    }

    private void pruneTrackedAnchors() {
        trackedAnchors.entrySet().removeIf(entry -> worldTicks - entry.getValue().lastSeenTick() > 20 * 30
                || !BlockUtils.isBlock(entry.getKey(), Blocks.RESPAWN_ANCHOR));
    }

    private void trackAnchor(BlockPos pos) {
        if (pos == null || mc.player == null) return;
        trackedAnchors.put(pos.toImmutable(), new TrackedAnchor(pos.toImmutable(), mc.player.getUuid(), worldTicks));
    }

    private BlockPos getPlacementPos(BlockHitResult hit) {
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        return state.isReplaceable() ? hit.getBlockPos() : hit.getBlockPos().offset(hit.getSide());
    }

    private boolean canPlaceAnchorAt(BlockPos pos) {
        return pos != null && mc.world.getBlockState(pos).isReplaceable();
    }

    private BlockHitResult centerHit(BlockPos pos) {
        return new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
    }

    private void aimAt(BlockPos pos) {
        if (rotationMode.isMode(RotationMode.SILENT) || mc.player == null) return;
        Rotation target = RotationUtils.getDirection(mc.player, pos.toCenterPos());
        Rotation current = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        Rotation smooth = RotationUtils.getSmoothRotation(current, target, 0.35);
        mc.player.setYaw((float) smooth.yaw());
        mc.player.setPitch((float) smooth.pitch());
    }

    private void pressSneak() {
        mc.options.sneakKey.setPressed(true);
        sneaking = true;
    }

    private void releaseSneak() {
        if (mc.player != null) mc.options.sneakKey.setPressed(false);
        sneaking = false;
        protectTimer = 0;
    }

    private void resetState() {
        actionTimer = 0;
        actionDelay = 0;
        explodeTimer = 0;
        worldTicks = 0;
        protectTimer = 0;
        sneaking = false;
        safeStage = SafeStage.PLACE;
        safeTarget = null;
        trackedAnchors.clear();
    }
}
