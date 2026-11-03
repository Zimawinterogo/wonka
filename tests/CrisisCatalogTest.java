import com.google.gson.Gson;
import dev.whiteout.power.*;
import java.util.*;

public final class CrisisCatalogTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void reject(Runnable code){try{code.run();throw new AssertionError("invalid catalog accepted");}catch(IllegalArgumentException|NullPointerException expected){checks++;}}
 public static void main(String[] args){
  Map<String,StoryEventCatalog.Event> events=new HashMap<>();List<CrisisScenes.Stage> stages=new ArrayList<>();
  var area=new StoryEventCatalog.Area("minecraft:overworld",0,70,0,3);
  for(var phase:CrisisScenes.Phase.values()){
   String key="qa:"+phase.name().toLowerCase(Locale.ROOT);stages.add(new CrisisScenes.Stage(phase,key,20));
   events.put(key,new StoryEventCatalog.Event(key,true,area,List.of(),List.of(),List.of(),null,true));
  }
  events.put("qa:reset",new StoryEventCatalog.Event("qa:reset",false,area,List.of(),List.of(),List.of(),null,true));
  var scene=new CrisisScenes.Scene("qa","minecraft:overworld",10,100,stages,"qa:reset");
  var catalog=new CrisisScenes.Catalog(1,List.of(scene));
  check(CrisisScenes.validateCatalog(catalog,events::get).keySet().equals(Set.of("scene:qa")),"server-owned phase catalog accepted");
  Gson json=new Gson();check(CrisisScenes.validateCatalog(json.fromJson(json.toJson(catalog),CrisisScenes.Catalog.class),events::get).size()==1,"scene JSON roundtrip");
  reject(()->CrisisScenes.validateCatalog(new CrisisScenes.Catalog(2,List.of(scene)),events::get));
  reject(()->CrisisScenes.validateCatalog(new CrisisScenes.Catalog(1,List.of(scene,scene)),events::get));
  reject(()->CrisisScenes.validateCatalog(catalog,key->null));
  var first=events.get(stages.getFirst().event());
  events.put(first.key(),new StoryEventCatalog.Event(first.key(),true,area,List.of(),List.of()));
  reject(()->CrisisScenes.validateCatalog(catalog,events::get));
  events.put(first.key(),new StoryEventCatalog.Event(first.key(),false,area,List.of(),List.of(),List.of(),null,true));
  reject(()->CrisisScenes.validateCatalog(catalog,events::get));
  events.put(first.key(),new StoryEventCatalog.Event(first.key(),true,area,List.of(),List.of(),List.of(),null,true,List.of(new StorySceneActions.Change("qa",StorySceneActions.Operation.START))));
  reject(()->CrisisScenes.validateCatalog(catalog,events::get));events.put(first.key(),first);
  var wrongOrder=new ArrayList<>(stages);Collections.swap(wrongOrder,0,1);
  reject(()->new CrisisScenes.Scene("qa","minecraft:overworld",10,100,wrongOrder).validate());
  var data=new dev.whiteout.Store.Data();data.storyVariables.put(scene.key("phase"),dev.whiteout.StoryRules.Value.of(1));data.completedEvents.add(stages.get(1).event());
  check(!CrisisScenes.consistent(data,scene,1),"missing previous phase detected");data.completedEvents.add(stages.getFirst().event());
  check(CrisisScenes.consistent(data,scene,1),"cursor matches committed phase history");
  System.out.println("PASS CrisisCatalogTest: "+checks+" definition/ownership/recursion/recovery checks");
 }
}
