import dev.whiteout.power.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import io.netty.buffer.Unpooled;

public class DialogueRouteServerProbe {
 private static void testDialoguePersistence()throws Exception{
  var directory=java.nio.file.Files.createTempDirectory("whiteout-duo-save-");
  var path=directory.resolve("zones.json");
  try{
   var store=dev.whiteout.StoryStoreReader.open(path);
   var access=(DialogueBarrier.Access)(Object)store.data;
   var player=java.util.UUID.randomUUID();
   var id=java.util.UUID.randomUUID();
   var records=new DialogueBarrier.Readers();
   var secondId=java.util.UUID.randomUUID();
   var first=new DialogueBarrier.Reading(id,"story_first",false,3);
   var queued=first.append(new DialogueBarrier.Reading(secondId,"story_second",true,3));
   if(!first.queue().isEmpty()||!queued.blocks())throw new AssertionError("Queue mutates prior save or ignores queued barrier");
   if(queued.advance(secondId)!=queued)throw new AssertionError("Out of order acknowledgement consumed dialogue");
   records.put(player,queued);
   access.whiteout$readers(records);
   store.save();
   var loaded=dev.whiteout.StoryStoreReader.open(path);
   var resumed=((DialogueBarrier.Access)(Object)loaded.data).whiteout$readers().get(player);
   if(resumed==null||!id.equals(resumed.id())||!resumed.script().equals("story_first")||resumed.revision()!=3||resumed.waitForReaders()||resumed.queue().size()!=1)
    throw new AssertionError("Duo dialogue barrier lost after Store reopen");
   if(!((DialogueBarrier.Access)(Object)loaded.networkSnapshot()).whiteout$readers().isEmpty())
    throw new AssertionError("Pending Duo dialogues leaked into public snapshot");
   var advanced=resumed.advance(id);
   if(advanced==null||!advanced.id().equals(secondId)||!advanced.script().equals("story_second")||!advanced.blocks()||advanced.advance(id)!=advanced)
    throw new AssertionError("FIFO delivery or duplicate acknowledgement guard failed");
   ((DialogueBarrier.Access)(Object)loaded.data).whiteout$readers().put(player,advanced);
   loaded.save();
   loaded=dev.whiteout.StoryStoreReader.open(path);
   var last=((DialogueBarrier.Access)(Object)loaded.data).whiteout$readers().get(player);
   if(!last.id().equals(secondId)||last.advance(secondId)!=null)throw new AssertionError("Queue advancement not durable");
   // Pre-fix 3.13 records have no queue property.
   var legacy=dev.whiteout.Store.GSON.fromJson("{\"id\":\""+id+"\",\"script\":\"legacy\",\"waitForReaders\":true,\"revision\":3}",DialogueBarrier.Reading.class);
   if(!legacy.queue().isEmpty()||!legacy.blocks())throw new AssertionError("Legacy pending dialogue migration failed");
   var full=first;
   for(int i=0;i<255;i++)full=full.append(new DialogueBarrier.Reading(java.util.UUID.randomUUID(),"queued",true,3));
   try{full.append(new DialogueBarrier.Reading(java.util.UUID.randomUUID(),"overflow",true,3));throw new AssertionError("Unbounded queue");}catch(IllegalArgumentException expected){}
   if(full.queue().size()!=255)throw new AssertionError("Overflow damaged pending dialogues");
   var cleared=new DialogueBarrier.Readers();
   ((DialogueBarrier.Access)(Object)loaded.data).whiteout$readers(cleared);
   loaded.save();
   if(!((DialogueBarrier.Access)(Object)dev.whiteout.StoryStoreReader.open(path).data).whiteout$readers().isEmpty())
    throw new AssertionError("Completed acknowledgement revived on restart");
  }finally{
   java.nio.file.Files.deleteIfExists(path.resolveSibling("zones.json.tmp"));
   java.nio.file.Files.deleteIfExists(path.resolveSibling("zones.json.bak"));
   java.nio.file.Files.deleteIfExists(path);
   java.nio.file.Files.deleteIfExists(directory);
  }
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  DialogueRouteNetwork.register();
  StoryAuthority.register();
  ScopedStoryProbe.run();DuoTestProbe.run();testDialoguePersistence();
  long guards=java.util.Arrays.stream(dev.whiteout.net.WhiteoutNetwork.class.getDeclaredMethods())
   .filter(m->m.getName().contains("whiteout$before")).count();
  if(guards!=1)throw new AssertionError("Server story authority Mixin was not applied: "+guards);
  var b=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
  try{
   var story=new StoryAuthorityNetwork.Request(123,"cutscene_trigger:bridge");StoryAuthorityNetwork.Request.CODEC.encode(b,story);if(!story.equals(StoryAuthorityNetwork.Request.CODEC.decode(b)))throw new AssertionError();
   var manifest=new TriggerAuthority.Notice(true,"cutscene_trigger:a\ndialogue_choice:a:0:0");
   TriggerAuthority.Notice.CODEC.encode(b,manifest);
   if(!manifest.equals(TriggerAuthority.Notice.CODEC.decode(b)))throw new AssertionError("trigger manifest codec");
   for(var outcome:StoryReceipts.Outcome.values()){
    var receipt=new StoryAuthorityNetwork.Reply(123,outcome);StoryAuthorityNetwork.Reply.CODEC.encode(b,receipt);if(!receipt.equals(StoryAuthorityNetwork.Reply.CODEC.decode(b)))throw new AssertionError();
   }
   var resolve=new DialogueRouteNetwork.Resolve(42,"lake");DialogueRouteNetwork.Resolve.CODEC.encode(b,resolve);if(!resolve.equals(DialogueRouteNetwork.Resolve.CODEC.decode(b)))throw new AssertionError();
   var result=new DialogueRouteNetwork.Result(42,"диалог","fallback","");DialogueRouteNetwork.Result.CODEC.encode(b,result);if(!result.equals(DialogueRouteNetwork.Result.CODEC.decode(b)))throw new AssertionError();
   var edit=new DialogueRouteNetwork.Edit("save","{}",3,12345);DialogueRouteNetwork.Edit.CODEC.encode(b,edit);if(!edit.equals(DialogueRouteNetwork.Edit.CODEC.decode(b)))throw new AssertionError();
   var part=new DialogueRouteNetwork.Part(0,1,"{}");DialogueRouteNetwork.Part.CODEC.encode(b,part);if(!part.equals(DialogueRouteNetwork.Part.CODEC.decode(b)))throw new AssertionError();
  }finally{b.release();}
  System.out.println("PASS SERVER route/story registration, common guard and nine codec roundtrips; no client classes required");
 }
}
