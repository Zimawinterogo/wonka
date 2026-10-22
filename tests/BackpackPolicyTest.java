import dev.whiteout.power.backpack.*;
import java.nio.file.*;
import java.util.*;

public final class BackpackPolicyTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  int visible=0;
  for(int id=0;id<46;id++){
   var point=BackpackLayout.position(id);
   if(id<5){check(!BackpackLayout.visible(id)&&point.x()<-1000,"hidden crafting "+id);continue;}
   visible++;check(BackpackLayout.visible(id),"visible player slot "+id);
   check(point.x()>=2&&point.y()>=2&&point.x()+18<=320&&point.y()+18<=224,"in bag "+id);
   for(int other=5;other<id;other++){
    var previous=BackpackLayout.position(other);
    check(Math.abs(previous.x()-point.x())>=18||Math.abs(previous.y()-point.y())>=18,"overlap "+id+" / "+other);
   }
  }
  check(visible==27+9+4+1,"same capacity plus equipment");
  for(int i=0;i<9;i++)check(BackpackLayout.position(36+i).x()==BackpackLayout.position(9+i).x(),"hotbar alignment");
  check(!BackpackLayout.allowed(false,false,false),"off");
  check(BackpackLayout.allowed(true,false,false),"survival/adventure");
  check(!BackpackLayout.allowed(true,true,false),"creative override");
  check(!BackpackLayout.allowed(true,false,true),"spectator override");
  for(int[] viewport:new int[][]{{320,240},{427,240},{480,270},{640,360},{854,480},{1280,720},{1920,1080},{3440,1440}}){
   check(BackpackLayout.fits(viewport[0],viewport[1]),"viewport");
   int left=(viewport[0]-320)/2,top=(viewport[1]-224)/2;
   for(int id=5;id<46;id++){
    var point=BackpackLayout.position(id);
    check(left+point.x()>=0&&left+point.x()+16<=viewport[0]&&top+point.y()+16<=viewport[1],"viewport slot");
   }
  }
  check(!BackpackLayout.fits(200,150),"tiny window safely falls back");
  check(BackpackLayout.opening(0,false)==1,"animation disabled");
  check(BackpackLayout.opening(0,true)==0&&BackpackLayout.opening(180,true)==1,"animation duration");
  float before=0;for(int t=0;t<=200;t++){float now=BackpackLayout.opening(t,true);check(now>=before&&now<=1,"monotonic animation");before=now;}
  check(BackpackOpening.ready(0,false),"disabled cinematic immediate");
  check(!BackpackOpening.ready(1099,true)&&BackpackOpening.ready(1100,true),"input gate ends with cinematic");
  check(BackpackOpening.lift(0)==0&&BackpackOpening.lift(1100)==1,"lift endpoints");
  check(BackpackOpening.lid(480)==0&&BackpackOpening.lid(950)==1,"lid endpoints");
  check(BackpackOpening.dip(0)==0&&BackpackOpening.dip(1100)==.22f,"camera remains seated");
  for(long elapsed:new long[]{300,1100,10000,3600000})
   check(BackpackOpening.seatedDip(elapsed,true)==.22f,"seated while inventory stays open");
  check(BackpackOpening.seatedDip(0,false)==.22f,"skip/disabled opening stays seated");
  check(BackpackOpening.hands(0)==0&&BackpackOpening.hands(1100)==0,"hands restored");
  for(int t=-100;t<1500;t++){
   check(BackpackOpening.lid(t)>=0&&BackpackOpening.lid(t)<=1,"lid clamped");
   check(BackpackOpening.dip(t)>=0&&BackpackOpening.dip(t)<=.221f,"camera bounded");
   check(Math.abs(BackpackOpening.lift(t+1)-BackpackOpening.lift(t))<.01,"continuous lift");
  }
  Path base=Path.of("build/backpack-test").toAbsolutePath();Files.createDirectories(base);
  Path directory=Files.createTempDirectory(base,"config-");Path file=directory.resolve("whiteout-backpack.json");
  try{
   var preferences=new BackpackPreferences(file);preferences.load();
   check(!preferences.enabled()&&preferences.animation()&&preferences.error()==null,"safe defaults");
   preferences.setEnabled(true);var restarted=new BackpackPreferences(file);restarted.load();
   check(restarted.enabled(),"survives restart");restarted.setEnabled(false);
   restarted.setAnimation(false);preferences.load();
   check(!preferences.animation()&&!preferences.enabled(),"animation command persists without enabling backpack");
   preferences.setAnimation(true);restarted.load();check(restarted.animation(),"animation on persists");
   preferences.load();check(!preferences.enabled(),"off persisted");
   Files.writeString(file,"{\"enabled\":true,\"animation\":false,\"future\":\"keep\"}");preferences.load();
   check(preferences.enabled()&&!preferences.animation(),"animation option");preferences.setEnabled(false);
   check(Files.readString(file).contains("keep"),"unknown preferences preserved");
   for(String invalid:List.of("{broken","null","[]","{\"enabled\":\"yes\"}","{\"animation\":null}")){
    Files.writeString(file,invalid);preferences.load();check(!preferences.enabled()&&preferences.error()!=null,"invalid disabled");
    try{preferences.setEnabled(true);throw new AssertionError("corrupt config overwritten");}catch(java.io.IOException expected){}
    check(Files.readString(file).equals(invalid),"original retained");
   }
   Files.writeString(file,"{}");preferences.load();Files.delete(file);Files.createDirectory(file);
   try{preferences.setEnabled(true);throw new AssertionError("failed write enabled setting");}catch(java.io.IOException expected){}
   check(!preferences.enabled(),"failed save does not change mode");Files.delete(file);
  }finally{
   if(Files.isRegularFile(file))Files.delete(file);
   Files.deleteIfExists(file.resolveSibling(file.getFileName()+".bak"));Files.delete(directory);
  }
  System.out.println("PASS BackpackPolicyTest: 41 slots, geometry, modes, animation, persisted config, corrupt/failed saves");
 }
}
