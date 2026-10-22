import dev.whiteout.power.*;
import com.google.gson.Gson;
import java.util.*;
public final class DiagnosticDataTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int permission=0;permission<=4;permission++)for(boolean spectator:new boolean[]{false,true})
   check(DiagnosticData.allowed(permission,spectator)==(permission>=2&&!spectator),"permission");
  for(int mask=0;mask<1024;mask++){
   boolean[] b=new boolean[10];for(int i=0;i<10;i++)b[i]=(mask&(1<<i))!=0;
   boolean power=GridStatePolicy.supplied(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9]);
   String reason=DiagnosticData.reason(false,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],false);
   check(reason.equals("on")==power,"diagnostic differs from real power rule");
   check(DiagnosticData.reason(true,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],false).equals("invalid"),"invalid config");
  }
  List<DiagnosticData.Row> rows=new ArrayList<>();
  for(String kind:List.of("networks","generators","panels","zones"))rows.add(new DiagnosticData.Row(kind,kind,"Дом ❄", "on",List.of(new DiagnosticData.Detail("folder","Лес",false))));
  check(DiagnosticData.filter(rows,"all").size()==4,"all filter");
  for(var row:rows)check(DiagnosticData.filter(rows,row.kind()).equals(List.of(row)),"type filter");
  check(DiagnosticData.filter(rows,"missing").isEmpty(),"empty filter");
  var snapshot=new DiagnosticData.Snapshot(rows,false);Gson gson=new Gson();
  check(snapshot.equals(gson.fromJson(gson.toJson(snapshot),DiagnosticData.Snapshot.class)),"snapshot wire round trip");
  System.out.println("PASS DiagnosticDataTest: permission matrix, 1024 power states, filters, Unicode wire format");
 }
}
