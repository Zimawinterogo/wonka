package dev.whiteout.power;

import dev.whiteout.Zone;
import dev.whiteout.ZoneSelection;
import dev.whiteout.client.Bridge;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

/** Bounded air connectivity, not zone membership alone, establishes an exterior window. */
public final class WindowExposure {
 private WindowExposure(){}
 public static Zone weather(BlockPos at,String dimension){
  if(Bridge.snapshot==null||Bridge.snapshot.zones==null)return null;
  return ZoneSelection.chooseMatching(Bridge.snapshot.zones,z->z.enabled&&!z.shelter,
   at.getX()+.5,at.getY()+.5,at.getZ()+.5,dimension);
 }
 public static BlockPos find(ClientWorld world,BlockPos glass){
  String dimension=world.getRegistryKey().getValue().toString();
  var found=WindowAirSearch.find((x,y,z)->{
   BlockPos p=glass.add(x,y,z);
   if(!world.isInBuildLimit(p)||!world.isChunkLoaded(p))return 0;
   var state=world.getBlockState(p);
   if(!state.isAir()&&!state.getCollisionShape(world,p).isEmpty())return 0;
   return world.isSkyVisible(p)?2:1;
  },point->weather(glass.add(point.x(),point.y(),point.z()),dimension)!=null);
  return found==null?null:glass.add(found.x(),found.y(),found.z());
 }
}
