package dev.whiteout.power;

/** Emergency light: mains charges the battery, an outage activates its red backup light. */
public final class BackupLampPolicy {
 private BackupLampPolicy() {}
 /** Exact integer battery percentage, clamped for old/corrupt NBT. */
 public static int percent(int charge,int capacity){
  if(capacity<=0)return 0;
  long safe=Math.max(0L,Math.min((long)capacity,(long)charge));
  return (int)(safe*100L/capacity);
 }
 public static boolean lit(boolean linked,boolean mains,boolean enabled,int charge){
  return linked&&enabled&&!mains&&charge>0;
 }
 public static int tickCharge(boolean linked,boolean mains,boolean enabled,int charge,int capacity){
  if(!linked)return charge;
  if(mains)return Math.min(capacity,charge+1);
  return enabled?Math.max(0,charge-1):charge;
 }
}
