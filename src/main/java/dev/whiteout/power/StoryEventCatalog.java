package dev.whiteout.power;

import com.google.gson.Gson;
import dev.whiteout.StoryRules;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server-authored permissions, not another progress store. Never writes a world file. */
public final class StoryEventCatalog {
 public static final int MAX_BYTES=262144,MAX_EVENTS=128;
 private static final Gson JSON=new Gson();
 public record Area(String dimension,double x,double y,double z,double radius){
  public void validate(){
   if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||dimension.length()>128||
      !Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000||
      y < -2048||y>2048||!Double.isFinite(radius)||radius<=0||radius>128)throw new IllegalArgumentException("area");
  }
  public boolean contains(String dimension,double x,double y,double z){
   double dx=this.x-x,dy=this.y-y,dz=this.z-z;
   return this.dimension.equals(dimension)&&Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz)&&dx*dx+dy*dy+dz*dz<=radius*radius;
  }
 }
 public record Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions){
  public void validate(){
   if(key==null||!key.matches("[a-zA-Z0-9_.:/-]{1,128}")||area==null||conditions==null||actions==null||actions.isEmpty())throw new IllegalArgumentException("event");
   area.validate();StoryRules.validate(conditions,actions);
  }
 }
 public record Data(int schema,List<Event> events){}
 private final Map<String,String> events=new LinkedHashMap<>();
 private String error="";
 public StoryEventCatalog(Path path){
  if(!Files.exists(path))return;
  try{
   if(Files.size(path)>MAX_BYTES)throw new IOException("size");
   Data data=JSON.fromJson(Files.readString(path),Data.class);
   if(data==null||data.schema()!=1||data.events()==null||data.events().size()>MAX_EVENTS)throw new IllegalArgumentException("schema/events");
   for(Event event:data.events()){
    if(event==null)throw new IllegalArgumentException("null event");event.validate();
    if(events.putIfAbsent(event.key(),JSON.toJson(event))!=null)throw new IllegalArgumentException("duplicate key");
   }
  }catch(Exception failure){events.clear();error=failure.getClass().getSimpleName()+": "+failure.getMessage();}
 }
 public boolean invalid(){return !error.isEmpty();}
 public String error(){return error;}
 public int size(){return events.size();}
 /** Defensive copy: StoryRules DTOs are mutable in the existing base. */
 public Event find(String key){String json=events.get(key);return json==null?null:JSON.fromJson(json,Event.class);}
}
