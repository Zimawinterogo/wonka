package dev.whiteout.power;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Per-world settings are server-owned. Client GUI only asks for authorized changes. */
public final class EnvironmentNetwork {
 private EnvironmentNetwork(){}
 public record SetOldShelter(String zone, boolean old) implements CustomPayload {
  public static final Id<SetOldShelter> ID=new Id<>(PowerDevices.id("set_old_shelter"));
  public static final PacketCodec<RegistryByteBuf,SetOldShelter> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeString(p.zone,128);b.writeBoolean(p.old);},
   b->new SetOldShelter(b.readString(128),b.readBoolean()));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Settings(String oldZoneIds,boolean wind,boolean cold,boolean creaks,boolean staticNoise,boolean windowFrost,int windStrengthPercent) implements CustomPayload {
  public static final Id<Settings> ID=new Id<>(PowerDevices.id("atmosphere_settings"));
  public static final PacketCodec<RegistryByteBuf,Settings> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeString(p.oldZoneIds,16384);b.writeBoolean(p.wind);b.writeBoolean(p.cold);b.writeBoolean(p.creaks);b.writeBoolean(p.staticNoise);b.writeBoolean(p.windowFrost);b.writeVarInt(p.windStrengthPercent);},
   b->new Settings(b.readString(16384),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt()));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** Each old-zone set is sent in small independent payloads after the flags packet.
  * This avoids a 16k character serialization exception on heavily authored maps. */
 public record OldSheltersChunk(String zoneIds) implements CustomPayload {
  public static final Id<OldSheltersChunk> ID=new Id<>(PowerDevices.id("old_shelters_chunk"));
  public static final PacketCodec<RegistryByteBuf,OldSheltersChunk> CODEC=PacketCodec.ofStatic(
   (b,p)->b.writeString(p.zoneIds,ZoneIdBatcher.MAX_CHARS),
   b->new OldSheltersChunk(b.readString(ZoneIdBatcher.MAX_CHARS)));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** User-adjustable old-building ambient interval (seconds), one zone per packet.
  * This avoids truncating large maps and retains the zone identity. */
 public record OldSoundRate(String zone,int seconds) implements CustomPayload {
  public static final Id<OldSoundRate> ID=new Id<>(PowerDevices.id("old_sound_rate"));
  public static final PacketCodec<RegistryByteBuf,OldSoundRate> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeString(p.zone,128);b.writeVarInt(p.seconds);},
   b->new OldSoundRate(b.readString(128),b.readVarInt()));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** Client -> server: one zone's sound settings, server permission checked. */
 public record SetOldSound(String zone,boolean enabled,int seconds) implements CustomPayload {
  public static final Id<SetOldSound> ID=new Id<>(PowerDevices.id("set_old_sound"));
  public static final PacketCodec<RegistryByteBuf,SetOldSound> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeString(p.zone,128);b.writeBoolean(p.enabled);b.writeVarInt(p.seconds);},
   b->new SetOldSound(b.readString(128),b.readBoolean(),b.readVarInt()));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** Server -> client: individual muted zone (default is enabled). */
 public record MutedOldSound(String zone) implements CustomPayload {
  public static final Id<MutedOldSound> ID=new Id<>(PowerDevices.id("muted_old_sound"));
  public static final PacketCodec<RegistryByteBuf,MutedOldSound> CODEC=PacketCodec.ofStatic(
   (b,p)->b.writeString(p.zone,128),b->new MutedOldSound(b.readString(128)));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 public static Settings from(AtmosphereConfig c){return new Settings("",c.windThroughOpenings,c.coldLeaks,c.oldHouseSounds,c.radioStatic,c.windowFrost,c.windStrengthPercent);}
 public static List<OldSheltersChunk> chunks(AtmosphereConfig c){
  List<OldSheltersChunk> out=new ArrayList<>();
  for(String batch:ZoneIdBatcher.batches(c.oldShelters))out.add(new OldSheltersChunk(batch));
  return out;
 }
 public static void register(){
  PayloadTypeRegistry.playC2S().register(SetOldShelter.ID,SetOldShelter.CODEC);
  PayloadTypeRegistry.playS2C().register(Settings.ID,Settings.CODEC);
  PayloadTypeRegistry.playS2C().register(OldSheltersChunk.ID,OldSheltersChunk.CODEC);
  PayloadTypeRegistry.playS2C().register(OldSoundRate.ID,OldSoundRate.CODEC);
  PayloadTypeRegistry.playS2C().register(MutedOldSound.ID,MutedOldSound.CODEC);
  PayloadTypeRegistry.playC2S().register(SetOldSound.ID,SetOldSound.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(SetOldSound.ID,(packet,context)->context.server().execute(()->{
   if(!context.player().hasPermissionLevel(2)||packet.zone().isBlank()||packet.zone().length()>96
     ||packet.seconds()<8||packet.seconds()>300)return;
   editAtmosphere(context.player(),packet.zone(),()->{
    var after=WorldAtmosphere.editorSnapshot(context.server(),packet.zone());
    if(!after.get("old").getAsBoolean())return;
    after.addProperty("muted",!packet.enabled());after.addProperty("interval",packet.seconds());
    WorldAtmosphere.editorRestore(context.server(),packet.zone(),after);
   });
  }));
  ServerPlayNetworking.registerGlobalReceiver(SetOldShelter.ID,(packet,context)->context.server().execute(()->{
   var player=context.player();
   if(!player.hasPermissionLevel(2)||packet.zone.isBlank()||packet.zone.length()>96)return;
   editAtmosphere(player,packet.zone,()->{
    var zone=EditorHistory.find(player,packet.zone);if(zone==null||!zone.shelter)return;
    var after=WorldAtmosphere.editorSnapshot(player.getServer(),packet.zone);after.addProperty("old",packet.old);
    WorldAtmosphere.editorRestore(player.getServer(),packet.zone,after);
   });
  }));
 }
 private static void editAtmosphere(net.minecraft.server.network.ServerPlayerEntity player,String id,Runnable edit){
  var before=EditorHistory.capture(player,id);
  try{edit.run();EditorHistory.record(player,id,before,true);EditorNetwork.status(player,false);}
  catch(RuntimeException ex){player.sendMessage(net.minecraft.text.Text.literal("Не удалось изменить атмосферу: "+ex.getMessage()),false);}
 }
}
