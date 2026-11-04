package dev.whiteout.power;

import dev.whiteout.client.Bridge;
import dev.whiteout.client.director.*;
import dev.whiteout.power.mixin.SharedDirectorAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.UUID;

public final class SharedSceneClient{
 private static UUID run;private static String scene;private static boolean paused;
 public static boolean owns(){return run!=null&&DirectorPlayer.playing()&&DirectorPlayer.scene().id.equals(scene);}
 public static boolean paused(){return owns()&&paused;}
 public static boolean skip(){if(!owns())return false;
  ClientPlayNetworking.send(new DialogueBarrier.Cue(run,"skip",""));Bridge.message("Запрошен совместный пропуск");return true;
 }
 static void clear(){run=null;scene=null;paused=false;}
 static boolean control(DialogueBarrier.Cue cue){
  if(cue.kind().equals("scene")){
   String[] values=cue.value().split(" ",2);if(values.length!=2)return true;var asset=DirectorLibrary.get(values[0]);
   if(asset==null){Bridge.message("Отсутствует сцена: "+values[0]);return true;}
   // Gameplay effects are committed by the server completion event. The local track supplies camera and dialogue.
   var visual=dev.whiteout.Store.GSON.fromJson(dev.whiteout.Store.GSON.toJson(asset),DirectorScene.class);
   visual.events.removeIf(ev->ev.type!=DirectorScene.EventType.MESSAGE&&ev.type!=DirectorScene.EventType.DIALOGUE&&ev.type!=DirectorScene.EventType.RADIO);
   int elapsed=Integer.parseInt(values[1]);visual.events.removeIf(ev->elapsed>0&&ev.tick<=elapsed);
   if(!DirectorPlayer.play(visual))return true;run=cue.id();scene=asset.id;paused=true;
   SharedDirectorAccess.whiteout$seek(Math.min(elapsed,visual.durationTicks));ClientPlayNetworking.send(new DialogueBarrier.Cue(run,"loaded",""));return true;
  }
  if(cue.kind().equals("pause")){if(cue.id().equals(run))paused=cue.value().equals("1");return true;}
  if(cue.kind().equals("finish")){if(cue.id().equals(run)){boolean stop=owns();clear();if(stop)DirectorPlayer.stop(false);}return true;}
  return false;
 }
}
