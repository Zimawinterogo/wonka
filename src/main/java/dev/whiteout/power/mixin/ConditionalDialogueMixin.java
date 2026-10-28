package dev.whiteout.power.mixin;

import dev.whiteout.client.DialogueManager;
import dev.whiteout.power.DialogueRouteClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Verified base API has no callback before script lookup. Ordinary scripts pass through. */
@Mixin(value=DialogueManager.class,remap=false)
public abstract class ConditionalDialogueMixin {
 @Inject(method="openDirector(Ljava/lang/String;I)Z",at=@At("HEAD"),cancellable=true,remap=false)
 private static void whiteout$routeDirector(String id,int ticks,CallbackInfoReturnable<Boolean> ci){
  if(DialogueRouteClient.intercept(id,ticks))ci.setReturnValue(true);
 }
 @Inject(method="open(Ljava/lang/String;)Z",at=@At("HEAD"),cancellable=true,remap=false)
 private static void whiteout$routeOpen(String id,CallbackInfoReturnable<Boolean> ci){
  if(DialogueRouteClient.intercept(id,-1))ci.setReturnValue(true);
 }
}
