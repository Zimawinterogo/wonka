package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.StoryRules;
import dev.whiteout.power.lobby.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import java.util.*;

public final class Narrative{
 private Narrative(){}
 public enum Role{ANY,FIRST,SECOND}
 public enum Source{WORLD,FIRST,SECOND}
 public enum Mode{BOTH,SOLO,DUO}
 public enum Visibility{SHARED,PERSONAL,SECRET}
 public record Requirement(Source source,List<StoryRules.Condition> conditions,List<String> completed){
  public Requirement{Objects.requireNonNull(source);conditions=conditions==null?List.of():List.copyOf(conditions);completed=completed==null?List.of():List.copyOf(completed);
   StoryRules.validate(conditions,List.of());if(completed.size()>16||completed.stream().anyMatch(k->!key(k)))throw new IllegalArgumentException("requirements");}
 }
 public record Ending(String id,String title,int priority,List<Requirement> when,String event){
  public Ending{if(id==null||!id.matches("[a-z0-9_]{1,32}")||title==null||title.isBlank()||title.length()>100||!key(event))throw new IllegalArgumentException("ending");
   when=when==null?List.of():List.copyOf(when);if(when.size()>16)throw new IllegalArgumentException("ending requirements");}
 }
 public record Policy(Mode mode,Role actor,Role recipient,Visibility visibility,boolean together,String voteGroup,
                      List<Requirement> when,String firstDialogue,String secondDialogue,boolean waitForReaders,List<Ending> endings,SharedScene.Spec sharedScene){
  public Policy{
   mode=Objects.requireNonNullElse(mode,Mode.BOTH);actor=Objects.requireNonNullElse(actor,Role.ANY);recipient=Objects.requireNonNullElse(recipient,Role.ANY);
   visibility=Objects.requireNonNullElse(visibility,Visibility.SHARED);
   when=when==null?List.of():List.copyOf(when);endings=endings==null?List.of():List.copyOf(endings);
   if(when.size()>16||endings.size()>16||voteGroup!=null&&!key(voteGroup)||firstDialogue!=null&&!key(firstDialogue)||secondDialogue!=null&&!key(secondDialogue))throw new IllegalArgumentException("narrative");
   if(endings.stream().map(Ending::id).distinct().count()!=endings.size()||endings.stream().map(Ending::priority).distinct().count()!=endings.size())throw new IllegalArgumentException("duplicate ending id or priority");
  }
  void validate(StoryEventCatalog.Event e){
   if(sharedScene!=null&&(e.scope()!=ScopedStory.Scope.WORLD||!e.once()||visibility!=Visibility.SHARED||!endings.isEmpty()||waitForReaders))throw new IllegalArgumentException("shared scene needs an exclusive WORLD Once entrance");
   if(visibility!=Visibility.SHARED&&(recipient==Role.ANY||e.scope()!=ScopedStory.Scope.PLAYER)||visibility==Visibility.SECRET&&actor!=recipient||
      together&&(e.area()==null||!e.once())||voteGroup!=null&&(actor!=Role.ANY||!e.once()||e.scope()!=ScopedStory.Scope.WORLD)||
      !endings.isEmpty()&&(!e.once()||e.scope()!=ScopedStory.Scope.WORLD||e.checkpoint()!=null)||waitForReaders&&firstDialogue==null&&secondDialogue==null)
    throw new IllegalArgumentException("inconsistent narrative policy");
  }
 }
 private static boolean key(String value){return value!=null&&value.matches("[a-zA-Z0-9_.:/-]{1,128}");}
 private static UUID id(LobbyState d,Role role,UUID actor){return switch(role){case FIRST->d.first();case SECOND->d.second();case ANY->actor;};}
 public static UUID owner(ServerPlayerEntity p,StoryEventCatalog.Event e){
  var n=e.narrative();if(n==null||n.recipient==Role.ANY)return p.getUuid();
  var d=LobbyState.get(Store.get(p.getServer()));return d==null?null:id(d,n.recipient,p.getUuid());
 }
 static boolean matches(Store store,LobbyState d,List<Requirement> terms){
  for(var term:terms){
   UUID who=switch(term.source){case WORLD->null;case FIRST->d.first();case SECOND->d.second();};
   if(who==null&&term.source!=Source.WORLD)return false;
   var src=ScopedStory.read(store.data,who==null?ScopedStory.Scope.WORLD:ScopedStory.Scope.PLAYER,who);
   if(!src.completedEvents.containsAll(term.completed)||!StoryRules.matches(src.storyVariables,term.conditions))return false;
  }return true;
 }
 public static boolean allowed(ServerPlayerEntity p,StoryEventCatalog.Event e){
  var n=e.narrative();var store=Store.get(p.getServer());var d=LobbyState.get(store);
  if(d!=null&&d.started()&&(store.data.completedEvents.contains(FINISHED)||SharedScene.current(store)!=null||!Party.active(p)||!DialogueBarrier.admits(p)))return false;
  if(n==null)return true;
  if(d==null||!d.started()||!Party.member(d,p.getUuid())||!p.getUuid().equals(id(d,n.actor,p.getUuid()))||owner(p,e)==null||
     n.mode!=Mode.BOTH&&!n.mode.name().equals(d.mode().name())||!matches(store,d,n.when))return false;
  if(n.together){
   for(UUID who:new UUID[]{d.first(),d.second()}){if(who==null)continue;var q=p.getServer().getPlayerManager().getPlayer(who);
    if(q==null){if(Party.paused(p.getServer()))return false;continue;}
    if(!q.isAlive()||q.isSpectator()||!e.area().contains(q.getServerWorld().getRegistryKey().getValue().toString(),q.getX(),q.getY(),q.getZ()))return false;}
  }
  return true;
 }
 public static final String FINISHED="whiteout:story_finished";
 static StoryCommitTransaction.Result commit(ServerPlayerEntity p,StoryEventCatalog.Event e,ScopedStory.Position pos)throws Exception{
  var store=Store.get(p.getServer());var n=e.narrative();var d=LobbyState.get(store);
  if(n!=null&&n.sharedScene()!=null)return SharedScene.begin(p,store,e,pos);
  if(n==null||n.endings.isEmpty())return ScopedStory.apply(store,owner(p,e),e,pos);
  var ending=n.endings.stream().filter(end->matches(store,d,end.when)).max(Comparator.comparingInt(Ending::priority)).orElseThrow(()->new IllegalStateException("no eligible ending"));
  var finalEvent=StoryAuthority.definition(p.getServer(),ending.event);
  if(!finalEvent.serverOnly()||!finalEvent.once()||finalEvent.scope()!=ScopedStory.Scope.WORLD||finalEvent.narrative()!=null||finalEvent.world()!=null||
    !finalEvent.area().contains(pos.dimension(),pos.x(),pos.y(),pos.z()))throw new IllegalStateException("invalid ending definition");
  for(var scene:finalEvent.scenes())if(!CrisisRuntime.requestable(p.getServer(),scene))throw new IllegalStateException("ending scene missing");
  return StoryCommitTransaction.apply(store,e,state->{
   if(!state.completed.add(FINISHED)||!state.completed.add(finalEvent.key())||!StoryRules.matches(state.variables,finalEvent.conditions()))throw new IllegalStateException("ending unavailable");
   state.variables=StoryRules.apply(state.variables,finalEvent.actions());state.apply(finalEvent.objectives());StorySceneActions.apply(state,finalEvent.scenes());
   state.completed.add("whiteout:ending/"+ending.id);
  });
 }
}
