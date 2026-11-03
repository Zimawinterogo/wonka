package dev.whiteout.power;

import com.google.gson.Gson;
import dev.whiteout.ObjectiveRules;
import dev.whiteout.Store;
import dev.whiteout.StoryRules;
import java.util.*;

/** Server-authored objective chains, using the original ObjectiveRules and Store fields. */
public final class StoryObjectiveActions {
 private static final Gson JSON=new Gson();
 private StoryObjectiveActions(){}
 public record Change(String id,ObjectiveRules.Event event,String text,ObjectiveRules.Objective definition,boolean reset){
  public Change(String id,ObjectiveRules.Event event,String text,ObjectiveRules.Objective definition){this(id,event,text,definition,false);}
  public void validate(){
   StoryRules.name(id);if(!reset)Objects.requireNonNull(event);
   if(reset&&(event!=null||definition==null))throw new IllegalArgumentException("reset needs only an inactive definition");
   if(text!=null&&text.length()>1024)throw new IllegalArgumentException("objective text");
   if(definition!=null){
    definition.validate();
    if(!id.equals(definition.id)||definition.status!=ObjectiveRules.Status.INACTIVE)throw new IllegalArgumentException("objective definition must start inactive");
   }
  }
 }
 public static void validate(List<Change> changes){
  if(changes==null||changes.size()>16)throw new IllegalArgumentException("max 16 objective actions");
  for(Change change:changes){Objects.requireNonNull(change);change.validate();}
 }
 /** Detached transaction workspace. Unrelated Store fields are never copied or replaced. */
 public static final class State {
  public Map<String,StoryRules.Value> variables;
  public Set<String> completed;
  public Map<String,ObjectiveRules.Objective> objectives;
  public String active,type,id,text;
  public long serial;
  private State(){}
  public static State capture(Store.Data data){
   State s=new State();s.variables=data.storyVariables;s.completed=data.completedEvents;s.objectives=data.objectives;
   s.active=data.activeObjective;s.serial=data.objectiveEventSerial;s.type=data.objectiveEventType;s.id=data.objectiveEventId;s.text=data.objectiveEventText;return s;
  }
  public State copy(){
   State s=new State();s.variables=StoryRules.apply(variables,List.of());s.completed=new HashSet<>(completed);s.objectives=new LinkedHashMap<>();
   objectives.forEach((key,value)->s.objectives.put(key,JSON.fromJson(JSON.toJson(value),ObjectiveRules.Objective.class)));
   s.active=active;s.serial=serial;s.type=type;s.id=id;s.text=text;return s;
  }
  public void publish(Store.Data data){
   data.storyVariables=variables;data.completedEvents=completed;data.objectives=objectives;data.activeObjective=active;
   data.objectiveEventSerial=serial;data.objectiveEventType=type;data.objectiveEventId=id;data.objectiveEventText=text;
  }
  public void apply(List<Change> changes){
   validate(changes);ObjectiveRules.validate(objectives,active);
   for(Change change:changes){
    if(change.reset()){
     var replacement=JSON.fromJson(JSON.toJson(change.definition()),ObjectiveRules.Objective.class);
     objectives.put(change.id(),replacement);if(active.equals(change.id()))active="";
     ObjectiveRules.initializeVars(variables,replacement);continue;
    }
    if(!objectives.containsKey(change.id())){
     if(change.definition()==null)throw new IllegalArgumentException("undefined objective: "+change.id());
     var definition=JSON.fromJson(JSON.toJson(change.definition()),ObjectiveRules.Objective.class);
     objectives.put(change.id(),definition);ObjectiveRules.initializeVars(variables,definition);
    }
    var objective=ObjectiveRules.require(objectives,change.id());
    // Repeated lifecycle events do not replay authored reward actions.
    if(change.event()==ObjectiveRules.Event.START&&objective.status==ObjectiveRules.Status.ACTIVE
      ||change.event()==ObjectiveRules.Event.COMPLETE&&objective.status==ObjectiveRules.Status.COMPLETED
      ||change.event()==ObjectiveRules.Event.FAIL&&objective.status==ObjectiveRules.Status.FAILED)continue;
    switch(change.event()){
     case START->active=ObjectiveRules.start(objectives,variables,active,change.id());
     case UPDATE->ObjectiveRules.update(objectives,variables,change.id(),Objects.requireNonNullElse(change.text(),""));
     case COMPLETE->{requireActive(objective);active=ObjectiveRules.complete(objectives,variables,active,change.id());}
     case FAIL->{requireActive(objective);active=ObjectiveRules.fail(objectives,variables,active,change.id());}
    }
    if(!objective.hidden){
     serial=Math.incrementExact(serial);type=change.event().name();id=change.id();
     text=change.event()==ObjectiveRules.Event.UPDATE&&!objective.updateText.isBlank()?objective.updateText:objective.title;
    }
   }
   ObjectiveRules.validate(objectives,active);StoryRules.validateVariables(variables);
  }
  private static void requireActive(ObjectiveRules.Objective objective){
   if(objective.status!=ObjectiveRules.Status.ACTIVE)throw new IllegalArgumentException("objective is not active: "+objective.id);
  }
 }
}
