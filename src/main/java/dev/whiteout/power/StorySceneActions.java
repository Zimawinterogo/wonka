package dev.whiteout.power;

import dev.whiteout.StoryRules;
import java.util.*;

/** Durable requests to the existing Director, committed with the triggering story event. */
public final class StorySceneActions {
 private StorySceneActions(){}
 public enum Operation { START, CANCEL }
 public record Change(String id,Operation operation){
  public void validate(){if(id==null||!id.matches("[a-z0-9_]{1,24}")||operation==null)throw new IllegalArgumentException("scene request");}
 }
 public static void validate(List<Change> changes){
  if(changes==null||changes.size()>8)throw new IllegalArgumentException("scene requests limit");
  Set<String> ids=new HashSet<>();for(Change c:changes){Objects.requireNonNull(c);c.validate();if(!ids.add(c.id()))throw new IllegalArgumentException("duplicate scene request");}
 }
 private static boolean flag(StoryObjectiveActions.State state,String key){
  var value=state.variables.get(key);if(value==null)return false;
  if(value.type!=StoryRules.Type.BOOL)throw new IllegalStateException("invalid scene marker");return value.bool;
 }
 public static void apply(StoryObjectiveActions.State state,List<Change> changes){
  validate(changes);
  for(Change c:changes){
   String prefix="crisis."+c.id()+".";
   if(c.operation()==Operation.START){
    if(!flag(state,prefix+"done")&&!flag(state,prefix+"active"))state.variables.put(prefix+"queued",StoryRules.Value.of(true));
   }else{
    state.variables.put(prefix+"queued",StoryRules.Value.of(false));state.variables.put(prefix+"active",StoryRules.Value.of(false));
   }
  }
 }
}
