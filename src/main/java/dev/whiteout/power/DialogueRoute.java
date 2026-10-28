package dev.whiteout.power;

import dev.whiteout.StoryRules;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import dev.whiteout.power.WinterCrisisConditions.Truth;

/** References existing scripts only. No dialogue text, portraits or world objects are stored here. */
public record DialogueRoute(String id,String fallback,List<Variant> variants) {
 public static final String PREFIX="whiteout-route:";
 public static final int MAX_ROUTES=64,MAX_VARIANTS=16;
 public static boolean reference(String value){return value!=null&&value.startsWith(PREFIX)&&value.substring(PREFIX.length()).matches("[a-z0-9_-]{1,64}");}
 public enum Type { NETWORK,GENERATOR,WEATHER_ZONE,BLIZZARD,STORY }
 public record Condition(Type type,String target,String dimension,int x,int y,int z,boolean expected,String value,StoryRules.Operator operator) {
  public void validate(){
   if(type==null)throw new IllegalArgumentException("condition");
   bounded(target,128);bounded(dimension,128);bounded(value,64);
   switch(type){
    case NETWORK,WEATHER_ZONE->{if(target.isBlank())throw new IllegalArgumentException("target");}
    case GENERATOR->{if(!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||y< -2048||y>2048)throw new IllegalArgumentException("position");}
    case STORY->{storyCondition().validate();}
    default->{}
   }
  }
  public StoryRules.Condition storyCondition(){
   StoryRules.Condition c=new StoryRules.Condition();c.variable=target;c.operator=operator;c.value=StoryRules.parse(value);return c;
  }
  public Truth storyTruth(Map<String,StoryRules.Value> variables){
   if(variables==null||!variables.containsKey(target))return Truth.UNKNOWN;
   var actual=variables.get(target);var check=storyCondition();
   if(actual==null||actual.type!=check.value.type)return Truth.UNKNOWN;
   return Truth.of(check.test(variables));
  }
 }
 public record Variant(String dialogue,int priority,Condition condition){}
 public DialogueRoute {variants=variants==null?null:List.copyOf(variants);}
 public void validate(){
  if(id==null||!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("id");
  script(fallback);
  if(variants==null||variants.isEmpty()||variants.size()>MAX_VARIANTS)throw new IllegalArgumentException("variants");
  for(var v:variants){
   if(v==null||v.condition==null||v.priority< -10000||v.priority>10000)throw new IllegalArgumentException("variant");
   script(v.dialogue);v.condition.validate();
  }
 }
 private static void bounded(String value,int max){if(value==null||value.length()>max||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("text");}
 private static void script(String id){bounded(id,128);if(id.isBlank()||id.startsWith(PREFIX))throw new IllegalArgumentException("dialogue");}
 /** Stable sorting: priority descending, authored order for ties. UNKNOWN never matches either polarity. */
 public String select(Function<Condition,Truth> source){
  validate();
  for(var v:variants.stream().sorted(Comparator.comparingInt(Variant::priority).reversed()).toList()){
   Truth truth;try{truth=source.apply(v.condition);}catch(RuntimeException ex){truth=Truth.UNKNOWN;}
   if(truth==Truth.TRUE)return v.dialogue;
  }
  return fallback;
 }
 public static String playable(String selected,String fallback,Predicate<String> exists){
  if(selected!=null&&exists.test(selected))return selected;
  return fallback!=null&&exists.test(fallback)?fallback:null;
 }
}
