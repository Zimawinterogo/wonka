package dev.whiteout.power;

import java.util.*;

/** Read-only wire DTO, bounded independently of authored world size. */
public final class DiagnosticData {
 public static final int MAX_ROWS=2048;
 public record Detail(String key,String value,boolean translated){}
 public record Row(String id,String kind,String name,String state,List<Detail> details){}
 public record Snapshot(List<Row> rows,boolean truncated){}
 private DiagnosticData(){}
 public static boolean allowed(int permission,boolean spectator){return permission>=2&&!spectator;}
 public static List<Row> filter(List<Row> rows,String kind){return rows.stream().filter(r->kind.equals("all")||kind.equals(r.kind())).toList();}
 public static String reason(boolean invalid,boolean panelBound,boolean generatorBound,boolean panelSeen,boolean generatorSeen,
  boolean fuse,boolean burnt,boolean running,boolean fueled,boolean burnPending,boolean stopPending,boolean flicker){
  if(invalid)return "invalid";
  if(!panelBound||!generatorBound)return "unbound";
  if(!panelSeen||!generatorSeen)return "unknown";
  if(burnt||burnPending)return "burnt";
  if(!fuse)return "no_fuse";
  if(!fueled)return "no_fuel";
  if(!running||stopPending)return "stopped";
  return flicker?"flicker":"on";
 }
}
