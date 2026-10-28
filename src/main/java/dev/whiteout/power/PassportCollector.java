package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.power.bolt.DoorBolts;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;
import net.minecraft.block.Blocks;
import static dev.whiteout.power.PassportIssue.Severity.*;

/** Server-thread collector. Never loads chunks or writes world configuration. */
public final class PassportCollector {
 private PassportCollector(){}
 private static MapPassport.Position position(String dim,BlockPos p){return new MapPassport.Position(dim,p.getX(),p.getY(),p.getZ());}
 private static ServerWorld world(MinecraftServer server,String dim){
  for(var w:server.getWorlds())if(w.getRegistryKey().getValue().toString().equals(dim))return w;return null;
 }
 private static boolean loaded(MapPassport.Builder b,String id,ServerWorld w,MapPassport.Position p){
  if(w==null){b.issue(ERROR,"dimension",id,p);return false;}
  if(!w.isChunkLoaded(new BlockPos(p.x(),p.y(),p.z()))){b.issue(INFO,"unloaded",id,p);return false;}return true;
 }
 public static MapPassport capture(ServerPlayerEntity player){
  var server=player.getServer();var b=new MapPassport.Builder();
  var snapshot=PowerDiagnostics.capture(player);
  for(var row:snapshot.rows()){
   Map<String,String> details=new LinkedHashMap<>();int n=0;
   for(var d:row.details())details.put(d.key()+" "+(++n),d.value());
   b.entry(row.kind().equals("zones")?"zones":"networks",row.id(),null,details);
  }
  if(snapshot.truncated())b.add(new MapPassport(1,List.of(),List.of(),true));
  var configs=GridManager.diagnosticConfigs(server);
  for(var e:configs.entrySet()){
   String id=e.getKey();var c=e.getValue();var w=world(server,c.dimension);
   PassportRules.network(b,id,c.panelBound,c.generatorBound,c.lamps.size(),c.configurationError!=null);
   if(w==null)b.issue(ERROR,"dimension",id,null);
   for(boolean generator:new boolean[]{true,false}){
    if(generator?!c.generatorBound:!c.panelBound)continue;
    var pos=generator?c.generator():c.panel();var p=position(c.dimension,pos);
    if(!loaded(b,id,w,p))continue;
    var be=w.getBlockEntity(pos);
    boolean valid=be instanceof DeviceEntity d&&(generator?(d.kind()==0||d.kind()==3):d.kind()==1);
    var d=valid?(DeviceEntity)be:null;
    PassportRules.device(b,id,p,valid?PassportRules.Observation.PRESENT:PassportRules.Observation.MISSING,
     generator&&valid?d.fuel>0:null,!generator&&valid?d.fuse&&!d.burnt:null);
   }
   for(var f:c.lamps){
    var p=position(f.dimension,f.pos());var fw=world(server,f.dimension);
    b.entry("networks",id+"/lamp",p,Map.of("block",String.valueOf(f.original)));
    if(!loaded(b,id,fw,p))continue;
    var state=fw.getBlockState(f.pos());
    boolean valid=f.vanilla?(state.isOf(Blocks.LANTERN)||state.isOf(PowerDevices.LINKED_LANTERN)):
     Objects.equals(Registries.BLOCK.getId(state.getBlock()).toString(),f.original);
    PassportRules.device(b,id,p,valid?PassportRules.Observation.PRESENT:PassportRules.Observation.MISSING,null,null);
   }
  }
  if(GridManager.diagnosticInvalidNetworks(server))b.issue(ERROR,"invalid_config","networks",null);
  if(!ElectricDoors.worldStateAvailable(server))b.issue(ERROR,"invalid_config","electric_doors",null);
  for(var e:ElectricDoors.diagnosticBindings(server).entrySet()){
   var p=parseDoor(e.getKey());b.entry("networks","door: "+e.getKey(),p,Map.of("network",e.getValue()));
   PassportRules.reference(b,e.getKey(),e.getValue(),configs.keySet(),p);
   if(p==null)b.issue(ERROR,"invalid_config",e.getKey(),null);else loaded(b,e.getKey(),world(server,p.dimension()),p);
  }
  if(DoorBolts.diagnosticInvalid(server))b.issue(ERROR,"invalid_config","bolts",null);
  for(var e:DoorBolts.diagnosticEntries(server)){
   var p=new MapPassport.Position(e.dimension(),e.x(),e.y(),e.z());
   b.entry("networks","bolt",p,Map.of("inner",String.valueOf(e.inner()),"locked",String.valueOf(e.locked())));
   loaded(b,"bolt",world(server,p.dimension()),p);
  }
  zones(server,b);
  b.issue(INFO,"client_dialogues","",null);
  b.issue(INFO,"summary",snapshot.rows().size()+" diagnostic rows",null);
  return b.build();
 }
 private static MapPassport.Position parseDoor(String key){
  try{String[] a=key.split(":");if(a.length!=5)return null;return new MapPassport.Position(a[0]+":"+a[1],Integer.parseInt(a[2]),Integer.parseInt(a[3]),Integer.parseInt(a[4]));}
  catch(RuntimeException ex){return null;}
 }
 private static void zones(MinecraftServer s,MapPassport.Builder b){
  Set<String> ids=new HashSet<>();List<PassportRules.Zone> geometry=new ArrayList<>();
  var folders=ZoneFolders.diagnosticAssignments(s);
  if(Store.get(s).error!=null)b.issue(ERROR,"invalid_config","zones",null);
  if(ZoneFolders.diagnosticInvalid(s))b.issue(ERROR,"invalid_config","folders",null);
  for(var z:Store.get(s).data.zones){
   if(z==null){b.issue(ERROR,"invalid_zone","null",null);continue;}
   var p=new MapPassport.Position(z.dimension,(int)z.x,(int)z.y,(int)z.z);
   if(z.id==null||z.id.isBlank()||!ids.add(z.id))b.issue(ERROR,"invalid_zone",String.valueOf(z.id),p);
   if(world(s,z.dimension)==null)b.issue(ERROR,"dimension",z.id,p);
   boolean valid=z.cuboid?PassportRules.bounds(z.x1,z.y1,z.z1,z.x2,z.y2,z.z2):
    Float.isFinite(z.radius)&&z.radius>0&&Double.isFinite(z.x)&&Double.isFinite(z.y)&&Double.isFinite(z.z);
   if(!valid)b.issue(ERROR,"invalid_zone",z.id,p);
   b.entry("zones",z.id,p,Map.of("name",String.valueOf(z.name),"type",z.shelter?"shelter":"weather","folder",folders.getOrDefault(z.id,""),
    "enabled",String.valueOf(z.enabled),"music",String.valueOf(z.music),"priority",String.valueOf(z.priority)));
   if(valid&&geometry.size()<MapPassport.LIMIT)geometry.add(new PassportRules.Zone(z.id,z.dimension,z.enabled,z.shelter,z.priority,z.minX(),z.minY(),z.minZ(),z.maxX(),z.maxY(),z.maxZ()));
   else if(geometry.size()>=MapPassport.LIMIT)b.add(new MapPassport(1,List.of(),List.of(),true));
  }
  // Same-priority bounding volumes are candidates, not proof of a weather conflict (cuts may exclude them).
  for(int i=0;i<geometry.size();i++)for(int j=i+1;j<geometry.size();j++)if(PassportRules.overlaps(geometry.get(i),geometry.get(j)))
   b.issue(WARN,"overlap",geometry.get(i).id()+" / "+geometry.get(j).id(),null);
  folders.forEach((id,folder)->PassportRules.reference(b,"folder: "+folder,id,ids,null));
  if(!WorldAtmosphere.worldStateAvailable(s))b.issue(ERROR,"invalid_config","atmosphere",null);
  var a=WorldAtmosphere.get(s);
  b.entry("atmosphere","world",null,Map.of("windStrengthPercent",""+a.windStrengthPercent,"windMovement",""+a.windMovement,
   "windThroughOpenings",""+a.windThroughOpenings,"coldLeaks",""+a.coldLeaks,"oldHouseSounds",""+a.oldHouseSounds,
   "slammingDoors",""+a.slammingDoors,"radioStatic",""+a.radioStatic,"windowFrost",""+a.windowFrost,
   "tempOutdoorBlizzard",""+a.tempOutdoorBlizzard,"tempOutdoorNormal",""+a.tempOutdoorNormal));
  if(a.windMovement&&a.windStrengthPercent==0)b.issue(WARN,"zero_wind","world",null);
  Set<String> references=new HashSet<>(a.oldShelters);references.addAll(a.oldSoundIntervals.keySet());references.addAll(a.mutedOldSoundZones);
  for(String id:references){
   PassportRules.reference(b,"atmosphere",id,ids,null);
   b.entry("atmosphere",id,null,Map.of("old",""+a.oldShelters.contains(id),"muted",""+a.mutedOldSoundZones.contains(id),"soundInterval",""+a.oldSoundIntervals.getOrDefault(id,30)));
  }
 }
}
