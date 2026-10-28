import dev.whiteout.power.*;
import dev.whiteout.StoryRules;
import java.nio.file.*;
import java.util.*;
import static dev.whiteout.power.WinterCrisisConditions.Truth.*;

public class DialogueRouteTest {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 static DialogueRoute.Condition condition(String id){return new DialogueRoute.Condition(DialogueRoute.Type.NETWORK,id,"",0,0,0,true,"",null);}
 static DialogueRoute route(){return new DialogueRoute("test","fallback",List.of(new DialogueRoute.Variant("a",1,condition("first")),new DialogueRoute.Variant("b",2,condition("second"))));}
 public static void main(String[] args)throws Exception{
  var route=route();check(route.select(c->TRUE).equals("b"));check(route.select(c->FALSE).equals("fallback"));check(route.select(c->UNKNOWN).equals("fallback"));
  check(DialogueRoute.reference(DialogueRoute.PREFIX+"a".repeat(64)));check(!DialogueRoute.reference(DialogueRoute.PREFIX+"a".repeat(65)));check(!DialogueRoute.reference("legacy"));
  check(route.select(c->c.target().equals("second")?UNKNOWN:TRUE).equals("a"));
  var tie=new DialogueRoute("tie","fallback",List.of(new DialogueRoute.Variant("a",1,condition("a")),new DialogueRoute.Variant("b",1,condition("b"))));
  check(tie.select(c->TRUE).equals("a"));check(route.select(c->{throw new IllegalStateException();}).equals("fallback"));
  check(DialogueRoute.playable("deleted","fallback",Set.of("fallback")::contains).equals("fallback"));
  check(DialogueRoute.playable("deleted","gone",s->false)==null);check(DialogueRoute.playable("a","fallback",s->true).equals("a"));
  check(UNKNOWN.not()==UNKNOWN);
  for(var availability:WhiteoutWorldState.Availability.values()){
   var state=new WhiteoutWorldState.Reading<>(availability,0,Optional.of(true));
   var truth=WinterCrisisConditions.observed(state,v->v);
   check(truth==(availability==WhiteoutWorldState.Availability.AVAILABLE?TRUE:UNKNOWN));
   check(route.select(c->truth).equals(availability==WhiteoutWorldState.Availability.AVAILABLE?"b":"fallback"));
   if(availability!=WhiteoutWorldState.Availability.AVAILABLE)check(route.select(c->truth.not()).equals("fallback"));
  }
  var missing=new WhiteoutWorldState.Reading<Boolean>(WhiteoutWorldState.Availability.AVAILABLE,0,Optional.empty());
  check(WinterCrisisConditions.observed(missing,v->v)==UNKNOWN);
  var story=new DialogueRoute.Condition(DialogueRoute.Type.STORY,"chapter","",0,0,0,true,"2",StoryRules.Operator.GE);story.validate();
  check(story.storyTruth(Map.of())==UNKNOWN);check(story.storyTruth(Map.of("chapter",StoryRules.Value.of(true)))==UNKNOWN);
  check(story.storyTruth(Map.of("chapter",StoryRules.Value.of(1)))==FALSE);check(story.storyTruth(Map.of("chapter",StoryRules.Value.of(2)))==TRUE);
  var booleanStory=new DialogueRoute.Condition(DialogueRoute.Type.STORY,"ready","",0,0,0,true,"true",StoryRules.Operator.EQ);booleanStory.validate();
  check(booleanStory.storyTruth(Map.of("ready",StoryRules.Value.of(false)))==FALSE);check(booleanStory.storyTruth(Map.of("ready",StoryRules.Value.of(true)))==TRUE);
  var queue=new DialogueRouteQueue<String>();var first=queue.offer("test","one",10);
  check(first!=null);check(queue.offer("test","duplicate",10)==null);check(queue.offer("test","in-flight",11)==null);
  var second=queue.offer(null,"ordinary",11);check(queue.complete(second.token())==null);check(queue.complete(first.token()).value().equals("one"));
  check(queue.complete(second.token()).value().equals("ordinary"));check(queue.empty());
  var third=queue.offer("test","later",12);check(third!=null);queue.clear();check(queue.empty());
  var after=queue.offer("test","new world",12);check(after.token()!=third.token());check(queue.complete(third.token())==null);
  Path dir=Files.createTempDirectory(Path.of("build"),"route-test-");Path file=dir.resolve("routes.json");
  Path dialogues=dir.resolve("dialogues.json");Files.writeString(dialogues,"legacy sentinel");
  var store=new DialogueRouteStore(file);check(!store.invalid()&&store.snapshot().routes().isEmpty());store.save(route,0);
  var reloaded=new DialogueRouteStore(file);check(reloaded.find("test").equals(route));check(reloaded.snapshot().revision()==1);
  String saved=Files.readString(file);
  try{store.save(route,0);throw new AssertionError();}catch(java.io.IOException expected){check(Files.readString(file).equals(saved));}
  reloaded.delete("test",1);check(new DialogueRouteStore(file).snapshot().routes().isEmpty());
  Files.writeString(file,"{broken");var corrupt=new DialogueRouteStore(file);check(corrupt.invalid());check(corrupt.find("test")==null);
  try{corrupt.save(route,0);throw new AssertionError();}catch(java.io.IOException expected){check(Files.readString(file).equals("{broken"));}
  Files.writeString(file,"null");check(new DialogueRouteStore(file).invalid());
  Files.writeString(file,"{\"schema\":2,\"revision\":0,\"routes\":[]}");check(new DialogueRouteStore(file).invalid());
  Files.writeString(file," ".repeat(DialogueRouteStore.MAX_BYTES+1));check(new DialogueRouteStore(file).invalid());
  check(Files.readString(dialogues).equals("legacy sentinel"));
  var invalidStore=new DialogueRouteStore(dir.resolve("blocked"));Files.createDirectory(dir.resolve("blocked"));
  Files.writeString(dir.resolve("blocked/keep"),"x");
  try{invalidStore.save(route,0);throw new AssertionError();}catch(java.io.IOException expected){check(invalidStore.snapshot().revision()==0&&invalidStore.snapshot().routes().isEmpty());}
  Files.delete(dir.resolve("blocked/keep"));Files.delete(dir.resolve("blocked"));
  queue.clear();for(int i=0;i<32;i++)check(queue.offer("r"+i,"bounded",100)!=null);check(queue.offer("overflow","bounded",100)==null);queue.clear();
  try{new DialogueRoute("../bad","fallback",route.variants()).validate();throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  try{new DialogueRoute("test",DialogueRoute.PREFIX+"recursive",route.variants()).validate();throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  Files.delete(file);Files.delete(dialogues);Files.delete(dir);
  System.out.println("PASS DialogueRouteTest: "+checks+" selection, fallback, unknown, duplicate/FIFO, lifecycle, persistence and corrupt-file checks");
 }
}
