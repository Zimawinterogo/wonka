package dev.whiteout.power;

import java.util.*;
import java.util.function.Supplier;

/** Server-thread-only bounded memoization. Expiry is checked on every read. */
final class WorldStateCache<K,V> {
 private record Entry<V>(long tick,V value){}
 private final int limit;
 private final long ttl;
 private final LinkedHashMap<K,Entry<V>> entries=new LinkedHashMap<>(16,.75f,true);
 private final Set<K> loading=new HashSet<>();
 private long revision;
 WorldStateCache(int limit,long ttl){
  if(limit<1||ttl<1)throw new IllegalArgumentException("positive cache bounds required");
  this.limit=limit;this.ttl=ttl;
 }
 V get(K key,long tick,Supplier<V> source){
  Entry<V> cached=entries.get(key);
  if(cached!=null&&tick>=cached.tick&&tick-cached.tick<ttl)return cached.value;
  if(!loading.add(key))throw new IllegalStateException("Recursive WorldState query: "+key);
  long before=revision;
  try{
   V value=Objects.requireNonNull(source.get());
   if(revision==before){
    entries.put(key,new Entry<>(tick,value));
    if(entries.size()>limit)entries.remove(entries.keySet().iterator().next());
   }
   return value;
  }finally{loading.remove(key);}
 }
 void clear(){revision++;entries.clear();}
 int size(){return entries.size();}
}
