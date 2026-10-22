package dev.whiteout.power;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Linked emergency lighting: recharges on mains, illuminates on outage only. */
public final class BackupLampEntity extends BlockEntity {
 public static final int MAX_CHARGE=BackupLampPolicy.CAPACITY;
 private static final int LEGACY_MAX_CHARGE=12000;
 private int charge=MAX_CHARGE,progressRemainder,progressDuration;
 private boolean linked,gridPowered,switchEnabled=true;
 public BackupLampEntity(BlockPos pos,BlockState state){super(PowerDevices.EMERGENCY_LAMP_ENTITY,pos,state);}
 public static void tick(World world,BlockPos pos,BlockState state,BackupLampEntity entity){
  if(world.isClient)return;
  if(entity.linked){
   int previous=entity.charge,oldRemainder=entity.progressRemainder;
   var timing=BackupLampPolicy.timing(world.getDifficulty());
   int duration=entity.gridPowered?timing.chargeTicks():timing.outageTicks();
   if(entity.progressDuration>0&&entity.progressDuration!=duration)
    entity.progressRemainder=(int)((long)Math.floorMod(entity.progressRemainder,entity.progressDuration)*duration/entity.progressDuration);
   entity.progressDuration=duration;
   int[] next=BackupLampPolicy.tick(true,entity.gridPowered,entity.switchEnabled,previous,entity.progressRemainder,timing);
   entity.charge=next[0];entity.progressRemainder=next[1];
   // Persist in batches, plus exact empty/full boundaries. Fractional progress survives saves.
   if((previous!=entity.charge||oldRemainder!=entity.progressRemainder)
       &&(world.getTime()%20L==0L||entity.charge==0||entity.charge==MAX_CHARGE))entity.markDirty();
  }
  if(state.get(BackupLampBlock.LIT)!=BackupLampPolicy.lit(entity.linked,entity.gridPowered,entity.switchEnabled,entity.charge))entity.updateLight();
 }
 public void setGridPower(boolean powered,boolean enabled){
  if(linked&&gridPowered==powered&&switchEnabled==enabled)return;
  if(gridPowered!=powered){progressRemainder=0;progressDuration=0;}
  linked=true;gridPowered=powered;switchEnabled=enabled;markDirty();updateLight();
 }
 public void unlink(){linked=false;gridPowered=false;progressRemainder=0;markDirty();updateLight();}
 public int charge(){return charge;}
 boolean worldStateLinked(){return linked;}
 boolean worldStateMains(){return gridPowered;}
 boolean worldStateSwitch(){return switchEnabled;}
 public String statusTranslationKey(){
  if(!linked)return "message.whiteout.backup_lamp.unlinked";
  if(gridPowered)return charge>=MAX_CHARGE?"message.whiteout.backup_lamp.full":"message.whiteout.backup_lamp.charging";
  if(!switchEnabled)return "message.whiteout.backup_lamp.off";
  return charge>0?"message.whiteout.backup_lamp.discharging":"message.whiteout.backup_lamp.empty";
 }
 private void updateLight(){
  if(world==null||world.isClient)return;
  boolean light=BackupLampPolicy.lit(linked,gridPowered,switchEnabled,charge);
  BlockState state=getCachedState();
  if(state.isOf(PowerDevices.EMERGENCY_LAMP)&&state.get(BackupLampBlock.LIT)!=light){
   if(world.setBlockState(pos,state.with(BackupLampBlock.LIT,light),Block.NOTIFY_ALL))markDirty();
  }
 }
 @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){
  super.writeNbt(n,r);n.putInt("BackupCharge",charge);n.putInt("BackupChargeScale",MAX_CHARGE);n.putInt("BackupProgressRemainder",progressRemainder);n.putInt("BackupProgressDuration",progressDuration);
  n.putBoolean("PowerLinked",linked);n.putBoolean("GridPowered",gridPowered);n.putBoolean("SwitchEnabled",switchEnabled);
 }
 @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){
  super.readNbt(n,r);
  int stored=n.contains("BackupCharge")?n.getInt("BackupCharge"):LEGACY_MAX_CHARGE;
  if(n.contains("BackupChargeScale")){
   int oldScale=n.getInt("BackupChargeScale");
   stored=oldScale<=0?0:(int)Math.round(Math.max(0,Math.min(oldScale,stored))*1.0*MAX_CHARGE/oldScale);
  }else stored=(int)Math.round(Math.max(0,Math.min(LEGACY_MAX_CHARGE,stored))*1.0*MAX_CHARGE/LEGACY_MAX_CHARGE);
  charge=Math.max(0,Math.min(MAX_CHARGE,stored));progressRemainder=Math.max(0,n.getInt("BackupProgressRemainder"));
  progressDuration=Math.max(0,n.getInt("BackupProgressDuration"));
  if(progressDuration>0)progressRemainder%=progressDuration;else progressRemainder=0;
  linked=n.getBoolean("PowerLinked");gridPowered=n.getBoolean("GridPowered");switchEnabled=!n.contains("SwitchEnabled")||n.getBoolean("SwitchEnabled");
 }
 @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
 @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){return createNbt(r);}
}
