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
 @Unique private int whiteout$choiceIndex;
 @Shadow private DialogueManager.Line currentLine(){throw new AssertionError();}
 @Shadow private void logCurrentLine(){throw new AssertionError();}
 @Shadow private void showPlayerReply(DialogueManager.Line line,String text){throw new AssertionError();}
 @Shadow private void executeReply(String command,String dialogue,int next){throw new AssertionError();}
 @Inject(method="choose",at=@At("HEAD"),remap=false)
 private void whiteout$rememberChoice(int index,CallbackInfo ci){whiteout$choiceIndex=index;}
 @Redirect(method="choose",at=@At(value="INVOKE",target="Ldev/whiteout/client/StoryRuntime;commit(Ljava/lang/String;ZLjava/util/List;Ljava/util/List;)Z"),remap=false)
 private boolean whiteout$awaitChoice(String key,boolean once,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions){
  var line=currentLine();var choice=line.choices().get(whiteout$choiceIndex);var mc=MinecraftClient.getInstance();
  StoryAuthorityClient.request(key,()->mc.currentScreen==(Object)this&&currentLine()==line,()->true,()->{
   if(!StoryEffects.preflight(choice.effects()))return false;
   logCurrentLine();showPlayerReply(line,choice.text());
   boolean blocking=StoryEffects.hasBlocking(choice.effects());
   if(!choice.effects().isEmpty()&&!StoryEffects.run(choice.effects()))return false;
   if(!blocking)executeReply(choice.command(),choice.dialogue(),choice.next());return true;
  },null);
  return false;
 }
}
