import dev.whiteout.power.*;
import com.google.gson.Gson;
import java.util.*;

public class PassportRulesTest {
 private static void require(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  var b=new MapPassport.Builder();var pos=new MapPassport.Position("minecraft:overworld",1,2,3);
  PassportRules.device(b,"unloaded",pos,PassportRules.Observation.UNLOADED,false,false);
  require(b.build().issues().size()==1&&b.build().issues().getFirst().severity()==PassportIssue.Severity.INFO);
  PassportRules.device(b,"live",pos,PassportRules.Observation.PRESENT,false,false);
  require(b.build().issues().stream().filter(i->i.severity()==PassportIssue.Severity.WARN).count()==2);
  PassportRules.network(b,"empty",false,false,0,false);
  require(b.build().issues().stream().anyMatch(i->i.code().equals("unbound")));
  PassportRules.reference(b,"door","deleted",Set.of("main"),pos);
  require(b.build().issues().stream().anyMatch(i->i.code().equals("missing_reference")));
  int before=b.build().issues().size();
  PassportRules.dialogue(b,"builtin",true,false);PassportRules.dialogue(b,"scene-event",false,true);
  require(b.build().issues().size()==before);
  PassportRules.dialogue(b,"unused",false,false);require(b.build().issues().size()==before+1);
  require(!PassportRules.bounds(0,0,0,Double.NaN,1,1));require(!PassportRules.bounds(1,0,0,0,1,1));
  var a=new PassportRules.Zone("a","world",true,false,1,0,0,0,2,2,2);
  var same=new PassportRules.Zone("b","world",true,false,1,1,1,1,3,3,3);
  var other=new PassportRules.Zone("b","other",true,false,1,1,1,1,3,3,3);
  require(PassportRules.overlaps(a,same));require(!PassportRules.overlaps(a,other));
  b.entry("zones","snow ☃",pos,Map.of("name","Белая мгла"));
  Gson gson=new Gson();var report=b.build();
  require(report.equals(gson.fromJson(gson.toJson(report),MapPassport.class)));
  require(new MapPassport.Position("bad\ncommand",0,0,0).teleport().isEmpty());
  require(pos.teleport().equals("/execute in minecraft:overworld run tp @s 1 2 3"));
  for(int i=0;i<MapPassport.LIMIT+1;i++)b.entry("zones","large",pos,Map.of("value","x".repeat(1000)));
  require(b.build().truncated()&&b.build().entries().size()==MapPassport.LIMIT);
  String json=gson.toJson(b.build());var receiver=new SnapshotChunks.Receiver();String result=null;
  var parts=SnapshotChunks.split(json);require(!parts.isEmpty());
  for(int i=0;i<parts.size();i++)result=receiver.accept(i,parts.size(),parts.get(i));
  require(json.equals(result));
  System.out.println("PASS passport rules, unknown observations, references, scene dialogue, JSON and bounded transport");
 }
}
