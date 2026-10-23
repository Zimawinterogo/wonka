import dev.whiteout.power.LakeDialogueLines;
import java.util.HashSet;
public class LakeDialogueLinesTest {
 public static void main(String[] args){
  var lines=LakeDialogueLines.entries();
  if(lines.size()!=10)throw new AssertionError("Expected ten lines");
  var ids=new HashSet<String>();
  for(var line:lines){
   if(!ids.add(line.id())||!line.id().matches("lake_[0-9]{2}_[a-z_]+"))
    throw new AssertionError("Invalid or duplicate ID: "+line.id());
   if(!line.text().equals(LakeDialogueLines.defaultText(line.id())))
    throw new AssertionError("Fallback mismatch");
  }
  if(LakeDialogueLines.defaultText("unknown")!=null||LakeDialogueLines.isLakeId(null))
   throw new AssertionError("Invalid fallback");
  if(!LakeDialogueLines.defaultText(LakeDialogueLines.TO_SAWMILL).equals("Кажись придется идти на лесопилку, чтож.."))
   throw new AssertionError("Sawmill dialogue changed");
  if(!LakeDialogueLines.defaultText(LakeDialogueLines.GLASS_FINALE).equals("Там что-то наверху, мне надо проверить. может рыбаку нужна помощь?"))
   throw new AssertionError("Finale dialogue changed");
  System.out.println("PASS: 10 stable dialogue IDs and author-provided fallback lines");
 }
}
