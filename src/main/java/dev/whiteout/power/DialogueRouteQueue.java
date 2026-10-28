package dev.whiteout.power;

import java.util.*;

/** FIFO barrier for asynchronous route resolution; duplicate activations are coalesced. */
public final class DialogueRouteQueue<T> {
 public record Entry<T>(long token,String route,T value){}
 private final ArrayDeque<Entry<T>> pending=new ArrayDeque<>();
 private final Map<String,Long> lastTick=new HashMap<>();
 private long sequence;
 public Entry<T> offer(String route,T value,long tick){
  if(pending.size()>=32)return null;
  if(route!=null){
   if(lastTick.getOrDefault(route,Long.MIN_VALUE)==tick||pending.stream().anyMatch(e->route.equals(e.route)))return null;
   lastTick.entrySet().removeIf(e->e.getValue()!=tick);lastTick.put(route,tick);
  }
  var entry=new Entry<T>(++sequence,route,value);pending.add(entry);return entry;
 }
 public Entry<T> head(){return pending.peek();}
 public Entry<T> complete(long token){return pending.peek()!=null&&pending.peek().token==token?pending.poll():null;}
 public boolean empty(){return pending.isEmpty();}
 public void clear(){pending.clear();lastTick.clear();/* Tokens never reused across world sessions. */}
}
