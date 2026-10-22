package dev.whiteout.power.barricade;

import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

/**
 * One cell of boards nailed over an opening. Every cell is an independent block: it drops exactly one
 * item and never owns hidden data, so nothing can duplicate or leave an invisible obstacle.
 * PART/VARIANT remain saved for legacy compatibility and partner repair. Connected rendering and
 * collision use world-height parity and immediate neighbours, without new saved properties.
 */
public final class BarricadeBlock extends Block {
 public enum Part implements StringIdentifiable {
  SINGLE("single"),BOTTOM("bottom"),TOP("top");
  private final String id;Part(String id){this.id=id;}
  @Override public String asString(){return id;}
 }
 /** Side of the cell the plate is nailed to (towards the player who placed it). */
 public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
 public static final EnumProperty<Part> PART=EnumProperty.of("part",Part.class);
 public static final IntProperty VARIANT=IntProperty.of("variant",0,2);
 private static final VoxelShape[][][] COLLISION=new VoxelShape[4][2][16];
 private static final VoxelShape[] PLATE=new VoxelShape[4];

 static{
  for(Direction f:Direction.Type.HORIZONTAL){
   int fi=f.getHorizontal();
   PLATE[fi]=rotate(f,0,0,11,16,16,16);
   for(int p=0;p<2;p++)for(int v=0;v<16;v++){
    VoxelShape s=VoxelShapes.empty();
    for(double[] b:ConnectedBarricadeLayouts.BOXES[p][v])s=VoxelShapes.union(s,rotate(f,b[0],b[1],b[2],b[3],b[4],b[5]));
    COLLISION[fi][p][v]=s.simplify();
   }
  }
 }
 /** Layout is authored for SOUTH; rotate (x,z) so the plate lands on the requested side. */
 private static VoxelShape rotate(Direction f,double x0,double y0,double z0,double x1,double y1,double z1){
  double ax,az,bx,bz;
  switch(f){
   case WEST->{ax=16-z0;az=x0;bx=16-z1;bz=x1;}
   case NORTH->{ax=16-x0;az=16-z0;bx=16-x1;bz=16-z1;}
   case EAST->{ax=z0;az=16-x0;bx=z1;bz=16-x1;}
   default->{ax=x0;az=z0;bx=x1;bz=z1;}
  }
  return Block.createCuboidShape(Math.min(ax,bx),y0,Math.min(az,bz),Math.max(ax,bx),y1,Math.max(az,bz));
 }

 public BarricadeBlock(Settings settings){
  super(settings);
  setDefaultState(getDefaultState().with(FACING,Direction.SOUTH).with(PART,Part.SINGLE).with(VARIANT,0));
 }
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING,PART,VARIANT);}
 /** Placement is done only by BarricadeItem (it must plan the whole opening). Dispensers etc. get nothing. */
 @Override public BlockState getPlacementState(ItemPlacementContext ctx){return null;}

 /** Collision matches the visible boards, so gaps between boards are real gaps. */
 @Override protected VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
  return COLLISION[s.get(FACING).getHorizontal()][Math.floorMod(p.getY(),2)][BarricadeConnections.mask(w,s,p)];
 }
 /** Outline covers the whole plate so the barricade is easy to target and mine. */
 @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return PLATE[s.get(FACING).getHorizontal()];}
 @Override protected VoxelShape getCullingShape(BlockState s,BlockView w,BlockPos p){return VoxelShapes.empty();}
 @Override protected VoxelShape getSidesShape(BlockState s,BlockView w,BlockPos p){return VoxelShapes.empty();}

 private static Direction horizontal(int dx,int dz){
  return dx>0?Direction.EAST:dx<0?Direction.WEST:dz>0?Direction.SOUTH:Direction.NORTH;
 }
 /** World adapter for the pure rules in {@link BarricadeSupport}. */
 private static BarricadeSupport.Cells cells(WorldView w,Direction plateSide){
  return (x,y,z,fx,fz)->{
   BlockPos p=new BlockPos(x,y,z);
   BlockState st=w.getBlockState(p);
   if(st.isOf(Barricades.BARRICADE))return st.get(FACING)==plateSide?BarricadeSupport.Kind.PANEL:BarricadeSupport.Kind.OTHER;
   if((fx!=0||fz!=0)&&st.isSideSolidFullSquare(w,p,horizontal(fx,fz)))return BarricadeSupport.Kind.WALL;
   if((st.isAir()||st.isReplaceable())&&st.getFluidState().isEmpty())return BarricadeSupport.Kind.FREE;
   return BarricadeSupport.Kind.OTHER;
  };
 }
 /**
  * Support for a panel that already exists: it touches a solid wall on at least one side, or is chained
  * (same facing, up/down/sideways) to a panel that does. Bounded search, run only on neighbour changes.
  * This is what lets 2xN and 3x3 openings be boarded: panels hold on the frame and on each other.
  */
 public static boolean supported(WorldView w,BlockPos pos,Direction plateSide){
  Direction along=plateSide.rotateYClockwise();
  return BarricadeSupport.supported(cells(w,plateSide),pos.getX(),pos.getY(),pos.getZ(),along.getOffsetX(),along.getOffsetZ());
 }
 /**
  * Rule for placing a NEW panel: {@link #supported} AND the row lies in a gap framed by walls on both
  * sides (at most {@link BarricadeSupport#MAX_GAP_WIDTH} cells wide). 1x1 openings behave exactly as before.
  */
 public static boolean anchored(WorldView w,BlockPos pos,Direction plateSide){
  Direction along=plateSide.rotateYClockwise();
  return BarricadeSupport.canPlace(cells(w,plateSide),pos.getX(),pos.getY(),pos.getZ(),along.getOffsetX(),along.getOffsetZ());
 }
 @Override protected boolean canPlaceAt(BlockState s,WorldView w,BlockPos p){return anchored(w,p,s.get(FACING));}

 @Override protected BlockState getStateForNeighborUpdate(BlockState s,Direction dir,BlockState nb,WorldAccess w,BlockPos pos,BlockPos npos){
  // Do not return AIR here: vanilla neighbor replacement need not create an item drop.
  // Schedule normal server-side block destruction instead; it drops exactly one item.
  // Recheck support at execution, in case a wall or neighboring panel was restored.
  if(!supported(w,pos,s.get(FACING)))w.scheduleBlockTick(pos,this,1);
  Part part=s.get(PART);
  if(dir==Direction.UP&&part==Part.BOTTOM&&!partner(nb,s,Part.TOP))return s.with(PART,Part.SINGLE);
  if(dir==Direction.DOWN&&part==Part.TOP&&!partner(nb,s,Part.BOTTOM))return s.with(PART,Part.SINGLE);
  return s;
 }
 @Override protected void scheduledTick(BlockState s,ServerWorld world,BlockPos pos,Random random){
  BlockState current=world.getBlockState(pos);
  if(current.isOf(this)&&!supported(world,pos,current.get(FACING)))
   world.breakBlock(pos,true);
 }
 private boolean partner(BlockState other,BlockState self,Part want){
  return other.isOf(this)&&other.get(FACING)==self.get(FACING)&&other.get(PART)==want;
 }
}
