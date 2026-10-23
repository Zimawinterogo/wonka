package dev.whiteout.power;

import java.util.List;
import dev.whiteout.client.DialogueManager;
import dev.whiteout.client.DialogueScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Four meaningful goals only; conversations reuse Whiteout's non-modal dialogue HUD. */
public final class LakeStoryClient {
 private static String currentObjective="";
 private static int age;
 private LakeStoryClient(){}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(LakeStoryPackets.Story.ID,(message,context)->context.client().execute(()->receive(message)));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{currentObjective="";age=0;});
  ClientTickEvents.END_CLIENT_TICK.register(mc->{
   if(mc.player==null||mc.world==null||mc.isPaused())return;
   if(!currentObjective.isEmpty()&&++age>=180){currentObjective="";age=0;}
  });
  HudRenderCallback.EVENT.register((ctx,counter)->{
   MinecraftClient mc=MinecraftClient.getInstance();
   if(mc.player==null||mc.world==null||mc.options.hudHidden||mc.currentScreen!=null||currentObjective.isEmpty())return;
   drawGoal(ctx,mc);
  });
 }
 private static void receive(LakeStoryPackets.Story packet){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(packet.kind()==LakeStoryPackets.RESET){currentObjective="";age=0;StoryDialogueHud.clear();return;}
  if(packet.kind()==LakeStoryPackets.CLEAR){currentObjective="";age=0;return;}
  if(packet.kind()==LakeStoryPackets.OBJECTIVE){currentObjective=packet.text();age=0;return;}
  if(packet.kind()!=LakeStoryPackets.DIALOGUE||mc.player==null)return;
  // Render the user's exact words through the ORIGINAL Whiteout dialogue system.
  var line=new DialogueManager.Line("Я",packet.text(),"Я",0xFFE1E8F3,"player","",List.of(),null);
  var script=new DialogueManager.Script("lake_scene_line",List.of(),List.of(),List.of(line));
  StoryDialogueHud.route(mc,new DialogueScreen(script,null,140));
 }
 private static void drawGoal(DrawContext context,MinecraftClient mc){
  int screenWidth=mc.getWindow().getScaledWidth();
  int textWidth=mc.textRenderer.getWidth(currentObjective);
  int width=Math.max(186,Math.min(285,textWidth+38));
  int x=screenWidth-width-13, y=24;
  // Dark glass effect: enough contrast for snow and Iris shaders without a huge quest overlay.
  context.fill(x,y,x+width,y+38,0xB8101C24);
  context.fill(x,y,x+2,y+38,0xFF8EBDD1);
  context.drawTextWithShadow(mc.textRenderer,"WHITEOUT  /  ЦЕЛЬ",x+12,y+5,0xA8CBDD);
  context.drawTextWithShadow(mc.textRenderer,currentObjective,x+12,y+20,0xECF4F8);
 }
}
