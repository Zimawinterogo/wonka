package dev.whiteout.power;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Network-synced client-side door veto. A client must NOT run the vanilla
 * opening animation before the authoritative server rejects a frozen or
 * unpowered door. The server remains responsible for attempts and security. */
public final class DoorGateSync {
 private DoorGateSync(){}
 public record Snapshot(String dimension,String blocked) implements CustomPayload {
  public static final Id<Snapshot> ID=new Id<>(PowerDevices.id("door_gate_snapshot"));
  public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeString(p.dimension,128);b.writeString(p.blocked,16384);},
   b->new Snapshot(b.readString(128),b.readString(16384)));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 private static boolean registered;
 private record Sent(Snapshot snapshot,int tick){}
 private static final Map<MinecraftServer,Map<java.util.UUID,Sent>> LAST_SENT=new IdentityHashMap<>();
 static boolean shouldSend(Snapshot previous,Snapshot current,long elapsed){
  // A bounded heartbeat repairs client clears during respawn/world transitions.
  return !current.equals(previous)||elapsed<0||elapsed>=100;
 }
 private static void send(ServerPlayerEntity player){
  ServerWorld world=player.getServerWorld();
  BlockPos around=player.getBlockPos();
  SortedSet<String> blocked=new TreeSet<>();
  for(BlockPos p:ElectricDoors.nearbyLocked(world,around,9)){
   blocked.add(p.getX()+","+p.getY()+","+p.getZ());
  }
  for(BlockPos p:WorldAtmosphere.nearbyFrozen(world,around,9)){
   blocked.add(p.getX()+","+p.getY()+","+p.getZ());
  }
  String encoded=String.join("\n",blocked);
  if(encoded.length()>16000)encoded=encoded.substring(0,encoded.lastIndexOf('\n',16000));
  Snapshot snapshot=new Snapshot(world.getRegistryKey().getValue().toString(),encoded);
  java.util.UUID playerId=player.getUuid();
  var sent=LAST_SENT.computeIfAbsent(world.getServer(),s->new HashMap<>());
  Sent previous=sent.get(playerId);int tick=world.getServer().getTicks();
  if(previous!=null&&!shouldSend(previous.snapshot(),snapshot,(long)tick-previous.tick()))return;
  ServerPlayNetworking.send(player,snapshot);
  sent.put(playerId,new Sent(snapshot,tick));
 }
 public static void register(){
  if(registered)return;registered=true;
  PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);
  ServerTickEvents.END_SERVER_TICK.register(server->{
   if(Math.floorMod(server.getTicks(),10)!=0)return;
   for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())send(p);
  });
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{forget(server,handler.getPlayer().getUuid());send(handler.getPlayer());});
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->forget(server,handler.getPlayer().getUuid()));
  ServerLifecycleEvents.SERVER_STOPPED.register(LAST_SENT::remove);
 }
 private static void forget(MinecraftServer server,UUID player){var sent=LAST_SENT.get(server);if(sent!=null)sent.remove(player);}
}
