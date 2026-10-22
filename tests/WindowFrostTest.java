import dev.whiteout.power.FrostProgress;
import dev.whiteout.power.WindowAirSearch;

public class WindowFrostTest {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  int age=0;
  for(int i=0;i<100;i++)age=FrostProgress.advance(age,true);
  check(age==76,"accumulation must saturate");
  for(int i=0;i<76;i++){
   int before=age;age=FrostProgress.advance(age,false);
   check(age<before,"thaw must progress every update");
  }
  check(age==0&&FrostProgress.advance(age,false)==0,"complete thaw");
  check(FrostProgress.advance(age,true)==1,"new storm restarts frost");
  check(WindowAirSearch.find((x,y,z)->x==1?2:0)!=null,"exterior window");
  check(WindowAirSearch.find((x,y,z)->x>0?(x>=4?2:1):0)!=null,"deep eave");
  check(WindowAirSearch.find((x,y,z)->x==1&&z>=0?(z>=3?2:1):0)!=null,"corner around column");
  check(WindowAirSearch.find((x,y,z)->Math.abs(x)<3&&Math.abs(z)<3&&Math.abs(y)<2?1:0)==null,"sealed indoor partition");
  check(WindowAirSearch.find((x,y,z)->x==1?1:x==2?0:x>2?2:0)==null,"do not cross a second glass wall");
  // A sunlit opening in the wrong zone must not stop traversal to blizzard outdoors.
  var selected=WindowAirSearch.find((x,y,z)->x>=1&&x<=4&&y==0&&z==0?(x==1||x==4?2:1):0,
   point->point.x()==4);
  check(selected!=null&&selected.x()==4,"skip sky opening outside the weather zone");
  check(WindowAirSearch.find((x,y,z)->x==1?2:0,point->false)==null,
   "no exterior weather zone means no false exposure");
  int[] calls={0};
  check(WindowAirSearch.find((x,y,z)->{calls[0]++;return 1;})==null&&calls[0]<=512,"bounded search");
  System.out.println("PASS WindowFrostTest: thaw, restart, eaves, corners, interior isolation, bounded work");
 }
}
