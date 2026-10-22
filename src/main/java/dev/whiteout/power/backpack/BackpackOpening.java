package dev.whiteout.power.backpack;

/** Wall-clock cinematic; no changes to the player, inventory or server state. */
public final class BackpackOpening {
 public static final long DURATION=1100;
 private BackpackOpening(){}
 public static float phase(long elapsed,long start,long end){
  float t=Math.max(0,Math.min(1,(elapsed-start)/(float)(end-start)));
  return Math.max(0,Math.min(1,t*t*t*(t*(t*6-15)+10)));
 }
 public static boolean ready(long elapsed,boolean enabled){return !enabled||elapsed>=DURATION;}
 public static float lift(long elapsed){return phase(elapsed,120,650);}
 public static float lid(long elapsed){return phase(elapsed,480,950);}
 public static float hands(long elapsed){return phase(elapsed,100,340)*(1-phase(elapsed,850,DURATION));}
 public static float dip(long elapsed){return .22f*phase(elapsed,0,300);}
 /** Kept for the entire screen lifetime, including skipped/disabled opening. */
 public static float seatedDip(long elapsed,boolean animate){return animate?dip(elapsed):.22f;}
}
