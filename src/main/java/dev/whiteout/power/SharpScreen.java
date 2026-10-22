package dev.whiteout.power;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Base for gameplay overlays: never blur pixels already drawn by the GUI.
 * Draw dimming in render() before content, then call super.render() for widgets.
 */
public abstract class SharpScreen extends Screen {
 protected SharpScreen(Text title){super(title);}
 @Override public final void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
 @Override protected final void applyBlur(float delta){}
}
