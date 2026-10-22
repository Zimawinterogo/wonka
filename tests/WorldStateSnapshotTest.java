package dev.whiteout.power;

import java.util.*;
import dev.whiteout.Zone;
import dev.whiteout.ZoneSelection;
import static dev.whiteout.power.WhiteoutWorldState.*;

/** Actual adapter/legacy policy tests. These are not live Minecraft world tests. */
public final class WorldStateSnapshotTest {
 static int checks;
 static void ok(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static <T> Reading<T> live(T value){return new Reading<>(Availability.AVAILABLE,10,Optional.of(value));}
 static <T> Reading<T> missing(Availability state){return new Reading<>(state,10,Optional.empty());}
 static GridManager.Config config(){
  GridManager.Config c=new GridManager.Config();c.panelBound=true;c.generatorBound=true;
  c.panelObserved=true;c.generatorObserved=true;c.panelFuse=true;c.generatorRunning=true;c.generatorFueled=true;
  return c;
 }
 static Reading<PowerNetworkStateView> snapshot(GridManager.Config c,Reading<GeneratorStateView> g,Reading<FuseStateView> f){
  return WhiteoutWorldState.networkSnapshot("a",c,g,f,100,10);
 }
 public static void main(String[] args){
  var c=config();var g=live(new GeneratorStateView(3,36000,true));var f=live(new FuseStateView(true,false,true,.2f,true));
  var first=snapshot(c,g,f);
  ok(first.value().orElseThrow().powered(),"actual live generator and fuse");
  ok(first.value().get().generator().value().get().fuelTicks()==36000,"exact fuel units");
  ok(!snapshot(c,live(new GeneratorStateView(3,0,true)),f).value().get().powered(),"empty running generator cannot supply");
  ok(!snapshot(c,g,live(new FuseStateView(true,true,true,.2f,false))).value().get().powered(),"burnt fuse outage");
  ok(!snapshot(c,missing(Availability.MISSING),f).value().get().powered(),"removed device overrides stale observed facts");
  var unloaded=snapshot(c,missing(Availability.UNLOADED),f);
  ok(unloaded.availability()==Availability.CACHED&&unloaded.value().get().powered(),"legacy unloaded semantics retained, labelled cached");
  ok(unloaded.value().get().generator().value().isEmpty(),"never invent fuel for unloaded generator");
  c.generatorObserved=false;
  ok(!snapshot(c,missing(Availability.UNLOADED),f).value().get().powered(),"never-observed unloaded device fails closed");
  c=config();c.generatorStopPending=true;
  ok(!snapshot(c,g,f).value().get().powered(),"pending shutdown overrides live device");
  c.generatorStopPending=false;c.burntPending=true;
  ok(!snapshot(c,g,f).value().get().powered(),"pending fuse failure");
  c.burntPending=false;c.flickerEnd=101;
  ok(!snapshot(c,g,f).value().get().powered(),"flicker outage");
  c.flickerEnd=100;ok(snapshot(c,g,f).value().get().powered(),"flicker expiry boundary");
  c.configurationError="invalid";ok(!snapshot(c,g,f).value().get().powered(),"invalid config fails closed");
  c=config();var fixture=new GridManager.Fixture();fixture.dimension="minecraft:overworld";fixture.x=7;fixture.original="minecraft:lantern";c.lamps.add(fixture);
  var saved=snapshot(c,g,f).value().get();fixture.x=99;c.lamps.clear();
  ok(saved.fixtures().getFirst().address().x()==7,"snapshot detached from mutable owner");
  try{saved.fixtures().clear();throw new AssertionError("mutable list");}catch(UnsupportedOperationException expected){checks++;}
  var a=config();var b=config();var cache=new WorldStateCache<String,Reading<PowerNetworkStateView>>(128,1);
  cache.get("a",10,()->snapshot(a,g,f));cache.get("b",10,()->snapshot(b,g,f));
  a.burntPending=true;b.generatorStopPending=true;cache.clear();
  ok(!cache.get("a",10,()->snapshot(a,g,f)).value().get().powered(),"first independent outage");
  ok(!cache.get("b",10,()->snapshot(b,g,f)).value().get().powered(),"second independent outage");
  a.burntPending=false;cache.clear();
  ok(cache.get("a",10,()->snapshot(a,g,f)).value().get().powered()&&!cache.get("b",10,()->snapshot(b,g,f)).value().get().powered(),"restoring one circuit does not restore another");
  String before=dev.whiteout.Store.GSON.toJson(a);
  for(int i=0;i<100;i++)snapshot(a,g,f);
  ok(before.equals(dev.whiteout.Store.GSON.toJson(a)),"adapter does not mutate persisted Config");
  var reload=dev.whiteout.Store.GSON.fromJson(before,GridManager.Config.class);
  ok(snapshot(reload,missing(Availability.UNLOADED),missing(Availability.UNLOADED)).value().get().powered(),"persisted legacy facts across DTO reload");
  Zone low=new Zone();low.id="low";low.radius=100;low.cold=.4f;
  Zone high=new Zone();high.id="high";high.radius=100;high.priority=2;high.blizzard=true;
  List<Zone> zones=List.of(low,high);
  Zone selected=ZoneSelection.chooseMatching(zones,z->z.enabled&&!z.shelter,0,0,0,"minecraft:overworld");
  var z=WhiteoutWorldState.zoneSnapshot(selected,0,0,0,"minecraft:overworld");
  ok(z.id().equals("high")&&z.blizzard(),"real zone priority");
  high.enabled=false;
  selected=ZoneSelection.chooseMatching(zones,q->q.enabled&&!q.shelter,0,0,0,"minecraft:overworld");
  ok(selected==low&&z.id().equals("high"),"zone replacement and immutable previous snapshot");
  ok(ZoneSelection.choose(zones,0,0,0,"minecraft:the_nether")==null,"dimension isolation");
  Reading<GeneratorStateView> absent=missing(Availability.UNAVAILABLE);
  ok(absent.value().isEmpty(),"unavailable is not zero fuel");
  System.out.println("WorldStateSnapshotTest PASS: "+checks);
 }
}
