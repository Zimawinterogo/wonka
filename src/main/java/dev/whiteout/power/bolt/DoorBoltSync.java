package dev.whiteout.power.bolt;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: bolts near the player. Sent only when the nearby set actually changes. */
public record DoorBoltSync(String dimension,String lines) implements CustomPayload {
 public static final Id<DoorBoltSync> ID=new Id<>(Identifier.of("whiteout","door_bolt_snapshot"));
 public static final PacketCodec<RegistryByteBuf,DoorBoltSync> CODEC=PacketCodec.ofStatic(
  (b,p)->{b.writeString(p.dimension,128);b.writeString(p.lines,16384);},
  b->new DoorBoltSync(b.readString(128),b.readString(16384)));
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
