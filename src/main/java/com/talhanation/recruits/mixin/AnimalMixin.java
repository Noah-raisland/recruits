package com.talhanation.recruits.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In Minecraft 1.21.1 Animal no longer declares its own hurt method. The
 * implementation lives on LivingEntity, so inject there and restrict the
 * behaviour to Animal instances at runtime.
 */
@Mixin(LivingEntity.class)
public class AnimalMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void recruits$hurtWhenRecruitRides(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Animal animal
                && animal.isAlive()
                && animal.isVehicle()
                && animal.getControllingPassenger() instanceof AbstractRecruitEntity recruit
                && source.getEntity() instanceof LivingEntity target
                && recruit.canAttack(target)) {
            recruit.setTarget(target);
        }
    }
}
