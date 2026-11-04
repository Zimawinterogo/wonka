package dev.whiteout.power.mixin;

import dev.whiteout.client.director.DirectorTriggerManager;
import dev.whiteout.power.MigratedTriggerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=DirectorTriggerManager.class,remap=false)
public abstract class MigratedTriggerMixin {
 @Inject(method={"schedule","launch"},at=@At("HEAD"),cancellable=true,remap=false)
 private static void whiteout$serverManaged(DirectorTriggerManager.Trigger trigger,CallbackInfo ci){
  if(trigger!=null&&MigratedTriggerClient.managed("cutscene_trigger:"+trigger.id))ci.cancel();
 }
}
