package dev.whiteout.power;

import com.google.gson.Gson;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import static net.minecraft.server.command.CommandManager.literal;

public final class PassportNetwork {
 private static final Gson GSON=new Gson();
 private static final Map<ServerPlayerEntity,Long> LAST=new WeakHashMap<>();
 public record Request(int token) implements CustomPayload {
  public static final Id<Request> ID=new Id<>(PowerDevices.id("passport_request"));
  public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,p)->b.writeVarInt(p.token),b->new Request(b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Part(int token,int index,int total,String json) implements CustomPayload {
  public static final Id<Part> ID=new Id<>(PowerDevices.id("passport_snapshot"));
  public static final PacketCodec<RegistryByteBuf,Part> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeVarInt(p.token);b.writeVarInt(p.index);b.writeVarInt(p.total);b.writeString(p.json,SnapshotChunks.SIZE);},
   b->new Part(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readString(SnapshotChunks.SIZE)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public static void register(){
  PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
  PayloadTypeRegistry.playS2C().register(Part.ID,Part.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Request.ID,(p,c)->c.server().execute(()->run(c.player(),p.token(),false,false)));
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->LAST.remove(handler.getPlayer()));
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,env)->dispatcher.register(literal("whiteout")
   .then(literal("passport").requires(s->s.hasPermissionLevel(2))
    .executes(c->run(c.getSource().getPlayerOrThrow(),0,false,false))
    .then(literal("validate").executes(c->run(c.getSource().getPlayerOrThrow(),0,true,false)))
    .then(literal("export").executes(c->run(c.getSource().getPlayerOrThrow(),0,false,true))))));
 }
 private static int run(ServerPlayerEntity player,int token,boolean validate,boolean export){
  if(!player.hasPermissionLevel(2))return 0;
  long now=System.nanoTime();Long previous=LAST.get(player);
  if(previous!=null&&now-previous<1_000_000_000L)return 0;
  LAST.put(player,now);
  try{
   MapPassport report=PassportCollector.capture(player);
   if(export){
    var path=player.getServer().getSavePath(WorldSavePath.ROOT).resolve("whiteout-passport.json");
    GridFileIO.writeAtomic(path,GSON.toJson(report));
    player.sendMessage(Text.translatable("passport.whiteout.exported",path.toString()),false);
   }
   if(validate){
    long errors=report.issues().stream().filter(i->i.severity()==PassportIssue.Severity.ERROR).count();
    long warnings=report.issues().stream().filter(i->i.severity()==PassportIssue.Severity.WARN).count();
    player.sendMessage(Text.translatable("passport.whiteout.validated",errors,warnings,report.truncated()),false);
   }
   var parts=SnapshotChunks.split(GSON.toJson(report));
   while(parts.isEmpty()){
    report=new MapPassport(1,report.entries().subList(0,report.entries().size()/2),report.issues().subList(0,report.issues().size()/2),true);
    parts=SnapshotChunks.split(GSON.toJson(report));
   }
   for(int i=0;i<parts.size();i++)ServerPlayNetworking.send(player,new Part(token,i,parts.size(),parts.get(i)));
   return 1;
  }catch(Exception ex){
   System.err.println("[Whiteout] Passport failed: "+ex);player.sendMessage(Text.translatable("passport.whiteout.failed"),false);return 0;
  }
 }
}
