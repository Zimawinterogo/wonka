package dev.whiteout.power.mixin;

import dev.whiteout.power.GlassFrostOverlay;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class GlassWipeKeyboardMixin {
 @Inject(method="onKey",at=@At("HEAD"),cancellable=true)
 private void whiteout$wipePress(long window,int key,int scanCode,int action,int modifiers,CallbackInfo ci){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(window==mc.getWindow().getHandle()&&mc.mouse.isCursorLocked()
    &&mc.options.useKey.matchesKey(key,scanCode)){
   if(action==GLFW.GLFW_RELEASE)GlassFrostOverlay.release();
   else if(action==GLFW.GLFW_PRESS&&GlassFrostOverlay.press())ci.cancel();
  }
 }
}
