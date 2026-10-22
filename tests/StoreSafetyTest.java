import dev.whiteout.Store;
import dev.whiteout.Zone;
import java.nio.file.*;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

/** Uses only disposable files under build, never a user world. */
public final class StoreSafetyTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Store open(Path path)throws Exception{
  Constructor<Store> constructor=Store.class.getDeclaredConstructor(Path.class);
  constructor.setAccessible(true);return constructor.newInstance(path);
 }
 static void rejected(Store store)throws Exception{
  try{store.save();throw new AssertionError("Invalid store saved");}
  catch(java.io.IOException expected){}
 }
 public static void main(String[] args)throws Exception{
  Path scratch=Path.of("build","audit-test-tmp").toAbsolutePath();Files.createDirectories(scratch);
  Path directory=Files.createTempDirectory(scratch,"store-");
  Path path=directory.resolve("zones.json");
  Store store=open(path);check(store.error==null,"missing file supported");store.save();
  String valid=Files.readString(path);
  Store loaded=open(path);check(loaded.error==null,"roundtrip");
  loaded.data.zones=new ArrayList<>();loaded.data.zones.add(null);rejected(loaded);
  check(valid.equals(Files.readString(path)),"invalid write preserved original");
  Files.writeString(path,"{broken");Store corrupt=open(path);check(corrupt.error!=null,"corruption flagged");rejected(corrupt);
  check(Files.readString(path).equals("{broken"),"corrupt original retained");
  Files.writeString(path,valid);Store blocked=open(path);
  Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.createDirectory(temp);
  rejected(blocked);check(Files.readString(path).equals(valid),"failed temporary write preserved original");
  Files.delete(temp);
  Zone zone=new Zone();zone.validate();zone.x=Double.NaN;
  try{zone.validate();throw new AssertionError("NaN coordinate accepted");}catch(IllegalArgumentException expected){}
  Zone badRadius=new Zone();badRadius.radius=-1;
  try{badRadius.validate();throw new AssertionError("Negative radius accepted");}catch(IllegalArgumentException expected){}
  Files.writeString(path,valid.replace("\"zones\": []","\"zones\": null"));
  // Do not depend on Gson pretty-print spacing for the malformed fixture.
  var json=com.google.gson.JsonParser.parseString(valid).getAsJsonObject();json.add("zones",com.google.gson.JsonNull.INSTANCE);
  Files.writeString(path,json.toString());check(open(path).error!=null,"null mandatory collection rejected");
  Files.writeString(path,valid);open(path).save();
  check(Files.readString(path.resolveSibling(path.getFileName()+".bak")).equals(valid),"backup retains original");
  Files.deleteIfExists(path.resolveSibling(path.getFileName()+".bak"));Files.delete(path);Files.delete(directory);
  System.out.println("PASS StoreSafetyTest: actual base Store missing/corrupt files, rejected saves and write failure");
 }
}
