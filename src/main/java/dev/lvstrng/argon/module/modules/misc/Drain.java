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
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class Drain extends Module implements TickListener, AttackListener {

    public enum ActivationMode { Auto, OnHit }
    public enum LiquidMode     { Water, Lava, Both }
    public enum TargetMode     { Self, Enemy, Both }

    private final ModeSetting<ActivationMode> activation = new ModeSetting<>(
            EncryptedString.of("Activation"), ActivationMode.OnHit, ActivationMode.class)
            .setDescription(EncryptedString.of("Auto runs every tick; OnHit runs when you attack"));

    private final ModeSetting<LiquidMode> liquidMode = new ModeSetting<>(
            EncryptedString.of("Liquid"), LiquidMode.Both, LiquidMode.class)
            .setDescription(EncryptedString.of("Which liquid type to drain"));

    private final ModeSetting<TargetMode> targetMode = new ModeSetting<>(
            EncryptedString.of("Target"), TargetMode.Enemy, TargetMode.class)
            .setDescription(EncryptedString.of("Area to drain: around self, enemy, or both"));

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 8.0, 4.0, 0.5);

    private final NumberSetting cooldown = new NumberSetting(
            EncryptedString.of("Cooldown"), 0.0, 5000.0, 500.0, 100.0)
            .setDescription(EncryptedString.of("Milliseconds between drains"));

    private final BooleanSetting drainAll = new BooleanSetting(
            EncryptedString.of("Drain All"), true)
            .setDescription(EncryptedString.of("Drain all matching blocks at once instead of one per tick"));

    private final TimerUtils timer = new TimerUtils();

    public Drain() {
        super(EncryptedString.of("Drain"),
                EncryptedString.of("Removes water or lava from around you or your target"),
                -1, Category.MISC);
        addSettings(activation, liquidMode, targetMode, range, cooldown, drainAll);
    }

    @Override public void onEnable()  { eventManager.add(TickListener.class, this); eventManager.add(AttackListener.class, this); super.onEnable(); }
    @Override public void onDisable() { eventManager.remove(TickListener.class, this); eventManager.remove(AttackListener.class, this); super.onDisable(); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (activation.isMode(ActivationMode.Auto)) execute();
    }

    @Override
    public void onAttack(AttackListener.AttackEvent event) {
        if (activation.isMode(ActivationMode.OnHit)) execute();
    }

    private void execute() {
        if (!timer.hasReached(cooldown.getValue())) return;
        if (!InventoryUtils.hasItemInHotbar(i -> i == Items.BUCKET)) return;

        boolean found = false;
        if (targetMode.isMode(TargetMode.Self) || targetMode.isMode(TargetMode.Both))
            found |= drainAround(mc.player.getBlockPos());

        if (targetMode.isMode(TargetMode.Enemy) || targetMode.isMode(TargetMode.Both)) {
            PlayerEntity enemy = WorldUtils.findNearestPlayer(mc.player, range.getValueFloat(), true, true);
            if (enemy != null) found |= drainAround(enemy.getBlockPos());
        }

        if (found) timer.reset();
    }

    private boolean drainAround(BlockPos center) {
        double r = range.getValue();
        Box box = new Box(center).expand(r);
        boolean found = false;

        for (BlockPos pos : BlockPos.iterate(
                BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {

            if (!shouldDrain(pos)) continue;
            interactAt(pos.toImmutable());
            found = true;
            if (!drainAll.getValue()) return true;
        }
        return found;
    }

    private void interactAt(BlockPos pos) {
        InventoryUtils.selectItemFromHotbar(Items.BUCKET);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted() && result.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean shouldDrain(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return switch (liquidMode.getMode()) {
            case Water -> state.isOf(Blocks.WATER);
            case Lava  -> state.isOf(Blocks.LAVA);
            case Both  -> state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
        };
    }
}
