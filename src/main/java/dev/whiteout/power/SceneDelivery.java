package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.power.lobby.LobbyState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public final class SceneDelivery{
 private SceneDelivery(){}
 public record Move(String dimension,double x,double y,double z){
  public Move{if(dimension==null||Identifier.tryParse(dimension)==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29999984||Math.abs(z)>29999984||y< -2048||y>2048)throw new IllegalArgumentException("scene destination");}
 }
 public record Action(Narrative.Role recipient,String item,int count,Move move){
  public Action{recipient=Objects.requireNonNullElse(recipient,Narrative.Role.ANY);if(item==null?count!=0||move==null:Identifier.tryParse(item)==null||count<1||count>64)throw new IllegalArgumentException("scene delivery");}
 }
 public record Grant(UUID player,String receipt,Action action){}
 public static final class Ledger extends ArrayList<Grant>{}
 public interface Access{Ledger whiteout$grants();void whiteout$grants(Ledger ledger);}
 static Ledger prepare(MinecraftServer s,Store store,String key,List<Action> actions){
  var access=(Access)(Object)store.data;var result=new Ledger();result.addAll(access.whiteout$grants());
  var d=LobbyState.get(store);if(actions.isEmpty())return result;
  if(d==null||!d.started()||actions.size()>8)throw new IllegalArgumentException("scene participants");
  for(int i=0;i<actions.size();i++){
   var action=actions.get(i);if(action.item!=null){var item=Registries.ITEM.getOrEmpty(Identifier.of(action.item)).orElseThrow();if(item.getDefaultStack().isEmpty()||item.getMaxCount()<action.count)throw new IllegalArgumentException("item/count");}
   if(action.move!=null){var m=action.move;var world=s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(m.dimension)));var pos=BlockPos.ofFloored(m.x,m.y,m.z);if(world==null||world.isOutOfHeightLimit(pos)||!world.getWorldBorder().contains(pos))throw new IllegalArgumentException("destination");}
   for(var who:new UUID[]{d.first(),d.second()}){if(who==null||action.recipient==Narrative.Role.FIRST&&!who.equals(d.first())||action.recipient==Narrative.Role.SECOND&&!who.equals(d.second()))continue;
    String receipt="whiteout.grant."+UUID.nameUUIDFromBytes((key+"/"+i+"/"+who).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if(result.stream().noneMatch(g->g.receipt.equals(receipt)))result.add(new Grant(who,receipt,action));
   }
  }
  if(result.size()>256)throw new IllegalArgumentException("delivery ledger full");return result;
 }
 static void register(){ServerTickEvents.END_SERVER_TICK.register(s->{
  if(s.getTicks()%20!=0||s.getCurrentPlayerCount()==0)return;var store=Store.get(s);if(store.error!=null)return;
  boolean changed=false;for(var grant:((Access)(Object)store.data).whiteout$grants()){
   var p=s.getPlayerManager().getPlayer(grant.player);if(p==null||!p.isAlive()||p.isSpectator()||p.getCommandTags().contains(grant.receipt))continue;
   var a=grant.action;int slot=a.item==null?-1:p.getInventory().getEmptySlot();if(a.item!=null&&slot<0)continue;
   var item=a.item==null?null:Registries.ITEM.getOrEmpty(Identifier.of(a.item)).orElse(null);if(a.item!=null&&item==null)continue;
   var m=a.move;var dest=m==null?null:s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(m.dimension)));if(m!=null&&dest==null)continue;
   if(!p.addCommandTag(grant.receipt))continue;
   if(m!=null)p.teleport(dest,m.x,m.y,m.z,p.getYaw(),p.getPitch());
   if(item!=null)p.getInventory().setStack(slot,new ItemStack(item,a.count));changed=true;
  }
  if(changed)s.getPlayerManager().saveAllPlayerData();
 });}
}
