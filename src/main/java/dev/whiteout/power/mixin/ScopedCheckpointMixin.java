package dev.whiteout.power.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.whiteout.CheckpointService;
import dev.whiteout.Store;
import dev.whiteout.power.ScopedStory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=CheckpointService.class,remap=false)
public abstract class ScopedCheckpointMixin {
 @Shadow private static void persist(Store store){throw new AssertionError();}
 @Shadow private static String normalizeId(String id){throw new AssertionError();}
 @Redirect(method={"saveCurrent","load","delete"},at=@At(value="FIELD",target="Ldev/whiteout/Store;data:Ldev/whiteout/Store$Data;",opcode=Opcodes.GETFIELD))
 private static Store.Data whiteout$select(Store store,MinecraftServer server,ServerPlayerEntity player,String id,@Share("personal") LocalRef<Store.Data> personal){
  if(personal.get()==null)personal.set(ScopedStory.checkpointView(store,player.getUuid(),normalizeId(id)));
  return personal.get()==null?store.data:personal.get();
 }
 @Redirect(method={"saveCurrent","load","delete"},at=@At(value="INVOKE",target="Ldev/whiteout/CheckpointService;persist(Ldev/whiteout/Store;)V"))
 private static void whiteout$persist(Store store,MinecraftServer server,ServerPlayerEntity player,String id,@Share("personal") LocalRef<Store.Data> personal){
  if(personal.get()==null){persist(store);return;}
  try{ScopedStory.persistCheckpoint(store,player.getUuid(),personal.get());}
  catch(java.io.IOException failure){throw new IllegalStateException("Cannot save personal checkpoint",failure);}
 }
}
