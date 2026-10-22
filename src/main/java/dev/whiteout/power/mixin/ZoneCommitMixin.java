package dev.whiteout.power.mixin;
import dev.whiteout.Zone;
import dev.whiteout.client.Bridge;
import dev.whiteout.power.EditorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Bridge.class,remap=false)
public abstract class ZoneCommitMixin {
 @Inject(method="save",at=@At("RETURN"),remap=false)
 private static void whiteout$saveSent(Zone zone,CallbackInfo ci){EditorClient.commitSent();}
 @Inject(method="delete",at=@At("RETURN"),remap=false)
 private static void whiteout$deleteSent(String id,CallbackInfo ci){EditorClient.commitSent();}
}
