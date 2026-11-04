package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.power.lobby.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

public final class DialogueBarrier{
 private DialogueBarrier(){}
 public record Cue(UUID id,String kind,String value) implements CustomPayload{
  public static final Id<Cue> ID=new Id<>(PowerDevices.id("duo_cue"));
  public static final PacketCodec<RegistryByteBuf,Cue> CODEC=PacketCodec.ofStatic((b,p)->{b.writeUuid(p.id);b.writeString(p.kind,16);b.writeString(p.value,128);},b->new Cue(b.readUuid(),b.readString(16),b.readString(128)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 private record Ballot(String key,long until,long revision){}
 private record Reading(UUID id,String script,boolean waitForReaders,long revision){}
 private static final class Run{
  final Map<UUID,Ballot> votes=new HashMap<>();final Map<UUID,Reading> readers=new HashMap<>();
 }
 private static final Map<MinecraftServer,Run> runs=new WeakHashMap<>();
 private static Run run(MinecraftServer s){return runs.computeIfAbsent(s,k->new Run());}
 static boolean admits(ServerPlayerEntity p){
  var run=runs.get(p.getServer());if(run==null)return true;
  var d=LobbyState.get(Store.get(p.getServer()));if(d==null)return true;
  return run.readers.entrySet().stream().noneMatch(entry->entry.getValue().revision==d.revision()&&entry.getValue().waitForReaders&&p.getServer().getPlayerManager().getPlayer(entry.getKey())!=null);
 }
 static boolean vote(ServerPlayerEntity p,StoryEventCatalog.Event e){
  var n=e.narrative();if(n==null||n.voteGroup()==null)return true;
  var s=p.getServer();var d=LobbyState.get(Store.get(s));if(d.second()==null)return true;
  var peer=s.getPlayerManager().getPlayer(p.getUuid().equals(d.first())?d.second():d.first());if(peer==null)return !Party.paused(s);
  var r=run(s);long tick=s.getOverworld().getTime();r.votes.put(p.getUuid(),new Ballot(e.key(),tick+2400,d.revision()));
  var other=r.votes.get(peer.getUuid());return other!=null&&other.key.equals(e.key())&&other.until>=tick&&other.revision==d.revision()&&Narrative.allowed(peer,e)&&
   (e.trigger()==null?e.area().contains(peer.getServerWorld().getRegistryKey().getValue().toString(),peer.getX(),peer.getY(),peer.getZ()):TriggerAuthority.permits(peer,e.key()));
 }
 private static void send(ServerPlayerEntity p,Cue cue){if(ServerPlayNetworking.canSend(p,Cue.ID))ServerPlayNetworking.send(p,cue);}
 static void committed(ServerPlayerEntity actor,StoryEventCatalog.Event e){
  var n=e.narrative();if(n==null)return;var s=actor.getServer();var d=LobbyState.get(Store.get(s));var r=run(s);
  for(var who:new UUID[]{d.first(),d.second()}){if(who==null)continue;var peer=s.getPlayerManager().getPlayer(who);
   if(n.voteGroup()!=null){var ballot=r.votes.remove(who);if(peer!=null&&peer!=actor&&ballot!=null&&ballot.key.equals(e.key())){
    if(e.trigger()!=null)TriggerAuthority.companion(peer,e.key());send(peer,new Cue(UUID.randomUUID(),"agreed",e.key()));}}
   if(n.visibility()!=Narrative.Visibility.SHARED&&!who.equals(Narrative.owner(actor,e)))continue;
   if(peer!=null&&peer!=actor&&(n.together()||n.visibility()!=Narrative.Visibility.SHARED)&&e.trigger()!=null){TriggerAuthority.companion(peer,e.key());send(peer,new Cue(UUID.randomUUID(),"trigger",e.key()));}
   String script=who.equals(d.first())?n.firstDialogue():n.secondDialogue();if(script==null||peer==null)continue;
   var reader=new Reading(UUID.randomUUID(),script,n.waitForReaders(),d.revision());r.readers.put(who,reader);send(peer,new Cue(reader.id,"dialogue",script));
  }
 }
 static void register(){
  PayloadTypeRegistry.playS2C().register(Cue.ID,Cue.CODEC);PayloadTypeRegistry.playC2S().register(Cue.ID,Cue.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Cue.ID,(cue,c)->c.server().execute(()->{
   if(!cue.kind.equals("read"))return;var r=run(c.server());var reader=r.readers.get(c.player().getUuid());
   if(reader!=null&&reader.id.equals(cue.id))r.readers.remove(c.player().getUuid());
  }));
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->{var d=LobbyState.get(Store.get(s));var reader=run(s).readers.get(h.getPlayer().getUuid());if(reader!=null&&d!=null&&d.revision()==reader.revision)send(h.getPlayer(),new Cue(reader.id,"dialogue",reader.script));});
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->run(s).votes.clear());ServerLifecycleEvents.SERVER_STOPPED.register(runs::remove);
 }
}
