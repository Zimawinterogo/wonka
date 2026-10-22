package dev.whiteout.power;
import java.util.*;
import java.util.function.*;
/** One client resource per world/position and block-entity identity. */
public final class TransientInstances<K,O,V> {
 private record Entry<O,V>(O owner,V value){}
 private final Map<K,Entry<O,V>> entries=new HashMap<>();
 public V acquire(K key,O owner,Predicate<V> expired,Supplier<V> create,Consumer<V> stop){
  var old=entries.get(key);
  if(old!=null&&old.owner()==owner&&!expired.test(old.value()))return old.value();
  if(old!=null)stop.accept(old.value());
  V next=create.get();entries.put(key,new Entry<>(owner,next));return next;
 }
 public void removeIf(BiPredicate<K,V> invalid,Consumer<V> stop){
  entries.entrySet().removeIf(e->{if(!invalid.test(e.getKey(),e.getValue().value()))return false;stop.accept(e.getValue().value());return true;});
 }
 public void clear(Consumer<V> stop){entries.values().forEach(e->stop.accept(e.value()));entries.clear();}
 public int size(){return entries.size();}
}
