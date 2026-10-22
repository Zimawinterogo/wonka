package dev.whiteout.power.backpack;

/** Shared timing and validation; visual pose never changes inventory or collision. */
public final class BackpackPosePolicy {
 public static final float DROP=10f/16f;
 public static final int LEASE_TICKS=60;
 private BackpackPosePolicy(){}
 public static boolean allowed(boolean alive,boolean spectator,boolean creative,boolean grounded,
   boolean riding,boolean swimming,boolean flying,boolean sleeping,boolean ownInventory){
  return alive&&!spectator&&!creative&&grounded&&!riding&&!swimming&&!flying&&!sleeping&&ownInventory;
 }
 public static boolean validLease(long now,long touched,boolean sameWorld,double distanceSquared){
  return now>=touched&&now-touched<=LEASE_TICKS&&sameWorld&&distanceSquared<=.04;
 }
 public static float blend(float from,float target,long elapsed){
  return from+(target-from)*BackpackOpening.phase(elapsed,0,350);
 }
}
