package dev.whiteout.power;

import java.util.*;
import java.util.function.Predicate;

/** Connection-local evidence. The only persistent Once ledger is Store.completedEvents. */
public final class TriggerEvidence {
 public enum Kind { ENTER_BOX, EXIT_BOX, INTERACT_ENTITY, SIGNAL, CHOICE }
 public record Binding(Kind type,int delayTicks,String target,List<String> nextChoices,String emit){
  public Binding {
   Objects.requireNonNull(type);
   if(delayTicks<0||delayTicks>72000)throw new IllegalArgumentException("trigger delay");
   target=Objects.requireNonNullElse(target,"");
   if(type==Kind.INTERACT_ENTITY&&!UUID.fromString(target).toString().equalsIgnoreCase(target))throw new IllegalArgumentException("NPC UUID");
   if(type==Kind.SIGNAL&&(target.isBlank()||target.length()>128))throw new IllegalArgumentException("signal target");
   nextChoices=nextChoices==null?List.of():List.copyOf(nextChoices);
   if(nextChoices.size()>128||nextChoices.stream().anyMatch(k->!k.startsWith("dialogue_choice:")||k.length()>128))throw new IllegalArgumentException("choice keys");
   if(emit!=null&&(emit.isBlank()||emit.length()>128))throw new IllegalArgumentException("signal name");
  }
  boolean zone(){return type==Kind.ENTER_BOX||type==Kind.EXIT_BOX;}
 }
 public record Rule(String key,StoryEventCatalog.Area area,Binding binding){}
 public record Body(String dimension,double x1,double y1,double z1,double x2,double y2,double z2){
  boolean intersects(StoryEventCatalog.Area area){
   var b=area.box();
   return dimension.equals(area.dimension())&&x2>b.x1()&&x1<b.x2()&&y2>b.y1()&&y1<b.y2()&&z2>b.z1()&&z1<b.z2();
  }
 }
 private record Pending(long due,boolean offered){}
 private final Map<String,Boolean> inside=new HashMap<>();
 private final Map<String,Pending> pending=new LinkedHashMap<>();
 private Set<String> choices=Set.of();
 private long choiceDeadline;
 public void arm(Rule rule,long tick){
  pending.putIfAbsent(rule.key(),new Pending(tick+rule.binding().delayTicks(),false));
 }
 public boolean permits(Rule rule,long tick){
  if(rule.binding().type()==Kind.CHOICE)return tick<=choiceDeadline&&choices.contains(rule.key());
  var p=pending.get(rule.key());return p!=null&&p.offered()&&tick-p.due()<=200;
 }
 public void consume(Rule rule,long tick){
  pending.remove(rule.key());
  if(rule.binding().type()==Kind.CHOICE||!rule.binding().nextChoices().isEmpty()){
   choices=Set.copyOf(rule.binding().nextChoices());choiceDeadline=tick+1200;
  }
 }
 public void observe(Rule rule,Body body,long tick,Predicate<String> eligible,java.util.function.Consumer<String> offer){
  Binding binding=rule.binding();
  if(binding.zone()){
   if(!body.dimension().equals(rule.area().dimension())){inside.remove(rule.key());pending.remove(rule.key());return;}
   boolean hit=body.intersects(rule.area());
   Boolean before=inside.put(rule.key(),hit);
   boolean expected=hit==(binding.type()==Kind.ENTER_BOX);
   if(!expected){pending.remove(rule.key());return;}
   if(before!=null&&before!=hit&&eligible.test(rule.key()))arm(rule,tick);
  }
  Pending p=pending.get(rule.key());
  if(p==null)return;
  if(tick-p.due()>200||!eligible.test(rule.key())){pending.remove(rule.key());return;}
  if(tick>=p.due()&&!p.offered()){
   pending.put(rule.key(),new Pending(p.due(),true));offer.accept(rule.key());
  }
 }
}
