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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class LavaPlace extends Module implements TickListener, AttackListener {

    public enum ActivationMode { Auto, OnHit }

    private final ModeSetting<ActivationMode> activation = new ModeSetting<>(
            EncryptedString.of("Activation"), ActivationMode.OnHit, ActivationMode.class);

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 8.0, 4.0, 0.5);

    private final NumberSetting offsetX = new NumberSetting(
            EncryptedString.of("Offset X"), -3.0, 3.0, 0.0, 1.0);

    private final NumberSetting offsetY = new NumberSetting(
            EncryptedString.of("Offset Y"), -2.0, 5.0, 1.0, 1.0)
            .setDescription(EncryptedString.of("Blocks above target feet to place lava"));

    private final NumberSetting offsetZ = new NumberSetting(
            EncryptedString.of("Offset Z"), -3.0, 3.0, 0.0, 1.0);

    private final NumberSetting cooldown = new NumberSetting(
            EncryptedString.of("Cooldown"), 0.0, 5000.0, 500.0, 100.0);

    private final BooleanSetting autoRemove = new BooleanSetting(
            EncryptedString.of("Auto Remove"), false)
            .setDescription(EncryptedString.of("Pick up the lava automatically after a delay"));

    private final NumberSetting removeDelay = new NumberSetting(
            EncryptedString.of("Remove Delay"), 0.0, 5000.0, 800.0, 50.0)
            .setDescription(EncryptedString.of("Milliseconds before collecting the lava"));

    private final BooleanSetting requireAir = new BooleanSetting(
            EncryptedString.of("Require Air"), true)
            .setDescription(EncryptedString.of("Only place lava if the target block is air"));

    private final TimerUtils cooldownTimer = new TimerUtils();
    private final TimerUtils removeTimer   = new TimerUtils();
    private BlockPos pendingRemove = null;

    public LavaPlace() {
        super(EncryptedString.of("Lava Place"),
                EncryptedString.of("Places lava above the nearest enemy with configurable offsets"),
                -1, Category.MISC);
        addSettings(activation, range, offsetX, offsetY, offsetZ,
                cooldown, autoRemove, removeDelay, requireAir);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(AttackListener.class, this);
        pendingRemove = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(AttackListener.class, this);
        pendingRemove = null;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Handle scheduled auto-remove
        if (pendingRemove != null && removeTimer.hasReached(removeDelay.getValue())) {
            if (mc.world.getBlockState(pendingRemove).isOf(Blocks.LAVA)) {
                InventoryUtils.selectItemFromHotbar(Items.BUCKET);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pendingRemove), Direction.UP, pendingRemove, false);
                ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                if (r.isAccepted() && r.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);
            }
            pendingRemove = null;
        }

        if (activation.isMode(ActivationMode.Auto)) execute();
    }

    @Override
    public void onAttack(AttackListener.AttackEvent event) {
        if (activation.isMode(ActivationMode.OnHit)) execute();
    }

    private void execute() {
        if (!cooldownTimer.hasReached(cooldown.getValue())) return;
        if (!InventoryUtils.hasItemInHotbar(i -> i == Items.LAVA_BUCKET)) return;

        PlayerEntity target = WorldUtils.findNearestPlayer(mc.player, range.getValueFloat(), true, true);
        if (target == null) return;

        BlockPos placePos = target.getBlockPos().add(
                (int) offsetX.getValue(), (int) offsetY.getValue(), (int) offsetZ.getValue());

        if (requireAir.getValue() && !mc.world.getBlockState(placePos).isAir()) return;

        InventoryUtils.selectItemFromHotbar(Items.LAVA_BUCKET);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(placePos), Direction.UP, placePos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted() && result.shouldSwingHand()) mc.player.swingHand(Hand.MAIN_HAND);

        if (autoRemove.getValue()) {
            pendingRemove = placePos;
            removeTimer.reset();
        }
        cooldownTimer.reset();
    }
}
