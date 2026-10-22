import dev.whiteout.power.*;
import com.google.gson.Gson;

public final class GroundSnowWindTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void bounded(double v){check(Double.isFinite(v)&&v>=0&&v<=1,"finite normalized result: "+v);}
 public static void main(String[] args){
  for(double snow:new double[]{0,.001,.1,1}){
   check(GroundSnowPolicy.intensity(snow,0,.65)==0,"calm in every weather");
   double previous=0;
   for(double wind:new double[]{.2,.5,1}){
    double amount=GroundSnowPolicy.intensity(snow,wind,.65);
    bounded(amount);check(amount>previous,"stronger wind increases density");previous=amount;
   }
  }
  check(GroundSnowPolicy.intensity(0,.5,.65)>=.29,"clear moderate wind readable at normal setting");
  check(GroundSnowPolicy.intensity(1,1,.65)<=.65,"storm capped below white carpet probability");
  check(GroundSnowPolicy.strength(0,1,1)==0,"storm cannot override explicit calm");
  check(GroundSnowPolicy.intensity(0,.5,0)==0,"density zero");
  check(GroundSnowPolicy.intensity(0,.5,.4)<GroundSnowPolicy.intensity(0,.5,.65)
   &&GroundSnowPolicy.intensity(0,.5,.65)<GroundSnowPolicy.intensity(0,.5,.9),"future 40/65/90 presets stay external");
  double[] invalid={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-10,0,.2,1,10};
  for(double a:invalid)for(double b:invalid)for(double c:invalid){
   bounded(GroundSnowPolicy.strength(a,b,c));bounded(GroundSnowPolicy.intensity(a,b,c));
   check(Double.isFinite(GroundSnowPolicy.speed(a))&&GroundSnowPolicy.speed(a)>=0&&GroundSnowPolicy.speed(a)<=8,"speed bounded");
   check(GroundSnowPolicy.lifetime(a)>=34&&GroundSnowPolicy.lifetime(a)<=48,"lifetime below cleanup watchdog");
   bounded(GroundSnowPolicy.groupChance(a));
  }
  double sum=0,maxJump=0,min=1,max=0;int different=0;
  for(int t=-20000;t<200000;t++){
   double g=GroundSnowPolicy.gustAt(t);bounded(g);check(g>=.55,"no mandatory calm, including join/reset time");
   double jump=Math.abs(g-GroundSnowPolicy.gustAt(t-1));maxJump=Math.max(maxJump,jump);
   check(jump<.003,"smooth gust and knot transitions");
   double density=GroundSnowPolicy.intensity(0,GroundSnowPolicy.strength(.35,0,g),.65);
   double prior=GroundSnowPolicy.intensity(0,GroundSnowPolicy.strength(.35,0,GroundSnowPolicy.gustAt(t-1)),.65);
   check(Math.abs(density-prior)<.001,"smooth spawn intensity");
   check(g==GroundSnowPolicy.gustAt(t),"deterministic reset-independent sampling");
   if(Math.abs(g-GroundSnowPolicy.gustAt(t+960))>.001)different++;
   min=Math.min(min,g);max=Math.max(max,g);sum+=density;
  }
  check(different>200000,"not a repeated 48-second loop");
  check(max-min>.35,"actual stronger and weaker gusts");
  check(sum/220000>.13,"clear default average probability exceeds previous ~4 percent");
  for(long time:new long[]{Long.MIN_VALUE,Long.MAX_VALUE,-1,0,1})bounded(GroundSnowPolicy.gustAt(time));
  System.out.printf("Clear default mean probability %.4f, gust range %.4f..%.4f, max tick change %.6f%n",sum/220000,min,max,maxJump);

  Gson gson=new Gson();
  GroundSnowConfig legacy=gson.fromJson("{\"enabled\":false,\"intensity\":40}",GroundSnowConfig.class);legacy.normalize();
  check(!legacy.enabled&&legacy.intensity==40&&legacy.wind==35,"3.12.7 config without wind");
  GroundSnowConfig prior=gson.fromJson("{\"enabled\":true,\"intensity\":90,\"wind\":0}",GroundSnowConfig.class);prior.normalize();
  check(prior.enabled&&prior.intensity==90&&prior.wind==0,"previous patch explicit zero retained");
  GroundSnowConfig clamped=gson.fromJson("{\"intensity\":999,\"wind\":-42}",GroundSnowConfig.class);clamped.normalize();
  check(clamped.intensity==100&&clamped.wind==0,"config normalization");
  var fields=gson.toJsonTree(new GroundSnowConfig()).getAsJsonObject();
  check(fields.size()==3&&fields.has("enabled")&&fields.has("intensity")&&fields.has("wind"),"same persisted settings, no difficulty coupling");

  var sample=new GroundSnowWindSource.Sample();
  check(!GroundSnowWindSource.connected(),"no falsely advertised integration");
  GroundSnowWindSource.install((dimension,tick,x,y,z,out)->{
   check(dimension.equals("minecraft:overworld")&&tick==123&&x==4&&y==5&&z==6,"adapter query location and clock");
   out.set(-3,4,.5,.75);return true;
  });
  check(GroundSnowWindSource.read("minecraft:overworld",123,4,5,6,sample),"authoritative wind accepted");
  check(Math.abs(sample.x+.6)<1e-12&&Math.abs(sample.z-.8)<1e-12,"provider direction preserved and normalized");
  check(sample.strength==.5&&sample.gust==.75,"no second independent gust or weather boost");
  GroundSnowWindSource.install((d,t,x,y,z,out)->false);
  check(!GroundSnowWindSource.read("",0,0,0,0,sample)&&sample.strength==0&&sample.x==0,"unready provider clears stale sample, no fallback wind");
  for(double invalidValue:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1}){
   GroundSnowWindSource.install((d,t,x,y,z,out)->{out.set(1,0,invalidValue,.8);return true;});
   check(!GroundSnowWindSource.read("",0,0,0,0,sample)&&sample.strength==0,"reject invalid authoritative sample");
  }
  GroundSnowWindSource.install((d,t,x,y,z,out)->{out.set(0,0,.5,.8);return true;});
  check(!GroundSnowWindSource.read("",0,0,0,0,sample),"nonzero wind needs direction");
  GroundSnowWindSource.install((d,t,x,y,z,out)->{throw new IllegalStateException("not ready");});
  check(!GroundSnowWindSource.read("",0,0,0,0,sample),"provider failure cannot retain particles' stale wind");
  GroundSnowWindSource.install((d,t,x,y,z,out)->{out.set(0,0,0,.8);return true;});
  check(GroundSnowWindSource.read("",0,0,0,0,sample)&&sample.strength==0,"authoritative calm");
  GroundSnowWindSource.install(null);
  System.out.println("PASS GroundSnowWindTest: weather matrix, 220000-tick continuity, reset, finite bounds, legacy configs, shared-provider contract");
 }
}
