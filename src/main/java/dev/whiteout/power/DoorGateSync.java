package dev.whiteout.power;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
 private static int ticks;
 private static void send(ServerPlayerEntity player){
  ServerWorld world=player.getServerWorld();
  BlockPos around=player.getBlockPos();
  LinkedHashSet<String> blocked=new LinkedHashSet<>();
  for(BlockPos p:ElectricDoors.nearbyLocked(world,around,9)){
   blocked.add(p.getX()+","+p.getY()+","+p.getZ());
  }
  for(BlockPos p:WorldAtmosphere.nearbyFrozen(world,around,9)){
   blocked.add(p.getX()+","+p.getY()+","+p.getZ());
  }
  String encoded=String.join("\n",blocked);
  if(encoded.length()>16000)encoded=encoded.substring(0,encoded.lastIndexOf('\n',16000));
  ServerPlayNetworking.send(player,new Snapshot(world.getRegistryKey().getValue().toString(),encoded));
 }
 public static void register(){
  PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);
  ServerTickEvents.END_SERVER_TICK.register(server->{
   if(++ticks%10!=0)return;
   for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())send(p);
  });
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.getPlayer()));
 }
}
