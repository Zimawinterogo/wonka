package dev.whiteout.power;

import java.util.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import static dev.whiteout.power.WinterCrisisConditions.*;
import static dev.whiteout.power.WhiteoutWorldState.*;

/** Per-cycle server adapter. Do not retain beyond the cycle; stores no players/worlds. */
public final class CrisisWorldSource implements Source {
 private final MinecraftServer server;
 private final Map<String,Object> readings=new HashMap<>();
 public CrisisWorldSource(MinecraftServer server){this.server=Objects.requireNonNull(server);}
 @SuppressWarnings("unchecked") private <T> T once(String key,java.util.function.Supplier<T> load){
  return (T)readings.computeIfAbsent(key,k->load.get());
 }
 public Truth sample(Signal signal){
  if(!server.isOnThread()||server.isStopped())return Truth.UNKNOWN;
  WhiteoutWorldState state=WhiteoutWorldState.of(server);
  if(signal.type()==Type.NETWORK_OUTAGE){
   Reading<PowerNetworkStateView> n=once("network:"+signal.id(),()->state.network(signal.id()));
   return observed(n,v->!v.powered());
  }
  if(signal.player()!=null){
   var player=server.getPlayerManager().getPlayer(signal.player());if(player==null)return Truth.UNKNOWN;
   String dim=player.getServerWorld().getRegistryKey().getValue().toString();
   if(signal.address()!=null&&!signal.address().dimension().equals(dim))return Truth.UNKNOWN;
   if(signal.type()==Type.OUTSIDE_REGION){
    Region region=signal.region();if(!region.dimension().equals(dim))return Truth.UNKNOWN;
    return Truth.of(!region.contains(player.getX(),player.getY(),player.getZ()));
   }
   if(signal.type()==Type.OUTSIDE_SHELTER){
    Reading<ShelterStateView> shelter=once("shelter:"+signal.player(),()->state.shelter(player));
    return observed(shelter,v->v.protection()<signal.threshold());
   }
   Reading<WeatherStateView> weather=once("weather:"+signal.player(),()->state.weather(player.getServerWorld(),BlockPos.ofFloored(player.getEyePos())));
   return switch(signal.type()){
    case BLIZZARD->observed(weather,v->v.blizzard()&&v.windStrength()>=signal.threshold());
    case ZONE_ACTIVE->observed(weather,v->v.weather().map(z->z.id().equals(signal.id())).orElse(false)||v.shelter().map(z->z.id().equals(signal.id())).orElse(false));
    default->Truth.UNKNOWN;
   };
  }
  Address a=signal.address();if(a==null)return Truth.UNKNOWN;
  Identifier id=Identifier.tryParse(a.dimension());if(id==null)return Truth.UNKNOWN;
  ServerWorld world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id));if(world==null)return Truth.UNKNOWN;
  BlockPos pos=new BlockPos(a.x(),a.y(),a.z());
  return switch(signal.type()){
   case GENERATOR_OFF->observed(once("generator:"+a,()->state.generator(world,pos)),v->!v.running());
   case FUSE_BURNT->observed(once("fuse:"+a,()->state.fuse(world,pos)),FuseStateView::burnt);
   case LAMP_EMPTY->observed(once("lamp:"+a,()->state.emergencyLight(world,pos)),v->v.linked()&&v.chargeTicks()==0);
   case DOOR_LOCKED->{
    Reading<DoorStateView> door=once("door:"+a,()->state.door(world,pos));
    if(door.availability()!=Availability.AVAILABLE||door.value().isEmpty())yield Truth.UNKNOWN;
    yield observed(door.value().get().electricallyLocked(),Boolean::booleanValue);
   }
   default->Truth.UNKNOWN;
  };
 }
}
