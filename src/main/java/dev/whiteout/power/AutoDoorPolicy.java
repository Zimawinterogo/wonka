package dev.whiteout.power;

/** Pure rules shared by the automatic door closer and its regression tests. */
public final class AutoDoorPolicy {
 private AutoDoorPolicy(){}
 public static final int DELAY_TICKS = 210; // 10.5 seconds at 20 TPS
 public static boolean shouldClose(boolean open, boolean electricManaged, boolean redstonePowered,
                                   boolean occupied, boolean loaded, boolean expired){
  return open && !electricManaged && !redstonePowered && !occupied && loaded && expired;
 }
}
