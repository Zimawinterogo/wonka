package dev.whiteout.power;

import dev.whiteout.client.*;
import dev.whiteout.client.director.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import java.util.*;

final class DuoPresentation {
 private static final Deque<DialogueBarrier.Cue> queue=new ArrayDeque<>();
 private static DialogueBarrier.Cue reading;
 static void register(){
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{queue.clear();reading=null;SharedSceneClient.clear();});
  ClientPlayNetworking.registerGlobalReceiver(DialogueBarrier.Cue.ID,(m,c)->c.client().execute(()->{if(!SharedSceneClient.control(m)&&queue.size()<32)queue.addLast(m);}));
  ClientTickEvents.END_CLIENT_TICK.register(mc->{
   if(mc.player==null)return;
   if(reading!=null){
    if(mc.currentScreen instanceof DialogueScreen||reading.value().equals(DialogueManager.activeId()))return;
    ClientPlayNetworking.send(new DialogueBarrier.Cue(reading.id(),"read",""));reading=null;
   }
   var cue=queue.peekFirst();if(cue==null)return;
   if(cue.kind().equals("agreed")){if(StoryAuthorityClient.busy())return;queue.removeFirst();StoryAuthorityClient.agreed(cue.value());return;}
   if(mc.currentScreen!=null||DirectorPlayer.playing()||StoryAuthorityClient.busy())return;queue.removeFirst();
   if(cue.kind().equals("dialogue")){reading=cue;DialogueManager.open(cue.value());}
   else if(cue.kind().equals("trigger")&&cue.value().startsWith("cutscene_trigger:")){
    var trigger=DirectorTriggerManager.get(cue.value().substring("cutscene_trigger:".length()));if(trigger==null)return;var effects=MigratedTriggerClient.presentation(trigger.effects);
    if(StoryEffects.preflight(effects)&&StoryEffects.run(effects)&&!StoryEffects.hasBlocking(effects)&&!trigger.sceneId.isBlank())DirectorPlayer.play(trigger.sceneId);
   }
  });
 }
}
