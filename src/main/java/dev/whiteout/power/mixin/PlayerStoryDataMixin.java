package dev.whiteout.power.mixin;

import com.google.gson.annotations.SerializedName;
import dev.whiteout.Store;
import dev.whiteout.power.ScopedStory;
import org.spongepowered.asm.mixin.*;
import java.util.*;

@Mixin(value=Store.Data.class,remap=false)
public abstract class PlayerStoryDataMixin implements ScopedStory.Players,dev.whiteout.power.lobby.LobbyState.Access,dev.whiteout.power.lobby.Balance.Access,dev.whiteout.power.SharedScene.Access,dev.whiteout.power.SceneDelivery.Access,dev.whiteout.power.DialogueBarrier.Access {
 @Unique @SerializedName("duoPendingDialogues") private dev.whiteout.power.DialogueBarrier.Readers whiteout$pendingDialogues=new dev.whiteout.power.DialogueBarrier.Readers();
 public dev.whiteout.power.DialogueBarrier.Readers whiteout$readers(){return whiteout$pendingDialogues;}
 public void whiteout$readers(dev.whiteout.power.DialogueBarrier.Readers pending){whiteout$pendingDialogues=pending;}
 @Unique @SerializedName("storyDeliveries") private dev.whiteout.power.SceneDelivery.Ledger whiteout$deliveries=new dev.whiteout.power.SceneDelivery.Ledger();
 public dev.whiteout.power.SceneDelivery.Ledger whiteout$grants(){return whiteout$deliveries;}
 public void whiteout$grants(dev.whiteout.power.SceneDelivery.Ledger ledger){whiteout$deliveries=ledger;}
 @Unique @SerializedName("runningStoryScene") private dev.whiteout.power.SharedScene.Playing whiteout$playing;
 public dev.whiteout.power.SharedScene.Playing whiteout$scene(){return whiteout$playing;}
 public void whiteout$scene(dev.whiteout.power.SharedScene.Playing playing){whiteout$playing=playing;}
 @Unique @SerializedName("lobbyBalance") private dev.whiteout.power.lobby.Balance whiteout$tuning;
 public dev.whiteout.power.lobby.Balance whiteout$balance(){return whiteout$tuning;}
 public void whiteout$balance(dev.whiteout.power.lobby.Balance value){whiteout$tuning=value;}
 @Unique @SerializedName("storyLobby") private dev.whiteout.power.lobby.LobbyState whiteout$board;
 public dev.whiteout.power.lobby.LobbyState whiteout$lobby(){return whiteout$board;}
 public void whiteout$lobby(dev.whiteout.power.lobby.LobbyState value){whiteout$board=value;}
 @Unique @SerializedName("playerStory") private ScopedStory.Profiles whiteout$profiles=new ScopedStory.Profiles();
 public Map<UUID,ScopedStory.Profile> whiteout$players(){return whiteout$profiles;}
}
