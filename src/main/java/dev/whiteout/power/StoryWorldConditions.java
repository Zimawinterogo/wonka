package dev.whiteout.power;

import java.util.*;
import static dev.whiteout.power.WinterCrisisConditions.Truth;

/** Data form of bounded server conditions. Missing facts remain UNKNOWN even under NOT. */
public final class StoryWorldConditions {
 private StoryWorldConditions(){}
 public enum Op { ALL, ANY, NOT, COMPARE }
 public enum Compare { EQ, NE, LT, LE, GT, GE }
 public enum Metric { PLAYER_COUNT, TIME_OF_DAY, NETWORK_POWERED, GENERATOR_RUNNING, GENERATOR_FUEL,
  FUSE_INSERTED, FUSE_BURNT, BLIZZARD, WIND_STRENGTH, SNOWFALL, SHELTER_PROTECTION, COLD_LEVEL,
  ZONE_ACTIVE, OBJECTIVE_ACTIVE, OBJECTIVE_COMPLETED, OBJECTIVE_FAILED, EVENT_COMPLETED }
 public record Node(Op op,List<Node> terms,Metric metric,Compare compare,double value,String id,WhiteoutWorldState.Address address){
  public Node {terms=terms==null?List.of():List.copyOf(terms);}
  public void validate(){validate(0,new int[]{64});}
  private void validate(int depth,int[] nodes){
   if(depth>8||--nodes[0]<0||op==null)throw new IllegalArgumentException("condition size/depth/op");
   if(op==Op.COMPARE){
    if(metric==null||compare==null||!Double.isFinite(value)||!terms.isEmpty())throw new IllegalArgumentException("comparison");
    switch(metric){
     case NETWORK_POWERED,ZONE_ACTIVE,OBJECTIVE_ACTIVE,OBJECTIVE_COMPLETED,OBJECTIVE_FAILED,EVENT_COMPLETED->{
      if(id==null||!id.matches("[a-zA-Z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("condition id");
     }
     case GENERATOR_RUNNING,GENERATOR_FUEL,FUSE_INSERTED,FUSE_BURNT->{
      if(address==null||!address.dimension().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||address.dimension().length()>128
       ||Math.abs((long)address.x())>30000000||Math.abs((long)address.z())>30000000||Math.abs((long)address.y())>2048)throw new IllegalArgumentException("device address");
     }
     default->{}
    }
   }else{
    if(metric!=null||compare!=null||address!=null||id!=null||value!=0||terms.isEmpty()||terms.size()>16||op==Op.NOT&&terms.size()!=1)throw new IllegalArgumentException("logical condition");
    for(Node term:terms){Objects.requireNonNull(term);term.validate(depth+1,nodes);}
   }
  }
 }
 @FunctionalInterface public interface Source {OptionalDouble sample(Node atom)throws Exception;}
 public static Truth evaluate(Node node,Source source){
  if(node==null)return Truth.TRUE;node.validate();return evaluate(node,source,new HashMap<>());
 }
 private static Truth evaluate(Node n,Source source,Map<Node,Truth> cache){
  if(n.op()==Op.COMPARE)return cache.computeIfAbsent(n,key->{
   OptionalDouble reading;try{reading=source.sample(n);}catch(Exception failure){return Truth.UNKNOWN;}
   if(reading==null||reading.isEmpty()||!Double.isFinite(reading.getAsDouble()))return Truth.UNKNOWN;
   double a=reading.getAsDouble(),b=n.value();return Truth.of(switch(n.compare()){
    case EQ->a==b;case NE->a!=b;case LT->a<b;case LE->a<=b;case GT->a>b;case GE->a>=b;
   });
  });
  if(n.op()==Op.NOT)return evaluate(n.terms().getFirst(),source,cache).not();
  Truth result=n.op()==Op.ALL?Truth.TRUE:Truth.FALSE;
  for(Node term:n.terms()){
   Truth next=evaluate(term,source,cache);result=n.op()==Op.ALL?result.and(next):result.or(next);
   if(n.op()==Op.ALL&&result==Truth.FALSE||n.op()==Op.ANY&&result==Truth.TRUE)break;
  }
  return result;
 }
}
