import dev.whiteout.power.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import io.netty.buffer.Unpooled;

public class DialogueRouteServerProbe {
 public static void main(String[] args){
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  DialogueRouteNetwork.register();
  var b=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
  try{
   var resolve=new DialogueRouteNetwork.Resolve(42,"lake");DialogueRouteNetwork.Resolve.CODEC.encode(b,resolve);if(!resolve.equals(DialogueRouteNetwork.Resolve.CODEC.decode(b)))throw new AssertionError();
   var result=new DialogueRouteNetwork.Result(42,"диалог","fallback","");DialogueRouteNetwork.Result.CODEC.encode(b,result);if(!result.equals(DialogueRouteNetwork.Result.CODEC.decode(b)))throw new AssertionError();
   var edit=new DialogueRouteNetwork.Edit("save","{}",3,12345);DialogueRouteNetwork.Edit.CODEC.encode(b,edit);if(!edit.equals(DialogueRouteNetwork.Edit.CODEC.decode(b)))throw new AssertionError();
   var part=new DialogueRouteNetwork.Part(0,1,"{}");DialogueRouteNetwork.Part.CODEC.encode(b,part);if(!part.equals(DialogueRouteNetwork.Part.CODEC.decode(b)))throw new AssertionError();
  }finally{b.release();}
  System.out.println("PASS SERVER route registration and four packet codec roundtrips; no client classes required");
 }
}
