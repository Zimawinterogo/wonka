package dev.whiteout.power;

import com.google.gson.*;

/** Validate the wire/file shape before Gson can coerce numbers or supply defaults. */
public final class FolderDataValidation {
 private FolderDataValidation(){}
 public static void validate(JsonElement root){
  if(root==null||!root.isJsonObject())throw new IllegalArgumentException("Expected folder object");
  JsonObject object=root.getAsJsonObject();
  if(!object.has("folders")||!object.get("folders").isJsonArray()
    ||!object.has("assigned")||!object.get("assigned").isJsonObject())
   throw new IllegalArgumentException("Missing folders/assigned");
  java.util.Set<String> names=new java.util.HashSet<>();
  for(JsonElement value:object.getAsJsonArray("folders")){
   String name=string(value);
   if(name.isBlank()||name.length()>32||!name.equals(name.strip())
      ||name.chars().anyMatch(Character::isISOControl)||!names.add(name))
    throw new IllegalArgumentException("Invalid or duplicate folder name");
  }
  for(var entry:object.getAsJsonObject("assigned").entrySet()){
   if(entry.getKey().isBlank()||!names.contains(string(entry.getValue())))
    throw new IllegalArgumentException("Invalid folder assignment");
  }
  // Older files have no order; retain that supported format.
  if(!object.has("order")||object.get("order").isJsonNull())return;
  if(!object.get("order").isJsonObject())throw new IllegalArgumentException("Invalid folder order");
  for(var entry:object.getAsJsonObject("order").entrySet()){
   if(!entry.getValue().isJsonArray())throw new IllegalArgumentException("Invalid zone order list");
   for(JsonElement id:entry.getValue().getAsJsonArray())
    if(string(id).isBlank())throw new IllegalArgumentException("Empty ordered zone id");
  }
 }
 private static String string(JsonElement value){
  if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())
   throw new IllegalArgumentException("Expected string in folder data");
  return value.getAsString();
 }
}
