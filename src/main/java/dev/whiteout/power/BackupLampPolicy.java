package dev.whiteout.power;

import net.minecraft.world.Difficulty;

/** Emergency light: mains charges the battery, an outage activates its red backup light. */
public final class BackupLampPolicy {
 private BackupLampPolicy() {}
 // Preserve the existing NBT and WorldState scale; duration is now difficulty-dependent.
 public static final int CAPACITY=12_000;
 public record Timing(int outageTicks,int chargeTicks) {
  public Timing {if(outageTicks<1||chargeTicks<1)throw new IllegalArgumentException("Positive durations required");}
 }
 private static final Timing EASY=new Timing(18_000,7_200),NORMAL=new Timing(8_400,18_000),HARD=new Timing(4_800,30_000);
 public static Timing timing(Difficulty difficulty){
  if(difficulty==Difficulty.EASY||difficulty==Difficulty.PEACEFUL)return EASY;
  if(difficulty==Difficulty.HARD)return HARD;
  return NORMAL;
 }
 public static int percent(int charge,int capacity){
  if(capacity<=0)return 0;
  long safe=Math.max(0L,Math.min((long)capacity,(long)charge));
  return (int)(safe*100L/capacity);
 }
 public static boolean lit(boolean linked,boolean mains,boolean enabled,int charge){return linked&&enabled&&!mains&&charge>0;}
 /** Advances normalized charge and carries fractional progress across ticks/saves. */
 public static int[] tick(boolean linked,boolean mains,boolean enabled,int charge,int remainder,Timing timing){
  charge=Math.max(0,Math.min(CAPACITY,charge));
  int duration=mains?timing.chargeTicks():timing.outageTicks();
  remainder=Math.floorMod(remainder,duration);
  if(!linked||(!mains&&!enabled))return new int[]{charge,remainder};
  if(mains&&charge==CAPACITY||!mains&&charge==0)return new int[]{charge,0};
  long numerator=(long)remainder+CAPACITY;
  int amount=(int)(numerator/duration),nextRemainder=(int)(numerator%duration);
  int next=mains?Math.min(CAPACITY,charge+amount):Math.max(0,charge-amount);
  // Clear only at the destination boundary. At the starting boundary fractional
  // progress must accumulate, or slow drain/recharge gets stuck forever.
  if(mains&&next==CAPACITY||!mains&&next==0)nextRemainder=0;
  return new int[]{next,nextRemainder};
 }
 /** Legacy tick primitive retained for existing standalone policy regressions. */
 public static int tickCharge(boolean linked,boolean mains,boolean enabled,int charge,int capacity){
  if(!linked)return charge;if(mains)return Math.min(capacity,charge+1);return enabled?Math.max(0,charge-1):charge;
 }
}
