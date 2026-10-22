package dev.whiteout.power;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.Predicate;

/** Pure bounded geometry search. 0=blocked/unloaded, 1=covered air, 2=open sky. */
public final class WindowAirSearch {
 public record Point(int x,int y,int z){}
 @FunctionalInterface public interface Sample { int at(int x,int y,int z); }
 private static final int[][] STEPS={{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
 private WindowAirSearch(){}
 public static Point find(Sample sample){return find(sample,p->true);}
 /** A reachable sky position may still be in an indoor light well; accept only
  * a destination matching the desired exterior region before stopping search. */
 public static Point find(Sample sample,Predicate<Point> outside){
  ArrayDeque<Point> queue=new ArrayDeque<>();
  HashSet<Point> seen=new HashSet<>();
  seen.add(new Point(0,0,0));
  for(int i=0;i<4;i++){var d=STEPS[i];var p=new Point(d[0],0,d[2]);queue.add(p);seen.add(p);}
  int budget=512;
  while(!queue.isEmpty()&&budget-->0){
   Point p=queue.removeFirst();
   int kind=sample.at(p.x,p.y,p.z);
   if(kind==0)continue;
   if(kind==2&&outside.test(p))return p;
   for(var d:STEPS){
    Point n=new Point(p.x+d[0],p.y+d[1],p.z+d[2]);
    if(Math.abs(n.x)<=6&&Math.abs(n.z)<=6&&Math.abs(n.y)<=2&&seen.add(n))queue.addLast(n);
   }
  }
  return null;
 }
}
