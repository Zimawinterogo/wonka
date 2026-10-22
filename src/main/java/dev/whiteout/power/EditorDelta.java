package dev.whiteout.power;

import com.google.gson.*;
import java.util.*;

/** Sparse, immutable inverse patch. Arrays are atomic so order is preserved. */
public final class EditorDelta {
 public record Change(List<String> path, JsonElement before, JsonElement after) {}
 private final List<Change> changes=new ArrayList<>();
 public EditorDelta(JsonObject before,JsonObject after){diff(List.of(),before,after);}
 private void diff(List<String> path,JsonElement a,JsonElement b){
  if(Objects.equals(a,b))return;
  if(a!=null&&b!=null&&a.isJsonObject()&&b.isJsonObject()){
   Set<String> keys=new LinkedHashSet<>(a.getAsJsonObject().keySet());keys.addAll(b.getAsJsonObject().keySet());
   for(String k:keys){List<String> next=new ArrayList<>(path);next.add(k);diff(List.copyOf(next),a.getAsJsonObject().get(k),b.getAsJsonObject().get(k));}
  }else changes.add(new Change(path,a==null?null:a.deepCopy(),b==null?null:b.deepCopy()));
 }
 public boolean empty(){return changes.isEmpty();}
 public int bytes(){int n=0;for(Change c:changes)n+=64+2*(c.path.toString().length()+String.valueOf(c.before).length()+String.valueOf(c.after).length());return n;}
 public boolean touches(String root){return changes.stream().anyMatch(c->c.path.getFirst().equals(root));}
 public JsonObject undo(JsonObject current){
  JsonObject result=current.deepCopy();
  for(Change c:changes){
   JsonObject parent=result;
   for(int i=0;i<c.path.size()-1;i++){
    JsonElement child=parent.get(c.path.get(i));
    if(child==null||!child.isJsonObject())throw new IllegalStateException("Состояние изменилось другим действием; отмена отклонена.");
    parent=child.getAsJsonObject();
   }
   String key=c.path.getLast();
   if(!Objects.equals(parent.get(key),c.after))throw new IllegalStateException("Состояние изменилось другим действием; отмена отклонена.");
   if(c.before==null)parent.remove(key);else parent.add(key,c.before.deepCopy());
  }
  return result;
 }
}
