package dev.whiteout.client;

import dev.whiteout.MusicCatalog;
import java.nio.file.Path;

/** The 7 embedded soundtrack files are bundled in Whiteout.
 * No external override pack or names file is created or loaded.
 */
public final class CustomMusicManager {
 public static final String PACK_FOLDER = "Whiteout_Custom_Music";
 public static final String PACK_ID = "file/Whiteout_Custom_Music";
 private CustomMusicManager() {}
 public static synchronized void init() {}
 public static synchronized String displayName(int slot) { return MusicCatalog.name(slot); }
 public static synchronized boolean hasCustom(int slot) { return false; }
 public static synchronized String importWithDialog(int slot) { return net.minecraft.text.Text.translatable("screen.whiteout.music.import_disabled").getString(); }
 public static synchronized String importPackWithDialog() { return net.minecraft.text.Text.translatable("screen.whiteout.music.import_disabled").getString(); }
 static synchronized String importFile(int slot, Path file) { return net.minecraft.text.Text.translatable("screen.whiteout.music.import_disabled").getString(); }
 public static synchronized String remove(int slot) { return net.minecraft.text.Text.translatable("screen.whiteout.music.cannot_remove").getString(); }
 public static synchronized int customCount() { return 0; }
}
