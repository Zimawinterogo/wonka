import com.google.gson.*;
import dev.whiteout.power.*;

public final class AuditHardeningTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void invalid(String json){
  try{FolderDataValidation.validate(JsonParser.parseString(json));throw new AssertionError("Accepted: "+json);}
  catch(IllegalArgumentException | JsonParseException expected){}
 }
 public static void main(String[] args){
  FolderDataValidation.validate(JsonParser.parseString("{\"folders\":[\"Лес\"],\"assigned\":{\"a\":\"Лес\"}}"));
  FolderDataValidation.validate(JsonParser.parseString("{\"folders\":[],\"assigned\":{},\"order\":null}"));
  invalid("null");invalid("{}");invalid("{");
  invalid("{\"folders\":[12],\"assigned\":{}}");
  invalid("{\"folders\":[null],\"assigned\":{}}");
  invalid("{\"folders\":[\"a\",\"a\"],\"assigned\":{}}");
  invalid("{\"folders\":[\" a\"],\"assigned\":{}}");
  invalid("{\"folders\":[],\"assigned\":{\"a\":\"missing\"}}");
  invalid("{\"folders\":[],\"assigned\":{},\"order\":{\"\":[null]}}");
  invalid("{\"folders\":[],\"assigned\":{},\"order\":{\"\":[12]}}");
  invalid("{\"folders\":[],\"assigned\":{},\"order\":[]}");
  var gate=new RepeatedFailure();
  check(gate.report("disk",-100),"first report with negative nanoTime");
  check(!gate.report("disk",0),"same failure suppressed");
  check(gate.report("permissions",1),"different failure immediate");
  check(!gate.report("permissions",60_000_000_000L),"before boundary");
  check(gate.report("permissions",60_000_000_001L),"minute boundary");
  gate.clear();check(gate.report("permissions",60_000_000_002L),"recovery resets gate");
  System.out.println("PASS AuditHardeningTest: malformed folder data, legacy format, repeated errors");
 }
}
