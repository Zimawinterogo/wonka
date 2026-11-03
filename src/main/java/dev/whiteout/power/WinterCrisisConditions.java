package dev.whiteout.power;

import java.util.*;
import java.util.function.Predicate;
import static dev.whiteout.power.WhiteoutWorldState.*;

/** Deterministic analysis only. No listeners, saves, actions or world mutation. */
public final class WinterCrisisConditions {
 private WinterCrisisConditions(){}
 public enum Truth {
  TRUE,FALSE,UNKNOWN;
  public Truth not(){return this==UNKNOWN?UNKNOWN:this==TRUE?FALSE:TRUE;}
  public Truth and(Truth b){return this==FALSE||b==FALSE?FALSE:this==UNKNOWN||b==UNKNOWN?UNKNOWN:TRUE;}
  public Truth or(Truth b){return this==TRUE||b==TRUE?TRUE:this==UNKNOWN||b==UNKNOWN?UNKNOWN:FALSE;}
  public static Truth of(boolean value){return value?TRUE:FALSE;}
 }
 public enum Type { BLIZZARD, OUTSIDE_SHELTER, NETWORK_OUTAGE, GENERATOR_OFF, FUSE_BURNT,
  LAMP_EMPTY, DOOR_LOCKED, ZONE_ACTIVE, OUTSIDE_REGION, SCENARIO_READY }
 public record Region(String dimension,double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
  public Region {
   Objects.requireNonNull(dimension);
   if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)
    ||minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("Invalid region");
  }
  public boolean contains(double x,double y,double z){return x>=minX&&x<=maxX&&y>=minY&&y<=maxY&&z>=minZ&&z<=maxZ;}
 }
 /** IDs and value addresses only; never retains an entity or world. */
 public record Signal(Type type,UUID player,Address address,String id,double threshold,Region region){
  public Signal {
   Objects.requireNonNull(type);
   if(!Double.isFinite(threshold)||threshold<0||threshold>1)throw new IllegalArgumentException("Threshold 0..1 required");
   switch(type){
    case BLIZZARD,OUTSIDE_SHELTER,ZONE_ACTIVE,OUTSIDE_REGION->Objects.requireNonNull(player);
    case GENERATOR_OFF,FUSE_BURNT,LAMP_EMPTY,DOOR_LOCKED->Objects.requireNonNull(address);
    case NETWORK_OUTAGE,SCENARIO_READY->Objects.requireNonNull(id);
   }
   if(type==Type.ZONE_ACTIVE)Objects.requireNonNull(id);
   if(type==Type.OUTSIDE_REGION)Objects.requireNonNull(region);
  }
  public int cost(){return type==Type.OUTSIDE_SHELTER?8:type==Type.NETWORK_OUTAGE?4:1;}
  public static Signal player(Type type,UUID player,String dimension,String zone,double threshold){
   return new Signal(type,player,new Address(dimension,0,0,0),zone,threshold,null);
  }
  public static Signal network(String id){return new Signal(Type.NETWORK_OUTAGE,null,null,id,0,null);}
  public static Signal device(Type type,Address address){return new Signal(type,null,address,null,0,null);}
 }
 @FunctionalInterface public interface Source { Truth sample(Signal signal) throws Exception; }
 public sealed interface Condition permits Atom,All,Any,Not {}
 public record Atom(Signal signal) implements Condition {public Atom{Objects.requireNonNull(signal);}}
 public record All(List<Condition> terms) implements Condition {public All{terms=WinterCrisisConditions.terms(terms);}}
 public record Any(List<Condition> terms) implements Condition {public Any{terms=WinterCrisisConditions.terms(terms);}}
 public record Not(Condition term) implements Condition {public Not{Objects.requireNonNull(term);}}
 private static List<Condition> terms(List<Condition> terms){
  if(terms.isEmpty()||terms.size()>32)throw new IllegalArgumentException("1..32 terms required");return List.copyOf(terms);
 }
 /** One scoped, memoized sample set. Caller closes at the end of the cycle. */
 public static final class Cycle implements AutoCloseable {
  private Source source;
  private int remaining;
  private final Map<Signal,Truth> samples=new HashMap<>();
  public Cycle(Source source,int budget){
   if(budget<1||budget>256)throw new IllegalArgumentException("Budget 1..256 required");
   this.source=Objects.requireNonNull(source);remaining=budget;
  }
  public Truth evaluate(Condition condition){
   if(source==null)throw new IllegalStateException("Closed cycle");
   return evaluate(Objects.requireNonNull(condition),0,new int[]{128});
  }
  private Truth evaluate(Condition c,int depth,int[] nodes){
   if(depth>16||--nodes[0]<0)return Truth.UNKNOWN;
   if(c instanceof Atom a)return sample(a.signal());
   if(c instanceof Not n)return evaluate(n.term(),depth+1,nodes).not();
   List<Condition> terms=c instanceof All a?a.terms():((Any)c).terms();
   Truth value=c instanceof All?Truth.TRUE:Truth.FALSE;
   for(Condition term:terms){
    Truth next=evaluate(term,depth+1,nodes);
    value=c instanceof All?value.and(next):value.or(next);
    if(c instanceof All&&value==Truth.FALSE||c instanceof Any&&value==Truth.TRUE)break;
   }
   return value;
  }
  private Truth sample(Signal signal){
   Truth prior=samples.get(signal);if(prior!=null)return prior;
   if(remaining<signal.cost()||samples.size()>=64)return Truth.UNKNOWN;
   remaining-=signal.cost();Truth value;
   try{value=Objects.requireNonNull(source.sample(signal));}catch(Exception failure){value=Truth.UNKNOWN;}
   samples.put(signal,value);return value;
  }
  public int remaining(){return remaining;}
  public void close(){source=null;samples.clear();remaining=0;}
 }
 /** Unknown/unloaded/missing/cached facts can never establish a condition. */
 public static <T> Truth observed(Reading<T> reading,Predicate<T> predicate){
  if(reading==null||reading.availability()!=Availability.AVAILABLE||reading.value().isEmpty())return Truth.UNKNOWN;
  return Truth.of(predicate.test(reading.value().get()));
 }
 public record Stabilized(Truth value,boolean rising){}
 /** UNKNOWN breaks continuity and clears truth immediately, but does not rearm a fired episode.
  * A stable FALSE is required to rearm, so chunk flapping cannot create repeated triggers. */
 public static final class Tracker {
  private final long trueTicks,falseTicks,maxGap;
  private long last=Long.MIN_VALUE,since;
  private Truth candidate=Truth.UNKNOWN,stable=Truth.UNKNOWN;
  private boolean latched;
  public Tracker(long trueTicks,long falseTicks,long maxGap){
   if(trueTicks<0||falseTicks<0||maxGap<1)throw new IllegalArgumentException("Invalid timing");
   this.trueTicks=trueTicks;this.falseTicks=falseTicks;this.maxGap=maxGap;
  }
  public Stabilized update(Truth raw,long tick){
   Objects.requireNonNull(raw);
   if(last!=Long.MIN_VALUE&&tick==last){
    if(raw==Truth.UNKNOWN){candidate=Truth.UNKNOWN;stable=Truth.UNKNOWN;}
    return new Stabilized(raw==candidate?stable:Truth.UNKNOWN,false);
   }
   if(last!=Long.MIN_VALUE&&(tick<last||tick-last>maxGap)){candidate=Truth.UNKNOWN;stable=Truth.UNKNOWN;}
   last=tick;
   if(raw==Truth.UNKNOWN){candidate=Truth.UNKNOWN;stable=Truth.UNKNOWN;return new Stabilized(stable,false);}
   if(raw!=candidate){candidate=raw;since=tick;}
   long delay=raw==Truth.TRUE?trueTicks:falseTicks;
   if(tick-since<delay)return new Stabilized(Truth.UNKNOWN,false);
   stable=raw;
   if(raw==Truth.FALSE){latched=false;return new Stabilized(stable,false);}
   boolean rising=!latched;latched=true;return new Stabilized(stable,rising);
  }
 }
 /** Numeric hysteresis is opt-in, local to an observation scope; never changes game thresholds. */
 public static final class Hysteresis {
  private final double enter,exit;
  private Truth last=Truth.UNKNOWN;
  public Hysteresis(double enter,double exit){
   if(!Double.isFinite(enter)||!Double.isFinite(exit)||exit>enter)throw new IllegalArgumentException("exit <= enter");
   this.enter=enter;this.exit=exit;
  }
  public Truth above(OptionalDouble value){
   if(value.isEmpty()||!Double.isFinite(value.getAsDouble())){last=Truth.UNKNOWN;return last;}
   double n=value.getAsDouble();
   last=n>=enter?Truth.TRUE:n<=exit?Truth.FALSE:last;return last;
  }
 }
}
