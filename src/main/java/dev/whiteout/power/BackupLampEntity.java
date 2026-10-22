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
 public static final int MAX_CHARGE=12000; // ten minutes of backup light
 private int charge=MAX_CHARGE;
 private boolean linked,gridPowered,switchEnabled=true;
 public BackupLampEntity(BlockPos pos,BlockState state){super(PowerDevices.EMERGENCY_LAMP_ENTITY,pos,state);}
 public static void tick(World world,BlockPos pos,BlockState state,BackupLampEntity entity){
  if(world.isClient)return;
  if(entity.linked){
   int previous=entity.charge;
   entity.charge=BackupLampPolicy.tickCharge(true,entity.gridPowered,entity.switchEnabled,previous,MAX_CHARGE);
   // Persist drain/recharge in batches; save the exact empty/full transition immediately.
   if(previous!=entity.charge&&(world.getTime()%20L==0L||entity.charge==0||entity.charge==MAX_CHARGE))entity.markDirty();
  }
  // Also repair stale block states after loading old worlds or interrupted saves.
  // Most ticks do not need any block writes or lighting recalculation.
  if(state.get(BackupLampBlock.LIT)!=BackupLampPolicy.lit(entity.linked,entity.gridPowered,entity.switchEnabled,entity.charge))entity.updateLight();
 }
 public void setGridPower(boolean powered,boolean enabled){
  if(linked&&gridPowered==powered&&switchEnabled==enabled)return;
  linked=true;gridPowered=powered;switchEnabled=enabled;markDirty();updateLight();
 }
 public void unlink(){linked=false;gridPowered=false;markDirty();updateLight();}
 public int charge(){return charge;}
 boolean worldStateLinked(){return linked;}
 boolean worldStateMains(){return gridPowered;}
 boolean worldStateSwitch(){return switchEnabled;}
 /** Translation key for the server-side battery state; display only, no state changes. */
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
   // NOTIFY_ALL already updates neighbouring blocks and client lighting.
   // Calling updateNeighborsAlways again duplicates work on every transition.
   if(world.setBlockState(pos,state.with(BackupLampBlock.LIT,light),Block.NOTIFY_ALL))markDirty();
  }
 }
 @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){
  super.writeNbt(n,r);n.putInt("BackupCharge",charge);n.putBoolean("PowerLinked",linked);n.putBoolean("GridPowered",gridPowered);n.putBoolean("SwitchEnabled",switchEnabled);
 }
 @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){
  super.readNbt(n,r);charge=n.contains("BackupCharge")?Math.max(0,Math.min(MAX_CHARGE,n.getInt("BackupCharge"))):MAX_CHARGE;
  linked=n.getBoolean("PowerLinked");gridPowered=n.getBoolean("GridPowered");
  switchEnabled=!n.contains("SwitchEnabled")||n.getBoolean("SwitchEnabled");
 }
 @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
 @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){return createNbt(r);}
}
