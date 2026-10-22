import dev.whiteout.power.StarterLogic;
public class StarterLogicTest {
 public static void main(String[] args){
  double previousWidth=1;int previousPeriod=100;
  for(int s=0;s<3;s++){
   int period=StarterLogic.period(s),start=StarterLogic.windowStart(s),end=StarterLogic.windowEnd(s);
   double width=(end-start)/(double)period;
   if(width>=previousWidth||period>=previousPeriod)throw new AssertionError("difficulty");
   previousWidth=width;previousPeriod=period;
   for(int t=0;t<period*100;t++)if(StarterLogic.hit(t,s)!=(t%period>=start&&t%period<end))throw new AssertionError("window");
   if(StarterLogic.hit(-1,s))throw new AssertionError("early");
  }
  int p=0;for(int i=0;i<3;i++)p=StarterLogic.next(p,true);
  if(p!=3||StarterLogic.next(0,false)!=0||StarterLogic.next(2,false)!=1)throw new AssertionError("progress");
  System.out.println("Starter timing: all 3 stages passed");
 }
}
