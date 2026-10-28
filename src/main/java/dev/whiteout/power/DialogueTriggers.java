package dev.whiteout.power;

import dev.whiteout.client.DialogueManager;
import dev.whiteout.client.director.DirectorLibrary;
import dev.whiteout.client.director.DirectorScene;
import dev.whiteout.client.director.DirectorTriggerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Authoring adapter around the existing Whiteout Director. No separate trigger store. */
public final class DialogueTriggers {
 private DialogueTriggers(){}
 private static String uniqueScene(String script){
  String safe=script.replaceAll("[^a-zA-Z0-9_-]","_");
  if(safe.length()>45)safe=safe.substring(0,45);
  String prefix="wdlg_"+safe;
  String candidate=prefix;int i=2;
  while(DirectorLibrary.get(candidate)!=null)candidate=prefix+"_"+(i++);
  return candidate;
 }
 public static String bind(String script,Kind kind,MinecraftClient mc,int radius,boolean once,Entity npc){
  if(!DialogueRouteClient.isRoute(script)&&DialogueManager.findById(script)==null)throw new IllegalArgumentException("Сначала сохрани диалог");
  if(mc==null||mc.player==null||mc.world==null)throw new IllegalStateException("Мир не загружен");
  if(kind==Kind.NPC&&npc==null)throw new IllegalArgumentException("Наведись на NPC до открытия каталога");
  String sceneId=uniqueScene(script);
  DirectorScene scene=new DirectorScene();scene.id=sceneId;
  scene.durationTicks=2;scene.lockPlayer=false;scene.hideHud=false;scene.skippable=true;
  DirectorScene.Event event=new DirectorScene.Event(0,DirectorScene.EventType.DIALOGUE,script,"","");
  event.durationTicks=140;scene.events.add(event);
  if(!DirectorLibrary.save(scene))throw new IllegalStateException("Не получилось сохранить сцену");
  DirectorTriggerManager.Trigger trigger=null;
  try {
   switch(kind){
    case AREA -> {
     trigger=DirectorTriggerManager.createArea(sceneId,mc);
     if(trigger!=null){
      BlockPos p=mc.player.getBlockPos();int r=Math.max(1,Math.min(64,radius));
      trigger.x1=p.getX()-r;trigger.y1=p.getY()-r;trigger.z1=p.getZ()-r;
      trigger.x2=p.getX()+r+1;trigger.y2=p.getY()+r+1;trigger.z2=p.getZ()+r+1;
     }
    }
    case NPC -> trigger=DirectorTriggerManager.createEntity(sceneId,npc);
    case SIGNAL -> {
     trigger=DirectorTriggerManager.createSignal(sceneId);
     if(trigger!=null)trigger.target="wdlg:"+script;
    }
   }
   if(trigger==null)throw new IllegalStateException("Не удалось создать триггер");
   trigger.once=once;trigger.enabled=true;trigger.delayTicks=0;
   if(!DirectorTriggerManager.save(trigger))throw new IllegalStateException("Не удалось сохранить триггер");
   DirectorTriggerManager.resetCompleted(trigger.id);
   return trigger.id;
  }catch(RuntimeException ex){
   if(trigger!=null)DirectorTriggerManager.delete(trigger.id);
   DirectorLibrary.delete(sceneId);
   throw ex;
  }
 }
 public enum Kind {AREA,NPC,SIGNAL}

 /** Only auto-generated dialogue-only scenes bypass DirectorPlayer's camera swap.
  * Called by a narrowly scoped ASM hook; all other scenes keep vanilla Whiteout behaviour. */
 public static boolean playSimpleScene(DirectorScene scene){
  if(scene==null||scene.id==null||!scene.id.startsWith("wdlg_")||scene.events==null||scene.events.size()!=1)return false;
  DirectorScene.Event e=scene.events.get(0);
  if(e==null||e.type!=DirectorScene.EventType.DIALOGUE||e.value==null)return false;
  // A deleted script does not turn into a camera-stealing Director scene.
  if(!DialogueRouteClient.isRoute(e.value)&&DialogueManager.findById(e.value)==null)return true;
  DialogueManager.openDirector(e.value,Math.max(80,e.durationTicks));
  return true;
 }
}
