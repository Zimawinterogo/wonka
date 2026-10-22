import dev.whiteout.power.GeneratorSoundWatchdog;

public final class GeneratorSoundWatchdogTest {
 private static void check(boolean ok,String description){if(!ok)throw new AssertionError(description);}
 public static void main(String[] args){
  var healthy=new GeneratorSoundWatchdog();
  for(int t=0;t<300;t++)check(!healthy.shouldRecover(true,true,false),"healthy loop not recreated");
  var lostOnJoin=new GeneratorSoundWatchdog();
  for(int t=0;t<59;t++)check(!lostOnJoin.shouldRecover(true,false,false),"startup grace");
  check(lostOnJoin.shouldRecover(true,false,false),"missing loop recovers after startup grace");
  var intermittent=new GeneratorSoundWatchdog();
  for(int t=0;t<100;t++)check(!intermittent.shouldRecover(true,true,false),"initial healthy");
  for(int t=0;t<29;t++)check(!intermittent.shouldRecover(true,false,false),"short interruption ignored");
  check(!intermittent.shouldRecover(true,true,false),"recovery canceled when sound returns");
  for(int t=0;t<29;t++)check(!intermittent.shouldRecover(true,false,false),"missing streak reset");
  check(intermittent.shouldRecover(true,false,false),"sustained interruption recovers");
  var far=new GeneratorSoundWatchdog();
  for(int t=0;t<200;t++)check(!far.shouldRecover(false,false,false),"do not spam distant sources");
  for(int t=0;t<29;t++)check(!far.shouldRecover(true,false,false),"nearby requires sustained missing playback");
  check(far.shouldRecover(true,false,false),"recover after listener returns");
  var pause=new GeneratorSoundWatchdog();
  for(int t=0;t<100;t++)check(!pause.shouldRecover(true,false,true),"paused world never restarts audio");
  for(int t=0;t<29;t++)check(!pause.shouldRecover(true,false,false),"pause does not count as missing playback");
  check(pause.shouldRecover(true,false,false),"resume with broken loop recovers");
  var repeated=new GeneratorSoundWatchdog();
  for(int t=0;t<60;t++)repeated.shouldRecover(true,false,false);
  var replacement=new GeneratorSoundWatchdog();
  for(int t=0;t<59;t++)check(!replacement.shouldRecover(true,false,false),"replacement has fresh grace period");
  System.out.println("PASS GeneratorSoundWatchdogTest: healthy, initial load, interruption, distance, pause, cooldown");
 }
}
