package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.Zone;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Read-only server-thread facade over existing owners. No persistence, gameplay mutations,
 * packets or tick scans. Snapshots may be retained; query again to obtain current facts.
 * AVAILABLE means observed now, CACHED identifies legacy observed facts from unloaded devices.
 * Client-smoothed weather and accumulated client cold are deliberately not simulated here.
 */
public final class WhiteoutWorldState {
 public enum Availability { AVAILABLE, CACHED, UNBOUND, MISSING, UNLOADED, UNAVAILABLE }
 public record Reading<T>(Availability availability,long tick,Optional<T> value){
  public Reading { Objects.requireNonNull(availability);Objects.requireNonNull(value); }
 }
 public record Address(String dimension,int x,int y,int z){
  public Address { Objects.requireNonNull(dimension); }
 }
 public record GeneratorStateView(int kind,int fuelTicks,boolean running){}
 public record FuseStateView(boolean inserted,boolean burnt,boolean unstable,float failureChance,boolean active){}
 public record FixtureStateView(Address address,String originalBlock){}
 public record PowerNetworkStateView(String id,boolean powered,boolean pendingFuseFailure,
   boolean pendingGeneratorStop,boolean flickering,Reading<GeneratorStateView> generator,
   Reading<FuseStateView> fuse,List<FixtureStateView> fixtures){
  public PowerNetworkStateView { fixtures=List.copyOf(fixtures); }
 }
 public record ZoneStateView(String id,String name,String dimension,boolean shelter,boolean blizzard,
   float snow,float cold,float weight){}
 public record WeatherStateView(Optional<ZoneStateView> weather,Optional<ZoneStateView> shelter,
   boolean blizzard,double snowfall,double windX,double windZ,double windStrength){}
 /** Cold is the legacy client's accumulated meter, not an estimate from ambient temperature. */
 public record ShelterStateView(Optional<ZoneStateView> zone,float opening,float protection,
   float ambientTemperature,Availability accumulatedColdAvailability,OptionalDouble accumulatedCold){}
 public record DoorStateView(boolean open,boolean redstonePowered,Optional<String> network,
   Reading<Boolean> electricallyLocked){}
 public record EmergencyLightStateView(int chargeTicks,boolean linked,boolean mainsPowered,
   boolean switchEnabled,boolean lit){}
 public record SwitchStateView(String network,boolean on,boolean all,List<String> targets){
  public SwitchStateView {targets=List.copyOf(targets);}
 }

