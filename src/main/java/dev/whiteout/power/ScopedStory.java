package dev.whiteout.power;

import dev.whiteout.CheckpointRules;
import dev.whiteout.ObjectiveRules;
import dev.whiteout.Store;
import dev.whiteout.StoryRules;
import java.util.*;

/** WORLD uses the legacy fields. PLAYER records are serialized inside the same Store.Data. */
public final class ScopedStory {
 private ScopedStory(){}
 public enum Scope { WORLD, PLAYER }
 public interface Players {Map<UUID,Profile> whiteout$players();}
 public record Position(String dimension,double x,double y,double z,float yaw,float pitch){}
 public record Profile(StoryObjectiveActions.State story,Map<String,CheckpointRules.Checkpoint> checkpoints,long epoch,long noticeWorldSerial){
  Store.Data view(){
   var view=new Store.Data();story.publish(view);view.checkpoints=checkpoints;view.storyResetEpoch=epoch;return view;
  }
  void validate(){
   Objects.requireNonNull(story.completed);Objects.requireNonNull(story.type);Objects.requireNonNull(story.text);Objects.requireNonNull(story.id);
   if(story.serial<0||story.completed.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("invalid personal progress");
   StoryRules.validateVariables(story.variables);ObjectiveRules.validate(story.objectives,story.active);CheckpointRules.validateAll(checkpoints);
  }
 }
 static Map<UUID,Profile> profiles(Store.Data data){return ((Players)(Object)data).whiteout$players();}
 public static void validate(Store.Data data){
  var records=profiles(data);
  if(records==null||records.containsKey(null))throw new IllegalArgumentException("invalid player story table");
  records.forEach((id,profile)->{try{profile.validate();}catch(RuntimeException cause){throw new IllegalArgumentException("invalid story for "+id,cause);}});
 }
 public static Store.Data read(Store.Data data,Scope scope,UUID player){
  if(scope==Scope.WORLD)return data;
  var profile=profiles(data).get(Objects.requireNonNull(player));
  return profile==null?new Store.Data():profile.view();
 }
 public static StoryCommitTransaction.Result apply(Store store,UUID player,StoryEventCatalog.Event event,Position position)throws Exception{
  if(store.error!=null)throw new IllegalStateException(store.error);
  if(event.scope()==Scope.WORLD&&event.checkpoint()==null)return StoryCommitTransaction.apply(store,event);
  var target=read(store.data,event.scope(),player);
  return StoryCommitTransaction.stage(target,event,s->{},()->{
   var previousCheckpoints=target.checkpoints;
   Profile previous=null;
   if(event.checkpoint()!=null){
    var point=Objects.requireNonNull(position);
    var captured=CheckpointRules.capture(event.checkpoint(),point.dimension(),point.x(),point.y(),point.z(),point.yaw(),point.pitch(),target);
    target.checkpoints=new TreeMap<>(target.checkpoints);target.checkpoints.put(captured.id,captured);
   }
   boolean personal=event.scope()==Scope.PLAYER;
   try{
    if(personal){
     previous=profiles(store.data).get(player);
     long notice=previous==null||target.objectiveEventSerial!=previous.story.serial?store.data.objectiveEventSerial:previous.noticeWorldSerial;
     profiles(store.data).put(player,new Profile(StoryObjectiveActions.State.capture(target),target.checkpoints,target.storyResetEpoch,notice));
    }
    store.save();
   }catch(Exception failure){
    target.checkpoints=previousCheckpoints;
    if(personal){if(previous==null)profiles(store.data).remove(player);else profiles(store.data).put(player,previous);}
    throw failure;
   }
  });
 }
 public static long epoch(Store.Data data,UUID player){
  var profile=profiles(data).get(player);return data.storyResetEpoch+(profile==null?0:profile.epoch());
 }
 /** The editor still owns permission checks, teleportation, metadata and rollback. */
 public static Store.Data checkpointView(Store store,UUID player,String id){
  var profile=profiles(store.data).get(player);
  if(profile==null||!profile.checkpoints.containsKey(id))return null;
  var target=profile.view();profile.story.copy().publish(target);
  target.checkpoints=new TreeMap<>(target.checkpoints);return target;
 }
 public static void persistCheckpoint(Store store,UUID player,Store.Data target)throws java.io.IOException{
  var records=profiles(store.data);var old=records.get(player);
  target.completedEvents.addAll(old.story.completed);
  records.put(player,new Profile(StoryObjectiveActions.State.capture(target),target.checkpoints,target.storyResetEpoch,
   target.objectiveEventSerial==old.story.serial?old.noticeWorldSerial:store.data.objectiveEventSerial));
  try{store.save();}catch(java.io.IOException|RuntimeException failure){records.put(player,old);throw failure;}
 }
 /** Apply only this recipient's progress over the normal WORLD snapshot. */
 public static Store.Data snapshot(Store store,UUID player){
  var result=store.networkSnapshot();
  var profile=profiles(store.data).get(player);
  if(profile==null)return result;
  var state=profile.story();
  result.storyVariables.putAll(state.variables);result.completedEvents.addAll(state.completed);
  result.objectives.putAll(state.objectives);
  if(!state.active.isEmpty())result.activeObjective=state.active;
  result.storyResetEpoch=Math.addExact(result.storyResetEpoch,profile.epoch());
  if(state.serial>0&&profile.noticeWorldSerial()==result.objectiveEventSerial){
   result.objectiveEventType=state.type;result.objectiveEventId=state.id;result.objectiveEventText=state.text;
  }
  result.objectiveEventSerial=Math.addExact(result.objectiveEventSerial,state.serial);
  profile.checkpoints().forEach((id,checkpoint)->result.checkpoints.put(id,checkpoint.metadataCopy()));
  return result;
 }
}
