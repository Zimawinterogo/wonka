package dev.whiteout.power.mixin;

import com.google.gson.annotations.SerializedName;
import dev.whiteout.Store;
import dev.whiteout.power.ScopedStory;
import org.spongepowered.asm.mixin.*;
import java.util.*;

@Mixin(value=Store.Data.class,remap=false)
public abstract class PlayerStoryDataMixin implements ScopedStory.Players {
 @Unique @SerializedName("playerStory") private Map<UUID,ScopedStory.Profile> whiteout$profiles=new LinkedHashMap<>();
 public Map<UUID,ScopedStory.Profile> whiteout$players(){return whiteout$profiles;}
}
