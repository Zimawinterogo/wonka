package dev.whiteout.power.bolt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Door bolts: data lives in a world-side JSON file (same approach as ElectricDoors), keyed by the
 * LOWER door block. No block is added, so vanilla/modded doors keep their own behaviour.
 * The only global hook is UseBlockCallback, which returns PASS immediately unless the clicked block
 * is a DoorBlock that either has a bolt record or is clicked with the bolt item.
 */
public final class DoorBolts implements ModInitializer {
 public record Bolt(boolean inner,boolean locked){}
 public static final Item DOOR_BOLT=Registry.register(Registries.ITEM,Identifier.of("whiteout","door_bolt"),new Item(new Item.Settings().maxCount(16)));
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static final double REACH=5.5;
 private static final Map<MinecraftServer,Store> STORES=new WeakHashMap<>();
 private static final Map<UUID,Integer> LAST_SENT=new HashMap<>();
 private static int ticks;

 private static final class Store{
  final Map<String,Map<Long,Bolt>> dims=new HashMap<>();
  boolean invalid,dirty;
  Map<Long,Bolt> of(String dim){return dims.computeIfAbsent(dim,k->new HashMap<>());}
 }
 private static final class Row{String d;int x,y,z;boolean inner,locked;}
 private static final class FileData{List<Row> bolts=new ArrayList<>();}

 private static Path file(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("whiteout-door-bolts.json");}
 private static Store store(MinecraftServer server){
  return STORES.computeIfAbsent(server,s->{
   Store st=new Store();
   try{
    if(Files.isRegularFile(file(s))){
     FileData d=GSON.fromJson(Files.readString(file(s)),FileData.class);
     if(d!=null&&d.bolts!=null)for(Row r:d.bolts){
      if(r==null||r.d==null)throw new IllegalArgumentException("bad row");
      st.of(r.d).put(BlockPos.asLong(r.x,r.y,r.z),new Bolt(r.inner,r.locked));
     }
    }
   }catch(Exception ex){
    // Keep the original file untouched for manual recovery; bolts stay disabled this session.
    System.err.println("[Whiteout] Door bolts disabled, file preserved: "+ex);st.invalid=true;st.dims.clear();
   }
   return st;
  });
 }
 private static void flush(MinecraftServer server){
  Store st=store(server);if(st.invalid||!st.dirty)return;
  FileData d=new FileData();
  st.dims.forEach((dim,m)->m.forEach((k,b)->{
   BlockPos p=BlockPos.fromLong(k);Row r=new Row();r.d=dim;r.x=p.getX();r.y=p.getY();r.z=p.getZ();r.inner=b.inner();r.locked=b.locked();d.bolts.add(r);
  }));
  try{dev.whiteout.power.GridFileIO.writeAtomic(file(server),GSON.toJson(d));st.dirty=false;}
  catch(Exception ex){System.err.println("[Whiteout] Could not save door bolts (will retry): "+ex);}
 }
 private static String dim(World w){return w.getRegistryKey().getValue().toString();}

 /** Authoritative on server, synced copy on client. {@code low} must be the lower door block. */
 static Bolt lookup(World world,BlockPos low){
  if(world.isClient){
   if(!dim(world).equals(ClientCache.dimension))return null;
   ClientCache.Entry e=ClientCache.MAP.get(low.asLong());return e==null?null:new Bolt(e.inner(),e.locked());
  }
  if(!(world instanceof ServerWorld sw))return null;
  Store st=store(sw.getServer());if(st.invalid)return null;
  Map<Long,Bolt> m=st.dims.get(dim(world));return m==null?null:m.get(low.asLong());
 }
 private static boolean suitable(BlockState s){
  return s.getBlock() instanceof DoorBlock&&!s.isOf(Blocks.IRON_DOOR)&&s.contains(Properties.DOOR_HINGE)
   &&s.contains(Properties.DOUBLE_BLOCK_HALF)&&s.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER;
 }
 private static void put(ServerWorld w,BlockPos low,Bolt b){
  Store st=store(w.getServer());if(st.invalid)return;
  st.of(dim(w)).put(low.asLong(),b);st.dirty=true;flush(w.getServer());syncNear(w,low);
 }
 /** Removes the record exactly once; the item is dropped only by the call that really removed it. */
 private static void detach(ServerWorld w,BlockPos low,boolean drop){
  Store st=store(w.getServer());if(st.invalid)return;
  Map<Long,Bolt> m=st.dims.get(dim(w));if(m==null||m.remove(low.asLong())==null)return;
  st.dirty=true;flush(w.getServer());syncNear(w,low);
  if(drop&&w.getGameRules().getBoolean(GameRules.DO_TILE_DROPS))Block.dropStack(w,low,new ItemStack(DOOR_BOLT));
 }

