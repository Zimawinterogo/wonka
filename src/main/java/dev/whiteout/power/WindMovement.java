package dev.whiteout.power;

import dev.whiteout.Zone;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import static net.minecraft.server.command.CommandManager.literal;

/** Server-authoritative, direction-sensitive headwind resistance.
 * Uses the synced movement-speed attribute: no forced velocity, knockback or client mixins. */
public final class WindMovement {
 private WindMovement(){}
 private static final Identifier MODIFIER_ID=Identifier.of("whiteout","blizzard_headwind");
 private record Sample(ServerWorld world,Vec3d pos){}
 private static final Map<UUID,Sample> LAST=new HashMap<>();
 private static final Map<UUID,String> STATUS=new HashMap<>();
 private static int ticks;
 private static final int INTERVAL=5; // 0.25s: less lag than the previous 0.5s sampling

 private static void apply(ServerPlayerEntity player,double resistance){
  EntityAttributeInstance speed=player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
  if(speed==null)return;
  // Quantize to 1% increments to avoid excess attribute-sync traffic and FOV flicker.
  double penalty=Math.round(Math.max(0,Math.min(WindMovementPolicy.MAX_RESISTANCE,resistance))*100d)/100d;
  EntityAttributeModifier previous=speed.getModifier(MODIFIER_ID);
  if(previous!=null&&Math.abs(previous.value()+penalty)<.0001)return;
  if(previous!=null)speed.removeModifier(MODIFIER_ID);
  if(penalty>.001)speed.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID,-penalty,
    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
 }
 private static Zone weatherAtPlayer(ServerWorld world,ServerPlayerEntity player){
  // The renderer evaluates weather at camera/eye level, not at the feet. Zone Y
  // bounds can contain the camera but not the block beneath the player's boots.
  BlockPos eye=BlockPos.ofFloored(player.getEyePos());
  Zone zone=WorldAtmosphere.weather(world,eye);
  return zone!=null?zone:WorldAtmosphere.weather(world,player.getBlockPos());
 }
 private static void tick(net.minecraft.server.MinecraftServer server){
  if(++ticks%INTERVAL!=0)return;
  Set<UUID> present=new HashSet<>();
  for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){
   UUID id=player.getUuid();present.add(id);
   ServerWorld world=player.getServerWorld();Vec3d pos=player.getPos();
   Sample previous=LAST.put(id,new Sample(world,pos));
   if(previous==null||previous.world()!=world){apply(player,0);STATUS.put(id,"ожидание следующего измерения");continue;}
   Vec3d displacement=pos.subtract(previous.pos());
   if(!WorldAtmosphere.get(server).windMovement){apply(player,0);STATUS.put(id,"отключено настройкой wind-movement");continue;}
   if(player.isSpectator()||player.isCreative()||player.getAbilities().flying||player.hasVehicle()||player.isTouchingWater()){
    apply(player,0);STATUS.put(id,"режим игрока / полёт / транспорт / вода");continue;
   }
   if(displacement.horizontalLengthSquared()>25){apply(player,0);STATUS.put(id,"телепортация / слишком большой скачок позиции");continue;}
   BlockPos feet=player.getBlockPos();
   if(!world.isSkyVisible(feet.up()) || WorldAtmosphere.shelter(world,feet)!=null ||
      WorldAtmosphere.shelter(world,BlockPos.ofFloored(player.getEyePos()))!=null){
    apply(player,0);STATUS.put(id,"игрок в помещении или под укрытием");continue;
   }
   Zone zone=weatherAtPlayer(world,player);
   // Both systems read the same deterministic field, including gust and weather multiplier.
   // With no weather zone there can still be a gentle outdoor breeze and ground snow.
   long time=world.getTime();
   int base=WorldAtmosphere.get(server).windStrengthPercent;
   double snow=zone==null?0:zone.blizzard?1:zone.snow;
   boolean blizzard=zone!=null&&zone.blizzard;
   double strength=SharedWindField.strength(time,base/100.0,blizzard,snow);
   double windX=SharedWindField.directionX(time),windZ=SharedWindField.directionZ(time);
   double resistance=WindMovementPolicy.resistance(displacement.x,displacement.z,windX,windZ,strength);
   apply(player,resistance);
   String context=blizzard?"метель":zone==null?"вне зон":"погодная зона";
   if(strength==0)STATUS.put(id,"общий ветер: штиль (0%), "+context);
   else if(displacement.horizontalLengthSquared()<.0001)STATUS.put(id,"общий ветер "+Math.round(strength*100)+"%, "+context+"; игрок стоит");
   else STATUS.put(id,String.format(Locale.ROOT,"общий ветер %d%%, %s; встречное сопротивление %.1f%%",
     Math.round(strength*100),context,resistance*100d));
  }
  LAST.keySet().retainAll(present);
  STATUS.keySet().retainAll(present);
 }
 private static int showStatus(ServerCommandSource source){
  if(!(source.getEntity() instanceof ServerPlayerEntity player))return 0;
  String status=STATUS.getOrDefault(player.getUuid(),"ещё нет измерений (подожди секунду)");
  EntityAttributeInstance speed=player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
  EntityAttributeModifier mod=speed==null?null:speed.getModifier(MODIFIER_ID);
  int applied=mod==null?0:(int)Math.round(-mod.value()*100);
  source.sendFeedback(()->Text.literal("Whiteout ветер: "+status+" | модификатор скорости: -"+applied+"%"),false);
  return 1;
 }
 public static void register(){
  ServerTickEvents.END_SERVER_TICK.register(WindMovement::tick);
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->
   dispatcher.register(literal("whiteout-wind").then(literal("status")
    .requires(source->source.getEntity() instanceof ServerPlayerEntity)
    .executes(ctx->showStatus(ctx.getSource())))));
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{
   for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())apply(p,0);
   LAST.clear();STATUS.clear();ticks=0;
  });
 }
}
