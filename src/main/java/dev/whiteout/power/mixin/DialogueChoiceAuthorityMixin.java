package dev.whiteout.power.mixin;

import java.util.List;
import dev.whiteout.StoryRules;
import dev.whiteout.client.*;
import dev.whiteout.power.StoryAuthorityClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve the existing reply/effect presentation after the server accepts the selected choice. */
@Mixin(value=DialogueScreen.class,remap=false)
public abstract class DialogueChoiceAuthorityMixin {
 @Inject(method="choiceAvailable",at=@At("HEAD"),cancellable=true,remap=false)
 private void whiteout$visible(int index,DialogueManager.Choice choice,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci){
  if(dev.whiteout.power.MigratedTriggerClient.hidden(choiceKey(index)))ci.setReturnValue(false);
 }
 @Unique private int whiteout$choiceIndex;
 @ModifyArg(method={"tick","method_25393"},at=@At(value="INVOKE",target="Ljava/lang/Math;min(II)I",ordinal=0),index=1,remap=false)
 private int whiteout$readingSpeed(int ticks){return ticks*dev.whiteout.power.lobby.PersonalScreen.prefs().letters();}
 @Shadow private String choiceKey(int index){throw new AssertionError();}
 @Redirect(method="choose",at=@At(value="INVOKE",target="Ldev/whiteout/client/DialogueManager$Choice;once()Z"),remap=false)
 private boolean whiteout$requiresConfirmation(DialogueManager.Choice choice){
  return choice.once()||dev.whiteout.power.MigratedTriggerClient.managed(choiceKey(whiteout$choiceIndex));
 }
 @Redirect(method="choose",at=@At(value="INVOKE",target="Ldev/whiteout/client/StoryEffects;preflight(Ljava/util/List;)Z"),remap=false)
 private boolean whiteout$preflight(List<StoryEffects.Effect> effects){
  return StoryEffects.preflight(dev.whiteout.power.MigratedTriggerClient.managed(choiceKey(whiteout$choiceIndex))
   ?dev.whiteout.power.MigratedTriggerClient.presentation(effects):effects);
 }
 @Shadow private DialogueManager.Line currentLine(){throw new AssertionError();}
 @Shadow private void logCurrentLine(){throw new AssertionError();}
 @Shadow private void showPlayerReply(DialogueManager.Line line,String text){throw new AssertionError();}
 @Shadow private void executeReply(String command,String dialogue,int next){throw new AssertionError();}
 @Inject(method="choose",at=@At("HEAD"),cancellable=true,remap=false)
 private void whiteout$rememberChoice(int index,CallbackInfo ci){
  if(StoryAuthorityClient.busy()){ci.cancel();return;}
  whiteout$choiceIndex=index;
 }
 @Redirect(method="choose",at=@At(value="INVOKE",target="Ldev/whiteout/client/StoryRuntime;commit(Ljava/lang/String;ZLjava/util/List;Ljava/util/List;)Z"),remap=false)
 private boolean whiteout$awaitChoice(String key,boolean once,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions){
  var line=currentLine();var choice=line.choices().get(whiteout$choiceIndex);var mc=MinecraftClient.getInstance();
  StoryAuthorityClient.request(key,()->mc.currentScreen==(Object)this&&currentLine()==line,()->true,()->{
   boolean migrated=dev.whiteout.power.MigratedTriggerClient.managed(key);
   var effects=migrated?dev.whiteout.power.MigratedTriggerClient.presentation(choice.effects()):choice.effects();
   if(!StoryEffects.preflight(effects))return false;
   logCurrentLine();showPlayerReply(line,choice.text());
   boolean blocking=StoryEffects.hasBlocking(effects);
   if(!effects.isEmpty()&&!StoryEffects.run(effects))return false;
   if(!blocking){
    if(migrated&&choice.command()!=null&&choice.command().regionMatches(true,0,"director_signal:",0,16)){mc.setScreen(null);return true;}
    executeReply(migrated?"":choice.command(),choice.dialogue(),choice.next());
   }
   return true;
  },null);
  return false;
 }
}
