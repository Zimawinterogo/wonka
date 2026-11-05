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
 public record Reading(UUID id,String script,boolean waitForReaders,long revision){
  public Reading{
   Objects.requireNonNull(id);
   if(script==null||!script.matches("[a-zA-Z0-9_.:/-]{1,128}")||revision<1)throw new IllegalArgumentException("invalid pending Duo dialogue");
  }
 }
 /** Persisted in Store.Data, alongside the same transaction as story progress. */
 public static final class Readers extends LinkedHashMap<UUID,Reading>{}
 public interface Access{Readers whiteout$readers();void whiteout$readers(Readers value);}
 public static void validate(Readers value){
  if(value==null||value.size()>16)throw new IllegalArgumentException("invalid pending Duo dialogue ledger");
  value.forEach((who,reading)->{
   if(who==null||reading==null)throw new IllegalArgumentException("null pending Duo dialogue");
   new Reading(reading.id(),reading.script(),reading.waitForReaders(),reading.revision());
  });
 }
 private static Readers readers(Store store){return ((Access)(Object)store.data).whiteout$readers();}
 static final class Stage{
  private final Access access;private final Readers previous;
  private Stage(Access access,Readers previous){this.access=access;this.previous=previous;}
  void rollback(){access.whiteout$readers(previous);}
 }
 /** Stage acknowledgements before Narrative.commit so one Store.save commits both. */
 static Stage stage(ServerPlayerEntity actor,Store store,StoryEventCatalog.Event e){
  var n=e.narrative();var d=LobbyState.get(store);
  if(n==null||d==null||!d.started())return null;
  var prior=readers(store);var next=new Readers();
  prior.forEach((who,reading)->{if(reading.revision()==d.revision())next.put(who,reading);});
  boolean changed=false;
  for(var who:new UUID[]{d.first(),d.second()}){
   if(who==null||n.visibility()!=Narrative.Visibility.SHARED&&!who.equals(Narrative.owner(actor,e)))continue;
   String script=who.equals(d.first())?n.firstDialogue():n.secondDialogue();
   if(script==null)continue;
   next.put(who,new Reading(UUID.randomUUID(),script,n.waitForReaders(),d.revision()));changed=true;
  }
  if(!changed)return null;
  var access=(Access)(Object)store.data;access.whiteout$readers(next);return new Stage(access,prior);
 }
 private static final class Run{
  final Map<UUID,Ballot> votes=new HashMap<>();
 }
 private static final Map<MinecraftServer,Run> runs=new WeakHashMap<>();
 private static Run run(MinecraftServer s){return runs.computeIfAbsent(s,k->new Run());}
 static boolean admits(ServerPlayerEntity p,String key){
  var store=Store.get(p.getServer());var d=LobbyState.get(store);if(d==null)return true;
  var pending=readers(store);var mine=pending.get(p.getUuid());
  if(mine!=null&&mine.revision()==d.revision()&&key.startsWith("dialogue_choice:"+mine.script()+":"))return true;
  return pending.entrySet().stream().noneMatch(entry->entry.getValue().revision()==d.revision()&&entry.getValue().waitForReaders()&&p.getServer().getPlayerManager().getPlayer(entry.getKey())!=null);
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
  var n=e.narrative();if(n==null)return;var s=actor.getServer();var store=Store.get(s);var d=LobbyState.get(store);var r=run(s);var pending=readers(store);
  for(var who:new UUID[]{d.first(),d.second()}){if(who==null)continue;var peer=s.getPlayerManager().getPlayer(who);
   if(n.voteGroup()!=null){var ballot=r.votes.remove(who);if(peer!=null&&peer!=actor&&ballot!=null&&ballot.key.equals(e.key())){
    if(e.trigger()!=null)TriggerAuthority.companion(peer,e.key());send(peer,new Cue(UUID.randomUUID(),"agreed",e.key()));}}
   if(n.visibility()!=Narrative.Visibility.SHARED&&!who.equals(Narrative.owner(actor,e)))continue;
   if(peer!=null&&peer!=actor&&(n.together()||n.visibility()!=Narrative.Visibility.SHARED)&&e.trigger()!=null){TriggerAuthority.companion(peer,e.key());send(peer,new Cue(UUID.randomUUID(),"trigger",e.key()));}
   String script=who.equals(d.first())?n.firstDialogue():n.secondDialogue();if(script==null||peer==null)continue;
   if(peer!=actor&&e.trigger()!=null)TriggerAuthority.companion(peer,e.key());
   var reader=pending.get(who);if(reader!=null&&reader.revision()==d.revision()&&reader.script().equals(script))
    send(peer,new Cue(reader.id(),"dialogue",script));
  }
 }
 static void register(){
  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
   net.minecraft.server.command.CommandManager.literal("whiteout").then(net.minecraft.server.command.CommandManager.literal("lobby")
   .then(net.minecraft.server.command.CommandManager.literal("retry-dialogue").executes(context->{
    var player=context.getSource().getPlayerOrThrow();var test=DuoTestMode.active(player);
    if(test!=null)return test.retryDialogue(player)?1:0;
    var store=Store.get(player.getServer());var reader=readers(store).get(player.getUuid());var state=LobbyState.get(store);
    if(reader==null||state==null||reader.revision()!=state.revision())return 0;
    send(player,new Cue(reader.id(),"dialogue",reader.script()));return 1;
   })))));
  SharedScene.register();PayloadTypeRegistry.playS2C().register(Cue.ID,Cue.CODEC);PayloadTypeRegistry.playC2S().register(Cue.ID,Cue.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Cue.ID,(cue,c)->c.server().execute(()->{
   if(cue.kind.equals("scope_ack")){DuoTestMode.acknowledge(c.player(),cue.id);return;}
   if(cue.kind.equals("read_error")){
    var test=DuoTestMode.active(c.player());
    var reader=readers(Store.get(c.server())).get(c.player().getUuid());
    if(test!=null?test.readError(cue.id):reader!=null&&reader.id().equals(cue.id)){
     c.player().sendMessage(net.minecraft.text.Text.literal("Диалог не прочитан или прерван. Проверьте клиентские ресурсы; повтор: /whiteout lobby retry-dialogue. Сюжетное подтверждение не выдано."));
     org.slf4j.LoggerFactory.getLogger("whiteout-dialogue").warn("Dialogue load failed for player {} cue {}",c.player().getUuid(),cue.id);
    }
    return;
   }
   var test=DuoTestMode.active(c.player());if(test!=null&&cue.kind.equals("read")){test.read(cue.id);return;}
   if(!cue.kind.equals("read")){SharedScene.response(c.player(),cue);return;}
   var store=Store.get(c.server());var previous=readers(store);var reader=previous.get(c.player().getUuid());
   if(reader!=null&&reader.id().equals(cue.id)){
    var next=new Readers();next.putAll(previous);next.remove(c.player().getUuid());
    var access=(Access)(Object)store.data;access.whiteout$readers(next);
    try{store.save();}
    catch(Exception failed){
     access.whiteout$readers(previous);
     c.player().sendMessage(net.minecraft.text.Text.literal("Не удалось сохранить прочтение диалога. Повторите: /whiteout lobby retry-dialogue."));
     org.slf4j.LoggerFactory.getLogger("whiteout-dialogue").warn("Cannot persist dialogue acknowledgement",failed);
    }
   }
  }));
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->{var store=Store.get(s);var d=LobbyState.get(store);var reader=readers(store).get(h.getPlayer().getUuid());
   if(reader!=null&&d!=null&&d.revision()==reader.revision())send(h.getPlayer(),new Cue(reader.id(),"dialogue",reader.script()));});
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->run(s).votes.clear());ServerLifecycleEvents.SERVER_STOPPED.register(runs::remove);
 }
}
