package dev.whiteout.power;

/** Deterministic chapter rules, independent of Minecraft and render frame rate. */
public final class LakeStoryPolicy {
 private LakeStoryPolicy() {}
 public static boolean inStart(double x,double y,double z) {
  return Math.abs(x-8381)<=3&&Math.abs(y-72)<=3&&Math.abs(z-8366)<=3;
 }
 public static int afterPull(int stage,boolean fueled,int pulls) {
  return stage==3&&fueled&&pulls>=3?4:stage;
 }
 public static int afterPanel(int stage,boolean attempted) {return stage==4&&attempted?5:stage;}
 public static boolean transition(int from,int to){
  return switch(from){case 0,1,2,3,4,5,8,9,10 -> to==from+1;case 6 -> to==8;default -> false;};
 }
 public static boolean shouldRestore(boolean matchesBefore,boolean matchesAfter,boolean hasBlockEntity){return !hasBlockEntity&&!matchesBefore&&matchesAfter;}
 public static String objective(int stage) {
  if(stage>=2&&stage<=4)return "Найти генератор";
  if(stage==5)return "Добраться до лесопилки";
  if(stage>=6&&stage<=8)return "Восстановить электроснабжение";
  return stage==9?"Вернуться к дому":"";
 }
 public static String description(String goal) {
  return switch(goal) {
   case "Найти генератор" -> "Осмотрите участок возле дома рыбака.";
   case "Добраться до лесопилки" -> "Пройдите к лесопилке за домом.";
   case "Восстановить электроснабжение" -> "Выясните причину отключения света.";
   case "Вернуться к дому" -> "Вернитесь к рыбаку и проверьте дом.";
   default -> "";
  };
 }
 public static double ease(double value) {double t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}
 public static double opacity(double seconds) {return ease(seconds/.5)*(1-ease((seconds-8.3)/1.2));}
 public static double descriptionOpacity(double seconds) {return ease((seconds-.5)/.7)*opacity(seconds);}
 public static boolean finished(double seconds) {return seconds>=9.5;}
}
