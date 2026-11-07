package com.creaturestudio.client.screen;

import com.creaturestudio.client.ClientFiles;
import com.creaturestudio.client.render.AnimState;
import com.creaturestudio.client.render.PaintCache;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.Movement;
import com.creaturestudio.data.Templates;
import com.creaturestudio.network.Payloads;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla principal del estudio.
 * Izquierda: "Mis criaturas" con los botones Nueva, Importar y Abrir carpeta.
 * Derecha: vista previa de la criatura elegida y, debajo, todo lo que se puede hacer con ella.
 */
public class LibraryScreen extends Screen {
    private static final int ROW_H = 14;
    private static final int TITLE_H = 22;
    private static final int MARGIN = 6;
    private static final int BTN_H = 20;

    private List<CreatureDefinition> creatures = new ArrayList<>();
    @Nullable
    private String selectedId;
    @Nullable
    private String pendingSelectId;
    private int scroll;
    private final Preview3D preview = new Preview3D();
    @Nullable
    private String fittedId;
    private int fittedRevision = -1;

    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int headerY;
    @Nullable
    private Button editButton;
    @Nullable
    private Button duplicateButton;
    @Nullable
    private Button deleteButton;
    @Nullable
    private Button eggButton;
    @Nullable
    private Button summonButton;
    @Nullable
    private Button exportButton;

    private boolean dragging;
    private int dragButton;
    private long lastInteraction;
    private long lastClickTime;
    private int lastClickIndex = -1;

    @Nullable
    private Component status;
    private int statusColor = Ui.TEXT;
    private long statusUntil;

    public LibraryScreen() {
        super(Ui.tr("library.title"));
    }

    @Override
    protected void init() {
        reloadList();
        listX = MARGIN;
        listW = Mth.clamp(width / 3, 130, 200);
        int px = listX + listW + MARGIN;
        int pw = width - px - MARGIN;

        Component closeText = Ui.tr("library.close");
        int wClose = Math.max(50, font.width(closeText) + 16);
        addRenderableWidget(Button.builder(closeText, b -> onClose())
                .bounds(width - MARGIN - wClose, 3, wClose, 16).build());

        // Columna izquierda: la coleccion
        headerY = TITLE_H + 6;
        int y = headerY + 12;
        addRenderableWidget(Button.builder(Ui.tr("library.new"), b -> openNew())
                .bounds(listX, y, listW, BTN_H).tooltip(Tooltip.create(Ui.tr("library.new_tooltip"))).build());
        y += BTN_H + 4;
        listY = y;
        int bottom = height - MARGIN - BTN_H;
        listH = Math.max(ROW_H * 2 + 4, bottom - 4 - listY);
        int half = (listW - 4) / 2;
        addRenderableWidget(Button.builder(Ui.tr("library.import"), b -> minecraft.setScreen(new ImportScreen(this)))
                .bounds(listX, bottom, half, BTN_H).tooltip(Tooltip.create(Ui.tr("library.import_tooltip"))).build());
        addRenderableWidget(Button.builder(Ui.tr("library.folder"), b -> ClientFiles.openFolder())
                .bounds(listX + half + 4, bottom, listW - half - 4, BTN_H)
                .tooltip(Tooltip.create(Ui.tr("library.folder_tooltip"))).build());

        // Columna derecha: la criatura elegida
        int row2 = height - MARGIN - BTN_H;
        int row1 = row2 - BTN_H - 4;
        int previewTop = TITLE_H + 6;
        preview.setArea(px, previewTop, pw, Math.max(40, row1 - 6 - previewTop));
        int bw = (pw - 8) / 3;
        int x2 = px + bw + 4;
        int x3 = px + (bw + 4) * 2;
        int w3 = pw - (bw + 4) * 2;
        editButton = addRenderableWidget(Button.builder(Ui.tr("library.edit"), b -> openEdit())
                .bounds(px, row1, bw, BTN_H).tooltip(Tooltip.create(Ui.tr("library.edit_tooltip"))).build());
        duplicateButton = addRenderableWidget(Button.builder(Ui.tr("library.duplicate"), b -> duplicate())
                .bounds(x2, row1, bw, BTN_H).tooltip(Tooltip.create(Ui.tr("library.duplicate_tooltip"))).build());
        deleteButton = addRenderableWidget(Button.builder(Ui.tr("library.delete"), b -> confirmDelete())
                .bounds(x3, row1, w3, BTN_H).build());
        eggButton = addRenderableWidget(Button.builder(Ui.tr("library.egg"), b -> giveEgg())
                .bounds(px, row2, bw, BTN_H).tooltip(Tooltip.create(Ui.tr("library.egg_tooltip"))).build());
        summonButton = addRenderableWidget(Button.builder(Ui.tr("library.summon"), b -> summon())
                .bounds(x2, row2, bw, BTN_H).tooltip(Tooltip.create(Ui.tr("library.summon_tooltip"))).build());
        exportButton = addRenderableWidget(Button.builder(Ui.tr("library.export"), b -> export())
                .bounds(x3, row2, w3, BTN_H).tooltip(Tooltip.create(Ui.tr("library.export_tooltip"))).build());
        updateButtons();
    }

