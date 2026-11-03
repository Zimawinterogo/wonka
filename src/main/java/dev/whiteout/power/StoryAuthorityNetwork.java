package dev.whiteout.power;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Only an event key crosses C2S; replies acknowledge the server's completed transaction. */
public final class StoryAuthorityNetwork {
 private static final Map<ServerPlayerEntity,StoryReceipts> RECEIPTS=new WeakHashMap<>();
 private static boolean registered;
 public record Request(long token,String key) implements CustomPayload {
  public static final Id<Request> ID=new Id<>(PowerDevices.id("story_request"));
  public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeLong(p.token);b.writeString(p.key,128);},b->new Request(b.readLong(),b.readString(128)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Reply(long token,StoryReceipts.Outcome outcome) implements CustomPayload {
  public static final Id<Reply> ID=new Id<>(PowerDevices.id("story_reply"));
  public static final PacketCodec<RegistryByteBuf,Reply> CODEC=PacketCodec.ofStatic(
   (b,p)->{b.writeLong(p.token);b.writeVarInt(p.outcome.ordinal());},b->new Reply(b.readLong(),decode(b.readVarInt())));
  private static StoryReceipts.Outcome decode(int ordinal){
   if(ordinal<0||ordinal>=StoryReceipts.Outcome.values().length)throw new IllegalArgumentException("story outcome");
   return StoryReceipts.Outcome.values()[ordinal];
  }
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public static void register(){
  if(registered)return;registered=true;
  PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Reply.ID,Reply.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Request.ID,(p,c)->c.server().execute(()->{
   if(!ServerPlayNetworking.canSend(c.player(),Reply.ID))return;
   var reply=RECEIPTS.computeIfAbsent(c.player(),v->new StoryReceipts()).handle(new StoryReceipts.Request(p.token,p.key),
    Integer.toUnsignedLong(c.server().getTicks()),()->StoryAuthority.execute(c.player(),p.key));
   ServerPlayNetworking.send(c.player(),new Reply(reply.token(),reply.outcome()));
  }));
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->RECEIPTS.remove(h.getPlayer()));
  ServerLifecycleEvents.SERVER_STOPPED.register(s->RECEIPTS.keySet().removeIf(p->p.getServer()==s));
 }
}
