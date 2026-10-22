package dev.whiteout.power.backpack;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;

public final class BackpackVisualClient {
 private record Pose(float from,boolean open,long changed,long started,long seen){
  float amount(long now){return BackpackPosePolicy.blend(from,open?1:0,now-changed);}
 }
 private static final Map<UUID,Pose> poses=new HashMap<>();
 private static Object world;
 private static boolean sent;
 private static int ticks;
 private static void update(UUID id,boolean open,int age){
  long now=Util.getMeasuringTimeMs();Pose old=poses.get(id);
  if(old!=null&&old.open==open){poses.put(id,new Pose(old.from,open,old.changed,old.started,now));return;}
  long elapsed=Math.max(0,Math.min(age,1100));
  poses.put(id,new Pose(old==null?0:old.amount(now),open,now-elapsed,now-elapsed,now));
 }
 public static float amount(PlayerEntity player){
  if(player.getWorld()!=world||!BackpackNetwork.eligible(player))return 0;
  Pose p=poses.get(player.getUuid());return p==null?0:p.amount(Util.getMeasuringTimeMs());
 }
 public static float unpack(PlayerEntity player){
  Pose p=poses.get(player.getUuid());if(p==null||!BackpackNetwork.eligible(player))return 0;
  return p.open?BackpackOpening.phase(Util.getMeasuringTimeMs()-p.started,220,800):p.amount(Util.getMeasuringTimeMs());
 }
 public static float lid(PlayerEntity player){
  Pose p=poses.get(player.getUuid());if(p==null||!p.open)return 0;
  return BackpackOpening.phase(Util.getMeasuringTimeMs()-p.started,600,1100);
 }
 private static void tick(MinecraftClient client){
  if(world!=client.world){poses.clear();sent=false;ticks=0;world=client.world;}
  if(client.player==null||client.world==null)return;
  boolean open=client.currentScreen instanceof BackpackScreen&&BackpackNetwork.eligible(client.player);
  update(client.player.getUuid(),open,0);
  ticks++;
  if((sent!=open||(open&&ticks>=20))&&ClientPlayNetworking.canSend(BackpackNetwork.Request.ID)){
   ClientPlayNetworking.send(new BackpackNetwork.Request(open));sent=open;ticks=0;
  }
  long now=Util.getMeasuringTimeMs();
  for(var entry:new ArrayList<>(poses.entrySet())){
   Pose p=entry.getValue();
   if(p.open&&now-p.seen>4000)update(entry.getKey(),false,0);
   else if(!p.open&&now-p.changed>500)poses.remove(entry.getKey());
  }
 }
 public static void initialize(){
  ClientPlayNetworking.registerGlobalReceiver(BackpackNetwork.State.ID,(p,c)->{
   var connection=c.client().getNetworkHandler();
   c.client().execute(()->{
    var client=c.client();
    if(client.getNetworkHandler()!=connection||client.world==null||client.player==null||
      !client.world.getRegistryKey().getValue().equals(p.dimension())||client.player.getUuid().equals(p.player()))return;
    update(p.player(),p.open(),p.age());
   });
  });
  ClientTickEvents.END_CLIENT_TICK.register(BackpackVisualClient::tick);
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{poses.clear();world=null;sent=false;ticks=0;});
  LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{
   if(renderer instanceof PlayerEntityRenderer player)helper.register(new BackpackFeatureRenderer(player));
  });
 }
}
