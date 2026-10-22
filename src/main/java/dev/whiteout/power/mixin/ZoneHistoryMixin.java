package dev.whiteout.power.mixin;
import dev.whiteout.net.WhiteoutNetwork;
import dev.whiteout.net.WhiteoutActionPayload;
import dev.whiteout.power.EditorHistory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=WhiteoutNetwork.class,remap=false)
public abstract class ZoneHistoryMixin {
 @Inject(method="handle",at=@At("HEAD"),remap=false)
 private static void whiteout$before(ServerPlayerEntity player,WhiteoutActionPayload packet,CallbackInfo ci){EditorHistory.begin(player,packet);}
 @Inject(method="handle",at=@At("RETURN"),remap=false)
 private static void whiteout$after(ServerPlayerEntity player,WhiteoutActionPayload packet,CallbackInfo ci){EditorHistory.finish(player,packet);}
}
