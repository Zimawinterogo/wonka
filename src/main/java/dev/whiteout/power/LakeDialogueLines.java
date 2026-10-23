package dev.whiteout.power;

import java.util.List;

/** Stable lake-chapter IDs and verbatim author-provided fallback lines. */
public final class LakeDialogueLines {
 private LakeDialogueLines(){}
 public static final String ARRIVAL="lake_01_arrival";
 public static final String LOCKED_DOOR="lake_02_locked_door";
 public static final String FOUND_GENERATOR="lake_03_found_generator";
 public static final String FAILED_START="lake_04_failed_start";
 public static final String TO_SAWMILL="lake_05_to_sawmill";
 public static final String SAWMILL_ARRIVAL="lake_06_sawmill_arrival";
 public static final String BRICK_ROOM="lake_07_brick_room";
 public static final String RESERVE_STARTED="lake_08_reserve_started";
 public static final String DECAYED_HOUSE="lake_09_decayed_house";
 public static final String GLASS_FINALE="lake_10_glass_finale";
 public record Entry(String id,String text){}
 private static final List<Entry> ENTRIES=List.of(
  new Entry(ARRIVAL,"Холодно блять, зайду чтоль к рыбаку, узнаю че да как у него"),
  new Entry(LOCKED_DOOR,"Че за хуйня? у него электрическая дверь чтоль.. надо включить генератор, только где он....."),
  new Entry(FOUND_GENERATOR,"Ну что за напасть ебаная, ладно может все не так плохо"),
  new Entry(FAILED_START,"Мда блять"),
  new Entry(TO_SAWMILL,"Кажись придется идти на лесопилку, чтож.."),
  new Entry(SAWMILL_ARRIVAL,"Что..."),
  new Entry(BRICK_ROOM,"Ага, теперь ясно, надо включить резервный источник питания, и все должно заработать, осталось только его найти, блять"),
  new Entry(RESERVE_STARTED,"Заебись"),
  new Entry(DECAYED_HOUSE,"Какого хуя тут все гнилое?? я же вчера заходил к нему,, все нормально было.."),
  new Entry(GLASS_FINALE,"Там что-то наверху, мне надо проверить. может рыбаку нужна помощь?")
 );
 public static List<Entry> entries(){return ENTRIES;}
 public static String defaultText(String id){
  if(id==null)return null;
  for(Entry entry:ENTRIES)if(entry.id().equals(id))return entry.text();
  return null;
 }
 public static boolean isLakeId(String id){return defaultText(id)!=null;}
}
