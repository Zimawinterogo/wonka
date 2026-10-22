import dev.whiteout.power.ZoneFolderOrder;
import java.util.*;
public final class ZoneFolderOrderTest {
 public static void main(String[] args){
  List<String> a=new ArrayList<>(List.of("A","B","C"));
  assertTrue(ZoneFolderOrder.move(a,"B",-1));
  eq(a,List.of("B","A","C"));
  assertTrue(!ZoneFolderOrder.move(a,"B",-1));
  assertTrue(ZoneFolderOrder.move(a,"B",1));
  eq(a,List.of("A","B","C"));
  ZoneFolderOrder.reconcile(a,List.of("C","B","D"));
  eq(a,List.of("B","C","D")); // stable edits, append new zones
  assertTrue(ZoneFolderOrder.move(a,"D",-1));
  eq(a,List.of("B","D","C"));
  assertTrue(!ZoneFolderOrder.move(a,"missing",1));
  a=new ArrayList<>(List.of("A","A","deleted","B"));
  ZoneFolderOrder.reconcile(a,List.of("A","B","C","C"));
  eq(a,List.of("A","B","C"));
  System.out.println("PASS: folder reordering, boundaries, deletion, insertion");
 }
 private static void assertTrue(boolean b){if(!b)throw new AssertionError();}
 private static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
}
