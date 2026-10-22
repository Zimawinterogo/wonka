package dev.whiteout.power.mixin;

import dev.whiteout.power.WhiteoutTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Styling is limited to mod-owned menus. Vanilla and other mods stay untouched. */
@Mixin(PressableWidget.class)
public abstract class WhiteoutButtonMixin {
 @Inject(method="renderWidget",at=@At("HEAD"),cancellable=true)
 private void whiteout$winterButtons(DrawContext context,int mouseX,int mouseY,float delta,CallbackInfo ci){
  if(!((Object)this instanceof ButtonWidget button))return;
  var screen=MinecraftClient.getInstance().currentScreen;
  if(screen==null||!screen.getClass().getName().startsWith("dev.whiteout."))return;
  WhiteoutTheme.button(context,button);
  ci.cancel();
 }
}
