package dev.whiteout.power;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.whiteout.Store;
import dev.whiteout.Zone;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.WorldSavePath;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Atmosphere gameplay configuration, loaded once per world; never changes existing Zone JSON. */
public final class WorldAtmosphere {
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static final Map<MinecraftServer,AtmosphereConfig> SETTINGS=new WeakHashMap<>();
 private static int ticks;
 public static final int DOOR_FREEZE_TICKS = 1600;
 private WorldAtmosphere(){}
 static boolean worldStateAvailable(MinecraftServer server){get(server);return !INVALID.contains(server);}
 public static AtmosphereConfig get(MinecraftServer server){return SETTINGS.computeIfAbsent(server,WorldAtmosphere::load);}
 public static com.google.gson.JsonObject editorSnapshot(MinecraftServer s,String id){
  AtmosphereConfig c=get(s);var o=new com.google.gson.JsonObject();
  o.addProperty("old",c.oldShelters.contains(id));o.addProperty("muted",c.mutedOldSoundZones.contains(id));
  o.add("interval",c.oldSoundIntervals.containsKey(id)?new com.google.gson.JsonPrimitive(c.oldSoundIntervals.get(id)):com.google.gson.JsonNull.INSTANCE);return o;
 }
 public static void editorRestore(MinecraftServer s,String id,com.google.gson.JsonObject o){
  AtmosphereConfig c=get(s);if(INVALID.contains(s))throw new IllegalStateException("Данные атмосферы недоступны");
  var previous=editorSnapshot(s,id);editorAssign(c,id,o);
  try{
   Path path=file(s),tmp=path.resolveSibling(path.getFileName()+".tmp");Files.createDirectories(path.getParent());
   Files.writeString(tmp,GSON.toJson(c));Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);
  }catch(Exception e){editorAssign(c,id,previous);throw new IllegalStateException("Ошибка сохранения атмосферы",e);}syncAll(s);
 }
 private static void editorAssign(AtmosphereConfig c,String id,com.google.gson.JsonObject o){
  if(o.get("old").getAsBoolean())c.oldShelters.add(id);else c.oldShelters.remove(id);
  if(o.get("muted").getAsBoolean())c.mutedOldSoundZones.add(id);else c.mutedOldSoundZones.remove(id);
  if(o.get("interval").isJsonNull())c.oldSoundIntervals.remove(id);else c.oldSoundIntervals.put(id,o.get("interval").getAsInt());
 }
 private static Path file(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("whiteout-atmosphere.json");}
 private static AtmosphereConfig load(MinecraftServer server){
  try{
   AtmosphereConfig c=Files.isRegularFile(file(server))?GSON.fromJson(Files.readString(file(server)),AtmosphereConfig.class):new AtmosphereConfig();
   if(c==null)throw new IllegalArgumentException("Empty atmosphere configuration");c.normalize();return c;
  }catch(Exception e){System.err.println("[Whiteout] Cannot read whiteout-atmosphere.json; original not overwritten: "+e);AtmosphereConfig c=new AtmosphereConfig();INVALID.add(server);return c;}
 }
 private static final Set<MinecraftServer> INVALID=Collections.newSetFromMap(new WeakHashMap<>());
 private static void save(MinecraftServer server){
  if(INVALID.contains(server))return;
  try{
   Path path=file(server);Files.createDirectories(path.getParent());
   Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,GSON.toJson(get(server)));
   Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);
  }catch(Exception e){System.err.println("[Whiteout] Cannot save atmosphere: "+e);}
 }
 private static void sync(ServerPlayerEntity player){
  AtmosphereConfig config=get(player.getServer());
  ServerPlayNetworking.send(player,EnvironmentNetwork.from(config));
  for(EnvironmentNetwork.OldSheltersChunk chunk:EnvironmentNetwork.chunks(config))ServerPlayNetworking.send(player,chunk);
  for(var e:config.oldSoundIntervals.entrySet())ServerPlayNetworking.send(player,new EnvironmentNetwork.OldSoundRate(e.getKey(),e.getValue()));
  for(String zone:config.mutedOldSoundZones)ServerPlayNetworking.send(player,new EnvironmentNetwork.MutedOldSound(zone));
 }
 private static void syncAll(MinecraftServer server){for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())sync(player);}
 public static boolean oldBuilding(MinecraftServer server,String zone){return get(server).oldShelters.contains(zone);}
 public static void setOldSound(MinecraftServer server,String zone,boolean enabled,int seconds){
  if(server==null||INVALID.contains(server)||zone==null||zone.isBlank()||seconds<8||seconds>300)return;
  AtmosphereConfig config=get(server);
  if(!config.oldShelters.contains(zone))return;
  config.oldSoundIntervals.put(zone,seconds);
  if(enabled)config.mutedOldSoundZones.remove(zone);else config.mutedOldSoundZones.add(zone);
  save(server);syncAll(server);
 }
 public static void setOld(MinecraftServer server,String zone,boolean old){
  if(server==null||INVALID.contains(server)||zone==null||zone.isBlank())return;
  boolean exists=false;
  try{for(Zone z:Store.get(server).data.zones)if(z!=null&&zone.equals(z.id)&&z.shelter){exists=true;break;}}catch(Exception ignored){}
  if(!exists)return;
  if(old)get(server).oldShelters.add(zone);else get(server).oldShelters.remove(zone);
  save(server);syncAll(server);
 }
 private static int setOldInterval(ServerCommandSource src,String zone,int seconds){
  MinecraftServer server=src.getServer();AtmosphereConfig c=get(server);
  if(INVALID.contains(server)||!c.oldShelters.contains(zone)){
   src.sendError(Text.translatable("message.whiteout.atmosphere.not_old"));return 0;
  }
  c.oldSoundIntervals.put(zone,seconds);save(server);syncAll(server);
  src.sendFeedback(()->Text.translatable("message.whiteout.atmosphere.interval",zone,seconds),false);
  return 1;
 }
 public static Zone shelter(World world,BlockPos pos){return choose(world,pos,true);}
 public static Zone weather(World world,BlockPos pos){return choose(world,pos,false);}
 private static Zone choose(World world,BlockPos pos,boolean shelter){
  if(!(world instanceof ServerWorld sw))return null;
  try{
   List<Zone> zones=Store.get(sw.getServer()).data.zones;
   return dev.whiteout.ZoneSelection.chooseMatching(zones,z->z.enabled&&z.shelter==shelter,
    pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,sw.getRegistryKey().getValue().toString());
  }catch(Exception ignored){return null;}
 }
 public static boolean blizzard(World world,BlockPos pos){Zone z=weather(world,pos);return z!=null&&z.blizzard;}
 public static float temperature(ServerWorld world,BlockPos pos){
  AtmosphereConfig c=get(world.getServer());Zone w=weather(world,pos);
  float outside=w==null?12f:w.blizzard?c.tempOutdoorBlizzard:c.tempOutdoorNormal;
  Zone shelter=shelter(world,pos);
  if(shelter==null)return outside;
  if(!c.coldLeaks)return 19f;
  return ShelterExposure.indoorTemperature(outside,ShelterExposure.measure(world,Vec3d.ofCenter(pos).add(0,.65,0)).opening());
 }
 private static String key(World w,BlockPos p){return w.getRegistryKey().getValue()+":"+p.getX()+","+p.getY()+","+p.getZ();}
 private static BlockPos lowerDoor(World world,BlockPos pos){
  BlockState s=world.getBlockState(pos);
  if(s.getBlock() instanceof DoorBlock&&s.contains(Properties.DOUBLE_BLOCK_HALF)&&s.get(Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER)return pos.down();
  return pos;
 }
 /** Blizzard weather zones can end at a shelter boundary: sample outside the door too. */
 private static boolean icyOutside(World world,BlockPos at){
  if(blizzard(world,at))return true;
  for(net.minecraft.util.math.Direction side:net.minecraft.util.math.Direction.Type.HORIZONTAL){
   BlockPos next=at.offset(side);
   if(world.isChunkLoaded(next)&&blizzard(world,next))return true;
   next=next.offset(side);
   if(world.isChunkLoaded(next)&&blizzard(world,next))return true;
  }
  return false;
 }
 private static BlockPos targeted(ServerPlayerEntity p){
  HitResult result=p.raycast(6,0,false);return result instanceof BlockHitResult hit?hit.getBlockPos():null;
 }
 private static int doorCommand(ServerCommandSource source){
  if(!(source.getEntity() instanceof ServerPlayerEntity player))return 0;
  BlockPos pos=targeted(player);if(pos==null)return 0;
  ServerWorld w=player.getServerWorld();pos=lowerDoor(w,pos);
  if(!(w.getBlockState(pos).getBlock() instanceof DoorBlock)){source.sendError(Text.translatable("message.whiteout.atmosphere.look_door"));return 0;}
  String id=key(w,pos);
  AtmosphereConfig c=get(player.getServer());
  boolean added=c.freezingDoors.add(id);if(!added)c.freezingDoors.remove(id);
  save(player.getServer());source.sendFeedback(()->Text.translatable(added?"message.whiteout.atmosphere.door_enabled":"message.whiteout.atmosphere.door_disabled"),false);return 1;
 }
 private static void registerCommands(){
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("whiteout")
   .then(literal("atmosphere").requires(s->s.hasPermissionLevel(2))
    .then(literal("old").then(argument("zone",StringArgumentType.word())
     .then(argument("enabled",BoolArgumentType.bool()).executes(ctx->{setOld(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"zone"),BoolArgumentType.getBool(ctx,"enabled"));return 1;}))))
    .then(literal("frozen-door").executes(ctx->doorCommand(ctx.getSource())))
    .then(literal("old-interval").then(argument("zone",StringArgumentType.word())
     .then(argument("seconds",IntegerArgumentType.integer(8,300)).executes(ctx->setOldInterval(ctx.getSource(),StringArgumentType.getString(ctx,"zone"),IntegerArgumentType.getInteger(ctx,"seconds"))))))
    .then(literal("reload").executes(ctx->{
     MinecraftServer server=ctx.getSource().getServer();INVALID.remove(server);
     AtmosphereConfig loaded=load(server);
     if(INVALID.contains(server)){ctx.getSource().sendError(Text.translatable("message.whiteout.atmosphere.invalid_file"));return 0;}
     SETTINGS.put(server,loaded);syncAll(server);
     ctx.getSource().sendFeedback(()->Text.translatable("message.whiteout.atmosphere.reloaded"),false);return 1;
    }))
    .then(literal("old-sounds").then(argument("enabled",BoolArgumentType.bool()).executes(ctx->{
     AtmosphereConfig c=get(ctx.getSource().getServer());c.oldHouseSounds=BoolArgumentType.getBool(ctx,"enabled");
     save(ctx.getSource().getServer());syncAll(ctx.getSource().getServer());return 1;
    })))
    .then(literal("radio-static").then(argument("enabled",BoolArgumentType.bool()).executes(ctx->{AtmosphereConfig c=get(ctx.getSource().getServer());c.radioStatic=BoolArgumentType.getBool(ctx,"enabled");save(ctx.getSource().getServer());syncAll(ctx.getSource().getServer());return 1;})))
    .then(literal("wind-movement").then(argument("enabled",BoolArgumentType.bool()).executes(ctx->{AtmosphereConfig c=get(ctx.getSource().getServer());c.windMovement=BoolArgumentType.getBool(ctx,"enabled");save(ctx.getSource().getServer());ctx.getSource().sendFeedback(()->Text.literal("Wind movement: "+(c.windMovement?"ON":"OFF")),false);return 1;})))
    .then(literal("wind-strength").then(argument("percent",IntegerArgumentType.integer(0,100)).executes(ctx->{
     MinecraftServer server=ctx.getSource().getServer();AtmosphereConfig c=get(server);
     if(INVALID.contains(server)){ctx.getSource().sendError(Text.literal("Ошибка: данные атмосферы не загружены"));return 0;}
     c.windStrengthPercent=IntegerArgumentType.getInteger(ctx,"percent");save(server);syncAll(server);
     ctx.getSource().sendFeedback(()->Text.literal("Общий ветер: "+c.windStrengthPercent+"%"),false);return 1;
    })))
    .then(literal("footprints").then(argument("enabled",BoolArgumentType.bool()).executes(ctx->{AtmosphereConfig c=get(ctx.getSource().getServer());c.indoorFootprints=false;save(ctx.getSource().getServer());ctx.getSource().sendFeedback(()->Text.translatable("message.whiteout.footprints_disabled"),false);return 1;})))
    .then(literal("status").executes(ctx->{AtmosphereConfig c=get(ctx.getSource().getServer());ctx.getSource().sendFeedback(()->Text.translatable("message.whiteout.atmosphere.status",c.oldShelters.size(),c.freezingDoors.size(),c.indoorFootprints),false);return 1;})))));
 }
 /** Return configured doors that are currently frozen (not on thaw cooldown). */
 public static List<BlockPos> nearbyFrozen(ServerWorld world,BlockPos around,int radius){
  List<BlockPos> result=new ArrayList<>();
  String dim=world.getRegistryKey().getValue().toString()+":";
  for(String id:get(world.getServer()).freezingDoors){
   if(!id.startsWith(dim))continue;
   String[] xyz=id.substring(dim.length()).split(",");if(xyz.length!=3)continue;
   try{
    BlockPos p=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
    if(!p.isWithinDistance(around.toCenterPos(),radius)||!world.isChunkLoaded(p))continue;
    BlockState s=world.getBlockState(p);
    if(!(s.getBlock() instanceof DoorBlock)||s.get(DoorBlock.OPEN)||!icyOutside(world,p))continue;
    AtmosphereConfig.DoorFrostState d=get(world.getServer()).doorStates.get(id);
    if(d==null||d.coldTicks<DOOR_FREEZE_TICKS)continue;
    result.add(p);
   }catch(NumberFormatException ignored){}
  }
  return result;
 }
 public static void register(){
  registerCommands();
  ServerLifecycleEvents.SERVER_STARTED.register(WorldAtmosphere::get);
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(SETTINGS.containsKey(server))save(server);SETTINGS.remove(server);INVALID.remove(server);});
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sync(handler.getPlayer()));
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if(world.isClient||!(player instanceof ServerPlayerEntity sp)||player.isSpectator())return ActionResult.PASS;
   BlockPos pos=lowerDoor(world,hit.getBlockPos());
   BlockState bs=world.getBlockState(pos);
   if(world instanceof ServerWorld sw&&ElectricDoors.locked(sw,pos)){sp.sendMessage(Text.translatable("message.whiteout.atmosphere.no_power_door"),true);return ActionResult.FAIL;}
   if(!(bs.getBlock() instanceof DoorBlock)||!get(sp.getServer()).freezingDoors.contains(key(world,pos)))return ActionResult.PASS;
   if(bs.get(DoorBlock.OPEN)||!icyOutside(world,pos))return ActionResult.PASS;
   AtmosphereConfig.DoorFrostState d=get(sp.getServer()).doorStates.get(key(world,pos));
   if(d==null||d.coldTicks<DOOR_FREEZE_TICKS)return ActionResult.PASS;
   if(world.getTime()-d.lastAttempt>120)d.attempts=0;
   d.lastAttempt=world.getTime();d.attempts++;
   if(world instanceof ServerWorld sw)FrozenDoorEffects.crack(sw,pos,bs,d.attempts>=get(sp.getServer()).doorRequiredPulls);
   if(d.attempts<get(sp.getServer()).doorRequiredPulls){save(sp.getServer());sp.sendMessage(Text.translatable("message.whiteout.frozen_door",d.attempts,get(sp.getServer()).doorRequiredPulls),true);return ActionResult.SUCCESS;}
   d.attempts=0;d.coldTicks=0;save(sp.getServer());
   sp.sendMessage(Text.translatable("message.whiteout.door_thawed"),true);
   // Apply both door halves on the authoritative server; do not rely on client prediction.
   world.setBlockState(pos,bs.with(DoorBlock.OPEN,true),3);
   BlockPos upper=pos.up();BlockState upperState=world.getBlockState(upper);
   if(upperState.getBlock()==bs.getBlock())world.setBlockState(upper,upperState.with(DoorBlock.OPEN,true),3);
   world.playSound(null,pos,PowerDevices.OLD_SLAM,SoundCategory.BLOCKS,.8f,.83f);
   return ActionResult.SUCCESS;
  });
  ServerTickEvents.END_SERVER_TICK.register(server->{
   ticks++;
   if(ticks%20==0)updateDoorFrost(server);
   if(ticks%40==0){slamming(server);frostIndicators(server);}
  });
 }
 /** Tiny blue dust motes around the handle tell players a configured door is frozen.
  * Iterates configured positions rather than scanning blocks/chunks every tick. */
 private static void frostIndicators(MinecraftServer server){
  AtmosphereConfig config=get(server);
  for(String encoded:config.freezingDoors){
   if(encoded==null)continue;
   int sep=encoded.lastIndexOf(':');if(sep<0)continue;
   String dim=encoded.substring(0,sep),coords=encoded.substring(sep+1);
   String[] xyz=coords.split(",");if(xyz.length!=3)continue;
   BlockPos pos;
   try{pos=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));}
   catch(NumberFormatException ex){continue;}
   for(ServerWorld world:server.getWorlds()){
    if(!world.getRegistryKey().getValue().toString().equals(dim)||!world.isChunkLoaded(pos))continue;
    BlockState state=world.getBlockState(pos);
    if(!(state.getBlock() instanceof DoorBlock)||state.get(DoorBlock.OPEN)||!icyOutside(world,pos))continue;
    AtmosphereConfig.DoorFrostState opened=config.doorStates.get(encoded);
    if(opened==null||opened.coldTicks<DOOR_FREEZE_TICKS)continue;
    boolean near=false;
    for(ServerPlayerEntity player:world.getPlayers())if(player.squaredDistanceTo(pos.toCenterPos())<400){near=true;break;}
    if(!near)continue;
    FrozenDoorEffects.frost(world,pos,state);
   }
  }
 }
 private static void updateDoorFrost(MinecraftServer server){
  AtmosphereConfig c=get(server);if(INVALID.contains(server))return;
  c.doorStates.keySet().retainAll(c.freezingDoors);
  boolean changed=false;
  for(String id:c.freezingDoors){
   int split=id.lastIndexOf(':');if(split<0)continue;
   String[] xyz=id.substring(split+1).split(",");if(xyz.length!=3)continue;
   try{
    BlockPos pos=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
    for(ServerWorld world:server.getWorlds()){
     if(!world.getRegistryKey().getValue().toString().equals(id.substring(0,split))||!world.isChunkLoaded(pos))continue;
     BlockState block=world.getBlockState(pos);
     boolean cold=block.getBlock() instanceof DoorBlock&&!block.get(DoorBlock.OPEN)&&icyOutside(world,pos);
     AtmosphereConfig.DoorFrostState state=c.doorStates.computeIfAbsent(id,k->new AtmosphereConfig.DoorFrostState());
     int next=cold?Math.min(DOOR_FREEZE_TICKS,state.coldTicks+20):0;
     if(next!=state.coldTicks){state.coldTicks=next;changed=true;}
     if(!cold)state.attempts=0;
    }
   }catch(NumberFormatException ignored){}
  }
  if(changed&&ticks%200==0)save(server);
 }
 /** Never scans unloaded chunks; only nearby open doors in the current shelter. */
 private static void slamming(MinecraftServer server){
  if(!get(server).slammingDoors)return;
  for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()){
   ServerWorld w=p.getServerWorld();BlockPos center=p.getBlockPos();
   Zone zone=shelter(w,center);
   if(zone==null||!blizzard(w,center)||ShelterExposure.measure(w,p.getEyePos()).opening()<.1f)continue;
   for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)for(int dy=-2;dy<=2;dy++){
    BlockPos at=center.add(dx,dy,dz);if(!w.isChunkLoaded(at))continue;
    BlockState state=w.getBlockState(at);
    if(!(state.getBlock() instanceof DoorBlock)||!state.get(DoorBlock.OPEN))continue;
    if(w.random.nextInt(60)!=0)continue;
    // Only the lower half is the source of a two-block-wide state mutation.
    at=lowerDoor(w,at);state=w.getBlockState(at);
    if(!(state.getBlock() instanceof DoorBlock)||!state.get(DoorBlock.OPEN))continue;
    w.setBlockState(at,state.with(DoorBlock.OPEN,false),3);
    BlockPos upper=at.up();BlockState upperState=w.getBlockState(upper);
    if(upperState.getBlock()==state.getBlock())w.setBlockState(upper,upperState.with(DoorBlock.OPEN,false),3);
    w.playSound(null,at,PowerDevices.OLD_SLAM,SoundCategory.BLOCKS,1.1f,.8f);
   }
  }
 }
}
