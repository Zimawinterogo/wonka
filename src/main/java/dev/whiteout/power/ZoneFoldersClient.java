package dev.whiteout.power;

import com.google.gson.Gson;
import dev.whiteout.Zone;
import dev.whiteout.client.Bridge;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** The original resonator list is filtered only for display; underlying zones stay intact. */
public final class ZoneFoldersClient {
 private ZoneFoldersClient(){}
 public static String currentFolder=null; // null = all, empty = unfiled, otherwise named folder
 private static ZoneFolders.Data folders=new ZoneFolders.Data();
 private static final Gson GSON=new Gson();
 private static final SnapshotChunks.Receiver receiver=new SnapshotChunks.Receiver();
 public static ZoneFolders.Data get(){return folders;}
 public static void initialize(){
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{
   receiver.clear();folders=new ZoneFolders.Data();currentFolder=null;
  });
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{
   if(!(mc.currentScreen instanceof dev.whiteout.client.ZoneScreen) &&
      !(mc.currentScreen instanceof ZoneFolderScreen))currentFolder=null;
  });
  ClientPlayNetworking.registerGlobalReceiver(ZoneFolders.Snapshot.ID,(p,c)->c.client().execute(()->{
   try{
    String complete=receiver.accept(p.index(),p.total(),p.json());if(complete==null)return;
    var json=com.google.gson.JsonParser.parseString(complete);
    FolderDataValidation.validate(json);
    ZoneFolders.Data d=GSON.fromJson(json,ZoneFolders.Data.class);
    if(d==null||d.folders==null||d.assigned==null)return;
    if(d.order==null)d.order=new LinkedHashMap<>();
    folders=d;
    if(currentFolder!=null&&!currentFolder.isEmpty()&&!d.folders.contains(currentFolder))currentFolder=null;
    if(c.client().currentScreen instanceof ZoneFolderScreen screen)screen.refreshFromServer();
    else if(c.client().currentScreen instanceof dev.whiteout.client.ZoneScreen screen)screen.refresh();
   }catch(Exception ex){System.err.println("[Whiteout] Invalid folder snapshot: "+ex);}
  }));
 }
 public static List<Zone> sortedForFolder(List<Zone> original,String folder){
  if(original==null)return List.of();
  List<Zone> out=new ArrayList<>();
  for(Zone z:original){if(z==null||z.id==null)continue;
   if(folder.equals(folders.assigned.getOrDefault(z.id,"")))out.add(z);
  }
  List<String> order=folders.order==null?null:folders.order.get(folder);
  if(order!=null&&!order.isEmpty()){
   Map<String,Integer> rank=new HashMap<>();
   for(int i=0;i<order.size();i++)rank.put(order.get(i),i);
   out.sort(Comparator.comparingInt(z->rank.getOrDefault(z.id,Integer.MAX_VALUE)));
  }
  return out;
 }
 public static List<Zone> visible(List<Zone> original){
  return currentFolder==null?original:sortedForFolder(original,currentFolder);
 }
 /** The Meteoresonator opens folders first; an explicitly selected folder
  * bypasses this redirect so the original editor stays available.
  * With no named folders the folder list would only contain "unfiled", so it is skipped entirely
  * (folders can still be created with the "Папки зон" button). */
 public static void redirectUnfiledEditor(dev.whiteout.client.ZoneScreen screen){
  if(currentFolder!=null)return;
  if(folders.folders.isEmpty()){currentFolder="";return;}
  MinecraftClient.getInstance().setScreen(new ZoneFolderScreen());
 }
 /** Opens the zone editor for a folder in one step. If a zone is given it is already selected
  * (same state as clicking it in the editor's list); no second list-click is needed. */
 public static void openEditor(String folder,Zone zone){
  currentFolder=folder;
  dev.whiteout.client.ZoneScreen screen=new dev.whiteout.client.ZoneScreen();
  if(zone!=null){
   var access=(dev.whiteout.power.mixin.ZoneScreenAccess)(Object)screen;
   access.whiteout$setDraft(zone.copy());
   int index=0;List<Zone> visible=visible(Bridge.snapshot==null?null:Bridge.snapshot.zones);
   for(int i=0;i<visible.size();i++)if(visible.get(i)!=null&&zone.id!=null&&zone.id.equals(visible.get(i).id)){index=i;break;}
   access.whiteout$setListPage(index/6);
  }
  MinecraftClient.getInstance().setScreen(screen);
 }
 public static Runnable opener(){return ()->MinecraftClient.getInstance().setScreen(new ZoneFolderScreen());}
 public static void edit(String action,String folder,String value){ClientPlayNetworking.send(new ZoneFolders.Edit(action,folder,value));}
}
