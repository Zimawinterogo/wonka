package dev.whiteout.power.barricade;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * Pure support / framing rules for barricade panels. No Minecraft types, so the logic is covered by
 * tests/BarricadeSupportTest.java with plain javac. {@link BarricadeBlock} adapts the world to {@link Cells}.
 * All checks are bounded (row scan {@link #MAX_GAP_WIDTH}, chain search {@link #MAX_CHAIN} panels), never recursive,
 * and run only on placement and neighbour updates.
 */
final class BarricadeSupport {
 private BarricadeSupport(){}
 enum Kind {WALL,FREE,PANEL,OTHER}
 /** (faceDx,faceDz) is the side of the cell that is being probed from, i.e. the face a wall must present. */
 interface Cells {Kind kind(int x,int y,int z,int faceDx,int faceDz);}

 /** Widest opening (in cells, along the wall) that can be boarded. */
 static final int MAX_GAP_WIDTH=8;
 /** Hard cap on panels visited while looking for a supporting wall. */
 static final int MAX_CHAIN=128;

 /** A solid wall block touches the cell on at least one side along the wall plane. */
 static boolean wallAdjacent(Cells c,int x,int y,int z,int ax,int az){
  return c.kind(x+ax,y,z+az,-ax,-az)==Kind.WALL||c.kind(x-ax,y,z-az,ax,az)==Kind.WALL;
 }
 /** Steps to the first wall in direction (sx,sz) through free cells / same-facing panels; 0 if none. */
 static int distanceToWall(Cells c,int x,int y,int z,int sx,int sz){
  for(int i=1;i<=MAX_GAP_WIDTH;i++){
   Kind k=c.kind(x+sx*i,y,z+sz*i,-sx,-sz);
   if(k==Kind.WALL)return i;
   if(k!=Kind.FREE&&k!=Kind.PANEL)return 0;
  }
  return 0;
 }
 /** The cell lies in a gap bounded by walls on both sides, at most MAX_GAP_WIDTH cells wide. */
 static boolean framed(Cells c,int x,int y,int z,int ax,int az){
  int a=distanceToWall(c,x,y,z,ax,az),b=distanceToWall(c,x,y,z,-ax,-az);
  return a>0&&b>0&&a+b-1<=MAX_GAP_WIDTH;
 }
 /** The cell touches a wall, or is chained (up/down/sideways, same facing) to a panel that does. */
 static boolean supported(Cells c,int x,int y,int z,int ax,int az){
  if(wallAdjacent(c,x,y,z,ax,az))return true;
  ArrayDeque<int[]> queue=new ArrayDeque<>();
  HashSet<Long> seen=new HashSet<>();
  queue.add(new int[]{x,y,z});seen.add(key(0,0,0));
  int[][] steps={{0,1,0},{0,-1,0},{ax,0,az},{-ax,0,-az}};
  int panels=0;
  while(!queue.isEmpty()){
   int[] at=queue.removeFirst();
   for(int[] s:steps){
    int nx=at[0]+s[0],ny=at[1]+s[1],nz=at[2]+s[2];
    if(!seen.add(key(nx-x,ny-y,nz-z)))continue;
    if(c.kind(nx,ny,nz,0,0)!=Kind.PANEL)continue;
    if(wallAdjacent(c,nx,ny,nz,ax,az))return true;
    if(++panels>=MAX_CHAIN)return false;
    queue.addLast(new int[]{nx,ny,nz});
   }
  }
  return false;
 }
 /** Rule for NEW panels: supported AND inside a bounded framed gap (no loose boards beside random walls). */
 static boolean canPlace(Cells c,int x,int y,int z,int ax,int az){
  return supported(c,x,y,z,ax,az)&&framed(c,x,y,z,ax,az);
 }
 private static long key(int dx,int dy,int dz){return(((long)(dx+512)*2048L)+(dy+512))*2048L+(dz+512);}
}