    // ---------------------------------------------------------------------------------------

    private void reloadList() {
        creatures = CreatureStore.CLIENT.sorted();
        if (pendingSelectId != null && CreatureStore.CLIENT.get(pendingSelectId) != null) {
            selectedId = pendingSelectId;
            pendingSelectId = null;
        }
        if (selectedId == null && !creatures.isEmpty()) {
            selectedId = creatures.get(0).id;
        }
        if (selectedId != null && CreatureStore.CLIENT.get(selectedId) == null) {
            selectedId = creatures.isEmpty() ? null : creatures.get(0).id;
        }
    }

    /** Llamado cuando el servidor envia cambios. */
    public void refresh() {
        reloadList();
        updateButtons();
    }

    /** Selecciona una criatura (si existe) al volver del editor. */
    public void selectCreature(String id) {
        if (CreatureStore.CLIENT.get(id) != null) {
            selectedId = id;
            updateButtons();
        } else {
            pendingSelectId = id; // recien creada: se seleccionara cuando llegue del servidor
        }
    }

    public void showNotice(Component message, boolean success) {
        status = message;
        statusColor = success ? Ui.OK : Ui.ERROR;
        statusUntil = Util.getMillis() + 4000;
    }

    @Nullable
    private CreatureDefinition selected() {
        return CreatureStore.CLIENT.get(selectedId);
    }

    private int selectedIndex() {
        for (int i = 0; i < creatures.size(); i++) {
            if (creatures.get(i).id.equals(selectedId)) {
                return i;
            }
        }
        return -1;
    }

    private void updateButtons() {
        if (editButton == null || duplicateButton == null || deleteButton == null || eggButton == null
                || summonButton == null || exportButton == null) {
            return;
        }
        boolean has = selected() != null;
        editButton.active = has;
        duplicateButton.active = has;
        deleteButton.active = has;
        eggButton.active = has;
        exportButton.active = has;
        boolean creative = minecraft != null && minecraft.player != null
                && (minecraft.player.getAbilities().instabuild || minecraft.player.hasPermissions(2));
        summonButton.active = has && creative;
    }

    private void openNew() {
        CreatureDefinition def = Templates.empty();
        def.id = CreatureDefinition.newId();
        def.name = Ui.tr("library.default_name").getString();
        minecraft.setScreen(new EditorScreen(this, def, true, true));
    }

    private void openEdit() {
        CreatureDefinition d = selected();
        if (d != null) {
            minecraft.setScreen(new EditorScreen(this, d.copy(), false, false));
        }
    }

