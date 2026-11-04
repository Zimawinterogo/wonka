package dev.whiteout.power;

import dev.whiteout.client.StoryEffects;
import dev.whiteout.client.director.DirectorPlayer;
import dev.whiteout.client.director.DirectorTriggerManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import java.util.*;

public final class MigratedTriggerClient {
 private MigratedTriggerClient(){}
 private static Set<String> managed=Set.of();
 private static final Map<String,Long> offered=new LinkedHashMap<>();
 private static Object world;
 private static long tick;
 public static boolean managed(String key){return managed.contains(key);}
 public static List<StoryEffects.Effect> presentation(List<StoryEffects.Effect> effects){
  return effects.stream().filter(e->switch(e.type){
   case DIALOGUE,RADIO,SOUND,SCENE->true;
   default->false;
  }).toList();
 }
 private static void clear(){managed=Set.of();offered.clear();world=null;}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(TriggerAuthority.Notice.ID,(notice,context)->context.client().execute(()->{
   if(notice.manifest()){
    clear();world=context.client().world;
    if(!notice.data().isEmpty())managed=Set.copyOf(Arrays.asList(notice.data().split("\\n")));
   }else if(managed(notice.data()))offered.put(notice.data(),tick+180);
  }));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
  ClientTickEvents.END_CLIENT_TICK.register(MigratedTriggerClient::tick);
 }
 private static void tick(MinecraftClient client){
  tick++;
  if(world!=client.world){clear();return;}
  if(offered.isEmpty()||client.player==null||client.currentScreen!=null||StoryAuthorityClient.busy()||DirectorPlayer.playing())return;
  var entry=offered.entrySet().iterator().next();String key=entry.getKey();long deadline=entry.getValue();offered.remove(key);
  if(tick>deadline||!key.startsWith("cutscene_trigger:"))return;
  var trigger=DirectorTriggerManager.get(key.substring("cutscene_trigger:".length()));
  if(trigger==null||!trigger.enabled)return;
  var effects=presentation(trigger.effects);
  if(!StoryEffects.preflight(effects))return;
  StoryAuthorityClient.request(key,()->world==client.world&&managed(key),()->client.currentScreen==null&&!DirectorPlayer.playing(),()->{
   if(!effects.isEmpty()&&!StoryEffects.run(effects))return false;
   if(!trigger.sceneId.isBlank()&&!StoryEffects.hasBlocking(effects)&&!DirectorPlayer.playing())return DirectorPlayer.play(trigger.sceneId);
   return true;
  },null);
 }
}
