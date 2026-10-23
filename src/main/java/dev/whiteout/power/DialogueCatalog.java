package dev.whiteout.power;

import dev.whiteout.client.DialogueManager;
import dev.whiteout.client.StoryDialogueEditorScreen;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Read-only index of the ORIGINAL Whiteout dialogue registry and mod-authored fallback scripts.
 * User edits go through StoryDialogueEditorScreen and DialogueManager.saveScript(), not another store.
 */
public final class DialogueCatalog {
 public static final String OUTAGE_ID="whiteout_power_outage";
 public static final String FUSE_ID="whiteout_fuse_failure";
 public static final String OUTAGE_TEXT="Бля, опять предохранитель перегорел, надо выйти в подвал за новым и перезапустить генератор";
 private static final Field SCRIPTS = findRegistry();
 private DialogueCatalog(){}

 private static Field findRegistry(){
  try {Field f=DialogueManager.class.getDeclaredField("SCRIPTS");f.setAccessible(true);return f;}
  catch(ReflectiveOperationException e){throw new IllegalStateException("Whiteout DialogueManager registry incompatible",e);}
 }

 @SuppressWarnings("unchecked")
 private static Map<String,DialogueManager.Script> registry(){
  try {return (Map<String,DialogueManager.Script>)SCRIPTS.get(null);}
  catch(IllegalAccessException e){throw new IllegalStateException("Cannot read Whiteout dialogues",e);}
 }

 /** All loaded Whiteout scripts, including scripts created by the map author, plus built-in event lines.
  * Other mods' private dialogue systems have no common Minecraft dialogue registry. */
 public static List<Entry> entries(){
  Map<String,Entry> indexed=new LinkedHashMap<>();
  for(DialogueManager.Script script:new ArrayList<>(registry().values())){
   if(script!=null&&script.id()!=null&&!script.id().isBlank())
    indexed.put(script.id(),new Entry(script.id(),script.lines(),false));
  }
  if(!indexed.containsKey(OUTAGE_ID))
   indexed.put(OUTAGE_ID,new Entry(OUTAGE_ID,builtinOutage().lines(),true));
  if(!indexed.containsKey(FUSE_ID))
   indexed.put(FUSE_ID,new Entry(FUSE_ID,builtinFuseFailure().lines(),true));
  for(var line:LakeDialogueLines.entries())
   if(!indexed.containsKey(line.id()))indexed.put(line.id(),new Entry(line.id(),builtinLake(line.id()).lines(),true));
  return indexed.values().stream().sorted(Comparator.comparing(Entry::id,String.CASE_INSENSITIVE_ORDER)).toList();
 }

 public static DialogueManager.Script builtinOutage(){
  DialogueManager.Line line=new DialogueManager.Line("Я",OUTAGE_TEXT,"Я",0xFFE1E8F3,"player","",List.of(),null);
  return new DialogueManager.Script(OUTAGE_ID,List.of(),List.of(),List.of(line));
 }

 public static DialogueManager.Script builtinFuseFailure(){
  DialogueManager.Line line=new DialogueManager.Line("Я","Опять предохранитель выбило...","Я",0xFFE1E8F3,"player","",List.of(),null);
  return new DialogueManager.Script(FUSE_ID,List.of(),List.of(),List.of(line));
 }
 /** Editor-visible fallback; saved dialogue scripts replace it in the registry. */
 public static DialogueManager.Script builtinLake(String id){
  String text=LakeDialogueLines.defaultText(id);
  if(text==null)return null;
  var line=new DialogueManager.Line("Я",text,"Я",0xFFE1E8F3,"player","",List.of(),null);
  return new DialogueManager.Script(id,List.of(),List.of(),List.of(line));
 }
 public static boolean isBuiltin(String id){return OUTAGE_ID.equals(id)||FUSE_ID.equals(id)||LakeDialogueLines.isLakeId(id);}

