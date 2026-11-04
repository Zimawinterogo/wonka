package dev.whiteout.power.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.whiteout.client.Bridge;
import dev.whiteout.power.lobby.Balance;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.*;
@Mixin(value=Bridge.class,remap=false)
public abstract class LobbyBatteryMixin{
 @Unique private static int whiteout$remaining;
 @Unique private static Object whiteout$world;
 @WrapMethod(method="batteryTick")
 private static void whiteout$drain(boolean on,Operation<Void> original){
  var world=MinecraftClient.getInstance().world;if(world!=whiteout$world){whiteout$remaining=0;whiteout$world=world;}
  var b=Balance.get(Bridge.snapshot);if(!on||b==null){original.call(on);return;}
  whiteout$remaining+=20;while(whiteout$remaining>=b.torchMinutes()){original.call(true);whiteout$remaining-=b.torchMinutes();}
 }
}
