package dev.whiteout.power;

import java.util.*;
import static dev.whiteout.power.WinterCrisisConditions.*;

/** Bounded, server-thread-owned coordinator. No story state, entity references or persistence. */
public final class WinterCrisisDirector implements AutoCloseable {
 public enum State { IDLE, ARMED, ACTIVE, COOLDOWN, COMPLETED, CANCELLED }
 public enum Scope { PLAYER, GROUP, WORLD }
 public enum CancelPolicy { REQUIRE_TRUE_WHILE_ACTIVE, CHECK_AT_START }
 public enum CompletionPolicy { AFTER_DURATION }
 public record Target(Scope scope,String dimension,Set<UUID> players){
  public Target {
   Objects.requireNonNull(scope);Objects.requireNonNull(dimension);players=Set.copyOf(players);
   if(dimension.isBlank()||players.size()>8||scope==Scope.PLAYER&&players.size()!=1||scope==Scope.GROUP&&players.isEmpty()
     ||scope==Scope.WORLD&&!players.isEmpty())throw new IllegalArgumentException("Invalid target");
  }
 }
 public record Spec(String id,Target target,Condition precondition,Condition trigger,int priority,
                    int maxRuns,long holdTicks,long armTimeout,long activeTicks,long cooldownTicks,
                    CancelPolicy cancellation,CompletionPolicy completion,List<Long> phases){
  public Spec(String id,Target target,Condition precondition,Condition trigger,int priority,int maxRuns,long holdTicks,long armTimeout,long activeTicks,long cooldownTicks,CancelPolicy cancellation,CompletionPolicy completion){
   this(id,target,precondition,trigger,priority,maxRuns,holdTicks,armTimeout,activeTicks,cooldownTicks,cancellation,completion,List.of(activeTicks));
  }
  public Spec {
   if(id==null||!id.matches("[a-z0-9_.:-]{1,64}"))throw new IllegalArgumentException("Invalid scenario ID");
   Objects.requireNonNull(target);Objects.requireNonNull(precondition);Objects.requireNonNull(trigger);
   Objects.requireNonNull(cancellation);Objects.requireNonNull(completion);
   phases=List.copyOf(phases);
   if(phases.isEmpty()||phases.size()>6||phases.stream().anyMatch(v->v==null||v<1||v>72000)||phases.stream().mapToLong(Long::longValue).sum()!=activeTicks)throw new IllegalArgumentException("Invalid phases");
   if(priority<0||priority>100||maxRuns<1||maxRuns>100||holdTicks<0||holdTicks>12000||armTimeout<holdTicks+40
    ||armTimeout>72000||activeTicks<1||activeTicks>72000||cooldownTicks<0||cooldownTicks>72000)throw new IllegalArgumentException("Invalid timing/repeats");
  }
 }
 public record View(String id,Target target,State state,int runs,long changed,String reason,int phase){}
 public record Event(long tick,String id,State state,String reason){}
 /** Explicit integration boundary. Implement with an existing, server-safe story API only.
  * Never replay actions on restart. Implementations must handle partial failure/cancellation.
  * Current adapter is diagnostic only; no arbitrary commands or client scene calls. */
 public interface Actions {
  void enter(View view) throws Exception;
  default boolean ready(View nextPhase) throws Exception {return true;}
  default void phase(View view) throws Exception {}
  default void complete(View view) throws Exception {}
  default void cancel(View view) throws Exception {}
  default void cancelPending(View view) throws Exception {}
 }
 @FunctionalInterface public interface Presence {boolean valid(Target target) throws Exception;}
 private static final int MAX_INSTANCES=16,MAX_ACTIVE=4,MAX_CHECKS=4;
 private final Map<String,Instance> instances=new LinkedHashMap<>();
 private final Deque<Event> events=new ArrayDeque<>();
 private Actions actions;
 private boolean enabled,closed;
 private long lastTick=Long.MIN_VALUE;
 private static final class Instance {
  final Spec spec; State state=State.IDLE; int runs,phase; long changed,checked=Long.MIN_VALUE,ended;
  String reason="registered";Tracker tracker;
  Instance(Spec spec){this.spec=spec;}
  View view(){return new View(spec.id(),spec.target(),state,runs,changed,reason,phase);}
 }
 public WinterCrisisDirector(Actions actions){this.actions=Objects.requireNonNull(actions);}
 public boolean enabled(){return enabled&&!closed;}
 public void enabled(boolean value,long tick){
  if(closed)throw new IllegalStateException("Closed director");
  if(!value)cancelAll(tick,"disabled");enabled=value;
 }
 public boolean register(Spec spec){
  Objects.requireNonNull(spec);
  if(closed||instances.size()>=MAX_INSTANCES||instances.containsKey(spec.id()))return false;
  instances.put(spec.id(),new Instance(spec));return true;
 }
 public boolean arm(String id,long tick){
  Instance i=instances.get(id);
  if(!enabled()||i==null||i.state!=State.IDLE||i.runs>=i.spec.maxRuns())return false;
  i.tracker=new Tracker(i.spec.holdTicks(),0,50);transition(i,State.ARMED,tick,"manual request");return true;
 }
 public List<View> status(){return instances.values().stream().map(Instance::view).toList();}
 /** Restore only a previously committed phase. Caller validates durable author data first.
  * Time spent offline is paused; the current phase receives its full duration again. */
 public boolean restoreActive(String id,int phase,long tick){
  Instance i=instances.get(id);if(i==null||phase<0||phase>=i.spec.phases().size())return false;
  return restoreActive(id,phase,tick,i.spec.phases().get(phase));
 }
 public boolean restoreActive(String id,int phase,long tick,long remaining){
  Instance i=instances.get(id);
  if(!enabled()||i==null||i.state!=State.IDLE||phase<0||phase>=i.spec.phases().size()||instances.values().stream().filter(v->v.state==State.ACTIVE).count()>=MAX_ACTIVE)return false;
  if(remaining<0||remaining>i.spec.phases().get(phase))return false;
  i.phase=phase;i.runs=1;transition(i,State.ACTIVE,tick,"restored committed phase");i.changed=tick-(i.spec.phases().get(phase)-remaining);return true;
 }
 public boolean forget(String id){
  Instance i=instances.get(id);if(i==null||i.state==State.ACTIVE||i.state==State.ARMED)return false;
  instances.remove(id);return true;
 }
 public List<Event> history(){return List.copyOf(events);}
 public Optional<View> status(String id){return Optional.ofNullable(instances.get(id)).map(Instance::view);}
 private void transition(Instance i,State state,long tick,String reason){
  reason=Objects.requireNonNullElse(reason,"unspecified");if(reason.length()>160)reason=reason.substring(0,160);
  i.state=state;i.changed=tick;i.reason=reason;
  if(events.size()==64)events.removeFirst();events.addLast(new Event(tick,i.spec.id(),state,reason));
 }
 public boolean cancel(String id,long tick,String reason){
  Instance i=instances.get(id);if(i==null||i.state!=State.ARMED&&i.state!=State.ACTIVE)return false;
  boolean active=i.state==State.ACTIVE;i.tracker=null;i.ended=tick;
  transition(i,State.CANCELLED,tick,reason);
  if(active)try{actions.cancel(i.view());}catch(Exception failure){i.reason="cancel action failed: "+failure.getClass().getSimpleName();}
  else try{actions.cancelPending(i.view());}catch(Exception failure){i.reason="pending cancel failed: "+failure.getClass().getSimpleName();}
  return true;
 }
 public void cancelAll(long tick,String reason){for(String id:List.copyOf(instances.keySet()))cancel(id,tick,reason);}
 public void departed(UUID player,long tick){
  for(Instance i:instances.values())if(i.spec.target().players().contains(player))cancel(i.spec.id(),tick,"player disconnected");
 }
 /** Operator reset deliberately discards temporary limits/history; never modifies story saves. */
 public void reset(long tick){cancelAll(tick,"operator reset");instances.clear();events.clear();lastTick=Long.MIN_VALUE;}
 public void tick(long tick,Source source,Presence presence){
  if(!enabled()||lastTick!=Long.MIN_VALUE&&tick-lastTick<10)return;
  lastTick=tick;
  List<Instance> ordered=instances.values().stream()
   .sorted(Comparator.comparingLong((Instance i)->i.checked).thenComparing(Comparator.comparingInt((Instance i)->i.spec.priority()).reversed())
    .thenComparing(i->i.spec.id())).toList();
  int checked=0;
  try(Cycle cycle=new Cycle(source,64)){
   for(Instance i:ordered){
    if(i.state==State.IDLE)continue;
    if(++checked>MAX_CHECKS)break;i.checked=tick;
    if(i.state==State.COMPLETED||i.state==State.CANCELLED){
     if(i.runs<i.spec.maxRuns())transition(i,State.COOLDOWN,tick,"repeat delay");
     continue;
    }
    if(i.state==State.COOLDOWN){if(tick-i.ended>=i.spec.cooldownTicks())transition(i,State.IDLE,tick,"ready for manual request");continue;}
    try{
     if(!presence.valid(i.spec.target())){cancel(i.spec.id(),tick,"target absent or dimension changed");continue;}
     Truth value=i.state==State.ACTIVE&&i.spec.cancellation()==CancelPolicy.CHECK_AT_START?Truth.TRUE:cycle.evaluate(new All(List.of(i.spec.precondition(),i.spec.trigger())));
     if(i.state==State.ARMED){
      if(tick-i.changed>=i.spec.armTimeout()){cancel(i.spec.id(),tick,"conditions timed out");continue;}
      Stabilized stable=i.tracker.update(value,tick);
      if(stable.value()==Truth.TRUE&&instances.values().stream().filter(v->v.state==State.ACTIVE).count()<MAX_ACTIVE){
       i.runs++;i.phase=0;i.tracker=null;transition(i,State.ACTIVE,tick,"conditions satisfied");actions.enter(i.view());
      }
     }else if(i.state==State.ACTIVE){
      if(value!=Truth.TRUE){cancel(i.spec.id(),tick,"active condition "+value);continue;}
      if(tick-i.changed>=i.spec.phases().get(i.phase)){
       if(i.phase+1<i.spec.phases().size()){
        var next=new View(i.spec.id(),i.spec.target(),State.ACTIVE,i.runs,tick,"phase ready",i.phase+1);
        if(actions.ready(next)){actions.phase(next);i.phase++;transition(i,State.ACTIVE,tick,"phase "+i.phase+" committed");}
       }else{actions.complete(i.view());i.ended=tick;transition(i,State.COMPLETED,tick,"duration completed");}
      }
     }
    }catch(Exception failure){cancel(i.spec.id(),tick,"scenario failed: "+failure.getClass().getSimpleName());}
   }
  }
 }
 public void close(){if(closed)return;cancelAll(lastTick==Long.MIN_VALUE?0:lastTick,"server stopped");enabled=false;instances.clear();events.clear();actions=null;closed=true;}
}
