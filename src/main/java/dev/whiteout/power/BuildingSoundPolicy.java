package dev.whiteout.power;

/** Configured minimum interval at full blizzard; weak storms are quieter and rarer. */
public final class BuildingSoundPolicy {
 private BuildingSoundPolicy(){}
 public static int delayTicks(int seconds,float strength,float random){
  double storm=Float.isFinite(strength)?Math.max(0,Math.min(1,strength)):0;
  double jitter=Float.isFinite(random)?Math.max(0,Math.min(1,random)):0;
  return (int)Math.ceil(Math.max(8,Math.min(300,seconds))*20*(2.5-1.5*storm)*(1+.5*jitter));
 }
}
