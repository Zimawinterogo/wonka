package dev.whiteout.power.mixin;

import dev.whiteout.power.backpack.BackpackVisualClient;
import dev.whiteout.power.backpack.BackpackPoseModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applied to the real player model before vanilla copies sleeves and armor transforms. */
@Mixin(BipedEntityModel.class)
public abstract class BackpackPlayerPoseMixin {
 @Inject(method="setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
 private void whiteout$seat(LivingEntity entity,float limb,float distance,float age,float yaw,float pitch,CallbackInfo ci){
  if(!(entity instanceof PlayerEntity player))return;
  float sit=BackpackVisualClient.amount(player);if(sit<=0)return;
  BipedEntityModel<?> m=(BipedEntityModel<?>)(Object)this;
  BackpackPoseModel.apply(m,sit,BackpackVisualClient.unpack(player));
 }
}
