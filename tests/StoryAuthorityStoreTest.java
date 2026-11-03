import dev.whiteout.Store;
import dev.whiteout.StoryRules;
import dev.whiteout.power.*;
import java.nio.file.*;

/** Inject a real Store write failure; check disk and in-memory rollback, then reopen. */
public final class StoryAuthorityStoreTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Store open(Path path)throws Exception{
  var ctor=Store.class.getDeclaredConstructor(Path.class);ctor.setAccessible(true);return ctor.newInstance(path);
 }
 public static void main(String[] args)throws Exception{
  Path root=Path.of("build","story-store-test");Files.createDirectories(root);
  Path dir=Files.createTempDirectory(root,"save-");Path path=dir.resolve("zones.json");
  Path temporary=path.resolveSibling("zones.json.tmp");
  try{
   Store store=open(path);store.data.storyVariables.put("chapter",StoryRules.Value.of(1));store.data.completedEvents.add("other");store.save();
   String before=Files.readString(path);var variables=store.data.storyVariables;var completed=store.data.completedEvents;
   Files.createDirectory(temporary);
   try{StoryCommitTransaction.apply(store,StoryAuthorityTest.event());throw new AssertionError("disk error accepted");}catch(java.io.IOException expected){}
   check(store.data.storyVariables==variables&&store.data.completedEvents==completed,"references rolled back");
   check(store.data.storyVariables.get("chapter").integer==1&&!completed.contains("test:bridge"),"failed write did not advance progress");
   check(Files.readString(path).equals(before),"live save unchanged");Files.delete(temporary);
   store=open(path);check(StoryCommitTransaction.apply(store,StoryAuthorityTest.event())==StoryCommitTransaction.Result.COMMITTED,"retry after reopen");
   Store reopened=open(path);check(reopened.data.storyVariables.get("chapter").integer==2,"persisted variable");
   check(reopened.data.completedEvents.contains("other"),"other scene preserved");
   check(StoryCommitTransaction.apply(reopened,StoryAuthorityTest.event())==StoryCommitTransaction.Result.ALREADY_COMPLETED,"restart cannot repeat once");
   Files.writeString(path,"{broken");Store corrupt=open(path);
   try{StoryCommitTransaction.apply(corrupt,StoryAuthorityTest.event());throw new AssertionError("corrupt store accepted");}catch(IllegalStateException expected){}
   check(Files.readString(path).equals("{broken"),"corrupt save preserved");
  }finally{Files.deleteIfExists(temporary);Files.deleteIfExists(path.resolveSibling("zones.json.bak"));Files.deleteIfExists(path);Files.deleteIfExists(dir);}
  System.out.println("PASS StoryAuthorityStoreTest: real Store failure, rollback, reopen, once and corrupt save");
 }
}
