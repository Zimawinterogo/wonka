import dev.whiteout.power.*;
import java.util.*;
import static dev.whiteout.power.StoryWorldConditions.*;
import static dev.whiteout.power.WinterCrisisConditions.Truth;

public final class StoryWorldConditionsTest {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static Node atom(Compare compare,double value){return new Node(Op.COMPARE,List.of(),Metric.PLAYER_COUNT,compare,value,null,null);}
 static Node logic(Op op,Node... terms){return new Node(op,List.of(terms),null,null,0,null,null);}
 static void invalid(Node node){try{node.validate();throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  for(Compare comparison:Compare.values()){
   for(double a:new double[]{0,1,2}){
    boolean expected=switch(comparison){case EQ->a==1;case NE->a!=1;case LT->a<1;case LE->a<=1;case GT->a>1;case GE->a>=1;};
    check(evaluate(atom(comparison,1),n->OptionalDouble.of(a))==Truth.of(expected),"numeric comparison "+comparison);
   }
  }
  var yes=atom(Compare.GE,1);var no=atom(Compare.LT,1);
  check(evaluate(logic(Op.ALL,yes,no),n->OptionalDouble.of(2))==Truth.FALSE,"ALL");
  check(evaluate(logic(Op.ANY,yes,no),n->OptionalDouble.of(2))==Truth.TRUE,"ANY");
  check(evaluate(logic(Op.NOT,yes),n->OptionalDouble.empty())==Truth.UNKNOWN,"NOT unknown fails closed");
  check(evaluate(logic(Op.ANY,no,yes),n->n.equals(no)?OptionalDouble.of(2):OptionalDouble.empty())==Truth.UNKNOWN,"false or unknown");
  check(evaluate(yes,n->{throw new IllegalStateException();})==Truth.UNKNOWN,"unavailable adapter");
  check(evaluate(yes,n->OptionalDouble.of(Double.NaN))==Truth.UNKNOWN,"NaN not true");
  int[] reads={0};check(evaluate(logic(Op.ALL,yes,yes,yes),n->{reads[0]++;return OptionalDouble.of(2);})==Truth.TRUE&&reads[0]==1,"sample reuse");
  invalid(logic(Op.NOT,yes,no));invalid(logic(Op.ALL));invalid(atom(Compare.EQ,Double.POSITIVE_INFINITY));
  Node deep=yes;for(int i=0;i<10;i++)deep=logic(Op.NOT,deep);invalid(deep);
  invalid(new Node(Op.COMPARE,List.of(),Metric.GENERATOR_FUEL,Compare.GT,0,null,null));
  invalid(new Node(Op.COMPARE,List.of(),Metric.NETWORK_POWERED,Compare.EQ,1,"../not valid",null));
  for(var availability:WhiteoutWorldState.Availability.values()){
   var reading=new WhiteoutWorldState.Reading<>(availability,1,Optional.of(new WhiteoutWorldState.GeneratorStateView(0,100,true)));
   var result=StoryWorldSource.observed(reading,WhiteoutWorldState.GeneratorStateView::fuelTicks);
   check(result.isPresent()==(availability==WhiteoutWorldState.Availability.AVAILABLE),"cached/unloaded data cannot grant permissions: "+availability);
  }
  check(evaluate(null,n->{throw new AssertionError();})==Truth.TRUE,"legacy catalog without world condition");
  System.out.println("PASS StoryWorldConditionsTest: "+checks+" logical, numeric, bounded and unavailable-fact checks");
 }
}
