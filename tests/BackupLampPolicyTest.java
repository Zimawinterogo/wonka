import dev.whiteout.power.BackupLampPolicy;
import net.minecraft.world.Difficulty;
public class BackupLampPolicyTest {
 private static void timing(Difficulty difficulty,int outageTicks,int chargeTicks){
  BackupLampPolicy.Timing t=BackupLampPolicy.timing(difficulty);
  if(t.outageTicks()!=outageTicks||t.chargeTicks()!=chargeTicks)throw new AssertionError("difficulty timing: "+difficulty);
  int charge=BackupLampPolicy.CAPACITY,remainder=0;
  for(int i=0;i<outageTicks;i++){
   int[] next=BackupLampPolicy.tick(true,false,true,charge,remainder,t);charge=next[0];remainder=next[1];
   if(i<outageTicks-1&&charge==0)throw new AssertionError("outage ended early: "+difficulty);
  }
  if(charge!=0)throw new AssertionError("outage duration: "+difficulty+" charge="+charge);
  for(int i=0;i<chargeTicks;i++){
   int[] next=BackupLampPolicy.tick(true,true,true,charge,remainder,t);charge=next[0];remainder=next[1];
   if(i<chargeTicks-1&&charge==BackupLampPolicy.CAPACITY)throw new AssertionError("recharge ended early: "+difficulty);
  }
  if(charge!=BackupLampPolicy.CAPACITY)throw new AssertionError("charge duration: "+difficulty+" charge="+charge);
 }
 public static void main(String[] args){
  timing(Difficulty.EASY,18_000,7_200);timing(Difficulty.PEACEFUL,18_000,7_200);
  timing(Difficulty.NORMAL,8_400,18_000);timing(Difficulty.HARD,4_800,30_000);
  var easy=BackupLampPolicy.timing(Difficulty.EASY);
  int[] first=BackupLampPolicy.tick(true,false,true,BackupLampPolicy.CAPACITY,0,easy);
  int[] paused=BackupLampPolicy.tick(true,false,false,first[0],first[1],easy);
  if(paused[0]!=first[0]||paused[1]!=first[1])throw new AssertionError("switch pause lost fractional drain");
  int[] resumed=BackupLampPolicy.tick(true,false,true,paused[0],paused[1],easy);
  if(resumed[0]>=first[0])throw new AssertionError("switch toggling evaded drain");
  int preserved=(int)Math.round(BackupLampPolicy.CAPACITY*.37);
  if(BackupLampPolicy.percent(preserved,BackupLampPolicy.CAPACITY)!=37)throw new AssertionError("difficulty change preserves charge percentage");
  for(boolean linked:new boolean[]{false,true})for(boolean mains:new boolean[]{false,true})
   for(boolean enabled:new boolean[]{false,true})for(int charge:new int[]{0,1,11999,12000}){
    boolean lit=BackupLampPolicy.lit(linked,mains,enabled,charge);
    if(lit!=(linked&&enabled&&!mains&&charge>0))throw new AssertionError("backup-only light");
    int next=BackupLampPolicy.tickCharge(linked,mains,enabled,charge,12000);
    if(next<0||next>12000)throw new AssertionError("capacity");
    if((!linked||!mains&&!enabled)&&next!=charge)throw new AssertionError("OFF drains battery");
    if(linked&&mains&&next!=Math.min(12000,charge+1))throw new AssertionError("recharge");
    if(linked&&!mains&&enabled&&next!=Math.max(0,charge-1))throw new AssertionError("discharge");
   }
  if(BackupLampPolicy.percent(12000,12000)!=100||BackupLampPolicy.percent(11999,12000)!=99||
     BackupLampPolicy.percent(6000,12000)!=50||BackupLampPolicy.percent(0,12000)!=0||
     BackupLampPolicy.percent(-10,12000)!=0||BackupLampPolicy.percent(30000,12000)!=100||
     BackupLampPolicy.percent(100,0)!=0)throw new AssertionError("battery readout percentages");
  int charge=12000;
  for(int i=0;i<36000;i++)charge=BackupLampPolicy.tickCharge(true,false,false,charge,12000);
  if(charge!=12000)throw new AssertionError("OFF after 30 minutes");
  for(int i=0;i<12000;i++)charge=BackupLampPolicy.tickCharge(true,false,true,charge,12000);
  if(charge!=0||BackupLampPolicy.lit(true,false,true,charge))throw new AssertionError("empty battery");
  if(BackupLampPolicy.lit(true,true,true,charge))throw new AssertionError("lit while mains are healthy");
  for(int i=0;i<12000;i++)charge=BackupLampPolicy.tickCharge(true,true,false,charge,12000);
  if(charge!=12000||BackupLampPolicy.lit(true,true,false,charge))throw new AssertionError("recharge on mains");
  if(!BackupLampPolicy.lit(true,false,true,charge))throw new AssertionError("outage activation");
  for(int i=0;i<12000;i++)charge=BackupLampPolicy.tickCharge(true,false,false,charge,12000);
  if(charge!=12000||BackupLampPolicy.lit(true,false,false,charge))throw new AssertionError("switch off during outage");
  int a=1,b=12000;a=BackupLampPolicy.tickCharge(true,false,true,a,12000);b=BackupLampPolicy.tickCharge(true,false,false,b,12000);
  if(a!=0||b!=12000)throw new AssertionError("independent backups");
  if(BackupLampPolicy.lit(false,false,true,12000)||BackupLampPolicy.lit(false,true,true,12000))throw new AssertionError("unlinked light");
  System.out.println("BackupLampPolicy: difficulty timings, fractional progression, percent preservation and legacy policy passed");
 }
}
