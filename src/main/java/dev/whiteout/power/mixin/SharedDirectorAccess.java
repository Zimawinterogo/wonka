package dev.whiteout.power.mixin;
import dev.whiteout.client.director.DirectorPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=DirectorPlayer.class,remap=false)
public interface SharedDirectorAccess{
 @Accessor("tick") static void whiteout$seek(int value){throw new AssertionError();}
}
