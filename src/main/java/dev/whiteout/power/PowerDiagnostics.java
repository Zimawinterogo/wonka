package dev.whiteout.power;

import com.google.gson.Gson;
import dev.whiteout.Store;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

/** Operator-only snapshots of existing state; no edit operations and no forced chunk loads. */
public final class PowerDiagnostics {
 private static final Gson GSON=new Gson();
 private static final Map<ServerPlayerEntity,Long> LAST=new WeakHashMap<>();
 public record Request(int token) implements CustomPayload {
  public static final Id<Request> ID=new Id<>(PowerDevices.id("diagnostics_request"));
  public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,p)->b.writeVarInt(p.token),b->new Request(b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Part(int token,int index,int total,String json) implements CustomPayload {
  public static final Id<Part> ID=new Id<>(PowerDevices.id("diagnostics_snapshot"));
  public static final PacketCodec<RegistryByteBuf,Part> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeVarInt(p.token);b.writeVarInt(p.index);b.writeVarInt(p.total);b.writeString(p.json,SnapshotChunks.SIZE);},
   b->new Part(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readString(SnapshotChunks.SIZE)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 private PowerDiagnostics(){}
 public static void register(){
  PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
  PayloadTypeRegistry.playS2C().register(Part.ID,Part.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Request.ID,(p,c)->c.server().execute(()->send(c.player(),p.token())));
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->LAST.remove(handler.getPlayer()));
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,env)->dispatcher.register(literal("whiteout")
   .then(literal("power").then(literal("diagnostics").requires(s->s.hasPermissionLevel(2))
    .executes(c->{send(c.getSource().getPlayerOrThrow(),0);return 1;})))));
 }
 private static void send(ServerPlayerEntity player,int token){
  if(!DiagnosticData.allowed(player.hasPermissionLevel(2)?2:0,player.isSpectator()))return;
  long now=System.nanoTime();Long before=LAST.get(player);
  if(before!=null&&now-before<1_000_000_000L)return;
  LAST.put(player,now);
  var snapshot=capture(player);
  var chunks=SnapshotChunks.split(GSON.toJson(snapshot));
  while(chunks.isEmpty()&&snapshot.rows().size()>1){
   snapshot=new DiagnosticData.Snapshot(snapshot.rows().subList(0,snapshot.rows().size()/2),true);
   chunks=SnapshotChunks.split(GSON.toJson(snapshot));
  }
  if(chunks.isEmpty()){player.sendMessage(Text.translatable("diag.whiteout.too_large"),false);return;}
  for(int i=0;i<chunks.size();i++)ServerPlayNetworking.send(player,new Part(token,i,chunks.size(),chunks.get(i)));
 }
 private static String text(String value){return value==null?"":value.substring(0,Math.min(160,value.length()));}
 private static DiagnosticData.Detail raw(String key,Object value){return new DiagnosticData.Detail(key,text(String.valueOf(value)),false);}
 private static DiagnosticData.Detail status(String key,String value){return new DiagnosticData.Detail(key,value,true);}
 private static String key(String dim,BlockPos pos){return dim+":"+pos.asLong();}
 private static DeviceEntity device(ServerWorld world,BlockPos pos){return world!=null&&world.isChunkLoaded(pos)&&world.getBlockEntity(pos) instanceof DeviceEntity e?e:null;}
 static DiagnosticData.Snapshot capture(ServerPlayerEntity player){
  var server=player.getServer();var configs=GridManager.diagnosticConfigs(server);
  List<DiagnosticData.Row> rows=new ArrayList<>();Set<String> devices=new HashSet<>();
  Map<String,String> owned=new HashMap<>();
  boolean truncated=false;
  for(var entry:configs.entrySet()){
   String id=entry.getKey();var c=entry.getValue();ServerWorld world=null;
   for(var w:server.getWorlds())if(w.getRegistryKey().getValue().toString().equals(c.dimension)){world=w;break;}
   var gen=c.generatorBound?device(world,c.generator()):null;
   var panel=c.panelBound?device(world,c.panel()):null;
   if(c.generatorBound&&world!=null&&world.isChunkLoaded(c.generator())){
    c.generatorObserved=gen!=null&&(gen.kind()==0||gen.kind()==3);c.generatorRunning=c.generatorObserved&&gen.active();c.generatorFueled=c.generatorObserved&&gen.fuel>0;
   }
   if(c.panelBound&&world!=null&&world.isChunkLoaded(c.panel())){
    c.panelObserved=panel!=null&&panel.kind()==1;c.panelFuse=c.panelObserved&&panel.fuse;c.panelBurnt=c.panelObserved&&panel.burnt;
   }
   String reason=DiagnosticData.reason(c.configurationError!=null,c.panelBound,c.generatorBound,c.panelObserved,c.generatorObserved,
    c.panelFuse,c.panelBurnt,c.generatorRunning,c.generatorFueled,c.burntPending,c.generatorStopPending,world!=null&&world.getTime()<c.flickerEnd);
   var details=new ArrayList<DiagnosticData.Detail>();details.add(raw("dimension",c.dimension));details.add(status("power",reason));
   details.add(c.generatorBound?raw("generator",c.generator().toShortString()):status("generator","unbound"));
   details.add(c.panelBound?raw("panel",c.panel().toShortString()):status("panel","unbound"));
   if((c.generatorBound&&gen==null)||(c.panelBound&&panel==null))details.add(status("notice","cached"));
   details.add(raw("fixtures",c.lamps.size()));
   for(var f:c.lamps){if(details.size()>=100){details.add(status("notice","truncated"));break;}details.add(raw("fixture",f.dimension+" "+f.pos().toShortString()));}
   if(rows.size()>=DiagnosticData.MAX_ROWS){truncated=true;break;}
   rows.add(new DiagnosticData.Row("network:"+id,"networks",text(id),reason,details));
   for(boolean generator:new boolean[]{true,false}){
    if(generator?!c.generatorBound:!c.panelBound)continue;
    BlockPos pos=generator?c.generator():c.panel();owned.put(key(c.dimension,pos),id);
    if(rows.size()<DiagnosticData.MAX_ROWS){
     if(devices.add(key(c.dimension,pos)))rows.add(deviceRow(c.dimension,pos,generator?gen:panel,generator,id,reason,world!=null&&world.isChunkLoaded(pos)));
    }else truncated=true;
   }
  }
  // Unbound devices: inspect only block-entity maps of already loaded nearby chunks.
  var world=player.getServerWorld();int cx=player.getBlockX()>>4,cz=player.getBlockZ()>>4;
  for(int x=cx-4;x<=cx+4;x++)for(int z=cz-4;z<=cz+4;z++){
   var chunk=world.getChunkManager().getWorldChunk(x,z);if(chunk==null)continue;
   for(var be:chunk.getBlockEntities().values())if(be instanceof DeviceEntity e&&(e.kind()==0||e.kind()==3||e.kind()==1)){
    String dim=world.getRegistryKey().getValue().toString(),key=key(dim,e.getPos());
    if(rows.size()>=DiagnosticData.MAX_ROWS){truncated=true;break;}
    if(devices.add(key))rows.add(deviceRow(dim,e.getPos(),e,e.kind()!=1,owned.getOrDefault(key,""),"unbound",true));
   }
  }
  var folders=ZoneFolders.diagnosticAssignments(server);
  for(var zone:Store.get(server).data.zones){
   if(zone==null)continue;if(rows.size()>=DiagnosticData.MAX_ROWS){truncated=true;break;}
   rows.add(new DiagnosticData.Row("zone:"+zone.id,"zones",text(zone.name),zone.enabled?"enabled":"disabled",List.of(
    raw("id",zone.id),raw("dimension",zone.dimension),raw("folder",folders.getOrDefault(zone.id,"")),status("network","not_supported"))));
  }
  var doors=ElectricDoors.diagnosticBindings(server);var switches=PowerSwitches.diagnosticBindings(server);
  for(int i=0;i<rows.size();i++){
   var row=rows.get(i);if(!row.kind().equals("networks"))continue;
   var details=new ArrayList<>(row.details());String id=row.id().substring("network:".length());
   boolean[] omitted={false};
   doors.forEach((pos,network)->{if(id.equals(network)){if(details.size()<100)details.add(raw("door",pos));else omitted[0]=true;}});
   switches.forEach((pos,network)->{if(id.equals(network)){if(details.size()<100)details.add(raw("switch",pos));else omitted[0]=true;}});
   if(omitted[0]&&details.stream().noneMatch(d->d.value().equals("truncated")))details.add(status("notice","truncated"));
   rows.set(i,new DiagnosticData.Row(row.id(),row.kind(),row.name(),row.state(),details));
  }
  Map<String,List<DiagnosticData.Detail>> networkDetails=new HashMap<>();
  for(var row:rows)if(row.kind().equals("networks"))networkDetails.put(row.id().substring(8),row.details());
  for(int i=0;i<rows.size();i++){
   var row=rows.get(i);if(!row.kind().equals("panels"))continue;
   String network=row.details().stream().filter(d->d.key().equals("network")&&!d.translated()).map(DiagnosticData.Detail::value).findFirst().orElse("");
   var details=new ArrayList<>(row.details());
   for(var d:networkDetails.getOrDefault(network,List.of()))
    if(Set.of("fixtures","fixture","door","switch","notice").contains(d.key()))details.add(d);
   rows.set(i,new DiagnosticData.Row(row.id(),row.kind(),row.name(),row.state(),details));
  }
  if(GridManager.diagnosticInvalidNetworks(server)&&rows.size()<DiagnosticData.MAX_ROWS)
   rows.add(new DiagnosticData.Row("invalid-networks","networks","","invalid",List.of(status("notice","invalid"))));
  return new DiagnosticData.Snapshot(List.copyOf(rows.subList(0,Math.min(rows.size(),DiagnosticData.MAX_ROWS))),truncated);
 }
 private static DiagnosticData.Row deviceRow(String dim,BlockPos pos,DeviceEntity e,boolean generator,String network,String power,boolean loaded){
  var details=new ArrayList<DiagnosticData.Detail>();details.add(raw("dimension",dim));details.add(raw("position",pos.toShortString()));
  details.add(network.isEmpty()?status("network","unbound"):raw("network",network));
  details.add(status("power",power));
  boolean valid=e!=null&&(generator?(e.kind()==0||e.kind()==3):e.kind()==1);
  String state=!loaded?"unloaded":!valid?"missing":e.active()?"on":"stopped";
  details.add(status("state",state));
  if(valid){
   details.add(status("type",e.kind()==3?"old_generator":generator?"modern_generator":"panel"));
   if(generator)details.add(raw("fuel",e.fuel));
   else{details.add(status("fuse",e.burnt?"burnt":e.fuse?"present":"no_fuse"));details.add(status("unstable",e.unstableFuse?"yes":"no"));}
  }
  return new DiagnosticData.Row((generator?"generator:":"panel:")+key(dim,pos),generator?"generators":"panels",pos.toShortString(),state,details);
 }
}
