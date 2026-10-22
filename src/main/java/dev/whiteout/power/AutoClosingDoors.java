package dev.whiteout.power;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Server-authoritative closer for ordinary doors. No global block-update hooks.
 * New openings are discovered from player use and a bounded scan around players,
 * also covering NPC/redstone opens and doors left open across server restarts. */
public final class AutoClosingDoors {
 private AutoClosingDoors(){}
 private record Tracked(Block block,long due){}
 private static final Map<ServerWorld,Map<BlockPos,Tracked>> OPEN=new WeakHashMap<>();
 private static final Map<ServerWorld,Set<BlockPos>> PENDING=new WeakHashMap<>();
 private static final Map<ServerWorld,Integer> SCAN_TICKS=new WeakHashMap<>();
 private static final int SCAN_INTERVAL=20;
 private static final int HORIZONTAL_RADIUS=9;
 private static final int VERTICAL_RADIUS=5;

 private static BlockPos lower(ServerWorld world, BlockPos pos){
  BlockState state=world.getBlockState(pos);
  if(state.getBlock() instanceof DoorBlock && state.contains(Properties.DOUBLE_BLOCK_HALF)
      && state.get(Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER)return pos.down();
  return pos;
 }
 private static boolean open(BlockState state){
  return state.getBlock() instanceof DoorBlock && state.contains(DoorBlock.OPEN)
    && state.get(DoorBlock.OPEN);
 }
 private static void discover(ServerWorld world, BlockPos at){
  BlockPos lower=lower(world,at);
  if(!world.isChunkLoaded(lower)||ElectricDoors.isManaged(world,lower))return;
  BlockState state=world.getBlockState(lower);
  if(!open(state))return;
  OPEN.computeIfAbsent(world,w->new HashMap<>())
   .computeIfAbsent(lower.toImmutable(),p->new Tracked(state.getBlock(),world.getTime()+AutoDoorPolicy.DELAY_TICKS));
 }
 private static void discoverAroundPlayers(ServerWorld world){
  // Scan no unloaded chunks, and only a bounded neighbourhood every second.
  BlockPos.Mutable at=new BlockPos.Mutable();
  Set<BlockPos> scanned=new HashSet<>();
  for(var player:world.getPlayers()){
   BlockPos center=player.getBlockPos();
   int minY=Math.max(world.getBottomY(),center.getY()-VERTICAL_RADIUS);
   int maxY=Math.min((world.getTopY() - 1),center.getY()+VERTICAL_RADIUS);
   for(int x=center.getX()-HORIZONTAL_RADIUS;x<=center.getX()+HORIZONTAL_RADIUS;x++)
    for(int z=center.getZ()-HORIZONTAL_RADIUS;z<=center.getZ()+HORIZONTAL_RADIUS;z++){
     at.set(x,center.getY(),z);
     if(!world.isChunkLoaded(at))continue;
     for(int y=minY;y<=maxY;y++){
      at.set(x,y,z);
      BlockState s=world.getBlockState(at);
      if(!(s.getBlock() instanceof DoorBlock)||!s.contains(DoorBlock.OPEN)||!s.get(DoorBlock.OPEN))continue;
      BlockPos bottom=lower(world,at);
      if(scanned.add(bottom.toImmutable()))discover(world,bottom);
     }
    }
  }
 }
 private static boolean occupied(ServerWorld world, BlockPos pos){
  // Include both door halves, with a margin so an NPC is not clipped by the closing panel.
  Box passage=new Box(pos).stretch(0,1,0).expand(.16,.05,.16);
  return !world.getOtherEntities(null,passage,e->e instanceof LivingEntity && e.isAlive()).isEmpty();
 }
 private static boolean powered(ServerWorld world, BlockPos pos){
  return world.isReceivingRedstonePower(pos)||world.isReceivingRedstonePower(pos.up());
 }
 private static void close(ServerWorld world,BlockPos pos,BlockState state){
  // Vanilla API also plays the correct wood/iron/copper door sound and updates
  // the other half. Avoid custom blockstate changes that could miss neighbours.
  ((DoorBlock)state.getBlock()).setOpen(null,world,state,pos,false);
 }
 private static void tick(ServerWorld world){
  Set<BlockPos> queued=PENDING.get(world);
  if(queued!=null){
   for(BlockPos at:queued){
    // The player opened a previously closed door again. Reset its old deadline
    // even if a close+reopen happened between two server ticks.
    Map<BlockPos,Tracked> tracked=OPEN.get(world);
    if(tracked!=null)tracked.remove(at);
    discover(world,at);
   }
   queued.clear();
  }
  int elapsed=SCAN_TICKS.merge(world,1,Integer::sum);
  if(elapsed>=SCAN_INTERVAL){SCAN_TICKS.put(world,0);discoverAroundPlayers(world);}
  Map<BlockPos,Tracked> tracked=OPEN.get(world);
  if(tracked==null||tracked.isEmpty())return;
  long now=world.getTime();
  Iterator<Map.Entry<BlockPos,Tracked>> it=tracked.entrySet().iterator();
  while(it.hasNext()){
   var entry=it.next();BlockPos pos=entry.getKey();Tracked value=entry.getValue();
   if(!world.isChunkLoaded(pos)){it.remove();continue;}
   BlockState state=world.getBlockState(pos);
   if(state.getBlock()!=value.block()||!open(state)||ElectricDoors.isManaged(world,pos)){it.remove();continue;}
   if(now<value.due())continue;
   boolean redstone=powered(world,pos),blocked=occupied(world,pos);
   if(AutoDoorPolicy.shouldClose(true,false,redstone,blocked,true,true)){
    close(world,pos,state);it.remove();
   }
   // When obstructed or redstone-powered, try again without resetting the timer.
  }
 }
 public static void register(){
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if(!(world instanceof ServerWorld sw))return ActionResult.PASS;
   BlockPos pos=lower(sw,hit.getBlockPos());BlockState state=sw.getBlockState(pos);
   if(state.getBlock() instanceof DoorBlock&&!open(state)&&!ElectricDoors.isManaged(sw,pos))
    PENDING.computeIfAbsent(sw,w->new HashSet<>()).add(pos.toImmutable());
   return ActionResult.PASS;
  });
  ServerTickEvents.END_WORLD_TICK.register(AutoClosingDoors::tick);
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{OPEN.clear();PENDING.clear();SCAN_TICKS.clear();});
 }
}
