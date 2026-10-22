import dev.whiteout.power.CircuitRules;
/** Run with tests/run-standalone.sh (no Minecraft dependencies). */
public final class CircuitRulesTest {
 public static void main(String[] args){
  int checks=0;
  for(int mask=0;mask<128;mask++)for(int fuel=-1;fuel<=3;fuel++){
   boolean[] b=new boolean[7];for(int i=0;i<7;i++)b[i]=(mask&(1<<i))!=0;
   boolean expected=b[0]&&b[1]&&b[2]&&b[3]&&b[4]&&!b[5]&&b[6]&&fuel>0;
   boolean got=CircuitRules.hasPower(b[0],b[1],b[2],b[3],b[4],b[5],b[6],fuel);
   if(expected!=got)throw new AssertionError("power mask "+mask+" fuel "+fuel);
   checks++;
  }
  for(int mask=0;mask<16;mask++){
   boolean configured=(mask&1)!=0,fired=(mask&2)!=0,armed=(mask&4)!=0,inside=(mask&8)!=0;
   if(CircuitRules.entered(configured,fired,armed,inside)!=(configured&&!fired&&armed&&inside))throw new AssertionError("trigger "+mask);
   if(CircuitRules.rearm(configured,fired,inside)!=(configured&&!fired&&!inside))throw new AssertionError("rearm "+mask);
   checks+=2;
  }
  System.out.println(checks+" circuit and trigger assertions passed");
 }
}
