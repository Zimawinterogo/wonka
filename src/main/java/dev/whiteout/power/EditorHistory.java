package dev.whiteout.power;

import com.google.gson.*;
import dev.whiteout.*;
import dev.whiteout.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Server-thread only, one bounded history per live connection and dimension. */
public final class EditorHistory {
 private EditorHistory(){}
 private static final int LIMIT=30, MAX_BYTES=2*1024*1024;
 private record Entry(String id,EditorDelta delta,boolean operator){}
 private static final class Session {
  String dimension; final Deque<Entry> undo=new ArrayDeque<>();int bytes;
  Session(String d){dimension=d;}
 }
 private static final Map<ServerPlayerEntity,Session> sessions=new WeakHashMap<>();
 private record Pending(String id,JsonObject before){}
 private static final Map<ServerPlayerEntity,Pending> pending=new WeakHashMap<>();
 private static Session session(ServerPlayerEntity p){
  String d=p.getWorld().getRegistryKey().getValue().toString();
  Session s=sessions.get(p);
  if(s==null||!s.dimension.equals(d)){s=new Session(d);sessions.put(p,s);}
  return s;
 }
 public static boolean allowed(ServerPlayerEntity p){return p.hasPermissionLevel(2)&&p.isCreative()&&(p.getMainHandStack().isOf(Whiteout.CONTROLLER)||p.getOffHandStack().isOf(Whiteout.CONTROLLER));}
 public static Zone find(ServerPlayerEntity p,String id){for(Zone z:Store.get(p.getServer()).data.zones)if(z!=null&&id.equals(z.id))return z;return null;}
 public static JsonObject capture(ServerPlayerEntity p,String id){
  JsonObject o=new JsonObject();
  if(!id.isEmpty()){
   Zone z=find(p,id);o.add("zone",z==null?JsonNull.INSTANCE:Store.GSON.toJsonTree(z));
   o.addProperty("index",z==null?-1:Store.get(p.getServer()).data.zones.indexOf(z));
   o.add("atmosphere",WorldAtmosphere.editorSnapshot(p.getServer(),id));
  }
  o.add("folders",ZoneFolders.editorSnapshot(p.getServer()));return o;
 }
 public static void begin(ServerPlayerEntity p,WhiteoutActionPayload packet){
  pending.remove(p);
  if(!allowed(p)||!(packet.action().equals("SAVE_ZONE")||packet.action().equals("DELETE_ZONE")))return;
  try{
   String raw=new String(packet.data(),StandardCharsets.UTF_8);
   String id=packet.action().equals("SAVE_ZONE")?JsonParser.parseString(raw).getAsJsonObject().get("id").getAsString():raw;
   if(id.isBlank()||id.length()>64)return;
   pending.put(p,new Pending(id,capture(p,id)));
  }catch(RuntimeException ignored){} // Original handler owns malformed-packet errors.
 }
 public static void finish(ServerPlayerEntity p,WhiteoutActionPayload packet){
  Pending q=pending.remove(p);
  if(q==null){if(packet.action().equals("SAVE_ZONE")||packet.action().equals("DELETE_ZONE"))EditorNetwork.status(p,false,true);return;}
  if(Objects.equals(q.before.get("zone"),capture(p,q.id).get("zone"))){EditorNetwork.status(p,false,true);return;}
  // Reconcile before recording, so deletion and its folder cleanup are one action.
  ZoneFolders.editorReconcile(p.getServer());record(p,q.id,q.before,false);
  EditorNetwork.status(p,true,true,q.id);
 }
 public static void record(ServerPlayerEntity p,String id,JsonObject before,boolean operator){
  EditorDelta delta=new EditorDelta(before,capture(p,id));if(delta.empty())return;
  Session s=session(p);int size=delta.bytes();
  if(size>MAX_BYTES){s.undo.clear();s.bytes=0;p.sendMessage(Text.literal("Действие слишком велико для Undo; история очищена."),false);return;}
  s.undo.addLast(new Entry(id,delta,operator));s.bytes+=size;
  while(s.undo.size()>LIMIT||s.bytes>MAX_BYTES)s.bytes-=s.undo.removeFirst().delta.bytes();
 }
 public static int count(ServerPlayerEntity p){return session(p).undo.size();}
 public static String undo(ServerPlayerEntity p){
  if(!allowed(p))throw new IllegalStateException("Нужны креатив и метеорезонатор в руке.");
  Session s=session(p);Entry e=s.undo.peekLast();if(e==null)return "";
  if(e.operator&&!p.hasPermissionLevel(2))throw new IllegalStateException("Нужны права оператора уровня 2.");
  JsonObject current=capture(p,e.id),target=e.delta.undo(current);
  apply(p,e.id,current,target,e.delta);
  s.undo.removeLast();s.bytes-=e.delta.bytes();
  WhiteoutNetwork.syncAll(p.getServer());
  return e.id;
 }
 public static void apply(ServerPlayerEntity p,String id,JsonObject before,JsonObject after,EditorDelta delta){
  Store store=Store.get(p.getServer());
  if(store.error!=null)throw new IllegalStateException("Хранилище зон недоступно: "+store.error);
  List<Zone> old=store.data.zones;
  boolean zone=delta.touches("zone")||delta.touches("index"),folders=delta.touches("folders"),atmosphere=delta.touches("atmosphere");
  try{
   if(zone){
    List<Zone> next=new ArrayList<>(old);next.removeIf(z->id.equals(z.id));
    if(!after.get("zone").isJsonNull()){
     Zone z=Store.GSON.fromJson(after.get("zone"),Zone.class);z.validate();
     if(!id.equals(z.id)||next.size()>=256)throw new IllegalStateException("Недопустимое восстановление зоны.");
     next.add(Math.max(0,Math.min(next.size(),after.get("index").getAsInt())),z);
    }
    store.data.zones=next;
    if(Store.GSON.toJson(store.networkSnapshot()).getBytes(StandardCharsets.UTF_8).length>0x200000)throw new IllegalStateException("Состояние превышает существующий лимит синхронизации 2 МиБ.");
    store.save();
   }
   if(folders)ZoneFolders.editorRestore(p.getServer(),after.getAsJsonObject("folders"));
   if(atmosphere)WorldAtmosphere.editorRestore(p.getServer(),id,after.getAsJsonObject("atmosphere"));
  }catch(Exception failure){
   store.data.zones=old;
   try{if(zone)store.save();if(folders)ZoneFolders.editorRestore(p.getServer(),before.getAsJsonObject("folders"));if(atmosphere)WorldAtmosphere.editorRestore(p.getServer(),id,before.getAsJsonObject("atmosphere"));}
   catch(Exception rollback){failure.addSuppressed(rollback);System.err.println("[Whiteout] Editor rollback failed: "+failure);}
   throw new IllegalStateException("Не удалось сохранить изменение: "+failure.getMessage(),failure);
  }
 }
 public static void register(){
  net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p,from,to)->{sessions.remove(p);pending.remove(p);});
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{sessions.remove(h.getPlayer());pending.remove(h.getPlayer());});
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->{sessions.clear();pending.clear();});
 }
}
