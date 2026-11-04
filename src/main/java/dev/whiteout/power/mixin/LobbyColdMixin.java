package dev.whiteout.power.mixin;
import dev.whiteout.client.*;
import dev.whiteout.power.lobby.Balance;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=SurvivalState.class,remap=false)
public abstract class LobbyColdMixin{
 @ModifyArg(method="tick",at=@At(value="INVOKE",target="Ldev/whiteout/SurvivalMeter;tick(FFZZZ)V"),index=0)
 private static float whiteout$exposure(float exposure){
  var b=Balance.get(Bridge.snapshot);var p=MinecraftClient.getInstance().player;if(b==null||p==null||exposure<=0)return exposure;
  var z=WeatherState.weatherAt(p.getX(),p.getY(),p.getZ(),p.getWorld().getRegistryKey().getValue().toString());
  boolean storm=z!=null&&z.blizzard;return exposure*200f/(storm?1.4f*b.stormSeconds():b.snowSeconds());
 }
}
