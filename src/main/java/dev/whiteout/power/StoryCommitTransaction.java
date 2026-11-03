package dev.whiteout.power;

import dev.whiteout.StoryRules;
import dev.whiteout.Store;
import java.util.*;

/** Existing WORLD variables and once keys; publication happens only after persistence succeeds. */
public final class StoryCommitTransaction {
 private StoryCommitTransaction(){}
 public enum Result { COMMITTED, ALREADY_COMPLETED, CONDITIONS_FALSE }
 @FunctionalInterface public interface Writer {
  void save(Map<String,StoryRules.Value> variables,Set<String> completed) throws Exception;
 }
 public static Result apply(Store store,StoryEventCatalog.Event event)throws Exception {
  if(store.error!=null)throw new IllegalStateException("story store is read-only");
  return apply(store.data.storyVariables,store.data.completedEvents,event,(variables,completed)->{
   var oldVariables=store.data.storyVariables;var oldCompleted=store.data.completedEvents;
   store.data.storyVariables=variables;store.data.completedEvents=completed;
   try{store.save();}
   catch(Exception failure){store.data.storyVariables=oldVariables;store.data.completedEvents=oldCompleted;throw failure;}
  });
 }
 public static Result apply(Map<String,StoryRules.Value> variables,Set<String> completed,
                            StoryEventCatalog.Event event,Writer writer)throws Exception {
  event.validate();Objects.requireNonNull(writer);
  if(event.once()&&completed.contains(event.key()))return Result.ALREADY_COMPLETED;
  if(!StoryRules.matches(variables,event.conditions()))return Result.CONDITIONS_FALSE;
  Map<String,StoryRules.Value> next=StoryRules.apply(variables,event.actions());
  StoryRules.validateVariables(next);
  Set<String> nextCompleted=new HashSet<>(completed);
  if(event.once())nextCompleted.add(event.key());
  writer.save(next,nextCompleted);
  return Result.COMMITTED;
 }
}
