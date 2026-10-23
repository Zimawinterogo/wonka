package dev.whiteout.power;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** One small server->client channel; clients never advance the story themselves. */
public final class LakeStoryPackets {
 private LakeStoryPackets() {}
 public static final int OBJECTIVE=0, DIALOGUE=1, CLEAR=2, RESET=3;
 public record Story(int kind, String text) implements CustomPayload {
  public static final Id<Story> ID=new Id<>(PowerDevices.id("lake_story_event"));
  public static final PacketCodec<RegistryByteBuf,Story> CODEC=PacketCodec.ofStatic(
   (buf,message)->{buf.writeVarInt(message.kind());buf.writeString(message.text(),512);},
   buf->new Story(buf.readVarInt(),buf.readString(512)));
  @Override public Id<? extends CustomPayload> getId(){return ID;}
 }
 public static void register(){PayloadTypeRegistry.playS2C().register(Story.ID,Story.CODEC);}
}
