package com.belzebool.freefpv.client.tools;

import com.belzebool.freefpv.client.DroneController;
import com.belzebool.freefpv.client.GuiCanvas;
import com.belzebool.freefpv.client.Keys;
import com.belzebool.freefpv.client.ServerFeatures;
import com.belzebool.freefpv.core.DroneConfig;
import com.belzebool.freefpv.core.tool.ToolHudPainter;
import com.belzebool.freefpv.core.tool.ToolWheel;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
//import net.minecraft.client.gui.GuiGraphics;

/**
 * The 10th slot next to the hotbar, in the spirit of Axiom's tool slot. It holds one multitool at a time; the tool wheel
 * (hold Left Alt) swaps it. The slot is purely client-side: the server still sees the hotbar slot that was selected
 * before, and while the tool slot is active clicks go to the tool instead of the held item.
 */
public final class ToolSlot {
    public static final ToolSlot INSTANCE = new ToolSlot();

    private final ToolWheel wheel = new ToolWheel();
    private boolean active;
    private String nameText = "";
    private double nameTimer;
    private double pop;
    private int lastHovered = -1;
    private long lastFrameNanos;

    private ToolSlot() {
    }

    private static DroneConfig.Tools settings() {
        return DroneController.INSTANCE.config().tools;
    }

    private static boolean enabled() {
        return settings().enabled;
    }

    /** Tools this server lets the player use, in wheel order. */
    public List<Multitool> available() {
        List<Multitool> list = new ArrayList<>();
        for (Multitool t : ToolRegistry.all()) {
            if (ServerFeatures.allows(t.id(), t.needsServer())) list.add(t);
        }
        return list;
    }

