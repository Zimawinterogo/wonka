package dev.whiteout.power;

import com.google.gson.*;
import dev.whiteout.StoryRules;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-authored permissions, not another progress store. Never writes a world file. */
public final class StoryEventCatalog {
 public static final int MAX_BYTES=262144,MAX_EVENTS=128,MAX_REQUEST_BYTES=8192;
 private static final Gson JSON=new Gson();
 /** Half-open exact bounds, matching normalized legacy ENTER_BOX geometry. */
 public record Box(double x1,double y1,double z1,double x2,double y2,double z2){
  public void validate(){
   double[] lo={x1,y1,z1},hi={x2,y2,z2};
   for(int i=0;i<3;i++)if(!Double.isFinite(lo[i])||!Double.isFinite(hi[i])||lo[i]>=hi[i]||hi[i]-lo[i]>256||Math.abs(lo[i])>(i==1?2048:30000000)||Math.abs(hi[i])>(i==1?2048:30000000))throw new IllegalArgumentException("box");
  }
  public boolean contains(double x,double y,double z){return x>=x1&&x<x2&&y>=y1&&y<y2&&z>=z1&&z<z2;}
 }
 public record Area(String dimension,double x,double y,double z,double radius,Box box){
  public Area(String dimension,double x,double y,double z,double radius){this(dimension,x,y,z,radius,null);}
  public void validate(){
   if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||dimension.length()>128)throw new IllegalArgumentException("dimension");
   if(box!=null){box.validate();if(x!=0||y!=0||z!=0||radius!=0)throw new IllegalArgumentException("ambiguous area");return;}
   if(
      !Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000||
      y < -2048||y>2048||!Double.isFinite(radius)||radius<=0||radius>128)throw new IllegalArgumentException("area");
  }
  public boolean contains(String dimension,double x,double y,double z){
   if(box!=null)return this.dimension.equals(dimension)&&box.contains(x,y,z);
   double dx=this.x-x,dy=this.y-y,dz=this.z-z;
   return this.dimension.equals(dimension)&&Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz)&&dx*dx+dy*dy+dz*dz<=radius*radius;
  }
 }
 public record Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world,boolean serverOnly){
  public Event { objectives=objectives==null?List.of():List.copyOf(objectives); }
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions){this(key,once,area,conditions,actions,List.of(),null,false);}
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives){this(key,once,area,conditions,actions,objectives,null,false);}
  public Event(String key,boolean once,Area area,List<StoryRules.Condition> conditions,List<StoryRules.Action> actions,List<StoryObjectiveActions.Change> objectives,StoryWorldConditions.Node world){this(key,once,area,conditions,actions,objectives,world,false);}
  public void validate(){
   if(key==null||!key.matches("[a-zA-Z0-9_.:/-]{1,128}")||area==null||conditions==null||actions==null)throw new IllegalArgumentException("event");
   area.validate();StoryRules.validate(conditions,actions);StoryObjectiveActions.validate(objectives);
   if(world!=null)world.validate();
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
 /** Legacy packets may contain actions/conditions/once; only the key has authority as a request. */
 public Event resolve(byte[] payload){
  if(invalid())throw new IllegalArgumentException("invalid server catalog: "+error);
  if(payload==null||payload.length>MAX_REQUEST_BYTES)throw new IllegalArgumentException("request size");
  JsonObject request=JsonParser.parseString(new String(payload,StandardCharsets.UTF_8)).getAsJsonObject();
  JsonElement key=request.get("key");
  if(key==null||!key.isJsonPrimitive()||!key.getAsJsonPrimitive().isString()||key.getAsString().length()>128)throw new IllegalArgumentException("key");
  Event event=find(key.getAsString());
  if(event==null)throw new IllegalArgumentException("unregistered story key");
  return event;
 }
 /** Defensive copy: StoryRules DTOs are mutable in the existing base. */
 public Event find(String key){String json=events.get(key);return json==null?null:JSON.fromJson(json,Event.class);}
}
