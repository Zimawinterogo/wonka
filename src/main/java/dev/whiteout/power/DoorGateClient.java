package dev.whiteout.power;

import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

/** Client-only registration: never load MinecraftClient on a dedicated server. */
public final class DoorGateClient {
 private DoorGateClient(){}
 private static String clientDimension="";
 private static final Set<Long> CLIENT_BLOCKED=new HashSet<>();
 private static int ticks;
 public static void clearClient(){clientDimension="";CLIENT_BLOCKED.clear();}
 public static void registerClient(){
  ClientPlayNetworking.registerGlobalReceiver(DoorGateSync.Snapshot.ID,(packet,context)->context.client().execute(()->{
   clientDimension=packet.dimension();CLIENT_BLOCKED.clear();
   if(!packet.blocked().isEmpty())for(String line:packet.blocked().split("\\n")){
    String[] p=line.split(",");if(p.length!=3)continue;
    try{CLIENT_BLOCKED.add(new BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])).asLong());}
    catch(NumberFormatException ignored){}
   }
  }));
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if(!world.isClient||player.isSpectator()||MinecraftClient.getInstance().player!=player)return ActionResult.PASS;
   String dimension=world.getRegistryKey().getValue().toString();
   if(!dimension.equals(clientDimension))return ActionResult.PASS;
   BlockPos at=hit.getBlockPos();BlockState s=world.getBlockState(at);
   if(!(s.getBlock() instanceof DoorBlock))return ActionResult.PASS;
   if(s.contains(Properties.DOUBLE_BLOCK_HALF)&&s.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER)at=at.down();
   // SUCCESS consumes the local vanilla animation, while allowing the normal
   // interaction packet to reach the server so frozen-door pulls still count.
   return CLIENT_BLOCKED.contains(at.asLong())?ActionResult.SUCCESS:ActionResult.PASS;
  });
 }
}
