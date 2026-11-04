package dev.whiteout.power;

import com.google.gson.*;
import net.minecraft.server.network.ServerPlayerEntity;
import dev.whiteout.StoryRules;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-authored permissions, not another progress store. Never writes a world file. */
public final class StoryEventCatalog {
 public static final int MAX_BYTES=262144,MAX_EVENTS=128,MAX_REQUEST_BYTES=8192;
 private static final Gson JSON=new Gson();
 /** Half-open exact bounds, matching normalized legacy ENTER_BOX geometry. */
 public record Box(double x1,double y1,double z1,double x2,double y2,double z2){
  public void validate(){
   double[] lo={x1,y1,z1},hi={x2,y2,z2};
   for(int i=0;i<3;i++)if(!Double.isFinite(lo[i])||!Double.isFinite(hi[i])||lo[i]>=hi[i]||hi[i]-lo[i]>256||Math.abs(lo[i])>(i==1?2048:30000000)||Math.abs(hi[i])>(i==1?2048:30000000))throw new IllegalArgumentException("box");
  }
  public boolean contains(double x,double y,double z){return x>=x1&&x<x2&&y>=y1&&y<y2&&z>=z1&&z<z2;}
 }
 public record Area(String dimension,double x,double y,double z,double radius,Box box){
  public Area(String dimension,double x,double y,double z,double radius){this(dimension,x,y,z,radius,null);}
  public void validate(){
   if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||dimension.length()>128)throw new IllegalArgumentException("dimension");
   if(box!=null){box.validate();if(x!=0||y!=0||z!=0||radius!=0)throw new IllegalArgumentException("ambiguous area");return;}
   if(
      !Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000||
      y < -2048||y>2048||!Double.isFinite(radius)||radius<=0||radius>128)throw new IllegalArgumentException("area");
  }
  public boolean contains(String dimension,double x,double y,double z){
   if(box!=null)return this.dimension.equals(dimension)&&box.contains(x,y,z);
   double dx=this.x-x,dy=this.y-y,dz=this.z-z;
   return this.dimension.equals(dimension)&&Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz)&&dx*dx+dy*dy+dz*dz<=radius*radius;
  }
 }
 public record Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world,boolean serverOnly,List<StorySceneActions.Change> scenes,TriggerEvidence.Binding trigger,ScopedStory.Scope scope,String checkpoint,Narrative.Policy narrative){
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world,boolean serverOnly,List<StorySceneActions.Change> scenes,TriggerEvidence.Binding trigger,ScopedStory.Scope scope,String checkpoint){this(key,once,area,conditions,actions,objectives,world,serverOnly,scenes,trigger,scope,checkpoint,null);}
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world,boolean serverOnly,List<StorySceneActions.Change> scenes,TriggerEvidence.Binding trigger){this(key,once,area,conditions,actions,objectives,world,serverOnly,scenes,trigger,ScopedStory.Scope.WORLD,null);}
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world,boolean serverOnly,List<StorySceneActions.Change> scenes){this(key,once,area,conditions,actions,objectives,world,serverOnly,scenes,null);}
  public Event { scope=Objects.requireNonNullElse(scope,ScopedStory.Scope.WORLD);objectives=objectives==null?List.of():List.copyOf(objectives);scenes=scenes==null?List.of():List.copyOf(scenes); }
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world,boolean serverOnly){this(key,once,area,conditions,actions,objectives,world,serverOnly,List.of());}
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions){this(key,once,area,conditions,actions,List.of(),null,false);}
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives){this(key,once,area,conditions,actions,objectives,null,false);}
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world){this(key,once,area,conditions,actions,objectives,world,false);}
  public void validate(){
   if(key==null||!key.matches("[a-zA-Z0-9_.:/-]{1,128}")||(area==null&&trigger==null)||conditions==null||actions==null)throw new IllegalArgumentException("event");
   if(narrative!=null)narrative.validate(this);
   if(scope==ScopedStory.Scope.PLAYER&&(serverOnly||!scenes.isEmpty()))throw new IllegalArgumentException("Director events require WORLD scope");
   if(checkpoint!=null){StoryRules.name(checkpoint);if(serverOnly)throw new IllegalArgumentException("checkpoint requires a player position");}
   if(trigger!=null){
    if(serverOnly||trigger.zone()&&(area==null||area.box()==null))throw new IllegalArgumentException("invalid trigger binding");
    String prefix=trigger.type()==TriggerEvidence.Kind.CHOICE?"dialogue_choice:":"cutscene_trigger:";
    if(!key.startsWith(prefix))throw new IllegalArgumentException("inconsistent trigger key");
   }
   if(area!=null)area.validate();
   StoryRules.validate(conditions,actions);StoryObjectiveActions.validate(objectives);
   if(world!=null)world.validate();
   StorySceneActions.validate(scenes);
  }
 }
 public record Data(int schema,List<Event> events){}
 private final Map<String,String> events=new LinkedHashMap<>();
 private final Map<String,TriggerEvidence.Rule> rules=new LinkedHashMap<>();
 private final Map<String,String> fingerprints=new HashMap<>();
 String fingerprint(String key){return fingerprints.computeIfAbsent(key,TriggerAuthority::fingerprint);}
 private final Map<String,Event> boundEvents=new HashMap<>();
 boolean eligible(String key,dev.whiteout.Store.Data root,net.minecraft.server.network.ServerPlayerEntity player){
  Event event=boundEvents.get(key);
  if(event==null)return false;
  if(!Narrative.allowed(player,event))return false;
  var data=ScopedStory.read(root,event.scope(),Narrative.owner(player,event));
  var world=new StoryWorldSource(player,data);
  return event!=null&&!(event.once()&&data.completedEvents.contains(key))
   &&StoryRules.matches(data.storyVariables,event.conditions())
   &&StoryWorldConditions.evaluate(event.world(),world)==WinterCrisisConditions.Truth.TRUE;
 }
 public Collection<TriggerEvidence.Rule> rules(){return Collections.unmodifiableCollection(rules.values());}
 public TriggerEvidence.Rule rule(String key){return rules.get(key);}
 boolean visible(ServerPlayerEntity player,String key){var e=boundEvents.get(key);return e!=null&&(e.narrative()==null||Narrative.allowed(player,e));}
 private String error="";
 public StoryEventCatalog(Path path){
  if(!Files.exists(path))return;
  try{
   if(Files.size(path)>MAX_BYTES)throw new IOException("size");
   var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   for(var entry:root.getAsJsonArray("events")){
    var object=entry.getAsJsonObject();
    if(object.has("narrative")&&root.get("schema").getAsInt()!=4)throw new IllegalArgumentException("narrative requires schema 4");
    if(object.has("narrative")){var policy=object.getAsJsonObject("narrative");
     if(policy.has("mode"))Narrative.Mode.valueOf(policy.get("mode").getAsString());
     if(policy.has("actor"))Narrative.Role.valueOf(policy.get("actor").getAsString());
     if(policy.has("recipient"))Narrative.Role.valueOf(policy.get("recipient").getAsString());
     if(policy.has("visibility"))Narrative.Visibility.valueOf(policy.get("visibility").getAsString());
    }
    if(object.has("scope")||object.has("checkpoint")){
     if(root.get("schema").getAsInt()<3&&(object.has("checkpoint")||!"WORLD".equals(object.get("scope").getAsString())))throw new IllegalArgumentException("scoped progress requires schema 3");
     if(object.has("scope"))ScopedStory.Scope.valueOf(object.get("scope").getAsString());
    }
   }
   Data data=JSON.fromJson(root,Data.class);
   if(data==null||(data.schema()!=1&&data.schema()!=2&&data.schema()!=3&&data.schema()!=4)||data.events()==null||data.events().size()>MAX_EVENTS)throw new IllegalArgumentException("schema/events");
   for(Event event:data.events()){
    if(event==null)throw new IllegalArgumentException("null event");event.validate();
    if(events.putIfAbsent(event.key(),JSON.toJson(event))!=null)throw new IllegalArgumentException("duplicate key");
    if(event.trigger()!=null){
     if(data.schema()<2)throw new IllegalArgumentException("trigger bindings require schema 2 or later");
     boundEvents.put(event.key(),event);
     rules.put(event.key(),new TriggerEvidence.Rule(event.key(),event.area(),event.trigger()));
    }
   }
   for(Event entry:data.events()){var policy=entry.narrative();if(policy==null)continue;
    if(policy.sharedScene()!=null)requireExit(policy.sharedScene().completion());
    for(var ending:policy.endings()){var target=requireExit(ending.event());if(target.world()!=null)throw new IllegalArgumentException("world conditions belong on ending entrance");
     if(ending.finale()!=null){if(ending.finale().completion().equals(target.key()))throw new IllegalArgumentException("finale requires a separate completion");requireExit(ending.finale().completion());}}
   }
   for(var rule:rules.values())for(String next:rule.binding().nextChoices()){
    if(!rules.containsKey(next)||rules.get(next).binding().type()!=TriggerEvidence.Kind.CHOICE)throw new IllegalArgumentException("unknown choice: "+next);
   }
  }catch(Exception failure){events.clear();rules.clear();boundEvents.clear();error=failure.getClass().getSimpleName()+": "+failure.getMessage();}
 }
 private Event requireExit(String key){var target=find(key);if(target==null||!target.serverOnly()||!target.once()||target.scope()!=ScopedStory.Scope.WORLD||target.narrative()!=null)throw new IllegalArgumentException("missing canonical WORLD Once event: "+key);return target;}
 public boolean invalid(){return !error.isEmpty();}
 public String error(){return error;}
 public int size(){return events.size();}
 /** Legacy packets may contain actions/conditions/once; only the key has authority as a request. */
 public Event resolve(byte[] payload){
  if(invalid())throw new IllegalArgumentException("invalid server catalog: "+error);
  if(payload==null||payload.length>MAX_REQUEST_BYTES)throw new IllegalArgumentException("request size");
  JsonObject request=JsonParser.parseString(new String(payload,StandardCharsets.UTF_8)).getAsJsonObject();
  JsonElement key=request.get("key");
  if(key==null||!key.isJsonPrimitive()||!key.getAsJsonPrimitive().isString()||key.getAsString().length()>128)throw new IllegalArgumentException("key");
  Event event=find(key.getAsString());
  if(event==null)throw new IllegalArgumentException("unregistered story key");
  return event;
 }
 /** Defensive copy: StoryRules DTOs are mutable in the existing base. */
 public Event find(String key){String json=events.get(key);return json==null?null:JSON.fromJson(json,Event.class);}
 public Set<String> dedicatedSceneStartKeys(String scene){
  Set<String> keys=new HashSet<>();
  for(String json:events.values()){
   Event event=JSON.fromJson(json,Event.class);
   if(event.scenes().size()==1&&event.scenes().getFirst().id().equals(scene)&&event.scenes().getFirst().operation()==StorySceneActions.Operation.START)keys.add(event.key());
  }
  return Set.copyOf(keys);
 }
}
