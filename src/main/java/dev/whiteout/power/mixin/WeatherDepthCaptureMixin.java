package dev.whiteout.power.mixin;

import dev.whiteout.power.WeatherDepth;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WeatherDepthCaptureMixin {
 // Both normal and Fabulous branches reach this after drawing entity layers,
 // before rendering translucent terrain (glass/water). No forced buffer flush.
 @Inject(method="render",at=@At(value="INVOKE",
  target="Lnet/minecraft/client/render/RenderLayer;getTranslucent()Lnet/minecraft/client/render/RenderLayer;"))
 private void whiteout$captureCompleteDepth(CallbackInfo ci){WeatherDepth.captureBeforeTranslucent();}
}
