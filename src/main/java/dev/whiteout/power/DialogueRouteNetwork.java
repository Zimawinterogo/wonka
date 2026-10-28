package dev.whiteout.power;

import com.google.gson.Gson;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.WorldSavePath;

/** Server owns route configuration and world observations. Clients never send claimed world facts. */
public final class DialogueRouteNetwork {
 private static final Gson GSON=new Gson();
 private static final Map<MinecraftServer,DialogueRouteStore> STORES=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,Cache> CACHES=new WeakHashMap<>();
 private static final class Cache {
  long window;int count;final Map<Long,Result> replies=new LinkedHashMap<>();
 }
 public record Resolve(long token,String id) implements CustomPayload {
  public static final Id<Resolve> ID=new Id<>(PowerDevices.id("route_resolve"));
  public static final PacketCodec<RegistryByteBuf,Resolve> CODEC=PacketCodec.ofStatic((b,p)->{b.writeLong(p.token);b.writeString(p.id,64);},b->new Resolve(b.readLong(),b.readString(64)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Result(long token,String selected,String fallback,String error) implements CustomPayload {
  public static final Id<Result> ID=new Id<>(PowerDevices.id("route_result"));
  public static final PacketCodec<RegistryByteBuf,Result> CODEC=PacketCodec.ofStatic((b,p)->{b.writeLong(p.token);b.writeString(p.selected,128);b.writeString(p.fallback,128);b.writeString(p.error,32);},b->new Result(b.readLong(),b.readString(128),b.readString(128),b.readString(32)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Edit(String action,String json,long revision) implements CustomPayload {
  public static final Id<Edit> ID=new Id<>(PowerDevices.id("route_edit"));
  public static final PacketCodec<RegistryByteBuf,Edit> CODEC=PacketCodec.ofStatic((b,p)->{b.writeString(p.action,8);b.writeString(p.json,12000);b.writeLong(p.revision);},b->new Edit(b.readString(8),b.readString(12000),b.readLong()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Part(int index,int total,String json) implements CustomPayload {
  public static final Id<Part> ID=new Id<>(PowerDevices.id("route_editor"));
  public static final PacketCodec<RegistryByteBuf,Part> CODEC=PacketCodec.ofStatic((b,p)->{b.writeVarInt(p.index);b.writeVarInt(p.total);b.writeString(p.json,SnapshotChunks.SIZE);},b->new Part(b.readVarInt(),b.readVarInt(),b.readString(SnapshotChunks.SIZE)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record EditorData(DialogueRouteStore.Data data,boolean invalid,String error){}
 static DialogueRouteStore store(MinecraftServer server){return STORES.computeIfAbsent(server,s->new DialogueRouteStore(s.getSavePath(WorldSavePath.ROOT).resolve("whiteout-dialogue-routes.json")));}
 public static void register(){
  PayloadTypeRegistry.playC2S().register(Resolve.ID,Resolve.CODEC);PayloadTypeRegistry.playC2S().register(Edit.ID,Edit.CODEC);
  PayloadTypeRegistry.playS2C().register(Result.ID,Result.CODEC);PayloadTypeRegistry.playS2C().register(Part.ID,Part.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Resolve.ID,(p,c)->c.server().execute(()->resolve(c.player(),p)));
  ServerPlayNetworking.registerGlobalReceiver(Edit.ID,(p,c)->c.server().execute(()->edit(c.player(),p)));
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->CACHES.remove(h.getPlayer()));
  ServerLifecycleEvents.SERVER_STOPPED.register(s->{STORES.remove(s);CACHES.keySet().removeIf(p->p.getServer()==s);});
 }
 private static void resolve(ServerPlayerEntity player,Resolve request){
  var cache=CACHES.computeIfAbsent(player,p->new Cache());long now=Integer.toUnsignedLong(player.getServer().getTicks());
  if(now-cache.window>=20||now<cache.window){cache.window=now;cache.count=0;cache.replies.clear();}
  Result result=cache.replies.get(request.token);
  if(result==null){
   if(++cache.count>32)result=new Result(request.token,"","","rate");
   else try{
    var store=store(player.getServer());var route=store.find(request.id);
    result=route==null?new Result(request.token,"","",store.invalid()?"corrupt":"missing_route"):
     new Result(request.token,route.select(c->DialogueRouteConditions.sample(player,c)),route.fallback(),"");
   }catch(RuntimeException ex){result=new Result(request.token,"","","unavailable");}
   if(cache.replies.size()<32)cache.replies.put(request.token,result);
  }
  ServerPlayNetworking.send(player,result);
 }
 private static void edit(ServerPlayerEntity player,Edit request){
  if(!player.hasPermissionLevel(2)){sendEditor(player,new EditorData(new DialogueRouteStore.Data(1,0,List.of()),true,"permission"));return;}
  var store=store(player.getServer());String error="";
  try{
   switch(request.action){
    case "list"->{}
    case "create","save"->{
     var route=GSON.fromJson(request.json,DialogueRoute.class);
     if(route!=null&&request.action.equals("create")&&store.find(route.id())!=null)throw new IllegalArgumentException("duplicate");
     store.save(route,request.revision);
    }
    case "delete"->{if(!request.json.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException();store.delete(request.json,request.revision);}
    default->throw new IllegalArgumentException();
   }
  }catch(Exception ex){error=store.invalid()?"corrupt":request.revision!=store.snapshot().revision()?"conflict":"duplicate".equals(ex.getMessage())?"duplicate":"save_failed";}
  sendEditor(player,new EditorData(store.snapshot(),store.invalid(),error));
 }
 private static void sendEditor(ServerPlayerEntity player,EditorData value){
  var parts=SnapshotChunks.split(GSON.toJson(value));
  if(parts.isEmpty())parts=List.of(GSON.toJson(new EditorData(new DialogueRouteStore.Data(1,0,List.of()),true,"size")));
  for(int i=0;i<parts.size();i++)ServerPlayNetworking.send(player,new Part(i,parts.size(),parts.get(i)));
 }
}
