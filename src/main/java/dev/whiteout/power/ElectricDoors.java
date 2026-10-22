package dev.whiteout.power;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Only explicitly targeted iron/copper doors are tied to a named power grid. */
public final class ElectricDoors {
 private ElectricDoors(){}
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static final Map<MinecraftServer,Data> CACHE=new WeakHashMap<>();
 private static final class Data {Map<String,String> doors=new LinkedHashMap<>();transient boolean invalid;}
 private static Path file(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("whiteout-electric-doors.json");}
 private static String key(ServerWorld world,BlockPos p){return world.getRegistryKey().getValue().toString()+":"+p.getX()+":"+p.getY()+":"+p.getZ();}
 private static BlockPos lower(ServerWorld world,BlockPos p){
  BlockState s=world.getBlockState(p);
  return s.getBlock() instanceof DoorBlock&&s.contains(Properties.DOUBLE_BLOCK_HALF)&&s.get(Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER?p.down():p;
 }
 private static boolean supported(BlockState state){
  if(!(state.getBlock() instanceof DoorBlock))return false;
  String id=Registries.BLOCK.getId(state.getBlock()).toString();
  return id.equals("minecraft:iron_door")||id.startsWith("minecraft:")&&id.endsWith("copper_door");
 }
 private static Data load(MinecraftServer server){
  try{
   Data d=Files.isRegularFile(file(server))?GSON.fromJson(Files.readString(file(server)),Data.class):new Data();
   if(d==null||d.doors==null||d.doors.containsKey(null)||d.doors.containsValue(null))throw new IllegalArgumentException("Invalid door data");return d;
  }catch(Exception ex){System.err.println("[Whiteout] Electric doors disabled; original file preserved: "+ex);Data d=new Data();d.invalid=true;return d;}
 }
 private static Data data(MinecraftServer s){return CACHE.computeIfAbsent(s,ElectricDoors::load);}
 public static boolean removeNetwork(MinecraftServer s,String network){
  Data current=data(s);if(current.invalid)return false;
  Data updated=GSON.fromJson(GSON.toJson(current),Data.class);
  if(!updated.doors.values().removeIf(network::equals))return true;
  try{
   GridFileIO.writeAtomic(file(s),GSON.toJson(updated));
   CACHE.put(s,updated);return true;
  }catch(Exception e){System.err.println("[Whiteout] Door cleanup pending: "+e);return false;}
 }
 private static boolean save(MinecraftServer server){
  Data d=data(server);if(d.invalid)return false;
  try{GridFileIO.writeAtomic(file(server),GSON.toJson(d));return true;}
  catch(Exception ex){System.err.println("[Whiteout] Could not save electric doors: "+ex);return false;}
 }
 static boolean worldStateAvailable(MinecraftServer server){return !data(server).invalid;}
 static String worldStateBinding(ServerWorld world,BlockPos lower){
  return supported(world.getBlockState(lower))?data(world.getServer()).doors.get(key(world,lower)):null;
 }
 public static Map<String,String> diagnosticBindings(MinecraftServer server){return Map.copyOf(data(server).doors);}
 /** A door remains excluded from automatic closing while it is bound to Whiteout power,
  * even when that grid is currently switched on. */
 public static boolean isManaged(ServerWorld world,BlockPos target){
  BlockPos pos=lower(world,target);
  return supported(world.getBlockState(pos))&&data(world.getServer()).doors.containsKey(key(world,pos));
 }
 public static boolean locked(ServerWorld world,BlockPos target){
  BlockPos pos=lower(world,target);
  String network=data(world.getServer()).doors.get(key(world,pos));
  return network!=null&&supported(world.getBlockState(pos))&&!GridManager.networkPowered(world.getServer(),network);
 }
 private static BlockPos looking(ServerPlayerEntity p){
  HitResult result=p.raycast(6,0,false);return result instanceof BlockHitResult h?lower(p.getServerWorld(),h.getBlockPos()):null;
 }
 private static int bind(ServerCommandSource source,String network,boolean remove){
  if(!(source.getEntity() instanceof ServerPlayerEntity p))return 0;
  BlockPos pos=looking(p);ServerWorld w=p.getServerWorld();
  if(pos==null||!supported(w.getBlockState(pos))){source.sendError(Text.literal("Наведи прицел на железную или любую медную дверь (до 6 блоков)"));return 0;}
  if(!remove&&!GridManager.networkNames(p.getServer()).contains(network)){
   source.sendError(Text.literal("Электросеть не найдена: "+network));return 0;
  }
  Data d=data(p.getServer());if(d.invalid){source.sendError(Text.literal("Файл электрических дверей повреждён; данные сохранены для восстановления"));return 0;}
  String position=key(w,pos),previous=d.doors.get(position);
  if(remove)d.doors.remove(position);else d.doors.put(position,network);
  if(!save(p.getServer())){
   if(previous==null)d.doors.remove(position);else d.doors.put(position,previous);
   source.sendError(Text.literal("Не удалось сохранить привязку двери, изменение отменено"));return 0;
  }
  source.sendFeedback(()->Text.literal(remove?"Дверь отвязана от электросети":"Дверь привязана к сети: "+network),false);
  if(!remove&&locked(w,pos))close(w,pos);
  return 1;
 }
 private static void close(ServerWorld world,BlockPos pos){
  if(!world.isChunkLoaded(pos))return;
  BlockState bottom=world.getBlockState(pos);
  if(!supported(bottom)||!bottom.contains(Properties.OPEN)||!bottom.get(Properties.OPEN))return;
  world.setBlockState(pos,bottom.with(Properties.OPEN,false),Block.NOTIFY_ALL);
  BlockPos upperPos=pos.up();BlockState top=world.getBlockState(upperPos);
  if(top.getBlock()==bottom.getBlock()&&top.contains(Properties.OPEN)&&top.get(Properties.OPEN))
   world.setBlockState(upperPos,top.with(Properties.OPEN,false),Block.NOTIFY_ALL);
 }
 private static void tick(ServerWorld world){
  // Bound the safety sweep for externally opened doors; interaction checks stay live.
  if(Math.floorMod(world.getTime(),10L)!=0L)return;
  String dimension=world.getRegistryKey().getValue().toString()+":";
  Map<String,Boolean> powerByNetwork=new HashMap<>();
  for(var entry:data(world.getServer()).doors.entrySet()){
   String address=entry.getKey();if(!address.startsWith(dimension))continue;
   String coords=address.substring(dimension.length());String[] xyz=coords.split(":");if(xyz.length!=3)continue;
   try{
    BlockPos p=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
    if(!world.isChunkLoaded(p))continue;
    boolean powered=powerByNetwork.computeIfAbsent(entry.getValue(),id->GridManager.networkPowered(world.getServer(),id));
    if(!powered)close(world,p);
   }catch(NumberFormatException ignored){}
  }
 }
 /** Only loaded doors near a player are sent to that client. */
 public static List<BlockPos> nearbyLocked(ServerWorld world,BlockPos around,int radius){
  List<BlockPos> result=new ArrayList<>();
  Map<String,Boolean> powerByNetwork=new HashMap<>();
  String prefix=world.getRegistryKey().getValue().toString()+":";
  for(var entry:data(world.getServer()).doors.entrySet()){
   if(!entry.getKey().startsWith(prefix))continue;
   String[] xyz=entry.getKey().substring(prefix.length()).split(":");
   if(xyz.length!=3)continue;
   try{
    BlockPos p=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
    if(p.isWithinDistance(around.toCenterPos(),radius)&&world.isChunkLoaded(p)&&supported(world.getBlockState(p))
      &&!powerByNetwork.computeIfAbsent(entry.getValue(),id->GridManager.networkPowered(world.getServer(),id)))result.add(p);
   }catch(NumberFormatException ignored){}
  }
  return result;
 }
 public static void register(){
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,env)->dispatcher.register(literal("whiteout-powerdoor")
   .requires(s->s.hasPermissionLevel(2))
   .then(literal("bind").then(argument("network",StringArgumentType.word()).executes(c->bind(c.getSource(),StringArgumentType.getString(c,"network"),false))))
   .then(literal("unbind").executes(c->bind(c.getSource(),"",true)))));
  ServerTickEvents.END_WORLD_TICK.register(ElectricDoors::tick);
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(CACHE.containsKey(server))save(server);CACHE.remove(server);});
 }
}
