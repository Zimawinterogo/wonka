package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.Zone;
import dev.whiteout.client.Bridge;
import dev.whiteout.client.WeatherState;
import dev.whiteout.client.WhiteoutClient;
import net.minecraft.client.MinecraftClient;
import java.util.*;

/** Eliminate loss of fog/snow where contiguous equivalent blizzard zones meet.
 * Unlike altering Zone.weight, this affects no objectives, triggers or shelters. */
public final class ZoneSeamRepair {
 private ZoneSeamRepair(){}
 private static boolean same(Zone a,Zone b){
  // Whiteout's `blizzard` flag is the extreme-weather setting. Adjacent
  // extreme zones may have different falloff/metadata and still must hand off
  // seamlessly; treating metadata differences as clear weather was a bug.
  return a!=null&&b!=null&&!a.shelter&&!b.shelter&&a.blizzard&&b.blizzard;
 }
 private static boolean at(Zone z,double x,double y,double zz,String dimension){
  return z!=null&&z.enabled&&z.blizzard&&dimension.equals(z.dimension)&&z.contains(x,y,zz,dimension)&&!z.excluded(x,y,zz);
 }
 /** Called once, immediately after the original WeatherState tick. */
 public static void afterTick(MinecraftClient mc){
  if(mc==null||mc.player==null||mc.world==null||Bridge.snapshot==null||Bridge.snapshot.zones==null)return;
  List<Zone> zones=Bridge.snapshot.zones;
  double x=mc.player.getX(),y=mc.player.getY(),z=mc.player.getZ();
  String dim=mc.world.getRegistryKey().getValue().toString();
  Zone first=null,second=null;
  List<Zone> nearby=new ArrayList<>();
  // Sampling BOTH sides of the player's position finds two touching zones,
  // even when their cuboid bounds don't overlap in world coordinates.
  for(Zone zone:zones){
   if(zone==null||!zone.enabled||zone.shelter||!zone.blizzard)continue;
   boolean near=at(zone,x,y,z,dim)||at(zone,x+1,y,z,dim)||at(zone,x-1,y,z,dim)||
    at(zone,x,y,z+1,dim)||at(zone,x,y,z-1,dim)||
    at(zone,x+2,y,z,dim)||at(zone,x-2,y,z,dim)||at(zone,x,y,z+2,dim)||at(zone,x,y,z-2,dim);
   if(!near)continue;
   nearby.add(zone);
  }
  outer: for(int i=0;i<nearby.size();i++)for(int j=i+1;j<nearby.size();j++){
   if(same(nearby.get(i),nearby.get(j))){first=nearby.get(i);second=nearby.get(j);break outer;}
  }
  if(second==null||(!at(first,x,y,z,dim)&&!at(second,x,y,z,dim)))return;
  // For equal neighbouring storms the visual target must be independent of
  // the fading edge weights. The original tick still handles all real changes.
  float targetFog=Math.max(Math.max(first.fog,second.fog),.94f),targetSnow=Math.max(Math.max(first.snow,second.snow),1f);
  float targetDistance=Math.max(12f,Math.min(256f,Math.max(first.snowDistance,second.snowDistance)));
  float targetVisibility=Math.max(2f,Math.min(9f,Math.min(first.visibility,second.visibility)));
  WhiteoutClient.fogAmount=Math.max(WhiteoutClient.fogAmount,targetFog);
  WhiteoutClient.viewDistance=Math.min(WhiteoutClient.viewDistance,targetVisibility);
  WeatherState.storm=Math.max(WeatherState.storm,1f);
  WeatherState.snow=Math.max(WeatherState.snow,targetSnow);
  WeatherState.snowDistance=Math.max(WeatherState.snowDistance,targetDistance);
  WeatherState.strength=Math.max(WeatherState.strength,1f);
  if(WhiteoutClient.active==null)WhiteoutClient.active=at(first,x,y,z,dim)?first:second;
 }
}
