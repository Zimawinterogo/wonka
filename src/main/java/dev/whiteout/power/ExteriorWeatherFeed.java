package dev.whiteout.power;

import dev.whiteout.Zone;
import dev.whiteout.client.WeatherState;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;

/** Keeps the ORIGINAL snow renderer fed with the exterior storm while a player
 * looks through transparent glass from a roofed room. No weather is created on
 * the indoor side, since base SnowRenderer still tests outdoor roof heights. */
public final class ExteriorWeatherFeed {
 private ExteriorWeatherFeed(){}
 private static net.minecraft.world.World previousWorld;
 private static BlockPos previousPosition;
 private static long previousTick=Long.MIN_VALUE;
 private static float previousYaw=Float.NaN;
 private static float exteriorSnow,exteriorStorm;
 // Sample all angles, not just 8 cardinal/diagonal rays: a one-block window
 // can easily fall between those probes as the player moves sideways.
 private static final double[][] RAYS=new double[32][2];
 static {for(int i=0;i<RAYS.length;i++){
  double angle=2*Math.PI*i/RAYS.length;
  RAYS[i][0]=Math.cos(angle);RAYS[i][1]=Math.sin(angle);
 }}
 private static boolean transparentWeatherWindow(BlockState state){
  // Avoid a registry lookup for every air cell in the ray (the common case).
  if(state.isAir())return false;
  String id=Registries.BLOCK.getId(state.getBlock()).toString();
  return id.contains("glass")||id.endsWith(":frost_window")||id.endsWith(":rime_glass_pane");
 }
 // Mutate one block position rather than allocate a Vec3d and BlockPos for
 // every 0.35-block increment. Preserve the original probe spacing/coverage:
 // it is important for narrow glass panes at oblique angles.
 private static void sampleWindowRay(MinecraftClient mc, Vec3d pos, String dimension,
                                     double dx, double dz){
  boolean glass=false;
  BlockPos.Mutable at=new BlockPos.Mutable();
  int lastX=Integer.MIN_VALUE,lastZ=Integer.MIN_VALUE;
  int y=MathHelper.floor(pos.y);
  for(double dist=.35;dist<=20;dist+=.35){
   int x=MathHelper.floor(pos.x+dx*dist);
   int z=MathHelper.floor(pos.z+dz*dist);
   if(x==lastX&&z==lastZ)continue;
   lastX=x;lastZ=z;
   at.set(x,y,z);
   if(!mc.world.isInBuildLimit(at)||!mc.world.isChunkLoaded(at))break;
   BlockState state=mc.world.getBlockState(at);
   if(state.isAir()){
    if(!glass||!mc.world.isSkyVisible(at))continue;
   }else if(transparentWeatherWindow(state)){
    glass=true;
    continue;
   }else if(!state.getCollisionShape(mc.world,at).isEmpty())break;
   else if(!glass||!mc.world.isSkyVisible(at))continue;
   Zone weather=WindowExposure.weather(at,dimension);
   if(weather!=null&&weather.enabled){
    exteriorSnow=Math.max(exteriorSnow,weather.blizzard?1f:Math.max(0,weather.snow));
    exteriorStorm=Math.max(exteriorStorm,weather.blizzard?1f:0f);
   }
   break;
  }
 }
 private static void refresh(){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(mc.player==null||mc.world==null){exteriorSnow=0;exteriorStorm=0;previousWorld=null;previousYaw=Float.NaN;return;}
  BlockPos eye=mc.player.getBlockPos().up();
  long tick=mc.world.getTime();
  float yaw=mc.player.getYaw();
  // Recheck on camera rotation, not just every eight ticks. Otherwise a narrow
  // window can seem to stop showing snow when the player first turns toward it.
  if(previousWorld==mc.world&&eye.equals(previousPosition)
    &&Math.abs(MathHelper.wrapDegrees(yaw-previousYaw))<5f
    &&tick-previousTick>=0&&tick-previousTick<8)return;
  previousWorld=mc.world;previousPosition=eye;previousTick=tick;previousYaw=yaw;
  exteriorSnow=0;exteriorStorm=0;
  if(mc.world.isSkyVisible(eye))return; // normal outdoor renderer unchanged
  // Do not depend on a hand-authored Shelter zone. A physically roofed room
  // can still look out through windows at an active exterior weather zone.
  // Unlike the removed particle preview, this emits NO new snow particles.
  String dimension=mc.world.getRegistryKey().getValue().toString();
  Vec3d pos=mc.player.getEyePos();
  Vec3d look=mc.player.getRotationVec(1.0f);
  double horizontal=Math.hypot(look.x,look.z);
  if(horizontal>0.001){
   double x=look.x/horizontal,z=look.z/horizontal;
   // The radial probes are too widely spaced to always hit a 1-block window
   // at 10-20 blocks. Always scan the actual direction of the camera as well.
   sampleWindowRay(mc,pos,dimension,x,z);
   double angle=.055,co=Math.cos(angle),si=Math.sin(angle);
   sampleWindowRay(mc,pos,dimension,x*co-z*si,x*si+z*co);
   sampleWindowRay(mc,pos,dimension,x*co+z*si,-x*si+z*co);
  }
  // A full-strength blizzard cannot get any stronger: do not trace the
  // remaining 32 radial sightlines after it has already been found.
  if(exteriorSnow>=1f&&exteriorStorm>=1f)return;
  for(double[] dir:RAYS){
   sampleWindowRay(mc,pos,dimension,dir[0],dir[1]);
   if(exteriorSnow>=1f&&exteriorStorm>=1f)break;
  }
 }
 public static float snow(){refresh();return Math.max(WeatherState.snow,exteriorSnow);}
 public static float storm(){refresh();return Math.max(WeatherState.storm,exteriorStorm);}
}
