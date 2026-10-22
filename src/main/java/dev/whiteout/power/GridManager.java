package dev.whiteout.power;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.whiteout.ObjectiveRules;
import dev.whiteout.Store;
import dev.whiteout.net.WhiteoutNetwork;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Hand;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Server-authoritative independent circuits. Legacy main grid is preserved unchanged. */
public final class GridManager {
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static final Map<MinecraftServer,Config> CONFIGS=new WeakHashMap<>();
 private static final Map<MinecraftServer,NetworkStore> NETWORKS=new WeakHashMap<>();
 public static final class NetworkStore {
  public Map<String,Config> grids=new LinkedHashMap<>();
  // Durable deletion journal: cleanup may need unloaded chunks or a retry
  // after a failed write to the switch/door files.
  public Map<String,Config> deleting=new LinkedHashMap<>();
  public transient String error;
  public transient boolean backupCreated;
 }
 private static final String MAIN="main";
 private static final String NETWORK_FILE="whiteout-grid-networks.json";
 private static final String QUEST="whiteout_restore_power";
 private static final String OVERWORLD="minecraft:overworld";
 private static int ticks;
 private GridManager() {}

 public static final class Fixture {
  public String dimension=OVERWORLD;
  public int x,y,z;
  public String original;
  public boolean vanilla;
  public Map<String,String> properties=new TreeMap<>();
  public Fixture() {}
  public Fixture(String dim,BlockPos pos,BlockState state) {
   dimension=dim;x=pos.getX();y=pos.getY();z=pos.getZ();
   original=Registries.BLOCK.getId(state.getBlock()).toString();
   vanilla=state.isOf(Blocks.LANTERN)||state.isOf(PowerDevices.LINKED_LANTERN);
   state.getEntries().forEach((p,v)->properties.put(p.getName(),v.toString()));
   if(vanilla)original="minecraft:lantern";
  }
  BlockPos pos(){return new BlockPos(x,y,z);}
  boolean at(String d,BlockPos p){return dimension.equals(d)&&x==p.getX()&&y==p.getY()&&z==p.getZ();}
 }
 public static final class Config {
  public String dimension=OVERWORLD;
  public int panelX=0,panelY=0,panelZ=0;
  public int generatorX=0,generatorY=0,generatorZ=0;
  public boolean panelBound=false,generatorBound=false;
  public List<Fixture> lamps=new ArrayList<>();
  public List<Fixture> pendingRestores=new ArrayList<>();
  public boolean triggerSet=false,fired=false,questDone=false,armed=true;
  public int triggerX,triggerY,triggerZ,radius=3;
  public boolean fuseLocationSet=false;
  public int fuseX,fuseY,fuseZ;
  public boolean burntPending=false;
  // The generator must stop even when its chunk is unloaded at the moment of the incident.
  public boolean generatorStopPending=false;
  public String questStep="";
  private transient RepeatedFailure questFailure=new RepeatedFailure();
  public transient Boolean cachedPower;
  public transient boolean reconciling;
  // Cached facts survive chunk unload and server restart. Never force-load chunks.
  // Unknown devices remain unpowered until inspected at least once.
  public boolean panelObserved, panelFuse, panelBurnt;
  public boolean generatorObserved, generatorRunning, generatorFueled;
  public transient String configurationError;
  public transient boolean interactionDirty;
  public transient boolean backupCreated;
  public transient long flickerEnd,lastFlicker;

  BlockPos panel(){return new BlockPos(panelX,panelY,panelZ);}
  BlockPos generator(){return new BlockPos(generatorX,generatorY,generatorZ);}
 }

