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
   var legacy=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   legacy.remove("playerStory");legacy.remove("storyLobby");Files.writeString(path,legacy.toString());
   store=StoryStoreReader.open(path);check(store.error==null,"legacy file loaded");
   var event=Store.GSON.fromJson("""
    {"key":"dialogue_choice:test:0:1","once":true,"scope":"PLAYER","checkpoint":"camp",
    "area":{"dimension":"minecraft:overworld","radius":2},"conditions":[],
    "actions":[{"variable":"step","operation":"SET","value":{"type":"INT","integer":3}}]}
    """,StoryEventCatalog.Event.class);
   var pos=new ScopedStory.Position("minecraft:overworld",5,70,5,0,0);
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
