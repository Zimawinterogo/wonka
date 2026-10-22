package dev.whiteout.power.backpack;

/** GUI pixels, never inventory indices or storage. No scaling of item rendering. */
public final class BackpackLayout {
 public static final int WIDTH=320, HEIGHT=224, SLOT_COUNT=46;
 private BackpackLayout(){}
 public record Point(int x,int y){}
 public static boolean visible(int id){return id>=5&&id<SLOT_COUNT;}
 public static Point position(int id){
  if(!visible(id))return new Point(-10000,-10000);
  if(id>=5&&id<=8)return new Point(251,65+(id-5)*29);
  if(id==45)return new Point(251,181);
  int index=id>=36?id-36:id-9;
  int col=index%9;
  return new Point(40+col*20,id>=36?179:68+(index/9)*33);
 }
 public static boolean fits(int width,int height){return width>=WIDTH&&height>=HEIGHT;}
 public static boolean allowed(boolean enabled,boolean creative,boolean spectator){return enabled&&!creative&&!spectator;}
 public static float opening(long elapsedMillis,boolean animation){
  if(!animation)return 1;
  float progress=Math.max(0,Math.min(1,elapsedMillis/180f));
  return 1-(1-progress)*(1-progress)*(1-progress);
 }
}
