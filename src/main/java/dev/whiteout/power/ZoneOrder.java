package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.Zone;
import java.util.*;

/** Preserve the original order of existing zones when the base SAVE_ZONE handler
 * replaces a zone by removeIf+add. New zones are appended once; deleted zones
 * stay deleted. Never sort by name, priority or zone id. */
public final class ZoneOrder {
 private ZoneOrder(){}
 public static void preserve(Store store,List<Zone> next){
  if(store==null || store.data==null || store.data.zones==null || next==null)return;
  Map<String,Zone> updated=new LinkedHashMap<>();
  for(Zone z:next)if(z!=null&&z.id!=null)updated.put(z.id,z);
  List<Zone> result=new ArrayList<>(updated.size());
  for(Zone original:store.data.zones){
   if(original!=null&&original.id!=null){Zone replacement=updated.remove(original.id);if(replacement!=null)result.add(replacement);}
  }
  result.addAll(updated.values());
  next.clear();next.addAll(result);
 }
}
