package dev.whiteout.power;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class EditorNetwork {
 private EditorNetwork(){}
 public record Request(String action,String id,int flags) implements CustomPayload {
  public static final Id<Request> ID=new Id<>(net.minecraft.util.Identifier.of("whiteout","editor_request"));
  public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,p)->{b.writeString(p.action,16);b.writeString(p.id,64);b.writeVarInt(p.flags);},b->new Request(b.readString(16),b.readString(64),b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Status(int count,boolean refresh,boolean settled,String zone) implements CustomPayload {
  public static final Id<Status> ID=new Id<>(net.minecraft.util.Identifier.of("whiteout","editor_status"));
  public static final PacketCodec<RegistryByteBuf,Status> CODEC=PacketCodec.ofStatic((b,p)->{b.writeVarInt(p.count);b.writeBoolean(p.refresh);b.writeBoolean(p.settled);b.writeString(p.zone,64);},b->new Status(b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readString(64)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public interface Extension {boolean handle(ServerPlayerEntity player,Request request);}
 public static final List<Extension> extensions=new ArrayList<>();
 public static void status(ServerPlayerEntity p,boolean refresh){status(p,refresh,false);}
 public static void status(ServerPlayerEntity p,boolean refresh,boolean settled){status(p,refresh,settled,"");}
 public static void status(ServerPlayerEntity p,boolean refresh,boolean settled,String zone){if(ServerPlayNetworking.canSend(p,Status.ID))ServerPlayNetworking.send(p,new Status(EditorHistory.count(p),refresh,settled,zone));}
 public static void register(){
  EditorHistory.register();
  PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Status.ID,Status.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Request.ID,(r,c)->c.server().execute(()->{
   try{
    if(!EditorHistory.allowed(c.player()))return;
    if(r.action.equals("undo")){String id=EditorHistory.undo(c.player());status(c.player(),true,false,id);}
    else if(r.action.equals("status"))status(c.player(),false);
    else for(Extension e:extensions)if(e.handle(c.player(),r))break;
   }catch(RuntimeException e){c.player().sendMessage(Text.literal(e.getMessage()==null?"Ошибка редактора":e.getMessage()),false);status(c.player(),false);}
   finally{if(r.action.equals("undo")||r.action.equals("paste"))status(c.player(),false,true);}
  }));
 }
}
