package dev.whiteout.power;

import java.util.*;

/** Pure ordering logic; never rearranges the world's actual weather-zone list. */
public final class ZoneFolderOrder {
 private ZoneFolderOrder(){}
 /** Drop deleted/moved zones and append newly encountered entries in their original order. */
 public static void reconcile(List<String> ordered,Collection<String> actual){
  Set<String> active=new LinkedHashSet<>(actual);
  Set<String> seen=new HashSet<>();
  ordered.removeIf(id->!active.contains(id)||!seen.add(id));
  for(String id:active)if(seen.add(id))ordered.add(id);
 }
 public static boolean move(List<String> ordered,String id,int delta){
  int at=ordered.indexOf(id);int target=at+delta;
  if(at<0||target<0||target>=ordered.size())return false;
  Collections.swap(ordered,at,target);
  return true;
 }
}
