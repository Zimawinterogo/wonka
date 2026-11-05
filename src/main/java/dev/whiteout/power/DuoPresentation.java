package dev.whiteout.power;

import dev.whiteout.client.*;
import dev.whiteout.client.director.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import java.util.*;

public final class DuoPresentation {
 private static final Deque<DialogueBarrier.Cue> queue=new ArrayDeque<>();
 private static DialogueBarrier.Cue reading;
 private static boolean finished;
 private static String readingScript;
 public static void finished(String script){if(reading!=null&&readingScript.equalsIgnoreCase(script))finished=true;}
 public static void resolved(String requested,String script){if(reading!=null&&readingScript.equalsIgnoreCase(requested))readingScript=script;}
 static boolean openDialogue(DialogueBarrier.Cue cue,java.util.function.Predicate<String> opener){
  if(!opener.test(cue.value()))return false;
  reading=cue;readingScript=cue.value();finished=false;return true;
 }
 static boolean duplicate(DialogueBarrier.Cue cue){return reading!=null&&reading.id().equals(cue.id())||queue.stream().anyMatch(pending->pending.id().equals(cue.id()));}
 static void register(){
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{queue.clear();reading=null;SharedSceneClient.clear();});
  ClientPlayNetworking.registerGlobalReceiver(DialogueBarrier.Cue.ID,(m,c)->c.client().execute(()->{if(m.kind().equals("test_scope")){queue.clear();reading=null;StoryAuthorityClient.cancel();if(c.client().currentScreen instanceof DialogueScreen)c.client().setScreen(null);
   ClientPlayNetworking.send(new DialogueBarrier.Cue(m.id(),"scope_ack",""));return;}
   if(!SharedSceneClient.control(m)&&!duplicate(m)){
    if(queue.size()<32)queue.addLast(m);
    else if(m.kind().equals("dialogue"))ClientPlayNetworking.send(new DialogueBarrier.Cue(m.id(),"read_error",""));
   }
  }));
  ClientTickEvents.END_CLIENT_TICK.register(mc->{
   if(mc.player==null)return;
   if(reading!=null){
    if(!finished&&(mc.currentScreen instanceof DialogueScreen||StoryDialogueHud.contains(readingScript)||DialogueRouteClient.pending()))return;
    ClientPlayNetworking.send(new DialogueBarrier.Cue(reading.id(),finished?"read":"read_error",""));reading=null;
   }
   var cue=queue.peekFirst();if(cue==null)return;
   if(cue.kind().equals("agreed")){if(StoryAuthorityClient.busy())return;queue.removeFirst();StoryAuthorityClient.agreed(cue.value());return;}
   if(mc.currentScreen!=null||DirectorPlayer.playing()||StoryAuthorityClient.busy())return;queue.removeFirst();
   if(cue.kind().equals("dialogue")){
    if(!openDialogue(cue,DialogueManager::open)){
     ClientPlayNetworking.send(new DialogueBarrier.Cue(cue.id(),"read_error",""));
     Bridge.message("Не удалось открыть диалог: "+cue.value()+". Проверьте dialogues.json; повтор: /whiteout lobby retry-dialogue.");
    }
   }
   else if(cue.kind().equals("trigger")&&cue.value().startsWith("cutscene_trigger:")){
    var trigger=DirectorTriggerManager.get(cue.value().substring("cutscene_trigger:".length()));if(trigger==null)return;var effects=MigratedTriggerClient.presentation(trigger.effects);
    if(StoryEffects.preflight(effects)&&StoryEffects.run(effects)&&!StoryEffects.hasBlocking(effects)&&!trigger.sceneId.isBlank())DirectorPlayer.play(trigger.sceneId);
   }
  });
 }
}
