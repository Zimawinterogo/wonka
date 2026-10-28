package dev.whiteout.power;

import com.google.gson.Gson;
import java.util.*;
import dev.whiteout.client.director.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import static dev.whiteout.power.PassportIssue.Severity.*;

/** Client-only catalogue/Director inspection, never referenced by server collectors. */
public final class PassportClient {
 private static final SnapshotChunks.Receiver RECEIVER=new SnapshotChunks.Receiver();
 private static int requested,receiving=-1;
 public static void register(){
  ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->{RECEIVER.clear();requested=0;receiving=-1;});
  ClientPlayNetworking.registerGlobalReceiver(PassportNetwork.Part.ID,(p,c)->c.client().execute(()->{
   if(c.client().player==null||(p.token()!=0&&p.token()!=requested))return;
   try{
    if(p.index()==0){RECEIVER.clear();receiving=p.token();}
    if(receiving!=p.token())return;
    String json=RECEIVER.accept(p.index(),p.total(),p.json());if(json==null)return;
    var report=new Gson().fromJson(json,MapPassport.class);
    if(report==null||report.schema()!=1||report.entries()==null||report.issues()==null||report.entries().size()>MapPassport.LIMIT||report.issues().size()>MapPassport.LIMIT)return;
    var b=new MapPassport.Builder();b.add(report);collect(b);report=b.build();
    if(c.client().currentScreen instanceof PassportScreen screen)screen.update(report);
    else c.client().setScreen(new PassportScreen(report));
   }catch(RuntimeException ex){RECEIVER.clear();receiving=-1;}
  }));
 }
 public static void request(){
  if(MinecraftClient.getInstance().getNetworkHandler()==null)return;
  requested=requested==Integer.MAX_VALUE?1:requested+1;RECEIVER.clear();receiving=-1;
  ClientPlayNetworking.send(new PassportNetwork.Request(requested));
 }
 private static void collect(MapPassport.Builder b){
  try{
   var entries=DialogueCatalog.entries();Set<String> known=new HashSet<>(),used=new HashSet<>(),scenes=new HashSet<>();
   for(var e:entries)known.add(e.id());
   for(var scene:DirectorLibrary.all()){
    scenes.add(scene.id);b.entry("dialogues","scene: "+scene.id,null,Map.of("source","client"));
    if(scene.events==null){b.issue(WARN,"invalid_trigger",scene.id,null);continue;}
    for(var event:scene.events)if(event!=null&&event.type==DirectorScene.EventType.DIALOGUE){
     used.add(event.value);if(!known.contains(event.value))b.issue(WARN,"invalid_trigger",scene.id+" -> "+event.value,null);
    }
   }
   for(var t:DirectorTriggerManager.all()){
    if(t.effects!=null)for(var effect:t.effects)if(effect!=null&&effect.type==dev.whiteout.client.StoryEffects.Type.DIALOGUE){
     used.add(effect.target);
     if(!known.contains(effect.target))b.issue(WARN,"invalid_trigger",t.id+" -> "+effect.target,null);
    }
    var p=t.areaType()?new MapPassport.Position(t.dimension,(int)t.x1,(int)t.y1,(int)t.z1):null;
    b.entry("dialogues","trigger: "+t.id,p,Map.of("scene",String.valueOf(t.sceneId),"enabled",""+t.enabled,"type",String.valueOf(t.type),"source","client"));
    // Action-only story triggers do not require a Director scene.
    if(t.type==null||t.delayTicks<0||(t.sceneId!=null&&!t.sceneId.isBlank()&&!scenes.contains(t.sceneId))||
     (t.areaType()&&!PassportRules.bounds(t.x1,t.y1,t.z1,t.x2,t.y2,t.z2)))b.issue(WARN,"invalid_trigger",t.id,p);
   }
   for(var e:entries){
    b.entry("dialogues",e.id(),null,Map.of("lines",""+e.lines().size(),"builtin",""+DialogueCatalog.isBuiltin(e.id()),"source","client"));
    PassportRules.dialogue(b,e.id(),DialogueCatalog.isBuiltin(e.id()),used.contains(e.id()));
   }
  }catch(RuntimeException ex){b.issue(INFO,"unavailable","client dialogues",null);}
 }
}
