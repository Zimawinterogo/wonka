package dev.whiteout.power;

import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.*;
import static dev.whiteout.power.WinterCrisisConditions.*;
import static dev.whiteout.power.WinterCrisisDirector.*;

/** Fabric lifecycle/commands only. Disabled worlds never sample conditions or run actions. */
public final class CrisisRuntime {
 private static final Logger LOG=LoggerFactory.getLogger("whiteout-crisis");
 private static final Map<MinecraftServer,Slot> SERVERS=new IdentityHashMap<>();
 private static boolean registered;
 private static final class Slot {
  final CrisisSettings settings;final WinterCrisisDirector director;final CrisisScenes scenes;long tick;boolean stopping;
  Slot(MinecraftServer server,CrisisSettings settings){
   this.settings=settings;
   scenes=new CrisisScenes(server);
   director=new WinterCrisisDirector(new Actions(){
    public void enter(View v)throws Exception{if(scenes.owns(v.id()))scenes.enter(v);else LOG.info("Technical crisis {} ACTIVE (diagnostic only)",v.id());}
    public boolean ready(View v){return !scenes.owns(v.id())||scenes.ready(v.id(),v.phase());}
    public void phase(View v)throws Exception{if(scenes.owns(v.id()))scenes.advance(v);}
    public void complete(View v)throws Exception{if(scenes.owns(v.id()))scenes.complete(v);else LOG.info("Technical crisis {} COMPLETED",v.id());}
    public void cancel(View v)throws Exception{if(scenes.owns(v.id())){if(!stopping)scenes.cancel(v);}else LOG.info("Technical crisis {} CANCELLED: {}",v.id(),v.reason());}
    public void cancelPending(View v)throws Exception{if(scenes.owns(v.id())&&!stopping)scenes.cancel(v);}
   });
   director.enabled(settings.enabled(),0);
  }
 }
 private CrisisRuntime(){}
 public static synchronized void register(){
  if(registered)return;registered=true;
  ServerLifecycleEvents.SERVER_STARTED.register(server->{
   Slot old=SERVERS.remove(server);if(old!=null){old.stopping=true;old.director.close();}
   CrisisSettings settings=CrisisSettings.load(server.getSavePath(WorldSavePath.ROOT).resolve("whiteout-winter-crisis.json"));
   Slot slot=new Slot(server,settings);SERVERS.put(server,slot);slot.scenes.recover(slot.director,slot.tick);
   if(settings.blocked())LOG.error("Winter Crisis disabled: {}. File preserved.",settings.error());
  });
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{Slot slot=SERVERS.remove(server);if(slot!=null){slot.stopping=true;slot.director.close();}});
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
   Slot s=SERVERS.get(server);if(s==null)return;
   s.director.departed(handler.getPlayer().getUuid(),s.tick);
   if(server.getCurrentPlayerCount()<=1)for(View v:s.director.status())if(!s.scenes.owns(v.id()))s.director.cancel(v.id(),s.tick,"last player disconnected");
  });
  ServerTickEvents.END_SERVER_TICK.register(server->{
   Slot s=SERVERS.get(server);if(s==null||server.getCurrentPlayerCount()==0)return;s.tick++;
   if(!s.director.enabled()||s.tick%10!=0)return;
   s.scenes.reconcile(s.director,s.tick);
   var source=new CrisisWorldSource(server);
   s.director.tick(s.tick,signal->signal.type()==Type.SCENARIO_READY?Truth.of(s.scenes.ready(signal.id(),0)):source.sample(signal),target->present(server,target));
  });
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("whiteout")
   .then(literal("crisis").requires(source->source.hasPermissionLevel(2))
    .then(literal("status").executes(c->status(c.getSource())))
    .then(literal("scene").then(argument("id",StringArgumentType.word()).executes(c->scene(c.getSource(),StringArgumentType.getString(c,"id")))))
    .then(literal("scene-reset").then(argument("id",StringArgumentType.word()).executes(c->sceneReset(c.getSource(),StringArgumentType.getString(c,"id")))))
    .then(literal("enable").executes(c->enable(c.getSource(),true)))
    .then(literal("disable").executes(c->enable(c.getSource(),false)))
    .then(literal("test").executes(c->test(c.getSource(),null))
     .then(argument("network",StringArgumentType.word()).executes(c->test(c.getSource(),StringArgumentType.getString(c,"network")))))
    .then(literal("cancel").executes(c->cancel(c.getSource())))
    .then(literal("reset").executes(c->reset(c.getSource()))))));
 }
 private static boolean present(MinecraftServer server,Target target){
  Identifier id=Identifier.tryParse(target.dimension());
  if(id==null||server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id))==null||server.getCurrentPlayerCount()==0)return false;
  for(UUID uuid:target.players()){
   var p=server.getPlayerManager().getPlayer(uuid);
   if(p==null||!p.isAlive()||p.isSpectator()||!p.getServerWorld().getRegistryKey().getValue().toString().equals(target.dimension()))return false;
  }
  if(target.scope()==Scope.WORLD)return server.getPlayerManager().getPlayerList().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&p.getServerWorld().getRegistryKey().getValue().toString().equals(target.dimension()));
  return true;
 }
 private static Slot slot(ServerCommandSource source){return SERVERS.get(source.getServer());}
 public static boolean requestable(MinecraftServer server,StorySceneActions.Change request){
  Slot slot=SERVERS.get(server);return slot!=null&&slot.scenes.owns("scene:"+request.id())&&(request.operation()==StorySceneActions.Operation.CANCEL||slot.director.enabled());
 }
 private static int reply(ServerCommandSource source,String text){source.sendFeedback(()->Text.literal(text),false);return 1;}
 private static int enable(ServerCommandSource source,boolean value){
  Slot s=slot(source);if(s==null)return reply(source,"Crisis runtime not ready");
  if(!value)s.director.enabled(false,s.tick);
   try{s.settings.save(value);s.director.enabled(value,s.tick);if(value)s.scenes.recover(s.director,s.tick);return reply(source,"Winter Crisis enabled="+value+" for this save");}
  catch(Exception error){
   if(s.settings.blocked())s.director.enabled(false,s.tick);
   source.sendError(Text.literal("Settings not saved; existing file preserved. "+error.getMessage()));return 0;
  }
 }
 private static int status(ServerCommandSource source){
  Slot s=slot(source);if(s==null)return reply(source,"Crisis runtime not ready");
  reply(source,"Winter Crisis enabled="+s.director.enabled()+", configBlocked="+s.settings.blocked()+", instances="+s.director.status().size());
  reply(source,s.scenes.status());
  for(View v:s.director.status())reply(source,v.id()+" "+v.state()+" phase="+v.phase()+" runs="+v.runs()+" "+v.reason());
  List<Event> events=s.director.history();
  for(Event e:events.subList(Math.max(0,events.size()-6),events.size()))reply(source,"@"+e.tick()+" "+e.id()+" "+e.state()+" "+e.reason());
  return 1;
 }
 private static int scene(ServerCommandSource source,String id){
  Slot s=slot(source);if(s==null)return 0;
  try{return reply(source,s.scenes.start(id,s.director,s.tick));}
  catch(Exception failure){source.sendError(Text.literal("Scene refused: "+failure.getMessage()));return 0;}
 }
 private static int sceneReset(ServerCommandSource source,String id){
  Slot s=slot(source);if(s==null)return 0;
  try{return reply(source,s.scenes.reset(id,s.director,s.tick));}
  catch(Exception failure){source.sendError(Text.literal("Scene reset refused: "+failure.getMessage()));return 0;}
 }
 /** Known weather and shelter are deliberately non-destructive availability probes:
  * X OR NOT X is TRUE for a known observation, UNKNOWN for missing/unloaded data. */
 private static Condition known(Condition c){return new Any(List.of(c,new Not(c)));}
 private static int test(ServerCommandSource source,String network)throws CommandSyntaxException {
  Slot s=slot(source);if(s==null||!s.director.enabled())return reply(source,"Enable Winter Crisis explicitly first");
  var p=source.getPlayerOrThrow();String dimension=p.getServerWorld().getRegistryKey().getValue().toString();
  String id="technical:"+p.getUuid();
  if(network!=null&&(network.isBlank()||network.length()>64))return reply(source,"Network ID must be 1..64 characters");
  Condition weather=known(new Atom(Signal.player(Type.BLIZZARD,p.getUuid(),dimension,null,0)));
  Condition shelter=known(new Atom(Signal.player(Type.OUTSIDE_SHELTER,p.getUuid(),dimension,null,.5)));
  Condition trigger=network==null?shelter:new All(List.of(shelter,known(new Atom(Signal.network(network)))));
  var existing=s.director.status(id);
  if(existing.isPresent()&&!existing.get().target().dimension().equals(dimension))return reply(source,"Target dimension changed; use crisis reset before another test");
  if(existing.isEmpty()){
   Spec spec=new Spec(id,new Target(Scope.PLAYER,dimension,Set.of(p.getUuid())),weather,trigger,10,3,10,200,40,100,
    CancelPolicy.REQUIRE_TRUE_WHILE_ACTIVE,CompletionPolicy.AFTER_DURATION);
   if(!s.director.register(spec))return reply(source,"Crisis capacity reached; use status/reset");
  }else if(network!=null)return reply(source,"Existing test keeps its original probes; reset before changing network");
  return reply(source,s.director.arm(id,s.tick)?"Technical test ARMED; inspect crisis status":"Test already running, cooling down, or repeat limit reached; inspect status");
 }
 private static int cancel(ServerCommandSource source){Slot s=slot(source);if(s==null)return 0;s.director.cancelAll(s.tick,"operator cancellation");return reply(source,"Pending/active technical tests cancelled");}
 private static int reset(ServerCommandSource source){Slot s=slot(source);if(s==null)return 0;s.director.reset(s.tick);return reply(source,"Temporary crisis instances/history reset; story saves unchanged");}
}
