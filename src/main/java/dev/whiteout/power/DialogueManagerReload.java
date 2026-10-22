package dev.whiteout.power;

import dev.whiteout.client.DialogueManager;

/** Single route into the legacy Whiteout JSON parser; never creates a parallel dialogue database. */
public final class DialogueManagerReload {
 private DialogueManagerReload(){}
 public static void reload(){DialogueManager.load();}
}