    private void duplicate() {
        CreatureDefinition d = selected();
        if (d == null) {
            return;
        }
        CreatureDefinition copy = d.copy();
        copy.id = CreatureDefinition.newId();
        copy.owner = "";
        copy.revision = 0;
        String suffix = " " + Ui.tr("library.copy_suffix").getString();
        copy.name = CreatureDefinition.cleanText(d.name, CreatureDefinition.NAME_MAX - suffix.length()) + suffix;
        minecraft.setScreen(new EditorScreen(this, copy, true, false));
    }

    private void confirmDelete() {
        CreatureDefinition d = selected();
        if (d == null) {
            return;
        }
        String id = d.id;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                PacketDistributor.sendToServer(new Payloads.DeleteCreature(id));
            }
            minecraft.setScreen(this);
        }, Ui.tr("library.delete_title"), Ui.tr("library.delete_message", d.name)));
    }

    private void giveEgg() {
        CreatureDefinition d = selected();
        if (d != null) {
            PacketDistributor.sendToServer(new Payloads.GiveEgg(d.id));
        }
    }

    private void summon() {
        CreatureDefinition d = selected();
        if (d != null) {
            PacketDistributor.sendToServer(new Payloads.Summon(d.id));
        }
    }

    private void export() {
        CreatureDefinition d = selected();
        if (d == null) {
            return;
        }
        try {
            Path file = ClientFiles.export(d);
            showNotice(Ui.tr("library.exported", file.getFileName().toString()), true);
        } catch (IOException e) {
            showNotice(Ui.tr("library.export_failed", e.getMessage()), false);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Dibujado
    // ---------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Ui.BG);
        g.fill(0, 0, width, TITLE_H, Ui.PANEL_DARK);
        g.hLine(0, width, TITLE_H - 1, Ui.BORDER);

        Ui.panel(g, listX, listY, listW, listH);
        int visible = Math.max(1, (listH - 4) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, creatures.size() - visible));
        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        for (int r = 0; r < visible + 1 && r + scroll < creatures.size(); r++) {
            CreatureDefinition d = creatures.get(r + scroll);
            int y = listY + 2 + r * ROW_H;
            boolean sel = d.id.equals(selectedId);
            boolean hover = Ui.inside(mouseX, mouseY, listX, y, listW, ROW_H);
            if (sel) {
                g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, Ui.SELECT);
            } else if (hover) {
                g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, 0x30FFFFFF);
            }
            Ui.swatch(g, listX + 4, y + 3, 8, d.eggColor1, false);
            String name = font.plainSubstrByWidth(d.name, listW - 22);
            g.drawString(font, name, listX + 16, y + 3, sel ? 0xFFFFFFFF : Ui.TEXT, false);
        }
        g.disableScissor();
        if (creatures.size() > visible) {
            int trackH = listH - 4;
            int barH = Math.max(8, trackH * visible / creatures.size());
            int barY = listY + 2 + (trackH - barH) * scroll / Math.max(1, creatures.size() - visible);
            g.fill(listX + listW - 4, barY, listX + listW - 2, barY + barH, 0xFF6A7080);
        }

        g.fillGradient(preview.x, preview.y, preview.x + preview.w, preview.y + preview.h, Ui.PREVIEW_TOP,
                Ui.PREVIEW_BOTTOM);
        g.renderOutline(preview.x, preview.y, preview.w, preview.h, Ui.BORDER);
        CreatureDefinition d = selected();
        if (d != null && !d.parts.isEmpty()) {
            if (!d.id.equals(fittedId) || d.revision != fittedRevision) {
                preview.fit(d);
                fittedId = d.id;
                fittedRevision = d.revision;
            }
            long now = Util.getMillis();
            if (!dragging && now - lastInteraction > 2500) {
                preview.yaw += 0.01f;
            }
            float t = (now % 1_000_000L) / 50f;
            AnimState anim = new AnimState(t * 0.35f, 0.6f, t, Mth.sin(t * 0.04f) * 15f, 0f, 0f,
                    d.movement == Movement.FLY);
            preview.render(g, d, anim, -1, true, PaintCache.get(d));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, MARGIN, 7, Ui.TEXT, false);
        g.drawString(font, Ui.tr("library.my_creatures", creatures.size()), listX, headerY, Ui.MUTED, false);

        if (creatures.isEmpty()) {
            Ui.wrapped(g, font, Ui.tr("library.empty"), listX + 6, listY + 8, listW - 12, Ui.MUTED);
        }

        CreatureDefinition d = selected();
        int ix = preview.x + 6;
        int iy = preview.y + 6;
        if (d != null) {
            g.drawString(font, d.name, ix, iy, 0xFFFFFFFF, true);
            iy += 12;
            g.drawString(font, Ui.tr("library.info.behavior").append(": ")
                    .append(Ui.tr("behavior." + d.behavior.key())), ix, iy, Ui.MUTED, false);
            iy += 10;
            g.drawString(font, Ui.tr("library.info.stats", Ui.fmt(d.health), Ui.fmt(d.damage), Ui.fmt(d.speed)),
                    ix, iy, Ui.MUTED, false);
            iy += 10;
            g.drawString(font, Ui.tr("library.info.parts", d.parts.size()), ix, iy, Ui.MUTED, false);
        } else {
            Ui.wrapped(g, font, Ui.tr("library.no_selection"), ix, iy, preview.w - 12, Ui.MUTED);
        }

        int hintY = preview.y + preview.h - 12;
        String hint = font.plainSubstrByWidth(Ui.tr("library.hint").getString(), Math.max(10, preview.w - 12));
        g.drawString(font, hint, preview.x + 6, hintY, 0xFF6C7380, false);

        if (status != null && Util.getMillis() < statusUntil) {
            String s = font.plainSubstrByWidth(status.getString(), Math.max(10, preview.w - 20));
            int sw = font.width(s) + 12;
            int sx = preview.x + (preview.w - sw) / 2;
            int sy = hintY - 18;
            g.fill(sx, sy, sx + sw, sy + 14, 0xD0101216);
            g.renderOutline(sx, sy, sw, 14, statusColor);
            g.drawString(font, s, sx + 6, sy + 3, statusColor, false);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Entrada
    // ---------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (Ui.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int row = (int) ((mouseY - listY - 2) / ROW_H) + scroll;
            if (row >= 0 && row < creatures.size() && mouseY >= listY + 2) {
                String id = creatures.get(row).id;
                long now = Util.getMillis();
                boolean doubleClick = row == lastClickIndex && now - lastClickTime < 350;
                selectedId = id;
                lastClickIndex = row;
                lastClickTime = now;
                updateButtons();
                if (doubleClick) {
                    openEdit();
                }
            }
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            dragging = true;
            dragButton = button;
            lastInteraction = Util.getMillis();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            if (dragButton == 0) {
                preview.rotate(dragX, dragY);
            } else {
                preview.pan(dragX, dragY);
            }
            lastInteraction = Util.getMillis();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == dragButton) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Ui.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            preview.zoomBy(scrollY);
            lastInteraction = Util.getMillis();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int index = selectedIndex();
        if (keyCode == 264 && index + 1 < creatures.size()) { // flecha abajo
            selectedId = creatures.get(index + 1).id;
            ensureVisible(index + 1);
            updateButtons();
            return true;
        }
        if (keyCode == 265 && index > 0) { // flecha arriba
            selectedId = creatures.get(index - 1).id;
            ensureVisible(index - 1);
            updateButtons();
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && selected() != null) { // Enter
            openEdit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureVisible(int row) {
        int visible = Math.max(1, (listH - 4) / ROW_H);
        if (row < scroll) {
            scroll = row;
        } else if (row >= scroll + visible) {
            scroll = row - visible + 1;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
