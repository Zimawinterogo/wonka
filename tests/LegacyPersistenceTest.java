import dev.whiteout.power.*;
import dev.whiteout.Zone;
import com.google.gson.Gson;
import java.util.*;
public final class LegacyPersistenceTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  Gson gson=new Gson();
  var folders=gson.fromJson("{\"folders\":[\"Лес\"],\"assigned\":{\"zone-a\":\"Лес\"}}",ZoneFolders.Data.class);
  check(folders.order!=null&&folders.order.isEmpty(),"old folder format defaults");
  check(folders.assigned.get("zone-a").equals("Лес"),"assignment retained");
  folders.order.put("Лес",new ArrayList<>(List.of("zone-a","zone-a","deleted")));
  ZoneFolderOrder.reconcile(folders.order.get("Лес"),List.of("zone-a","zone-b"));
  var reloaded=gson.fromJson(gson.toJson(folders),ZoneFolders.Data.class);
  check(reloaded.order.get("Лес").equals(List.of("zone-a","zone-b")),"unique order after reload");
  check(reloaded.assigned.equals(folders.assigned),"folder order never changes assignment");
  var c=gson.fromJson("{\"generatorX\":123,\"generatorY\":70,\"generatorZ\":-54,\"generatorBound\":true,\"panelBound\":true,\"lamps\":[]}",GridManager.Config.class);
  check(c.generatorX==123&&c.generatorY==70&&c.generatorZ==-54&&c.generatorBound,"legacy coordinates");
  check(c.dimension.equals("minecraft:overworld")&&c.pendingRestores!=null&&!c.generatorObserved,"new defaults: unknown not assumed powered");
  c.generatorObserved=c.generatorRunning=c.generatorFueled=c.panelObserved=c.panelFuse=true;
  var c2=gson.fromJson(gson.toJson(c),GridManager.Config.class);
  check(GridStatePolicy.supplied(c2.panelBound,c2.generatorBound,c2.panelObserved,c2.generatorObserved,c2.panelFuse,c2.panelBurnt,c2.generatorRunning,c2.generatorFueled,c2.burntPending,c2.generatorStopPending),"cached grid survives restart");
  var networks=gson.fromJson("{\"grids\":{}}",GridManager.NetworkStore.class);
  check(networks.deleting!=null&&networks.deleting.isEmpty(),"old networks deletion journal default");
  var atmosphere=gson.fromJson("{\"oldShelters\":[\"zone-a\"],\"oldSoundIntervals\":{\"zone-a\":45}}",AtmosphereConfig.class);atmosphere.normalize();
  check(atmosphere.mutedOldSoundZones!=null&&atmosphere.mutedOldSoundZones.isEmpty(),"old sound settings default");
  atmosphere.mutedOldSoundZones.add("zone-a");
  var atmosphere2=gson.fromJson(gson.toJson(atmosphere),AtmosphereConfig.class);atmosphere2.normalize();
  check(atmosphere2.mutedOldSoundZones.contains("zone-a")&&atmosphere2.oldSoundIntervals.get("zone-a")==45,"sound edits persist");
  var zone=gson.fromJson("{\"id\":\"zone-a\",\"name\":\"Дом\",\"x\":10,\"radius\":25}",Zone.class);
  var z2=gson.fromJson(gson.toJson(zone),Zone.class);
  check(z2.id.equals(zone.id)&&z2.name.equals(zone.name)&&z2.x==10&&z2.radius==25,"zone identity and geometry round trip");
  System.out.println("PASS LegacyPersistenceTest: real Gson DTOs for folders, zones, networks, atmosphere; not a Minecraft world load");
 }
}
