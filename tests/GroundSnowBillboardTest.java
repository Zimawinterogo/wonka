import dev.whiteout.power.GroundSnowBillboard;

/** Standalone Java 21 regressions for the production billboard math. */
public final class GroundSnowBillboardTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  double minArea=1;
  for(int yaw=0;yaw<=360;yaw++)for(int pitch=-90;pitch<=90;pitch++){
   double y=Math.toRadians(yaw),p=Math.toRadians(pitch),t=GroundSnowBillboard.tilt(pitch);
   double rx=-Math.cos(y),rz=-Math.sin(y);
   double ux=-Math.sin(y)*Math.sin(t),uy=Math.cos(t),uz=Math.cos(y)*Math.sin(t);
   double nx=-rz*uy,ny=rz*ux-rx*uz,nz=rx*uy;
   double area=nx*Math.sin(y)*Math.cos(p)+ny*Math.sin(p)-nz*Math.cos(y)*Math.cos(p);
   minArea=Math.min(minArea,area);
   check(area>.75,"quad must remain front-facing and readable at every yaw/pitch");
   check(uy>=0,"top must never rotate beneath the ground anchor");
   check(Math.abs(rx*ux+rz*uz)<1e-12,"orthogonal in-plane axes");
   check(Math.abs(t-GroundSnowBillboard.tilt(pitch+.001))<.0001,"no orientation discontinuity");
  }
  for(int p=-25;p<=25;p++)check(GroundSnowBillboard.tilt(p)==0,"cylindrical near horizon");
  for(int sky=0;sky<16;sky++)for(int block=0;block<16;block++){
   int out=GroundSnowBillboard.softLight((sky<<20)|(block<<4));
   check((out>>>20)==sky,"sky light unchanged");
   check((out&0xffff)==Math.max(6,block)*16,"bounded non-black block light");
  }
  System.out.println("PASS "+checks+" billboard checks; minimum projected area="+minArea);
 }
}
