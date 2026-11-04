package dev.whiteout.power;

import com.google.gson.Gson;
import dev.whiteout.Store;
import dev.whiteout.StoryRules;
import dev.whiteout.net.WhiteoutNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import static dev.whiteout.power.WinterCrisisDirector.*;
import static dev.whiteout.power.WinterCrisisConditions.*;

/** Authored six-phase adapter for the existing coordinator. Progress lives only in Store. */
public final class CrisisScenes {
 public enum Phase { PREPARATION, ONSET, ESCALATION, CRITICAL, RESOLUTION, AFTERMATH }
 public record Stage(Phase phase,String event,long ticks){}
 public record Scene(String id,String dimension,int priority,long armTimeout,List<Stage> phases,String resetEvent){
  public Scene(String id,String dimension,int priority,long armTimeout,List<Stage> phases){this(id,dimension,priority,armTimeout,phases,null);}
  public void validate(){
   if(id==null||!id.matches("[a-z0-9_]{1,24}")||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||dimension.length()>128||priority<0||priority>100||armTimeout<40||armTimeout>72000||phases==null||phases.size()!=6)throw new IllegalArgumentException("scene metadata");
   Set<String> keys=new HashSet<>();long total=0;
   for(int i=0;i<6;i++){
    Stage s=phases.get(i);
    if(s==null||s.phase()!=Phase.values()[i]||s.event()==null||!s.event().matches("[a-zA-Z0-9_.:/-]{1,128}")||!keys.add(s.event())||s.ticks()<1||s.ticks()>72000)throw new IllegalArgumentException("scene phase");
    total+=s.ticks();
   }
   if(total>72000)throw new IllegalArgumentException("scene exceeds 72000 ticks");
   if(resetEvent!=null&&(!resetEvent.matches("[a-zA-Z0-9_.:/-]{1,128}")||keys.contains(resetEvent)))throw new IllegalArgumentException("reset event");
  }
  public String runtimeId(){return "scene:"+id;}
  public String key(String suffix){return "crisis."+id+"."+suffix;}
 }
 public record Catalog(int schema,List<Scene> scenes){}
 private final MinecraftServer server;
 private final Map<String,Scene> scenes=new LinkedHashMap<>();
 private final Map<String,String> reasons=new LinkedHashMap<>();
 private String error="";
 public CrisisScenes(MinecraftServer server){
  this.server=server;Path path=server.getSavePath(WorldSavePath.ROOT).resolve("whiteout-crisis-scenes.json");
  if(!Files.exists(path))return;
  try{
   if(Files.size(path)>262144)throw new IllegalArgumentException("scene catalog size");
   Catalog catalog=new Gson().fromJson(Files.readString(path),Catalog.class);
   scenes.putAll(validateCatalog(catalog,key->StoryAuthority.definition(server,key)));
  }catch(Exception failure){scenes.clear();error=failure.getClass().getSimpleName()+": "+failure.getMessage();}
 }
 public static Map<String,Scene> validateCatalog(Catalog catalog,java.util.function.Function<String,StoryEventCatalog.Event> lookup){
   Map<String,Scene> result=new LinkedHashMap<>();
   if(catalog==null||catalog.schema()!=1||catalog.scenes()==null||catalog.scenes().size()>8)throw new IllegalArgumentException("scene catalog schema/count");
   Set<String> events=new HashSet<>();
   for(Scene scene:catalog.scenes()){
    scene.validate();if(result.putIfAbsent(scene.runtimeId(),scene)!=null)throw new IllegalArgumentException("duplicate scene");
    for(Stage phase:scene.phases()){
     if(!events.add(phase.event()))throw new IllegalArgumentException("phase event shared by scenes");
     var event=Objects.requireNonNull(lookup.apply(phase.event()));event.validate();
     if(!event.once()||!event.serverOnly()||!event.area().dimension().equals(scene.dimension()))throw new IllegalArgumentException("phase needs server-only Once event in scene dimension");
     if(!event.scenes().isEmpty())throw new IllegalArgumentException("nested scene requests in phases are not supported");
    }
    if(scene.resetEvent()!=null){
     var reset=Objects.requireNonNull(lookup.apply(scene.resetEvent()));reset.validate();
     if(!reset.serverOnly()||reset.once()||!reset.scenes().isEmpty()||reset.world()!=null)throw new IllegalArgumentException("reset needs repeatable server-only event without scene requests/world conditions");
    }
   }
   return Collections.unmodifiableMap(result);
 }
 public boolean owns(String id){return scenes.containsKey(id);}
 public String status(){return "Authored scenes="+scenes.size()+", error="+error+", waiting="+reasons;}
 private Store store(){return Store.get(server);}
 public static int phase(Store.Data data,Scene scene){
  var v=data.storyVariables.get(scene.key("phase"));if(v==null)return -1;
  if(v.type!=StoryRules.Type.INT||v.integer<0||v.integer>=6)throw new IllegalStateException("invalid saved crisis phase");return v.integer;
 }
 public static boolean flag(Store.Data data,Scene scene,String name){
  var v=data.storyVariables.get(scene.key(name));if(v==null)return false;
  if(v.type!=StoryRules.Type.BOOL)throw new IllegalStateException("invalid crisis flag");return v.bool;
 }
 public static long remaining(Store.Data data,Scene scene,int phase){
  var value=data.storyVariables.get(scene.key("remaining"));long duration=scene.phases().get(phase).ticks();
  if(value==null)return duration;
  if(value.type!=StoryRules.Type.INT||value.integer<0||value.integer>duration)throw new IllegalStateException("invalid phase timer");return value.integer;
 }
 public static void markers(StoryObjectiveActions.State state,Scene scene,int phase,boolean active,boolean queued,boolean done){
  if(phase>=0)state.variables.put(scene.key("phase"),StoryRules.Value.of(phase));
  state.variables.put(scene.key("active"),StoryRules.Value.of(active));state.variables.put(scene.key("queued"),StoryRules.Value.of(queued));state.variables.put(scene.key("done"),StoryRules.Value.of(done));
 }
 private StoryEventCatalog.Event internal(Scene scene){return new StoryEventCatalog.Event("crisis_internal:"+scene.id(),false,new StoryEventCatalog.Area(scene.dimension(),0,0,0,1),List.of(),List.of());}
 private void flags(Scene scene,boolean active,boolean queued,boolean done)throws Exception{
  StoryCommitTransaction.apply(store(),internal(scene),s->markers(s,scene,phase(store().data,scene),active,queued,done));
 }
 private void sync(){try{WhiteoutNetwork.syncAll(server);}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger("whiteout-crisis").warn("Crisis saved but snapshot delivery failed",failure);}}
 private Spec spec(Scene scene){
  Condition ready=new Atom(new Signal(Type.SCENARIO_READY,null,null,scene.runtimeId(),0,null));
  var durations=scene.phases().stream().map(Stage::ticks).toList();
  return new Spec(scene.runtimeId(),new Target(Scope.WORLD,scene.dimension(),Set.of()),ready,ready,scene.priority(),1,0,scene.armTimeout(),durations.stream().mapToLong(Long::longValue).sum(),0,CancelPolicy.CHECK_AT_START,CompletionPolicy.AFTER_DURATION,durations);
 }
 public String start(String id,WinterCrisisDirector director,long tick)throws Exception{
  Scene scene=scenes.get("scene:"+id);if(scene==null)throw new IllegalArgumentException("unknown scene; "+error);
  if(!director.enabled())throw new IllegalStateException("Winter Crisis is disabled");
  if(flag(store().data,scene,"done"))return "Scene already completed: "+id;
  var previous=director.status(scene.runtimeId());
  if(previous.isPresent()&&(previous.get().state()==State.ACTIVE||previous.get().state()==State.ARMED))return "Scene already running: "+id;
  director.forget(scene.runtimeId());if(!director.register(spec(scene)))throw new IllegalStateException("Director capacity");
  int phase=phase(store().data,scene);
  if(phase>=0){
   for(int n=0;n<=phase;n++)if(!store().data.completedEvents.contains(scene.phases().get(n).event()))throw new IllegalStateException("phase marker does not match committed events");
   if(director.status().stream().filter(v->v.state()==State.ACTIVE).count()>=4)throw new IllegalStateException("waiting for active scene capacity");
   long remaining=remaining(store().data,scene,phase);
   flags(scene,true,false,false);if(!director.restoreActive(scene.runtimeId(),phase,tick,remaining))throw new IllegalStateException("restore rejected");
  }else{
   if(store().data.completedEvents.contains(scene.phases().getFirst().event()))throw new IllegalStateException("phase event committed without progress marker");
   flags(scene,false,true,false);if(!director.arm(scene.runtimeId(),tick))throw new IllegalStateException("arm rejected");
  }
  return "Scene scheduled: "+id;
 }
 public void recover(WinterCrisisDirector director,long tick){
  if(!director.enabled())return;
  for(Scene scene:scenes.values())try{
   if(!flag(store().data,scene,"done")&&(flag(store().data,scene,"active")||flag(store().data,scene,"queued")))start(scene.id(),director,tick);
  }catch(Exception failure){reasons.put(scene.runtimeId(),"restore blocked: "+failure.getMessage());}
 }
 public void reconcile(WinterCrisisDirector director,long tick){
  for(Scene scene:scenes.values())try{
   var view=director.status(scene.runtimeId());boolean queued=flag(store().data,scene,"queued"),active=flag(store().data,scene,"active");
   if(view.isPresent()&&(view.get().state()==State.ACTIVE||view.get().state()==State.ARMED)){
    if(view.get().state()==State.ACTIVE&&!consistent(store().data,scene,view.get().phase()))director.cancel(scene.runtimeId(),tick,"saved phase was reset or is inconsistent");
    else if(!queued&&!active)director.cancel(scene.runtimeId(),tick,"durable story cancellation");
   }else if(queued||active)start(scene.id(),director,tick);
  }catch(Exception failure){reasons.put(scene.runtimeId(),"request blocked: "+failure.getMessage());}
 }
 /** One write at orderly shutdown, never serialization per tick. Crash recovery is conservative. */
 public void checkpoint(WinterCrisisDirector director,long tick)throws Exception{
  var active=director.status().stream().filter(v->v.state()==State.ACTIVE&&owns(v.id())).toList();if(active.isEmpty())return;
  StoryCommitTransaction.apply(store(),internal(scenes.get(active.getFirst().id())),state->{
   for(View view:active){
    Scene scene=scenes.get(view.id());long remaining=Math.max(0,scene.phases().get(view.phase()).ticks()-Math.max(0,tick-view.changed()));
    state.variables.put(scene.key("remaining"),StoryRules.Value.of((int)remaining));
   }
  });
 }
 public static boolean consistent(Store.Data data,Scene scene,int phase){
  if(phase(data,scene)!=phase)return false;
  for(int n=0;n<=phase;n++)if(!data.completedEvents.contains(scene.phases().get(n).event()))return false;
  return true;
 }
 public String reset(String id,WinterCrisisDirector director,long tick)throws Exception{
  Scene scene=scenes.get("scene:"+id);if(scene==null||scene.resetEvent()==null)throw new IllegalArgumentException("scene requires an authored reset event");
  var reset=StoryAuthority.definition(server,scene.resetEvent());
  var launchKeys=StoryAuthority.sceneStartKeys(server,id);
  if(!reset.serverOnly()||reset.once()||!reset.scenes().isEmpty()||reset.world()!=null)throw new IllegalArgumentException("invalid reset event; use story-variable preconditions only");
  var result=StoryCommitTransaction.apply(store(),reset,s->{
   for(Stage stage:scene.phases())s.completed.remove(stage.event());
   s.completed.removeAll(launchKeys);
   for(String name:List.of("phase","active","queued","done","remaining"))s.variables.remove(scene.key(name));
  });
  if(result!=StoryCommitTransaction.Result.COMMITTED)throw new IllegalStateException("reset precondition failed");
  director.cancel(scene.runtimeId(),tick,"authored scene reset");director.forget(scene.runtimeId());reasons.remove(scene.runtimeId());sync();
  return "Scene reset with authored actions: "+id;
 }
 public boolean ready(String id,int phase){
  Scene scene=scenes.get(id);if(scene==null)return false;
  try{
   var event=StoryAuthority.definition(server,scene.phases().get(phase).event());
   if(!event.once()||!event.serverOnly()||!event.scenes().isEmpty()||!event.area().dimension().equals(scene.dimension()))throw new IllegalStateException("phase definition changed");
   Store store=store();if(store.error!=null)throw new IllegalStateException("Store read-only");
   if(phase>0&&!consistent(store.data,scene,phase-1)||phase==0&&phase(store.data,scene)!=-1){reasons.put(id,"phase cursor/Once mismatch");return false;}
   if(store.data.completedEvents.contains(event.key())){reasons.put(id,"phase already committed without matching cursor");return false;}
   if(!StoryRules.matches(store.data.storyVariables,event.conditions())){reasons.put(id,"story conditions false");return false;}
   int inspected=0;
   for(var player:server.getPlayerManager().getPlayerList()){
    if(!player.isAlive()||player.isSpectator()||!dev.whiteout.power.lobby.Party.active(player)||!player.getServerWorld().getRegistryKey().getValue().toString().equals(scene.dimension()))continue;
    if(++inspected>8)break;
    if(event.area().contains(scene.dimension(),player.getX(),player.getY(),player.getZ())&&StoryWorldConditions.evaluate(event.world(),new StoryWorldSource(player,store.data))==Truth.TRUE){reasons.remove(id);return true;}
   }
   reasons.put(id,"no eligible player in area or world conditions unknown/false");return false;
  }catch(Exception failure){reasons.put(id,"blocked: "+failure.getMessage());return false;}
 }
 public void enter(View view)throws Exception{advance(view);}
 public void advance(View view)throws Exception{
  Scene scene=scenes.get(view.id());if(!ready(view.id(),view.phase()))throw new IllegalStateException("phase conditions changed");
  var event=StoryAuthority.definition(server,scene.phases().get(view.phase()).event());
  var result=StoryCommitTransaction.apply(store(),event,s->{markers(s,scene,view.phase(),true,false,false);s.variables.put(scene.key("remaining"),StoryRules.Value.of((int)scene.phases().get(view.phase()).ticks()));});
  if(result!=StoryCommitTransaction.Result.COMMITTED)throw new IllegalStateException("phase not committed: "+result);sync();
 }
 public void complete(View view)throws Exception{Scene scene=scenes.get(view.id());flags(scene,false,false,true);reasons.remove(view.id());sync();}
 public void cancel(View view)throws Exception{Scene scene=scenes.get(view.id());flags(scene,false,false,false);reasons.put(view.id(),view.reason());sync();}
}
