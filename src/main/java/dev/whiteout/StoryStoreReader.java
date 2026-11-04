package dev.whiteout;

import java.nio.file.Path;

/** Validate recovery candidates with the same loader used to open a world. */
public final class StoryStoreReader {
 private StoryStoreReader(){}
 public static Store open(Path path){return new Store(path);}
}