 public static void register(){
  // Defer all block IO until ordinary ticks; CHUNK_LOAD itself is not safe for block reads.
  ServerLifecycleEvents.SERVER_STARTED.register(server->{get(server);networks(server);});
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{
   Config primary=CONFIGS.get(server);if(primary!=null)save(server,primary);
   NetworkStore ns=NETWORKS.get(server);if(ns!=null)saveNetworks(server,ns);
   CONFIGS.remove(server);NETWORKS.remove(server);
  });
  ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
   String dim=world.getRegistryKey().getValue().toString();
   int cx=chunk.getPos().x,cz=chunk.getPos().z;
   for(Config c:all(world.getServer())){
    if(!c.dimension.equals(dim))continue;
    boolean affected=c.panelBound&&(c.panelX>>4)==cx&&(c.panelZ>>4)==cz
     ||c.generatorBound&&(c.generatorX>>4)==cx&&(c.generatorZ>>4)==cz;
    if(!affected)for(Fixture f:c.lamps){
     if(f.dimension.equals(dim)&&(f.x>>4)==cx&&(f.z>>4)==cz){affected=true;break;}
    }
    if(!affected)for(Fixture f:c.pendingRestores){
     if(f.dimension.equals(dim)&&(f.x>>4)==cx&&(f.z>>4)==cz){affected=true;break;}
    }
    if(affected)c.cachedPower=null;
   }
  });
  ServerTickEvents.END_SERVER_TICK.register(server->{
   boolean periodic=++ticks%10==0;
   boolean dirty=false;
   for(Config c:all(server))if(c.interactionDirty){c.interactionDirty=false;dirty=true;}
   if(dirty)refresh(server);
   if(!periodic)return;
   finishDeletions(server);
   Config main=get(server);
   // The map's historical power-failure event belongs ONLY to the legacy main circuit.
   if(healthy(main)&&main.triggerSet&&!main.fired){
    ServerWorld w=world(server,main.dimension);
    List<ServerPlayerEntity> participants=new ArrayList<>();
    if(w!=null)for(ServerPlayerEntity p:w.getPlayers()){
     double dx=p.getX()-(main.triggerX+.5),dy=p.getY()-(main.triggerY+.5),dz=p.getZ()-(main.triggerZ+.5);
     if(dx*dx+dy*dy+dz*dz<=main.radius*main.radius)participants.add(p);
    }
    boolean inside=!participants.isEmpty();
    if(CircuitRules.rearm(main.triggerSet,main.fired,inside)&&!main.armed){main.armed=true;persist(server,main);}
    else if(CircuitRules.entered(main.triggerSet,main.fired,main.armed,inside))trip(server,main,participants);
   }
   if(ticks%20==0){refresh(server);quest(server,main);}
  });
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if(player.getStackInHand(hand).isOf(PowerDevices.CIRCUIT_LINKER)) {
    if(world.isClient)return ActionResult.SUCCESS; // ensure a client -> server use-block packet
    return linkerClick((ServerPlayerEntity)player,(ServerWorld)world,hand,hit.getBlockPos());
   }
   if(world.isClient)return ActionResult.PASS;
   Config c=fixtureOwner(((ServerWorld)world).getServer(),world.getRegistryKey().getValue().toString(),hit.getBlockPos());
   if(c!=null) {
    // Allow ordinary block placement against a fixture, but repair any native
    // Furniture LIT toggle at the end of this server tick. Other interactions
    // cannot bypass the circuit's power setting.
    if(player.getStackInHand(hand).getItem() instanceof BlockItem){
     c.cachedPower=null;c.interactionDirty=true;
     return ActionResult.PASS;
    }
    // Unlike generic linked lamps, the backup fixture has a read-only charge readout.
    // Let its own onUse show the status. No power switch or binding is changed.
    if(world.getBlockState(hit.getBlockPos()).isOf(PowerDevices.EMERGENCY_LAMP))return ActionResult.PASS;
    if(!player.isSpectator())player.sendMessage(Text.literal("Светильник управляется электрощитком"),true);
    return ActionResult.SUCCESS;
   }
   return ActionResult.PASS;
  });
  CommandRegistrationCallback.EVENT.register((d,a,e)->commands(d));
 }
 private static Config load(MinecraftServer s){
  Path file=path(s);
  try{
   if(Files.exists(file)&&!Files.isRegularFile(file))throw new IOException("Path is not a regular file");
   Config c=Files.isRegularFile(file)?GSON.fromJson(Files.readString(file),Config.class):new Config();
   if(c==null)throw new IOException("Empty configuration document");
   if(c.lamps==null||c.pendingRestores==null||Identifier.tryParse(c.dimension)==null)
    throw new IOException("Invalid Whiteout grid configuration structure");
   if(c.radius<1||c.radius>64)throw new IOException("Invalid event radius");
   for(Fixture f:c.lamps)if(f==null||Identifier.tryParse(f.dimension)==null||Identifier.tryParse(f.original)==null)
    throw new IOException("Invalid lamp entry");
   for(Fixture f:c.pendingRestores)if(f==null||Identifier.tryParse(f.dimension)==null||Identifier.tryParse(f.original)==null)
    throw new IOException("Invalid pending restoration entry");
   c.cachedPower=null;
   return c;
  }catch(Exception ex){
   // Fail closed. A broken file must NEVER be overwritten by an empty circuit.
   Config disabled=new Config();
   disabled.configurationError="Невозможно прочитать whiteout-grid.json: "+ex.getMessage();
   try{
    Path backup=file.resolveSibling("whiteout-grid.corrupt-"+Instant.now().toEpochMilli()+".json");
    Files.copy(file,backup);
    System.err.println("[Whiteout] Grid disabled; original preserved, diagnostic copy: "+backup);
   }catch(IOException copyError){System.err.println("[Whiteout] Cannot copy broken grid: "+copyError);}
   System.err.println("[Whiteout] Grid disabled: "+ex);
   return disabled;
  }
 }
 private static Config get(MinecraftServer s){return CONFIGS.computeIfAbsent(s,GridManager::load);}
 private static Path path(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("whiteout-grid.json");}
 private static Path networkPath(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve(NETWORK_FILE);}
 private static String address(String dim,BlockPos pos){return dim+":"+pos.getX()+":"+pos.getY()+":"+pos.getZ();}
 private static boolean occupied(Config c,String dim,BlockPos pos){
  if(c.panelBound&&c.dimension.equals(dim)&&c.panel().equals(pos))return true;
  if(c.generatorBound&&c.dimension.equals(dim)&&c.generator().equals(pos))return true;
  if(find(c,dim,pos)!=null)return true;
  for(Fixture f:c.pendingRestores)if(f.at(dim,pos))return true;
  return false;
 }
 private static void validateConfig(Config c) throws IOException {
  if(c==null||Identifier.tryParse(c.dimension)==null||c.lamps==null||c.pendingRestores==null)
   throw new IOException("Invalid circuit configuration");
  if(c.radius<1||c.radius>64)throw new IOException("Invalid circuit event radius");
  Set<String> used=new HashSet<>();
  if(c.panelBound&&!used.add(address(c.dimension,c.panel())))throw new IOException("Duplicated panel");
  if(c.generatorBound&&!used.add(address(c.dimension,c.generator())))throw new IOException("Panel and generator overlap");
  for(Fixture f:c.lamps){
   if(f==null||Identifier.tryParse(f.dimension)==null||Identifier.tryParse(f.original)==null||!used.add(address(f.dimension,f.pos())))throw new IOException("Invalid or duplicated lamp");
  }
  for(Fixture f:c.pendingRestores)if(f==null||Identifier.tryParse(f.dimension)==null||Identifier.tryParse(f.original)==null)
   throw new IOException("Invalid pending restoration entry");
  c.cachedPower=null;c.reconciling=false;
 }
 private static NetworkStore loadNetworks(MinecraftServer server){
  Path file=networkPath(server);
  try{
   NetworkStore out=Files.isRegularFile(file)?GSON.fromJson(Files.readString(file),NetworkStore.class):new NetworkStore();
   if(out==null||out.grids==null)throw new IOException("Missing circuits");
   if(out.deleting==null)out.deleting=new LinkedHashMap<>();
   for(var entry:out.deleting.entrySet()){
    if(!CircuitTopology.validName(entry.getKey())||out.grids.containsKey(entry.getKey()))throw new IOException("Invalid deletion journal");
    validateConfig(entry.getValue());
   }
   Config main=get(server);
   List<Config> visited=new ArrayList<>();
   visited.add(main);
   for(var item:out.grids.entrySet()){
    if(!CircuitTopology.validName(item.getKey()))throw new IOException("Invalid circuit name: "+item.getKey());
    Config c=item.getValue();validateConfig(c);
    if(!c.panelBound)throw new IOException("Named circuit has no panel: "+item.getKey());
    for(Config existing:visited){
     if(c.panelBound&&occupied(existing,c.dimension,c.panel()))throw new IOException("Panel collision: "+item.getKey());
     if(c.generatorBound&&occupied(existing,c.dimension,c.generator()))throw new IOException("Generator collision: "+item.getKey());
     for(Fixture f:c.lamps)if(occupied(existing,f.dimension,f.pos()))throw new IOException("Lamp collision: "+item.getKey());
    }
    visited.add(c);
   }
   return out;
  }catch(Exception ex){
   NetworkStore invalid=new NetworkStore();invalid.error=ex.getMessage();
   try{if(Files.isRegularFile(file))Files.copy(file,file.resolveSibling(NETWORK_FILE+".corrupt-"+Instant.now().toEpochMilli()+".bak"));}
   catch(IOException copy){System.err.println("[Whiteout] Cannot back up invalid networks: "+copy);}
   System.err.println("[Whiteout] Additional circuits disabled; file preserved: "+ex);
   return invalid;
  }
 }
 private static NetworkStore networks(MinecraftServer server){return NETWORKS.computeIfAbsent(server,GridManager::loadNetworks);}
 private static List<Config> all(MinecraftServer server){
  List<Config> result=new ArrayList<>();result.add(get(server));
  NetworkStore ns=networks(server);
  if(ns.error==null)result.addAll(ns.grids.values());
  return result;
 }
 private static boolean saveNetworks(MinecraftServer server,NetworkStore ns){
  if(ns.error!=null){System.err.println("[Whiteout] Refusing to overwrite invalid additional circuits: "+ns.error);return false;}
  try{
   Path file=networkPath(server);Files.createDirectories(file.getParent());
   if(!ns.backupCreated&&Files.isRegularFile(file)){
    Files.copy(file,file.resolveSibling(NETWORK_FILE+".bak"),StandardCopyOption.REPLACE_EXISTING);ns.backupCreated=true;
   }
   GridFileIO.writeAtomic(file,GSON.toJson(ns));
   return true;
  }catch(Exception ex){System.err.println("[Whiteout] Cannot save additional circuits: "+ex);return false;}
 }
 private static void persist(MinecraftServer server,Config c){
  if(c==get(server))save(server,c);
  else{NetworkStore ns=networks(server);if(ns.grids.containsValue(c))saveNetworks(server,ns);
   else System.err.println("[Whiteout] Refusing to save an unregistered circuit");}
 }
 private static Config panelOwner(MinecraftServer server,String dim,BlockPos pos){
  for(Config c:all(server))if(c.panelBound&&c.dimension.equals(dim)&&c.panel().equals(pos))return c;
  return null;
 }
 private static Config generatorOwner(MinecraftServer server,String dim,BlockPos pos){
  for(Config c:all(server))if(c.generatorBound&&c.dimension.equals(dim)&&c.generator().equals(pos))return c;
  return null;
 }
 private static Config fixtureOwner(MinecraftServer server,String dim,BlockPos pos){
  for(Config c:all(server))if(find(c,dim,pos)!=null)return c;
  return null;
 }
 private static boolean claimedElsewhere(MinecraftServer server,Config c,String dim,BlockPos pos){
  for(Config pending:networks(server).deleting.values())if(occupied(pending,dim,pos))return true;
  for(Config other:all(server))if(other!=c&&occupied(other,dim,pos))return true;
  return false;
 }
 private static String networkId(MinecraftServer server,Config c){
  if(c==get(server))return MAIN;
  for(var entry:networks(server).grids.entrySet())if(entry.getValue()==c)return entry.getKey();
  return "unknown";
 }
 /** Detached diagnostic copies: no device sampling, persistence or power changes. */
 public static Map<String,Config> diagnosticConfigs(MinecraftServer server){
  Map<String,Config> result=new LinkedHashMap<>();
  for(Config original:all(server)){
   Config copy=GSON.fromJson(GSON.toJson(original),Config.class);
   copy.configurationError=original.configurationError==null?null:"invalid";
   copy.flickerEnd=original.flickerEnd;
   result.put(networkId(server,original),copy);
  }
  return Collections.unmodifiableMap(result);
 }
 public static boolean diagnosticInvalidNetworks(MinecraftServer server){return networks(server).error!=null;}
 /** Read-only circuit access for wall switches and power-dependent doors. */
 public static boolean networkPowered(MinecraftServer s,String id){
  Config c=MAIN.equals(id)?get(s):networks(s).grids.get(id);
  if(c==null||!healthy(c))return false;
  sampleDevices(s,c);return powered(s,c);
 }
 public static List<String> networkNames(MinecraftServer s){
  List<String> ids=new ArrayList<>();ids.add(MAIN);
  if(networks(s).error==null)ids.addAll(networks(s).grids.keySet());return ids;
 }
 public static List<String> fixtureKeys(MinecraftServer s,String id){
  Config c=MAIN.equals(id)?get(s):networks(s).grids.get(id);
  if(c==null)return List.of();List<String> result=new ArrayList<>();
  for(Fixture f:c.lamps)result.add(address(f.dimension,f.pos()));return result;
 }
 public static void refreshSwitches(MinecraftServer s){
  for(Config c:all(s))c.cachedPower=null;
  refresh(s);
 }
 private static String nextName(NetworkStore store){
  for(int i=2;i<100000;i++){String key="circuit_"+i;if(!store.grids.containsKey(key)&&!store.deleting.containsKey(key))return key;}
  throw new IllegalStateException("Network limit reached");
 }
 private static void save(MinecraftServer s,Config c){
  if(c.configurationError!=null){
   System.err.println("[Whiteout] Refusing to overwrite invalid grid config: "+c.configurationError);
   return;
  }
  try{
   Path file=path(s);Files.createDirectories(file.getParent());
   if(!c.backupCreated&&Files.isRegularFile(file)){
    Files.copy(file,file.resolveSibling("whiteout-grid.json.pre-3.6.6.bak"),StandardCopyOption.REPLACE_EXISTING);
    c.backupCreated=true;
   }
   GridFileIO.writeAtomic(file,GSON.toJson(c));
  }catch(Exception ex){System.err.println("[Whiteout] Cannot save grid configuration: "+ex);}
 }
 private static boolean healthy(Config c){return c.configurationError==null;}
 private static CommandSyntaxException commandFailure(String message){return new SimpleCommandExceptionType(Text.literal(message)).create();}
 private static void requireHealthy(Config c) throws CommandSyntaxException{
  if(!healthy(c))throw commandFailure("Электросеть отключена: повреждён whiteout-grid.json. Исправь файл и выполни /whiteout power reload");
 }
 private static int reload(ServerCommandSource src){
  MinecraftServer server=src.getServer();
  Config replacement=load(server);
  if(!healthy(replacement)){
   feedback(src,"Ошибочный конфиг оставлен без изменений. Исправь whiteout-grid.json и повтори reload.");
   return 0;
  }
  Config previous=CONFIGS.put(server,replacement);
  NetworkStore extra=loadNetworks(server);
  if(extra.error!=null){
   if(previous!=null)CONFIGS.put(server,previous);else CONFIGS.remove(server);
   feedback(src,"Файл дополнительных сетей повреждён: "+extra.error+". Оставлены прежние настройки.");return 0;
  }
  NETWORKS.put(server,extra);
  refresh(server);
  feedback(src,"Конфигурация всех электросетей повторно загружена");
  return 1;
 }
 private static ServerWorld world(MinecraftServer s,String id){
  Identifier parsed=Identifier.tryParse(id);if(parsed==null)return null;
  return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,parsed));
 }
 private static boolean matches(ServerWorld w,Config c,BlockPos p){return c.dimension.equals(w.getRegistryKey().getValue().toString())&&w.isChunkLoaded(p);}
 private static boolean same(ServerWorld w,Config c,Fixture f){return f.dimension.equals(w.getRegistryKey().getValue().toString());}
 private static Fixture find(Config c,String dimension,BlockPos p){for(Fixture f:c.lamps)if(f.at(dimension,p))return f;return null;}
 /** Only the actual chosen panel is accepted. Stored in the item's CUSTOM_DATA, not a client cache. */
 private static final String LINKER_TAG="WhiteoutSelectedPanel";
 private static final class Selection {
  final String dim;final BlockPos pos;
  Selection(String dim,BlockPos pos){this.dim=dim;this.pos=pos;}
 }
 private static Selection selectedPanel(ItemStack stack){
  NbtComponent component=stack.get(DataComponentTypes.CUSTOM_DATA);
  if(component==null)return null;
  NbtCompound tag=component.copyNbt();
  if(!tag.contains(LINKER_TAG,NbtElement.COMPOUND_TYPE))return null;
  NbtCompound selected=tag.getCompound(LINKER_TAG);
  String dim=selected.getString("dimension");
  if(Identifier.tryParse(dim)==null||!selected.contains("x",NbtElement.INT_TYPE)||!selected.contains("y",NbtElement.INT_TYPE)||!selected.contains("z",NbtElement.INT_TYPE))return null;
  return new Selection(dim,new BlockPos(selected.getInt("x"),selected.getInt("y"),selected.getInt("z")));
 }
 private static void selectPanel(ItemStack stack,String dim,BlockPos pos){
  NbtComponent component=stack.get(DataComponentTypes.CUSTOM_DATA);
  NbtCompound tag=component==null?new NbtCompound():component.copyNbt();
  NbtCompound selected=new NbtCompound();
  selected.putString("dimension",dim);selected.putInt("x",pos.getX());selected.putInt("y",pos.getY());selected.putInt("z",pos.getZ());
  tag.put(LINKER_TAG,selected);
  stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(tag));
 }
 private static void clearSelection(ItemStack stack){
  NbtComponent component=stack.get(DataComponentTypes.CUSTOM_DATA);
  if(component==null)return;
  NbtCompound tag=component.copyNbt();tag.remove(LINKER_TAG);
  stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(tag));
 }
 private static void linkerMessage(ServerPlayerEntity player,String message){
  player.sendMessage(Text.literal("[Whiteout] "+message),false);
 }
 /** Item has no maximum linking range. The player only needs to reach each clicked block normally. */
 private static ActionResult linkerClick(ServerPlayerEntity player,ServerWorld world,Hand hand,BlockPos pos){
  // No modifications (even to the item's marker) for non-operators.
  if(player.isSpectator()||!player.hasPermissionLevel(2)){
   linkerMessage(player,"Привязка доступна только оператору карты.");return ActionResult.SUCCESS;
  }
  if(!world.isChunkLoaded(pos))return ActionResult.SUCCESS;
  ItemStack item=player.getStackInHand(hand);
  Config c=get(world.getServer());
  if(!healthy(c)){linkerMessage(player,"Нужно восстановить whiteout-grid.json (power reload).");return ActionResult.SUCCESS;}
  String dim=world.getRegistryKey().getValue().toString();
  BlockState block=world.getBlockState(pos);
  boolean panel=block.isOf(PowerDevices.PANEL),generator=block.isOf(PowerDevices.GENERATOR)||block.isOf(PowerDevices.OLD_GENERATOR);
  if(panel){
   if(player.isSneaking()){
    clearSelection(item);linkerMessage(player,"Выбор щитка очищен, связи не изменены.");
   }else{
    if(!healthy(get(world.getServer()))||networks(world.getServer()).error!=null){
     linkerMessage(player,"Исправь повреждённый файл конфигурации сетей, затем /whiteout power reload.");return ActionResult.SUCCESS;
    }
    Config target=panelOwner(world.getServer(),dim,pos);
    if(target==null){
     if(claimedElsewhere(world.getServer(),null,dim,pos)){
      linkerMessage(player,"Эта позиция уже занята устройством другой цепи.");return ActionResult.SUCCESS;
     }
     Config main=get(world.getServer());
     if(!main.panelBound&&!main.generatorBound&&main.lamps.isEmpty()){
      target=main;
     }else{
      NetworkStore ns=networks(world.getServer());
      String id=nextName(ns);target=new Config();target.dimension=dim;ns.grids.put(id,target);
     }
     target.dimension=dim;target.panelX=pos.getX();target.panelY=pos.getY();target.panelZ=pos.getZ();
     target.panelBound=true;target.panelObserved=false;
     persist(world.getServer(),target);
    }
    selectPanel(item,dim,pos);refresh(world.getServer());
    linkerMessage(player,"Выбрана сеть '"+networkId(world.getServer(),target)+"', щиток: "+pos.toShortString()+". Подключай её генератор и лампы.");
    status(player.getCommandSource(),target);
   }
   return ActionResult.SUCCESS;
  }
  Selection selected=selectedPanel(item);
  if(selected==null){linkerMessage(player,"Сначала нажми этим инструментом на щиток.");return ActionResult.SUCCESS;}
  Config selectedGrid=panelOwner(world.getServer(),selected.dim,selected.pos);
  if(selectedGrid==null){linkerMessage(player,"Выбранный щиток удалён из конфигурации. Выбери существующий щиток.");return ActionResult.SUCCESS;}
  if(!dim.equals(selected.dim)){
   linkerMessage(player,"Устройства одной сети должны находиться в одном измерении.");return ActionResult.SUCCESS;
  }
  c=selectedGrid;
  if(claimedElsewhere(world.getServer(),c,dim,pos)){
   linkerMessage(player,"Этот блок уже принадлежит другой электросети. Сначала отвяжи его от старой.");return ActionResult.SUCCESS;
  }
  if(generator){
   if(player.isSneaking()){
    if(c.generatorBound&&c.generator().equals(pos)){
     c.generatorBound=false;c.generatorObserved=false;c.generatorRunning=false;c.generatorFueled=false;c.cachedPower=null;
     persist(world.getServer(),c);refresh(world.getServer());
     linkerMessage(player,"Генератор отвязан. Лампы без питания.");
    }else linkerMessage(player,"Этот генератор не привязан к выбранному щитку.");
   }else{
    c.generatorX=pos.getX();c.generatorY=pos.getY();c.generatorZ=pos.getZ();c.generatorBound=true;
    c.generatorObserved=false;c.generatorRunning=false;c.generatorFueled=false;c.cachedPower=null;
    persist(world.getServer(),c);refresh(world.getServer());
    linkerMessage(player,"Генератор привязан: "+pos.toShortString()+". Расстояние не ограничено.");
   }
   status(player.getCommandSource(),c);
   return ActionResult.SUCCESS;
  }
  if(compatible(block)||find(c,dim,pos)!=null){
   ServerCommandSource src=player.getCommandSource();
   if(!player.isSneaking()&&find(c,dim,pos)!=null){
    linkerMessage(player,"Эта лампа уже подключена к выбранной цепи.");status(src,c);return ActionResult.SUCCESS;
   }
   try{lamp(src,c,pos,!player.isSneaking());status(src,c);}
   catch(CommandSyntaxException ex){linkerMessage(player,ex.getRawMessage().getString());}
   return ActionResult.SUCCESS;
  }
  linkerMessage(player,"Это не генератор и не совместимая лампа. Всего подключено частей: "+c.lamps.size());
  return ActionResult.SUCCESS;
 }
 private static DeviceEntity device(ServerWorld w,BlockPos pos,int kind){
  if(w==null||!w.isChunkLoaded(pos))return null;
  return w.getBlockEntity(pos) instanceof DeviceEntity e&&(e.kind()==kind||(kind==0&&e.kind()==3))?e:null;
 }
 /** Device snapshots change only when their chunk is actually loaded.
  * Chunk unload means unknown current state, not confirmed shutdown. */
 private static boolean sampleDevices(MinecraftServer s,Config c){
  if(!healthy(c))return false;
  ServerWorld w=world(s,c.dimension);
  if(w==null)return false;
  boolean changed=false;
  if(c.panelBound&&w.isChunkLoaded(c.panel())){
   DeviceEntity panel=device(w,c.panel(),1);
   boolean known=panel!=null, fuse=known&&panel.fuse, burnt=known&&panel.burnt;
   if(c.panelObserved!=known||c.panelFuse!=fuse||c.panelBurnt!=burnt){
    c.panelObserved=known;c.panelFuse=fuse;c.panelBurnt=burnt;changed=true;
   }
  }
  if(c.generatorBound&&w.isChunkLoaded(c.generator())){
   DeviceEntity gen=device(w,c.generator(),0);
   boolean known=gen!=null, running=known&&gen.active(), fueled=known&&gen.fuel>0;
   if(c.generatorObserved!=known||c.generatorRunning!=running||c.generatorFueled!=fueled){
    c.generatorObserved=known;c.generatorRunning=running;c.generatorFueled=fueled;changed=true;
   }
  }
  if(changed)persist(s,c);
  return changed;
 }
 private static boolean powered(MinecraftServer s,Config c){
  return healthy(c)&&GridStatePolicy.supplied(c.panelBound,c.generatorBound,c.panelObserved,c.generatorObserved,
    c.panelFuse,c.panelBurnt,c.generatorRunning,c.generatorFueled,c.burntPending,c.generatorStopPending) && (c.flickerEnd==0|| (world(s,c.dimension)!=null&&world(s,c.dimension).getTime()>=c.flickerEnd));
 }
 /** Short, random voltage drops for a running old generator; not a real outage. */
 public static void oldGeneratorTick(ServerWorld w,DeviceEntity gen){
  Config c=generatorOwner(w.getServer(),w.getRegistryKey().getValue().toString(),gen.getPos());
  if(c==null||!healthy(c))return;
  long now=w.getTime();
  if(c.flickerEnd>0&&now>=c.flickerEnd){c.flickerEnd=0;refresh(w.getServer());}
  if(gen.active()&&c.flickerEnd==0&&now-c.lastFlicker>=100&&w.random.nextInt(350)==0){
   c.lastFlicker=now;c.flickerEnd=now+3+w.random.nextInt(8);refresh(w.getServer());
  }
 }
 public static void panelTick(ServerWorld w,DeviceEntity e){
  Config c=panelOwner(w.getServer(),w.getRegistryKey().getValue().toString(),e.getPos());
  // Every server already refreshes all circuits once per 20 ticks, and interactions
  // refresh immediately. Repeating the full refresh for EVERY panel scaled O(N²).
  if((c==null||!healthy(c))&&e.active())e.setActive(false);
 }
 /** A failed fuse stops only the generator belonging to this panel. */
 public static void fuseFailed(ServerWorld w,BlockPos panel){
  Config c=panelOwner(w.getServer(),w.getRegistryKey().getValue().toString(),panel);
  if(c==null||!healthy(c))return;
  c.generatorStopPending=true;c.cachedPower=null;
  persist(w.getServer(),c);refreshOne(w.getServer(),c);
 }
 public static void refresh(MinecraftServer server){
  if(server==null)return;
  for(Config c:all(server))refreshOne(server,c);
 }
 private static void refreshOne(MinecraftServer server,Config c){
  if(!healthy(c)||c.reconciling)return;
  c.reconciling=true;
  try{
   ServerWorld w=world(server,c.dimension);
   if(w!=null)applyPending(w,c);
   DeviceEntity panel=c.panelBound?device(w,c.panel(),1):null;
   if(panel!=null&&c.burntPending){panel.fuse=true;panel.burnt=true;c.burntPending=false;panel.sync();persist(server,c);}
   DeviceEntity generator=c.generatorBound?device(w,c.generator(),0):null;
   if(c.generatorStopPending&&generator!=null){
    c.generatorStopPending=false;
    generator.emergencyStop();
    persist(server,c);
   }
   sampleDevices(server,c);
   boolean want=powered(server,c);
   if(panel!=null&&panel.active()!=want)panel.setActive(want);
   if(c.cachedPower==null||c.cachedPower!=want){
    c.cachedPower=want;
    String circuitId=networkId(server,c);
    for(Fixture f:c.lamps){ServerWorld fw=world(server,f.dimension);if(fw!=null)apply(fw,f,want,PowerSwitches.allows(server,circuitId,address(f.dimension,f.pos())));}
   }
  }finally{c.reconciling=false;}
 }
 private static void apply(ServerWorld w,Fixture f,boolean mains,boolean enabled){
  boolean power=mains&&enabled;
  BlockPos pos=f.pos();if(!w.isChunkLoaded(pos))return;
  BlockState state=w.getBlockState(pos);
  if(state.isOf(PowerDevices.EMERGENCY_LAMP)){
   if(w.getBlockEntity(pos) instanceof BackupLampEntity lamp)lamp.setGridPower(mains,enabled);
  }else if(f.vanilla){
   if(state.isOf(Blocks.LANTERN)){
    BlockState changed=PowerDevices.LINKED_LANTERN.getDefaultState().with(Properties.HANGING,state.get(Properties.HANGING)).with(Properties.WATERLOGGED,state.get(Properties.WATERLOGGED)).with(PoweredLantern.LIT,power);
    w.setBlockState(pos,changed,Block.NOTIFY_ALL);
   }else if(state.isOf(PowerDevices.LINKED_LANTERN)&&state.get(PoweredLantern.LIT)!=power){
    w.setBlockState(pos,state.with(PoweredLantern.LIT,power),Block.NOTIFY_ALL);
   }
  }else if(Registries.BLOCK.getId(state.getBlock()).toString().equals(f.original)&&state.contains(Properties.LIT)&&state.get(Properties.LIT)!=power){
   // The same Furniture block stays in place. This preserves block-state properties and any BE.
   w.setBlockState(pos,state.with(Properties.LIT,power),Block.NOTIFY_ALL);
  }
 }
 private static void restore(ServerWorld w,Fixture f){
  BlockPos pos=f.pos();if(!w.isChunkLoaded(pos))return;
  BlockState current=w.getBlockState(pos);
  if(current.isOf(PowerDevices.EMERGENCY_LAMP)&&w.getBlockEntity(pos) instanceof BackupLampEntity lamp)lamp.unlink();
  if(f.vanilla&&current.isOf(PowerDevices.LINKED_LANTERN)){
   BlockState vanilla=Blocks.LANTERN.getDefaultState().with(Properties.HANGING,current.get(Properties.HANGING)).with(Properties.WATERLOGGED,current.get(Properties.WATERLOGGED));
   w.setBlockState(pos,vanilla,Block.NOTIFY_ALL);
  }
  if(!f.vanilla&&Registries.BLOCK.getId(current.getBlock()).toString().equals(f.original)
     &&current.contains(Properties.LIT)&&f.properties!=null&&f.properties.containsKey("lit")
     &&!current.isOf(PowerDevices.EMERGENCY_LAMP)){
   w.setBlockState(pos,current.with(Properties.LIT,Boolean.parseBoolean(f.properties.get("lit"))),Block.NOTIFY_ALL);
  }
  // For non-vanilla furniture, the original lamp is never replaced.
 }
 private static void applyPending(ServerWorld w,Config c){
  boolean changed=false;
  for(Iterator<Fixture> it=c.pendingRestores.iterator();it.hasNext();){
   Fixture f=it.next();
   if(!same(w,c,f)||!w.isChunkLoaded(f.pos()))continue;
   restore(w,f);it.remove();changed=true;
  }
  if(changed)persist(w.getServer(),c);
 }
 private static void trip(MinecraftServer server,Config c,List<ServerPlayerEntity> participants){
  if(c.fired)return;
  c.fired=true;c.burntPending=true;c.generatorStopPending=true;
  persist(server,c); // Persist the one-shot + required stop before touching loaded chunks.
  refresh(server); // Stop the chosen generator, burn the fuse and darken the circuit.
  ServerWorld w=world(server,c.dimension);
  if(w!=null){
   // A sharp electrical failure at the panel. Sound only: no explosion entity,
   // block damage, particles or player knockback.
   w.playSound(null,c.panel(),PowerDevices.FAIL,SoundCategory.BLOCKS,1.2f,.7f);
   // The event trigger can be far from the panel. Deliver a one-shot jump-scare
   // close to each participant, so the sound is always audible there.
   // Use built-in Minecraft sounds; no external resource pack is required.
   for(ServerPlayerEntity p:participants){
    BlockPos ear=p.getBlockPos();
    w.playSound(null,ear,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.MASTER,1.7f,1.18f);
    w.playSound(null,ear,SoundEvents.BLOCK_GLASS_BREAK,SoundCategory.MASTER,1.35f,.75f);
    // A story line in the ORIGINAL Whiteout DialogueScreen, never chat.
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,new PowerNetwork.OutageDialogue());
   }
  }
  startQuest(server,c);persist(server,c);
 }
 private static void startQuest(MinecraftServer server,Config c){
  if(c.questDone)return;
  try{
   Store store=Store.get(server);if(store.error!=null)return;
   var data=store.data;
   if(!data.objectives.containsKey(QUEST))ObjectiveRules.define(data.objectives,QUEST,"Восстановить электричество");
   var objective=data.objectives.get(QUEST);
   if(objective.status==ObjectiveRules.Status.COMPLETED)objective.status=ObjectiveRules.Status.INACTIVE;
   data.activeObjective=ObjectiveRules.start(data.objectives,data.storyVariables,data.activeObjective,QUEST);
   objective.description="Найдите запасной предохранитель для щитка.";
   store.save();WhiteoutNetwork.syncAll(server);
  }catch(Exception ex){System.err.println("Whiteout grid quest start: "+ex);}
 }
 private static boolean hasFuse(MinecraftServer s){
  for(ServerPlayerEntity p:s.getPlayerManager().getPlayerList()){
   for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(PowerDevices.FUSE)||p.getInventory().getStack(i).isOf(PowerDevices.UNSTABLE_FUSE))return true;
  }
  return false;
 }
 /** Complete only against the configured fixtures; do not require exactly nine.
  * Defer evaluation when devices/fixtures have never been inspected. */
 private static boolean fixturesVerified(MinecraftServer server,Config c){
  if(c.lamps.isEmpty())return false;
  for(Fixture f:c.lamps){
   ServerWorld w=world(server,f.dimension);
   if(w==null||!w.isChunkLoaded(f.pos()))return false;
   BlockState state=w.getBlockState(f.pos());
   if(f.vanilla){if(!state.isOf(PowerDevices.LINKED_LANTERN)&&!state.isOf(Blocks.LANTERN))return false;}
   else if(!Registries.BLOCK.getId(state.getBlock()).toString().equals(f.original))return false;
  }
  return true;
 }
 private static void quest(MinecraftServer server,Config c){
  if(!healthy(c)||!c.fired||c.questDone||!c.panelBound||!c.generatorBound)return;
  // Unloaded device chunks must NOT rewind objectives, prematurely complete
  // them based only on a historical snapshot, or trigger a syncAll flood.
  ServerWorld questWorld=world(server,c.dimension);
  if(questWorld==null||!questWorld.isChunkLoaded(c.panel())||!questWorld.isChunkLoaded(c.generator()))return;
  if(!c.panelObserved||!c.generatorObserved)return;
  String next;
  if(c.burntPending||c.panelBurnt)next="Извлеките перегоревший предохранитель из щитка";
  else if(!c.panelFuse)next=hasFuse(server)?"Установите запасной предохранитель в щиток":"Найдите запасной предохранитель";
  else if(!c.generatorFueled)next="Заправьте генератор";
  else if(!c.generatorRunning||c.generatorStopPending)next="Заведите генератор (3 успешных рывка)";
  else if(!GridStatePolicy.questComplete(powered(server,c),c.lamps.size(),fixturesVerified(server,c)))
   next="Проверьте привязку электрической цепи";
  else next="Готово";
  if(next.equals(c.questStep))return;
  try{
   Store store=Store.get(server);if(store.error!=null)return;
   var data=store.data;
   var obj=data.objectives.get(QUEST);
   if(obj==null)return;
   // Store is authoritative for quest progress. Roll back memory on a failed
   // atomic save, and leave the grid step unchanged so the next tick retries.
   var previous=GSON.fromJson(GSON.toJson(data),Store.Data.class);
   try{
    if(next.equals("Готово")){data.activeObjective=ObjectiveRules.complete(data.objectives,data.storyVariables,data.activeObjective,QUEST);}
    else if(obj.status==ObjectiveRules.Status.ACTIVE)ObjectiveRules.update(data.objectives,data.storyVariables,QUEST,next);
    store.save();
   }catch(Exception failure){store.data=previous;throw failure;}
   c.questFailure.clear();
   c.questStep=next;c.questDone=next.equals("Готово");persist(server,c);
   WhiteoutNetwork.syncAll(server);
  }catch(Exception ex){
   if(c.questFailure.report(ex.toString(),System.nanoTime()))
    org.slf4j.LoggerFactory.getLogger("Whiteout").error("Grid quest update failed; unchanged failures are logged at most once per minute",ex);
  }
 }

 private static void feedback(ServerCommandSource src,String message){src.sendFeedback(()->Text.literal("[Whiteout] "+message),false);}
 private static void checked(ServerCommandSource src,BlockPos p,int kind) throws CommandSyntaxException{
  if(device(src.getWorld(),p,kind)==null)throw commandFailure("На этих координатах нет "+(kind==0?"генератора":"электрощитка")+" Whiteout в загруженном чанке");
 }
 private static int setDevice(ServerCommandSource src,BlockPos p,boolean generator) throws CommandSyntaxException{
  return setDevice(src,get(src.getServer()),p,generator);
 }
 private static int setDevice(ServerCommandSource src,Config c,BlockPos p,boolean generator) throws CommandSyntaxException{
  checked(src,p,generator?0:1);MinecraftServer server=src.getServer();requireHealthy(c);
  String dim=src.getWorld().getRegistryKey().getValue().toString();
  if(claimedElsewhere(server,c,dim,p))throw commandFailure("Блок уже принадлежит другой электросети");
  if(!c.dimension.equals(dim)&&(c.panelBound||c.generatorBound||!c.lamps.isEmpty()||!c.pendingRestores.isEmpty()))
   throw commandFailure("Устройства одной сети должны находиться в одном измерении");
  DeviceEntity previousPanel=null;
  if(!generator&&c.panelBound&&(!c.dimension.equals(dim)||!c.panel().equals(p)))
   previousPanel=device(world(server,c.dimension),c.panel(),1);
  c.dimension=dim;
  if(generator){
   c.generatorX=p.getX();c.generatorY=p.getY();c.generatorZ=p.getZ();c.generatorBound=true;
   c.generatorObserved=false;c.generatorRunning=false;c.generatorFueled=false;
  }else{
   c.panelX=p.getX();c.panelY=p.getY();c.panelZ=p.getZ();c.panelBound=true;
   c.panelObserved=false;c.panelFuse=false;c.panelBurnt=false;
  }
  c.cachedPower=null;persist(server,c);refresh(server);
  if(previousPanel!=null&&previousPanel.active())previousPanel.setActive(false);
  feedback(src,"Привязано к сети '"+networkId(server,c)+"': "+p.toShortString());return 1;
 }
 private static boolean compatible(BlockState st){
  String id=Registries.BLOCK.getId(st.getBlock()).toString();
  return st.isOf(Blocks.LANTERN)||st.isOf(PowerDevices.LINKED_LANTERN)||st.isOf(PowerDevices.EMERGENCY_LAMP)||
   (id.startsWith("furniture:lamp_")&&st.contains(Properties.LIT));
 }
 private static List<BlockPos> furnitureParts(ServerWorld w,BlockPos p){
  BlockState s=w.getBlockState(p);
  String id=Registries.BLOCK.getId(s.getBlock()).toString();
  var type=s.getBlock().getStateManager().getProperty("type");
  if(!id.startsWith("furniture:lamp_")||type==null||"none".equals(s.get(type).toString()))return List.of(p);
  List<BlockPos> result=new ArrayList<>();result.add(p);
  for(int sign:new int[]{-1,1})for(int offset=1;offset<=16;offset++){
   BlockPos part=p.add(0,offset*sign,0);
   if(!w.isChunkLoaded(part)||!w.getBlockState(part).isOf(s.getBlock()))break;
   result.add(part);
  }
  return result;
 }
 private static int lamp(ServerCommandSource src,BlockPos pos,boolean add) throws CommandSyntaxException{
  return lamp(src,get(src.getServer()),pos,add);
 }
 private static int lamp(ServerCommandSource src,Config c,BlockPos pos,boolean add) throws CommandSyntaxException{
  MinecraftServer server=src.getServer();requireHealthy(c);ServerWorld w=src.getWorld();
  String dim=w.getRegistryKey().getValue().toString();
  if(!c.dimension.equals(dim))throw commandFailure("Неправильное измерение");
  if(!w.isChunkLoaded(pos))throw commandFailure("Чанк должен быть загружен");
  List<BlockPos> parts=furnitureParts(w,pos);
  if(!add){
   int removed=0;
   for(BlockPos part:parts){
    Fixture old=find(c,dim,part);
    if(old==null)continue;
    c.lamps.remove(old);removed++;
    restore(w,old);
   }
   if(removed==0)throw commandFailure("Светильник не привязан");
   persist(server,c);refresh(server);feedback(src,"Отвязано частей: "+removed+"; осталось ламп: "+c.lamps.size());return removed;
  }
  for(BlockPos part:parts){
   if(claimedElsewhere(server,c,dim,part))throw commandFailure("Светильник уже находится в другой электросети");
   if(find(c,dim,part)!=null)throw commandFailure("Одна из частей светильника уже привязана");
   if(!compatible(w.getBlockState(part)))throw commandFailure("Светильник несовместим");
  }
  for(BlockPos part:parts){Fixture fixture=new Fixture(dim,part,w.getBlockState(part));c.lamps.add(fixture);}
  persist(server,c);
  for(BlockPos part:parts)apply(w,find(c,dim,part),powered(server,c),PowerSwitches.allows(server,networkId(server,c),address(dim,part)));
  feedback(src,"Добавлено частей: "+parts.size()+". Всего: "+c.lamps.size());return parts.size();

 }
 private static int status(ServerCommandSource src){
  return status(src,get(src.getServer()));
 }
 private static int status(ServerCommandSource src,Config c){
  MinecraftServer server=src.getServer();
  if(!healthy(c)){feedback(src,c.configurationError+" (исправь файл и выполни /whiteout power reload)");return 0;}
  String reason=!c.panelBound?"щиток не привязан":!c.generatorBound?"генератор не привязан":
   !c.panelObserved?"щиток отсутствует или пока не проверен":
   !c.generatorObserved?"генератор отсутствует или пока не проверен":
   c.burntPending||c.panelBurnt?"предохранитель перегорел":
   !c.panelFuse?"нет предохранителя":!c.generatorFueled?"нет топлива":
   c.generatorStopPending||!c.generatorRunning?"генератор не заведён":
   "питание есть";
  feedback(src,"Сеть '"+networkId(server,c)+"': ламп "+c.lamps.size()+"; измерение: "+c.dimension+
   "; щиток "+(c.panelBound?c.panel().toShortString():"нет")+
   "; генератор "+(c.generatorBound?c.generator().toShortString():"нет")+"; "+reason);
  return 1;
 }
 private static Config requested(ServerCommandSource src,String id) throws CommandSyntaxException{
  if(MAIN.equals(id))return get(src.getServer());
  NetworkStore ns=networks(src.getServer());
  if(ns.error!=null)throw commandFailure("Файл дополнительных сетей повреждён: "+ns.error);
  Config c=ns.grids.get(id);
  if(c==null)throw commandFailure("Электросеть не найдена: "+id);
  return c;
 }
 private static int listNetworks(ServerCommandSource src){
  NetworkStore ns=networks(src.getServer());
  if(ns.error!=null){feedback(src,"Дополнительные сети отключены: "+ns.error);return 0;}
  feedback(src,"Электросети: main"+(ns.grids.isEmpty()?"":", "+String.join(", ",ns.grids.keySet())));
  return 1+ns.grids.size();
 }
 private static int createNetwork(ServerCommandSource src,String id,BlockPos pos) throws CommandSyntaxException{
  if(!CircuitTopology.validName(id))throw commandFailure("Название сети: 1–32 символа a-z, 0-9, _ или -, не main");
  NetworkStore ns=networks(src.getServer());
  if(ns.error!=null||!healthy(get(src.getServer())))throw commandFailure("Сначала исправь конфигурацию электросети");
  if(ns.grids.containsKey(id)||ns.deleting.containsKey(id))throw commandFailure("Сеть с таким именем уже существует или ожидает завершения удаления");
  checked(src,pos,1);
  String dim=src.getWorld().getRegistryKey().getValue().toString();
  if(claimedElsewhere(src.getServer(),null,dim,pos))throw commandFailure("Этот блок уже принадлежит другой электросети");
  Config c=new Config();c.dimension=dim;c.panelX=pos.getX();c.panelY=pos.getY();c.panelZ=pos.getZ();c.panelBound=true;
  ns.grids.put(id,c);
  if(!saveNetworks(src.getServer(),ns)){
   ns.grids.remove(id);
   throw commandFailure("Не удалось сохранить новую электросеть; создание отменено");
  }
  refresh(src.getServer());
  feedback(src,"Создана отдельная сеть '"+id+"' со щитком "+pos.toShortString());return 1;
 }
 private static int deleteNetwork(ServerCommandSource src,String id) throws CommandSyntaxException{
  if(MAIN.equals(id))throw commandFailure("Основную сеть нельзя удалить");
  Config c=requested(src,id);
  NetworkStore ns=networks(src.getServer());ns.grids.remove(id);ns.deleting.put(id,c);
  if(!saveNetworks(src.getServer(),ns)){
   ns.deleting.remove(id);ns.grids.put(id,c);
   throw commandFailure("Не удалось сохранить удаление сети; сеть сохранена");
  }
  finishDeletions(src.getServer());
  feedback(src,"Сеть '"+id+"' удалена; восстановление выгруженных устройств завершится при загрузке чанков");return 1;
 }
 private static void finishDeletions(MinecraftServer server){
  NetworkStore ns=networks(server);if(ns.error!=null||ns.deleting.isEmpty())return;
  for(String id:new ArrayList<>(ns.deleting.keySet())){
   Config c=ns.deleting.get(id);
   if(!PowerSwitches.removeNetwork(server,id)||!ElectricDoors.removeNetwork(server,id))continue;
   Config before=GSON.fromJson(GSON.toJson(c),Config.class);
   boolean ready=true;
   for(var it=c.lamps.iterator();it.hasNext();){Fixture f=it.next();ServerWorld w=world(server,f.dimension);if(w==null||!w.isChunkLoaded(f.pos()))ready=false;else{restore(w,f);it.remove();}}
   for(var it=c.pendingRestores.iterator();it.hasNext();){Fixture f=it.next();ServerWorld w=world(server,f.dimension);if(w==null||!w.isChunkLoaded(f.pos()))ready=false;else{restore(w,f);it.remove();}}
   ServerWorld w=world(server,c.dimension);
   if(c.panelBound){
    if(w==null||!w.isChunkLoaded(c.panel()))ready=false;
    else{DeviceEntity p=device(w,c.panel(),1);if(p!=null&&p.active())p.setActive(false);c.panelBound=false;}
   }
   if(c.generatorBound){
    if(w==null||!w.isChunkLoaded(c.generator()))ready=false;
    else{DeviceEntity g=device(w,c.generator(),0);if(g!=null)g.emergencyStop();c.generatorBound=false;}
   }
   boolean changed=ready||before.lamps.size()!=c.lamps.size()||before.pendingRestores.size()!=c.pendingRestores.size()
      ||before.panelBound!=c.panelBound||before.generatorBound!=c.generatorBound;
   if(ready)ns.deleting.remove(id);
   if(changed&&!saveNetworks(server,ns))ns.deleting.put(id,before);
  }
 }
 private static int eventStatus(ServerCommandSource src){
  Config c=get(src.getServer());if(!healthy(c)){feedback(src,c.configurationError);return 0;}feedback(src,"Триггер "+(c.triggerSet?(c.dimension+" @ "+c.triggerX+" "+c.triggerY+" "+c.triggerZ+", r="+c.radius):"НЕ ЗАДАН")+"; авария "+(c.fired?"уже была":"ещё не была")+"; задание "+(c.questDone?"выполнено":c.questStep));return 1;
 }
 private static int setTrigger(ServerCommandSource src,BlockPos pos) throws CommandSyntaxException{
  Config c=get(src.getServer());requireHealthy(c);
  if(!c.dimension.equals(src.getWorld().getRegistryKey().getValue().toString()))throw commandFailure("Точка должна быть в измерении электрической цепи");
  c.triggerX=pos.getX();c.triggerY=pos.getY();c.triggerZ=pos.getZ();c.triggerSet=true;c.armed=false;
  persist(src.getServer(),c);feedback(src,"Точка аварии: "+pos.toShortString());return 1;
 }
 /** Delete the configured trigger location without replaying an already-fired incident.
  * Does not touch the quest, fuse, generator, or linked devices. */
 private static int clearTrigger(ServerCommandSource src) throws CommandSyntaxException{
  Config c=get(src.getServer());requireHealthy(c);
  c.triggerSet=false;
  c.armed=false;
  c.triggerX=0;c.triggerY=0;c.triggerZ=0;
  persist(src.getServer(),c);
  feedback(src,"Точка аварии удалена. Событие отключено до /whiteout event setpos. Состояние аварии и задания сохранено.");
  return 1;
 }
 private static int reset(ServerCommandSource src,boolean quest) throws CommandSyntaxException{
  Config c=get(src.getServer());requireHealthy(c);c.fired=false;c.armed=false;c.burntPending=false;c.generatorStopPending=false;c.questStep="";
  // Reset never creates a spare fuse and never restores the physical fuse/panel.
  if(quest){
   c.questDone=false;
   try{Store store=Store.get(src.getServer());var data=store.data;var obj=data.objectives.get(QUEST);
    if(obj!=null){obj.status=ObjectiveRules.Status.INACTIVE;if(QUEST.equals(data.activeObjective))data.activeObjective="";store.save();WhiteoutNetwork.syncAll(src.getServer());}
   }catch(Exception ex){System.err.println("Whiteout grid quest reset: "+ex);}
  }
  persist(src.getServer(),c);feedback(src,quest?"Авария и задание сброшены (предметы не создавались)":"Авария сброшена (предохранитель не восстановлен)");return 1;
 }
 private static void commands(CommandDispatcher<ServerCommandSource> d){
  d.register(literal("whiteout")
   .then(literal("dialogues").requires(src->src.hasPermissionLevel(2)).executes(c->{
    var player=c.getSource().getPlayerOrThrow();
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new PowerNetwork.OpenDialogues());
    return 1;
   }))
   .then(literal("dialogue").requires(src->src.hasPermissionLevel(2))
    .then(literal("play").then(argument("id",StringArgumentType.greedyString()).executes(c->{
     var player=c.getSource().getPlayerOrThrow();
     net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new PowerNetwork.PlayDialogue(StringArgumentType.getString(c,"id"),false));return 1;
    })))
    .then(literal("trigger").then(argument("id",StringArgumentType.greedyString()).executes(c->{
     var player=c.getSource().getPlayerOrThrow();
     net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new PowerNetwork.PlayDialogue(StringArgumentType.getString(c,"id"),true));return 1;
    })))
   )
   .then(literal("power").requires(src->src.hasPermissionLevel(2))
    .then(literal("status").executes(c->status(c.getSource())))
    .then(literal("reload").executes(c->reload(c.getSource())))
    .then(literal("network")
     .then(literal("list").executes(c->listNetworks(c.getSource())))
     .then(literal("status").then(argument("id",StringArgumentType.word()).executes(c->status(c.getSource(),requested(c.getSource(),StringArgumentType.getString(c,"id"))))))
     .then(literal("create").then(argument("id",StringArgumentType.word())
      .then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->createNetwork(c.getSource(),StringArgumentType.getString(c,"id"),BlockPosArgumentType.getBlockPos(c,"pos"))))))
     .then(literal("generator").then(argument("id",StringArgumentType.word())
      .then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->setDevice(c.getSource(),requested(c.getSource(),StringArgumentType.getString(c,"id")),BlockPosArgumentType.getBlockPos(c,"pos"),true)))))
     .then(literal("lamp")
      .then(literal("add").then(argument("id",StringArgumentType.word())
       .then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->lamp(c.getSource(),requested(c.getSource(),StringArgumentType.getString(c,"id")),BlockPosArgumentType.getBlockPos(c,"pos"),true)))))
      .then(literal("remove").then(argument("id",StringArgumentType.word())
       .then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->lamp(c.getSource(),requested(c.getSource(),StringArgumentType.getString(c,"id")),BlockPosArgumentType.getBlockPos(c,"pos"),false))))))
     .then(literal("delete").then(argument("id",StringArgumentType.word()).executes(c->deleteNetwork(c.getSource(),StringArgumentType.getString(c,"id")))))
    )
    .then(literal("panel").then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->setDevice(c.getSource(),BlockPosArgumentType.getBlockPos(c,"pos"),false))))
    .then(literal("generator").then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->setDevice(c.getSource(),BlockPosArgumentType.getBlockPos(c,"pos"),true))))
    .then(literal("lamp")
     .then(literal("add").then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->lamp(c.getSource(),BlockPosArgumentType.getBlockPos(c,"pos"),true))))
     .then(literal("remove").then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->lamp(c.getSource(),BlockPosArgumentType.getBlockPos(c,"pos"),false)))))
   )
   .then(literal("event").requires(src->src.hasPermissionLevel(2))
    .then(literal("status").executes(c->eventStatus(c.getSource())))
    .then(literal("setpos").executes(c->setTrigger(c.getSource(),BlockPos.ofFloored(c.getSource().getPosition())))
     .then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->setTrigger(c.getSource(),BlockPosArgumentType.getBlockPos(c,"pos")))))
    .then(literal("clearpos").executes(c->clearTrigger(c.getSource())))
    .then(literal("radius").then(argument("blocks",IntegerArgumentType.integer(1,64)).executes(c->{Config g=get(c.getSource().getServer());requireHealthy(g);g.radius=IntegerArgumentType.getInteger(c,"blocks");save(c.getSource().getServer(),g);return eventStatus(c.getSource());})))
    .then(literal("fusepos").then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->{Config g=get(c.getSource().getServer());requireHealthy(g);BlockPos p=BlockPosArgumentType.getBlockPos(c,"pos");g.fuseLocationSet=true;g.fuseX=p.getX();g.fuseY=p.getY();g.fuseZ=p.getZ();save(c.getSource().getServer(),g);feedback(c.getSource(),"Место запасного предохранителя отмечено: "+p.toShortString()+" (предмет не создаётся)");return 1;})))
    .then(literal("reset").executes(c->reset(c.getSource(),false)))
    .then(literal("questreset").executes(c->reset(c.getSource(),true)))
   )
  );
 }
}
