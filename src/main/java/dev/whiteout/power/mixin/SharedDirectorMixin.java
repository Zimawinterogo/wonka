package dev.whiteout.power.mixin;
import dev.whiteout.client.director.DirectorPlayer;
import dev.whiteout.power.SharedSceneClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=DirectorPlayer.class,remap=false)
public abstract class SharedDirectorMixin{
 @Inject(method="stop",at=@At("HEAD"),cancellable=true)
 private static void whiteout$vote(boolean requested,CallbackInfo ci){if(requested&&SharedSceneClient.skip())ci.cancel();}
 @Inject(target=@Desc(value="tick",args={MinecraftClient.class}),at=@At("HEAD"),cancellable=true)
 private static void whiteout$pause(MinecraftClient client,CallbackInfo ci){if(client.world!=null&&SharedSceneClient.paused())ci.cancel();}
}
