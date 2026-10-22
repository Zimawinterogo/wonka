import dev.whiteout.power.BackupLampPolicy;
public class BackupLampPolicyTest {
 public static void main(String[] args){
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
  // A restored grid must turn the emergency light off and recharge it, even when switched off.
  if(BackupLampPolicy.lit(true,true,true,charge))throw new AssertionError("lit while mains are healthy");
  for(int i=0;i<12000;i++)charge=BackupLampPolicy.tickCharge(true,true,false,charge,12000);
  if(charge!=12000||BackupLampPolicy.lit(true,true,false,charge))throw new AssertionError("recharge on mains");
  if(!BackupLampPolicy.lit(true,false,true,charge))throw new AssertionError("outage activation");
  // A disabled switch must not discharge reserve power during an outage.
  for(int i=0;i<12000;i++)charge=BackupLampPolicy.tickCharge(true,false,false,charge,12000);
  if(charge!=12000||BackupLampPolicy.lit(true,false,false,charge))throw new AssertionError("switch off during outage");
  // Independent emergency lamps must never share battery state.
  int a=1,b=12000;
  a=BackupLampPolicy.tickCharge(true,false,true,a,12000);
  b=BackupLampPolicy.tickCharge(true,false,false,b,12000);
  if(a!=0||b!=12000)throw new AssertionError("independent backups");
  // Unlinked lights stay off regardless of saved charge or stale mains data.
  if(BackupLampPolicy.lit(false,false,true,12000)||BackupLampPolicy.lit(false,true,true,12000))throw new AssertionError("unlinked light");
  System.out.println("BackupLampPolicy: 32 combinations, 10-minute outage, OFF preservation, recharge, switching and independence passed");
 }
}
