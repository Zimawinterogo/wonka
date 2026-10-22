import dev.whiteout.power.StarterLogic;
import dev.whiteout.power.WornStarterLogic;
import dev.whiteout.power.UnstableFusePolicy;
public class NewFeaturesTest {
 public static void main(String[] args) {
  if(WornStarterLogic.PULLS != 5 || StarterLogic.PULLS != 3)throw new AssertionError("starter amounts");
  for(int phase=0;phase<WornStarterLogic.PULLS;phase++) {
   int period=WornStarterLogic.period(phase);
   if(period<25 || WornStarterLogic.windowEnd(phase)>=period)throw new AssertionError("period");
   for(int tick=0;tick<period*4;tick++) {
    boolean expected=WornStarterLogic.phase(tick,phase)>=WornStarterLogic.windowStart(phase)
      &&WornStarterLogic.phase(tick,phase)<WornStarterLogic.windowEnd(phase);
    if(expected!=WornStarterLogic.hit(tick,phase))throw new AssertionError("hit window");
   }
  }
  if(WornStarterLogic.next(4,false)!=0)throw new AssertionError("miss must reset");
  for(int stage=0;stage<WornStarterLogic.PULLS-1;stage++){
   if(WornStarterLogic.period(stage+1)>=WornStarterLogic.period(stage))throw new AssertionError("worn must accelerate");
   if(WornStarterLogic.windowEnd(stage+1)-WornStarterLogic.windowStart(stage+1)>WornStarterLogic.windowEnd(stage)-WornStarterLogic.windowStart(stage))throw new AssertionError("worn window must not widen");
  }
  int last=WornStarterLogic.PULLS-1;
  if(WornStarterLogic.period(last)>=StarterLogic.period(StarterLogic.PULLS-1))throw new AssertionError("worn final speed");
  if(WornStarterLogic.windowEnd(last)-WornStarterLogic.windowStart(last)>=StarterLogic.windowEnd(StarterLogic.PULLS-1)-StarterLogic.windowStart(StarterLogic.PULLS-1))throw new AssertionError("worn final window");
  if(UnstableFusePolicy.blows(.0f,0f)||!UnstableFusePolicy.blows(.99f,1f))throw new AssertionError("editable probability");
  int[] periods={46,41,36,31,26},starts={25,22,19,16,13},ends={33,29,25,21,17};
  for(int stage=0;stage<5;stage++){
   if(WornStarterLogic.period(stage)!=periods[stage]||WornStarterLogic.windowStart(stage)!=starts[stage]||WornStarterLogic.windowEnd(stage)!=ends[stage])throw new AssertionError("known timing");
   for(int t=-1;t<periods[stage]*100;t++){
    boolean expected=t>=0&&t%periods[stage]>=starts[stage]&&t%periods[stage]<ends[stage];
    if(WornStarterLogic.hit(t,stage)!=expected)throw new AssertionError("independent timing oracle");
   }
   if(WornStarterLogic.next(stage,false)!=0)throw new AssertionError("miss at every stage");
  }
  int pulls=0;for(int i=0;i<5;i++){if(pulls!=i)throw new AssertionError("one stage per hit");pulls=WornStarterLogic.next(pulls,true);}
  if(pulls!=5 || WornStarterLogic.next(pulls,true)!=5||WornStarterLogic.next(0,false)!=0)throw new AssertionError("progress");
  if(UnstableFusePolicy.INTERVAL_TICKS!=200||UnstableFusePolicy.FAILURE_CHANCE!=.20f)throw new AssertionError("configuration");
  if(!UnstableFusePolicy.blows(.199f)||UnstableFusePolicy.blows(.20f)||UnstableFusePolicy.blows(-.01f))throw new AssertionError("probability edge");
  if(!UnstableFusePolicy.shouldCheck(true,false,true,201,200) || UnstableFusePolicy.shouldCheck(true,true,true,201,200)
    ||UnstableFusePolicy.shouldCheck(true,false,false,201,200)||UnstableFusePolicy.shouldCheck(true,false,true,199,200))throw new AssertionError("check guard");
  System.out.println("PASS new: 5-stage worn generator and independent 200-tick 20% fuse failure rules");
 }
}
