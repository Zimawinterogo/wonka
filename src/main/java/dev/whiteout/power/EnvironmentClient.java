package dev.whiteout.power;

import dev.whiteout.Zone;
import dev.whiteout.Whiteout;
import dev.whiteout.client.WeatherState;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Client-side effects; no new weather renderer or shader dependency is introduced. */
public final class EnvironmentClient {
 private static final Set<String> OLD=new HashSet<>();
 private static final Map<String,Integer> OLD_RATES=new HashMap<>();
 private static final Set<String> MUTED_OLD_SOUNDS=new HashSet<>();
 private static boolean cold=true,creaks=true,radio=true;
 private static int synchronizedWindStrength=-1;
 public static int windStrengthPercent(){return synchronizedWindStrength;}
 private static float opening, audibleStorm;
 private static int tick,creakDelay,radioDelay,glassDelay;
 private static net.minecraft.world.World lastWorld;
 private static String lastOldZone="";
 private static final Random RANDOM=new Random();
 private EnvironmentClient(){}
 public static boolean isOld(String id){return OLD.contains(id);}
 public static void setOldLocal(String id,boolean enabled){if(enabled)OLD.add(id);else OLD.remove(id);}
 public static boolean oldSoundEnabled(String zone){return !MUTED_OLD_SOUNDS.contains(zone);}
 public static boolean oldSoundsGloballyEnabled(){return creaks;}
 public static int oldSoundInterval(String zone){return OLD_RATES.getOrDefault(zone,30);}
 public static void setOldSoundLocal(String zone,boolean sound,int rate){
  OLD_RATES.put(zone,rate);
  if(sound)MUTED_OLD_SOUNDS.remove(zone);else MUTED_OLD_SOUNDS.add(zone);
 }
 public static void initialize(){
  registerRateReceiver();
  ClientPlayNetworking.registerGlobalReceiver(EnvironmentNetwork.MutedOldSound.ID,(packet,context)->context.client().execute(()->{
   if(packet.zone().length()<=96)MUTED_OLD_SOUNDS.add(packet.zone());
  }));
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->clear());
  ClientPlayNetworking.registerGlobalReceiver(EnvironmentNetwork.OldSheltersChunk.ID,(packet,context)->context.client().execute(()->{
   if(!packet.zoneIds().isBlank())OLD.addAll(Arrays.asList(packet.zoneIds().split("\\n")));
  }));
  ClientPlayNetworking.registerGlobalReceiver(EnvironmentNetwork.Settings.ID,(packet,context)->context.client().execute(()->{
   OLD.clear();OLD_RATES.clear();MUTED_OLD_SOUNDS.clear();if(!packet.oldZoneIds().isBlank())OLD.addAll(Arrays.asList(packet.oldZoneIds().split("\\n")));
   cold=packet.cold();creaks=packet.creaks();radio=packet.staticNoise();
   synchronizedWindStrength=Math.max(0,Math.min(100,packet.windStrengthPercent()));
   GlassFrostOverlay.setEnabled(packet.windowFrost());
  }));
 }
 public static void registerRateReceiver(){
  ClientPlayNetworking.registerGlobalReceiver(EnvironmentNetwork.OldSoundRate.ID,(packet,context)->context.client().execute(()->{
   if(packet.zone().length()<=96 && packet.seconds()>=8 && packet.seconds()<=300)
    OLD_RATES.put(packet.zone(),packet.seconds());
  }));
 }
 private static void clearTransient(){opening=0;audibleStorm=0;tick=0;creakDelay=0;radioDelay=0;glassDelay=0;lastOldZone="";}
 public static void clear(){OLD.clear();OLD_RATES.clear();MUTED_OLD_SOUNDS.clear();cold=true;creaks=true;radio=true;synchronizedWindStrength=-1;lastWorld=null;clearTransient();}
 /** Called at the end of base WeatherState.tick, BEFORE the survival meter calculates cold. */
 public static void applyShelter(){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(mc.world==null||mc.player==null||WeatherState.shelterZone==null){opening=0;return;}
  if(++tick%20==0){
   ShelterExposure.Sample sample=ShelterExposure.measure(mc.world,mc.player.getEyePos());
   opening=sample.opening();
  }
  if(cold)WeatherState.shelter=ShelterExposure.effectiveProtection(WeatherState.shelter,opening);
 }
 private static void play(MinecraftClient mc,String name,float volume,float pitch){
  // Existing Whiteout positional atmosphere samples stay unmodified in the merged JAR.
  mc.getSoundManager().play(PositionedSoundInstance.ambient(SoundEvent.of(PowerDevices.id(name)),pitch,volume));
 }
 private static double soundAngle;
 /** At most 12 short rays, only when a rare sound is due; never loads chunks. */
 private static void playBuildingSound(MinecraftClient mc,Zone zone,float strength){
  BlockPos eye=BlockPos.ofFloored(mc.player.getEyePos());
  if(mc.world.isSkyVisible(eye))return;
  boolean wind=RANDOM.nextFloat()<.25f+.35f*strength;
  soundAngle+=2.399963229728653; // Rotate the starting direction between events.
  for(int ray=0;ray<12;ray++){
   double angle=soundAngle+ray*Math.PI/6;
   for(int distance=2;distance<=10;distance++){
    BlockPos at=BlockPos.ofFloored(mc.player.getX()+Math.cos(angle)*distance,
     mc.player.getEyeY()+(ray%3)*.6,mc.player.getZ()+Math.sin(angle)*distance);
    if(!mc.world.isChunkLoaded(at))break;
    var state=mc.world.getBlockState(at);
    if(state.getCollisionShape(mc.world,at).isEmpty())continue;
    String dimension=mc.world.getRegistryKey().getValue().toString();
    if(!zone.contains(at.getX()+.5,at.getY()+.5,at.getZ()+.5,dimension))break;
    boolean wooden=state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
      ||state.isIn(net.minecraft.registry.tag.BlockTags.PLANKS)
      ||state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_DOORS)
      ||state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_STAIRS);
    if(!wind&&!wooden)break;
    String sound=wind?"old_window_rattle":"old_house_creak";
    mc.getSoundManager().play(new PositionedSoundInstance(SoundEvent.of(PowerDevices.id(sound)),
     net.minecraft.sound.SoundCategory.AMBIENT,AtmosphereSoundPolicy.creakVolume(strength,distance),.85f+RANDOM.nextFloat()*.2f,
     net.minecraft.util.math.random.Random.create(),at));
    return;
   }
  }
 }
 public static void tick(MinecraftClient mc){
  if(mc.player==null||mc.world==null){clearTransient();lastWorld=null;return;}
  if(lastWorld!=mc.world){clearTransient();lastWorld=mc.world;}
  // A paused singleplayer world must not accumulate transient particles.
  if(mc.isPaused())return;
  if(creakDelay>0)creakDelay--;if(radioDelay>0)radioDelay--;if(glassDelay>0)glassDelay--;
  Zone z=WeatherState.shelterZone;
  String oldNow=z!=null&&OLD.contains(z.id)?z.id:"";
  // Smooth storm strength for sound only; the weather renderer and cold logic
  // continue using their instantaneous measurements unchanged.
  audibleStorm=AtmosphereSoundPolicy.smoothStorm(audibleStorm,
   Math.max(WeatherState.storm,ExteriorWeatherFeed.storm()));
  float buildingStorm=audibleStorm;
  if(!oldNow.equals(lastOldZone)){
   creakDelay=Math.max(creakDelay,BuildingSoundPolicy.delayTicks(OLD_RATES.getOrDefault(oldNow,30),buildingStorm,RANDOM.nextFloat()));
   lastOldZone=oldNow;
  }
  boolean storm=audibleStorm>.5f;
  if(creaks&&z!=null&&z.enabled&&!oldNow.isEmpty()&&!MUTED_OLD_SOUNDS.contains(z.id)&&buildingStorm>.05f&&creakDelay==0){
   creakDelay=BuildingSoundPolicy.delayTicks(OLD_RATES.getOrDefault(z.id,30),buildingStorm,RANDOM.nextFloat());
   playBuildingSound(mc,z,buildingStorm);
  }
  // A radio in a player's hands picks up additional interference in dense blizzards.
  if(storm&&radio&&radioDelay==0&&(mc.player.getMainHandStack().isOf(Whiteout.RADIO)||mc.player.getOffHandStack().isOf(Whiteout.RADIO))){
   play(mc,"radio_wind_static",AtmosphereSoundPolicy.radioVolume(audibleStorm),.8f+RANDOM.nextFloat()*.3f);
   radioDelay=26+RANDOM.nextInt(48);
  }
  // Aiming at the new wall thermometer gives a numeric readout without opening a GUI.
  if(glassDelay==0&&mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&mc.world.getBlockState(hit.getBlockPos()).isOf(PowerDevices.THERMOMETER)){
   glassDelay=20;
   mc.player.sendMessage(net.minecraft.text.Text.translatable("message.whiteout.thermometer_hint"),true);
  }
 }
}
