package dev.whiteout.power;

/** Slow-down only when walking against the wind. No forced velocity/knockback. */
public final class WindMovementPolicy {
 private WindMovementPolicy(){}
 public static final double MAX_RESISTANCE = 0.15; // at most 15% headwind resistance in a blizzard
 /** Headwind resistance scales with the common physical wind strength (0..1). */
 public static double resistance(double moveX,double moveZ,double windX,double windZ,double strength){
  if(!Double.isFinite(strength)||strength<=0)return 0;
  double distance=Math.hypot(moveX,moveZ),windLength=Math.hypot(windX,windZ);
  if(distance<0.035||distance>10||windLength<.001)return 0;
  double against=-(moveX*windX+moveZ*windZ)/(distance*windLength);
  if(!Double.isFinite(against)||against<=0)return 0;
  return Math.min(MAX_RESISTANCE,MAX_RESISTANCE*against*SharedWindField.clamp(strength));
 }
 /** Compatibility for existing behavioural tests. */
 public static double resistance(double moveX,double moveZ,double windX,double windZ,boolean blizzard){
  return resistance(moveX,moveZ,windX,windZ,blizzard?1.0:0.0);
 }
 /** Long, smooth wind rotation; deterministic in a given world, no network packets. */
 public static double angle(long worldTime,long seed){
  return (Math.floorMod(seed,10_000L)/10_000d)*Math.PI*2 + worldTime/9000d;
 }
}
