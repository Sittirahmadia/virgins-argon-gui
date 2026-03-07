package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.TimerUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class LavaSpam extends Module implements TickListener {

    private final NumberSetting spamTickDelay = new NumberSetting(
            EncryptedString.of("Spam Delay"), 1.0, 20.0, 2.0, 1.0)
            .setDescription(EncryptedString.of("Ticks between each lava placement"));

    private final NumberSetting lavaOffsetY = new NumberSetting(
            EncryptedString.of("Lava Offset Y"), 1.0, 5.0, 2.0, 1.0)
            .setDescription(EncryptedString.of("Blocks above the cobweb to place lava"));

    private final NumberSetting searchRange = new NumberSetting(
            EncryptedString.of("Search Range"), 2.0, 12.0, 5.0, 1.0)
            .setDescription(EncryptedString.of("Radius to search for nearby cobwebs"));

    private final NumberSetting selfBurnPrevention = new NumberSetting(
            EncryptedString.of("Self Burn Prevention"), 0.0, 10.0, 4.0, 0.5)
            .setDescription(EncryptedString.of("Skip if estimated self-damage would exceed this (hearts)"));

    private final BooleanSetting autoRemove = new BooleanSetting(
            EncryptedString.of("Auto Remove"), true)
            .setDescription(EncryptedString.of("Pick up placed lava automatically after a delay"));

    private final NumberSetting removeDelay = new NumberSetting(
            EncryptedString.of("Remove Delay"), 0.0, 3000.0, 500.0, 50.0)
            .setDescription(EncryptedString.of("Milliseconds before collecting the lava"));

    private final BooleanSetting requireHold = new BooleanSetting(
            EncryptedString.of("Require Hold"), true)
            .setDescription(EncryptedString.of("Hold right-click to spam; off = runs passively"));

    private int tickCounter = 0;
    private BlockPos lastPlaced = null;
    private final TimerUtils removeTimer = new TimerUtils();

    public LavaSpam() {
        super(EncryptedString.of("Lava Spam"),
                EncryptedString.of("Spams lava above nearby cobwebs to burn trapped enemies"),
                -1, Category.MISC);
        addSettings(spamTickDelay, lavaOffsetY, searchRange,
                selfBurnPrevention, autoRemove, removeDelay, requireHold);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        tickCounter = 0;
        lastPlaced = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        tickCounter = 0;
        lastPlaced = null;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Handle auto-remove
        if (autoRemove.getValue() && lastPlaced != null && removeTimer.hasReached(removeDelay.getValue())) {
            if (mc.world.getBlockState(lastPlaced).isOf(Blocks.LAVA)) {
                InventoryUtils.selectItemFromHotbar(Items.BUCKET);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(lastPlaced), Direction.UP, lastPlaced, false);
                ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                if (r.isAccepted() && r.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);
            }
            lastPlaced = null;
        }

        boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (requireHold.getValue() && !rmb) {
            tickCounter = 0;
            return;
        }

        tickCounter++;
        if (tickCounter < (int) spamTickDelay.getValue()) return;
        tickCounter = 0;

        if (!InventoryUtils.hasItemInHotbar(i -> i == Items.LAVA_BUCKET)) return;

        BlockPos webPos = findNearestCobweb();
        if (webPos == null) return;

        BlockPos lavaPos = webPos.up((int) lavaOffsetY.getValue());
        if (!mc.world.getBlockState(lavaPos).isAir()) return;
        if (wouldBurnSelf(lavaPos)) return;

        InventoryUtils.selectItemFromHotbar(Items.LAVA_BUCKET);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(lavaPos), Direction.UP, lavaPos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            if (result.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);
            lastPlaced = lavaPos;
            removeTimer.reset();
        }
    }

    private BlockPos findNearestCobweb() {
        BlockPos playerPos = mc.player.getBlockPos();
        double r = searchRange.getValue();
        Box box = new Box(playerPos).expand(r);

        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(
                BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) continue;
            double dist = mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ());
            if (dist < nearestDist) { nearestDist = dist; nearest = pos.toImmutable(); }
        }
        return nearest;
    }

    private boolean wouldBurnSelf(BlockPos lavaPos) {
        double dist = mc.player.getPos().distanceTo(Vec3d.ofCenter(lavaPos));
        if (dist > 3.0) return false;
        return (3.0 - dist) * 2.0 > selfBurnPrevention.getValue();
    }
}