 /** Materialize the hardcoded line as a user script only when the author actually asks to edit it. */
 public static boolean openEditor(Screen parent,String id){
  DialogueManager.Script script=DialogueManager.findById(id);
  backupBeforeEdit();
  if(script==null&&isBuiltin(id)){
   DialogueManager.Script fallback=OUTAGE_ID.equals(id)?builtinOutage():FUSE_ID.equals(id)?builtinFuseFailure():builtinLake(id);
   if(fallback==null||!DialogueManager.saveScript(fallback))return false;
   script=DialogueManager.findById(id);
  }
  if(script==null)return false;
  String speaker=script.lines().isEmpty()?id:script.lines().get(0).speaker();
  MinecraftClient.getInstance().setScreen(new StoryDialogueEditorScreen(parent,id,speaker));
  return true;
 }

 /** Remove one user-authored dialogue from the ORIGINAL dialogues.json, preserving all other
  * scripts, story metadata and unknown fields. Removing an override of the built-in outage
  * dialogue restores its bundled default on next use. A bundled-only line is not deletable.
  * Never change the registry without a successful on-disk write.
  */
 public static boolean deleteScript(String id) {
  if(id==null || id.isBlank())return false;
  Path source=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("whiteout/dialogues.json");
  if(!Files.isRegularFile(source))return false;
  Path temporary=null;
  try {
   JsonElement parsed=JsonParser.parseString(Files.readString(source,StandardCharsets.UTF_8));
   if(!parsed.isJsonObject())throw new IllegalStateException("dialogues.json: expected JSON object");
   JsonObject root=parsed.getAsJsonObject();
   JsonElement dialogues=root.get("dialogues");
   if(dialogues==null || !dialogues.isJsonObject())
    throw new IllegalStateException("dialogues.json: missing dialogues object");
   JsonObject scripts=dialogues.getAsJsonObject();
   if(!scripts.has(id))return false; // Built-in line with no user override: do not delete.

   // Backup EACH destructive action, including a second deletion during the same session.
   Path backup=source.resolveSibling("dialogues-before-delete-"+
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+
    UUID.randomUUID().toString().substring(0,8)+".json");
   Files.copy(source,backup);
   scripts.remove(id);
   String updated=new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n";
   temporary=Files.createTempFile(source.getParent(),"dialogues-write-",".tmp");
   Files.writeString(temporary,updated,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
   try {
    Files.move(temporary,source,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
   }catch(AtomicMoveNotSupportedException ex){
    Files.move(temporary,source,StandardCopyOption.REPLACE_EXISTING);
   }
   temporary=null;
   DialogueManager.load(); // Use the ORIGINAL loader, not a new data store.
   pruneBackups(source.getParent());
   return true;
  }catch(Exception ex){
   throw new IllegalStateException("Cannot delete dialogue '"+id+"': "+ex.getMessage(),ex);
  }finally {
   if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(Exception ignored){}
  }
 }

 private static String backupName;
 private static void backupBeforeEdit(){
  try {
   Path input=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("whiteout/dialogues.json");
   if(!Files.isRegularFile(input)||backupName!=null)return;
   String suffix=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
   Path output=input.resolveSibling("dialogues-before-catalog-"+suffix+".json");
   Files.copy(input,output);
   backupName=output.toString();
   pruneBackups(input.getParent());
  }catch(Exception ex){throw new IllegalStateException("Cannot back up dialogues.json",ex);}
 }
 private static void pruneBackups(Path directory){
  try(var stream=Files.list(directory)){
   List<Path> backups=stream.filter(p->p.getFileName().toString().matches("dialogues-before-(delete|catalog)-.*\\.json"))
    .filter(Files::isRegularFile).sorted(Comparator.comparing((Path p)->p.getFileName().toString()).reversed()).toList();
   for(int i=20;i<backups.size();i++)Files.deleteIfExists(backups.get(i));
  }catch(Exception e){System.err.println("[Whiteout] Cannot prune dialogue backups: "+e);}
 }

 public record Entry(String id,List<DialogueManager.Line> lines,boolean bundled){
  public String speaker(){return lines.isEmpty()?"":lines.get(0).speaker();}
  public String preview(){return lines.isEmpty()?"":lines.get(0).text();}
  public boolean matches(String needle){
   if(needle.isBlank())return true;
   if(id.toLowerCase(java.util.Locale.ROOT).contains(needle))return true;
   for(DialogueManager.Line line:lines){
    if(line.speaker().toLowerCase(java.util.Locale.ROOT).contains(needle)||line.text().toLowerCase(java.util.Locale.ROOT).contains(needle))return true;
   }
   return false;
  }
 }
}
