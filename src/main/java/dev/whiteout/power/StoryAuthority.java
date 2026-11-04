package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.net.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.literal;

/** Entry guard for the original network handler. All calls run on the server thread. */
public final class StoryAuthority {
 private static final Logger LOG=LoggerFactory.getLogger("whiteout-story");
 private static final Map<MinecraftServer,StoryEventCatalog> CATALOGS=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,StoryRequestPolicy.Budget> BUDGETS=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,Long> WARNINGS=new WeakHashMap<>();
 private static boolean registered;
 private StoryAuthority(){}
 public static void register(){
  if(registered)return;registered=true;
  StoryAuthorityNetwork.register();
  TriggerAuthority.register();
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{BUDGETS.remove(h.getPlayer());WARNINGS.remove(h.getPlayer());});
  ServerLifecycleEvents.SERVER_STOPPED.register(s->{CATALOGS.remove(s);BUDGETS.clear();WARNINGS.clear();});
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("whiteout")
   .then(literal("story-authority").requires(source->source.hasPermissionLevel(2))
    .then(literal("status").executes(c->{
     var catalog=catalog(c.getSource().getServer());
     c.getSource().sendFeedback(()->Text.literal("Story authority: events="+catalog.size()+", invalid="+catalog.invalid()+", error="+catalog.error()),false);return catalog.invalid()?0:1;
    }))
    .then(literal("store-status").executes(c->{
     var server=c.getSource().getServer();var store=Store.get(server);
     var status=StoryRecovery.status(store,server.getSavePath(WorldSavePath.ROOT).resolve("whiteout-zones.json"));
     c.getSource().sendFeedback(()->Text.literal(status),false);return store.error==null?1:0;
    }))
    .then(literal("recover-backup").executes(c->{
     var server=c.getSource().getServer();
     try{StoryRecovery.recover(Store.get(server),server.getSavePath(WorldSavePath.ROOT).resolve("whiteout-zones.json"));}
     catch(java.io.IOException failure){c.getSource().sendError(Text.literal(failure.getMessage()));return 0;}
     c.getSource().sendFeedback(()->Text.literal("Validated backup restored. Restart the server before resuming play."),true);return 1;
    }))
    .then(literal("reload").executes(c->{
     CATALOGS.remove(c.getSource().getServer());var catalog=catalog(c.getSource().getServer());
     c.getSource().sendFeedback(()->Text.literal("Story authority reloaded: events="+catalog.size()+", invalid="+catalog.invalid()),false);return catalog.invalid()?0:1;
    })))));
 }
 static StoryEventCatalog catalog(MinecraftServer server){
  return CATALOGS.computeIfAbsent(server,s->new StoryEventCatalog(s.getSavePath(WorldSavePath.ROOT).resolve("whiteout-story-events.json")));
 }
 public static StoryEventCatalog.Event definition(MinecraftServer server,String key){
  var definitions=catalog(server);if(definitions.invalid())throw new IllegalStateException("invalid story catalog");
  var event=definitions.find(key);if(event==null)throw new IllegalArgumentException("unregistered story key: "+key);return event;
 }
 public static Set<String> sceneStartKeys(MinecraftServer server,String id){
  var definitions=catalog(server);if(definitions.invalid())throw new IllegalStateException("invalid story catalog");
  return definitions.dedicatedSceneStartKeys(id);
 }
 private static void denied(ServerPlayerEntity player,String reason){
  long now=Integer.toUnsignedLong(player.getServer().getTicks());Long previous=WARNINGS.get(player);
  if(previous!=null&&now>=previous&&now-previous<200)return;
  WARNINGS.put(player,now);
  player.sendMessage(Text.translatable("story.whiteout.request_denied"),false);
  LOG.warn("Rejected story/editor request from {}: {}",player.getUuid(),reason.substring(0,Math.min(160,reason.length())));
 }
 /** true delegates to the original handler; false means denied or handled here. */
 public static boolean accept(ServerPlayerEntity player,WhiteoutActionPayload packet){
  if(!player.getServer().isOnThread())throw new IllegalStateException("Story requests require server thread");
  if(!"STORY_COMMIT".equals(packet.action())){
   boolean allowed=StoryRequestPolicy.legacyAllowed(packet.action(),player.hasPermissionLevel(2));
   if(!allowed)denied(player,"permission or unknown action");return allowed;
  }
  long tick=Integer.toUnsignedLong(player.getServer().getTicks());
  if(!BUDGETS.computeIfAbsent(player,p->new StoryRequestPolicy.Budget()).take(tick))return false;
  try{
   StoryEventCatalog.Event event=catalog(player.getServer()).resolve(packet.data());
   if(event.trigger()==null)execute(player,event.key());
  }catch(Exception failure){denied(player,failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
  return false;
 }
 public static StoryReceipts.Outcome execute(ServerPlayerEntity player,String key){
  if(!player.getServer().isOnThread())throw new IllegalStateException("Story requests require server thread");
  try{
   if(!player.isAlive()||player.isSpectator())throw new IllegalArgumentException("player state");
   StoryEventCatalog definitions=catalog(player.getServer());
   if(definitions.invalid())throw new IllegalStateException("invalid server catalog");
   StoryEventCatalog.Event event=definitions.find(key);
   if(event==null)throw new IllegalArgumentException("unregistered story key");
   if(event.serverOnly()||!dev.whiteout.power.lobby.Party.active(player))throw new IllegalArgumentException("event unavailable to participant");
   for(var scene:event.scenes())if(!CrisisRuntime.requestable(player.getServer(),scene))throw new IllegalArgumentException("scene unavailable: "+scene.id());
   if(event.trigger()!=null){
    if(!TriggerAuthority.permits(player,key))return StoryReceipts.Outcome.DENIED;
   }else if(!event.area().contains(player.getServerWorld().getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ()))throw new IllegalArgumentException("outside event area");
   Store store=Store.get(player.getServer());
   var scoped=ScopedStory.read(store.data,event.scope(),player.getUuid());
   if(StoryWorldConditions.evaluate(event.world(),new StoryWorldSource(player,scoped))!=WinterCrisisConditions.Truth.TRUE)return StoryReceipts.Outcome.CONDITIONS_FALSE;
   var point=new ScopedStory.Position(player.getServerWorld().getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ(),player.getYaw(),player.getPitch());
   var result=ScopedStory.apply(store,player.getUuid(),event,point);
   if(result==StoryCommitTransaction.Result.COMMITTED&&event.trigger()!=null){
    try{TriggerAuthority.committed(player,key);}
    catch(RuntimeException failure){LOG.warn("Committed trigger could not dispatch its continuation",failure);}
   }
   // A delivery error does not turn a committed transaction into a rejected one.
   try{if(result==StoryCommitTransaction.Result.COMMITTED)WhiteoutNetwork.syncAll(player.getServer());else WhiteoutNetwork.sync(player);}
   catch(RuntimeException failure){LOG.warn("Story state committed/read but snapshot delivery failed",failure);}
   return StoryReceipts.Outcome.valueOf(result.name());
  }catch(Exception failure){denied(player,failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());return StoryReceipts.Outcome.DENIED;}
 }
}
