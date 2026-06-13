package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.combat.Hitboxes;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true)
    private void argon$expandTargetingMargin(CallbackInfoReturnable<Float> cir) {
        Hitboxes hitboxes = Argon.INSTANCE.getModuleManager().getModule(Hitboxes.class);
        if (hitboxes != null && hitboxes.isEnabled()) {
            cir.setReturnValue(hitboxes.getTargetingMargin((Entity) (Object) this, cir.getReturnValueF()));
        }
    }
}
