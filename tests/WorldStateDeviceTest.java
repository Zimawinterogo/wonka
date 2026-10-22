package dev.whiteout.power;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.BlockPos;

/** Actual device snapshots/NBT under Fabric Loader. No server world is simulated. */
public final class WorldStateDeviceTest {
 static int checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  for(DeviceBlock block:new DeviceBlock[]{PowerDevices.GENERATOR,PowerDevices.OLD_GENERATOR}){
   var state=block.getDefaultState().with(DeviceBlock.ACTIVE,true);
   DeviceEntity e=new DeviceEntity(BlockPos.ORIGIN,state);e.fuel=27543;
   var snapshot=WhiteoutWorldState.generatorSnapshot(e);
   ok(snapshot.kind()==block.kind&&snapshot.fuelTicks()==27543&&snapshot.running(),"real generator fields");
   NbtCompound before=new NbtCompound();e.writeNbt(before,DynamicRegistryManager.EMPTY);
   WhiteoutWorldState.generatorSnapshot(e);
   NbtCompound after=new NbtCompound();e.writeNbt(after,DynamicRegistryManager.EMPTY);
   ok(before.equals(after),"reading does not change persisted generator");
   DeviceEntity reloaded=new DeviceEntity(BlockPos.ORIGIN,state);reloaded.readNbt(before,DynamicRegistryManager.EMPTY);
   ok(WhiteoutWorldState.generatorSnapshot(reloaded).equals(snapshot),"generator NBT reload");
   e.fuel=0;
   ok(snapshot.fuelTicks()==27543&&WhiteoutWorldState.generatorSnapshot(e).fuelTicks()==0,"old immutable snapshot, fresh depletion");
  }
  DeviceEntity panel=new DeviceEntity(BlockPos.ORIGIN,PowerDevices.PANEL.getDefaultState());
  panel.fuse=true;panel.burnt=true;panel.unstableFuse=true;panel.fuseFailureChance=.37f;
  var fuse=WhiteoutWorldState.fuseSnapshot(panel);
  ok(fuse.inserted()&&fuse.burnt()&&fuse.unstable()&&fuse.failureChance()==.37f,"actual fuse fields");
  BackupLampEntity lamp=new BackupLampEntity(BlockPos.ORIGIN,PowerDevices.EMERGENCY_LAMP.getDefaultState());
  NbtCompound saved=new NbtCompound();saved.putInt("BackupCharge",4312);saved.putBoolean("PowerLinked",true);
  saved.putBoolean("GridPowered",false);saved.putBoolean("SwitchEnabled",false);
  lamp.readNbt(saved,DynamicRegistryManager.EMPTY);
  var light=WhiteoutWorldState.emergencyLightSnapshot(lamp);
  ok(light.chargeTicks()==4312&&light.linked()&&!light.mainsPowered()&&!light.switchEnabled(),"actual backup NBT facts");
  NbtCompound roundtrip=new NbtCompound();lamp.writeNbt(roundtrip,DynamicRegistryManager.EMPTY);
  BackupLampEntity reloaded=new BackupLampEntity(BlockPos.ORIGIN,PowerDevices.EMERGENCY_LAMP.getDefaultState());
  reloaded.readNbt(roundtrip,DynamicRegistryManager.EMPTY);
  ok(WhiteoutWorldState.emergencyLightSnapshot(reloaded).equals(light),"lamp NBT reload");
  reloaded.readNbt(new NbtCompound(),DynamicRegistryManager.EMPTY);
  ok(WhiteoutWorldState.emergencyLightSnapshot(reloaded).chargeTicks()==BackupLampEntity.MAX_CHARGE,"legacy lamp defaults retained");
  saved.putInt("BackupCharge",3700);saved.putInt("BackupChargeScale",10000);
  saved.putInt("BackupProgressRemainder",6000);saved.putInt("BackupProgressDuration",18000);
  lamp.readNbt(saved,DynamicRegistryManager.EMPTY);
  ok(lamp.charge()==4440,"foreign normalized scale preserves 37 percent");
  lamp.setGridPower(false,true);lamp.setGridPower(false,false);
  NbtCompound fractional=new NbtCompound();lamp.writeNbt(fractional,DynamicRegistryManager.EMPTY);
  ok(fractional.getInt("BackupProgressRemainder")==6000,"switch toggles preserve fractional progress");
  reloaded.readNbt(fractional,DynamicRegistryManager.EMPTY);
  NbtCompound restored=new NbtCompound();reloaded.writeNbt(restored,DynamicRegistryManager.EMPTY);
  ok(restored.equals(fractional),"fractional NBT roundtrip");
  var empty=new DoorGateSync.Snapshot("minecraft:overworld","");
  var blocked=new DoorGateSync.Snapshot("minecraft:overworld","1,2,3");
  ok(!DoorGateSync.shouldSend(empty,empty,10),"identical door packet suppressed");
  ok(DoorGateSync.shouldSend(empty,blocked,10)&&DoorGateSync.shouldSend(blocked,empty,10),"lock and unlock sent immediately");
  ok(DoorGateSync.shouldSend(empty,new DoorGateSync.Snapshot("minecraft:the_nether",""),10),"dimension change sent");
  ok(DoorGateSync.shouldSend(empty,empty,100)&&DoorGateSync.shouldSend(null,empty,0),"heartbeat and reconnect snapshot");
  System.out.println("WorldStateDeviceTest PASS: "+checks);
 }
}
