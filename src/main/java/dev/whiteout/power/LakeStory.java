package dev.whiteout.power;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;

/**
 * Chapter one: Lake cabin -> broken generator -> sawmill -> remote reserve -> lake cabin.
 * Opt-in ONLY via whiteout-lake-scene.json in the save root. All block positions are
 * preflighted against the supplied map. A fresh world is never affected.
 */
public final class LakeStory {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 private static final String CONFIG="whiteout-lake-scene.json";
 private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
 private static final RegistryKey<net.minecraft.world.World> OVERWORLD=RegistryKey.of(RegistryKeys.WORLD,Identifier.of("minecraft","overworld"));
 // The actual short spruce-plank bridge approaches the cabin from the north.
 private static final BlockPos BRIDGE=new BlockPos(8381,72,8366);
 private static final BlockPos HOME=new BlockPos(8407,71,8382);
 private static final BlockPos DOOR=new BlockPos(8411,72,8383);
 private static final BlockPos HOME_GEN=new BlockPos(8404,71,8392);
 private static final BlockPos HOME_PANEL=new BlockPos(8402,72,8392);
 private static final BlockPos MILL_ENTRY=new BlockPos(8390,71,8425);
 private static final BlockPos MILL_PANEL=new BlockPos(8357,73,8436);
 private static final BlockPos MILL_GEN=new BlockPos(8355,72,8436);
 // Separate elevated platform on the west edge of the sawmill; exact user-chosen coordinates.
 private static final BlockPos RESERVE=new BlockPos(8375,76,8414);
 private static final BlockPos LAMP=new BlockPos(8373,76,8414);
 private static final BlockPos OLD_RESERVE=new BlockPos(8385,72,8409);
 private static final BlockPos OLD_LAMP=new BlockPos(8383,72,8411);
 private static final BlockPos SOUND_ORIGIN=new BlockPos(8410,80,8383);
 private static final List<BlockPos> WINDOWS=List.of(SOUND_ORIGIN,SOUND_ORIGIN.up());
 private static final int ONE_CAN=DeviceEntity.CAN_TICKS;
 private static int globalTicks;
 private LakeStory(){}

