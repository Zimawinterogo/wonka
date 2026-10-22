import dev.whiteout.Zone;
import dev.whiteout.ZoneSelection;
import dev.whiteout.power.EnvironmentClient;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public final class AuditRegressionTest {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static Field field(String name)throws Exception{
  Field f=EnvironmentClient.class.getDeclaredField(name);f.setAccessible(true);return f;
 }
 public static void main(String[] args)throws Exception{
  EnvironmentClient.setOldLocal("shelter",true);
  ((Map<String,Integer>)field("OLD_RATES").get(null)).put("shelter",42);
  field("cold").setBoolean(null,false);
  field("creakDelay").setInt(null,99);
  var reset=EnvironmentClient.class.getDeclaredMethod("clearTransient");reset.setAccessible(true);reset.invoke(null);
  check(EnvironmentClient.isOld("shelter"),"dimension change must retain old shelters");
  check(((Map<?,?>)field("OLD_RATES").get(null)).get("shelter").equals(42),"dimension change must retain sound rates");
  check(!field("cold").getBoolean(null),"dimension change must retain server flags");
  check(field("creakDelay").getInt(null)==0,"dimension change must reset transient timers");
  EnvironmentClient.clear();
  check(!EnvironmentClient.isOld("shelter")&&((Map<?,?>)field("OLD_RATES").get(null)).isEmpty(),"disconnect clears server state");
  check(field("cold").getBoolean(null),"disconnect restores default flags");
  Zone outer=new Zone();outer.id="outer";outer.enabled=true;outer.dimension="minecraft:overworld";outer.radius=50;outer.x=outer.y=outer.z=0;
  Zone inner=new Zone();inner.id="inner";inner.enabled=true;inner.dimension=outer.dimension;inner.radius=10;inner.x=inner.y=inner.z=0;
  for(var zones:List.of(List.of(outer,inner),List.of(inner,outer)))
   check(ZoneSelection.chooseMatching(zones,z->z.enabled&&!z.shelter,0,0,0,outer.dimension)==inner,"equal-priority nested zones must select smaller zone regardless of order");
  outer.priority=10;
  check(ZoneSelection.chooseMatching(List.of(inner,outer),z->z.enabled,0,0,0,outer.dimension)==outer,"explicit priority must win");
  System.out.println("PASS AuditRegressionTest: dimension/disconnect state and nested zone selection");
 }
}
