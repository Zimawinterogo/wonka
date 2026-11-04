package dev.whiteout.power;

import java.util.HashMap;
import java.util.List;
import dev.whiteout.client.DialogueManager;
import dev.whiteout.client.DialogueScreen;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

public final class PowerClient implements ClientModInitializer {
 // Positions alone are not unique across worlds/dimensions.
 private record EngineKey(net.minecraft.world.World world,BlockPos pos){}
 private static final TransientInstances<EngineKey,DeviceEntity,EngineSound> sounds=new TransientInstances<>();
 private static boolean outageDialoguePending=false;
 private static int fuseReactionPending=-1;

 private static void showOutageDialogue(MinecraftClient mc){
  // Reuse Whiteout's own script/line model and actual DialogueScreen, not chat or a new HUD.
  var line=new DialogueManager.Line("Я",DialogueCatalog.OUTAGE_TEXT,"Я",0xFFE1E8F3,"player","",List.of(),null);
  // If the map author wrote this dialogue in Whiteout's editor, use that script instead.
  if(DialogueManager.findById("whiteout_power_outage")!=null){
   DialogueManager.openDirector("whiteout_power_outage",140);
  }else{
   var script=new DialogueManager.Script("whiteout_power_outage",List.of(),List.of(),List.of(line));
   StoryDialogueHud.route(mc,new DialogueScreen(script,null,140));
  }
 }

 private static void showFuseReaction(MinecraftClient mc,int variant){
  String dialogue="whiteout_fuse_failure";
  if(DialogueManager.findById(dialogue)!=null){DialogueManager.openDirector(dialogue,140);return;}
  String lineText=Text.translatable("message.whiteout.fuse_reaction_"+Math.floorMod(variant,3)).getString();
  var line=new DialogueManager.Line("Я",lineText,"Я",0xFFE1E8F3,"player","",List.of(),null);
  var script=new DialogueManager.Script(dialogue,List.of(),List.of(),List.of(line));
  StoryDialogueHud.route(mc,new DialogueScreen(script,null,140));
 }

