package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.MovementPacketListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.AdvancedInputSimulator;
import dev.lvstrng.argon.utils.BlockUtils;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.RotationUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AnchorMacroV2 extends Module implements TickListener, ItemUseListener, MovementPacketListener, GameRenderListener {
    private enum RotationMode { Silent, Legit, Hybrid }
    private enum Stage { PLACE, CHARGE, PROTECT, EXPLODE }
    private enum TargetPriority { Nearest, Lowest_Health, Crosshair }
    private record TrackedAnchor(BlockPos pos, UUID owner, int tick, int seen) {}

    private final BooleanSetting global = new BooleanSetting(EncryptedString.of("Global Enabled"), true);
    private final BooleanSetting safeAnchor = new BooleanSetting(EncryptedString.of("Safe Anchor"), true);
    private final BooleanSetting onlyOwn = new BooleanSetting(EncryptedString.of("Only Own Anchor"), true);
    private final BooleanSetting visual = new BooleanSetting(EncryptedString.of("Visual Anchors"), true);
    private final NumberSetting anchorSlot = new NumberSetting(EncryptedString.of("Anchor Slot"), 1, 9, 1, 1);
    private final NumberSetting glowstoneSlot = new NumberSetting(EncryptedString.of("Glowstone Slot"), 1, 9, 2, 1);
    private final NumberSetting totemSlot = new NumberSetting(EncryptedString.of("Totem Slot"), 1, 9, 9, 1);
    private final NumberSetting explodeCooldown = new NumberSetting(EncryptedString.of("Explode Cooldown"), 0, 30, 8, 1);
    private final MinMaxSetting delay = new MinMaxSetting(EncryptedString.of("Delay Min/Max"), 0, 20, 1, 2, 5);
    private final NumberSetting scanRadius = new NumberSetting(EncryptedString.of("Scan Radius"), 2, 8, 5, 0.5);
    private final NumberSetting explodeRange = new NumberSetting(EncryptedString.of("Explode Range"), 2, 6, 4.5, 0.1);
    private final NumberSetting placeDistance = new NumberSetting(EncryptedString.of("Place Distance"), 1, 5, 3.5, 0.1);
    private final NumberSetting heightOffset = new NumberSetting(EncryptedString.of("Height Offset"), -2, 2, 0, 1);
    private final NumberSetting minHealth = new NumberSetting(EncryptedString.of("Min Health"), 1, 20, 8, 0.5);
    private final ModeSetting<RotationMode> rotations = new ModeSetting<>(EncryptedString.of("Rotations"), RotationMode.Hybrid, RotationMode.class);
    private final ModeSetting<TargetPriority> priority = new ModeSetting<>(EncryptedString.of("Priority"), TargetPriority.Nearest, TargetPriority.class);

    private final Map<BlockPos, TrackedAnchor> tracked = new HashMap<>();
    private int ticks;
    private int actionTicks;
    private int actionDelay;
    private int explodeTicks;
    private int protectTicks;
    private Stage stage = Stage.PLACE;
    private BlockPos activeAnchor;
    private Rotation serverRotation;

    public AnchorMacroV2() {
        super(EncryptedString.of("Anchor Macro V2"), EncryptedString.of("Advanced safe anchor automation with own-anchor tracking and humanized input"), -1, Category.COMBAT);
        addSettings(global, safeAnchor, onlyOwn, visual, anchorSlot, glowstoneSlot, totemSlot, explodeCooldown, delay,
                scanRadius, explodeRange, placeDistance, heightOffset, minHealth, rotations, priority);
    }

    @Override public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        eventManager.add(MovementPacketListener.class, this);
        eventManager.add(GameRenderListener.class, this);
        reset();
        super.onEnable();
    }

    @Override public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        eventManager.remove(MovementPacketListener.class, this);
        eventManager.remove(GameRenderListener.class, this);
        releaseSafetyKeys();
        reset();
        super.onDisable();
    }

    @Override public void onTick() {
        if (!canRun()) return;
        ticks++;
        actionTicks++;
        explodeTicks++;
        pruneTrackedAnchors();
        monitorTrackedAnchors();
        if (!global.getValue() || isUnsafe()) { releaseSafetyKeys(); return; }
        if (!isActionReady()) return;

        if (activeAnchor == null || !isUsableAnchor(activeAnchor)) {
            activeAnchor = findTrackedAnchor().orElse(null);
            stage = activeAnchor == null ? Stage.PLACE : stageFor(activeAnchor);
        }

        if (safeAnchor.getValue()) runSafeSequence();
        else runLookedAnchor();
    }

    @Override public void onItemUse(ItemUseEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isHolding(Items.RESPAWN_ANCHOR) && mc.crosshairTarget instanceof BlockHitResult hit) track(getPlacementPos(hit));
    }

    @Override public void onSendMovementPackets() {
        if (mc.player == null || serverRotation == null) return;
        if (rotations.isMode(RotationMode.Silent) || rotations.isMode(RotationMode.Hybrid)) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float) serverRotation.yaw(), (float) serverRotation.pitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
        }
    }

    @Override public void onGameRender(GameRenderEvent event) {
        if (!visual.getValue() || mc.player == null) return;
        Vec3d cam = RenderUtils.getCameraPos();
        for (BlockPos pos : tracked.keySet()) {
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > scanRadius.getValue() * scanRadius.getValue()) continue;
            Box box = new Box(pos).expand(0.02).offset(-cam.x, -cam.y, -cam.z);
            Color color = pos.equals(activeAnchor) ? new Color(255, 70, 130, 55) : new Color(70, 220, 255, 35);
            RenderUtils.renderFilledBox(event.matrices, (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ, color);
        }
    }

    private void runSafeSequence() {
        switch (stage) {
            case PLACE -> placeOptimalAnchor();
            case CHARGE -> charge(activeAnchor);
            case PROTECT -> protect();
            case EXPLODE -> explode(activeAnchor);
        }
    }

    private void runLookedAnchor() {
        HitResult hit = WorldUtils.getHitResult(explodeRange.getValue());
        if (!(hit instanceof BlockHitResult blockHit)) return;
        BlockPos pos = blockHit.getBlockPos();
        if (BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR)) {
            activeAnchor = pos;
            stage = stageFor(pos);
            runSafeSequence();
        } else placeOptimalAnchor();
    }

    private void placeOptimalAnchor() {
        BlockPos pos = findPlacePos().orElse(null);
        if (pos == null || !selectConfiguredSlot(anchorSlot, Items.RESPAWN_ANCHOR)) return;
        BlockHitResult hit = placementHit(pos);
        if (hit == null) return;
        aimAt(pos.toCenterPos());
        rightClick();
        WorldUtils.placeBlock(hit, true);
        track(pos);
        activeAnchor = pos;
        stage = Stage.CHARGE;
        resetActionDelay();
    }

    private void charge(BlockPos pos) {
        if (pos == null || !selectConfiguredSlot(glowstoneSlot, Items.GLOWSTONE)) return;
        aimAt(pos.toCenterPos());
        rightClick();
        WorldUtils.placeBlock(centerHit(pos), true);
        track(pos);
        stage = Stage.PROTECT;
        resetActionDelay();
    }

    private void protect() {
        selectConfiguredSlot(totemSlot, Items.TOTEM_OF_UNDYING);
        AdvancedInputSimulator.setKey(mc.options.sneakKey, true);
        protectTicks++;
        if (protectTicks >= 2 + delay.getRandomValueInt()) {
            protectTicks = 0;
            stage = Stage.EXPLODE;
        }
    }

    private void explode(BlockPos pos) {
        if (pos == null || explodeTicks < explodeCooldown.getValueInt()) return;
        if (!isOwnedOrAllowed(pos) || !BlockUtils.isAnchorCharged(pos)) { activeAnchor = null; stage = Stage.PLACE; return; }
        aimAt(pos.toCenterPos());
        rightClick();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, centerHit(pos), 0));
        explodeTicks = 0;
        activeAnchor = null;
        stage = Stage.PLACE;
        releaseSafetyKeys();
        resetActionDelay();
    }

    private Optional<BlockPos> findPlacePos() {
        LivingEntity target = findTarget();
        BlockPos center = target == null ? mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(), 2) : target.getBlockPos();
        center = center.up(heightOffset.getValueInt());
        return java.util.Arrays.stream(new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
                .map(center::offset)
                .filter(this::canPlaceAnchor)
                .filter(pos -> mc.player.squaredDistanceTo(pos.toCenterPos()) <= placeDistance.getValue() * placeDistance.getValue())
                .min(Comparator.comparingDouble(pos -> target == null ? mc.player.squaredDistanceTo(pos.toCenterPos()) : target.squaredDistanceTo(pos.toCenterPos())));
    }

    private LivingEntity findTarget() {
        Comparator<PlayerEntity> comparator = switch (priority.getMode()) {
            case Lowest_Health -> Comparator.comparingDouble(PlayerEntity::getHealth);
            case Crosshair -> Comparator.comparingDouble(p -> RotationUtils.getAngleToRotation(RotationUtils.getDirection(mc.player, p.getBoundingBox().getCenter())));
            case Nearest -> Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p));
        };
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && !p.isSpectator())
                .filter(p -> mc.player.squaredDistanceTo(p) <= scanRadius.getValue() * scanRadius.getValue())
                .min(comparator)
                .orElse(null);
    }

    private Optional<BlockPos> findTrackedAnchor() {
        return tracked.keySet().stream()
                .filter(this::isUsableAnchor)
                .filter(pos -> mc.player.squaredDistanceTo(pos.toCenterPos()) <= explodeRange.getValue() * explodeRange.getValue())
                .min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(pos.toCenterPos())));
    }

    private Stage stageFor(BlockPos pos) {
        if (!BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR)) return Stage.PLACE;
        if (BlockUtils.isAnchorNotCharged(pos)) return Stage.CHARGE;
        return safeAnchor.getValue() ? Stage.PROTECT : Stage.EXPLODE;
    }

    private boolean canRun() { return mc.player != null && mc.world != null && mc.currentScreen == null && mc.getNetworkHandler() != null; }

    private boolean isUnsafe() {
        ClientPlayerEntity player = mc.player;
        if (player.getHealth() + player.getAbsorptionAmount() <= minHealth.getValue()) return true;
        if (player.fallDistance > 4 || player.isTouchingWater()) return true;
        UseAction action = player.getMainHandStack().getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK || action == UseAction.BOW || action == UseAction.CROSSBOW;
    }

    private boolean isUsableAnchor(BlockPos pos) { return pos != null && BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR) && isOwnedOrAllowed(pos); }
    private boolean isOwnedOrAllowed(BlockPos pos) { return !onlyOwn.getValue() || tracked.containsKey(pos); }
    private boolean canPlaceAnchor(BlockPos pos) { return pos != null && mc.world.getBlockState(pos).isReplaceable() && placementHit(pos) != null; }
    private boolean isActionReady() { return actionTicks >= actionDelay; }

    private boolean selectConfiguredSlot(NumberSetting setting, net.minecraft.item.Item item) {
        int configured = MathHelper.clamp(setting.getValueInt() - 1, 0, 8);
        return AdvancedInputSimulator.selectHotbarSlot(configured, item);
    }

    private void rightClick() { AdvancedInputSimulator.rightClick(28, 58); }

    private void aimAt(Vec3d pos) {
        Rotation target = RotationUtils.getDirection(mc.player, pos);
        serverRotation = serverRotation == null ? target : RotationUtils.getSmoothRotation(serverRotation, target, 0.45);
        if (rotations.isMode(RotationMode.Legit) || rotations.isMode(RotationMode.Hybrid)) {
            mc.player.setYaw((float) serverRotation.yaw());
            mc.player.setPitch((float) serverRotation.pitch());
        }
    }

    private BlockHitResult centerHit(BlockPos pos) { return new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false); }

    private BlockHitResult placementHit(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (!mc.world.getBlockState(neighbor).isReplaceable()) {
                return new BlockHitResult(neighbor.toCenterPos(), direction.getOpposite(), neighbor, false);
            }
        }
        return null;
    }

    private BlockPos getPlacementPos(BlockHitResult hit) {
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        return state.isReplaceable() ? hit.getBlockPos().toImmutable() : hit.getBlockPos().offset(hit.getSide()).toImmutable();
    }

    private void track(BlockPos pos) {
        if (pos == null || mc.player == null) return;
        tracked.put(pos.toImmutable(), new TrackedAnchor(pos.toImmutable(), mc.player.getUuid(), ticks, ticks));
    }

    private void monitorTrackedAnchors() {
        int range = (int) Math.ceil(scanRadius.getValue());
        BlockPos playerPos = mc.player.getBlockPos();
        BlockUtils.getAllInBoxStream(playerPos.add(-range, -range, -range), playerPos.add(range, range, range))
                .filter(pos -> tracked.containsKey(pos) && BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR))
                .forEach(this::track);
    }

    private void pruneTrackedAnchors() {
        tracked.entrySet().removeIf(entry -> ticks - entry.getValue().seen() > 20 * 30 || !BlockUtils.isBlock(entry.getKey(), Blocks.RESPAWN_ANCHOR));
    }

    private void resetActionDelay() {
        actionTicks = 0;
        actionDelay = delay.getRandomValueInt();
    }

    private void releaseSafetyKeys() {
        if (mc.player != null) AdvancedInputSimulator.setKey(mc.options.sneakKey, false);
        protectTicks = 0;
    }

    private void reset() {
        ticks = 0;
        actionTicks = 0;
        actionDelay = 0;
        explodeTicks = 0;
        protectTicks = 0;
        stage = Stage.PLACE;
        activeAnchor = null;
        serverRotation = null;
        tracked.clear();
    }
}
