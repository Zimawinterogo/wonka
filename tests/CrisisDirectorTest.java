package dev.whiteout.power;

import java.util.*;
import static dev.whiteout.power.WinterCrisisConditions.*;
import static dev.whiteout.power.WinterCrisisDirector.*;

public final class CrisisDirectorTest {
 static int checks;
 static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static final UUID P=UUID.fromString("00000000-0000-0000-0000-000000000001"),Q=UUID.fromString("00000000-0000-0000-0000-000000000002");
 static Spec spec(String id,UUID player,int priority,int repeats){
  Condition c=new Atom(Signal.network(id));
  return new Spec(id,new Target(Scope.PLAYER,"minecraft:overworld",Set.of(player)),c,c,priority,repeats,0,100,20,30,
   CancelPolicy.REQUIRE_TRUE_WHILE_ACTIVE,CompletionPolicy.AFTER_DURATION);
 }
 static State state(WinterCrisisDirector d,String id){return d.status(id).orElseThrow().state();}
 static class ActionsLog implements Actions {
  final List<String> starts=new ArrayList<>();int completed,cancelled;String fail="";
  public void enter(View v){starts.add(v.id());if(v.id().equals(fail))throw new IllegalStateException("injected action failure");}
  public void complete(View v){completed++;}
  public void cancel(View v){cancelled++;}
 }
 public static void main(String[] args){
  ActionsLog a=new ActionsLog();WinterCrisisDirector d=new WinterCrisisDirector(a);int[] samples={0};
  Source yes=s->{samples[0]++;return Truth.TRUE;};
  ok(d.register(spec("one",P,10,2)),"register");ok(!d.register(spec("one",P,10,2)),"duplicate rejected");
  ok(!d.arm("one",0),"disabled cannot arm");d.tick(0,yes,t->{throw new AssertionError("disabled presence called");});
  ok(samples[0]==0&&a.starts.isEmpty()&&state(d,"one")==State.IDLE,"disabled makes no observation/action");
  d.enabled(true,0);ok(d.arm("one",0),"manual armed");ok(!d.arm("one",0),"duplicate arm rejected");
  d.tick(0,yes,t->true);ok(state(d,"one")==State.ACTIVE&&a.starts.size()==1,"active once");
  ok(samples[0]==1,"precondition and trigger shared sample");d.tick(0,yes,t->true);ok(a.starts.size()==1,"same tick ignored");
  d.tick(20,yes,t->true);ok(state(d,"one")==State.COMPLETED&&a.completed==1,"completed");
  d.tick(30,yes,t->true);ok(state(d,"one")==State.COOLDOWN&&!d.arm("one",30),"cooldown prevents repetition");
  d.tick(50,yes,t->true);ok(state(d,"one")==State.IDLE&&d.arm("one",50),"manual repeat after delay");
  d.tick(60,yes,t->true);d.tick(80,yes,t->true);d.tick(100,yes,t->true);
  ok(state(d,"one")==State.COMPLETED&&!d.arm("one",100)&&a.starts.size()==2,"finite repeat limit");
  ok(d.history().stream().map(Event::state).toList().containsAll(List.of(State.ARMED,State.ACTIVE,State.COMPLETED,State.COOLDOWN,State.IDLE)),"explicit lifecycle diagnostics");
  d.close();d.close();ok(d.status().isEmpty()&&d.history().isEmpty()&&!d.enabled(),"stop clears references and is idempotent");
  WinterCrisisDirector restart=new WinterCrisisDirector(a);restart.enabled(true,0);restart.tick(0,yes,t->true);
  ok(restart.status().isEmpty()&&a.starts.size()==2,"restart never replays actions");restart.close();

  for(String mode:List.of("manual","unknown","false","disconnect","dimension","disable","missing","exception","stop")){
   ActionsLog log=new ActionsLog();WinterCrisisDirector x=new WinterCrisisDirector(log);x.register(spec("x",P,0,1));x.enabled(true,0);x.arm("x",0);x.tick(0,s->Truth.TRUE,t->true);
   switch(mode){
    case "manual"->x.cancel("x",10,"manual");
    case "unknown","missing"->x.tick(10,s->Truth.UNKNOWN,t->true);
    case "false"->x.tick(10,s->Truth.FALSE,t->true);
    case "disconnect"->x.departed(P,10);
    case "dimension"->x.tick(10,s->Truth.TRUE,t->false);
    case "disable"->x.enabled(false,10);
    case "exception"->x.tick(10,s->{throw new IllegalStateException("source error");},t->true);
    case "stop"->x.close();
   }
   ok(log.cancelled==1,mode+" cancels exactly once");
   if(!mode.equals("stop"))ok(state(x,"x")==State.CANCELLED,mode+" explicit cancelled state");
   x.close();ok(log.cancelled==1,mode+" close does not duplicate cancellation");
  }
  ActionsLog log=new ActionsLog();WinterCrisisDirector x=new WinterCrisisDirector(log);x.enabled(true,0);
  x.register(spec("bad",P,100,1));x.register(spec("good",Q,10,1));log.fail="bad";x.arm("bad",0);x.arm("good",0);
  x.tick(0,s->Truth.TRUE,t->true);
  ok(state(x,"bad")==State.CANCELLED&&state(x,"good")==State.ACTIVE,"action exception isolated");
  ok(log.starts.equals(List.of("bad","good")),"higher priority first");
  x.departed(P,10);ok(state(x,"good")==State.ACTIVE,"other player unaffected");x.close();

  x=new WinterCrisisDirector(new ActionsLog());x.enabled(true,0);x.register(spec("a",P,1,1));x.register(spec("b",Q,1,1));x.arm("a",0);x.arm("b",0);
  x.tick(0,s->s.id().equals("a")?Truth.TRUE:Truth.FALSE,t->true);
  ok(state(x,"a")==State.ACTIVE&&state(x,"b")==State.ARMED,"networks independent");
  x.tick(100,s->Truth.UNKNOWN,t->true);ok(state(x,"a")==State.CANCELLED&&state(x,"b")==State.CANCELLED,"missing active source cancels; armed source times out");x.close();

  ActionsLog bounded=new ActionsLog();x=new WinterCrisisDirector(bounded);x.enabled(true,0);
  for(int i=0;i<16;i++){ok(x.register(spec("n"+i,P,i,1)),"bounded registration");x.arm("n"+i,0);}
  ok(!x.register(spec("overflow",P,0,1)),"instance bound");int[] count={0};
  x.tick(0,s->{count[0]++;return Truth.TRUE;},t->true);
  ok(count[0]==4&&bounded.starts.size()==4,"four checks and four active maximum");
  ok(bounded.starts.getFirst().equals("n15"),"priority at capacity");
  for(int tick=10;tick<=400;tick+=10)x.tick(tick,s->Truth.TRUE,t->true);
  ok(x.history().size()<=64,"history bounded");
  ok(x.status().stream().noneMatch(v->v.state()==State.ARMED||v.state()==State.ACTIVE),"bounded queue drains without starvation");
  x.reset(410);ok(x.status().isEmpty()&&x.history().isEmpty(),"temporary reset");x.close();
  boolean invalid=false;try{spec("INVALID ID",P,0,1);}catch(IllegalArgumentException expected){invalid=true;}ok(invalid,"malformed scenario rejected");
  Target group=new Target(Scope.GROUP,"minecraft:overworld",Set.of(P,Q));ok(group.players().size()==2,"group value scope");
  ok(new Target(Scope.WORLD,"minecraft:overworld",Set.of()).players().isEmpty(),"world scope no player retention");
  for(boolean completeFails:List.of(true,false)){
   WinterCrisisDirector broken=new WinterCrisisDirector(new Actions(){
    public void enter(View v){}
    public void complete(View v){if(completeFails)throw new IllegalStateException("complete fails");}
    public void cancel(View v){throw new IllegalStateException("cancel fails");}
   });
   broken.register(spec("broken",P,1,1));broken.enabled(true,0);broken.arm("broken",0);broken.tick(0,s->Truth.TRUE,t->true);
   if(completeFails)broken.tick(20,s->Truth.TRUE,t->true);else broken.cancel("broken",10,"cancel");
   ok(state(broken,"broken")==State.CANCELLED,"completion/cancel failures contained");broken.close();
  }
  WinterCrisisDirector waiting=new WinterCrisisDirector(new ActionsLog());waiting.enabled(true,0);waiting.register(spec("waiting",P,0,1));waiting.arm("waiting",0);
  waiting.tick(0,s->Truth.UNKNOWN,t->true);ok(state(waiting,"waiting")==State.ARMED,"unknown never starts");
  waiting.tick(10,s->Truth.TRUE,t->{throw new IllegalStateException("presence failure");});ok(state(waiting,"waiting")==State.CANCELLED,"presence failure isolated");waiting.close();
  System.out.println("CrisisDirectorTest PASS: "+checks+" deterministic runtime checks (no live server)");
 }
}
