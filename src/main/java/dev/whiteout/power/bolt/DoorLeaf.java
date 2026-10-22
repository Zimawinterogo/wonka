package dev.whiteout.power.bolt;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Pure geometry of a vanilla door leaf (no client classes, safe on both sides).
 * Frame: origin corner = hinge corner at the "back" plane, W = hinge -> free edge,
 * T = from the leaf's outer plane inwards (3/16 thick). Works for open and closed doors,
 * so a bolt always follows the leaf and never floats in the air.
 */
public final class DoorLeaf {
 private DoorLeaf(){}
 /** Bolt centre height above the lower door block, in blocks. */
 public static final double BOLT_HEIGHT=1.3;
 public static final double THICKNESS=3.0/16.0;

 public record Frame(double cx,double cz,Direction w,Direction t){
  /** s,h,d in pixels: along leaf, above lower block base, off the mounting face. */
  public double[] point(BlockPos low,boolean inner,double s,double h,double d){
   double t0=inner?THICKNESS:0,tt=t0+(inner?1:-1)*d/16.0,ss=s/16.0;
   return new double[]{low.getX()+cx+w.getOffsetX()*ss+t.getOffsetX()*tt,low.getY()+h/16.0,low.getZ()+cz+w.getOffsetZ()*ss+t.getOffsetZ()*tt};
  }
  public Direction mountNormal(boolean inner){return inner?t:t.getOpposite();}
 }

 public static Frame frame(BlockState lower){
  Direction f=lower.get(Properties.HORIZONTAL_FACING);
  boolean open=lower.get(Properties.OPEN);
  Direction hinge=lower.get(Properties.DOOR_HINGE)==DoorHinge.RIGHT?f.rotateYClockwise():f.rotateYCounterclockwise();
  Direction back=f.getOpposite();
  double cx=back.getOffsetX()+hinge.getOffsetX()>0?1:0,cz=back.getOffsetZ()+hinge.getOffsetZ()>0?1:0;
  return open?new Frame(cx,cz,f,hinge.getOpposite()):new Frame(cx,cz,hinge.getOpposite(),f);
 }

 /** True when a click (world position + face) lands on the bolt's mounting area. */
 public static boolean hits(BlockState lower,BlockPos low,boolean inner,Vec3d hit,Direction side){
  Frame fr=frame(lower);
  if(side!=fr.mountNormal(inner))return false;
  double s=((hit.x-low.getX()-fr.cx)*fr.w.getOffsetX()+(hit.z-low.getZ()-fr.cz)*fr.w.getOffsetZ())*16.0;
  double h=(hit.y-low.getY())*16.0;
  return s>=4.5&&s<=16.3&&Math.abs(h-BOLT_HEIGHT*16.0)<=3.2;
 }
}
