package dev.whiteout.power.mixin;

import dev.whiteout.Store;
import dev.whiteout.power.ScopedStory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.nio.file.*;

@Mixin(value=Store.class,remap=false)
public abstract class StoryStoreMixin {
 @Shadow public Store.Data data;
 @Shadow public String error;
 @Redirect(method="save",at=@At(value="INVOKE",target="Ljava/nio/file/Files;writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;Ljava/nio/charset/Charset;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"))
 private Path whiteout$flush(Path path,CharSequence content,java.nio.charset.Charset charset,OpenOption[] options)throws java.io.IOException{
  Files.writeString(path,content,charset,options);
  try(var channel=java.nio.channels.FileChannel.open(path,StandardOpenOption.WRITE)){channel.force(true);}
  return path;
 }
 @Inject(method="validateData",at=@At("RETURN"))
 private void whiteout$validatePlayers(CallbackInfo ci){ScopedStory.validate(data);dev.whiteout.power.SceneDelivery.validate(((dev.whiteout.power.SceneDelivery.Access)(Object)data).whiteout$grants());dev.whiteout.power.DialogueBarrier.validate(((dev.whiteout.power.DialogueBarrier.Access)(Object)data).whiteout$readers());}
 @Inject(method="networkSnapshot",at=@At("RETURN"))
 private void whiteout$privateProgress(CallbackInfoReturnable<Store.Data> ci){
  ((ScopedStory.Players)(Object)ci.getReturnValue()).whiteout$players().clear();
  ((dev.whiteout.power.lobby.LobbyState.Access)(Object)ci.getReturnValue()).whiteout$lobby(null);
  ((dev.whiteout.power.SharedScene.Access)(Object)ci.getReturnValue()).whiteout$scene(null);
  ((dev.whiteout.power.SceneDelivery.Access)(Object)ci.getReturnValue()).whiteout$grants().clear();
  ((dev.whiteout.power.DialogueBarrier.Access)(Object)ci.getReturnValue()).whiteout$readers(new dev.whiteout.power.DialogueBarrier.Readers());
 }
 @Inject(method="<init>(Ljava/nio/file/Path;)V",at=@At("RETURN"))
 private void whiteout$incompleteSave(Path path,CallbackInfo ci){
  if(!Files.exists(path)&&(Files.exists(path.resolveSibling(path.getFileName()+".bak"))||Files.exists(path.resolveSibling(path.getFileName()+".tmp"))))
   error="Missing Store file with recovery files present: "+path.getFileName();
 }
}
