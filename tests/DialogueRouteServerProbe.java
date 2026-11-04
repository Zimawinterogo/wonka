import dev.whiteout.power.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import io.netty.buffer.Unpooled;

public class DialogueRouteServerProbe {
 public static void main(String[] args)throws Exception{
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  DialogueRouteNetwork.register();
  StoryAuthority.register();
  ScopedStoryProbe.run();DuoTestProbe.run();
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