    public Multitool selected() {
        List<Multitool> list = available();
        String id = settings().selected;
        for (Multitool t : list) {
            if (t.id().equals(id)) return t;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isActive() {
        return active && enabled() && !DroneController.INSTANCE.isFlying() && selected() != null;
    }

    /** The hotbar hides its own selection frame while the tool slot has it. */
    public boolean hidesHotbarSelection() {
        return isActive();
    }

    /** What the pilot holds in first person while the slot is active, or null for the real item. */
    public ItemStack heldStack() {
        if (!isActive()) return null;
        return GuiCanvas.iconStack(selected().icon());
    }

    public void setActive(boolean value) {
        if (value && (!enabled() || selected() == null)) return;
        if (value && !active) showName();
        active = value;
    }

    public void select(Multitool tool) {
        if (tool == null) return;
        if (!tool.id().equals(settings().selected)) {
            settings().selected = tool.id();
            DroneController.INSTANCE.saveConfig();
        }
        showName();
    }

    public void showName() {
        Multitool tool = selected();
        if (tool == null) return;
        pop = 1;
        String status = tool.status();
        nameText = tool.name().getString() + (status.isEmpty() ? "" : "  -  " + status);
        nameTimer = 2.2;
    }

    // ------------------------------------------------------------------ input

    /** From the start of the vanilla key handling each tick, while not flying. */
    public void handleKeys(Minecraft mc) {
        Options o = mc.options;
        if (!enabled() || mc.player == null || mc.player.isSpectator()) {
            active = false;
            wheel.hide();
            while (Keys.TOOL_SLOT.consumeClick()) {
            }
            return;
        }
        while (Keys.TOOL_SLOT.consumeClick()) setActive(!active);
        for (KeyMapping slot : o.keyHotbarSlots) {
            if (slot.isDown()) active = false;
        }

        boolean wheelKey = Keys.TOOL_WHEEL.isDown();
        if (wheelKey && !wheel.isVisible()) {
            wheel.show();
            lastHovered = -1;
        }
        if (!wheelKey && wheel.isVisible()) closeWheel(settings().releaseToSelect);

        if (wheel.isVisible()) {
            // Left click picks the hovered tool; nothing reaches the held item while the wheel is up.
            boolean pick = false;
            while (o.keyAttack.consumeClick()) pick = true;
            while (o.keyUse.consumeClick()) {
            }
            o.keyAttack.setDown(false);
            o.keyUse.setDown(false);
            if (pick) closeWheel(true);
            return;
        }

        if (isActive()) {
            Multitool tool = selected();
            while (o.keyUse.consumeClick()) tool.use(mc);
            while (o.keyAttack.consumeClick()) tool.secondary(mc);
            o.keyUse.setDown(false);
            o.keyAttack.setDown(false);
            while (o.keyDrop.consumeClick()) {
            }
            while (o.keySwapOffhand.consumeClick()) {
            }
            while (o.keyPickItem.consumeClick()) {
            }
        }
    }

    private void closeWheel(boolean pickHovered) {
        List<Multitool> list = available();
        int hovered = wheel.hovered(list.size());
        wheel.hide();
        if (pickHovered && hovered >= 0 && hovered < list.size()) {
            select(list.get(hovered));
            click(1.0f);
        }
        // A quick tap without pointing anywhere just jumps to the tool slot.
        setActive(true);
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    /** Mouse movement while the wheel is open steers its cursor instead of the head. Returns true if used. */
    public boolean onMouseTurn(double dx, double dy) {
        if (!wheel.isVisible()) return false;
        wheel.moveCursor(dx * 0.4, dy * 0.4);
        return true;
    }

    /** Scroll while the wheel is open steps through the tools. Returns true if used. */
    public boolean onScroll(double amount) {
        if (!wheel.isVisible() || amount == 0) return false;
        List<Multitool> list = available();
        if (list.isEmpty()) return true;
        int from = wheel.hovered(list.size());
        if (from < 0) from = Math.max(0, list.indexOf(selected()));
        int next = Math.floorMod(from + (amount > 0 ? -1 : 1), list.size());
        wheel.pointAt(next, list.size());
        return true;
    }

    /**
     * Hotbar scrolling with the tool slot sitting between slot 9 and slot 1. Returns true when the change was handled
     * here and the vanilla selection must not be applied.
     */
    public boolean onHotbarScroll(Inventory inventory, int from, int to) {
        if (!enabled() || selected() == null || !settings().scrollIntoToolSlot) {
            active = false;
            return false;
        }
        int size = Inventory.getSelectionSize();
        int delta = Math.floorMod(to - from, size);
        if (delta == 0) return false;
        boolean forward = delta <= size / 2;
        if (active) {
            active = false;
            inventory.setSelectedSlot(forward ? 0 : size - 1);
            return true;
        }
        if (forward && from == size - 1 && to == 0 || !forward && from == 0 && to == size - 1) {
            setActive(true);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ HUD

    public void render(/*? if >=26.1 {*/GuiGraphicsExtractor/*?} else {*//*GuiGraphics*//*?}*/ graphics) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
        lastFrameNanos = now;
        wheel.tick(dt);
        nameTimer = Math.max(0, nameTimer - dt);
        pop = Math.max(0, pop - dt / 0.2);

        if (!enabled() || mc.player == null || mc.player.isSpectator() || DroneController.INSTANCE.isFlying()) return;
        //? if >=26.1 {
        if (mc.gui.hud.isHidden()) return;
        //?} else
        //if (mc.options.hideGui) return;
        Multitool tool = selected();
        if (tool == null && !wheel.isDrawn()) return;
        GuiCanvas c = new GuiCanvas(graphics);

        if (tool != null) {
            boolean right = mc.player.getMainArm().getOpposite() == HumanoidArm.LEFT;
            int x = right ? c.width() / 2 + 91 + 4 : c.width() / 2 - 91 - 4 - 24;
            if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) x += right ? 22 : -22;
            ToolHudPainter.slot(c, x, c.height() - 23, tool.icon(), isActive(), pop);
            boolean survival = mc.gameMode != null && mc.gameMode.canHurtPlayer();
            ToolHudPainter.toolName(c, nameText, Math.min(1, nameTimer / 0.5), c.height() - (survival ? 59 : 45));
        }

        if (wheel.isDrawn()) {
            List<Multitool> list = available();
            int hovered = wheel.isVisible() ? wheel.hovered(list.size()) : -1;
            if (hovered != lastHovered) {
                if (hovered >= 0) click(1.8f);
                lastHovered = hovered;
            }
            List<ToolHudPainter.Entry> entries = new ArrayList<>(list.size());
            for (Multitool t : list) {
                entries.add(new ToolHudPainter.Entry(t.name().getString(), t.description().getString(), t.icon(), t.status(), false));
            }
            ToolHudPainter.wheel(c, entries, hovered, list.indexOf(tool),
                wheel.openAmount(), wheel.cursorX(), wheel.cursorY(),
                Component.translatable("tool.freefpv.none").getString(),
                Component.translatable("tool.freefpv.wheel_hint").getString());
        }
    }
}
