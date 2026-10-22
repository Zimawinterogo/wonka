package dev.whiteout.power.mixin;

import dev.whiteout.power.backpack.BackpackScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class BackpackHiddenSlotMixin {
 @Inject(method="isEnabled",at=@At("HEAD"),cancellable=true)
 private void whiteout$hiddenCrafting(CallbackInfoReturnable<Boolean> callback){
  if(MinecraftClient.getInstance().currentScreen instanceof BackpackScreen screen&&screen.hides((Slot)(Object)this))
   callback.setReturnValue(false);
 }
}
