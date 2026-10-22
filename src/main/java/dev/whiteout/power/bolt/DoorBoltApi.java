package dev.whiteout.power.bolt;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Read-only integration point for other systems (auto-close, shelter, AI...).
 * Works on server (authoritative) and on client (synced copy, nearby doors only).
 * Never mutates a bolt: closing a door does not touch bolt state.
 */
public final class DoorBoltApi {
 private DoorBoltApi(){}
 /** Any half of the door may be passed. */
 public static boolean hasBolt(World world,BlockPos doorPos){return DoorBolts.lookup(world,lowerOf(world,doorPos))!=null;}
 /** True if a bolt is installed AND shot. A locked door must not be opened by normal interaction. */
 public static boolean isLocked(World world,BlockPos doorPos){
  DoorBolts.Bolt b=DoorBolts.lookup(world,lowerOf(world,doorPos));return b!=null&&b.locked();
 }
 static BlockPos lowerOf(World world,BlockPos pos){
  BlockState s=world.getBlockState(pos);
  return s.getBlock() instanceof DoorBlock&&s.contains(Properties.DOUBLE_BLOCK_HALF)&&s.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER?pos.down():pos;
 }
}
