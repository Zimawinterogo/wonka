package dev.whiteout.power;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Bounded network packets, independent of the number of configured old shelters. */
public final class ZoneIdBatcher {
 public static final int MAX_CHARS=8192;
 private ZoneIdBatcher(){}
 public static List<String> batches(Collection<String> ids){
  if(ids==null||ids.isEmpty())return List.of();
  List<String> valid=new ArrayList<>();
  for(String id:ids)if(id!=null&&!id.isBlank()&&id.length()<=96&&id.indexOf('\n')<0&&id.indexOf('\r')<0)valid.add(id);
  Collections.sort(valid);
  List<String> result=new ArrayList<>();
  StringBuilder current=new StringBuilder();
  for(String id:valid){
   if(current.length()>0&&current.length()+1+id.length()>MAX_CHARS){result.add(current.toString());current.setLength(0);}
   if(current.length()>0)current.append('\n');
   current.append(id);
  }
  if(current.length()>0)result.add(current.toString());
  return result;
 }
}
