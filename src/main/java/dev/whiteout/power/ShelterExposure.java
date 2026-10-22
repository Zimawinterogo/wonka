package dev.whiteout.power;

import dev.whiteout.power.barricade.BarricadeApi;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Lightweight, chunk-safe sampling of openings between a sheltered player and outside. */
public final class ShelterExposure {
 private ShelterExposure(){}
 public record Sample(float opening,Vec3d direction) {}
 private static final double[][] RAYS={
  {1,0,0},{-1,0,0},{0,0,1},{0,0,-1},
  {0.71,0,0.71},{0.71,0,-0.71},{-0.71,0,0.71},{-0.71,0,-0.71},
  {1,.22,0},{-1,.22,0},{0,.22,1},{0,.22,-1},{0,1,0}
 };
 /** A solid pane/glass wall must STOP the ray; open doors and air do not. */
 private static boolean blocksWind(World world,BlockPos at,BlockState state){
  if(state.isAir()||state.getCollisionShape(world,at).isEmpty())return false;
  // Boards: the shared coverage threshold decides (gaps between boards are not treated as sealed wall).
  if(BarricadeApi.isBarricade(state))return BarricadeApi.isWindproof(world,at);
  if(state.getBlock() instanceof DoorBlock && state.contains(Properties.OPEN)&&state.get(Properties.OPEN))return false;
  return true;
 }
 public static Sample measure(World world,Vec3d eye){
  int exposed=0;Vec3d direction=Vec3d.ZERO;
  for(double[] v:RAYS){
   boolean blocked=false,open=false;
   for(double t=.75;t<=15;t+=.75){
    BlockPos p=BlockPos.ofFloored(eye.x+v[0]*t,eye.y+v[1]*t,eye.z+v[2]*t);
    if(!world.isInBuildLimit(p)||!world.isChunkLoaded(p)){blocked=true;break;}
    BlockState state=world.getBlockState(p);
    if(blocksWind(world,p,state)){blocked=true;break;}
    if(world.isSkyVisible(p)){open=true;break;}
   }
   if(!blocked&&open){exposed++;direction=direction.add(v[0],v[1],v[2]);}
  }
  return new Sample(Math.min(1f,exposed/4f),direction.lengthSquared()>0?direction.normalize():Vec3d.ZERO);
 }
 public static float effectiveProtection(float shelter,float opening){return shelter*(1f-.85f*Math.max(0f,Math.min(1f,opening)));}
 public static float indoorTemperature(float outside,float opening){return outside+(19f-outside)*(1f-.85f*Math.max(0f,Math.min(1f,opening)));}
}
