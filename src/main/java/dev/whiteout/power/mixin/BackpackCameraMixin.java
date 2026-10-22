package dev.whiteout.power.mixin;

import dev.whiteout.power.backpack.BackpackVisualClient;
import dev.whiteout.power.backpack.BackpackPosePolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class BackpackCameraMixin {
 @Shadow public abstract Vec3d getPos();
 @Shadow protected abstract void setPos(double x,double y,double z);
 @Inject(method="update",at=@At("TAIL"))
 private void whiteout$backpackDip(BlockView world,Entity entity,boolean thirdPerson,boolean inverseView,float tickDelta,CallbackInfo ci){
  MinecraftClient client=MinecraftClient.getInstance();
  // Vanilla computes the original camera each frame; closing needs no state restoration.
  if(!thirdPerson&&entity==client.player&&client.player!=null){
   float dip=BackpackPosePolicy.DROP*BackpackVisualClient.amount(client.player);
   Vec3d position=getPos();setPos(position.x,position.y-dip,position.z);
  }
 }
}
