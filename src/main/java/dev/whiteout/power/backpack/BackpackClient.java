package dev.whiteout.power.backpack;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class BackpackClient {
 private static BackpackPreferences preferences;
 private BackpackClient(){}
 public static void initialize(){
  BackpackVisualClient.initialize();
  preferences=new BackpackPreferences(FabricLoader.getInstance().getConfigDir().resolve("whiteout-backpack.json"));
  preferences.load();
  if(preferences.error()!=null)LoggerFactory.getLogger("Whiteout").error("Backpack disabled; client config preserved: {}",preferences.error());
  // The existing /whiteout is server-owned. Do not shadow its permission tree.
  ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("whiteoutbackpack")
   .executes(context->status(context.getSource()))
   .then(literal("on").executes(context->set(context.getSource(),true)))
   .then(literal("off").executes(context->set(context.getSource(),false)))
   .then(literal("toggle").executes(context->set(context.getSource(),!preferences.enabled())))
   .then(literal("status").executes(context->status(context.getSource())))
   .then(literal("animation")
    .then(literal("on").executes(context->setAnimation(context.getSource(),true)))
    .then(literal("off").executes(context->setAnimation(context.getSource(),false))))));
 }
 private static int setAnimation(FabricClientCommandSource source,boolean value){
  try{
   preferences.setAnimation(value);
   source.sendFeedback(Text.translatable(value?"backpack.whiteout.animation_on":"backpack.whiteout.animation_off"));return 1;
  }catch(java.io.IOException ex){
   LoggerFactory.getLogger("Whiteout").error("Could not save backpack animation setting",ex);
   source.sendError(Text.translatable("backpack.whiteout.config_error"));return 0;
  }
 }
 private static int status(FabricClientCommandSource source){
  source.sendFeedback(Text.translatable(preferences.enabled()?"backpack.whiteout.enabled":"backpack.whiteout.disabled"));
  if(preferences.error()!=null)source.sendError(Text.translatable("backpack.whiteout.config_error"));
  return 1;
 }
 private static int set(FabricClientCommandSource source,boolean value){
  try{preferences.setEnabled(value);return status(source);}
  catch(java.io.IOException ex){
   LoggerFactory.getLogger("Whiteout").error("Could not save backpack setting",ex);
   source.sendError(Text.translatable("backpack.whiteout.config_error"));return 0;
  }
 }
 public static boolean animation(){return preferences!=null&&preferences.animation();}
 public static boolean enabledFor(MinecraftClient client){
  return preferences!=null&&client.player!=null&&client.world!=null&&
   BackpackLayout.allowed(preferences.enabled(),client.player.isCreative(),client.player.isSpectator());
 }
 public static boolean emptyCrafting(PlayerScreenHandler handler){
  if(handler.slots.size()!=BackpackLayout.SLOT_COUNT)return false;
  for(int i=0;i<5;i++)if(handler.slots.get(i).hasStack())return false;
  return true;
 }
 public static Screen replace(Screen screen){
  MinecraftClient client=MinecraftClient.getInstance();
  if(screen==null||screen.getClass()!=InventoryScreen.class||!enabledFor(client))return screen;
  if(client.player.currentScreenHandler!=client.player.playerScreenHandler)return screen;
  if(!BackpackLayout.fits(client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight()))return screen;
  if(!emptyCrafting(client.player.playerScreenHandler)){
   client.player.sendMessage(Text.translatable("backpack.whiteout.clear_crafting"),false);return screen;
  }
  return new BackpackScreen(client.player);
 }
}
