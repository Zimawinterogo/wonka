package dev.whiteout.power.lobby;

import dev.whiteout.Store;
import dev.whiteout.power.PowerDevices;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public final class BoardNetwork{
 private BoardNetwork(){}
 private record Session(ServerWorld world,BlockPos root,UUID token){}
 private static final Map<ServerPlayerEntity,Session> sessions=new WeakHashMap<>();
 private static void write(RegistryByteBuf b,LobbyState.Card c){
  b.writeString(c.title(),80);b.writeString(c.description(),320);b.writeString(c.author(),64);b.writeString(c.duration(),48);b.writeString(c.note(),160);
 }
 private static LobbyState.Card read(RegistryByteBuf b){return new LobbyState.Card(b.readString(80),b.readString(320),b.readString(64),b.readString(48),b.readString(160));}
 public record View(UUID token,long revision,boolean editor,boolean started,LobbyState.Card card) implements CustomPayload{
  public static final Id<View> ID=new Id<>(PowerDevices.id("board_view"));
  public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,p)->{
   b.writeUuid(p.token);b.writeLong(p.revision);b.writeBoolean(p.editor);b.writeBoolean(p.started);b.writeBoolean(p.card!=null);if(p.card!=null)write(b,p.card);
  },b->new View(b.readUuid(),b.readLong(),b.readBoolean(),b.readBoolean(),b.readBoolean()?read(b):null));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** Null card requests the single Solo start; a supplied card is an editor request. */
 public record Change(UUID token,long revision,LobbyState.Card card) implements CustomPayload{
  public static final Id<Change> ID=new Id<>(PowerDevices.id("board_change"));
  public static final PacketCodec<RegistryByteBuf,Change> CODEC=PacketCodec.ofStatic((b,p)->{
   b.writeUuid(p.token);b.writeLong(p.revision);b.writeBoolean(p.card!=null);if(p.card!=null)write(b,p.card);
  },b->new Change(b.readUuid(),b.readLong(),b.readBoolean()?read(b):null));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static void open(ServerPlayerEntity p,BlockPos root){
  if(!ServerPlayNetworking.canSend(p,View.ID))return;
  var session=new Session(p.getServerWorld(),root.toImmutable(),UUID.randomUUID());sessions.put(p,session);
  var store=Store.get(p.getServer());if(store.error!=null){sessions.remove(p);p.sendMessage(Text.translatable("story.whiteout.request_denied"));return;}
  var state=LobbyState.get(store);
  ServerPlayNetworking.send(p,new View(session.token,state==null?0:state.revision(),p.isCreative()&&p.hasPermissionLevel(2),state!=null&&state.started(),state==null?null:state.card()));
 }
 private static boolean valid(ServerPlayerEntity p,Session s){
  if(s==null||p.getServerWorld()!=s.world||!p.isAlive()||p.isSpectator()||!s.world.isChunkLoaded(s.root))return false;
  var state=s.world.getBlockState(s.root);
  if(!state.isOf(PowerDevices.STORY_BOARD)||state.get(BoardBlock.PART)!=0||!BoardBlock.complete(s.world,s.root,state))return false;
  for(int i=0;i<4;i++)if(p.canInteractWithBlockAt(BoardBlock.cell(s.root,state.get(BoardBlock.FACE),i),0))return true;
  return false;
 }
 public static void register(){
  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
   net.minecraft.server.command.CommandManager.literal("whiteout").then(net.minecraft.server.command.CommandManager.literal("lobby")
   .then(net.minecraft.server.command.CommandManager.literal("reset-launch").requires(s->s.getEntity() instanceof ServerPlayerEntity p&&p.isCreative()&&s.hasPermissionLevel(2))
   .then(net.minecraft.server.command.CommandManager.literal("confirm").executes(c->{
    var server=c.getSource().getServer();
    try{LobbyState.resetLaunch(Store.get(server));}
    catch(java.io.IOException|IllegalStateException failure){c.getSource().sendError(Text.translatable("story.whiteout.request_denied"));return 0;}
    dev.whiteout.net.WhiteoutNetwork.syncAll(server);
    c.getSource().sendFeedback(()->Text.translatable("board.whiteout.reset"),true);return 1;
   }))))));
  PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);PayloadTypeRegistry.playC2S().register(Change.ID,Change.CODEC);
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->sessions.remove(h.getPlayer()));
  ServerPlayNetworking.registerGlobalReceiver(Change.ID,(msg,ctx)->ctx.server().execute(()->{
   var p=ctx.player();var session=sessions.get(p);
   if(session==null||!session.token.equals(msg.token))return;
   sessions.remove(p);
   if(!valid(p,session))return;
   var store=Store.get(ctx.server());
   try{
    if(msg.card!=null){if(!p.isCreative()||!p.hasPermissionLevel(2))return;LobbyState.edit(store,msg.revision,msg.card);}
    else LobbyState.startSolo(store,msg.revision,p.getUuid());
   }catch(java.io.IOException|IllegalStateException failure){p.sendMessage(Text.translatable("story.whiteout.request_denied"));}
   open(p,session.root);
   if(msg.card==null)dev.whiteout.net.WhiteoutNetwork.syncAll(ctx.server());
  }));
 }
}
