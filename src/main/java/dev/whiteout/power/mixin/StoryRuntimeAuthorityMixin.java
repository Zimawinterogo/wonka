package dev.whiteout.power.mixin;

import java.util.List;
import java.util.function.*;
import dev.whiteout.StoryRules;
import dev.whiteout.client.StoryRuntime;
import dev.whiteout.power.StoryAuthorityClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=StoryRuntime.class,remap=false)
public abstract class StoryRuntimeAuthorityMixin {
 @Inject(method="fire",at=@At("HEAD"),cancellable=true,remap=false)
 private static void whiteout$authorizedFire(String key,boolean once,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,BooleanSupplier action,Consumer<Boolean> done,CallbackInfoReturnable<Boolean> ci){
  ci.setReturnValue(StoryAuthorityClient.fire(key,action,done));
 }
 @Inject(method="commit",at=@At("HEAD"),cancellable=true,remap=false)
 private static void whiteout$noSpeculativeCommit(String key,boolean once,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(false);}
 @Inject(method="busy",at=@At("HEAD"),cancellable=true,remap=false)
 private static void whiteout$pending(CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(StoryAuthorityClient.busy());}
 @Inject(method="reset",at=@At("HEAD"),remap=false)
 private static void whiteout$reset(CallbackInfo ci){StoryAuthorityClient.reset();}
}
