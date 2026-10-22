package dev.whiteout.power;
/** 10 seconds of continuous panel power per check; 20% independent failure chance. */
public final class UnstableFusePolicy {
 public static final int INTERVAL_TICKS=200;
 public static final float FAILURE_CHANCE=.20f;
 public static boolean shouldCheck(boolean installed,boolean burnt,boolean powered,long currentTime,long nextTime){
  return installed&&!burnt&&powered&&currentTime>=nextTime;
 }
 public static boolean blows(float random){return random>=0&&random<FAILURE_CHANCE;}
 public static boolean blows(float random,float chance){return random>=0&&random<Math.max(0,Math.min(1,chance));}
 private UnstableFusePolicy(){}
}
