package dev.whiteout.power;

/** Identical deterministic wind field on server and client for a given world tick,
 * zone and server-synced strength. The server is authoritative for movement;
 * the client samples the same field only for drifting ground-snow particles.
 * Direction is the direction TOWARDS which air moves. */
public final class SharedWindField {
 private SharedWindField(){}
 public static double clamp(double v){return Double.isFinite(v)?Math.max(0,Math.min(1,v)):0;}
 public static double gust(long worldTime){return GroundSnowPolicy.gustAt(worldTime);}
 public static double directionX(long worldTime){return Math.cos(WindMovementPolicy.angle(worldTime,0));}
 public static double directionZ(long worldTime){return Math.sin(WindMovementPolicy.angle(worldTime,0));}
 /** A base breeze exists during clear weather and grows in heavy weather.
  * A base strength of zero really is calm even during a blizzard. */
 public static double strength(long worldTime,double baseStrength,boolean blizzard,double snowfall){
  double weather=blizzard?3:1+0.35*clamp(snowfall);
  return clamp(clamp(baseStrength)*weather)*gust(worldTime);
 }
}
