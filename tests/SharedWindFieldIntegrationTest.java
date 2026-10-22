import dev.whiteout.power.GroundSnowPolicy;
import dev.whiteout.power.GroundSnowWindSource;
import dev.whiteout.power.SharedWindField;
import dev.whiteout.power.WindMovementPolicy;

/** Standalone shared weather-field tests; no Fabric/Minecraft classes needed. */
public final class SharedWindFieldIntegrationTest {
 static int checks;
 static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 static void near(double a,double b,double tol,String msg){ok(Math.abs(a-b)<tol,msg+" "+a+" vs "+b);}
 public static void main(String[] args){
  for(long t:new long[]{-5000,0,1,100,600,960,7800,80000,300000}){
   double dx=SharedWindField.directionX(t),dz=SharedWindField.directionZ(t);
   near(Math.hypot(dx,dz),1,1e-10,"normalized direction");
   near(dx,Math.cos(WindMovementPolicy.angle(t,0)),1e-12,"same direction X");
   near(dz,Math.sin(WindMovementPolicy.angle(t,0)),1e-12,"same direction Z");
   double gust=SharedWindField.gust(t);ok(gust>=.55&&gust<=1,"no mandatory startup calm");
   double calm=SharedWindField.strength(t,0,true,1);
   ok(calm==0,"zero physical wind defeats blizzard");
   ok(WindMovementPolicy.resistance(-dx,-dz,dx,dz,calm)==0,"zero headwind penalty");
   double clear=SharedWindField.strength(t,.35,false,0);
   double storm=SharedWindField.strength(t,.35,true,1);
   ok(clear>0&&storm>=clear,"clear drift and stronger blizzard");
   near(WindMovementPolicy.resistance(-dx,-dz,dx,dz,storm),WindMovementPolicy.MAX_RESISTANCE*storm,1e-10,"server strength applied once");
   ok(WindMovementPolicy.resistance(dx,dz,dx,dz,storm)==0,"tailwind unaffected");
   ok(WindMovementPolicy.resistance(-dx,-dz,dx,dz,clear)<WindMovementPolicy.resistance(-dx,-dz,dx,dz,storm),"clear less resistance");
   ok(GroundSnowPolicy.intensity(0,clear,.65)>0,"clear weather ground snow not disabled");
   ok(GroundSnowPolicy.intensity(1,calm,.65)==0,"no ground snow at calm");
   // The client-side provider transports only the shared result; its reader never reapplies a gust.
   GroundSnowWindSource.install((dim,tick,x,y,z,out)->{out.set(dx,dz,storm,gust);return true;});
   var sample=new GroundSnowWindSource.Sample();
   ok(GroundSnowWindSource.read("minecraft:overworld",t,1,2,3,sample),"provider ready");
   near(sample.strength,storm,1e-12,"same strength client and server");
   near(sample.x,dx,1e-12,"same direction client and server");
   near(sample.z,dz,1e-12,"same direction client and server");
  }
  GroundSnowWindSource.install((dim,tick,x,y,z,out)->false);
  var stale=new GroundSnowWindSource.Sample();stale.set(1,0,1,1);
  ok(!GroundSnowWindSource.read("minecraft:overworld",0,0,0,0,stale)&&stale.strength==0,"missing server settings do not enable local wind");
  GroundSnowWindSource.install(null);
  System.out.println("SharedWindFieldIntegrationTest PASS: "+checks+" assertions");
 }
}
