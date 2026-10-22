package dev.whiteout.power;

import java.util.*;
import static dev.whiteout.power.WinterCrisisConditions.*;
import static dev.whiteout.power.WhiteoutWorldState.*;

public final class CrisisConditionsTest {
 static int checks;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  for(Truth a:Truth.values())for(Truth b:Truth.values()){
   ok(a.and(b)==b.and(a),"and commutes");ok(a.or(b)==b.or(a),"or commutes");
   ok(a.and(b).not()==a.not().or(b.not()),"three-valued De Morgan");
  }
  ok(Truth.UNKNOWN.not()==Truth.UNKNOWN&&Truth.UNKNOWN.and(Truth.FALSE)==Truth.FALSE&&Truth.UNKNOWN.or(Truth.TRUE)==Truth.TRUE,"unknown truth table");
  Tracker t=new Tracker(20,10,10);
  ok(!t.update(Truth.FALSE,0).rising(),"initial false");t.update(Truth.FALSE,10);
  ok(t.update(Truth.TRUE,20).value()==Truth.UNKNOWN,"debounce false to true");t.update(Truth.TRUE,30);
  ok(t.update(Truth.TRUE,40).rising(),"sustained outage");ok(!t.update(Truth.TRUE,50).rising(),"true true no retrigger");
  ok(t.update(Truth.UNKNOWN,60).value()==Truth.UNKNOWN,"true unknown clears truth");
  t.update(Truth.TRUE,70);t.update(Truth.TRUE,80);ok(!t.update(Truth.TRUE,90).rising(),"unknown true cannot duplicate episode");
  t.update(Truth.FALSE,100);ok(t.update(Truth.FALSE,110).value()==Truth.FALSE,"stable false rearms");
  t.update(Truth.TRUE,120);t.update(Truth.TRUE,130);ok(t.update(Truth.TRUE,140).rising(),"new sustained episode");
  Tracker flicker=new Tracker(20,10,10);flicker.update(Truth.TRUE,0);flicker.update(Truth.FALSE,5);
  ok(!flicker.update(Truth.FALSE,15).rising(),"short outage ignored");
  Tracker gap=new Tracker(20,10,10);gap.update(Truth.TRUE,0);ok(!gap.update(Truth.TRUE,100).rising(),"unobserved interval is not continuous");
  ok(!new Tracker(20,10,10).update(Truth.TRUE,0).rising(),"restart resets transient tracking");
  Signal a=Signal.network("a"),b=Signal.network("b");Map<Signal,Truth> facts=new HashMap<>();facts.put(a,Truth.TRUE);facts.put(b,Truth.FALSE);int[] reads={0};
  Source source=s->{reads[0]++;return facts.getOrDefault(s,Truth.UNKNOWN);};
  try(Cycle cycle=new Cycle(source,8)){
   ok(cycle.evaluate(new All(List.of(new Atom(a),new Not(new Atom(b)))))==Truth.TRUE,"two independent networks");
   facts.put(a,Truth.FALSE);ok(cycle.evaluate(new Atom(a))==Truth.TRUE&&reads[0]==2,"consistent once-per-cycle facts");
  }
  UUID p=UUID.randomUUID(),q=UUID.randomUUID();Signal sp=Signal.player(Type.BLIZZARD,p,"minecraft:overworld",null,0),sq=Signal.player(Type.BLIZZARD,q,"minecraft:overworld",null,0);
  facts.put(sp,Truth.TRUE);facts.put(sq,Truth.FALSE);
  try(Cycle cycle=new Cycle(source,2)){ok(cycle.evaluate(new Atom(sp))==Truth.TRUE&&cycle.evaluate(new Atom(sq))==Truth.FALSE,"players isolated");}
  try(Cycle cycle=new Cycle(source,1)){ok(cycle.evaluate(new Atom(Signal.player(Type.OUTSIDE_SHELTER,p,"minecraft:overworld",null,.5)))==Truth.UNKNOWN,"shelter budget denies expensive query");}
  try(Cycle cycle=new Cycle(s->{throw new IllegalStateException("corrupt data");},8)){ok(cycle.evaluate(new Atom(a))==Truth.UNKNOWN,"exceptions become unknown");}
  for(Availability unavailable:Availability.values())if(unavailable!=Availability.AVAILABLE){
   ok(observed(new Reading<Boolean>(unavailable,0,Optional.of(true)),v->v)==Truth.UNKNOWN,"missing/unloaded/cached/error not false: "+unavailable);
  }
  Tracker zones=new Tracker(20,10,10);for(int i=0;i<20;i++)ok(!zones.update(i%2==0?Truth.TRUE:Truth.FALSE,i*5).rising(),"rapid zone switches");
  Hysteresis h=new Hysteresis(.8,.6);ok(h.above(OptionalDouble.of(.9))==Truth.TRUE,"hysteresis enters");
  ok(h.above(OptionalDouble.of(.7))==Truth.TRUE&&h.above(OptionalDouble.of(.5))==Truth.FALSE,"hysteresis band");
  ok(h.above(OptionalDouble.empty())==Truth.UNKNOWN&&h.above(OptionalDouble.of(.7))==Truth.UNKNOWN,"unknown hysteresis resets");
  System.out.println("CrisisConditionsTest PASS: "+checks+" deterministic checks (not live world lifecycle)");
 }
}
