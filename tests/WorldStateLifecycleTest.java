package dev.whiteout.power;

import java.util.*;
import static dev.whiteout.power.WhiteoutWorldState.*;

public final class WorldStateLifecycleTest {
 public static void main(String[] args)throws Exception{
  var constructor=WhiteoutWorldState.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
  var facade=(WhiteoutWorldState)constructor.newInstance(new Object[]{null});
  var close=WhiteoutWorldState.class.getDeclaredMethod("close");close.setAccessible(true);close.invoke(facade);close.invoke(facade);
  var server=WhiteoutWorldState.class.getDeclaredField("server");server.setAccessible(true);
  if(server.get(facade)!=null)throw new AssertionError("server retained after close");
  try{facade.network("main");throw new AssertionError("closed facade accepted query");}catch(IllegalStateException expected){}
  for(Availability a:Availability.values()){
   var c=new GridManager.Config();c.generatorBound=true;c.panelBound=true;
   Reading<GeneratorStateView> g=new Reading<>(a,0,a==Availability.AVAILABLE?Optional.of(new GeneratorStateView(0,10,true)):Optional.empty());
   var f=new Reading<>(Availability.AVAILABLE,0,Optional.of(new FuseStateView(true,false,false,0,true)));
   var r=WhiteoutWorldState.networkSnapshot("main",c,g,f,0,0);
   Availability want=a==Availability.UNLOADED?Availability.CACHED:a;
   if(r.availability()!=want)throw new AssertionError(a+" mislabeled as "+r.availability());
  }
  var targets=new ArrayList<String>();targets.add("a");var sw=new SwitchStateView("main",true,false,targets);targets.clear();
  if(sw.targets().size()!=1)throw new AssertionError("mutable switch snapshot");
  System.out.println("WorldStateLifecycleTest PASS: close contract, all availability states, detached switch targets (no live world)");
 }
}
