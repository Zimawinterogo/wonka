package dev.whiteout.power;
/** Pure visual policy, also callable by the future common wind adapter. */
public final class GroundSnowPolicy {
 private GroundSnowPolicy(){}
 private static double clamp(double v){return Double.isFinite(v)?Math.max(0,Math.min(1,v)):0;}
 private static double smooth(double v){v=clamp(v);return v*v*v*(v*(v*6-15)+10);}
 private static long hash(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
 private static double unit(long v){return (hash(v)>>>11)*0x1.0p-53;}
 /** Smooth deterministic value noise with jittered knots, no allocations or reset clock.
  * Knot gaps vary from 300 to 900 ticks; every knot is >= .55, so joining at time 0
  * never introduces a guaranteed calm. Actual calm comes from wind strength zero. */
 public static double gustAt(long ticks){
  long cell=Math.floorDiv(ticks,600L);
  double local=Math.floorMod(ticks,600L);
  double knot=150+300*unit(cell);
  long left=local<knot?cell-1:cell;
  double start=(left==cell?0:-600)+150+300*unit(left);
  double end=(left==cell?600:0)+150+300*unit(left+1);
  double a=.55+.45*unit(left^0x632be59bd9b4e019L);
  double b=.55+.45*unit((left+1)^0x632be59bd9b4e019L);
  return a+(b-a)*smooth((local-start)/(end-start));
 }
 public static double strength(double wind,double storm,double gust){return clamp(clamp(wind)*(1+2*clamp(storm)))*clamp(gust);}
 /** Slower, longer-lived low wind wisps remain readable without larger sprites. */
 public static double speed(double strength){return 8*clamp(strength);}
 public static int lifetime(double strength){return (int)Math.round(48-14*clamp(strength));}
 public static double intensity(double snow,double wind,double limit){return clamp(wind)*(.9+.1*clamp(snow))*clamp(limit);}
 public static double groupChance(double wind){double w=clamp(wind);return .25*w*w;}
}
