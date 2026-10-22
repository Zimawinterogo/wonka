package dev.whiteout.power.barricade;

import java.util.HashSet;
import java.util.Set;

/** Standalone test of the pure barricade support/framing rules (no Minecraft). Run:
 *  javac -d /tmp/bt src/main/java/dev/whiteout/power/barricade/BarricadeSupport.java tests/BarricadeSupportTest.java
 *  java -cp /tmp/bt dev.whiteout.power.barricade.BarricadeSupportTest */
public final class BarricadeSupportTest {
 static int checks;
 static void ok(boolean c,String m){checks++;if(!c)throw new AssertionError(m);}

 /** Wall in plane z=0. Opening x 1..w, y 1..h; frame blocks outside it. Panels set by (x,y). */
 static final class World implements BarricadeSupport.Cells {
  final int w,h;final Set<Long> panels=new HashSet<>();final Set<Long> removedWalls=new HashSet<>();
  World(int w,int h){this.w=w;this.h=h;}
  static long k(int x,int y){return x*10000L+y;}
  boolean wall(int x,int y,int z){
   if(z!=0)return false;
   boolean frame=x<=0||x>=w+1||y<=0||y>=h+1;
   return frame&&!removedWalls.contains(k(x,y));
  }
  public BarricadeSupport.Kind kind(int x,int y,int z,int fx,int fz){
   if(z==0&&panels.contains(k(x,y)))return BarricadeSupport.Kind.PANEL;
   if((fx!=0||fz!=0)&&wall(x,y,z))return BarricadeSupport.Kind.WALL;
   return wall(x,y,z)?BarricadeSupport.Kind.OTHER:BarricadeSupport.Kind.FREE;
  }
  boolean canPlace(int x,int y){return BarricadeSupport.canPlace(this,x,y,0,1,0);}
  boolean supported(int x,int y){return BarricadeSupport.supported(this,x,y,0,1,0);}
  boolean place(int x,int y){if(!canPlace(x,y))return false;panels.add(k(x,y));return true;}
  /** Emulates neighbour updates: unsupported panels fall until stable. Returns how many fell. */
  int settle(){
   int fell=0;boolean changed=true;
   while(changed){changed=false;
    for(Long p:new HashSet<>(panels)){int x=(int)(p/10000),y=(int)(p%10000);
     if(!supported(x,y)){panels.remove(p);fell++;changed=true;}}
   }
   return fell;
  }
  void fill(){for(int y=1;y<=h;y++)for(int x=1;x<=w;x++)ok(place(x,y),"fill "+x+","+y+" in "+w+"x"+h);}
 }

 public static void main(String[] a){
  // 1x1 and 1x2 behave like before.
  World o=new World(1,1);ok(o.canPlace(1,1),"1x1");ok(o.place(1,1),"1x1 place");
  World t=new World(1,2);ok(t.place(1,1)&&t.place(1,2),"1x2");
  // 3x3: the centre cannot be started, a corner can, then the rest chains on.
  World n=new World(3,3);
  ok(!n.canPlace(2,2),"centre of an empty 3x3 must not float");
  ok(n.canPlace(1,1)&&n.canPlace(3,3),"edge cells start");
  n.fill();ok(n.panels.size()==9,"3x3 full");
  // Remove the centre section: the rest stays and the hole can be refilled.
  n.panels.remove(World.k(2,2));ok(n.settle()==0,"removing the centre drops nothing else");
  ok(n.place(2,2),"centre refilled");
  // Remove a centre column: left and right columns still hold on their walls.
  for(int y=1;y<=3;y++)n.panels.remove(World.k(2,y));ok(n.settle()==0,"column removal keeps sides");
  // Remove ONE side wall: the structure still touches the other wall.
  World s1=new World(3,3);s1.fill();for(int y=0;y<=4;y++)s1.removedWalls.add(World.k(0,y));
  ok(s1.settle()==0,"one wall left keeps panels");
  // Remove BOTH side walls: everything falls, nothing floats.
  s1.removedWalls.clear();for(int y=0;y<=4;y++){s1.removedWalls.add(World.k(0,y));s1.removedWalls.add(World.k(4,y));}
  ok(s1.settle()==9&&s1.panels.isEmpty(),"no wall contact -> all 9 fall");
  // Lintel and floor removal do not matter for support (support is lateral + chain).
  World s2=new World(3,3);s2.fill();for(int x=0;x<=4;x++){s2.removedWalls.add(World.k(x,0));s2.removedWalls.add(World.k(x,4));}
  ok(s2.settle()==0,"floor/lintel removal keeps panels");
  // Partial wall removal: only the wall block next to ONE panel; the chain still reaches the other wall.
  World s3=new World(3,1);s3.fill();s3.removedWalls.add(World.k(0,1));ok(s3.settle()==0,"1x3 row with one side open stays");
  // Isolated pair away from walls cannot exist.
  World loose=new World(6,1);loose.panels.add(World.k(3,1));loose.panels.add(World.k(4,1));
  ok(loose.settle()==2,"loose pair falls");
  // Width limits.
  World w8=new World(8,1);ok(w8.canPlace(1,1),"8-wide ok");World w9=new World(9,1);ok(!w9.canPlace(1,1),"9-wide refused");
  World big=new World(8,8);big.fill();ok(big.panels.size()==64,"8x8 board wall");
  big.panels.remove(World.k(4,4));ok(big.settle()==0,"8x8 hole stays stable");
  // No loose boards beside a lone wall in the open.
  World lone=new World(30,1);ok(!lone.canPlace(1,1),"wide gap refused");
  // Chain cap is bounded: huge detached blob never loops forever.
  World cap=new World(1,300);long t0=System.nanoTime();for(int y=1;y<=300;y++)cap.panels.add(World.k(1,y));
  cap.settle();ok((System.nanoTime()-t0)/1e9<5,"bounded cost");
  System.out.println("BarricadeSupportTest OK, checks="+checks);
 }
}
