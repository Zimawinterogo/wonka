package dev.whiteout.power.lobby;

import dev.whiteout.Store;
import dev.whiteout.power.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

public final class Party {
 private Party(){}
 public record Person(UUID id,String name){}
 public record Status(UUID host,UUID partner,UUID invited,boolean mine,boolean other,boolean canStart,List<Person> online,String dimension,double x,double y,double z,LobbyState.Start start){}
 public record Info(UUID token,String json) implements CustomPayload{
  public static final Id<Info> ID=new Id<>(PowerDevices.id("board_party"));
  public static final PacketCodec<RegistryByteBuf,Info> CODEC=PacketCodec.ofStatic((b,p)->{b.writeUuid(p.token);b.writeString(p.json,24000);},b->new Info(b.readUuid(),b.readString(24000)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Request(UUID token,long revision,String op,String value) implements CustomPayload{
  public static final Id<Request> ID=new Id<>(PowerDevices.id("board_party_action"));
  public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,p)->{b.writeUuid(p.token);b.writeLong(p.revision);b.writeString(p.op,16);b.writeString(p.value,512);},b->new Request(b.readUuid(),b.readLong(),b.readString(16),b.readString(512)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static final class Session{
  UUID invite,alone;long expiry,revision=-1;final Set<UUID> ready=new HashSet<>();
  void revision(long value){if(value!=revision){revision=value;ready.clear();invite=null;}}
 }
 private static final Map<MinecraftServer,Session> sessions=new WeakHashMap<>();
 private static Session session(MinecraftServer s,LobbyState d){
  var v=sessions.computeIfAbsent(s,k->new Session());v.revision(d==null?0:d.revision());
  if(s.getOverworld().getTime()>v.expiry)v.invite=null;return v;
 }
 private static ServerPlayerEntity online(MinecraftServer s,UUID id){return id==null?null:s.getPlayerManager().getPlayer(id);}
 public static boolean member(LobbyState d,UUID id){return d!=null&&(id.equals(d.first())||id.equals(d.second()));}
 public static boolean paused(MinecraftServer s){
  var d=LobbyState.get(Store.get(s));if(d==null||!d.started()||d.second()==null)return false;
  return (online(s,d.first())==null||online(s,d.second())==null)&&online(s,session(s,d).alone)==null;
 }
 public static boolean active(ServerPlayerEntity player){
  var d=LobbyState.get(Store.get(player.getServer()));return d==null||!d.started()||member(d,player.getUuid())&&!paused(player.getServer());
 }
 static void send(ServerPlayerEntity p,UUID token){
  if(!ServerPlayNetworking.canSend(p,Info.ID))return;var s=p.getServer();var d=LobbyState.get(Store.get(s));var v=session(s,d);
  UUID a=d==null?null:d.first(),b=d==null?null:d.second(),id=p.getUuid();
  var list=s.getPlayerManager().getPlayerList().stream().limit(128).map(q->new Person(q.getUuid(),q.getGameProfile().getName())).toList();
  boolean start=d!=null&&!d.started()&&(a==null||id.equals(a))&&(b==null?v.invite==null:v.ready.containsAll(List.of(a,b))&&online(s,b)!=null);
  var status=new Status(a,b,v.invite,v.ready.contains(id),v.ready.contains(id.equals(a)?b:a),start,list,p.getServerWorld().getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ(),d==null?null:d.start());
  ServerPlayNetworking.send(p,new Info(token,BoardNetwork.JSON.toJson(status)));
 }
 private static void require(boolean ok){if(!ok)throw new IllegalStateException("Action unavailable");}
 private static ServerWorld destination(MinecraftServer s,LobbyState.Start cfg){
  if(cfg==null||cfg.dimension()==null)return null;
  var world=s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(cfg.dimension())));var pos=BlockPos.ofFloored(cfg.x(),cfg.y(),cfg.z());
  require(world!=null&&!world.isOutOfHeightLimit(pos)&&world.getWorldBorder().contains(pos));return world;
 }
 private static StoryEventCatalog.Event event(MinecraftServer s,LobbyState.Start cfg){
  if(cfg==null||cfg.event()==null)return null;var event=StoryAuthority.definition(s,cfg.event());
  require(event.serverOnly()&&event.once()&&event.scope()==ScopedStory.Scope.WORLD&&event.trigger()==null);
  return event;
 }
 private record Origin(ServerPlayerEntity p,ServerWorld w,double x,double y,double z,float yaw,float pitch){
  Origin(ServerPlayerEntity p){this(p,p.getServerWorld(),p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());}
  void restore(){p.teleport(w,x,y,z,yaw,pitch);}
 }
 static void launch(ServerPlayerEntity host,long rev)throws Exception{
  var s=host.getServer();var store=Store.get(s);var d=LobbyState.editable(store,rev);var v=session(s,d);var id=host.getUuid();
  require(store.error==null&&(d.first()==null||id.equals(d.first()))&&v.invite==null);
  var players=new ArrayList<ServerPlayerEntity>();players.add(host);
  if(d.second()!=null){var partner=online(s,d.second());require(partner!=null&&v.ready.containsAll(List.of(id,d.second())));players.add(partner);}
  require(players.stream().allMatch(p->p.isAlive()&&!p.isSpectator()));
  var cfg=d.start();var world=destination(s,cfg);var ev=event(s,cfg);
  var next=d.change(id,d.second(),true,d.photos(),cfg);var origins=players.stream().map(Origin::new).toList();
  try{
   if(world!=null)for(var p:players)p.teleport(world,cfg.x(),cfg.y(),cfg.z(),p.getYaw(),p.getPitch());
   if(ev==null)LobbyState.save(store,d,next);
   else{
    require(ev.area().contains(host.getServerWorld().getRegistryKey().getValue().toString(),host.getX(),host.getY(),host.getZ()));
    require(StoryWorldConditions.evaluate(ev.world(),new StoryWorldSource(host,store.data))==WinterCrisisConditions.Truth.TRUE);
    for(var scene:ev.scenes())require(CrisisRuntime.requestable(s,scene));
    var access=(LobbyState.Access)(Object)store.data;access.whiteout$lobby(next);
    try{require(StoryCommitTransaction.apply(store,ev,st->st.completed.add(LobbyState.STARTED))==StoryCommitTransaction.Result.COMMITTED);}
    catch(Exception failure){access.whiteout$lobby(d);throw failure;}
   }
  }catch(Exception ex){if(world!=null)origins.forEach(Origin::restore);throw ex;}
  v.ready.clear();v.alone=null;
 }
 private static void request(ServerPlayerEntity p,Request m)throws Exception{
  boolean config=m.op.equals("start-config");require(BoardNetwork.access(p,m.token,config));
  var s=p.getServer();var store=Store.get(s);var d=LobbyState.get(store);require(d!=null&&d.revision()==m.revision&&store.error==null);
  var v=session(s,d);var id=p.getUuid();
  if(m.op.equals("difficulty")){
   require(id.equals(d.first())||d.first()==null&&!d.started()||id.equals(v.alone));
   var difficulty=net.minecraft.world.Difficulty.byName(m.value);require(difficulty!=null&&difficulty!=net.minecraft.world.Difficulty.PEACEFUL);
   s.setDifficulty(difficulty,true);v.ready.clear();
  }else{
   require(!d.started());
   switch(m.op){
    case "invite"->{require(d.first()==null||id.equals(d.first()));UUID who=UUID.fromString(m.value);
     var partner=online(s,who);require(partner!=null&&!id.equals(who)&&d.second()==null);
     if(d.first()==null){var next=d.change(id,null,false,d.photos(),d.start());LobbyState.save(store,d,next);v=session(s,next);}
     v.ready.clear();v.invite=who;v.expiry=s.getOverworld().getTime()+2400;partner.sendMessage(Text.literal("Whiteout · "+p.getGameProfile().getName()+" приглашает в Duo. Подойдите к сюжетной доске."));
    }
    case "accept"->{require(id.equals(v.invite)&&online(s,d.first())!=null);LobbyState.save(store,d,d.change(d.first(),id,false,d.photos(),d.start()));}
    case "decline"->{require(id.equals(v.invite));v.invite=null;}
    case "solo"->{require(id.equals(d.first())||id.equals(d.second()));LobbyState.save(store,d,d.change(null,null,false,d.photos(),d.start()));}
    case "ready"->{require(d.second()!=null&&member(d,id)&&online(s,d.first())!=null&&online(s,d.second())!=null);if(!v.ready.remove(id))v.ready.add(id);}
    case "start-config"->{var cfg=BoardNetwork.JSON.fromJson(m.value,LobbyState.Start.class);destination(s,cfg);event(s,cfg);LobbyState.save(store,d,d.change(d.first(),d.second(),false,d.photos(),cfg));}
    default->throw new IllegalArgumentException("unknown lobby action");
   }
  }
  BoardNetwork.refresh(p);BoardNetwork.notifyParty(s);
 }
 static void register(){
  PayloadTypeRegistry.playS2C().register(Info.ID,Info.CODEC);PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Request.ID,(m,c)->c.server().execute(()->{try{request(c.player(),m);}catch(Exception ex){c.player().sendMessage(Text.translatable("story.whiteout.request_denied"));}}));
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{
   var p=h.getPlayer();var d=LobbyState.get(Store.get(s));if(d==null)return;var v=session(s,d);
   if(p.getUuid().equals(v.invite)||p.getUuid().equals(d.first()))v.invite=null;
   if(!member(d,p.getUuid()))return;v.ready.clear();v.alone=null;
   var other=online(s,p.getUuid().equals(d.first())?d.second():d.first());
   if(other!=null&&d.started())other.sendMessage(Text.literal("Продолжить игру без второго игрока").styled(style->style.withUnderline(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/whiteout lobby continue-alone"))));
  });
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->{var d=LobbyState.get(Store.get(s));if(d!=null&&member(d,h.getPlayer().getUuid())){var v=session(s,d);v.alone=null;
   if(d.started()&&d.second()!=null&&paused(s))h.getPlayer().sendMessage(Text.literal("Продолжить игру без второго игрока").styled(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/whiteout lobby continue-alone"))));}});
  ServerLifecycleEvents.SERVER_STOPPED.register(sessions::remove);
  CommandRegistrationCallback.EVENT.register((cmd,r,env)->cmd.register(literal("whiteout").then(literal("lobby").then(literal("continue-alone").executes(c->{
   var p=c.getSource().getPlayerOrThrow();var s=p.getServer();var d=LobbyState.get(Store.get(s));
   if(d==null||!d.started()||d.second()==null||!member(d,p.getUuid())||online(s,p.getUuid().equals(d.first())?d.second():d.first())!=null)return 0;
   session(s,d).alone=p.getUuid();p.sendMessage(Text.literal("Сюжет продолжен. Роли Duo сохранены."));return 1;
  })))));
 }
}
