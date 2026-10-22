package dev.whiteout.power.barricade;

import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;
import net.minecraft.block.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

/** Runs actual block collision selection, including same-state neighbour removal. */
public final class BarricadeCollisionTest {
 static int checks;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  SharedConstants.createGameVersion(); Bootstrap.initialize();
  BarricadeBlock block=Barricades.BARRICADE;
  Map<BlockPos,BlockState> cells=new HashMap<>();
  BlockView view=(BlockView)Proxy.newProxyInstance(BlockView.class.getClassLoader(),new Class[]{BlockView.class},(o,m,a)->{
   if(m.getName().equals("getBlockState"))return cells.getOrDefault(a[0],Blocks.AIR.getDefaultState());
   if(m.getName().equals("getHeight"))return 384;
   if(m.getName().equals("getBottomY"))return -64;
   throw new UnsupportedOperationException(m.getName());
  });
  for(Direction facing:Direction.Type.HORIZONTAL)for(int y=-1;y<=1;y++)for(int mask=0;mask<16;mask++){
   cells.clear(); BlockPos pos=new BlockPos(0,y,0);
   BlockState state=block.getDefaultState().with(BarricadeBlock.FACING,facing);
   cells.put(pos,state); Direction right=facing.rotateYCounterclockwise();
   BlockPos[] neighbors={pos.offset(right.getOpposite()),pos.offset(right),pos.down(),pos.up()};
   for(int i=0;i<4;i++)if((mask&(1<<i))!=0)cells.put(neighbors[i],state);
   ok(BarricadeConnections.mask(view,state,pos)==mask,"connection orientation");
   VoxelShape shape=state.getCollisionShape(view,pos);
   var bounds=shape.getBoundingBox();
   ok(switch(facing){
    case SOUTH->bounds.minZ>=13.0/16&&bounds.maxZ==1;
    case NORTH->bounds.minZ==0&&bounds.maxZ<=3.0/16;
    case EAST->bounds.minX>=13.0/16&&bounds.maxX==1;
    case WEST->bounds.minX==0&&bounds.maxX<=3.0/16;
    default->false;
   },"plate on correct face");
   double volume=shape.getBoundingBoxes().stream().mapToDouble(b->b.getLengthX()*b.getLengthY()*b.getLengthZ()).sum();
   double expected=0;
   for(double[] b:ConnectedBarricadeLayouts.BOXES[Math.floorMod(y,2)][mask])expected+=(b[3]-b[0])*(b[4]-b[1])*(b[5]-b[2])/4096;
   ok(Math.abs(volume-expected)<1e-10,"physical wood volume");
   for(BarricadeBlock.Part part:BarricadeBlock.Part.values())for(int v=0;v<3;v++)
    ok(!VoxelShapes.matchesAnywhere(shape,state.with(BarricadeBlock.PART,part).with(BarricadeBlock.VARIANT,v).getCollisionShape(view,pos),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"legacy saved states retain connected geometry");
   for(int i=0;i<4;i++)if((mask&(1<<i))!=0){
    cells.remove(neighbors[i]);
    ok(BarricadeConnections.mask(view,state,pos)==(mask&~(1<<i)),"neighbor removal");
    if(i<2)ok(VoxelShapes.matchesAnywhere(shape,state.getCollisionShape(view,pos),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"dynamic collision updates without blockstate change");
    cells.put(neighbors[i],state.with(BarricadeBlock.FACING,facing.getOpposite()));
    ok(BarricadeConnections.mask(view,state,pos)==(mask&~(1<<i)),"opposite facing does not connect");
    cells.put(neighbors[i],state);
   }
  }
  int[] scheduled={0};
  WorldAccess access=(WorldAccess)Proxy.newProxyInstance(WorldAccess.class.getClassLoader(),new Class[]{WorldAccess.class},(o,m,a)->{
   if(m.getName().equals("getBlockState"))return cells.getOrDefault(a[0],Blocks.AIR.getDefaultState());
   if(m.getName().equals("scheduleBlockTick")){scheduled[0]++;return null;}
   if(m.getName().equals("getHeight"))return 384;
   if(m.getName().equals("getBottomY"))return -64;
   throw new UnsupportedOperationException(m.getName());
  });
  for(Direction facing:Direction.Type.HORIZONTAL){
   cells.clear();BlockPos pos=new BlockPos(0,10,0);Direction right=facing.rotateYCounterclockwise();
   BlockState state=block.getDefaultState().with(BarricadeBlock.FACING,facing).with(BarricadeBlock.PART,BarricadeBlock.Part.BOTTOM);
   cells.put(pos,state);cells.put(pos.offset(right),Blocks.STONE.getDefaultState());
   int before=scheduled[0];
   BlockState repaired=block.getStateForNeighborUpdate(state,Direction.UP,Blocks.AIR.getDefaultState(),access,pos,pos.up());
   ok(repaired.get(BarricadeBlock.PART)==BarricadeBlock.Part.SINGLE,"broken partner repaired");
   ok(scheduled[0]==before,"supported panel retained");
   cells.remove(pos.offset(right));
   BlockState unsupported=block.getStateForNeighborUpdate(repaired,right,Blocks.AIR.getDefaultState(),access,pos,pos.offset(right));
   ok(unsupported.isOf(block)&&scheduled[0]==before+1,"support loss schedules destruction, not silent AIR replacement");
  }
  System.out.println("BarricadeCollisionTest PASS: "+checks);
 }
}
