package dev.whiteout.power.mixin;

import com.google.gson.annotations.SerializedName;
import dev.whiteout.Store;
import dev.whiteout.power.ScopedStory;
import org.spongepowered.asm.mixin.*;
import java.util.*;

@Mixin(value=Store.Data.class,remap=false)
public abstract class PlayerStoryDataMixin implements ScopedStory.Players,dev.whiteout.power.lobby.LobbyState.Access {
 @Unique @SerializedName("storyLobby") private dev.whiteout.power.lobby.LobbyState whiteout$board;
 public dev.whiteout.power.lobby.LobbyState whiteout$lobby(){return whiteout$board;}
 public void whiteout$lobby(dev.whiteout.power.lobby.LobbyState value){whiteout$board=value;}
 @Unique @SerializedName("playerStory") private ScopedStory.Profiles whiteout$profiles=new ScopedStory.Profiles();
 public Map<UUID,ScopedStory.Profile> whiteout$players(){return whiteout$profiles;}
}
