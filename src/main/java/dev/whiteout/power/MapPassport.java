package dev.whiteout.power;

import java.util.*;

/** Separate versioned report, not a world configuration format. */
public record MapPassport(int schema,List<Entry> entries,List<PassportIssue> issues,boolean truncated) {
 public static final int LIMIT=2048;
 public record Position(String dimension,int x,int y,int z) {
  public String teleport(){return dimension!=null&&dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")?
   "/execute in "+dimension+" run tp @s "+x+" "+y+" "+z:"";}
 }
 public record Entry(String section,String id,Position position,Map<String,String> values) {}
 public static final class Builder {
  private final List<Entry> entries=new ArrayList<>();
  private final List<PassportIssue> issues=new ArrayList<>();
  private boolean truncated;
  public void entry(String section,String id,Position position,Map<String,String> values){
   if(entries.size()>=LIMIT){truncated=true;return;}
   Map<String,String> bounded=new LinkedHashMap<>();
   values.forEach((k,v)->{if(bounded.size()<32)bounded.put(text(k),text(v));else truncated=true;});
   entries.add(new Entry(section,text(id),position,bounded));
  }
  private String text(String s){if(s==null)return "";if(s.length()>512){truncated=true;return s.substring(0,512);}return s;}
  public void issue(PassportIssue.Severity severity,String code,String id,Position p){
   if(issues.size()>=LIMIT){truncated=true;return;}issues.add(new PassportIssue(severity,code,text(id),p));
  }
  public void add(MapPassport report){
   for(var e:report.entries())entry(e.section(),e.id(),e.position(),e.values());
   for(var i:report.issues())issue(i.severity(),i.code(),i.object(),i.position());
   truncated|=report.truncated();
  }
  public MapPassport build(){return new MapPassport(1,List.copyOf(entries),List.copyOf(issues),truncated);}
 }
}
