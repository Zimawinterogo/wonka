package dev.whiteout.power.backpack;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Only the sender's cosmetic state can be changed. No inventory or movement mutations. */
public final class BackpackNetwork {
 public record Request(boolean open) implements CustomPayload {
  public static final Id<Request> ID=new Id<>(Identifier.of("whiteout","backpack_request"));
  public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,p)->b.writeBoolean(p.open),b->new Request(b.readBoolean()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record State(UUID player,Identifier dimension,boolean open,int age) implements CustomPayload {
  public static final Id<State> ID=new Id<>(Identifier.of("whiteout","backpack_state"));
  public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.ofStatic((b,p)->{
   b.writeUuid(p.player);b.writeIdentifier(p.dimension);b.writeBoolean(p.open);b.writeVarInt(p.age);
  },b->new State(b.readUuid(),b.readIdentifier(),b.readBoolean(),b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 private record Session(Identifier world,Vec3d origin,long started,long touched){}
 private static final Map<UUID,Session> sessions=new HashMap<>();
 private static final Map<UUID,Long> lastStart=new HashMap<>();
 private static long tick;
 public static boolean eligible(net.minecraft.entity.player.PlayerEntity p){
  return BackpackPosePolicy.allowed(p.isAlive(),p.isSpectator(),p.isCreative(),p.isOnGround(),
   p.hasVehicle(),p.isSwimming(),p.isFallFlying(),p.isSleeping(),p.currentScreenHandler==p.playerScreenHandler);
 }
 private static void send(ServerPlayerEntity viewer,State state){
  if(ServerPlayNetworking.canSend(viewer,State.ID))ServerPlayNetworking.send(viewer,state);
 }
 private static State snapshot(ServerPlayerEntity p,Session s){
  return new State(p.getUuid(),s.world,true,(int)Math.min(1100,(tick-s.started)*50));
 }
 private static void publish(ServerPlayerEntity p,Session s){
  State state=snapshot(p,s);send(p,state);
  for(var viewer:PlayerLookup.tracking(p))send(viewer,state);
 }
 private static void close(ServerPlayerEntity p){
  Session old=sessions.remove(p.getUuid());if(old==null)return;
  // Include old-world viewers during dimension changes.
  State state=new State(p.getUuid(),old.world,false,0);
  for(var viewer:p.getServer().getPlayerManager().getPlayerList())send(viewer,state);
 }
 public static void register(){
  PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
  PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Request.ID,(packet,context)->context.server().execute(()->{
   var p=context.player();
   if(context.server().getPlayerManager().getPlayer(p.getUuid())!=p)return;
   Session s=sessions.get(p.getUuid());
   if(!packet.open||!eligible(p)){close(p);return;}
   if(s==null){
    if(tick-lastStart.getOrDefault(p.getUuid(),-20L)<4)return;
    lastStart.put(p.getUuid(),tick);
    s=new Session(p.getWorld().getRegistryKey().getValue(),p.getPos(),tick,tick);
   }else{
    if(tick-s.touched<10)return; // Keepalive limited to at most two updates/second.
    s=new Session(s.world,s.origin,s.started,tick);
   }
   sessions.put(p.getUuid(),s);publish(p,s);
  }));
  EntityTrackingEvents.START_TRACKING.register((entity,viewer)->{
   if(entity instanceof ServerPlayerEntity p){Session s=sessions.get(p.getUuid());if(s!=null)send(viewer,snapshot(p,s));}
  });
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{close(handler.player);lastStart.remove(handler.player.getUuid());});
  ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->close(p));
  ServerTickEvents.END_SERVER_TICK.register(server->{
   tick++;
   for(UUID id:new ArrayList<>(sessions.keySet())){
    var p=server.getPlayerManager().getPlayer(id);Session s=sessions.get(id);
    if(p==null){sessions.remove(id);continue;}
    if(!eligible(p)||!BackpackPosePolicy.validLease(tick,s.touched,
      p.getWorld().getRegistryKey().getValue().equals(s.world),p.getPos().squaredDistanceTo(s.origin)))close(p);
   }
  });
  ServerLifecycleEvents.SERVER_STOPPED.register(server->{sessions.clear();lastStart.clear();tick=0;});
 }
}
