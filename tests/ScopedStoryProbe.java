import dev.whiteout.Store;
import dev.whiteout.CheckpointRules;
import dev.whiteout.power.*;
import dev.whiteout.StoryStoreReader;
import java.nio.file.*;
import java.util.*;

public class ScopedStoryProbe {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run()throws Exception{
  // Force transformation of the actual editor save/load/delete entry points.
  check(Arrays.stream(dev.whiteout.CheckpointService.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("whiteout$persist")),"editor routing");
  var dir=Files.createTempDirectory(Path.of("."),"scoped-store-");
  var path=dir.resolve("world.json");var tmp=dir.resolve("world.json.tmp");
  var alice=UUID.randomUUID();var bob=UUID.randomUUID();
  try{
   var store=StoryStoreReader.open(path);store.save();
   for(var face:net.minecraft.util.math.Direction.Type.HORIZONTAL){
    var anchor=new net.minecraft.util.math.BlockPos(15,64,-16);
    var parts=new HashSet<net.minecraft.util.math.BlockPos>();
    for(int i=0;i<4;i++){
     var state=PowerDevices.STORY_BOARD.getDefaultState().with(dev.whiteout.power.lobby.BoardBlock.FACE,face).with(dev.whiteout.power.lobby.BoardBlock.PART,i);
     var p=dev.whiteout.power.lobby.BoardBlock.cell(anchor,face,i);
     check(parts.add(p)&&dev.whiteout.power.lobby.BoardBlock.root(p,state).equals(anchor),"four distinct cells share anchor");
    }
   }
   var card=new dev.whiteout.power.lobby.LobbyState.Card("Whiteout","Arrival","","","");
   dev.whiteout.power.lobby.LobbyState.edit(store,0,card);
   try{dev.whiteout.power.lobby.LobbyState.edit(store,0,card);throw new AssertionError("stale edit");}catch(IllegalStateException expected){}
   Files.createDirectory(tmp);
   try{dev.whiteout.power.lobby.LobbyState.startSolo(store,1,alice);throw new AssertionError("failed start");}catch(java.io.IOException expected){}
   Files.delete(tmp);check(!dev.whiteout.power.lobby.LobbyState.get(store).started(),"failed start rolled back");
   dev.whiteout.power.lobby.LobbyState.startSolo(store,1,alice);store=StoryStoreReader.open(path);
   check(dev.whiteout.power.lobby.LobbyState.get(store).first().equals(alice)&&store.data.completedEvents.contains(dev.whiteout.power.lobby.LobbyState.STARTED),"persisted start and host");
   try{dev.whiteout.power.lobby.LobbyState.startSolo(store,2,bob);throw new AssertionError("duplicate start");}catch(IllegalStateException expected){}
   store.data.completedEvents.add("existing:story");
   dev.whiteout.power.lobby.LobbyState.resetLaunch(store);store=StoryStoreReader.open(path);
   check(!dev.whiteout.power.lobby.LobbyState.get(store).started()&&dev.whiteout.power.lobby.LobbyState.get(store).card().equals(card)
    &&store.data.completedEvents.contains("existing:story")&&!store.data.completedEvents.contains(dev.whiteout.power.lobby.LobbyState.STARTED),"unlock board only");
   var legacy=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   legacy.remove("playerStory");legacy.remove("storyLobby");Files.writeString(path,legacy.toString());
   store=StoryStoreReader.open(path);check(store.error==null,"legacy file loaded");
   var event=Store.GSON.fromJson("""
    {"key":"dialogue_choice:test:0:1","once":true,"scope":"PLAYER","checkpoint":"camp",
    "area":{"dimension":"minecraft:overworld","radius":2},"conditions":[],
    "actions":[{"variable":"step","operation":"SET","value":{"type":"INT","integer":3}}]}
    """,StoryEventCatalog.Event.class);
   var pos=new ScopedStory.Position("minecraft:overworld",5,70,5,0,0);
   var token=UUID.randomUUID();
   var assembly=new dev.whiteout.power.lobby.PhotoTransfer.Assembly(token,7,2);
   var pixels=new byte[dev.whiteout.power.lobby.PhotoTransfer.CHUNK];
   try{assembly.add(new dev.whiteout.power.lobby.PhotoTransfer.Part(token,7,1,0,pixels));throw new AssertionError("mixed photo");}catch(IllegalArgumentException expected){}
   for(int i=0;i<dev.whiteout.power.lobby.PhotoTransfer.COUNT;i++)
    check(assembly.add(new dev.whiteout.power.lobby.PhotoTransfer.Part(token,7,2,i,pixels))==(i+1==dev.whiteout.power.lobby.PhotoTransfer.COUNT),"complete photo only");
   var before=Files.readString(path);
   Files.createDirectory(tmp);
   try{ScopedStory.apply(store,alice,event,pos);throw new AssertionError("failed save accepted");}catch(java.io.IOException expected){}
   check(before.equals(Files.readString(path))&&ScopedStory.read(store.data,ScopedStory.Scope.PLAYER,alice).completedEvents.isEmpty(),"failed save rolled back");
   Files.delete(tmp);ScopedStory.apply(store,alice,event,pos);
   store=StoryStoreReader.open(path);
   check(store.error==null&&ScopedStory.apply(store,alice,event,pos)==StoryCommitTransaction.Result.ALREADY_COMPLETED,"restart retains personal Once");
   var own=ScopedStory.snapshot(store,alice);var other=ScopedStory.snapshot(store,bob);
   check(own.storyVariables.get("step").integer==3&&own.checkpoints.containsKey("camp"),"personal state reaches owner");
   check(!other.storyVariables.containsKey("step")&&!other.checkpoints.containsKey("camp")&&!Store.GSON.toJson(other).contains(alice.toString()),"no other player state");
   check(!store.data.completedEvents.contains(event.key())&&ScopedStory.apply(store,bob,event,pos)==StoryCommitTransaction.Result.COMMITTED,"independent Once");
   var duo=new dev.whiteout.power.lobby.LobbyState(card,alice,bob,true,3);
   var fallback=new Narrative.Ending("default","Возвращение",1,List.of(),"exit",List.of(),null);
   var spec=new SharedScene.Spec("last_train",200,"exit_scene",List.of());
   var conditional=new Narrative.Ending("together","Поезд",2,List.of(new Narrative.Requirement(Narrative.Source.SECOND,List.of(),List.of(event.key()))),"exit",List.of(),spec);
   check(Narrative.chooseEnding(store,duo,List.of(fallback,conditional))==conditional,"second player's private progress selects priority ending");
   check(Narrative.chooseEnding(store,new dev.whiteout.power.lobby.LobbyState(card,alice,null,true,3),List.of(fallback,conditional))==fallback,"Solo cannot borrow a missing role");
   var scene=(SharedScene.Access)(Object)store.data;scene.whiteout$scene(new SharedScene.Playing(token,spec,98));
   var grants=((SceneDelivery.Access)(Object)store.data).whiteout$grants();grants.add(new SceneDelivery.Grant(bob,"whiteout.grant."+token,new SceneDelivery.Action(Narrative.Role.SECOND,"minecraft:paper",1,null)));
   store.save();store=StoryStoreReader.open(path);
   check(store.error==null&&((SharedScene.Access)(Object)store.data).whiteout$scene().elapsed()==98,"scene cursor survives restart");
   check(((SceneDelivery.Access)(Object)store.data).whiteout$grants().size()==1,"pending reward survives restart");
   var filtered=ScopedStory.snapshot(store,alice);
   check(((SceneDelivery.Access)(Object)filtered).whiteout$grants().isEmpty()&&((SharedScene.Access)(Object)filtered).whiteout$scene()==null,"private finale and receipts are filtered");
   var view=ScopedStory.checkpointView(store,alice,"camp");
   check(view.checkpoints.get("camp").completedEvents.contains(event.key()),"checkpoint captured after Once");
   CheckpointRules.restore(view,view.checkpoints.get("camp"));ScopedStory.persistCheckpoint(store,alice,view);
   check(ScopedStory.epoch(store.data,alice)>ScopedStory.epoch(store.data,bob),"only owner's epoch advances");
   store.save();
   Files.writeString(path,"{broken");
   var corrupt=StoryStoreReader.open(path);
   check(corrupt.error!=null&&StoryRecovery.status(corrupt,path).contains("backup: valid"),"diagnostic");
   StoryRecovery.recover(corrupt,path);check(corrupt.error!=null,"restart required");
   store=StoryStoreReader.open(path);
   check(store.error==null&&ScopedStory.apply(store,bob,event,pos)==StoryCommitTransaction.Result.ALREADY_COMPLETED,"recovery kept backup ledger");
   check(ScopedStory.apply(store,alice,event,pos)==StoryCommitTransaction.Result.ALREADY_COMPLETED,"recovery kept both players");
   try{StoryRecovery.recover(store,path);throw new AssertionError("healthy primary overwritten");}catch(java.io.IOException expected){}
   Files.delete(path);check(StoryStoreReader.open(path).error!=null,"missing primary with backup locked");
  }finally{
   try(var files=Files.list(dir)){for(var f:files.toList())Files.delete(f);}Files.delete(dir);
  }
  System.out.println("PASS Fabric scoped Store: legacy migration, rollback, restart, private snapshots, checkpoints, Once and recovery");
 }
}