 private static final Map<MinecraftServer,WhiteoutWorldState> INSTANCES=new IdentityHashMap<>();
 private final MinecraftServer server;
 private boolean closed;
 private final WorldStateCache<String,Reading<PowerNetworkStateView>> networks=new WorldStateCache<>(128,1);
 private record ShelterKey(String dimension,Vec3d eye){}
 private final WorldStateCache<ShelterKey,ShelterExposure.Sample> shelters=new WorldStateCache<>(128,5);
 private WhiteoutWorldState(MinecraftServer server){this.server=server;}
 public static WhiteoutWorldState of(MinecraftServer server){
  Objects.requireNonNull(server);
  if(!server.isOnThread()||server.isStopped())throw new IllegalStateException("WorldState requires a live server thread");
  return INSTANCES.computeIfAbsent(server,WhiteoutWorldState::new);
 }
 /** Called once by PowerDevices; callbacks invalidate only, never read chunks. */
 static void register(){
  ServerLifecycleEvents.SERVER_STOPPED.register(server->{
   WhiteoutWorldState view=INSTANCES.remove(server);
   if(view!=null){view.closed=true;view.networks.clear();view.shelters.clear();}
  });
  ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->invalidate(world.getServer()));
  ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->invalidate(world.getServer()));
  PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,entity)->{
   if(world instanceof ServerWorld sw)invalidate(sw.getServer());
  });
 }
 /** Existing owner hooks call this after changes. Does not instantiate a facade. */
 static void invalidate(MinecraftServer server){
  WhiteoutWorldState view=INSTANCES.get(server);
  if(view!=null){view.networks.clear();view.shelters.clear();}
 }
 static void invalidatePower(MinecraftServer server){
  WhiteoutWorldState view=INSTANCES.get(server);if(view!=null)view.networks.clear();
 }
 private long tick(){
  if(closed||!server.isOnThread())throw new IllegalStateException("WorldState is closed or off the server thread");
  return Integer.toUnsignedLong(server.getTicks());
 }
 private void world(ServerWorld world){
  tick();if(world==null||world.getServer()!=server)throw new IllegalArgumentException("World belongs to another server");
 }
 private <T> Reading<T> absent(Availability why){return new Reading<>(why,tick(),Optional.empty());}
 private <T> Reading<T> present(T value){return new Reading<>(Availability.AVAILABLE,tick(),Optional.of(value));}
 private static Address address(String dim,BlockPos pos){return new Address(dim,pos.getX(),pos.getY(),pos.getZ());}
 private ServerWorld resolve(String dimension){
  Identifier id=Identifier.tryParse(dimension);
  return id==null?null:server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id));
 }

 public Reading<GeneratorStateView> generator(ServerWorld world,BlockPos pos){
  world(world);
  if(!world.isChunkLoaded(pos))return absent(Availability.UNLOADED);
  if(!(world.getBlockEntity(pos) instanceof DeviceEntity e)||(e.kind()!=0&&e.kind()!=3))return absent(Availability.MISSING);
  return present(generatorSnapshot(e));
 }
 public Reading<FuseStateView> fuse(ServerWorld world,BlockPos pos){
  world(world);
  if(!world.isChunkLoaded(pos))return absent(Availability.UNLOADED);
  if(!(world.getBlockEntity(pos) instanceof DeviceEntity e)||e.kind()!=1)return absent(Availability.MISSING);
  return present(fuseSnapshot(e));
 }
 public Reading<EmergencyLightStateView> emergencyLight(ServerWorld world,BlockPos pos){
  world(world);
  if(!world.isChunkLoaded(pos))return absent(Availability.UNLOADED);
  if(!(world.getBlockEntity(pos) instanceof BackupLampEntity e))return absent(Availability.MISSING);
  return present(emergencyLightSnapshot(e));
 }
 static GeneratorStateView generatorSnapshot(DeviceEntity e){return new GeneratorStateView(e.kind(),e.fuel,e.active());}
 static FuseStateView fuseSnapshot(DeviceEntity e){return new FuseStateView(e.fuse,e.burnt,e.unstableFuse,e.fuseFailureChance,e.active());}
 static EmergencyLightStateView emergencyLightSnapshot(BackupLampEntity e){
  return new EmergencyLightStateView(e.charge(),e.worldStateLinked(),e.worldStateMains(),
   e.worldStateSwitch(),e.getCachedState().get(BackupLampBlock.LIT));
 }
 public Reading<SwitchStateView> powerSwitch(ServerWorld world,BlockPos pos){
  world(world);
  if(!world.isChunkLoaded(pos))return absent(Availability.UNLOADED);
  if(!world.getBlockState(pos).isOf(PowerDevices.POWER_SWITCH))return absent(Availability.MISSING);
  if(!PowerSwitches.worldStateAvailable(server))return absent(Availability.UNAVAILABLE);
  SwitchStateView snapshot=PowerSwitches.worldState(world,pos);
  return snapshot==null?absent(Availability.UNBOUND):present(snapshot);
 }
 public Reading<PowerNetworkStateView> network(String id){
  Objects.requireNonNull(id);long now=tick();
  return networks.get(id,now,()->readNetwork(id));
 }
 private Reading<PowerNetworkStateView> readNetwork(String id){
  if(!GridManager.worldStateAvailable(server,id))return absent(Availability.UNAVAILABLE);
  GridManager.Config c=GridManager.worldStateConfig(server,id);
  if(c==null)return absent(Availability.MISSING);
  if(c.configurationError!=null)return absent(Availability.UNAVAILABLE);
  ServerWorld world=resolve(c.dimension);
  if(world==null)return absent(Availability.UNAVAILABLE);
  Reading<GeneratorStateView> generator=c.generatorBound?generator(world,c.generator()):absent(Availability.UNBOUND);
  Reading<FuseStateView> fuse=c.panelBound?fuse(world,c.panel()):absent(Availability.UNBOUND);
  return networkSnapshot(id,c,generator,fuse,world.getTime(),tick());
 }
 /** Pure adapter of existing facts; never calls GridManager.sampleDevices/networkPowered. */
 static Reading<PowerNetworkStateView> networkSnapshot(String id,GridManager.Config c,
   Reading<GeneratorStateView> generator,Reading<FuseStateView> fuse,long worldTime,long tick){
  boolean genCached=generator.availability()==Availability.UNLOADED;
  boolean fuseCached=fuse.availability()==Availability.UNLOADED;
  GeneratorStateView g=generator.value().orElse(null);FuseStateView f=fuse.value().orElse(null);
  boolean genKnown=genCached?c.generatorObserved:g!=null;
  boolean fuseKnown=fuseCached?c.panelObserved:f!=null;
  boolean running=genCached?c.generatorRunning:g!=null&&g.running();
  boolean fueled=genCached?c.generatorFueled:g!=null&&g.fuelTicks()>0;
  boolean inserted=fuseCached?c.panelFuse:f!=null&&f.inserted();
  boolean burnt=fuseCached?c.panelBurnt:f!=null&&f.burnt();
  boolean flickering=c.flickerEnd!=0&&worldTime<c.flickerEnd;
  boolean power=c.configurationError==null&&GridStatePolicy.supplied(c.panelBound,c.generatorBound,
   fuseKnown,genKnown,inserted,burnt,running,fueled,c.burntPending,c.generatorStopPending)&&!flickering;
  List<FixtureStateView> fixtures=c.lamps.stream().map(x->new FixtureStateView(
   new Address(x.dimension,x.x,x.y,x.z),x.original)).toList();
  return new Reading<>(genCached||fuseCached?Availability.CACHED:Availability.AVAILABLE,tick,
   Optional.of(new PowerNetworkStateView(id,power,c.burntPending,c.generatorStopPending,flickering,generator,fuse,fixtures)));
 }
 public Reading<DoorStateView> door(ServerWorld world,BlockPos target){
  world(world);
  if(!world.isChunkLoaded(target))return absent(Availability.UNLOADED);
  BlockState state=world.getBlockState(target);
  if(!(state.getBlock() instanceof DoorBlock))return absent(Availability.MISSING);
  BlockPos pos=state.get(DoorBlock.HALF)==DoubleBlockHalf.UPPER?target.down():target;
  if(!world.isChunkLoaded(pos))return absent(Availability.UNLOADED);
  state=world.getBlockState(pos);
  if(!(state.getBlock() instanceof DoorBlock))return absent(Availability.MISSING);
  if(!ElectricDoors.worldStateAvailable(server))return absent(Availability.UNAVAILABLE);
  String binding=ElectricDoors.worldStateBinding(world,pos);
  Reading<Boolean> locked=present(false);
  if(binding!=null){
   Reading<PowerNetworkStateView> network=network(binding);
   locked=new Reading<>(network.availability(),tick(),network.value().map(n->!n.powered()));
  }
  return present(new DoorStateView(state.get(DoorBlock.OPEN),state.get(DoorBlock.POWERED),Optional.ofNullable(binding),locked));
 }
 static ZoneStateView zoneSnapshot(Zone z,double x,double y,double zz,String dimension){
  return new ZoneStateView(z.id,z.name,z.dimension,z.shelter,z.blizzard,z.snow,z.cold,z.weight(x,y,zz,dimension));
 }
 private Optional<ZoneStateView> zone(Zone z,Vec3d point,String dim){
  return z==null?Optional.empty():Optional.of(zoneSnapshot(z,point.x,point.y,point.z,dim));
 }
 /** Same block-centre selection as WorldAtmosphere; excludes client transition smoothing. */
 public Reading<WeatherStateView> weather(ServerWorld world,BlockPos pos){
  world(world);
  if(Store.get(server).error!=null||!WorldAtmosphere.worldStateAvailable(server))return absent(Availability.UNAVAILABLE);
  Zone w=WorldAtmosphere.weather(world,pos),s=WorldAtmosphere.shelter(world,pos);
  Vec3d point=Vec3d.ofCenter(pos);String dim=world.getRegistryKey().getValue().toString();
  boolean blizzard=w!=null&&w.blizzard;double snow=w==null?0:blizzard?1:w.snow;
  long time=world.getTime();
  return present(new WeatherStateView(zone(w,point,dim),zone(s,point,dim),blizzard,snow,
   SharedWindField.directionX(time),SharedWindField.directionZ(time),
   SharedWindField.strength(time,WorldAtmosphere.get(server).windStrengthPercent/100.0,blizzard,snow)));
 }
 /** Current eye-position shelter inputs; accumulated client cold is explicitly unavailable. */
 public Reading<ShelterStateView> shelter(ServerPlayerEntity player){
  ServerWorld world=player.getServerWorld();world(world);
  if(Store.get(server).error!=null||!WorldAtmosphere.worldStateAvailable(server))return absent(Availability.UNAVAILABLE);
  Vec3d eye=player.getEyePos();BlockPos pos=BlockPos.ofFloored(eye);
  if(!world.isChunkLoaded(pos))return absent(Availability.UNLOADED);
  String dim=world.getRegistryKey().getValue().toString();
  Zone shelter=WorldAtmosphere.shelter(world,pos),weather=WorldAtmosphere.weather(world,pos);
  AtmosphereConfig config=WorldAtmosphere.get(server);
  float opening=shelter==null?1:shelters.get(new ShelterKey(dim,eye),tick(),()->ShelterExposure.measure(world,eye)).opening();
  float weight=shelter==null?0:shelter.weight(eye.x,eye.y,eye.z,dim);
  float protection=config.coldLeaks?ShelterExposure.effectiveProtection(weight,opening):weight;
  float outside=weather==null?12:weather.blizzard?config.tempOutdoorBlizzard:config.tempOutdoorNormal;
  float temperature=shelter==null?outside:config.coldLeaks?ShelterExposure.indoorTemperature(outside,opening):19;
  return present(new ShelterStateView(zone(shelter,eye,dim),opening,protection,temperature,
   Availability.UNAVAILABLE,OptionalDouble.empty()));
 }
}