 // ---- interaction -------------------------------------------------------------------------------
 private static ActionResult onUse(PlayerEntity player,World world,Hand hand,BlockHitResult hit){
  if(player.isSpectator())return ActionResult.PASS;
  BlockPos at=hit.getBlockPos();BlockState clicked=world.getBlockState(at);
  if(!(clicked.getBlock() instanceof DoorBlock))return ActionResult.PASS; // cheap global filter
  BlockPos low=DoorBoltApi.lowerOf(world,at);
  BlockState door=low.equals(at)?clicked:world.getBlockState(low);
  if(!suitable(door))return ActionResult.PASS;
  Bolt bolt=lookup(world,low);ItemStack held=player.getStackInHand(hand);
  if(bolt==null){
   if(!held.isOf(DOOR_BOLT))return ActionResult.PASS;
   Direction f=door.get(Properties.HORIZONTAL_FACING);
   if(hit.getSide()!=f&&hit.getSide()!=f.getOpposite())return ActionResult.PASS;
   if(door.get(Properties.OPEN))return ActionResult.PASS;
   if(world.isClient)return ActionResult.SUCCESS;
   if(hand!=Hand.MAIN_HAND||!reachable(player,world,low,hit))return ActionResult.FAIL;
   put((ServerWorld)world,low,new Bolt(hit.getSide()==f,false));
   world.playSound(null,low,SoundEvents.BLOCK_CHAIN_PLACE,SoundCategory.BLOCKS,.8f,1.1f);
   if(!player.isCreative())held.decrement(1);
   return ActionResult.SUCCESS;
  }
  boolean onBolt=DoorLeaf.hits(door,low,bolt.inner(),hit.getPos(),hit.getSide());
  if(onBolt){
   if(hand!=Hand.MAIN_HAND)return ActionResult.PASS;
   if(world.isClient)return ActionResult.SUCCESS;
   if(!reachable(player,world,low,hit))return ActionResult.FAIL;
   ServerWorld sw=(ServerWorld)world;
   if(player.isSneaking()&&held.isEmpty()){
    if(bolt.locked()){msg(player,"message.whiteout.bolt_unlock_first");return ActionResult.SUCCESS;}
    detach(sw,low,!player.isCreative());
    world.playSound(null,low,SoundEvents.BLOCK_CHAIN_BREAK,SoundCategory.BLOCKS,.8f,1.0f);
    return ActionResult.SUCCESS;
   }
   if(!bolt.locked()&&door.get(Properties.OPEN)){msg(player,"message.whiteout.bolt_close_door");return ActionResult.SUCCESS;}
   put(sw,low,new Bolt(bolt.inner(),!bolt.locked()));
   world.playSound(null,low,bolt.locked()?SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN:SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,SoundCategory.BLOCKS,.7f,1.6f);
   return ActionResult.SUCCESS;
  }
  if(!bolt.locked())return ActionResult.PASS; // unlocked bolt: vanilla / other mods decide
  if(player.shouldCancelInteraction()&&!held.isEmpty())return ActionResult.PASS; // allow placing blocks against it
  if(!world.isClient){
   world.playSound(null,low,SoundEvents.BLOCK_CHAIN_HIT,SoundCategory.BLOCKS,.7f,.8f);
   msg(player,"message.whiteout.door_bolted");
  }
  return ActionResult.SUCCESS;
 }
 private static void msg(PlayerEntity p,String key){if(p instanceof ServerPlayerEntity sp)sp.sendMessage(Text.translatable(key),true);}
 /** Server-side distance, containment and line-of-sight check: the hit data comes from the client. */
 private static boolean reachable(PlayerEntity p,World w,BlockPos low,BlockHitResult hit){
  Vec3d eye=p.getEyePos(),pos=hit.getPos();
  if(eye.squaredDistanceTo(pos)>REACH*REACH)return false;
  if(!new Box(low).stretch(0,1,0).expand(.12).contains(pos))return false;
  Direction s=hit.getSide();
  Vec3d target=pos.add(s.getOffsetX()*.03,s.getOffsetY()*.03,s.getOffsetZ()*.03);
  // Must reach the face without crossing ANY block (wall, or the door leaf itself from the wrong side).
  return w.raycast(new RaycastContext(eye,target,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()==HitResult.Type.MISS;
 }

 // ---- housekeeping + sync -----------------------------------------------------------------------
 private static void sweep(MinecraftServer server){
  Store st=store(server);if(st.invalid)return;
  for(ServerWorld w:server.getWorlds()){
   Map<Long,Bolt> m=st.dims.get(dim(w));if(m==null||m.isEmpty())continue;
   for(Map.Entry<Long,Bolt> e:new ArrayList<>(m.entrySet())){
    BlockPos p=BlockPos.fromLong(e.getKey());
    if(!w.isChunkLoaded(p))continue;
    BlockState s=w.getBlockState(p);
    if(!suitable(s)){detach(w,p,true);continue;}
    // Invariant: shot bolt implies closed door (a door forced open by redstone drops the latch).
    if(e.getValue().locked()&&s.get(Properties.OPEN)){m.put(e.getKey(),new Bolt(e.getValue().inner(),false));st.dirty=true;}
   }
  }
  flush(server);
  for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())send(p);
 }
 private static void syncNear(ServerWorld w,BlockPos at){
  for(ServerPlayerEntity p:w.getPlayers())if(p.squaredDistanceTo(at.toCenterPos())<64*64)send(p);
 }
 private static void send(ServerPlayerEntity player){
  ServerWorld w=player.getServerWorld();Store st=store(w.getServer());
  StringBuilder sb=new StringBuilder();
  Map<Long,Bolt> m=st.invalid?null:st.dims.get(dim(w));
  if(m!=null)for(Map.Entry<Long,Bolt> e:m.entrySet()){
   BlockPos p=BlockPos.fromLong(e.getKey());
   if(p.getSquaredDistance(player.getPos())>48*48||sb.length()>15500)continue;
   sb.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append(',')
     .append(e.getValue().inner()?1:0).append(',').append(e.getValue().locked()?1:0).append('\n');
  }
  String text=sb.toString();int hash=Objects.hash(dim(w),text);
  Integer last=LAST_SENT.put(player.getUuid(),hash);
  if(last!=null&&last==hash)return;
  ServerPlayNetworking.send(player,new DoorBoltSync(dim(w),text));
 }

 @Override public void onInitialize(){
  PayloadTypeRegistry.playS2C().register(DoorBoltSync.ID,DoorBoltSync.CODEC);
  UseBlockCallback.EVENT.register(DoorBolts::onUse);
  PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,be)->{
   if(world instanceof ServerWorld sw&&state.getBlock() instanceof DoorBlock)
    detach(sw,state.contains(Properties.DOUBLE_BLOCK_HALF)&&state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER?pos.down():pos,!player.isCreative());
  });
  ServerTickEvents.END_SERVER_TICK.register(s->{if(++ticks%20==0)sweep(s);});
  ServerPlayConnectionEvents.JOIN.register((h,sender,server)->{LAST_SENT.remove(h.getPlayer().getUuid());send(h.getPlayer());});
  ServerPlayConnectionEvents.DISCONNECT.register((h,server)->LAST_SENT.remove(h.getPlayer().getUuid()));
  ServerLifecycleEvents.SERVER_STOPPING.register(s->{flush(s);STORES.remove(s);LAST_SENT.clear();});
  ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP,Identifier.of("whiteout","building_materials"))).register(e->e.add(DOOR_BOLT));
 }
}
