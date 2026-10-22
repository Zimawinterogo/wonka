package dev.whiteout.power.mixin;
import dev.whiteout.client.ZoneScreen;
import dev.whiteout.power.EditorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ZoneScreen.class,remap=false)
public abstract class ZoneEditorToolbarMixin {
 @Inject(method={"init","method_25426"},at=@At("TAIL"),remap=false,require=1)
 private void whiteout$toolbar(CallbackInfo ci){EditorClient.addToolbar((ZoneScreen)(Object)this);}
}
