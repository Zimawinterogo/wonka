package dev.whiteout.power;

import com.google.gson.*;
import dev.whiteout.Store;
import dev.whiteout.Zone;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

/** Author-only zone organization; NEVER changes weather geometry or zone ids. */
public final class ZoneFolders {
 private ZoneFolders(){}
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static final Map<MinecraftServer,Data> DATA=new WeakHashMap<>();
 public static final class Data {
  public List<String> folders=new ArrayList<>();
  public Map<String,String> assigned=new LinkedHashMap<>();
  // Per-folder stable zone order. Empty-string folder is the unfiled group.
  public Map<String,List<String>> order=new LinkedHashMap<>();
  public transient boolean invalid;
 }
 public record Snapshot(int index,int total,String json) implements CustomPayload {
  public static final Id<Snapshot> ID=new Id<>(PowerDevices.id("zone_folder_snapshot"));
  public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.ofStatic((b,p)->{b.writeVarInt(p.index);b.writeVarInt(p.total);b.writeString(p.json,16000);},b->new Snapshot(b.readVarInt(),b.readVarInt(),b.readString(16000)));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Edit(String action,String folder,String value) implements CustomPayload {
  public static final Id<Edit> ID=new Id<>(PowerDevices.id("zone_folder_edit"));
  public static final PacketCodec<RegistryByteBuf,Edit> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeString(p.action,16);b.writeString(p.folder,96);b.writeString(p.value,128);},
   b->new Edit(b.readString(16),b.readString(96),b.readString(128)));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 private static Path path(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("whiteout-zone-folders.json");}
 private static Data load(MinecraftServer s){
  try{
   Data d=new Data();
   if(Files.exists(path(s))){
    JsonElement json=JsonParser.parseString(Files.readString(path(s)));
    FolderDataValidation.validate(json);d=GSON.fromJson(json,Data.class);
   }
   if(d==null||d.folders==null||d.assigned==null)throw new IllegalArgumentException("Missing folder entries");
   if(d.order==null)d.order=new LinkedHashMap<>(); // existing 3.10.9 saves
   for(var e:d.order.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue().contains(null))throw new IllegalArgumentException("Invalid zone order");
   if(new HashSet<>(d.folders).size()!=d.folders.size())throw new IllegalArgumentException("Duplicate folders");
   for(String f:d.folders)if(!valid(f))throw new IllegalArgumentException("Invalid name");
   for(String name:d.assigned.values())if(!d.folders.contains(name))throw new IllegalArgumentException("Unknown folder: "+name);
   return d;
  }catch(Exception e){System.err.println("[Whiteout] Zone folders disabled, original JSON kept: "+e);Data d=new Data();d.invalid=true;return d;}
 }
 private static Data data(MinecraftServer s){return DATA.computeIfAbsent(s,ZoneFolders::load);}
 public static JsonObject editorSnapshot(MinecraftServer s){return GSON.toJsonTree(data(s)).getAsJsonObject();}
 public static void editorReconcile(MinecraftServer s){reconcile(s);}
 public static void editorRestore(MinecraftServer s,JsonObject json){
  if(data(s).invalid)throw new IllegalStateException("Данные папок недоступны");
  FolderDataValidation.validate(json);Data old=data(s);DATA.put(s,GSON.fromJson(json,Data.class));
  if(!save(s)){DATA.put(s,old);throw new IllegalStateException("Ошибка сохранения папок");}syncAll(s);
 }
 public static Map<String,String> diagnosticAssignments(MinecraftServer server){return Map.copyOf(data(server).assigned);}
 private static boolean valid(String s){return s!=null&&!s.isBlank()&&s.length()<=32&&s.equals(s.strip())&&s.chars().noneMatch(Character::isISOControl);}
 private static boolean save(MinecraftServer s){
  Data d=data(s);if(d.invalid)return false;
  try{Path p=path(s);Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".tmp");Files.writeString(tmp,GSON.toJson(d));
   try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException e){
    if(Files.exists(p))Files.copy(p,p.resolveSibling(p.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
    Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);
   }return true;
  }catch(Exception e){System.err.println("[Whiteout] Could not save folders: "+e);return false;}
 }
 private static void sync(ServerPlayerEntity player){
  String json=GSON.toJson(data(player.getServer()));
  List<String> chunks=SnapshotChunks.split(json);
  if(chunks.isEmpty()){
   player.sendMessage(net.minecraft.text.Text.translatable("message.whiteout.folders_too_large"),false);
   System.err.println("[Whiteout] Folder snapshot too large: "+json.length());return;
  }
  for(int i=0;i<chunks.size();i++)ServerPlayNetworking.send(player,new Snapshot(i,chunks.size(),chunks.get(i)));
 }
 private static void syncAll(MinecraftServer server){for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())sync(p);}
 private static void edit(ServerPlayerEntity player,Edit packet){
  if(!player.isCreative()||!player.hasPermissionLevel(2))return;
  Data d=data(player.getServer());if(d.invalid)return;
  Data previous=GSON.fromJson(GSON.toJson(d),Data.class);
  JsonObject undoBefore=EditorHistory.capture(player,"");
  String folder=packet.folder(),val=packet.value();
  switch(packet.action()){
   case "create" -> {if(!valid(folder)||d.folders.contains(folder))return;d.folders.add(folder);}
   case "rename" -> {
    if(!d.folders.contains(folder)||!valid(val)||d.folders.contains(val))return;
    int at=d.folders.indexOf(folder);d.folders.set(at,val);
    d.assigned.replaceAll((zone,f)->f.equals(folder)?val:f);
    List<String> ordered=d.order.remove(folder);
    if(ordered!=null)d.order.put(val,ordered);
   }
   case "delete" -> {
    if(!d.folders.remove(folder))return;
    List<String> former=d.order.remove(folder);
    if(former!=null)d.order.computeIfAbsent("",k->new ArrayList<>()).addAll(former);
    d.assigned.values().removeIf(folder::equals);
   }
   case "move" -> {
    if(!folder.isEmpty()&&!d.folders.contains(folder))return;
    boolean exists=false;
    try{for(Zone zone:Store.get(player.getServer()).data.zones)if(zone!=null&&val.equals(zone.id)){exists=true;break;}}
    catch(Exception ignored){}
    if(!exists)return;
    String previousFolder=d.assigned.getOrDefault(val,"");
    if(!previousFolder.equals(folder)){
     for(List<String> list:d.order.values())list.remove(val);
     d.order.computeIfAbsent(folder,k->new ArrayList<>()).add(val);
    }
    if(folder.isEmpty())d.assigned.remove(val);else d.assigned.put(val,folder);
   }
   case "up", "down" -> {
    if(!folder.isEmpty()&&!d.folders.contains(folder))return;
    List<String> ordered=d.order.computeIfAbsent(folder,k->new ArrayList<>());
    Set<String> actual=new LinkedHashSet<>();
    for(Zone z:Store.get(player.getServer()).data.zones)
     if(z!=null&&folder.equals(d.assigned.getOrDefault(z.id,"")))actual.add(z.id);
    ZoneFolderOrder.reconcile(ordered,actual);
    if(!ZoneFolderOrder.move(ordered,val,"up".equals(packet.action())?-1:1))return;
   }
   default -> {return;}
  }
  if(!save(player.getServer())){
   DATA.put(player.getServer(),previous);
   player.sendMessage(net.minecraft.text.Text.translatable("message.whiteout.folders_save_failed"),false);
  }
  syncAll(player.getServer());
  EditorHistory.record(player,"",undoBefore,true);
  EditorNetwork.status(player,false);
 }
 private static void reconcile(MinecraftServer server){
  Data d=data(server);Store store=Store.get(server);if(d.invalid||store.error!=null)return;
  Set<String> ids=new LinkedHashSet<>();for(Zone z:store.data.zones)if(z!=null&&z.id!=null)ids.add(z.id);
  Data previous=GSON.fromJson(GSON.toJson(d),Data.class);
  d.assigned.keySet().retainAll(ids);
  d.order.keySet().removeIf(f->!f.isEmpty()&&!d.folders.contains(f));
  List<String> groups=new ArrayList<>(d.folders);groups.add("");
  for(String folder:groups){
   List<String> actual=new ArrayList<>();for(String id:ids)if(folder.equals(d.assigned.getOrDefault(id,"")))actual.add(id);
   ZoneFolderOrder.reconcile(d.order.computeIfAbsent(folder,k->new ArrayList<>()),actual);
  }
  if(!previous.assigned.equals(d.assigned)||!previous.order.equals(d.order)){
   if(!save(server))DATA.put(server,previous);else syncAll(server);
  }
 }
 public static void register(){
  PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);
  PayloadTypeRegistry.playC2S().register(Edit.ID,Edit.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Edit.ID,(packet,c)->c.server().execute(()->edit(c.player(),packet)));
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sync(handler.getPlayer()));
  ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(DATA.containsKey(s))save(s);DATA.remove(s);});
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTicks()%20==0)reconcile(s);});
 }
}
