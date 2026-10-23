package dev.whiteout.power;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Saved in the same atomic JSON as progress. Only BE-free, exact-state changes are owned. */
final class LakeBlockJournal {
 static final class Snapshot {
  String block;
  Map<String,String> properties=new LinkedHashMap<>();
  static Snapshot of(BlockState state){
   Snapshot out=new Snapshot();out.block=Registries.BLOCK.getId(state.getBlock()).toString();
   for(var e:state.getEntries().entrySet())out.properties.put(e.getKey().getName(),name(e.getKey(),e.getValue()));
   return out;
  }
  @SuppressWarnings({"unchecked","rawtypes"}) private static String name(Property key,Comparable value){return key.name(value);}
  BlockState state(){
   Identifier id=Identifier.of(block);
   if(!Registries.BLOCK.containsId(id))throw new IllegalStateException("Missing journal block: "+block);
   BlockState state=Registries.BLOCK.get(id).getDefaultState();
   for(var e:properties.entrySet()){
    Property<?> property=state.getBlock().getStateManager().getProperty(e.getKey());
    if(property==null)throw new IllegalStateException("Missing journal property: "+e.getKey());
    state=with(state,property,e.getValue());
   }
   return state;
  }
  private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> p,String value){
   return state.with(p,p.parse(value).orElseThrow(()->new IllegalStateException("Invalid journal state")));
  }
 }
 static final class Change {
  int x,y,z;String group;Snapshot before,after;
  BlockPos pos(){return new BlockPos(x,y,z);}
 }
 List<Change> changes=new ArrayList<>();
 void add(ServerWorld world,BlockPos pos,BlockState after,String group){
  if(!world.isChunkLoaded(pos)||world.getBlockEntity(pos)!=null)throw new IllegalStateException("Unsafe journal target "+pos);
  if(changes.stream().anyMatch(c->c.pos().equals(pos)))return;
  Change c=new Change();c.x=pos.getX();c.y=pos.getY();c.z=pos.getZ();c.group=group;
  c.before=Snapshot.of(world.getBlockState(pos));c.after=Snapshot.of(after);changes.add(c);
 }
 boolean loaded(ServerWorld world,String group){return changes.stream().filter(c->c.group.equals(group)).allMatch(c->world.isChunkLoaded(c.pos()));}
 boolean apply(ServerWorld world,String group){
  List<Change> selected=changes.stream().filter(c->c.group.equals(group)).toList();
  for(Change c:selected){
   if(!world.isChunkLoaded(c.pos())||world.getBlockEntity(c.pos())!=null)return false;
   BlockState state=world.getBlockState(c.pos());
   if(!state.equals(c.before.state())&&!state.equals(c.after.state()))return false;
  }
  for(Change c:selected)if(world.getBlockState(c.pos()).equals(c.before.state()))world.setBlockState(c.pos(),c.after.state(),Block.NOTIFY_ALL);
  return true;
 }
 int restore(ServerWorld world){
  int conflicts=0;
  for(Change c:changes){
   BlockState state=world.getBlockState(c.pos());
   if(world.getBlockEntity(c.pos())!=null){conflicts++;continue;}
   if(LakeStoryPolicy.shouldRestore(state.equals(c.before.state()),state.equals(c.after.state()),false))world.setBlockState(c.pos(),c.before.state(),Block.NOTIFY_ALL);
   else if(!state.equals(c.before.state()))conflicts++;
  }
  return conflicts;
 }
}
