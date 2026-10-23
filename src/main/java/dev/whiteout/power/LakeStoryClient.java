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
import net.minecraft.text.Text;

/** Non-modal, frame-interpolated goal cards. Dialogue ownership is chapter-local. */
public final class LakeStoryClient {
 private static final String SCRIPT="lake_scene_line";
 private static String current="",pending="";
 private static int ticks,exitTick=-1;
 private static float lastDelta;
 private LakeStoryClient(){}
 private static void clear(){current="";pending="";ticks=0;exitTick=-1;lastDelta=0;}
 private static void start(String goal){current=goal;pending="";ticks=0;exitTick=-1;lastDelta=0;}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(LakeStoryPackets.Story.ID,(message,context)->context.client().execute(()->receive(message)));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{clear();StoryDialogueHud.clearScript(SCRIPT);});
  ClientTickEvents.END_CLIENT_TICK.register(mc->{
   if(mc.player==null||mc.world==null){clear();return;}
   if(mc.isPaused()||current.isEmpty())return;
   ticks++;
   if(LakeStoryPolicy.finished(ticks/20.0)||(exitTick>=0&&ticks-exitTick>=5)){
    if(pending.isEmpty())clear();else start(pending);
   }
  });
  HudRenderCallback.EVENT.register((ctx,counter)->{
   MinecraftClient mc=MinecraftClient.getInstance();
   if(mc.player==null||mc.world==null||mc.options.hudHidden||current.isEmpty())return;
   if(!mc.isPaused())lastDelta=counter.getTickDelta(false);
   if(mc.currentScreen==null)drawGoal(ctx,mc,(ticks+lastDelta)/20.0);
  });
 }
 private static void receive(LakeStoryPackets.Story packet){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(packet.kind()==LakeStoryPackets.RESET){clear();StoryDialogueHud.clearScript(SCRIPT);return;}
  if(packet.kind()==LakeStoryPackets.CLEAR){clear();return;}
  if(packet.kind()==LakeStoryPackets.OBJECTIVE){
   String goal=packet.text();
   if(LakeStoryPolicy.description(goal).isEmpty()||goal.equals(current)||goal.equals(pending))return;
   if(current.isEmpty())start(goal);else{pending=goal;if(exitTick<0)exitTick=ticks;}
   return;
  }
  if(packet.kind()!=LakeStoryPackets.DIALOGUE||mc.player==null)return;
  var line=new DialogueManager.Line("Я",packet.text(),"Я",0xFFE1E8F3,"player","",List.of(),null);
  var script=new DialogueManager.Script(SCRIPT,List.of(),List.of(),List.of(line));
  StoryDialogueHud.route(mc,new DialogueScreen(script,null,140));
 }
 private static int color(int rgb,double opacity){return ((int)(Math.max(0,Math.min(1,opacity))*255)<<24)|(rgb&0xffffff);}
 private static void drawGoal(DrawContext context,MinecraftClient mc,double seconds){
  double interrupted=exitTick<0?1:1-LakeStoryPolicy.ease((ticks+lastDelta-exitTick)/5.0);
  double alpha=LakeStoryPolicy.opacity(seconds)*interrupted;
  if(alpha<.01)return;
  int screenWidth=mc.getWindow().getScaledWidth();
  int width=Math.min(285,Math.max(80,screenWidth-26));
  var title=mc.textRenderer.wrapLines(Text.literal(current),width-24);
  var description=mc.textRenderer.wrapLines(Text.literal(LakeStoryPolicy.description(current)),width-24);
  int height=22+(title.size()+description.size())*11;
  float x=(float)(screenWidth-width-13+(width+13)*(1-alpha));
  context.getMatrices().push();context.getMatrices().translate(x,24,0);
  context.fill(0,0,width,height,color(0x101c24,alpha*.78));
  context.fill(0,0,2,height,color(0x8ebdd1,alpha));
  int y=9;
  for(var line:title){context.drawTextWithShadow(mc.textRenderer,line,12,y,color(0xecf4f8,alpha));y+=11;}
  y+=5;
  double descAlpha=LakeStoryPolicy.descriptionOpacity(seconds)*interrupted;
  if(descAlpha>=.01)for(var line:description){context.drawTextWithShadow(mc.textRenderer,line,12,y,color(0xb6cbd6,descAlpha));y+=11;}
  context.getMatrices().pop();
 }
}
