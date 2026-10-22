package dev.whiteout.power;

/** Read-only integration point. No networking or movement changes are installed here.
 * Install once on the client thread. Sample must read the other branch's authoritative
 * wind at the supplied position, including its zone modifiers and gust, without IO.
 * False means real wind data is not ready; do not invent a second wind in that case. */
public final class GroundSnowWindSource {
 private GroundSnowWindSource(){}
 public interface Provider {
  boolean sample(String dimension,long worldTick,double x,double y,double z,Sample output);
 }
 /** Reused by the caller: providers must not retain this object. Strength is final,
  * normalized 0..1; gust is diagnostic, already included in strength, never reapplied. */
 public static final class Sample {
  public double x,z,strength,gust;
  public void set(double x,double z,double strength,double gust){this.x=x;this.z=z;this.strength=strength;this.gust=gust;}
  public void clear(){set(0,0,0,0);}
 }
 private static Provider provider;
 public static void install(Provider source){provider=source;}
 public static boolean connected(){return provider!=null;}
 public static boolean read(String dimension,long tick,double x,double y,double z,Sample out){
  out.clear();
  try{
   if(provider==null||!provider.sample(dimension,tick,x,y,z,out)){out.clear();return false;}
  }catch(RuntimeException unavailable){out.clear();return false;}
  double length=Math.hypot(out.x,out.z);
  if(!Double.isFinite(length)||!Double.isFinite(out.strength)||!Double.isFinite(out.gust)
     ||out.strength<0||out.strength>1||out.gust<0||out.gust>1||(length<1e-9&&out.strength>0)){
   out.clear();return false;
  }
  if(length>0){out.x/=length;out.z/=length;}
  return true;
 }
}
