package dev.whiteout.power;

import dev.whiteout.ObjectiveRules;
import dev.whiteout.Store;
import java.util.*;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import static dev.whiteout.power.WhiteoutWorldState.*;

/** Short-lived adapter; delegates all device/weather availability to the existing facade. */
public final class StoryWorldSource implements StoryWorldConditions.Source {
 private final ServerPlayerEntity player;
 private final Store.Data data;
 public StoryWorldSource(ServerPlayerEntity player,Store.Data data){this.player=player;this.data=data;}
 private static OptionalDouble bool(boolean value){return OptionalDouble.of(value?1:0);}
 public static <T> OptionalDouble observed(Reading<T> reading,ToDoubleFunction<T> number){
  if(reading==null||reading.availability()!=Availability.AVAILABLE||reading.value().isEmpty())return OptionalDouble.empty();
  double result=number.applyAsDouble(reading.value().get());return Double.isFinite(result)?OptionalDouble.of(result):OptionalDouble.empty();
 }
 public OptionalDouble sample(StoryWorldConditions.Node node){
  var server=player.getServer();if(server==null||!server.isOnThread())return OptionalDouble.empty();
  var state=WhiteoutWorldState.of(server);
  switch(node.metric()){
   case PLAYER_COUNT:return OptionalDouble.of(server.getPlayerManager().getPlayerList().stream().filter(p->p.isAlive()&&!p.isSpectator()&&p.getServerWorld()==player.getServerWorld()).count());
   case TIME_OF_DAY:return OptionalDouble.of(Math.floorMod(player.getServerWorld().getTimeOfDay(),24000));
   case EVENT_COMPLETED:return bool(data.completedEvents.contains(node.id()));
   case OBJECTIVE_ACTIVE,OBJECTIVE_COMPLETED,OBJECTIVE_FAILED:{
    var o=data.objectives.get(node.id());if(o==null)return OptionalDouble.empty();
    var status=switch(node.metric()){case OBJECTIVE_ACTIVE->ObjectiveRules.Status.ACTIVE;case OBJECTIVE_COMPLETED->ObjectiveRules.Status.COMPLETED;default->ObjectiveRules.Status.FAILED;};return bool(o.status==status);
   }
   case NETWORK_POWERED:return observed(state.network(node.id()),v->v.powered()?1:0);
   case BLIZZARD,WIND_STRENGTH,SNOWFALL,ZONE_ACTIVE:return observed(state.weather(player.getServerWorld(),BlockPos.ofFloored(player.getEyePos())),v->switch(node.metric()){
    case BLIZZARD->v.blizzard()?1:0;case WIND_STRENGTH->v.windStrength();case SNOWFALL->v.snowfall();
    default->v.weather().map(z->z.id().equals(node.id())).orElse(false)||v.shelter().map(z->z.id().equals(node.id())).orElse(false)?1:0;
   });
   case SHELTER_PROTECTION:return observed(state.shelter(player),ShelterStateView::protection);
   case COLD_LEVEL:{
    var shelter=state.shelter(player);
    if(shelter.availability()!=Availability.AVAILABLE||shelter.value().isEmpty())return OptionalDouble.empty();
    var v=shelter.value().get();return v.accumulatedColdAvailability()==Availability.AVAILABLE?v.accumulatedCold():OptionalDouble.empty();
   }
   default:break;
  }
  var address=node.address();if(address==null)return OptionalDouble.empty();
  var id=Identifier.tryParse(address.dimension());if(id==null)return OptionalDouble.empty();
  var world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id));if(world==null)return OptionalDouble.empty();
  var pos=new BlockPos(address.x(),address.y(),address.z());
  return switch(node.metric()){
   case GENERATOR_RUNNING->observed(state.generator(world,pos),v->v.running()?1:0);
   case GENERATOR_FUEL->observed(state.generator(world,pos),GeneratorStateView::fuelTicks);
   case FUSE_INSERTED->observed(state.fuse(world,pos),v->v.inserted()?1:0);
   case FUSE_BURNT->observed(state.fuse(world,pos),v->v.burnt()?1:0);
   default->OptionalDouble.empty();
  };
 }
}
