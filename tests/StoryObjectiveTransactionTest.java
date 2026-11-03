import com.google.gson.Gson;
import dev.whiteout.*;
import dev.whiteout.power.*;
import java.nio.file.*;
import java.util.*;

public final class StoryObjectiveTransactionTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static final Gson JSON=new Gson();
 static ObjectiveRules.Objective definition(String id){
  var o=new ObjectiveRules.Objective();o.id=id;o.title="Title "+id;
  o.onStart=List.of(JSON.fromJson("{\"variable\":\"rewards\",\"operation\":\"ADD\",\"value\":{\"type\":\"INT\",\"integer\":1}}",StoryRules.Action.class));return o;
 }
 static StoryObjectiveActions.Change change(String id,ObjectiveRules.Event event,ObjectiveRules.Objective definition){return new StoryObjectiveActions.Change(id,event,"Updated",definition);}
 static StoryEventCatalog.Event event(String key,StoryObjectiveActions.Change... changes){
  return new StoryEventCatalog.Event(key,true,new StoryEventCatalog.Area("minecraft:overworld",0,70,0,3),List.of(),List.of(),List.of(changes));
 }
 public static void main(String[] args)throws Exception{
  Path root=Path.of("build","story-objective-test");Files.createDirectories(root);Path dir=Files.createTempDirectory(root,"save-");Path path=dir.resolve("zones.json"),tmp=dir.resolve("zones.json.tmp");
  try{
   Store store=StoryAuthorityStoreTest.open(path);store.data.storyVariables.put("rewards",StoryRules.Value.of(0));store.data.completedEvents.add("other_scene");store.save();
   var start=event("objective:start",change("first",ObjectiveRules.Event.START,definition("first")));
   var old=StoryObjectiveActions.State.capture(store.data);String disk=Files.readString(path);Files.createDirectory(tmp);
   try{StoryCommitTransaction.apply(store,start);throw new AssertionError("failed save accepted");}catch(java.io.IOException expected){}
   check(store.data.objectives==old.objectives&&store.data.storyVariables==old.variables&&store.data.completedEvents==old.completed,"all references restored");
   check(store.data.activeObjective.equals(old.active)&&store.data.objectiveEventSerial==old.serial,"pointer and serial restored");
   check(Files.readString(path).equals(disk)&&!store.data.completedEvents.contains(start.key()),"disk and Once unchanged");Files.delete(tmp);
   check(StoryCommitTransaction.apply(store,start)==StoryCommitTransaction.Result.COMMITTED,"start saved");
   check(store.data.activeObjective.equals("first")&&store.data.objectives.get("first").status==ObjectiveRules.Status.ACTIVE,"existing objective model used");
   check(store.data.storyVariables.get("rewards").integer==1&&store.data.objectiveEventType.equals("START"),"start hooks and existing HUD event");
   store=StoryAuthorityStoreTest.open(path);
   check(StoryCommitTransaction.apply(store,start)==StoryCommitTransaction.Result.ALREADY_COMPLETED,"reopen keeps Once");
   StoryCommitTransaction.apply(store,event("start_again",change("first",ObjectiveRules.Event.START,null)));
   check(store.data.storyVariables.get("rewards").integer==1,"new request cannot replay active start rewards");
   old=StoryObjectiveActions.State.capture(store.data);disk=Files.readString(path);
   var invalid=event("bad_chain",change("first",ObjectiveRules.Event.COMPLETE,null),change("missing",ObjectiveRules.Event.START,null));
   try{StoryCommitTransaction.apply(store,invalid);throw new AssertionError("partial chain accepted");}catch(IllegalArgumentException expected){}
   check(store.data.objectives==old.objectives&&store.data.objectives.get("first").status==ObjectiveRules.Status.ACTIVE,"second action failure rolls back first");
   check(Files.readString(path).equals(disk)&&!store.data.completedEvents.contains("bad_chain"),"failed chain never saved");
   var next=event("advance",change("first",ObjectiveRules.Event.COMPLETE,null),change("second",ObjectiveRules.Event.START,definition("second")));
   StoryCommitTransaction.apply(store,next);
   check(store.data.objectives.get("first").status==ObjectiveRules.Status.COMPLETED&&store.data.activeObjective.equals("second"),"ordered complete/start chain");
   check(store.data.storyVariables.get("objective.first.completed").bool&&store.data.storyVariables.get("objective.second.active").bool,"base objective state variables retained");
   check(store.data.objectiveEventId.equals("second")&&store.data.objectiveEventType.equals("START"),"last visible transition displayed");
   StoryCommitTransaction.apply(store,event("update",change("second",ObjectiveRules.Event.UPDATE,null)));
   check(store.data.objectives.get("second").updateText.equals("Updated")&&store.data.objectiveEventText.equals("Updated"),"update text integrated");
   StoryCommitTransaction.apply(store,event("fail",change("second",ObjectiveRules.Event.FAIL,null)));
   check(store.data.activeObjective.isEmpty()&&store.data.objectives.get("second").status==ObjectiveRules.Status.FAILED,"fail clears pointer");
   var hidden=definition("hidden");hidden.hidden=true;long serial=store.data.objectiveEventSerial;
   StoryCommitTransaction.apply(store,event("hidden",change("hidden",ObjectiveRules.Event.START,hidden)));
   check(store.data.objectiveEventSerial==serial,"hidden transition does not emit HUD notification");
   Store loaded=StoryAuthorityStoreTest.open(path);
   check(loaded.data.activeObjective.equals("hidden")&&loaded.data.completedEvents.containsAll(Set.of("other_scene","advance","hidden")),"restart and other-scene preservation");
   check(loaded.data.storyVariables.get("rewards").integer==3,"hooks execute once across restart");
   var reset=new StoryObjectiveActions.Change("first",null,null,definition("first"),true);
   StoryCommitTransaction.apply(loaded,event("reset_first",reset));
   check(loaded.data.objectives.get("first").status==ObjectiveRules.Status.INACTIVE&&!loaded.data.storyVariables.get("objective.first.completed").bool,"authored reset clears only selected objective state");
   check(loaded.data.activeObjective.equals("hidden")&&loaded.data.storyVariables.get("rewards").integer==3,"reset preserves other active objective and does not run hooks");
   Store resetLoaded=StoryAuthorityStoreTest.open(path);check(resetLoaded.data.objectives.get("first").status==ObjectiveRules.Status.INACTIVE,"objective reset survives reopen");
   var bad=definition("active_definition");bad.status=ObjectiveRules.Status.ACTIVE;
   try{event("bad_definition",change(bad.id,ObjectiveRules.Event.START,bad)).validate();throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
   var decoded=JSON.fromJson(JSON.toJson(start),StoryEventCatalog.Event.class);decoded.validate();check(decoded.objectives().size()==1,"catalog JSON roundtrip");
  }finally{Files.deleteIfExists(tmp);Files.deleteIfExists(dir.resolve("zones.json.bak"));Files.deleteIfExists(path);Files.deleteIfExists(dir);}
  System.out.println("PASS StoryObjectiveTransactionTest: "+checks+" real Store/objective/rollback/reopen checks");
 }
}
