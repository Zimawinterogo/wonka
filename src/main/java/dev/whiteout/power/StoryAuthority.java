package dev.whiteout.power;

import com.google.gson.*;
import dev.whiteout.Store;
import dev.whiteout.net.*;
import java.nio.charset.StandardCharsets;
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
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{BUDGETS.remove(h.getPlayer());WARNINGS.remove(h.getPlayer());});
  ServerLifecycleEvents.SERVER_STOPPED.register(s->{CATALOGS.remove(s);BUDGETS.clear();WARNINGS.clear();});
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("whiteout")
   .then(literal("story-authority").requires(source->source.hasPermissionLevel(2))
    .then(literal("status").executes(c->{
     var catalog=catalog(c.getSource().getServer());
     c.getSource().sendFeedback(()->Text.literal("Story authority: events="+catalog.size()+", invalid="+catalog.invalid()+", error="+catalog.error()),false);return catalog.invalid()?0:1;
    }))
    .then(literal("reload").executes(c->{
     CATALOGS.remove(c.getSource().getServer());var catalog=catalog(c.getSource().getServer());
     c.getSource().sendFeedback(()->Text.literal("Story authority reloaded: events="+catalog.size()+", invalid="+catalog.invalid()),false);return catalog.invalid()?0:1;
    })))));
 }
 private static StoryEventCatalog catalog(MinecraftServer server){
  return CATALOGS.computeIfAbsent(server,s->new StoryEventCatalog(s.getSavePath(WorldSavePath.ROOT).resolve("whiteout-story-events.json")));
 }
 private static void denied(ServerPlayerEntity player,String reason){
  long now=Integer.toUnsignedLong(player.getServer().getTicks());Long previous=WARNINGS.get(player);
  if(previous!=null&&now>=previous&&now-previous<200)return;
  WARNINGS.put(player,now);
  player.sendMessage(Text.translatable("story.whiteout.request_denied"),false);
  LOG.warn("Rejected story/editor request from {}: {}",player.getUuid(),reason);
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
   if(!player.isAlive()||player.isSpectator()||packet.data()==null||packet.data().length>8192)throw new IllegalArgumentException("request");
   JsonObject request=JsonParser.parseString(new String(packet.data(),StandardCharsets.UTF_8)).getAsJsonObject();
   JsonElement key=request.get("key");
   if(key==null||!key.isJsonPrimitive()||!key.getAsJsonPrimitive().isString()||key.getAsString().length()>128)throw new IllegalArgumentException("key");
   StoryEventCatalog definitions=catalog(player.getServer());
   if(definitions.invalid())throw new IllegalArgumentException("invalid server catalog: "+definitions.error());
   StoryEventCatalog.Event event=definitions.find(key.getAsString());
   if(event==null)throw new IllegalArgumentException("unregistered story key");
   if(!event.area().contains(player.getServerWorld().getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ()))throw new IllegalArgumentException("outside event area");
   Store store=Store.get(player.getServer());
   if(store.error!=null)throw new IllegalStateException("story store is read-only");
   var result=StoryCommitTransaction.apply(store.data.storyVariables,store.data.completedEvents,event,(variables,completed)->{
    var oldVariables=store.data.storyVariables;var oldCompleted=store.data.completedEvents;
    store.data.storyVariables=variables;store.data.completedEvents=completed;
    try{store.save();}
    catch(Exception failure){store.data.storyVariables=oldVariables;store.data.completedEvents=oldCompleted;throw failure;}
   });
   if(result==StoryCommitTransaction.Result.COMMITTED)WhiteoutNetwork.syncAll(player.getServer());
  }catch(Exception failure){denied(player,failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
  return false;
 }
}
