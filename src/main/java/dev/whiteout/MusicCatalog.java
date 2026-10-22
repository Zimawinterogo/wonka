package dev.whiteout;

/** Bundled soundtrack replacing the placeholders. Slots are 1-based. */
public final class MusicCatalog {
 public static final String[] NAMES = {
   "Без музыки",
   "OMORI — Lost Library",
   "OMORI — Numbers",
   "Sea Power — Instrument of Surrender",
   "Nightmargin — Distant Water",
   "Nightmargin — Geothermal",
   "Until Then OST — Broken Promises",
   "Kyle Patrick Naval — The World Hasn't Ended Yet",
   // Compatibility-only aliases for regions saved with music 8–16.
   // There are still only seven different compositions.
   "OMORI — Lost Library",
   "OMORI — Numbers",
   "Sea Power — Instrument of Surrender",
   "Nightmargin — Distant Water",
   "Nightmargin — Geothermal",
   "Until Then OST — Broken Promises",
   "Kyle Patrick Naval — The World Hasn't Ended Yet",
   "OMORI — Lost Library",
   "OMORI — Numbers"
 };
 public static final int COUNT = NAMES.length - 1;
 private MusicCatalog() {}
 public static String name(int slot) {
   return slot == 0 ? net.minecraft.text.Text.translatable("screen.whiteout.music.none").getString()
    : slot > 0 && slot < NAMES.length ? NAMES[slot]
    : net.minecraft.text.Text.translatable("screen.whiteout.music.invalid").getString();
 }
}
