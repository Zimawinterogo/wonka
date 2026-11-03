import dev.whiteout.power.*;
import java.util.*;
import static dev.whiteout.power.WinterCrisisDirector.*;
import static dev.whiteout.power.WinterCrisisConditions.*;

public final class CrisisPhasesTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Spec spec(){
  var condition=new Atom(Signal.network("test"));
  return new Spec("scene:test",new Target(Scope.WORLD,"minecraft:overworld",Set.of()),condition,condition,10,1,0,100,60,0,CancelPolicy.CHECK_AT_START,CompletionPolicy.AFTER_DURATION,List.of(10L,10L,10L,10L,10L,10L));
 }
 public static void main(String[] args){
  List<Integer> phases=new ArrayList<>();boolean[] ready={true};int[] completed={0},pending={0};
  Actions actions=new Actions(){
   public void enter(View v){phases.add(v.phase());}
   public boolean ready(View v){return ready[0];}
   public void phase(View v){phases.add(v.phase());}
   public void complete(View v){completed[0]++;}
   public void cancelPending(View v){pending[0]++;}
  };
  var director=new WinterCrisisDirector(actions);director.register(spec());director.enabled(true,0);director.arm("scene:test",0);
  director.tick(0,s->Truth.TRUE,t->true);check(phases.equals(List.of(0)),"preparation entered once");
  ready[0]=false;director.tick(10,s->{throw new AssertionError("entry conditions replayed");},t->true);
  check(phases.equals(List.of(0))&&director.status("scene:test").orElseThrow().state()==State.ACTIVE,"wait at phase boundary");
  ready[0]=true;director.tick(20,s->Truth.FALSE,t->true);check(phases.equals(List.of(0,1)),"advance only one phase");
  var restored=new WinterCrisisDirector(actions);restored.register(spec());restored.enabled(true,0);
  check(restored.restoreActive("scene:test",1,0),"restore saved phase");
  check(phases.equals(List.of(0,1)),"restore never replays phase actions");
  restored.tick(0,s->Truth.UNKNOWN,t->true);check(phases.equals(List.of(0,1)),"restored phase gets full duration");
  for(int tick=10;tick<=50;tick+=10)restored.tick(tick,s->Truth.UNKNOWN,t->true);
  check(phases.equals(List.of(0,1,2,3,4,5))&&completed[0]==1,"six phases ordered and completed once");
  restored.tick(100,s->Truth.TRUE,t->true);check(completed[0]==1,"terminal state does not repeat");
  check(!restored.arm("scene:test",100),"Once scene cannot rearm");
  var queue=new WinterCrisisDirector(actions);queue.enabled(true,0);queue.register(spec());queue.arm("scene:test",0);queue.cancel("scene:test",1,"operator");
  check(pending[0]==1&&queue.forget("scene:test"),"queued cancellation callback and bounded removal");
  var broken=new WinterCrisisDirector(new Actions(){public void enter(View v){}public void phase(View v){throw new IllegalStateException("disk");}});
  broken.enabled(true,0);broken.register(spec());broken.arm("scene:test",0);broken.tick(0,s->Truth.TRUE,t->true);broken.tick(10,s->Truth.TRUE,t->true);
  var stopped=broken.status("scene:test").orElseThrow();check(stopped.state()==State.CANCELLED&&stopped.phase()==0,"failed phase does not advance cursor");
  check(!broken.restoreActive("scene:test",6,20),"invalid restore refused");
  for(var d:List.of(director,restored,queue,broken))d.close();
  System.out.println("PASS CrisisPhasesTest: "+checks+" phase/wait/restore/cancel/failure checks");
 }
}