 @Override public void onInitializeClient(){
  dev.whiteout.power.lobby.BoardScreen.register();
  StoryAuthorityClient.register();
  MigratedTriggerClient.register();
  EditorClient.initialize();
  ZoneSettingsScreen.initialize();
  dev.whiteout.power.backpack.BackpackClient.initialize();
  BlockEntityRendererRegistry.register(PowerDevices.ENTITY,DeviceRenderer::new);
  GeneratorModels.register();
  dev.whiteout.power.barricade.BarricadeModels.register();
  EnvironmentClient.initialize();
  PowerDiagnosticsClient.register();
  PassportClient.register();
  DialogueRouteClient.register();
  GlassFrostOverlay.register();
  WeatherDepth.register();
  GroundSnow.initialize();
  DoorGateClient.registerClient();
  ZoneFoldersClient.initialize();
  ClientPlayNetworking.registerGlobalReceiver(PowerNetwork.FuseSettings.ID,(p,c)->c.client().execute(()->{
   if(c.client().currentScreen instanceof FuseSettingsScreen screen)screen.update(p);
   else c.client().setScreen(new FuseSettingsScreen(p));
  }));
  ClientPlayNetworking.registerGlobalReceiver(PowerSwitches.Snapshot.ID,(packet,context)->context.client().execute(()->{
   if(context.client().currentScreen instanceof PowerSwitchScreen screen)screen.update(packet);
   else context.client().setScreen(new PowerSwitchScreen(packet));
  }));
  net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(PowerDevices.POWER_SWITCH,net.minecraft.client.render.RenderLayer.getCutout());
  // Both frosted glass blocks must use the translucent layer (including with Iris).
  net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(dev.whiteout.Whiteout.RIME_GLASS,net.minecraft.client.render.RenderLayer.getTranslucent());
  net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(PowerDevices.RIME_GLASS_PANE,net.minecraft.client.render.RenderLayer.getTranslucent());
  net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(PowerDevices.FROST_WINDOW,net.minecraft.client.render.RenderLayer.getTranslucent());
  net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(PowerDevices.LINKED_LANTERN,net.minecraft.client.render.RenderLayer.getCutout());
  net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(PowerDevices.THERMOMETER,net.minecraft.client.render.RenderLayer.getCutout());
  ClientPlayNetworking.registerGlobalReceiver(PowerNetwork.OpenDialogues.ID,(v,c)->c.client().execute(()->{
   if(c.client().player!=null)c.client().setScreen(new DialogueCatalogScreen());
  }));
  ClientPlayNetworking.registerGlobalReceiver(PowerNetwork.PlayDialogue.ID,(v,c)->c.client().execute(()->{
   if(c.client().player==null||v.id()==null||(!v.id().matches("[a-zA-Z0-9_:-]{1,64}")&&!DialogueRoute.reference(v.id())))return;
   if(v.signal())dev.whiteout.client.director.DirectorTriggerManager.signal("wdlg:"+v.id());
   else DialogueManager.openDirector(v.id(),140);
  }));
  StoryDialogueHud.register();
  LakeStoryClient.register();
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->StoryPortraits.clearWorld());
  ClientPlayNetworking.registerGlobalReceiver(PowerNetwork.Starter.ID,(v,c)->c.client().execute(()->{
   if(c.client().currentScreen instanceof StarterScreen s&&s.pos.equals(v.pos()))s.update(v);
   else if(v.status()==0)c.client().setScreen(new StarterScreen(v));
  }));
  ClientPlayNetworking.registerGlobalReceiver(PowerNetwork.OutageDialogue.ID,(v,c)->c.client().execute(()->{
   outageDialoguePending=true; // Do not interrupt other narrative/dialogue screens.
  }));
  ClientPlayNetworking.registerGlobalReceiver(PowerNetwork.FuseFailure.ID,(v,c)->c.client().execute(()->{
   if(c.client().player==null)return;
   // Event can also trigger an editor-created Director scene or saved dialogue.
   boolean triggered=dev.whiteout.client.director.DirectorTriggerManager.signal("wdlg:whiteout_fuse_failure");
   // An editor-bound signal owns the scene. Never play the built-in reaction a second time.
   if(!triggered)fuseReactionPending=Math.floorMod(v.variant(),3);
  }));
  DeviceEntity.clientTicker=e->{
   if(e.kind()!=0&&e.kind()!=3)return;
   boolean started=e.clientMotion.tick(e.active(),e.kind()==3);
   GeneratorParticles.tick(e,started);
   if(!e.clientMotion.loopReady())return;
   var mc=MinecraftClient.getInstance();
   var key=new EngineKey(e.getWorld(),e.getPos().toImmutable());
   sounds.acquire(key,e,EngineSound::isDone,()->{
    EngineSound sound=new EngineSound(e);mc.getSoundManager().play(sound);return sound;
   },sound->mc.getSoundManager().stop(sound));
  };
  ClientTickEvents.END_CLIENT_TICK.register(mc->{
   StoryPortraits.tick(mc);
   EnvironmentClient.tick(mc);
   if(mc.world==null)DoorGateClient.clearClient();
   // The sound system can discard a loop on disconnect, audio-device reload or
   // an early client-world tick without marking our cached instance as done.
   // Recover only after sustained missing playback, never once per game tick.
   sounds.removeIf((key,sound)->mc.world!=sound.world||sound.isDone()
    ||!sound.world.isChunkLoaded(sound.pos)||sound.world.getBlockEntity(sound.pos)!=sound.owner||!sound.owner.active()
    ||sound.hasLostPlayback(mc),
    sound->mc.getSoundManager().stop(sound));
   StoryDialogueHud.tick(mc);
   if(mc.world==null){sounds.clear(s->mc.getSoundManager().stop(s));outageDialoguePending=false;fuseReactionPending=-1;StoryDialogueHud.clear();return;}
   if(fuseReactionPending>=0&&mc.player!=null&&mc.currentScreen==null){
    int variant=fuseReactionPending;fuseReactionPending=-1;showFuseReaction(mc,variant);
   }
   if(outageDialoguePending&&mc.player!=null&&mc.currentScreen==null){
    outageDialoguePending=false;
    showOutageDialogue(mc);
   }
  });
 }
 private static final class EngineSound extends MovingSoundInstance {
  final net.minecraft.world.World world;final BlockPos pos;final float target;final DeviceEntity owner;
  private final GeneratorSoundWatchdog playbackWatchdog=new GeneratorSoundWatchdog();
  EngineSound(DeviceEntity e){
   super(e.kind()==3?PowerDevices.OLD_GEN_LOOP:PowerDevices.ENGINE,SoundCategory.BLOCKS,Random.create());
   owner=e;world=e.getWorld();pos=e.getPos();x=pos.getX()+.5;y=pos.getY()+.5;z=pos.getZ()+.5;repeat=true;repeatDelay=0;pitch=1;
   // Each motor fades into its running loop; retain the existing target levels.
   target=e.kind()==3?1.0f:1.15f;volume=0.01f;
  }
  /** Called from the client tick even if the sound engine never started ticking this instance. */
  boolean hasLostPlayback(MinecraftClient mc){
   if(mc.player==null)return false;
   // Do not repeatedly attempt inaudible distant loops; recover when the player returns.
   double radius=owner.kind()==3?48:40;
   boolean listenerNear=mc.player.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=radius*radius;
   return playbackWatchdog.shouldRecover(listenerNear,mc.getSoundManager().isPlaying(this),mc.isPaused());
  }
  @Override public void tick(){
   if(MinecraftClient.getInstance().world!=world||!world.isChunkLoaded(pos)||world.getBlockEntity(pos)!=owner||!owner.active()){setDone();return;}
   volume=Math.max(.01f,target*owner.clientMotion.loopVolume(owner.kind()==3));
  }
 }
}
