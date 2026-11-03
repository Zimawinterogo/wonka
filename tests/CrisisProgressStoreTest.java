import dev.whiteout.*;
import dev.whiteout.power.*;
import java.nio.file.*;
import java.util.*;

public final class CrisisProgressStoreTest {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  var stages=new ArrayList<CrisisScenes.Stage>();for(var phase:CrisisScenes.Phase.values())stages.add(new CrisisScenes.Stage(phase,"test:"+phase.name().toLowerCase(Locale.ROOT),10));
  var scene=new CrisisScenes.Scene("test","minecraft:overworld",10,100,stages);scene.validate();
  Path root=Path.of("build","crisis-progress-test");Files.createDirectories(root);Path dir=Files.createTempDirectory(root,"save-");Path path=dir.resolve("zones.json"),tmp=dir.resolve("zones.json.tmp");
  try{
   Store store=StoryAuthorityStoreTest.open(path);store.data.completedEvents.add("unrelated");store.data.storyVariables.put("chapter",StoryRules.Value.of(1));store.save();
   var event=StoryAuthorityTest.event();String before=Files.readString(path);var old=StoryObjectiveActions.State.capture(store.data);Files.createDirectory(tmp);
   try{StoryCommitTransaction.apply(store,event,s->CrisisScenes.markers(s,scene,0,true,false,false));throw new AssertionError("save failure accepted");}catch(java.io.IOException expected){}
   check(Files.readString(path).equals(before)&&store.data.storyVariables==old.variables&&store.data.objectives==old.objectives,"phase, action and objective refs roll back together");
   check(CrisisScenes.phase(store.data,scene)==-1&&!store.data.completedEvents.contains(event.key()),"failed phase consumes neither cursor nor Once");Files.delete(tmp);
   StoryCommitTransaction.apply(store,event,s->CrisisScenes.markers(s,scene,0,true,false,false));
   store=StoryAuthorityStoreTest.open(path);
   check(CrisisScenes.phase(store.data,scene)==0&&CrisisScenes.flag(store.data,scene,"active"),"phase survives actual Store reopen");
   check(store.data.storyVariables.get("chapter").integer==2&&store.data.completedEvents.containsAll(Set.of("unrelated",event.key())),"action and phase share one saved state");
   check(StoryCommitTransaction.apply(store,event,s->{throw new AssertionError("committed phase replayed");})==StoryCommitTransaction.Result.ALREADY_COMPLETED,"replay cannot invoke markers");
   var internal=new StoryEventCatalog.Event("finish",false,event.area(),List.of(),List.of());
   StoryCommitTransaction.apply(store,internal,s->CrisisScenes.markers(s,scene,5,false,false,true));
   var loaded=StoryAuthorityStoreTest.open(path);check(CrisisScenes.flag(loaded.data,scene,"done")&&!CrisisScenes.flag(loaded.data,scene,"active")&&CrisisScenes.phase(loaded.data,scene)==5,"completion survives reopen");
   loaded.data.storyVariables.put(scene.key("phase"),StoryRules.Value.of(99));
   try{CrisisScenes.phase(loaded.data,scene);throw new AssertionError();}catch(IllegalStateException expected){checks++;}
  }finally{Files.deleteIfExists(tmp);Files.deleteIfExists(dir.resolve("zones.json.bak"));Files.deleteIfExists(path);Files.deleteIfExists(dir);}
  System.out.println("PASS CrisisProgressStoreTest: "+checks+" atomic phase/action/reopen checks");
 }
}
