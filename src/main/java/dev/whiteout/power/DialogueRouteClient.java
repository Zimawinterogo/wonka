package dev.whiteout.power;

import com.google.gson.Gson;
import dev.whiteout.client.*;
import dev.whiteout.client.director.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Resolves once, then resumes the original dialogue entry point with its original duration. */
public final class DialogueRouteClient {
 private record Call(String id,int duration,DirectorScene scene){}
 private static final DialogueRouteQueue<Call> QUEUE=new DialogueRouteQueue<>();
 private static final SnapshotChunks.Receiver EDITOR=new SnapshotChunks.Receiver();
 private static final Gson GSON=new Gson();
 private static Object world;
 private static long tick,sentAt,editorRequest;
 private static boolean bypass,waiting;
 private static DialogueRouteNetwork.Result ready;
 static DialogueRouteNetwork.EditorData editorData=new DialogueRouteNetwork.EditorData(new DialogueRouteStore.Data(1,0,java.util.List.of()),false,"",0);
 private DialogueRouteClient(){}
 public static Text text(String key,Object... args){return Text.translatable("route.whiteout."+key,args);}
 public static boolean isRoute(String id){return id!=null&&id.startsWith(DialogueRoute.PREFIX);}
 public static void register(){
  ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->clear());
  ClientTickEvents.END_CLIENT_TICK.register(mc->{tick++;if(world!=mc.world){clear();world=mc.world;}pump(mc);});
  ClientPlayNetworking.registerGlobalReceiver(DialogueRouteNetwork.Result.ID,(p,c)->c.client().execute(()->{
   var head=QUEUE.head();if(world!=c.client().world||head==null||head.token()!=p.token()||!waiting)return;
   ready=p;waiting=false;pump(c.client());
  }));
  ClientPlayNetworking.registerGlobalReceiver(DialogueRouteNetwork.Part.ID,(p,c)->c.client().execute(()->{
   try{
    String json=EDITOR.accept(p.index(),p.total(),p.json());if(json==null)return;
    var data=GSON.fromJson(json,DialogueRouteNetwork.EditorData.class);if(data.token()!=editorRequest)return;DialogueRouteStore.validate(data.data());editorData=data;
    if(c.client().currentScreen instanceof DialogueRouteScreen s)s.update(data);
    else if(c.client().currentScreen instanceof DialogueRouteEditScreen s)s.result(data);
   }catch(RuntimeException ex){EDITOR.clear();message("unavailable");}
  }));
 }
 private static void clear(){QUEUE.clear();EDITOR.clear();waiting=false;ready=null;world=null;editorRequest++;editorData=new DialogueRouteNetwork.EditorData(new DialogueRouteStore.Data(1,0,java.util.List.of()),false,"",0);}
 public static boolean intercept(String id,int duration){
  if(bypass)return false;
  if(!isRoute(id)&&QUEUE.empty())return false;
  var mc=MinecraftClient.getInstance();
  if(world!=mc.world){clear();world=mc.world;}
  if(mc.player==null||mc.world==null||!ClientPlayNetworking.canSend(DialogueRouteNetwork.Resolve.ID)){message("unavailable");return true;}
  String route=isRoute(id)?id.substring(DialogueRoute.PREFIX.length()):null;
  if(route!=null&&!route.matches("[a-z0-9_-]{1,64}")){message("id");return true;}
  var item=QUEUE.offer(route,new Call(id,duration,DirectorPlayer.playing()?DirectorPlayer.scene():null),tick);
  if(item!=null)pump(mc);return true;
 }
 private static void pump(MinecraftClient mc){
  var head=QUEUE.head();if(head==null||mc.player==null||mc.world==null)return;
  var call=head.value();
  if(call.scene()!=null&&call.scene()!=DirectorPlayer.scene()){
   QUEUE.complete(head.token());waiting=false;ready=null;return;
  }
  if(waiting){if(tick-sentAt>100){QUEUE.complete(head.token());waiting=false;message("timeout");}return;}
  if(mc.currentScreen instanceof DialogueScreen)return;
  if(head.route()!=null&&ready==null){waiting=true;sentAt=tick;ClientPlayNetworking.send(new DialogueRouteNetwork.Resolve(head.token(),head.route()));return;}
  String id=call.id();
  if(ready!=null){
   if(!ready.error().isEmpty()){message(ready.error());QUEUE.complete(head.token());ready=null;return;}
   id=DialogueRoute.playable(ready.selected(),ready.fallback(),DialogueRouteClient::exists);
   if(id==null){message("missing_dialogue");QUEUE.complete(head.token());ready=null;return;}
  }
  QUEUE.complete(head.token());ready=null;
  bypass=true;
  try{
   if(DialogueManager.findById(id)!=null){if(call.duration()<0)DialogueManager.open(id);else DialogueManager.openDirector(id,call.duration());}
   else if(DialogueCatalog.isBuiltin(id)){
    var script=DialogueCatalog.OUTAGE_ID.equals(id)?DialogueCatalog.builtinOutage():DialogueCatalog.FUSE_ID.equals(id)?DialogueCatalog.builtinFuseFailure():DialogueCatalog.builtinLake(id);
    if(script!=null)StoryDialogueHud.route(mc,new DialogueScreen(script,null,Math.max(1,call.duration())));
   }else message("missing_dialogue");
  }catch(RuntimeException ex){message("unavailable");}finally{bypass=false;}
 }
 public static boolean exists(String id){return id!=null&&(DialogueManager.findById(id)!=null||DialogueCatalog.isBuiltin(id));}
 private static void message(String key){var mc=MinecraftClient.getInstance();if(mc.player!=null)mc.player.sendMessage(text(key),false);}
 public static void open(Screen parent){var mc=MinecraftClient.getInstance();mc.setScreen(new DialogueRouteScreen(parent));edit("list","",0);}
 static void edit(String action,String json,long revision){
  if(ClientPlayNetworking.canSend(DialogueRouteNetwork.Edit.ID))ClientPlayNetworking.send(new DialogueRouteNetwork.Edit(action,json,revision,++editorRequest));else message("unavailable");
 }
 static void save(DialogueRoute route,long revision,boolean create){edit(create?"create":"save",DialogueRouteStore.validateWire(route),revision);}
}
