package dev.whiteout.power;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.BlockState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;

/** Persistent per-fixture light switching, independent of grid power state. */
public final class PowerSwitches {
 private PowerSwitches(){}
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static final Map<MinecraftServer,Data> STORES=new WeakHashMap<>();
 private static int elapsedTicks;
 public static final class Entry {
  public String dimension="minecraft:overworld",network="main";
  public int x,y,z;
  public boolean on=true,all=true;
  public Set<String> lamps=new LinkedHashSet<>();
 }
 public static final class Data {public Map<String,Entry> switches=new LinkedHashMap<>();public transient boolean invalid;}
 private static String key(String dim,BlockPos pos){return dim+":"+pos.getX()+":"+pos.getY()+":"+pos.getZ();}
 private static Path path(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("whiteout-light-switches.json");}
 private static Data load(MinecraftServer s){
  try{
   Data d=Files.isRegularFile(path(s))?GSON.fromJson(Files.readString(path(s)),Data.class):new Data();
   if(d==null||d.switches==null)throw new IllegalArgumentException("Missing switch map");
   for(Entry e:d.switches.values())if(e==null||e.lamps==null||e.network==null||e.dimension==null)throw new IllegalArgumentException("Invalid switch entry");
   return d;
  }catch(Exception ex){System.err.println("[Whiteout] Cannot read switches, original retained: "+ex);Data d=new Data();d.invalid=true;return d;}
 }
 private static Data data(MinecraftServer s){return STORES.computeIfAbsent(s,PowerSwitches::load);}
 public static boolean removeNetwork(MinecraftServer s,String network){
  Data current=data(s);if(current.invalid)return false;
  Data updated=GSON.fromJson(GSON.toJson(current),Data.class);
  if(!updated.switches.values().removeIf(e->network.equals(e.network)))return true;
  try{
   GridFileIO.writeAtomic(path(s),GSON.toJson(updated));
   STORES.put(s,updated);return true;
  }catch(Exception e){System.err.println("[Whiteout] Switch cleanup pending: "+e);return false;}
 }
 private static boolean save(MinecraftServer s){
  Data d=data(s);if(d.invalid)return false;
  try{GridFileIO.writeAtomic(path(s),GSON.toJson(d));return true;}
  catch(Exception ex){System.err.println("[Whiteout] Cannot save switches: "+ex);return false;}
 }
 private static Entry lookup(net.minecraft.server.world.ServerWorld world,BlockPos pos,boolean create){
  Data d=data(world.getServer());String id=key(world.getRegistryKey().getValue().toString(),pos);
  Entry existing=d.switches.get(id);
  if(existing==null&&create&&!d.invalid){
   existing=new Entry();existing.dimension=world.getRegistryKey().getValue().toString();existing.x=pos.getX();existing.y=pos.getY();existing.z=pos.getZ();d.switches.put(id,existing);
  }
  return existing;
 }
 /** If several switches control one lamp, any ON switch permits it. */
 public static Map<String,String> diagnosticBindings(MinecraftServer server){
  Map<String,String> result=new LinkedHashMap<>();
  data(server).switches.forEach((key,value)->result.put(key,value.network));
  return Map.copyOf(result);
 }
 public static boolean allows(MinecraftServer server,String network,String lamp){
  boolean assigned=false,enabled=false;
  for(Entry s:data(server).switches.values()){
   if(!network.equals(s.network)||!(s.all||s.lamps.contains(lamp)))continue;
   assigned=true;enabled|=s.on;
  }
  return !assigned||enabled;
 }
 public static void toggle(ServerPlayerEntity player,BlockPos pos){
  var world=player.getServerWorld();if(!world.getBlockState(pos).isOf(PowerDevices.POWER_SWITCH))return;
  String id=key(world.getRegistryKey().getValue().toString(),pos);
  boolean wasRegistered=data(world.getServer()).switches.containsKey(id);
  Entry e=lookup(world,pos,true);if(e==null)return;
  e.on=!e.on;
  if(!save(player.getServer())){
   e.on=!e.on;
   if(!wasRegistered)data(world.getServer()).switches.remove(id);
   player.sendMessage(Text.literal("Не удалось сохранить выключатель"),true);return;
  }
  world.setBlockState(pos,world.getBlockState(pos).with(PowerSwitchBlock.ON,e.on),3);
  GridManager.refreshSwitches(player.getServer());
 }
 /** Unregister broken switches so an absent block cannot keep a circuit off. */
 public static void remove(net.minecraft.server.world.ServerWorld world,BlockPos pos){
  Data d=data(world.getServer());if(d.invalid)return;
  String id=key(world.getRegistryKey().getValue().toString(),pos);
  Entry old=d.switches.remove(id);
  if(old!=null){
   if(!save(world.getServer())){d.switches.put(id,old);return;}
   GridManager.refreshSwitches(world.getServer());
  }
 }
 private static void updateBlock(ServerPlayerEntity player,BlockPos pos,Entry e){
  var w=player.getServerWorld();if(w.getBlockState(pos).isOf(PowerDevices.POWER_SWITCH))
   w.setBlockState(pos,w.getBlockState(pos).with(PowerSwitchBlock.ON,e.on),3);
 }
 public record Snapshot(BlockPos pos,String network,String networks,String lamps,String selected,boolean all,boolean on) implements CustomPayload {
  public static final Id<Snapshot> ID=new Id<>(PowerDevices.id("switch_snapshot"));
  public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeBlockPos(p.pos);b.writeString(p.network,64);b.writeString(p.networks,4096);b.writeString(p.lamps,16384);b.writeString(p.selected,16384);b.writeBoolean(p.all);b.writeBoolean(p.on);},
   b->new Snapshot(b.readBlockPos(),b.readString(64),b.readString(4096),b.readString(16384),b.readString(16384),b.readBoolean(),b.readBoolean()));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Edit(BlockPos pos,String action,String value) implements CustomPayload {
  public static final Id<Edit> ID=new Id<>(PowerDevices.id("switch_edit"));
  public static final PacketCodec<RegistryByteBuf,Edit> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeBlockPos(p.pos);b.writeString(p.action,24);b.writeString(p.value,128);},
   b->new Edit(b.readBlockPos(),b.readString(24),b.readString(128)));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 public static void openEditor(ServerPlayerEntity p,BlockPos pos){
  if(!p.isCreative()||!p.isAlive()||p.squaredDistanceTo(pos.toCenterPos())>64||!p.getServerWorld().isChunkLoaded(pos)||!p.getServerWorld().getBlockState(pos).isOf(PowerDevices.POWER_SWITCH))return;
  Entry e=lookup(p.getServerWorld(),pos,true);if(e==null)return;
  String networks=String.join("\n",GridManager.networkNames(p.getServer()));
  String lamps=String.join("\n",GridManager.fixtureKeys(p.getServer(),e.network));
  String selected=String.join("\n",e.lamps);
  if(networks.length()>4096||lamps.length()>16000||selected.length()>16000){p.sendMessage(Text.literal("Слишком много сетей или ламп для одного списка"),false);return;}
  ServerPlayNetworking.send(p,new Snapshot(pos,e.network,networks,lamps,selected,e.all,e.on));
 }
 private static void edit(ServerPlayerEntity p,Edit packet){
  BlockPos pos=packet.pos();if(!p.isCreative()||!p.isAlive()||p.squaredDistanceTo(pos.toCenterPos())>64||!p.getServerWorld().isChunkLoaded(pos)||!p.getServerWorld().getBlockState(pos).isOf(PowerDevices.POWER_SWITCH))return;
  String id=key(p.getServerWorld().getRegistryKey().getValue().toString(),pos);
  boolean wasRegistered=data(p.getServer()).switches.containsKey(id);
  Entry e=lookup(p.getServerWorld(),pos,true);if(e==null)return;
  Entry previous=GSON.fromJson(GSON.toJson(e),Entry.class);
  if("network".equals(packet.action())){
   if(!GridManager.networkNames(p.getServer()).contains(packet.value()))return;
   e.network=packet.value();e.lamps.clear();e.all=true;
  }else if("all".equals(packet.action())){e.all=!e.all;}
  else if("lamp".equals(packet.action())){
   if(!GridManager.fixtureKeys(p.getServer(),e.network).contains(packet.value()))return;
   if(!e.lamps.add(packet.value()))e.lamps.remove(packet.value());e.all=false;
  }else if("state".equals(packet.action())){e.on=!e.on;}
  else return;
  if(!save(p.getServer())){
   if(wasRegistered)data(p.getServer()).switches.put(id,previous);
   else data(p.getServer()).switches.remove(id);
   p.sendMessage(Text.literal("Не удалось сохранить настройки выключателя"),false);return;
  }
  updateBlock(p,pos,e);
  GridManager.refreshSwitches(p.getServer());openEditor(p,pos);
 }
 public static void register(){
  PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);
  PayloadTypeRegistry.playC2S().register(Edit.ID,Edit.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Edit.ID,(packet,context)->context.server().execute(()->edit(context.player(),packet)));
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(STORES.containsKey(server))save(server);STORES.remove(server);});
  // Restore the visual state after chunk reload, without forcing chunks to load.
  ServerTickEvents.END_SERVER_TICK.register(server->{if(++elapsedTicks%40!=0)return;
   for(Entry e:data(server).switches.values())for(var world:server.getWorlds()){
    if(!world.getRegistryKey().getValue().toString().equals(e.dimension))continue;
    BlockPos pos=new BlockPos(e.x,e.y,e.z);if(!world.isChunkLoaded(pos))continue;
    BlockState b=world.getBlockState(pos);
    if(b.isOf(PowerDevices.POWER_SWITCH)&&b.get(PowerSwitchBlock.ON)!=e.on)world.setBlockState(pos,b.with(PowerSwitchBlock.ON,e.on),3);
   }
  });
 }
}
