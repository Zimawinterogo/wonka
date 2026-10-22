package dev.whiteout.power;
import java.util.*;
/** Bounded, ordered transport; never split a UTF-16 surrogate pair. */
public final class SnapshotChunks {
 public static final int SIZE=16000,MAX_PARTS=256;
 private SnapshotChunks(){}
 public static List<String> split(String json){
  if(json.length()>SIZE*(MAX_PARTS-1))return List.of();
  List<String> parts=new ArrayList<>();
  for(int start=0;start<json.length();){
   int end=Math.min(json.length(),start+SIZE);
   if(end<json.length()&&Character.isHighSurrogate(json.charAt(end-1)))end--;
   parts.add(json.substring(start,end));start=end;
  }
  if(parts.isEmpty())parts.add("");return parts;
 }
 public static final class Receiver {
  private StringBuilder pending;private int next,total;
  public void clear(){pending=null;next=0;total=0;}
  public String accept(int index,int count,String text){
   if(index==0){clear();if(count<1||count>MAX_PARTS)throw new IllegalArgumentException("Invalid snapshot count");pending=new StringBuilder();total=count;}
   if(pending==null||count!=total||index!=next||text.length()>SIZE){clear();throw new IllegalArgumentException("Invalid snapshot sequence");}
   pending.append(text);next++;
   if(next!=total)return null;
   String complete=pending.toString();clear();return complete;
  }
 }
}
