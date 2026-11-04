package dev.whiteout.power;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

/** Auto portraits drawn into original Whiteout DialogueScreen bubble avatar tiles. */
public final class StoryPortraits {
 private StoryPortraits(){}
 private static Class<?> bubbleType;
 private static Method speakerMethod,avatarMethod;
 private static Entity cachedNpc;
 private static String cachedKey="";
 private static long nextScan;
 private static Object lastWorld;
 private static String hintSpeaker="";
 private static UUID hintUuid;
 public static void clearWorld(){
  cachedNpc=null;cachedKey="";nextScan=0;lastWorld=null;hintSpeaker="";hintUuid=null;
 }
 public static void tick(MinecraftClient mc){
  if(lastWorld!=null&&lastWorld!=mc.world)clearWorld();
 }
 public static void hintNpc(String speaker,List<String> ids){
  hintSpeaker=speaker;hintUuid=null;cachedKey="";cachedNpc=null;
  if(ids==null||ids.size()!=1)return;
  try {hintUuid=UUID.fromString(ids.getFirst());}catch(IllegalArgumentException ignored){}
 }

 /**
  * Called IN PLACE OF DialogueScreen's original avatar-letter renderer. It
  * draws either the real portrait or the old initial, never both. The user's
  * speaker label beside the dialogue stays untouched.
  */
 public static void drawAvatar(DrawContext ctx,TextRenderer font,String initial,
                               int initialX,int initialY,int initialColor,
                               Object bubble,Entity directNpc,int tileX,int tileY){
  if(!dev.whiteout.power.lobby.PersonalScreen.prefs().portraits()||!drawPortrait(ctx,bubble,directNpc,tileX,tileY)){
   // Missing skin or unloaded NPC: preserve the game's readable fallback.
   ctx.drawCenteredTextWithShadow(font,initial,initialX,initialY,initialColor);
  }
 }

 private static boolean drawPortrait(DrawContext ctx,Object bubble,Entity directNpc,int tileX,int tileY){
  var mc=MinecraftClient.getInstance();
  if(mc.world==null||mc.player==null||bubble==null)return false;
  try{
   if(bubbleType!=bubble.getClass()){
    bubbleType=bubble.getClass();
    speakerMethod=bubbleType.getDeclaredMethod("speaker");speakerMethod.setAccessible(true);
    avatarMethod=bubbleType.getDeclaredMethod("avatar");avatarMethod.setAccessible(true);
   }
   String speaker=(String)speakerMethod.invoke(bubble);
   String avatar=(String)avatarMethod.invoke(bubble);
   if(speaker==null)speaker="";
   if(avatar==null)avatar="";
   // Same original 32x32 portrait tile. The inner face is 24x24 px,
   // integer-scaled from the Minecraft skin and aligned within the frame.
   final int innerX=tileX+2,innerY=tileY+2;
   final int faceX=tileX+4,faceY=tileY+4,faceSize=24;
   if(isPlayer(speaker,avatar,mc)){
    ctx.fill(innerX,innerY,innerX+28,innerY+28,0xFF22262E);
    PlayerSkinDrawer.draw(ctx,mc.player.getSkinTextures(),faceX,faceY,faceSize);
    return true;
   }
   Entity npc=resolve(mc,directNpc,speaker,avatar);
   if(npc instanceof AbstractClientPlayerEntity player){
    ctx.fill(innerX,innerY,innerX+28,innerY+28,0xFF22262E);
    PlayerSkinDrawer.draw(ctx,player.getSkinTextures(),faceX,faceY,faceSize);
    return true;
   }
   if(npc instanceof LivingEntity living){
    ctx.fill(innerX,innerY,innerX+28,innerY+28,0xFF22262E);
    ctx.enableScissor(faceX,faceY,faceX+faceSize,faceY+faceSize);
    try{
     InventoryScreen.drawEntity(ctx,tileX-30,tileY-32,tileX+61,tileY+79,40,0,0,0,living);
    }finally{ctx.disableScissor();}
    return true;
   }
  }catch(Exception | LinkageError failure){
   // The fallback initial still works when an optional NPC renderer fails.
  }
  return false;
 }
 private static boolean isPlayer(String name,String avatar,MinecraftClient mc){
  String n=name.trim().toLowerCase(Locale.ROOT),a=avatar.trim().toLowerCase(Locale.ROOT);
  return n.equals("я")||n.equals("me")||n.equals("player")||
   n.equals(mc.player.getName().getString().toLowerCase(Locale.ROOT))||
   a.equals("player")||a.equals("self")||a.equals("я");
 }
 private static Entity resolve(MinecraftClient mc,Entity direct,String name,String avatar){
  if(direct!=null&&direct.isAlive())return direct;
  String key=name+"|"+avatar;
  if(mc.world!=lastWorld){cachedNpc=null;cachedKey="";lastWorld=mc.world;}
  if(Objects.equals(key,cachedKey)&&mc.world.getTime()<nextScan)
   return cachedNpc!=null&&cachedNpc.isAlive()?cachedNpc:null;
  cachedNpc=null;cachedKey=key;nextScan=mc.world.getTime()+30;
  UUID id=null;
  try{id=UUID.fromString(avatar.startsWith("uuid:")?avatar.substring(5):avatar);}catch(IllegalArgumentException ignored){}
  final UUID uuid=id!=null?id:(name.equals(hintSpeaker)?hintUuid:null);
  // Read ONLY client-loaded nearby entities; no chunk scans or loads.
  Entity unique=null;
  boolean ambiguous=false;
  for(Entity e:mc.world.getOtherEntities(mc.player,new Box(mc.player.getBlockPos()).expand(48),candidate->
   (uuid!=null&&uuid.equals(candidate.getUuid()))||
   (uuid==null&&(candidate.hasCustomName()||candidate instanceof AbstractClientPlayerEntity)
    &&candidate.getName().getString().equalsIgnoreCase(name)))){
   if(uuid!=null){cachedNpc=e;break;}
   if(unique!=null){ambiguous=true;break;} // never guess between NPCs with identical names
   unique=e;
  }
  if(uuid==null&&!ambiguous)cachedNpc=unique;
  return cachedNpc;
 }
}
