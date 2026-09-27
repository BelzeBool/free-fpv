package com.belzebool.freefpv.client;

import com.belzebool.freefpv.core.Airframe;
import com.belzebool.freefpv.core.DroneConfig;
import com.belzebool.freefpv.server.DroneServer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
//import net.minecraft.client.gui.GuiGraphics;

/** Drone workshop: pick the FPV build and paint the frame and LEDs. Everyone with the mod sees the result. */
public final class WorkshopScreen extends Screen {
    private static final int[] FRAME_COLORS = {
        0xEB7F24, 0xE0382B, 0xF4C534, 0x3CCB6A, 0x2E9BF0, 0x7B4DFF, 0xE848C8, 0xFFFFFF,
        0x9AA0A6, 0x5A5F66, 0x26262C, 0x8B5A2B, 0x00B5AD, 0xFF7AA8, 0xB0E040, 0x1C3F94};
    private static final int[] LED_COLORS = {
        0x3CD8F0, 0x3CEB5A, 0xFF4A3D, 0xFFE040, 0xFF5CE0, 0x7B4DFF, 0xFFFFFF, 0xFF8A1A};

    private boolean fpv;

    public WorkshopScreen(boolean fpv) {
        super(Component.translatable("workshop.freefpv.title"));
        this.fpv = fpv;
    }

    private static DroneConfig.Drone drone() {
        return DroneController.INSTANCE.config().drone;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150, y = height / 2 - 96;
        addRenderableWidget(new StringWidget(left, y, 300, 12, title, font));
        y += 16;
        addRenderableWidget(tab(Component.translatable("workshop.freefpv.fpv"), true, left, y));
        addRenderableWidget(tab(Component.translatable("workshop.freefpv.camera"), false, left + 76, y));
        y += 28;

        if (fpv) {
            addRenderableWidget(new StringWidget(left, y, 150, 12, Component.translatable("workshop.freefpv.airframe"), font));
            y += 13;
            int i = 0;
            for (Airframe a : Airframe.values()) {
                if (!a.fpv) continue;
                boolean selected = drone().airframe(true) == a;
                Button b = Button.builder(Component.translatable("airframe.freefpv." + a.id), btn -> {
                    drone().fpvAirframe = a.id;
                    changed();
                }).bounds(left + (i % 2) * 76, y + (i / 2) * 22, 74, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("airframe.freefpv." + a.id + ".desc")))
                    .build();
                b.active = !selected;
                addRenderableWidget(b);
                i++;
            }
            y += 48;
        }

        addRenderableWidget(new StringWidget(left, y, 150, 12, Component.translatable(fpv ? "workshop.freefpv.frame" : "workshop.freefpv.shell"), font));
        y += 13;
        y = swatches(FRAME_COLORS, left, y, rgb -> {
            if (fpv) drone().fpvFrameColor = DroneConfig.Drone.hex(rgb);
            else drone().cameraColor = DroneConfig.Drone.hex(rgb);
        }, drone().frameRgb(fpv));

        if (fpv) {
            y += 4;
            addRenderableWidget(new StringWidget(left, y, 150, 12, Component.translatable("workshop.freefpv.leds"), font));
            y += 13;
            y = swatches(LED_COLORS, left, y, rgb -> drone().fpvLedColor = DroneConfig.Drone.hex(rgb), drone().ledRgb());
            y += 4;
            addRenderableWidget(new StringWidget(left, y, 150, 12, Component.translatable("workshop.freefpv.video"), font));
            y += 13;
            String[] styles = {"analog", "o4", "walksnail", "clean", "off"};
            for (int i = 0; i < styles.length; i++) {
                String style = styles[i];
                Button b = Button.builder(Component.translatable("workshop.freefpv.video." + style), btn -> {
                    DroneController.INSTANCE.config().camera.videoStyle = style;
                    changed();
                }).bounds(left + (i % 3) * 51, y + (i / 3) * 22, 49, 20).build();
                b.active = !style.equalsIgnoreCase(DroneController.INSTANCE.config().camera.videoStyle);
                addRenderableWidget(b);
            }
            y += 44;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 50, Math.max(y + 10, height / 2 + 80), 100, 20).build());
    }

    private Button tab(Component label, boolean forFpv, int x, int y) {
        Button b = Button.builder(label, btn -> {
            fpv = forFpv;
            rebuildWidgets();
        }).bounds(x, y, 74, 20).build();
        b.active = fpv != forFpv;
        return b;
    }

    private int swatches(int[] colors, int left, int y, java.util.function.IntConsumer pick, int current) {
        for (int i = 0; i < colors.length; i++) {
            int rgb = colors[i];
            Component square = Component.literal(rgb == current ? "■" : "█").withStyle(Style.EMPTY.withColor(rgb));
            Button b = Button.builder(square, btn -> {
                pick.accept(rgb);
                changed();
            }).bounds(left + (i % 8) * 19, y + (i / 8) * 19, 18, 18).build();
            addRenderableWidget(b);
        }
        return y + ((colors.length + 7) / 8) * 19 + 2;
    }

    private void changed() {
        DroneController.INSTANCE.onDroneCustomized();
        rebuildWidgets();
    }

    private ItemStack preview() {
        DroneConfig.Drone d = drone();
        return DroneServer.droneStack(d.airframe(fpv), d.frameRgb(fpv), d.ledRgb(), false);
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawPreview(g);
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        int x = width / 2 + 20, y = height / 2 - 60;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(7f);
        g.item(preview(), 0, 0);
        g.pose().popMatrix();
        String name = Component.translatable("airframe.freefpv." + drone().airframe(fpv).id).getString();
        g.centeredText(font, name, x + 56, y + 118, 0xFFFFFFFF);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawPreview(g);
    }

    private void drawPreview(GuiGraphics g) {
        int x = width / 2 + 20, y = height / 2 - 60;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(7f);
        g.renderItem(preview(), 0, 0);
        g.pose().popMatrix();
        String name = Component.translatable("airframe.freefpv." + drone().airframe(fpv).id).getString();
        g.drawCenteredString(font, name, x + 56, y + 118, 0xFFFFFFFF);
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
