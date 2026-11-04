package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.net.WhiteoutNetwork;
import dev.whiteout.power.lobby.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

public final class SharedScene{
 private SharedScene(){}
 public record Spec(String scene,int ticks,String completion,List<SceneDelivery.Action> deliveries){
  public Spec{deliveries=deliveries==null?List.of():List.copyOf(deliveries);if(deliveries.size()>8||scene==null||!scene.matches("[a-zA-Z0-9_-]{1,80}")||ticks<1||ticks>72000||completion==null||!completion.matches("[a-zA-Z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("shared scene specification");}
 }
 public record Playing(UUID id,Spec spec,int elapsed){
  public Playing{Objects.requireNonNull(id);Objects.requireNonNull(spec);if(elapsed<0||elapsed>spec.ticks)throw new IllegalArgumentException("scene cursor");}
 }
 public interface Access{Playing whiteout$scene();void whiteout$scene(Playing run);}
 static Playing current(Store store){return ((Access)(Object)store.data).whiteout$scene();}
 private static final class Session{UUID id;int elapsed;final Set<UUID> votes=new HashSet<>(),sent=new HashSet<>(),loaded=new HashSet<>();boolean paused;final Map<UUID,Integer> requested=new HashMap<>();}
 private static final Map<Store,Session> runtime=new WeakHashMap<>();
 static StoryCommitTransaction.Result begin(ServerPlayerEntity p,Store store,StoryEventCatalog.Event event,ScopedStory.Position pos)throws Exception{
  var spec=event.narrative().sharedScene();var exit=StoryAuthority.definition(p.getServer(),spec.completion);
  if(!exit.serverOnly()||!exit.once()||exit.narrative()!=null||store.data.completedEvents.contains(exit.key()))throw new IllegalStateException("invalid scene completion");
  var access=(Access)(Object)store.data;if(access.whiteout$scene()!=null)throw new IllegalStateException("another scene active");
  access.whiteout$scene(new Playing(UUID.randomUUID(),spec,0));boolean ok=false;
  try{var result=ScopedStory.apply(store,p.getUuid(),event,pos);ok=result==StoryCommitTransaction.Result.COMMITTED;return result;}
  finally{if(!ok)access.whiteout$scene(null);}
 }
 private static void send(ServerPlayerEntity p,Playing r,String kind,String value){
  if(ServerPlayNetworking.canSend(p,DialogueBarrier.Cue.ID))ServerPlayNetworking.send(p,new DialogueBarrier.Cue(r.id,kind,value));
 }
 static void response(ServerPlayerEntity p,DialogueBarrier.Cue cue){
  var s=p.getServer();var test=DuoTestMode.active(p);var store=test==null?Store.get(s):test.store;var r=current(store);var d=LobbyState.get(store);var session=runtime.get(store);
  if(r==null||session==null||!r.id.equals(cue.id())||!Party.member(d,p.getUuid())||!session.sent.contains(p.getUuid()))return;
  if(cue.kind().equals("load_error")&&!session.loaded.contains(p.getUuid())){fallback(p,r,session);return;}
  if(cue.kind().equals("loaded"))session.loaded.add(p.getUuid());else if(cue.kind().equals("skip")&&session.loaded.contains(p.getUuid()))session.votes.add(p.getUuid());
 }
 private static void fallback(ServerPlayerEntity p,Playing r,Session st){st.loaded.add(p.getUuid());send(p,r,"finish","");p.sendMessage(net.minecraft.text.Text.literal("Камера недоступна. Сервер продолжит отсчёт сцены без изображения."));}
 static void tick(MinecraftServer s,Store store,DuoTestMode test){
  var r=current(store);if(r==null||store.error!=null){runtime.remove(store);return;}
  var d=LobbyState.get(store);if(d==null||!d.started())return;var st=runtime.computeIfAbsent(store,k->new Session());
  if(!r.id.equals(st.id)){st.id=r.id;st.elapsed=r.elapsed;st.sent.clear();st.loaded.clear();st.votes.clear();st.requested.clear();}
  var players=new ArrayList<ServerPlayerEntity>();for(var id:new UUID[]{d.first(),d.second()})if(id!=null){var p=s.getPlayerManager().getPlayer(id);if(p!=null&&(test==null||id.equals(test.operator)))players.add(p);}
  boolean pause=(test==null?Party.paused(s):!test.connected&&!test.alone)||players.isEmpty();
  for(var p:players)if(st.sent.add(p.getUuid())){st.requested.put(p.getUuid(),s.getTicks());send(p,r,"scene",r.spec.scene+" "+st.elapsed);send(p,r,"pause","1");}
  for(var p:players)if(!st.loaded.contains(p.getUuid())&&s.getTicks()-st.requested.getOrDefault(p.getUuid(),s.getTicks())>=200)fallback(p,r,st);
  boolean waiting=pause||(test!=null&&test.connected&&!test.botLoaded(r.id(),s.getTicks()))||players.stream().anyMatch(p->!st.loaded.contains(p.getUuid()));
  if(st.paused!=waiting||!waiting&&st.elapsed==r.elapsed){for(var p:players)send(p,r,"pause",waiting?"1":"0");st.paused=waiting;}
  if(waiting)return;
  var access=(Access)(Object)store.data;st.elapsed=Math.min(r.spec.ticks,st.elapsed+1);
  try{
   if(st.elapsed>=r.spec.ticks||(test==null||!test.connected&&test.alone||test.botSkip)&&players.stream().allMatch(p->st.votes.contains(p.getUuid()))){
    var exit=StoryAuthority.definition(s,r.spec.completion);if(!exit.serverOnly()||!exit.once()||exit.narrative()!=null)throw new IllegalArgumentException("completion changed");
    boolean eligible=players.stream().anyMatch(p->exit.area().contains(p.getServerWorld().getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ())&&StoryWorldConditions.evaluate(exit.world(),new StoryWorldSource(p,store.data))==WinterCrisisConditions.Truth.TRUE);
    if(!eligible)return;
    for(var scene:exit.scenes())if(!CrisisRuntime.requestable(s,scene))return;
    var grants=(SceneDelivery.Access)(Object)store.data;var old=grants.whiteout$grants();grants.whiteout$grants(SceneDelivery.prepare(s,store,exit.key(),r.spec.deliveries));
    access.whiteout$scene(null);boolean committed=false;
    try{committed=StoryCommitTransaction.apply(store,exit)==StoryCommitTransaction.Result.COMMITTED;}finally{if(!committed){access.whiteout$scene(r);grants.whiteout$grants(old);}}
    if(committed){for(var p:players)send(p,r,"finish","");runtime.remove(store);WhiteoutNetwork.syncAll(s);}
   }else if(st.elapsed%100==0){
    access.whiteout$scene(new Playing(r.id,r.spec,st.elapsed));try{store.save();}catch(Exception failure){access.whiteout$scene(r);throw failure;}
   }
  }catch(Exception ex){if(s.getTicks()%200==0)org.slf4j.LoggerFactory.getLogger("whiteout-story").warn("Shared scene waiting for completion",ex);}
 }
 static void forget(Store store){runtime.remove(store);}
 static void register(){SceneDelivery.register();ServerTickEvents.END_SERVER_TICK.register(s->tick(s,Store.get(s),null));ServerLifecycleEvents.SERVER_STOPPED.register(s->runtime.clear());
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{var st=runtime.get(Store.get(s));if(st!=null){UUID id=h.getPlayer().getUuid();st.sent.remove(id);st.loaded.remove(id);st.votes.clear();st.requested.remove(id);}});}
}
