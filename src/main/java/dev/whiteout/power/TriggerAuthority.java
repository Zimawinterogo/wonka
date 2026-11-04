package dev.whiteout.power;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.whiteout.Store;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Only observed play events or privileged server signals can create trigger evidence. */
public final class TriggerAuthority {
 private TriggerAuthority(){}
 public record Notice(boolean manifest,String data) implements CustomPayload {
  public static final Id<Notice> ID=new Id<>(PowerDevices.id("story_trigger_notice"));
  public static final PacketCodec<RegistryByteBuf,Notice> CODEC=PacketCodec.ofStatic(
   (buf,msg)->{buf.writeBoolean(msg.manifest);buf.writeString(msg.data,17000);},buf->new Notice(buf.readBoolean(),buf.readString(17000)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 private record Session(StoryEventCatalog catalog,Object world,long epoch,TriggerEvidence evidence){}
 private static final Map<ServerPlayerEntity,Session> sessions=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,String> manifests=new WeakHashMap<>();
 public static String fingerprint(String key){return dev.whiteout.power.lobby.PhotoTransfer.hash(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private static void manifest(ServerPlayerEntity p,Session session,boolean force){
  var list=new StringJoiner("\n");for(var rule:session.catalog.rules())list.add((session.catalog.visible(p,rule.key())?"+":"-")+session.catalog.fingerprint(rule.key()));
  String value=list.toString();if(force||!value.equals(manifests.get(p))){manifests.put(p,value);ServerPlayNetworking.send(p,new Notice(true,value));}
 }
 private static boolean live(ServerPlayerEntity p){return p.isAlive()&&!p.isSpectator();}
 private static long now(ServerPlayerEntity p){return p.getServerWorld().getTime();}
 private static TriggerEvidence.Body body(ServerPlayerEntity p){
  var b=p.getBoundingBox();
  return new TriggerEvidence.Body(p.getServerWorld().getRegistryKey().getValue().toString(),b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
 }
 static void reset(ServerPlayerEntity p){sessions.remove(p);manifests.remove(p);if(ServerPlayNetworking.canSend(p,Notice.ID))ServerPlayNetworking.send(p,new Notice(true,""));}
 private static Session session(ServerPlayerEntity p){
  if(DuoTestMode.active(p)!=null)return null;
  if(!live(p)||!ServerPlayNetworking.canSend(p,Notice.ID)){sessions.remove(p);return null;}
  var catalog=StoryAuthority.catalog(p.getServer());var current=sessions.get(p);
  if(current==null&&catalog.rules().isEmpty())return null;
  long epoch=ScopedStory.epoch(Store.get(p.getServer()).data,p.getUuid());
  if(current==null||current.catalog()!=catalog||current.world()!=p.getServerWorld()||current.epoch()!=epoch){
   current=new Session(catalog,p.getServerWorld(),epoch,new TriggerEvidence());sessions.put(p,current);
   manifest(p,current,true);
  }
  return current;
 }
 private static boolean eligible(ServerPlayerEntity p,Session session,String key){
  var store=Store.get(p.getServer());
  return store.error==null&&dev.whiteout.power.lobby.Party.active(p)&&session.catalog().eligible(key,store.data,p);
 }
 private static boolean position(TriggerEvidence.Rule r,ServerPlayerEntity p){
  if(r.binding().zone())return r.area().dimension().equals(body(p).dimension())&&body(p).intersects(r.area())==(r.binding().type()==TriggerEvidence.Kind.ENTER_BOX);
  return r.area()==null||r.area().contains(body(p).dimension(),p.getX(),p.getY(),p.getZ());
 }
 static boolean permits(ServerPlayerEntity p,String key){
  Session s=session(p);if(s==null)return false;var r=s.catalog().rule(key);
  return r!=null&&position(r,p)&&s.evidence().permits(r,now(p));
 }
 static void companion(ServerPlayerEntity p,String key){
  var s=session(p);if(s!=null){var r=s.catalog.rule(key);if(r!=null)s.evidence.consume(r,now(p));}
 }
 static void committed(ServerPlayerEntity p,String key){
  var s=sessions.get(p);var rule=s.catalog().rule(key);s.evidence().consume(rule,now(p));
  if(rule.binding().emit()!=null)signal(p,rule.binding().emit());
 }
 public static int signal(ServerPlayerEntity p,String name){return arm(p,TriggerEvidence.Kind.SIGNAL,name);}
 private static int arm(ServerPlayerEntity p,TriggerEvidence.Kind kind,String target){
  Session s=session(p);if(s==null)return 0;int count=0;
  for(var rule:s.catalog().rules()){
   if(rule.binding().type()==kind&&target.equalsIgnoreCase(rule.binding().target())&&position(rule,p)&&eligible(p,s,rule.key())){
    s.evidence().arm(rule,now(p));count++;
    if(kind==TriggerEvidence.Kind.INTERACT_ENTITY)break;
   }
  }
  return count;
 }
 static void register(){
  PayloadTypeRegistry.playS2C().register(Notice.ID,Notice.CODEC);
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->sessions.remove(handler.getPlayer()));
  ServerTickEvents.END_SERVER_TICK.register(server->{
   for(var player:server.getPlayerManager().getPlayerList()){
    Session s=session(player);if(s==null||s.catalog().rules().isEmpty())continue;
    if(server.getTicks()%20==0)manifest(player,s,false);
    var bounds=body(player);
    for(var rule:s.catalog().rules())s.evidence().observe(rule,bounds,now(player),k->eligible(player,s,k),
     k->ServerPlayNetworking.send(player,new Notice(false,k)));
   }
  });
  UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
   if(player instanceof ServerPlayerEntity p&&hand==Hand.MAIN_HAND&&entity!=player&&entity.isAlive()&&entity.getWorld()==world
      &&p.canInteractWithEntity(entity,0)&&p.canSee(entity)&&arm(p,TriggerEvidence.Kind.INTERACT_ENTITY,entity.getUuidAsString())>0)
    return ActionResult.SUCCESS;
   return ActionResult.PASS;
  });
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("whiteout")
   .then(literal("story-authority").requires(s->s.hasPermissionLevel(2)).then(literal("signal")
    .then(argument("player",EntityArgumentType.player()).then(argument("signal",StringArgumentType.greedyString())
     .executes(c->signal(EntityArgumentType.getPlayer(c,"player"),StringArgumentType.getString(c,"signal")))))))));
 }
}
