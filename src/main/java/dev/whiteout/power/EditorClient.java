package dev.whiteout.power;
import dev.whiteout.Zone;
import dev.whiteout.client.*;
import dev.whiteout.power.mixin.ZoneScreenAccess;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class EditorClient {
 private EditorClient(){}
 public interface Toolbar {void add(Screen screen);}
 public static final List<Toolbar> toolbars=new ArrayList<>();
 private static int count;
 private static int pendingCommits;
 public static void commitSent(){if(ClientPlayNetworking.canSend(EditorNetwork.Request.ID))pendingCommits++;}
 private static boolean waiting(){
  if(pendingCommits<=0)return false;
  var p=MinecraftClient.getInstance().player;if(p!=null)p.sendMessage(Text.literal("Дождитесь подтверждения сохранения сервером."),true);return true;
 }
 public static void send(String action,String id,int flags){
  if(!ClientPlayNetworking.canSend(EditorNetwork.Request.ID))return;
  boolean changes=action.equals("undo")||action.equals("paste");if(changes&&waiting())return;
  if(changes)pendingCommits++;ClientPlayNetworking.send(new EditorNetwork.Request(action,id,flags));
 }
 private static final Map<ZoneScreen,Draft> drafts=new WeakHashMap<>();
 private static final class Draft {Zone last;final Deque<Zone> undo=new ArrayDeque<>();}
 private static Object previousWorld;
 private static void observe(ZoneScreen screen){
  try{observeValid(screen);}catch(RuntimeException ignored){} // Invalid text must remain editable, not crash init/release.
 }
 private static void observeValid(ZoneScreen screen){
  Zone now=((ZoneScreenAccess)(Object)screen).whiteout$getDraft();if(now==null){drafts.remove(screen);return;}
  now.copy().validate();
  Draft d=drafts.computeIfAbsent(screen,s->new Draft());
  if(d.last==null||!now.id.equals(d.last.id)){d.undo.clear();d.last=now.copy();return;}
  if(!dev.whiteout.Store.GSON.toJsonTree(now).equals(dev.whiteout.Store.GSON.toJsonTree(d.last))){
   d.undo.addLast(d.last);while(d.undo.size()>30)d.undo.removeFirst();
   int bytes=0;for(Zone z:d.undo)bytes+=dev.whiteout.Store.GSON.toJson(z).length()*2;
   while(bytes>2*1024*1024&&!d.undo.isEmpty())bytes-=dev.whiteout.Store.GSON.toJson(d.undo.removeFirst()).length()*2;
   d.last=now.copy();
  }
 }
 public static void undo(){
  if(waiting())return;
  Screen current=MinecraftClient.getInstance().currentScreen;
  if(current instanceof ZoneScreen zs){
   if(zs.preview()==null){
    Draft valid=drafts.get(zs);
    if(valid!=null&&valid.last!=null){((ZoneScreenAccess)(Object)zs).whiteout$setDraft(valid.last.copy());zs.refresh();return;}
   }
   observe(zs);Draft d=drafts.get(zs);
   if(d!=null&&!d.undo.isEmpty()){
    Zone previous=d.undo.removeLast();d.last=previous.copy();((ZoneScreenAccess)(Object)zs).whiteout$setDraft(previous.copy());zs.refresh();return;
   }
  }
  send("undo","",0);
 }
 public static void addToolbar(Screen screen){
  if(screen instanceof ZoneScreen zs)observe(zs);
  int x=screen instanceof ZoneScreen?toolbarX(screen):8,y=screen instanceof ZoneScreen?toolbarY(screen):4;
  Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Отменить"),b->undo()).dimensions(x,y,screen instanceof ZoneScreen?toolbarWidth(screen):80,20).build());
  for(Toolbar toolbar:toolbars)toolbar.add(screen);
 }
 public static int toolbarX(Screen s){return Math.max(8,(s.width-640)/2)+144;}
 public static int toolbarY(Screen s){return Math.max(16,(s.height-250)/2)+227;}
 public static int toolbarWidth(Screen s){return Math.max(140,Math.min(450,s.width-toolbarX(s)-12))/2-4;}
 public static void initialize(){
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{if(mc.world!=previousWorld){drafts.clear();count=0;pendingCommits=0;previousWorld=mc.world;}});
  ClientPlayNetworking.registerGlobalReceiver(EditorNetwork.Status.ID,(p,c)->c.client().execute(()->{
   count=p.count();if(p.settled())pendingCommits=Math.max(0,pendingCommits-1);Screen screen=c.client().currentScreen;
   if(p.refresh()&&screen instanceof ZoneScreen zs){
    ZoneScreenAccess access=(ZoneScreenAccess)(Object)zs;Zone selected=access.whiteout$getDraft();
    if(selected!=null&&selected.id.equals(p.zone())){
     drafts.remove(zs);Zone saved=null;for(Zone z:Bridge.snapshot.zones)if(selected.id.equals(z.id)){saved=z.copy();break;}access.whiteout$setDraft(saved);zs.refresh();
    }
   }
   if(p.refresh()&&screen instanceof ZoneFolderScreen folders)folders.refreshFromServer();
  }));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{count=0;pendingCommits=0;drafts.clear();});
  ScreenEvents.AFTER_INIT.register((mc,screen,w,h)->{
   if(!(screen instanceof ZoneScreen)&&!(screen instanceof ZoneFolderScreen))return;
   send("status","",0);
   ScreenMouseEvents.afterMouseRelease(screen).register((s,x,y,button)->{if(s instanceof ZoneScreen zs){zs.preview();observe(zs);}});
   ScreenKeyboardEvents.allowKeyPress(screen).register((s,key,scan,mods)->{
    if(key==GLFW.GLFW_KEY_Z&&Screen.hasControlDown()&&!Screen.hasShiftDown()&&!(s.getFocused() instanceof TextFieldWidget)){undo();return false;}return true;
   });
  });
 }
}
