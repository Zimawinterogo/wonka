package dev.whiteout.power.mixin;
import dev.whiteout.net.WhiteoutNetwork;
import dev.whiteout.net.WhiteoutActionPayload;
import dev.whiteout.power.EditorHistory;
import dev.whiteout.power.StoryAuthority;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=WhiteoutNetwork.class,remap=false)
public abstract class ZoneHistoryMixin {
 @Redirect(method="sync",at=@At(value="INVOKE",target="Ldev/whiteout/Store;networkSnapshot()Ldev/whiteout/Store$Data;"),remap=false)
 private static dev.whiteout.Store.Data whiteout$recipient(dev.whiteout.Store store,ServerPlayerEntity player,String message){
  return dev.whiteout.power.ScopedStory.snapshot(store,player.getUuid());
 }
 @Inject(method="handle",at=@At("HEAD"),cancellable=true,remap=false)
 private static void whiteout$before(ServerPlayerEntity player,WhiteoutActionPayload packet,CallbackInfo ci){
  if(!StoryAuthority.accept(player,packet)){ci.cancel();return;}
  EditorHistory.begin(player,packet);
 }
 @Inject(method="handle",at=@At("RETURN"),remap=false)
 private static void whiteout$after(ServerPlayerEntity player,WhiteoutActionPayload packet,CallbackInfo ci){EditorHistory.finish(player,packet);}
}
