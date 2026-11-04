import com.google.gson.Gson;
import dev.whiteout.StoryRules;
import dev.whiteout.power.*;
import java.nio.file.*;
import java.util.*;

/** Real base StoryRules and production transaction/catalog; no Minecraft world. */
public final class StoryAuthorityTest {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static final Gson JSON=new Gson();
 static StoryEventCatalog.Event event(){
  return JSON.fromJson("""
   {"key":"test:bridge","once":true,
    "area":{"dimension":"minecraft:overworld","x":1,"y":72,"z":3,"radius":3},
    "conditions":[],"actions":[{"variable":"chapter","operation":"SET","value":{"type":"INT","integer":2}}]}
   """,StoryEventCatalog.Event.class);
 }
 public static void main(String[] args)throws Exception{
  var event=event();event.validate();
  // Shared fixture is also checked against the real Python converter in check_story_migration.py.
  var migrated=new StoryEventCatalog(Path.of("tests/fixtures/story-migration-catalog.json"));
  check(!migrated.invalid()&&migrated.size()==1,"migration output accepted by production catalog");
  var imported=migrated.find("cutscene_trigger:lake_gate");
  var rule=migrated.rule(imported.key());
  var tracker=new TriggerEvidence();
  var inside=new TriggerEvidence.Body("minecraft:overworld",9.9,70,0,10.1,72,1);
  var outside=new TriggerEvidence.Body("minecraft:overworld",9,70,0,10,72,1);
  var offers=new ArrayList<String>();
  var delayed=new TriggerEvidence.Rule(rule.key(),rule.area(),new TriggerEvidence.Binding(TriggerEvidence.Kind.ENTER_BOX,2,null,List.of("dialogue_choice:a:0:0","dialogue_choice:a:0:1"),null));
  tracker.observe(delayed,inside,0,k->true,offers::add);
  check(offers.isEmpty(),"join inside is not a crossing");
  tracker.observe(delayed,outside,1,k->true,offers::add);
  tracker.observe(delayed,inside,2,k->true,offers::add);
  tracker.observe(delayed,outside,3,k->true,offers::add);
  tracker.observe(delayed,inside,4,k->true,offers::add);
  tracker.observe(delayed,inside,5,k->true,offers::add);
  check(!tracker.permits(delayed,5),"early or cancelled crossing");
  tracker.observe(delayed,inside,6,k->true,offers::add);
  tracker.observe(delayed,inside,7,k->true,offers::add);
  check(offers.size()==1&&tracker.permits(delayed,7),"single mature crossing");
  tracker.consume(delayed,7);
  var choice=new TriggerEvidence.Binding(TriggerEvidence.Kind.CHOICE,0,null,List.of(),null);
  var left=new TriggerEvidence.Rule("dialogue_choice:a:0:0",null,choice);
  var right=new TriggerEvidence.Rule("dialogue_choice:a:0:1",null,choice);
  check(!tracker.permits(delayed,7)&&tracker.permits(left,8)&&tracker.permits(right,8),"only earned branches");
  tracker.consume(left,8);
  check(!tracker.permits(right,8)&&!new TriggerEvidence().permits(left,8),"sibling and reconnect denied");
  check(imported.area().contains("minecraft:overworld",10,70,-2)&&!imported.area().contains("minecraft:overworld",13,70,-2),"migrated box deserialized correctly");
  check(StoryCommitTransaction.apply(Map.of("chapter",StoryRules.Value.of(1)),Set.of(),imported,
   (v,c)->check(v.get("gate_seen").bool&&c.contains(imported.key()),"migrated rules execute and retain once"))==StoryCommitTransaction.Result.COMMITTED,"migrated transaction");
  check(event.area().contains("minecraft:overworld",4,72,3),"boundary permitted");
  check(!event.area().contains("minecraft:overworld",4.01,72,3),"distance denied");
  check(!event.area().contains("minecraft:the_nether",1,72,3),"dimension denied");
  check(!event.area().contains("minecraft:overworld",Double.NaN,72,3),"NaN denied");
  var box=new StoryEventCatalog.Box(1,2,3,4,5,6);box.validate();
  check(box.contains(1,2,3)&&box.contains(3.99,4.99,5.99)&&!box.contains(4,3,4),"exact half-open box");
  check(!box.contains(Double.NaN,3,4),"box NaN denied");
  var visual=new StoryEventCatalog.Event("visual",false,event.area(),List.of(),List.of());
  check(StoryCommitTransaction.apply(Map.of(),Set.of(),visual,(v,c)->{throw new AssertionError("visual event saved unchanged state");})==StoryCommitTransaction.Result.COMMITTED,"no needless serialization");
  var onceOnly=new StoryEventCatalog.Event("once_only",true,event.area(),List.of(),List.of());
  StoryCommitTransaction.apply(Map.of(),Set.of(),onceOnly,(v,c)->check(c.contains("once_only"),"pure dialogue once retained"));
  Map<String,StoryRules.Value> variables=new HashMap<>();variables.put("chapter",StoryRules.Value.of(1));variables.put("unrelated",StoryRules.Value.of(true));
  Set<String> completed=new HashSet<>(Set.of("other:scene"));int[] saves={0};
  StoryCommitTransaction.Writer write=(v,c)->{saves[0]++;check(v.get("chapter").integer==2,"canonical action");check(v.get("unrelated").bool,"unrelated variable retained");check(c.containsAll(Set.of("other:scene","test:bridge")),"once and unrelated keys retained");};
  check(StoryCommitTransaction.apply(variables,completed,event,write)==StoryCommitTransaction.Result.COMMITTED,"commit");
  check(variables.get("chapter").integer==1&&!completed.contains(event.key()),"input unchanged until publication");
  try{StoryCommitTransaction.apply(variables,completed,event,(v,c)->{throw new java.io.IOException("disk");});throw new AssertionError("write failure swallowed");}catch(java.io.IOException expected){}
  check(variables.get("chapter").integer==1&&!completed.contains(event.key()),"failed write does not publish or consume once");
  StoryCommitTransaction.apply(variables,completed,event,(v,c)->{variables.clear();variables.putAll(v);completed.clear();completed.addAll(c);});
  check(StoryCommitTransaction.apply(variables,completed,event,write)==StoryCommitTransaction.Result.ALREADY_COMPLETED&&saves[0]==1,"second player cannot repeat once");
  StoryRules.Condition condition=new StoryRules.Condition();condition.variable="chapter";condition.operator=StoryRules.Operator.EQ;condition.value=StoryRules.Value.of(99);
  var conditional=new StoryEventCatalog.Event("test:condition",true,event.area(),List.of(condition),event.actions());
  check(StoryCommitTransaction.apply(variables,completed,conditional,write)==StoryCommitTransaction.Result.CONDITIONS_FALSE&&saves[0]==1,"server precondition");
  Path dir=Files.createTempDirectory("whiteout-authority-");Path path=dir.resolve("events.json");
  try{
   check(new StoryEventCatalog(path).size()==0,"missing catalog denies all story keys");
   String json=JSON.toJson(new StoryEventCatalog.Data(1,List.of(event)));Files.writeString(path,json);
   StoryEventCatalog catalog=new StoryEventCatalog(path);check(!catalog.invalid()&&catalog.size()==1,"catalog loaded");
   check(catalog.find("unknown")==null,"unregistered key");
   byte[] forged="{\"key\":\"test:bridge\",\"once\":false,\"conditions\":[],\"actions\":[{\"variable\":\"hacked\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
   var canonical=catalog.resolve(forged);
   check(canonical.once()&&canonical.actions().getFirst().variable.equals("chapter"),"client actions and once ignored");
   for(String payload:List.of("{}","null","[]","{\"key\":123}","{\"key\":\"unknown\"}"," ".repeat(8193))){
    boolean rejected=false;try{catalog.resolve(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(RuntimeException expected){rejected=true;}
    check(rejected,"malformed/unregistered request rejected");
   }
   catalog.find(event.key()).actions().getFirst().variable="injected";
   check(catalog.find(event.key()).actions().getFirst().variable.equals("chapter"),"defensive DTO copy");
   var start=new StorySceneActions.Change("scene_a",StorySceneActions.Operation.START);
   var dedicated=new StoryEventCatalog.Event("dedicated",true,event.area(),List.of(),List.of(),List.of(),null,false,List.of(start));
   var shared=new StoryEventCatalog.Event("shared",true,event.area(),List.of(),List.of(),List.of(),null,false,List.of(start,new StorySceneActions.Change("scene_b",StorySceneActions.Operation.START)));
   Files.writeString(path,JSON.toJson(new StoryEventCatalog.Data(1,List.of(event,dedicated,shared))));
   check(new StoryEventCatalog(path).dedicatedSceneStartKeys("scene_a").equals(Set.of("dedicated")),"scene reset excludes unrelated/shared Once keys");
   Map<String,StoryRules.Value> reloadedVariables=JSON.fromJson(JSON.toJson(variables),new com.google.gson.reflect.TypeToken<Map<String,StoryRules.Value>>(){}.getType());
   Set<String> reloadedCompleted=new HashSet<>(Arrays.asList(JSON.fromJson(JSON.toJson(completed),String[].class)));
   check(StoryCommitTransaction.apply(reloadedVariables,reloadedCompleted,new StoryEventCatalog(path).find(event.key()),write)==StoryCommitTransaction.Result.ALREADY_COMPLETED,"restart keeps existing once keys");
   for(String invalid:List.of("{broken","null","{\"schema\":4,\"events\":[]}",JSON.toJson(new StoryEventCatalog.Data(1,List.of(event,event))),json.replace("\"radius\":3.0","\"radius\":-1")," ".repeat(StoryEventCatalog.MAX_BYTES+1))){
    Files.writeString(path,invalid);var corrupt=new StoryEventCatalog(path);check(corrupt.invalid()&&corrupt.size()==0,"invalid catalog fails closed");check(Files.readString(path).equals(invalid),"invalid file preserved");
   }
  }finally{Files.deleteIfExists(path);Files.deleteIfExists(dir);}
  for(String action:List.of("STORY_OBJECTIVE_EVENT","EVENT_DONE","REMOVE_COMPLETED_EVENT","ACTOR_BEGIN","ACTOR_COMMAND","ACTOR_STOP","EASY_NPC","SAVE_ZONE","CHECKPOINT_LOAD")){
   check(!StoryRequestPolicy.legacyAllowed(action,false),"unprivileged denied: "+action);check(StoryRequestPolicy.legacyAllowed(action,true),"operator editor: "+action);
  }
  for(boolean op:List.of(false,true)){
   check(!StoryRequestPolicy.legacyAllowed("STORY_COMMIT",op),"raw commit never delegated");check(!StoryRequestPolicy.legacyAllowed("NEW_UNAUDITED_ACTION",op),"unknown fails closed");check(!StoryRequestPolicy.legacyAllowed(null,op),"null fails closed");
   for(String action:List.of("REQUEST","EQUIP","GOGGLES","REPLACE_BATTERY"))check(StoryRequestPolicy.legacyAllowed(action,op),"ordinary gameplay retained");
  }
  var budget=new StoryRequestPolicy.Budget();for(int i=0;i<16;i++)check(budget.take(100),"within budget");
  check(!budget.take(100)&&!budget.take(119),"flood bounded");check(budget.take(120),"new window");check(budget.take(0),"clock reset");
  System.out.println("PASS StoryAuthorityTest: "+checks+" checks; real StoryRules, persistence callback, no Minecraft world");
 }
}
