package dev.whiteout.power;

import dev.whiteout.Store;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import static dev.whiteout.power.WinterCrisisConditions.*;

/** Called only at activation, on the authoritative server thread. No tick scanning. */
public final class DialogueRouteConditions {
 private DialogueRouteConditions(){}
 public static Truth sample(ServerPlayerEntity player,DialogueRoute.Condition condition){
  var server=player.getServer();var state=WhiteoutWorldState.of(server);Truth result;
  switch(condition.type()){
   case NETWORK->result=observed(state.network(condition.target()),n->n.powered());
   case GENERATOR->{
    var id=Identifier.tryParse(condition.dimension());var world=id==null?null:server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id));
    result=world==null?Truth.UNKNOWN:observed(state.generator(world,new BlockPos(condition.x(),condition.y(),condition.z())),g->g.running());
   }
   case WEATHER_ZONE,BLIZZARD->{
    if(!player.getServerWorld().isChunkLoaded(player.getBlockPos()))return Truth.UNKNOWN;
    if(condition.type()==DialogueRoute.Type.WEATHER_ZONE&&Store.get(server).data.zones.stream().noneMatch(z->z!=null&&condition.target().equals(z.id)))return Truth.UNKNOWN;
    result=observed(state.weather(player.getServerWorld(),player.getBlockPos()),w->condition.type()==DialogueRoute.Type.BLIZZARD?
     w.blizzard():w.weather().map(z->z.id().equals(condition.target())).orElse(false));
   }
   case STORY->{
    var store=Store.get(server);
    if(store.error!=null)return Truth.UNKNOWN;
    result=condition.storyTruth(store.data.storyVariables);
   }
   default->result=Truth.UNKNOWN;
  }
  return condition.expected()?result:result.not();
 }
}
