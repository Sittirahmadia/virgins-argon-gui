package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public final class Hitboxes extends Module implements TickListener {

    private final ModeSetting<ExpandMode> mode = new ModeSetting<>(
            EncryptedString.of("Expand Mode"), ExpandMode.Blatant, ExpandMode.class);

    private final NumberSetting expandAmount = new NumberSetting(
            EncryptedString.of("Expand Amount"), 0.0, 1.0, 0.1, 0.01);

    public enum ExpandMode {
        Blatant, Legit
    }

    public Hitboxes() {
        super(EncryptedString.of("Hitboxes"),
                EncryptedString.of("Expands enemy hitboxes"),
                -1,
                Category.COMBAT);
        addSettings(mode, expandAmount);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        double expand = expandAmount.getValue();
        if (expand <= 0) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity)) continue;
            if (entity == mc.player) continue;

            Box expanded = entity.getBoundingBox().expand(expand, 0, expand);
            // Expanding bounding box for hitbox detection
            // The actual hitbox expansion is handled via the EntityHitboxEvent equivalent
            // For Argon, this serves as a reference implementation
        }
    }

    public double getExpandAmount() {
        return expandAmount.getValue();
    }

    public boolean isBlatant() {
        return mode.isMode(ExpandMode.Blatant);
    }
}
