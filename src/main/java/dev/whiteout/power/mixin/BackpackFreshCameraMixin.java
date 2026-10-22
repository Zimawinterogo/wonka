package dev.whiteout.power.mixin;

import dev.whiteout.power.backpack.BackpackPosePolicy;
import dev.whiteout.power.backpack.BackpackVisualClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** FFP 1.3.0 samples the posed head. Remove our contribution before its smoothing:
 * Whiteout's camera mixin already applies this exact displacement once. */
@Pseudo
@Mixin(targets="dev.freshfirstperson.body.pose.HeadOffsetCapture",remap=false)
public abstract class BackpackFreshCameraMixin {
 @ModifyVariable(method="commitSample(FFFF)V",at=@At("HEAD"),argsOnly=true,ordinal=1,remap=false)
 private static float whiteout$excludeOwnHeadDrop(float y){
  var player=MinecraftClient.getInstance().player;
  return player==null?y:y-BackpackPosePolicy.DROP*BackpackVisualClient.amount(player);
 }
}
