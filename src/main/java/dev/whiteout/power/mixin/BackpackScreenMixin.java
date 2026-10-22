package dev.whiteout.power.mixin;

import dev.whiteout.power.backpack.BackpackClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class BackpackScreenMixin {
 @ModifyVariable(method="setScreen",at=@At("HEAD"),argsOnly=true)
 private Screen whiteout$backpack(Screen screen){return BackpackClient.replace(screen);}
}
