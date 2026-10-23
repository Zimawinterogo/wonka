package dev.whiteout.power;

import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public final class DeviceEntity extends BlockEntity {
 public static final int CAN_TICKS=36000,MAX_FUEL=CAN_TICKS*3;
 public static Consumer<DeviceEntity> clientTicker=e->{};
 public final DefaultedList<ItemStack> stacks=DefaultedList.ofSize(6,ItemStack.EMPTY);
 public final GeneratorMotion clientMotion=new GeneratorMotion();
 public int fuel;public boolean fuse;public boolean burnt;public boolean unstableFuse;
 public float fuseFailureChance=UnstableFusePolicy.FAILURE_CHANCE;
 private long nextFuseCheck;
 private UUID operator;private long epoch,expires;private int progress;
 private BlockPos supply;
 public DeviceEntity(BlockPos p,BlockState s){super(PowerDevices.ENTITY,p,s);}
 public int kind(){return ((DeviceBlock)getCachedState().getBlock()).kind;}
 public boolean active(){return getCachedState().get(DeviceBlock.ACTIVE);}
 public static void tick(World w,BlockPos p,BlockState s,DeviceEntity e){
  if(w.isClient){clientTicker.accept(e);return;}
  if(LakeStory.blocked(w,p)||LakeStory.brokenHome(w,p)){if(e.active())e.emergencyStop();}
  if(e.kind()==0||e.kind()==3){
   if(e.active()){
    if(e.fuel>0)e.fuel--;
    if(e.fuel<=0){e.setActive(false);e.stopSound();}
    if(w.getTime()%20==0)e.markDirty();
    if(e.kind()==3)GridManager.oldGeneratorTick((ServerWorld)w,e);
   }
   if(e.operator!=null){PlayerEntity player=w.getPlayerByUuid(e.operator);if(w.getTime()>e.expires||player==null||!player.isAlive()||player.squaredDistanceTo(p.toCenterPos())>36){if(player instanceof ServerPlayerEntity sp)e.send(sp,4);e.operator=null;}}
  }else if(e.kind()==1){
   if(w.getTime()%20==0)GridManager.panelTick((ServerWorld)w,e);
   if(e.unstableFuse&&e.fuse&&!e.burnt&&e.active()){
    if(e.nextFuseCheck==0)e.nextFuseCheck=w.getTime()+UnstableFusePolicy.INTERVAL_TICKS;
    if(UnstableFusePolicy.shouldCheck(e.fuse,e.burnt,true,w.getTime(),e.nextFuseCheck)){
     e.nextFuseCheck=w.getTime()+UnstableFusePolicy.INTERVAL_TICKS;
     if(UnstableFusePolicy.blows(w.random.nextFloat(),e.fuseFailureChance))e.blownUnstableFuse((ServerWorld)w);
     else e.markDirty();
    }
   }else if(e.unstableFuse&&e.nextFuseCheck!=0){e.nextFuseCheck=w.getTime()+UnstableFusePolicy.INTERVAL_TICKS;}
  }
 }

 /** Story outage: stop the motor and cancel any in-progress starter sequence, without touching fuel. */
 void emergencyStop(){
  if((kind()!=0&&kind()!=3)||world==null||world.isClient)return;
  if(operator!=null){
   PlayerEntity user=world.getPlayerByUuid(operator);
   if(user instanceof ServerPlayerEntity sp)send(sp,4);
   operator=null;
  }
  progress=0;epoch=0;expires=0;
  if(active()){
   setActive(false);
   stopSound();
  }
 }
 void setActive(boolean active){
  if(active&&(LakeStory.blocked(world,pos)||LakeStory.brokenHome(world,pos)))return;
  if(active()==active)return;
  world.setBlockState(pos,getCachedState().with(DeviceBlock.ACTIVE,active),3);
  world.updateNeighborsAlways(pos,getCachedState().getBlock());sync();
  if(world instanceof ServerWorld sw)GridManager.refresh(sw.getServer());
 }
 public void sound(SoundEvent event,float volume){world.playSound(null,pos,event,SoundCategory.BLOCKS,volume,1);}
 public void sound(SoundEvent event,float volume,float pitch){world.playSound(null,pos,event,SoundCategory.BLOCKS,volume,pitch);}
 /** Engine shutdown. The old generator has its own spin-down sample; the modern one is unchanged. */
 private void stopSound(){if(kind()==3)sound(PowerDevices.OLD_GEN_STOP,1.0f);else sound(PowerDevices.STOP,.75f);}
 private void message(PlayerEntity p,String key,Object... args){p.sendMessage(Text.translatable("message.whiteout."+key,args),true);}
 private void give(PlayerEntity p,ItemStack stack){if(!p.giveItemStack(stack))p.dropItem(stack,false);}
 public void interact(ServerPlayerEntity p,Hand hand,BlockHitResult hit){
  if(LakeStory.blocked(world,pos)||p.isSpectator()||p.squaredDistanceTo(pos.toCenterPos())>36)return;
  ItemStack held=p.getStackInHand(hand);
  if(kind()==2){
   Vec3d v=hit.getPos().subtract(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
   double localX=switch(getCachedState().get(DeviceBlock.FACING)){case NORTH->v.x;case SOUTH->-v.x;case EAST->v.z;default->-v.z;};
   int row=MathHelper.clamp((int)(v.y*3),0,2),slot=row*2+(localX>=0?1:0);
   ItemStack stored=stacks.get(slot);
   if(held.isEmpty()){
    if(!stored.isEmpty()){stacks.set(slot,ItemStack.EMPTY);give(p,stored);sound(SoundEvents.BLOCK_WOOD_PLACE,.45f);sync();}
   }else if(stored.isEmpty()){
    stacks.set(slot,held.copyWithCount(1));if(!p.isCreative())held.decrement(1);sound(SoundEvents.BLOCK_WOOD_PLACE,.45f);sync();
   }else if(ItemStack.areItemsAndComponentsEqual(held,stored)&&stored.getCount()<stored.getMaxCount()){
    stored.increment(1);if(!p.isCreative())held.decrement(1);sync();
   }else message(p,"shelf_occupied");
   return;
  }
  if(kind()==1){
   if(p.isCreative()&&p.hasPermissionLevel(2)&&p.isSneaking()&&!held.isEmpty()){
    ServerPlayNetworking.send(p,new PowerNetwork.FuseSettings(pos,Math.round(fuseFailureChance*100)));return;
   }
   if(burnt&&p.isSneaking()&&held.isEmpty()){burnt=false;fuse=false;unstableFuse=false;nextFuseCheck=0;setActive(false);sound(PowerDevices.CLICK,.65f);sync();GridManager.refresh(p.getServer());message(p,"fuse_burnt_removed");}
   else if(!fuse&&(held.isOf(PowerDevices.FUSE)||held.isOf(PowerDevices.UNSTABLE_FUSE))){fuse=true;burnt=false;unstableFuse=held.isOf(PowerDevices.UNSTABLE_FUSE);nextFuseCheck=world.getTime()+UnstableFusePolicy.INTERVAL_TICKS;if(!p.isCreative())held.decrement(1);sound(PowerDevices.CLICK,.65f);sync();GridManager.refresh(p.getServer());if(!unstableFuse)message(p,"fuse_inserted");}
   else if(fuse&&p.isSneaking()&&held.isEmpty()){ItemStack retrieved=new ItemStack(unstableFuse?PowerDevices.UNSTABLE_FUSE:PowerDevices.FUSE);fuse=false;unstableFuse=false;nextFuseCheck=0;setActive(false);give(p,retrieved);sound(PowerDevices.CLICK,.65f);sync();GridManager.refresh(p.getServer());}
   else message(p,burnt?"fuse_burnt":!fuse?"fuse_missing":active()?"panel_on":"panel_no_power");
   return;
  }
  if(held.isOf(PowerDevices.FUEL)){
   if(fuel>MAX_FUEL-CAN_TICKS){message(p,"tank_full");return;}
   fuel+=CAN_TICKS;if(!p.isCreative()){held.decrement(1);give(p,new ItemStack(PowerDevices.EMPTY_CAN));}sync();GridManager.refresh(p.getServer());sound(PowerDevices.CLICK,.65f);message(p,"fuel_added",fuel/1200);LakeStory.refueled(this);return;
  }
  if(active()){
   if(p.isSneaking()&&held.isEmpty()){setActive(false);stopSound();message(p,"generator_off");}
   else message(p,"generator_on",(fuel+1199)/1200);
   return;
  }
  if(fuel<=0){message(p,"fuel_missing");return;}
  if(operator!=null&&!operator.equals(p.getUuid())&&world.getTime()<expires){message(p,"starter_busy");return;}
  operator=p.getUuid();progress=0;epoch=world.getTime()+10;expires=world.getTime()+1200;send(p,0);
 }
 private void send(ServerPlayerEntity p,int status){ServerPlayNetworking.send(p,new PowerNetwork.Starter(pos,epoch,progress,status));}
 public void cancel(ServerPlayerEntity p){if(p.getUuid().equals(operator))operator=null;}
 public void pull(ServerPlayerEntity p){
  if(LakeStory.blocked(world,pos)||p.isSpectator()||!p.isAlive())return;
  long now=world.getTime();
  if((kind()!=0&&kind()!=3)||!p.getUuid().equals(operator)||now>expires||active()||fuel<=0||p.squaredDistanceTo(pos.toCenterPos())>36)return;
  if(now<epoch){send(p,5);return;}
  if(LakeStory.homePull(this)){
   sound(PowerDevices.OLD_GEN_FAIL,1.1f,.94f);
   epoch=now+15;
   if(LakeStory.homeAttemptComplete(this)){operator=null;send(p,4);}else send(p,2);
   return;
  }
  boolean worn=kind()==3;boolean ok=worn?WornStarterLogic.hit(now-epoch,progress):StarterLogic.hit(now-epoch,progress);progress=worn?WornStarterLogic.next(progress,ok):StarterLogic.next(progress,ok);
  if(worn){
   // Old generator: each pull plays its own starter sample (rope + turning engine), never the shared pull/fail sounds.
   if(progress==WornStarterLogic.PULLS){operator=null;setActive(true);sound(PowerDevices.OLD_GEN_START,1.1f);send(p,3);return;}
   if(ok)sound(PowerDevices.OLD_GEN_CATCH,.75f+.06f*progress,.94f+.03f*progress);else sound(PowerDevices.OLD_GEN_FAIL,.9f);
   epoch=now+10;send(p,ok?1:2);return;
  }
  sound(PowerDevices.PULL,.8f);
  if(progress==StarterLogic.PULLS){operator=null;setActive(true);sound(PowerDevices.START,.8f);send(p,3);return;}
  if(!ok)sound(PowerDevices.MODERN_GEN_FAIL,.5f);
  epoch=now+10;send(p,ok?1:2);
 }
 private void blownUnstableFuse(ServerWorld sw){
  if(!fuse||burnt||!unstableFuse)return;
  burnt=true;unstableFuse=false;nextFuseCheck=0;
  sync();GridManager.fuseFailed(sw,pos);
  sw.playSound(null,pos,net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.BLOCKS,1.1f,1.35f);
  sw.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,pos.getX()+.5,pos.getY()+.7,pos.getZ()+.5,12,.2,.25,.2,.025);
  for(ServerPlayerEntity p:sw.getPlayers())if(p.squaredDistanceTo(pos.toCenterPos())<576){
   net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,new PowerNetwork.OutageDialogue());
  }
 }
 public void sync(){if(world instanceof ServerWorld sw)WhiteoutWorldState.invalidatePower(sw.getServer());markDirty();if(world!=null&&!world.isClient)world.updateListeners(pos,getCachedState(),getCachedState(),3);}
 public void dropContents(){if(world==null||world.isClient)return;for(int i=0;i<stacks.size();i++){net.minecraft.block.Block.dropStack(world,pos,stacks.get(i));stacks.set(i,ItemStack.EMPTY);}if(fuse&&!burnt){net.minecraft.block.Block.dropStack(world,pos,new ItemStack(unstableFuse?PowerDevices.UNSTABLE_FUSE:PowerDevices.FUSE));}fuse=false;burnt=false;unstableFuse=false;nextFuseCheck=0;}
 @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);n.putInt("Fuel",fuel);n.putFloat("FuseFailureChance",fuseFailureChance);n.putBoolean("Fuse",fuse);n.putBoolean("Burnt",burnt);n.putBoolean("UnstableFuse",unstableFuse);n.putLong("FuseNextCheck",nextFuseCheck);Inventories.writeNbt(n,stacks,r);}
 @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);fuel=MathHelper.clamp(n.getInt("Fuel"),0,MAX_FUEL);fuseFailureChance=n.contains("FuseFailureChance")?n.getFloat("FuseFailureChance"):UnstableFusePolicy.FAILURE_CHANCE;if(!Float.isFinite(fuseFailureChance))fuseFailureChance=UnstableFusePolicy.FAILURE_CHANCE;fuseFailureChance=MathHelper.clamp(fuseFailureChance,0,1);fuse=n.getBoolean("Fuse");burnt=n.getBoolean("Burnt");unstableFuse=n.getBoolean("UnstableFuse");nextFuseCheck=n.getLong("FuseNextCheck");stacks.clear();Inventories.readNbt(n,stacks,r);}
 @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
 @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){return createNbt(r);}
}
