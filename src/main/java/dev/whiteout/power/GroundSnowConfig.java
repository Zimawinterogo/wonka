package dev.whiteout.power;

/** Compatible with both 3.12.7 and the first ground-snow patch; no difficulty binding. */
public final class GroundSnowConfig {
 public boolean enabled=true;
 public int intensity=65;
 /** Autonomous fallback only. A connected authoritative provider owns physical wind. */
 public int wind=35;
 public void normalize(){intensity=Math.max(0,Math.min(100,intensity));wind=Math.max(0,Math.min(100,wind));}
}
