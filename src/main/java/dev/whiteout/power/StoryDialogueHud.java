package dev.whiteout.power;

import dev.whiteout.client.DialogueManager;
import dev.whiteout.client.DialogueScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Displays Whiteout's ORIGINAL DialogueScreen on the HUD instead of installing it
 * as a modal Screen. Minecraft never loses keyboard/mouse/world interactions.
 * Interactive conversations (choices/auto-replies) still open normally.
 */
public final class StoryDialogueHud {
 private static final ArrayDeque<Entry> QUEUE=new ArrayDeque<>();
 private static Entry current;
 private static DialogueScreen display;
 private static MinecraftClient owner;
 private static int remaining;
 private static int lastWidth=-1,lastHeight=-1;
 private static boolean registered;
 private static final Field SCRIPT=field("script");
 private static final Field ENTITY=field("entity");
 private static Field field(String name){
  try {Field f=DialogueScreen.class.getDeclaredField(name);f.setAccessible(true);return f;}
  catch(ReflectiveOperationException e){throw new IllegalStateException("Whiteout DialogueScreen changed: "+name,e);}
 }
 private record Entry(DialogueManager.Line line,Entity entity,String id,List<String> npcUuids) {}
 private StoryDialogueHud(){}

 public static void register(){
  if(registered)return; registered=true;
  HudRenderCallback.EVENT.register((ctx,counter)->{
   MinecraftClient mc=MinecraftClient.getInstance();
   if(display!=null&&owner==mc&&mc.world!=null&&mc.player!=null&&mc.currentScreen==null){
    resizeIfNeeded(mc);
    // Render the original dialogue bubbles. The normal HUD is NOT a Screen,
    // therefore player movement, camera, mining and block interaction remain active.
    display.render(ctx,-999,-999,counter.getTickDelta(false));
   }
  });
 }

 /** Called from bytecode-bridged DialogueManager.open/openDirector, not openFor(NPC). */
 public static void route(MinecraftClient mc,Screen screen){
  if(!(screen instanceof DialogueScreen dialog)){mc.setScreen(screen);return;}
  try{
   DialogueManager.Script script=(DialogueManager.Script)SCRIPT.get(dialog);
   Entity entity=(Entity)ENTITY.get(dialog);
   if(script==null||script.lines().isEmpty()||hasInteraction(script)){
    mc.setScreen(screen);return;
   }
   // Enqueue all informational lines while preserving the script's actual speakers,
   // colors and avatar identifiers. Do not replace Whiteout's script model.
   if(owner!=null&&owner!=mc)clear();owner=mc;
   for(DialogueManager.Line line:script.lines())QUEUE.addLast(new Entry(line,entity,script.id(),script.npcUuids()));
   if(current==null&&mc.player!=null&&mc.world!=null)next(mc);
  }catch(ReflectiveOperationException | RuntimeException exception){
   // Safety fallback: never lose a dialogue if compatibility with a future
   // Whiteout DialogueScreen changes.
   mc.setScreen(screen);
  }
 }
 private static boolean hasInteraction(DialogueManager.Script script){
  for(DialogueManager.Line line:script.lines()){
   if(line.autoReply()!=null||!line.choices().isEmpty())return true;
  }
  return false;
 }
 private static void next(MinecraftClient mc){
  current=QUEUE.pollFirst();
  if(current==null){display=null;remaining=0;return;}
  // A huge internal hold prevents the original screen from closing the game's
  // active inventory or pause screen. We control the lifespan of HUD lines.
  DialogueManager.Script one=new DialogueManager.Script(current.id,List.of(),List.of(),List.of(current.line));
  StoryPortraits.hintNpc(current.line.speaker(),current.npcUuids);
  display=new DialogueScreen(one,current.entity,32767);
  display.init(mc,mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight());
  lastWidth=mc.getWindow().getScaledWidth();lastHeight=mc.getWindow().getScaledHeight();
  // 2 letters/tick; afterwards leave the entire sentence visible long enough.
  remaining=Math.max(50,(current.line.text().length()+1)/(2*dev.whiteout.power.lobby.PersonalScreen.prefs().letters())+55);
 }
 public static void tick(MinecraftClient mc){
  if(mc.world==null||mc.player==null){clear();return;}
  if(owner!=null&&owner!=mc){clear();return;}
  if(current==null&&!QUEUE.isEmpty()&&mc.currentScreen==null)next(mc);
  if(display==null||current==null)return;
  // When another GUI is opened, preserve the story line and resume afterwards.
  if(mc.currentScreen!=null)return;
  resizeIfNeeded(mc);
  display.tick();
  if(--remaining<=0){
   String finishedId=current.id;
   next(mc);
   if(current==null&&finishedId.equals(DialogueManager.activeId())) DialogueManager.resetSession();
  }
 }
 private static void resizeIfNeeded(MinecraftClient mc){
  int w=mc.getWindow().getScaledWidth(),h=mc.getWindow().getScaledHeight();
  if(display!=null&&(w!=lastWidth||h!=lastHeight)){
   display.resize(mc,w,h);lastWidth=w;lastHeight=h;
  }
 }
 /** Remove only entries owned by one story; preserve unrelated active/queued dialogue. */
 public static void clearScript(String id){
  QUEUE.removeIf(entry->entry.id.equals(id));
  if(current!=null&&current.id.equals(id)){
   current=null;display=null;remaining=0;
   StoryPortraits.hintNpc("",List.of());
   if(owner!=null&&owner.world!=null&&owner.player!=null&&owner.currentScreen==null)next(owner);
  }
 }
 public static void clear(){QUEUE.clear();current=null;display=null;owner=null;remaining=0;lastWidth=-1;lastHeight=-1;StoryPortraits.hintNpc("",List.of());}
}
