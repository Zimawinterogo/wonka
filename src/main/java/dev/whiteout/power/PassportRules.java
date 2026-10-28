package dev.whiteout.power;

import java.util.*;
import static dev.whiteout.power.PassportIssue.Severity.*;

/** No Minecraft dependencies, no mutation, and unknown observations never imply failure. */
public final class PassportRules {
 private PassportRules(){}
 public enum Observation { PRESENT,MISSING,UNLOADED,UNAVAILABLE }
 public static void network(MapPassport.Builder b,String id,boolean panel,boolean generator,int lamps,boolean invalid){
  if(invalid)b.issue(ERROR,"invalid_config",id,null);
  if(!panel||!generator)b.issue(ERROR,"unbound",id,null);
  if(lamps==0)b.issue(WARN,"no_lamps",id,null);
 }
 public static void device(MapPassport.Builder b,String id,MapPassport.Position p,Observation state,Boolean fuel,Boolean fuse){
  if(state==Observation.MISSING)b.issue(ERROR,"missing_block",id,p);
  if(state==Observation.UNLOADED)b.issue(INFO,"unloaded",id,p);
  if(state==Observation.UNAVAILABLE)b.issue(INFO,"unavailable",id,p);
  if(state==Observation.PRESENT){
   if(Boolean.FALSE.equals(fuel))b.issue(WARN,"no_fuel",id,p);
   if(Boolean.FALSE.equals(fuse))b.issue(WARN,"no_fuse",id,p);
  }
 }
 public static void reference(MapPassport.Builder b,String id,String target,Set<String> known,MapPassport.Position p){
  if(!known.contains(target))b.issue(ERROR,"missing_reference",id+" -> "+target,p);
 }
 public static boolean bounds(double... coordinates){
  if(coordinates.length!=6)return false;
  for(double v:coordinates)if(!Double.isFinite(v))return false;
  return coordinates[0]<coordinates[3]&&coordinates[1]<coordinates[4]&&coordinates[2]<coordinates[5];
 }
 public record Zone(String id,String dimension,boolean enabled,boolean shelter,int priority,double x1,double y1,double z1,double x2,double y2,double z2){}
 public static boolean overlaps(Zone a,Zone b){
  return a.enabled&&b.enabled&&a.shelter==b.shelter&&a.priority==b.priority&&Objects.equals(a.dimension,b.dimension)
   &&a.x1<b.x2&&a.x2>b.x1&&a.y1<b.y2&&a.y2>b.y1&&a.z1<b.z2&&a.z2>b.z1;
 }
 public static void dialogue(MapPassport.Builder b,String id,boolean builtin,boolean used){
  if(!builtin&&!used)b.issue(WARN,"unused_dialogue",id,null);
 }
}