 private static final class State {
  int version=1;
  LakeBlockJournal journal=new LakeBlockJournal();
  int finalPhase=0;
  boolean resetPending=false;
  boolean decayPrepared=false;
  String conflict="";
  transient boolean recovering=true;
  boolean homeFueled=false,homeAttempted=false;
  int homePulls=0;
  java.util.Set<String> waitingExit=new java.util.HashSet<>();
  transient java.util.Set<String> inside=new java.util.HashSet<>();
  boolean enabled=true;
  int stage=0;
  boolean localPrepared=false, reserveInstalled=false, millActivated=false, decayApplied=false;
  boolean rearmAfterExit=false;
  boolean millSnapshotValid=false, millWasRunning=false;
  boolean millFuseBefore=false, millBurntBefore=false, millUnstableBefore=false;
  int millFuelBefore=0;
  long finalSoundAt=0;
  transient String loadError;
 }
 private static Path file(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve(CONFIG);}
 private static State load(MinecraftServer server){
  if(!Files.isRegularFile(file(server)))return null;
  try{
   State state=JSON.fromJson(Files.readString(file(server)),State.class);
   if(state==null||state.version!=1||state.stage<0||state.stage>11)throw new IOException("invalid chapter configuration");
   if(state.journal==null||state.journal.changes==null||state.waitingExit==null)throw new IOException("invalid scene journal");
   if(state.finalPhase<0||state.finalPhase>3)throw new IOException("invalid final phase");
   for(var change:state.journal.changes){change.before.state();change.after.state();}
   return state;
  }catch(Exception ex){
   System.err.println("[Whiteout Lake] Disabled, preserving invalid scene file: "+ex);
   State disabled=new State();disabled.enabled=false;disabled.loadError=ex.toString();return disabled;
  }
 }
 private static void save(MinecraftServer server,State state){
  if(state==null||state.loadError!=null)return;
  try{GridFileIO.writeAtomic(file(server),JSON.toJson(state));}
  catch(Exception ex){state.loadError=ex.toString();throw new IllegalStateException("Lake scene stopped: original save preserved",ex);}
 }
 private static void next(MinecraftServer server,State state,int stage){
  if(stage<=state.stage)return;
  if(!LakeStoryPolicy.transition(state.stage,stage))throw new IllegalStateException("Invalid lake transition "+state.stage+" -> "+stage);
  state.stage=stage;save(server,state);
 }
 private static void goal(MinecraftServer server,String name){
  for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())if(player.getServerWorld().getRegistryKey().equals(OVERWORLD))
   ServerPlayNetworking.send(player,new LakeStoryPackets.Story(LakeStoryPackets.OBJECTIVE,name));
 }
 private static void speak(MinecraftServer server,String line){
  for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())if(player.getServerWorld().getRegistryKey().equals(OVERWORLD))
   ServerPlayNetworking.send(player,new LakeStoryPackets.Story(LakeStoryPackets.DIALOGUE,line));
 }
 private static void clear(MinecraftServer server){
  for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())
   ServerPlayNetworking.send(player,new LakeStoryPackets.Story(LakeStoryPackets.CLEAR,""));
 }
 private static String activeGoal(int stage){return LakeStoryPolicy.objective(stage);}
 private static boolean inStart(ServerPlayerEntity player){
  return !player.isSpectator()&&player.getServerWorld().getRegistryKey().equals(OVERWORLD)
   &&LakeStoryPolicy.inStart(player.getX(),player.getY(),player.getZ());
 }
 private static State scene(net.minecraft.world.World world){
  if(!(world instanceof ServerWorld sw)||!sw.getRegistryKey().equals(OVERWORLD))return null;
  State state=STATES.get(sw.getServer());return state!=null&&state.enabled?state:null;
 }
 public static boolean blocked(net.minecraft.world.World world,BlockPos pos){return scene(world)!=null&&pos.equals(MILL_GEN);}
 public static boolean brokenHome(net.minecraft.world.World world,BlockPos pos){return scene(world)!=null&&pos.equals(HOME_GEN);}
 public static void refueled(DeviceEntity device){
  State state=scene(device.getWorld());
  if(state==null||!device.getPos().equals(HOME_GEN)||device.fuel<=0)return;
  if(!state.homeFueled){state.homeFueled=true;save(((ServerWorld)device.getWorld()).getServer(),state);}
 }
 /** Called only after DeviceEntity authenticated the starter operator, range, fuel and cooldown. */
 public static boolean homePull(DeviceEntity device){
  State state=scene(device.getWorld());
  if(state==null||!device.getPos().equals(HOME_GEN))return false;
  if(state.loadError==null&&!state.resetPending&&state.stage==3&&state.homeFueled&&device.fuel>0){
   state.homePulls=Math.min(3,state.homePulls+1);
   if(LakeStoryPolicy.afterPull(state.stage,true,state.homePulls)==4){
    state.homeAttempted=true;next(((ServerWorld)device.getWorld()).getServer(),state,4);
    speak(((ServerWorld)device.getWorld()).getServer(),LakeDialogueLines.FAILED_START);
   }else save(((ServerWorld)device.getWorld()).getServer(),state);
  }
  return true;
 }
 public static boolean homeAttemptComplete(DeviceEntity device){State s=scene(device.getWorld());return s!=null&&s.homeAttempted;}
 public static boolean cabinLocked(ServerWorld world,BlockPos pos){
  State s=scene(world);return s!=null&&sameDoor(pos)&&s.stage<8;
 }
 /** Scene-only reserve feed. Circuit ownership, fuses and quest fields are never rewritten. */
 static Boolean circuitPower(MinecraftServer server,GridManager.Config grid){
  State s=STATES.get(server);if(s==null||!s.enabled||!"minecraft:overworld".equals(grid.dimension))return null;
  if(grid.panelBound&&grid.generatorBound&&grid.panel().equals(HOME_PANEL)&&grid.generator().equals(RESERVE))
   return s.stage<8?Boolean.FALSE:null;
  if(grid.panelBound&&grid.generatorBound&&grid.panel().equals(MILL_PANEL)&&grid.generator().equals(MILL_GEN))
   return s.stage>=8&&GridManager.lakeReserveBound(server)&&GridManager.networkPowered(server,"circuit_3");
  return null;
 }
 private static boolean close(ServerPlayerEntity player,BlockPos target,int radius){
  if(!player.getServerWorld().getRegistryKey().equals(OVERWORLD)||player.isSpectator())return false;
  double dx=player.getX()-target.getX()-.5,dz=player.getZ()-target.getZ()-.5;
  return Math.abs(player.getY()-target.getY())<=3&&dx*dx+dz*dz<=radius*radius;
 }
 private static boolean at(ServerPlayerEntity player,BlockPos pos){
  if(!player.getServerWorld().getRegistryKey().equals(OVERWORLD))return false;
  return !player.isSpectator()&&player.squaredDistanceTo(pos.toCenterPos())<=20.25;
 }
 private static boolean sameDoor(BlockPos clicked){return clicked.equals(DOOR)||clicked.equals(DOOR.up());}
 private static DeviceEntity device(ServerWorld world,BlockPos pos){
  return world.isChunkLoaded(pos)&&world.getBlockEntity(pos) instanceof DeviceEntity dev?dev:null;
 }
 private static void prepareLocal(MinecraftServer server,State state,ServerWorld world){
  if(state.localPrepared||!world.isChunkLoaded(HOME_GEN)||!world.isChunkLoaded(HOME_PANEL))return;
  DeviceEntity gen=device(world,HOME_GEN),panel=device(world,HOME_PANEL);
  if(gen==null||panel==null||gen.kind()!=3||panel.kind()!=1){
   System.err.println("[Whiteout Lake] Expected cabin generator/panel missing. Scene configuration preserved.");return;
  }
  gen.emergencyStop();gen.fuel=0;gen.sync();
  // Normal fuse, not an unstable fuse. Without the remote generator this remains OFF.
  panel.sync();
  state.localPrepared=true;save(server,state);GridManager.refresh(server);
 }
 private static void installReserve(MinecraftServer server,State state,ServerWorld world){
  // Placement is handled by the separately verified map copy. Runtime never erases
  // a same-type player block at an old test position or silently claims ownership.
  if(!world.isChunkLoaded(RESERVE))return;
  DeviceEntity reserve=device(world,RESERVE);
  if(reserve!=null&&reserve.kind()==3&&GridManager.lakeReserveBound(server)&&!state.reserveInstalled){
   state.reserveInstalled=true;save(server,state);
  }
 }
 private static void conflict(MinecraftServer server,State state,String message){
  if(message.equals(state.conflict))return;
  state.conflict=message;save(server,state);System.err.println("[Whiteout Lake] "+message);
 }
 private static void ageHouse(MinecraftServer server,State state,ServerWorld world){
  if(state.decayApplied)return;
  List<BlockPos> rotten=List.of(
   new BlockPos(8413,71,8385),new BlockPos(8409,71,8385),new BlockPos(8412,71,8390),
   new BlockPos(8410,71,8391),new BlockPos(8415,71,8388),new BlockPos(8405,72,8384),
   new BlockPos(8417,72,8387),new BlockPos(8417,72,8390),new BlockPos(8408,72,8393),new BlockPos(8410,72,8393));
  for(BlockPos pos:rotten)if(!world.isChunkLoaded(pos))return;
  for(BlockPos pos:SCENE_COBWEBS)if(!world.isChunkLoaded(pos))return;
  if(!state.decayPrepared){
   for(BlockPos pos:rotten)if(world.getBlockState(pos).isOf(Blocks.SPRUCE_PLANKS)&&world.getBlockEntity(pos)==null)
    state.journal.add(world,pos,PowerDevices.ROTTEN_SPRUCE_PLANKS.getDefaultState(),"decay");
   for(BlockPos pos:SCENE_COBWEBS)if(world.getBlockState(pos).isAir()&&world.getBlockEntity(pos)==null)
    state.journal.add(world,pos,Blocks.COBWEB.getDefaultState(),"decay");
   state.decayPrepared=true;save(server,state); // write-ahead, once rather than every retry
  }
  if(!state.journal.apply(world,"decay")){conflict(server,state,"Decoration changed; preserving player blocks");return;}
  // Three durable transaction boundaries per playthrough (decay, glass, explicit
  // reset), never a tick loop. Completed edits must not be replayed over later builds.
  world.getChunkManager().save(true);
  state.decayApplied=true;save(server,state);
 }
 private static boolean glass(BlockState block){
  String id=net.minecraft.registry.Registries.BLOCK.getId(block.getBlock()).toString();
  return id.equals("minecraft:glass")||id.equals("minecraft:glass_pane")||
   (id.startsWith("minecraft:")&&(id.endsWith("_stained_glass")||id.endsWith("_stained_glass_pane")))||
   id.equals("whiteout:rime_glass")||id.equals("whiteout:rime_glass_pane");
 }
 private static void playFinalSound(MinecraftServer server,State state,ServerWorld world){
  for(BlockPos pos:WINDOWS)if(!world.isChunkLoaded(pos))return;
  if(state.finalPhase==0){
   for(BlockPos pos:WINDOWS)if(!glass(world.getBlockState(pos))||world.getBlockEntity(pos)!=null){
    conflict(server,state,"Window preflight failed at "+pos.toShortString()+": "+world.getBlockState(pos));return;
   }
   for(BlockPos pos:WINDOWS)state.journal.add(world,pos,Blocks.AIR.getDefaultState(),"glass");
   state.finalPhase=1;save(server,state);
  }
  if(state.finalPhase==1){
   if(!state.journal.apply(world,"glass")){conflict(server,state,"Window replaced after snapshot; finale paused");return;}
   world.getChunkManager().save(true);
   state.finalPhase=2;state.finalSoundAt=world.getTime()+35;save(server,state);
   // Two powerful, spatially accurate breaks at the upper windows.
   world.playSound(null,SOUND_ORIGIN,SoundEvents.BLOCK_GLASS_BREAK,SoundCategory.BLOCKS,8f,.80f);
   world.playSound(null,SOUND_ORIGIN.up(),SoundEvents.BLOCK_GLASS_BREAK,SoundCategory.BLOCKS,7f,1.13f);
   for(var change:state.journal.changes)if(change.group.equals("glass")){
    BlockPos pos=change.pos();
    world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,change.before.state()),
     pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,35,.35,.45,.25,.12);
   }
   return;
  }
  if(state.finalPhase==2&&world.getTime()>=state.finalSoundAt){
   state.finalPhase=3;next(server,state,11);
   speak(server,LakeDialogueLines.GLASS_FINALE);clear(server);
  }
 }
 private static void tick(MinecraftServer server,State state){
  if(!state.enabled||state.loadError!=null)return;
  ServerWorld world=server.getWorld(OVERWORLD);
  if(world==null)return;
  if(state.resetPending){finishReset(server,state,world);return;}
  if(state.recovering){
   for(var change:state.journal.changes)if(!world.isChunkLoaded(change.pos()))return;
   if((!state.decayApplied&&!state.journal.apply(world,"decay"))||(state.finalPhase==1&&!state.journal.apply(world,"glass")))
    conflict(server,state,"Journal recovery conflict: preserving changed blocks");
   state.recovering=false;
  }
  prepareLocal(server,state,world);
  installReserve(server,state,world);
  if(state.stage>=9)ageHouse(server,state,world);
  if(state.stage==10){
   if(world.getTime()>=state.finalSoundAt&&state.decayApplied&&world.getPlayers().stream().anyMatch(p->
     p.getX()>8407&&p.getX()<8417&&p.getZ()>8383&&p.getZ()<8393&&p.getY()>71)){
    playFinalSound(server,state,world);
   }
   return;
  }
  for(ServerPlayerEntity player:world.getPlayers()){
   if(state.stage==0){
    String id=player.getUuidAsString();boolean inside=inStart(player);
    if(state.rearmAfterExit&&!state.waitingExit.contains(id)&&inside){state.waitingExit.add(id);save(server,state);}
    if(!inside){
     state.inside.remove(id);
     if(state.waitingExit.remove(id)){state.rearmAfterExit=false;save(server,state);}
     continue;
    }
    boolean entered=state.inside.add(id);
    if(entered&&!state.waitingExit.contains(id)){
     next(server,state,1);speak(server,LakeDialogueLines.ARRIVAL);return;
    }
   }
   if(state.stage==1&&at(player,DOOR)&&lookingAt(player,DOOR,true)){
    // ElectricDoors or another mod can consume a locked-door use packet.
    // Proximity is the reliable trigger: an impossible successful opening
    // is never required to continue the chapter.
    enterLockedDoor(server,state);return;
   }
   if(state.stage==2&&close(player,HOME_GEN,4)){
    next(server,state,3);speak(server,LakeDialogueLines.FOUND_GENERATOR);return;
   }
   if(LakeStoryPolicy.afterPanel(state.stage,state.homeAttempted)==5&&at(player,HOME_PANEL)&&lookingAt(player,HOME_PANEL,false)){
    used(player,world,HOME_PANEL);return;
   }
   if(state.stage==5&&close(player,MILL_ENTRY,4)){
    next(server,state,6);
    world.playSound(null,new BlockPos(8378,73,8420),PowerDevices.OLD_CREAK,SoundCategory.BLOCKS,.8f,.83f);
    speak(server,LakeDialogueLines.SAWMILL_ARRIVAL);goal(server,"Восстановить электроснабжение");return;
   }
   if(state.stage==6&&LakeStoryPolicy.inBrickRoom(player.getX(),player.getY(),player.getZ())){
    // The player's feet must be inside the mapped brick room; no panel click needed.
    next(server,state,8);speak(server,LakeDialogueLines.BRICK_ROOM);return;
   }
   if(state.stage==8){
    DeviceEntity gen=device(world,RESERVE);
    if(gen!=null&&gen.active()&&GridManager.lakeReserveBound(server)&&GridManager.networkPowered(server,"circuit_3")){
     next(server,state,9);ageHouse(server,state,world);
     speak(server,LakeDialogueLines.RESERVE_STARTED);goal(server,"Вернуться к дому");return;
    }
   }
   if(state.stage==9&&close(player,DOOR,4)&&player.getBlockPos().getZ()>DOOR.getZ()){
    // Only progress after the player is inside the cabin, not from a nearby yard.
    if(player.getX()>8407&&player.getX()<8417&&player.getZ()>8383&&player.getZ()<8393&&player.getY()>71&&player.getY()<76){
     state.finalSoundAt=world.getTime()+150;next(server,state,10);
     speak(server,LakeDialogueLines.DECAYED_HOUSE);
     clear(server);return;
    }
   }
  }
 }
 private static void enterLockedDoor(MinecraftServer server,State state){
  if(state.stage!=1)return;
  next(server,state,2);
  speak(server,LakeDialogueLines.LOCKED_DOOR);
  goal(server,"Найти генератор");
 }
 private static boolean lookingAt(ServerPlayerEntity player,BlockPos pos,boolean door){
  var hit=player.raycast(3,0,false);
  return hit instanceof net.minecraft.util.hit.BlockHitResult block&&
   (block.getBlockPos().equals(pos)||(door&&block.getBlockPos().equals(pos.up())));
 }
 private static void used(ServerPlayerEntity player,ServerWorld world,BlockPos pos){
  MinecraftServer server=player.getServer();State state=STATES.get(server);
  if(state==null||!state.enabled||state.loadError!=null||state.resetPending||player.isSpectator()||!world.getRegistryKey().equals(OVERWORLD))return;
  if(state.stage==1&&sameDoor(pos)&&at(player,DOOR)){
   enterLockedDoor(server,state);return;
  }
  if(LakeStoryPolicy.afterPanel(state.stage,state.homeAttempted)==5&&pos.equals(HOME_PANEL)&&at(player,HOME_PANEL)){
   next(server,state,5);speak(server,LakeDialogueLines.TO_SAWMILL);goal(server,"Добраться до лесопилки");return;
  }
 }
 private static final List<BlockPos> SCENE_COBWEBS=List.of(new BlockPos(8416,75,8384),new BlockPos(8416,75,8391));
 private static void ensureChunk(ServerWorld world,BlockPos pos){world.getChunk(pos.getX()>>4,pos.getZ()>>4);}
 private static void finishReset(MinecraftServer server,State state,ServerWorld world){
  // Tick recovery waits for chunks; explicit operator reset loads this bounded set.
  for(BlockPos pos:List.of(HOME_GEN,HOME_PANEL,RESERVE,MILL_GEN))if(!world.isChunkLoaded(pos))return;
  for(var change:state.journal.changes)if(!world.isChunkLoaded(change.pos()))return;
  int conflicts=state.journal.restore(world);
  for(BlockPos pos:List.of(RESERVE,HOME_GEN,MILL_GEN)){
   DeviceEntity gen=device(world,pos);if(gen!=null&&gen.kind()==3){gen.emergencyStop();if(pos.equals(HOME_GEN))gen.fuel=0;gen.sync();}
  }
  state.stage=0;state.localPrepared=true;state.millActivated=false;state.millSnapshotValid=false;
  state.decayApplied=false;state.decayPrepared=false;state.finalSoundAt=0;state.finalPhase=0;
  state.homeFueled=false;state.homeAttempted=false;state.homePulls=0;
  state.waitingExit.clear();state.inside.clear();state.rearmAfterExit=false;
  for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())if(inStart(p)){state.waitingExit.add(p.getUuidAsString());state.inside.add(p.getUuidAsString());}
  world.getChunkManager().save(true);
  state.journal.changes.clear();state.resetPending=false;state.recovering=false;
  state.conflict=conflicts==0?"":"Reset preserved "+conflicts+" player replacements";
  save(server,state);GridManager.refresh(server);
  for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())ServerPlayNetworking.send(p,new LakeStoryPackets.Story(LakeStoryPackets.RESET,""));
 }
 private static int reset(net.minecraft.server.command.ServerCommandSource source){
  MinecraftServer server=source.getServer();State state=STATES.get(server);
  if(state==null||state.loadError!=null||!state.enabled){source.sendError(Text.literal("Сцена не установлена, отключена или её конфигурация повреждена"));return 0;}
  ServerWorld world=server.getWorld(OVERWORLD);if(world==null)return 0;
  state.resetPending=true;save(server,state);
  for(BlockPos pos:List.of(HOME_GEN,HOME_PANEL,RESERVE,MILL_GEN))ensureChunk(world,pos);
  for(var change:state.journal.changes)ensureChunk(world,change.pos());
  finishReset(server,state,world);
  source.sendFeedback(()->Text.literal("[Whiteout] Сцена сброшена. Выйди из зоны 8381 72 8366 ±3 и войди снова. "+state.conflict),false);return 1;
 }
 private static String status(MinecraftServer server,State state){
  if(state==null)return "не установлена";
  ServerWorld world=server.getWorld(OVERWORLD);
  DeviceEntity home=world==null?null:device(world,HOME_GEN),reserve=world==null?null:device(world,RESERVE);
  return "stage="+state.stage+", waitingExit="+state.waitingExit+", resetPending="+state.resetPending+
   ", fueled="+state.homeFueled+", homeFuel="+(home==null?"unloaded":home.fuel)+", pulls="+state.homePulls+
   ", attempted="+state.homeAttempted+", reserveActive="+(reserve==null?"unloaded":reserve.active())+
   ", finalPhase="+state.finalPhase+", finalLine="+(state.finalPhase==3)+", windows="+
   WINDOWS.stream().map(pos->world==null||!world.isChunkLoaded(pos)?"unloaded":world.getBlockState(pos).toString()).toList()+
   ", objective="+activeGoal(state.stage)+", conflict="+state.conflict+", error="+state.loadError;
 }
 public static void register(){
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->
   dispatcher.register(literal("whiteout")
    .then(literal("lake").requires(source->source.hasPermissionLevel(2))
     .then(literal("reset").executes(context->reset(context.getSource())))
     .then(literal("status").executes(context->{
      State state=STATES.get(context.getSource().getServer());
      context.getSource().sendFeedback(()->Text.literal("[Whiteout Lake] "+status(context.getSource().getServer(),state)),false);
      return 1;
     })))));
  LakeStoryPackets.register();
  ServerLifecycleEvents.SERVER_STARTED.register(server->{State state=load(server);if(state!=null)STATES.put(server,state);});
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{State state=STATES.remove(server);if(state!=null)save(server,state);});
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
   State state=STATES.get(server);
   if(state!=null&&state.enabled){
    String current=activeGoal(state.stage);
    if(!current.isEmpty())ServerPlayNetworking.send(handler.getPlayer(),new LakeStoryPackets.Story(LakeStoryPackets.OBJECTIVE,current));
   }
  });
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if(blocked(world,hit.getBlockPos()))return ActionResult.SUCCESS;
   if(!world.isClient&&hand==Hand.MAIN_HAND&&player instanceof ServerPlayerEntity serverPlayer&&world instanceof ServerWorld serverWorld){
    used(serverPlayer,serverWorld,hit.getBlockPos());
   }
   return ActionResult.PASS;
  });
  ServerTickEvents.END_SERVER_TICK.register(server->{
   if(++globalTicks%5!=0)return;
   State state=STATES.get(server);if(state!=null)try{tick(server,state);}catch(RuntimeException ex){
    state.loadError=ex.toString();System.err.println("[Whiteout Lake] Scene paused safely: "+ex);
   }
  });
 }
}
