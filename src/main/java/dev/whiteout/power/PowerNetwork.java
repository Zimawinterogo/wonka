package dev.whiteout.power;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public final class PowerNetwork {
 public record FuseSettings(BlockPos pos,int percent) implements CustomPayload {
  public static final Id<FuseSettings> ID=new Id<>(PowerDevices.id("fuse_settings"));
  public static final PacketCodec<RegistryByteBuf,FuseSettings> CODEC=PacketCodec.ofStatic((b,p)->{b.writeBlockPos(p.pos);b.writeVarInt(p.percent);},b->new FuseSettings(b.readBlockPos(),b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Action(BlockPos pos,int operation) implements CustomPayload {
  public static final Id<Action> ID=new Id<>(PowerDevices.id("power_action"));
  public static final PacketCodec<RegistryByteBuf,Action> CODEC=PacketCodec.ofStatic((b,v)->{b.writeBlockPos(v.pos);b.writeVarInt(v.operation);},b->new Action(b.readBlockPos(),b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Starter(BlockPos pos,long start,int progress,int status) implements CustomPayload {
  public static final Id<Starter> ID=new Id<>(PowerDevices.id("power_starter"));
  public static final PacketCodec<RegistryByteBuf,Starter> CODEC=PacketCodec.ofStatic((b,v)->{b.writeBlockPos(v.pos);b.writeLong(v.start);b.writeVarInt(v.progress);b.writeVarInt(v.status);},b->new Starter(b.readBlockPos(),b.readLong(),b.readVarInt(),b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** Server -> client request for a one-line cutscene in Whiteout's existing dialogue UI. */
 public record OutageDialogue() implements CustomPayload {
  public static final Id<OutageDialogue> ID=new Id<>(PowerDevices.id("power_outage_dialogue"));
  public static final PacketCodec<RegistryByteBuf,OutageDialogue> CODEC=PacketCodec.ofStatic((b,v)->{},b->new OutageDialogue());
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record FuseFailure(int variant) implements CustomPayload {
  public static final Id<FuseFailure> ID=new Id<>(PowerDevices.id("unstable_fuse_failure"));
  public static final PacketCodec<RegistryByteBuf,FuseFailure> CODEC=PacketCodec.ofStatic((b,v)->b.writeVarInt(v.variant()),b->new FuseFailure(b.readVarInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record OpenDialogues() implements CustomPayload {
  public static final Id<OpenDialogues> ID=new Id<>(PowerDevices.id("dialogue_catalog_open"));
  public static final PacketCodec<RegistryByteBuf,OpenDialogues> CODEC=PacketCodec.ofStatic((b,v)->{},b->new OpenDialogues());
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** Operator-only client command to run a dialogue or a saved Director signal. */
 public record PlayDialogue(String id,boolean signal) implements CustomPayload {
  public static final Id<PlayDialogue> ID=new Id<>(PowerDevices.id("dialogue_play"));
  public static final PacketCodec<RegistryByteBuf,PlayDialogue> CODEC=PacketCodec.ofStatic(
   (b,v)->{b.writeString(v.id(),96);b.writeBoolean(v.signal());},
   b->new PlayDialogue(b.readString(96),b.readBoolean()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public static void register(){
  PayloadTypeRegistry.playS2C().register(FuseSettings.ID,FuseSettings.CODEC);
  PayloadTypeRegistry.playC2S().register(Action.ID,Action.CODEC);PayloadTypeRegistry.playS2C().register(Starter.ID,Starter.CODEC);PayloadTypeRegistry.playS2C().register(OutageDialogue.ID,OutageDialogue.CODEC);PayloadTypeRegistry.playS2C().register(FuseFailure.ID,FuseFailure.CODEC);PayloadTypeRegistry.playS2C().register(OpenDialogues.ID,OpenDialogues.CODEC);PayloadTypeRegistry.playS2C().register(PlayDialogue.ID,PlayDialogue.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Action.ID,(v,c)->c.server().execute(()->{
   var p=c.player();var w=p.getServerWorld();
   if(p.isSpectator()||!p.isAlive()||p.squaredDistanceTo(v.pos.toCenterPos())>36||!w.isChunkLoaded(v.pos))return;
   if(LakeStory.blocked(w,v.pos))return;
   if(w.getBlockEntity(v.pos) instanceof DeviceEntity e){
    if(v.operation>=100&&v.operation<=200&&e.kind()==1&&p.isCreative()&&p.hasPermissionLevel(2)){
     e.fuseFailureChance=(v.operation-100)/100f;e.sync();
     ServerPlayNetworking.send(p,new FuseSettings(v.pos,v.operation-100));
    }else if(v.operation==1)e.pull(p);else if(v.operation==2)e.cancel(p);
   }
  }));
 }
}
