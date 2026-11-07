package com.creaturestudio.client.screen;

import com.creaturestudio.client.render.AnimState;
import com.creaturestudio.client.render.Materials;
import com.creaturestudio.data.Behavior;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureJson;
import com.creaturestudio.data.CreaturePart;
import com.creaturestudio.data.DefCodec;
import com.creaturestudio.data.HitEffect;
import com.creaturestudio.data.MaterialIds;
import com.creaturestudio.data.Movement;
import com.creaturestudio.data.PartAnim;
import com.creaturestudio.data.PoseSolver;
import com.creaturestudio.data.RangedType;
import com.creaturestudio.data.SoundPreset;
import com.creaturestudio.data.SpawnTime;
import com.creaturestudio.data.Templates;
import com.creaturestudio.network.ModNetwork;
import com.creaturestudio.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Editor de criaturas al estilo Blockbench (modelo de cubos con jerarquia, texturas y animaciones)
 * y MCreator (estadisticas, IA, sonidos, botin y aparicion natural).
 */
public class EditorScreen extends Screen {
    private enum Mode {
        MODEL, BEHAVIOR
    }

    private static final int TOP = 24;
    private static final int ROW = 18;
    private static final int LIST_ROW = 11;
    private static final int FIELD_H = 16;
    private static final int TEMPLATE_ROW = 18;
    private static final int MAT_COLS = 9;
    private static final int MAT_CELL = 20;
    private static final int[] PALETTE = {
            0xFFFFFF, 0xD9D9D9, 0x7F7F7F, 0x2B2B2B, 0xE53935, 0xFB8C00, 0xFDD835, 0x7CB342,
            0x2E7D32, 0x26A69A, 0x29B6F6, 0x1E5BD8, 0x7E57C2, 0xD81B60, 0x8D6E63, 0xF2C79B
    };

    private final Screen parent;
    private CreatureDefinition def;
    private boolean isNew;
    private boolean dirty;
    private boolean nameEdited;
    private int selected;
    private Mode mode = Mode.MODEL;
    private int modelTab;
    private int behaviorTab;
    private int listScroll;
    private boolean animate = true;
    private boolean walk;
    private long attackStart = -100000L;
    private final Preview3D preview = new Preview3D();
    private boolean viewFitted;
    private boolean needsRebuild;
    @Nullable
    private Overlay overlay;

    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private String committed;
    private long lastChange;

    private boolean dragging;
    private int dragButton;
    private double pressX;
    private double pressY;
    private boolean moved;

    @Nullable
    private Component status;
    private int statusColor = Ui.TEXT;
    private long statusUntil;

    // Disposicion (se recalcula en init)
    private int leftW;
    private int rightW;
    private int rightX;
    private int listTop;
    private int listBottom;
    private int previewButtonX;
    private int previewButtonY;
    private int activeTabX;
    private int activeTabW;
    @Nullable
    private Button saveButton;
    private final List<NumberField> numberFields = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Hint> hints = new ArrayList<>();
    private final List<SwatchArea> swatches = new ArrayList<>();
    private int paletteX = -1;
    private int paletteY = -1;
    private int materialIconX = -1;
    private int materialIconY = -1;
    private int sizeInfoY = -1;
    private int sizeInfoX;

    private record Label(int x, int y, Component text, int color) {
    }

    private record Hint(int x, int y, int w, Component text) {
    }

    private record SwatchArea(int x, int y, int size, IntSupplier color, IntConsumer onPick) {
    }

    public EditorScreen(Screen parent, CreatureDefinition def, boolean isNew, boolean showTemplates) {
        super(Ui.tr("editor.title"));
        this.parent = parent;
        this.def = def;
        this.isNew = isNew;
        if (def.id.isEmpty()) {
            def.id = CreatureDefinition.newId();
        }
        this.selected = def.parts.isEmpty() ? -1 : 0;
        this.committed = CreatureJson.write(def, false);
        this.dirty = isNew;
        if (showTemplates) {
            this.overlay = new TemplateOverlay();
        }
    }

    // =======================================================================================
    // Construccion de la interfaz
    // =======================================================================================

    @Override
    protected void init() {
        numberFields.clear();
        labels.clear();
        hints.clear();
        swatches.clear();
        paletteX = -1;
        paletteY = -1;
        materialIconX = -1;
        materialIconY = -1;
        sizeInfoY = -1;
        saveButton = null;

        leftW = Mth.clamp(width / 4, 100, 136);
        rightW = Mth.clamp(width * 2 / 5, 170, 220);
        rightX = width - rightW;
        preview.setArea(leftW, TOP, rightX - leftW, height - TOP);
        if (!viewFitted) {
            preview.fit(def);
            viewFitted = true;
        }
        if (selected >= def.parts.size()) {
            selected = def.parts.size() - 1;
        }
        if (selected < 0 && !def.parts.isEmpty()) {
            selected = 0;
        }

        buildTopBar();
        if (mode == Mode.MODEL) {
            buildPartsPanel();
            buildModelPanel();
        } else {
            buildBehaviorNav();
            buildBehaviorPanel();
        }
        buildPreviewButtons();
        if (overlay != null) {
            overlay.layout();
        }
    }

    private void requestRebuild() {
        needsRebuild = true;
    }

    private void buildTopBar() {
        Component modelLabel = Ui.tr("editor.mode.model");
        Component behaviorLabel = Ui.tr("editor.mode.behavior");
        Component templatesLabel = Ui.tr("editor.templates");
        Component saveLabel = Ui.tr("editor.save");
        Component exitLabel = Ui.tr("editor.exit");
        int wModel = font.width(modelLabel) + 12;
        int wBehavior = font.width(behaviorLabel) + 12;
        int wTemplates = font.width(templatesLabel) + 12;
        int wSave = Math.max(46, font.width(saveLabel) + 12);
        int wExit = Math.max(40, font.width(exitLabel) + 12);
        int fixed = wModel + wBehavior + wTemplates + wSave + wExit + 30;
        int nameW = Mth.clamp(width - fixed, 60, 150);

        int x = 4;
        EditBox nameBox = new EditBox(font, x, 4, nameW, FIELD_H, Ui.tr("editor.name"));
        nameBox.setMaxLength(CreatureDefinition.NAME_MAX);
        nameBox.setValue(def.name);
        nameBox.setHint(Ui.tr("editor.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(s -> {
            def.name = s;
            nameEdited = true;
            markChanged();
        });
        addRenderableWidget(nameBox);
        x += nameW + 4;

        Button model = addRenderableWidget(Button.builder(modelLabel, b -> switchMode(Mode.MODEL))
                .bounds(x, 4, wModel, FIELD_H).build());
        model.active = mode != Mode.MODEL;
        if (mode == Mode.MODEL) {
            activeTabX = x;
            activeTabW = wModel;
        }
        x += wModel + 2;
        Button behavior = addRenderableWidget(Button.builder(behaviorLabel, b -> switchMode(Mode.BEHAVIOR))
                .bounds(x, 4, wBehavior, FIELD_H).build());
        behavior.active = mode != Mode.BEHAVIOR;
        if (mode == Mode.BEHAVIOR) {
            activeTabX = x;
            activeTabW = wBehavior;
        }
        x += wBehavior + 6;
        addRenderableWidget(Button.builder(templatesLabel, b -> openOverlay(new TemplateOverlay()))
                .bounds(x, 4, wTemplates, FIELD_H)
                .tooltip(Tooltip.create(Ui.tr("editor.templates_tooltip"))).build());

        int rx = width - 4 - wExit;
        addRenderableWidget(Button.builder(exitLabel, b -> onClose()).bounds(rx, 4, wExit, FIELD_H).build());
        rx -= wSave + 4;
        saveButton = addRenderableWidget(Button.builder(saveLabel, b -> save()).bounds(rx, 4, wSave, FIELD_H)
                .tooltip(Tooltip.create(Ui.tr("editor.save_tooltip"))).build());
    }

    private void switchMode(Mode newMode) {
        mode = newMode;
        requestRebuild();
    }

    private void buildPartsPanel() {
        int gap = 2;
        int bw = (leftW - 6 - gap) / 2;
        int y3 = height - 4 - FIELD_H;
        int y2 = y3 - FIELD_H - gap;
        int y1 = y2 - FIELD_H - gap;
        listTop = TOP + 14;
        listBottom = y1 - 4;
        boolean has = sel() != null;

        addRenderableWidget(Button.builder(Ui.tr("editor.part.add"), b -> addPart())
                .bounds(3, y1, bw, FIELD_H).tooltip(Tooltip.create(Ui.tr("editor.part.add_tooltip"))).build());
        Button dup = addRenderableWidget(Button.builder(Ui.tr("editor.part.duplicate"), b -> duplicateSelected(false))
                .bounds(3 + bw + gap, y1, bw, FIELD_H).tooltip(Tooltip.create(Ui.tr("editor.part.duplicate_tooltip"))).build());
        dup.active = has;
        Button del = addRenderableWidget(Button.builder(Ui.tr("editor.part.delete"), b -> deleteSelected())
                .bounds(3, y2, bw, FIELD_H).tooltip(Tooltip.create(Ui.tr("editor.part.delete_tooltip"))).build());
        del.active = has;
        Button mirror = addRenderableWidget(Button.builder(Ui.tr("editor.part.mirror"), b -> duplicateSelected(true))
                .bounds(3 + bw + gap, y2, bw, FIELD_H).tooltip(Tooltip.create(Ui.tr("editor.part.mirror_tooltip"))).build());
        mirror.active = has;
        Button center = addRenderableWidget(Button.builder(Ui.tr("editor.part.center_model"), b -> centerModel())
                .bounds(3, y3, leftW - 6, FIELD_H).tooltip(Tooltip.create(Ui.tr("editor.part.center_tooltip"))).build());
        center.active = !def.parts.isEmpty();
    }

    private void buildModelPanel() {
        int x = rightX + 5;
        int y = TOP + 4;
        int w = rightW - 10;
        int tw = (w - 4) / 3;
        String[] keys = {"editor.tab.shape", "editor.tab.look", "editor.tab.anim"};
        for (int i = 0; i < keys.length; i++) {
            final int tab = i;
            Button b = addRenderableWidget(Button.builder(Ui.tr(keys[i]), btn -> {
                modelTab = tab;
                requestRebuild();
            }).bounds(x + i * (tw + 2), y, tw, FIELD_H).build());
            b.active = modelTab != i;
        }
        y += 22;
        CreaturePart p = sel();
        if (p == null) {
            hints.add(new Hint(x, y, w, Ui.tr("editor.no_part")));
            return;
        }
        switch (modelTab) {
            case 0 -> buildShapeTab(p, x, y, w);
            case 1 -> buildLookTab(p, x, y, w);
            default -> buildAnimTab(p, x, y, w);
        }
    }

    private void buildShapeTab(CreaturePart p, int x, int y, int w) {
        int lw = 46;
        label(x, y, "editor.field.name");
        EditBox name = new EditBox(font, x + lw, y, w - lw, FIELD_H, Ui.tr("editor.field.name"));
        name.setMaxLength(24);
        name.setValue(p.name);
        name.setResponder(s -> {
            p.name = s;
            markChanged();
        });
        addRenderableWidget(name);
        y += ROW;

        label(x, y, "editor.field.parent");
        addRenderableWidget(Button.builder(parentLabel(p), b -> {
            cycleParent(p);
            b.setMessage(parentLabel(p));
            markChanged();
        }).bounds(x + lw, y, w - lw, FIELD_H).tooltip(Tooltip.create(Ui.tr("editor.field.parent_tooltip"))).build());
        y += ROW;

        final int index = selected;
        y = vecRow(x, y, w, "editor.field.position",
                () -> p.x, v -> moveSubtree(index, v - p.x, 0f, 0f),
                () -> p.y, v -> moveSubtree(index, 0f, v - p.y, 0f),
                () -> p.z, v -> moveSubtree(index, 0f, 0f, v - p.z),
                -512f, 512f, 1f);
        y = vecRow(x, y, w, "editor.field.size",
                () -> p.w, v -> p.w = v,
                () -> p.h, v -> p.h = v,
                () -> p.d, v -> p.d = v,
                0f, 256f, 1f);
        y = vecRow(x, y, w, "editor.field.pivot",
                () -> p.px, v -> p.px = v,
                () -> p.py, v -> p.py = v,
                () -> p.pz, v -> p.pz = v,
                -512f, 512f, 1f);
        y = vecRow(x, y, w, "editor.field.rotation",
                () -> p.rx, v -> p.rx = v,
                () -> p.ry, v -> p.ry = v,
                () -> p.rz, v -> p.rz = v,
                -360f, 360f, 5f);

        int bw = (w - 2) / 2;
        addRenderableWidget(Button.builder(Ui.tr("editor.pivot.center"), b -> {
            p.centerPivot();
            markChanged();
            requestRebuild();
        }).bounds(x, y, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Ui.tr("editor.rotation.reset"), b -> {
            p.rx = 0f;
            p.ry = 0f;
            p.rz = 0f;
            markChanged();
            requestRebuild();
        }).bounds(x + bw + 2, y, bw, FIELD_H).build());
        y += ROW + 4;
        hints.add(new Hint(x, y, w, Ui.tr("editor.hint.fields")));
    }

    private void buildLookTab(CreaturePart p, int x, int y, int w) {
        int lw = 46;
        label(x, y, "editor.field.color");
        swatches.add(new SwatchArea(x + lw, y, FIELD_H, () -> p.color, c -> {
            p.color = c;
            markChanged();
            requestRebuild();
        }));
        EditBox hex = new EditBox(font, x + lw + 20, y, 56, FIELD_H, Ui.tr("editor.field.color"));
        hex.setMaxLength(7);
        hex.setValue(CreatureJson.hex(p.color));
        hex.setResponder(s -> {
            int c = CreatureJson.parseHex(s);
            if (c >= 0 && c != p.color) {
                p.color = c;
                markChanged();
            }
        });
        addRenderableWidget(hex);
        int pickX = x + lw + 80;
        addRenderableWidget(Button.builder(Ui.tr("editor.pick"), b -> openOverlay(new ColorOverlay(p.color, c -> {
            p.color = c;
            markChanged();
            requestRebuild();
        }))).bounds(pickX, y, Math.max(20, x + w - pickX), FIELD_H).build());
        y += ROW;

        paletteX = x;
        paletteY = y;
        y += 30;

        label(x, y, "editor.field.texture");
        materialIconX = x + lw;
        materialIconY = y;
        addRenderableWidget(Button.builder(Ui.tr("editor.pick_texture"), b -> openOverlay(new MaterialOverlay(p)))
                .bounds(x + lw + 20, y, w - lw - 20, FIELD_H).build());
        y += ROW;

        EditBox material = new EditBox(font, x, y, w, FIELD_H, Ui.tr("editor.field.texture"));
        material.setMaxLength(128);
        material.setValue(p.material);
        material.setTextColor(Materials.exists(p.material) ? Ui.TEXT : Ui.ERROR);
        material.setResponder(s -> {
            p.material = s;
            material.setTextColor(Materials.exists(s) ? Ui.TEXT : Ui.ERROR);
            markChanged();
        });
        addRenderableWidget(material);
        y += ROW + 2;

        int bw = (w - 2) / 2;
        toggle(x, y, bw, "editor.field.stretch", () -> p.stretch, v -> p.stretch = v);
        toggle(x + bw + 2, y, bw, "editor.field.glow", () -> p.glow, v -> p.glow = v);
        y += ROW + 4;
        hints.add(new Hint(x, y, w, Ui.tr("editor.hint.look")));
    }

    private void buildAnimTab(CreaturePart p, int x, int y, int w) {
        int lw = 64;
        label(x, y, "editor.field.animation");
        addRenderableWidget(Button.builder(animLabel(p.anim), b -> {
            p.anim = hasShiftDown() ? p.anim.previous() : p.anim.next();
            b.setMessage(animLabel(p.anim));
            markChanged();
            requestRebuild();
        }).bounds(x + lw, y, w - lw, FIELD_H).tooltip(Tooltip.create(Ui.tr("editor.field.animation_tooltip"))).build());
        y += ROW + 2;
        hints.add(new Hint(x, y, w, Ui.tr("anim." + p.anim.key() + ".desc")));
        y += 44;
        label(x, y, "editor.field.amount");
        number(x + lw, y, w - lw, () -> p.animAmount, v -> p.animAmount = v, 0f, 5f, 0.1f, false);
        y += ROW;
        label(x, y, "editor.field.speed");
        number(x + lw, y, w - lw, () -> p.animSpeed, v -> p.animSpeed = v, 0f, 5f, 0.1f, false);
        y += ROW + 4;
        hints.add(new Hint(x, y, w, Ui.tr("editor.hint.anim")));
    }

    private void buildBehaviorNav() {
        String[] keys = {"editor.btab.stats", "editor.btab.ai", "editor.btab.spawn"};
        int y = TOP + 6;
        for (int i = 0; i < keys.length; i++) {
            final int tab = i;
            Button b = addRenderableWidget(Button.builder(Ui.tr(keys[i]), btn -> {
                behaviorTab = tab;
                requestRebuild();
            }).bounds(4, y, leftW - 8, 18).build());
            b.active = behaviorTab != i;
            y += 22;
        }
        hints.add(new Hint(6, y + 6, leftW - 12, Ui.tr("editor.bhint." + behaviorTab)));
    }

    private void buildBehaviorPanel() {
        int x = rightX + 5;
        int y = TOP + 6;
        int w = rightW - 10;
        switch (behaviorTab) {
            case 0 -> buildStatsTab(x, y, w);
            case 1 -> buildAiTab(x, y, w);
            default -> buildSpawnTab(x, y, w);
        }
    }

    private void buildStatsTab(int x, int y, int w) {
        int lw = 84;
        y = numRow(x, y, w, lw, "stat.health", () -> def.health, v -> def.health = v, 1f, 1024f, 1f, false);
        y = numRow(x, y, w, lw, "stat.damage", () -> def.damage, v -> def.damage = v, 0f, 200f, 0.5f, false);
        y = numRow(x, y, w, lw, "stat.speed", () -> def.speed, v -> def.speed = v, 0.01f, 1.5f, 0.01f, false);
        y = numRow(x, y, w, lw, "stat.armor", () -> def.armor, v -> def.armor = v, 0f, 30f, 1f, false);
        y = numRow(x, y, w, lw, "stat.knockback", () -> def.knockbackResistance, v -> def.knockbackResistance = v,
                0f, 1f, 0.05f, false);
        y = numRow(x, y, w, lw, "stat.follow_range", () -> def.followRange, v -> def.followRange = v,
                4f, 64f, 1f, false);
        y = numRow(x, y, w, lw, "stat.scale", () -> def.scale, v -> def.scale = v, 0.1f, 8f, 0.1f, false);
        y = numRow(x, y, w, lw, "stat.xp", () -> (float) def.xp, v -> def.xp = Math.round(v), 0f, 1000f, 1f, true);
        sizeInfoX = x;
        sizeInfoY = y + 2;
        hints.add(new Hint(x, y + 16, w, Ui.tr("editor.hint.stats")));
    }

    private void buildAiTab(int x, int y, int w) {
        int lw = 84;
        y = enumRow(x, y, w, lw, "ai.behavior", Behavior.values(), () -> def.behavior, v -> def.behavior = v,
                v -> Ui.tr("behavior." + v.key()));
        y = enumRow(x, y, w, lw, "ai.movement", Movement.values(), () -> def.movement, v -> def.movement = v,
                v -> Ui.tr("movement." + v.key()));
        y = enumRow(x, y, w, lw, "ai.ranged", RangedType.values(), () -> def.ranged, v -> def.ranged = v,
                v -> Ui.tr("ranged." + v.key()));
        y = enumRow(x, y, w, lw, "ai.hit_effect", HitEffect.values(), () -> def.hitEffect, v -> def.hitEffect = v,
                v -> Ui.tr("hit_effect." + v.key()));
        label(x, y, "ai.sound");
        int sw = w - lw - 20;
        enumButton(x + lw, y, sw, SoundPreset.values(), () -> def.sound, v -> def.sound = v,
                v -> Ui.tr("sound." + v.key()));
        addRenderableWidget(Button.builder(Component.literal("\u266A"), b -> playSound())
                .bounds(x + lw + sw + 2, y, 18, FIELD_H).tooltip(Tooltip.create(Ui.tr("ai.sound_test"))).build());
        y += ROW + 2;
        int bw = (w - 2) / 2;
        toggle(x, y, bw, "ai.fire_immune", () -> def.fireImmune, v -> def.fireImmune = v);
        toggle(x + bw + 2, y, bw, "ai.burns_in_sun", () -> def.burnsInSun, v -> def.burnsInSun = v);
        y += ROW + 2;
        y = itemRow(x, y, w, lw, "ai.tempt_item", () -> def.temptItem, v -> def.temptItem = v);
        hints.add(new Hint(x, y + 4, w, Ui.tr("editor.hint.ai")));
    }

    private void buildSpawnTab(int x, int y, int w) {
        int lw = 84;
        y = itemRow(x, y, w, lw, "loot.item", () -> def.dropItem, v -> def.dropItem = v);
        label(x, y, "loot.amount");
        int fw = (w - lw - 2) / 2;
        number(x + lw, y, fw, () -> (float) def.dropMin, v -> def.dropMin = Math.round(v), 0f, 64f, 1f, true);
        number(x + lw + fw + 2, y, fw, () -> (float) def.dropMax, v -> def.dropMax = Math.round(v), 0f, 64f, 1f, true);
        y += ROW + 4;

        toggle(x, y, w, "spawn.natural", () -> def.naturalSpawn, v -> def.naturalSpawn = v);
        y += ROW;
        y = enumRow(x, y, w, lw, "spawn.time", SpawnTime.values(), () -> def.spawnTime, v -> def.spawnTime = v,
                v -> Ui.tr("spawn_time." + v.key()));
        label(x, y, "spawn.biomes");
        EditBox biomes = new EditBox(font, x + lw, y, w - lw, FIELD_H, Ui.tr("spawn.biomes"));
        biomes.setMaxLength(512);
        biomes.setValue(def.spawnBiomes);
        biomes.setHint(Ui.tr("spawn.biomes_hint").withStyle(ChatFormatting.DARK_GRAY));
        biomes.setResponder(s -> {
            def.spawnBiomes = s;
            markChanged();
        });
        addRenderableWidget(biomes);
        y += ROW;
        y = numRow(x, y, w, lw, "spawn.chance", () -> (float) def.spawnChance, v -> def.spawnChance = Math.round(v),
                1f, 100f, 1f, true);
        y = numRow(x, y, w, lw, "spawn.max", () -> (float) def.spawnMax, v -> def.spawnMax = Math.round(v),
                1f, 32f, 1f, true);
        y += 4;

        label(x, y, "egg.colors");
        y += 12;
        swatches.add(new SwatchArea(x, y, FIELD_H, () -> def.eggColor1, c -> {
            def.eggColor1 = c;
            markChanged();
            requestRebuild();
        }));
        hexBox(x + 18, y, 52, () -> def.eggColor1, c -> def.eggColor1 = c);
        swatches.add(new SwatchArea(x + 76, y, FIELD_H, () -> def.eggColor2, c -> {
            def.eggColor2 = c;
            markChanged();
            requestRebuild();
        }));
        hexBox(x + 94, y, 52, () -> def.eggColor2, c -> def.eggColor2 = c);
        y += ROW + 2;
        hints.add(new Hint(x, y, w, Ui.tr("editor.hint.spawn")));
    }

    private void buildPreviewButtons() {
        previewButtonX = preview.x + 4;
        previewButtonY = preview.y + 4;
        previewButton(Ui.onOff("editor.preview.animate", animate), b -> {
            animate = !animate;
            b.setMessage(Ui.onOff("editor.preview.animate", animate));
        });
        previewButton(Ui.onOff("editor.preview.walk", walk), b -> {
            walk = !walk;
            b.setMessage(Ui.onOff("editor.preview.walk", walk));
        });
        previewButton(Ui.tr("editor.preview.attack"), b -> attackStart = Util.getMillis());
        previewButton(Ui.tr("editor.preview.reset"), b -> preview.fit(def));
    }

    private void previewButton(Component text, Button.OnPress onPress) {
        int bw = font.width(text) + 10;
        int maxX = preview.x + preview.w - 4;
        if (previewButtonX + bw > maxX && previewButtonX > preview.x + 4) {
            previewButtonX = preview.x + 4;
            previewButtonY += 16;
        }
        addRenderableWidget(Button.builder(text, onPress).bounds(previewButtonX, previewButtonY, bw, 14).build());
        previewButtonX += bw + 2;
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes de widgets
    // ---------------------------------------------------------------------------------------

    private void label(int x, int y, String key) {
        labels.add(new Label(x, y + 4, Ui.tr(key), Ui.MUTED));
    }

    private NumberField number(int x, int y, int w, Supplier<Float> getter, Consumer<Float> setter,
                               float min, float max, float step, boolean integer) {
        NumberField field = new NumberField(font, x, y, w, FIELD_H, getter, setter, min, max, step, integer,
                this::markChanged);
        numberFields.add(field);
        addRenderableWidget(field);
        return field;
    }

    private int vecRow(int x, int y, int w, String key,
                       Supplier<Float> gx, Consumer<Float> sx,
                       Supplier<Float> gy, Consumer<Float> sy,
                       Supplier<Float> gz, Consumer<Float> sz,
                       float min, float max, float step) {
        int lw = 46;
        int fw = (w - lw - 4) / 3;
        int fx = x + lw;
        label(x, y, key);
        number(fx, y, fw, gx, sx, min, max, step, false).setTextColor(0xFFFF9A9A);
        number(fx + fw + 2, y, fw, gy, sy, min, max, step, false).setTextColor(0xFF9AFF9A);
        number(fx + (fw + 2) * 2, y, fw, gz, sz, min, max, step, false).setTextColor(0xFF9AC4FF);
        return y + ROW;
    }

    private int numRow(int x, int y, int w, int lw, String key, Supplier<Float> getter, Consumer<Float> setter,
                       float min, float max, float step, boolean integer) {
        label(x, y, key);
        number(x + lw, y, w - lw, getter, setter, min, max, step, integer);
        return y + ROW;
    }

    private <E extends Enum<E>> int enumRow(int x, int y, int w, int lw, String key, E[] values, Supplier<E> getter,
                                            Consumer<E> setter, Function<E, Component> name) {
        label(x, y, key);
        enumButton(x + lw, y, w - lw, values, getter, setter, name);
        return y + ROW;
    }

    private <E extends Enum<E>> void enumButton(int x, int y, int w, E[] values, Supplier<E> getter, Consumer<E> setter,
                                                Function<E, Component> name) {
        addRenderableWidget(Button.builder(name.apply(getter.get()), b -> {
            int n = values.length;
            int i = getter.get().ordinal();
            E next = values[(i + (hasShiftDown() ? n - 1 : 1)) % n];
            setter.accept(next);
            b.setMessage(name.apply(next));
            markChanged();
        }).bounds(x, y, w, FIELD_H).build());
    }

    private void toggle(int x, int y, int w, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(Ui.onOff(key, getter.getAsBoolean()), b -> {
            boolean v = !getter.getAsBoolean();
            setter.accept(v);
            b.setMessage(Ui.onOff(key, v));
            markChanged();
        }).bounds(x, y, w, FIELD_H).build());
    }

    private int itemRow(int x, int y, int w, int lw, String key, Supplier<String> getter, Consumer<String> setter) {
        label(x, y, key);
        EditBox box = new EditBox(font, x + lw, y, w - lw, FIELD_H, Ui.tr(key));
        box.setMaxLength(128);
        box.setValue(getter.get());
        box.setHint(Ui.tr("field.item_hint").withStyle(ChatFormatting.DARK_GRAY));
        box.setTextColor(itemColor(getter.get()));
        box.setResponder(v -> {
            setter.accept(v.trim());
            box.setTextColor(itemColor(v));
            markChanged();
        });
        addRenderableWidget(box);
        return y + ROW;
    }

    private void hexBox(int x, int y, int w, IntSupplier getter, IntConsumer setter) {
        EditBox box = new EditBox(font, x, y, w, FIELD_H, Ui.tr("editor.field.color"));
        box.setMaxLength(7);
        box.setValue(CreatureJson.hex(getter.getAsInt()));
        box.setResponder(s -> {
            int c = CreatureJson.parseHex(s);
            if (c >= 0 && c != getter.getAsInt()) {
                setter.accept(c);
                markChanged();
            }
        });
        addRenderableWidget(box);
    }

    private static int itemColor(String id) {
        if (id == null || id.isBlank()) {
            return Ui.TEXT;
        }
        String clean = CreatureDefinition.cleanResource(id);
        if (clean.isEmpty()) {
            return Ui.ERROR;
        }
        ResourceLocation rl = ResourceLocation.tryParse(clean);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? Ui.OK : Ui.ERROR;
    }

    private Component parentLabel(CreaturePart p) {
        if (p.parent < 0 || p.parent >= def.parts.size()) {
            return Ui.tr("editor.field.parent_none");
        }
        return Component.literal(def.parts.get(p.parent).name);
    }

    private static Component animLabel(PartAnim anim) {
        return Ui.tr("anim." + anim.key());
    }

    // =======================================================================================
    // Operaciones del modelo
    // =======================================================================================

    @Nullable
    private CreaturePart sel() {
        return selected >= 0 && selected < def.parts.size() ? def.parts.get(selected) : null;
    }

    private void select(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        ensureVisible(index);
        requestRebuild();
    }

    private void addPart() {
        if (def.parts.size() >= CreatureDefinition.MAX_PARTS) {
            showNotice(Ui.tr("editor.too_many_parts", CreatureDefinition.MAX_PARTS), false);
            return;
        }
        CreaturePart p = CreaturePart.box(Ui.tr("editor.part.default_name", def.parts.size() + 1).getString(),
                -2, 0, -2, 4, 4, 4);
        p.color = PALETTE[(def.parts.size() * 5 + 4) % PALETTE.length];
        p.material = MaterialIds.SMOOTH;
        CreaturePart s = sel();
        if (s != null) {
            // Nuevo cubo encima de la pieza seleccionada y como hijo suyo.
            p.x = s.x + s.w / 2f - 2f;
            p.z = s.z + s.d / 2f - 2f;
            p.y = s.y + s.h;
            p.centerPivot();
            p.parent = selected;
        }
        def.parts.add(p);
        selected = def.parts.size() - 1;
        ensureVisible(selected);
        markChanged();
        requestRebuild();
    }

    /** Indice de la pieza y todas sus descendientes (la raiz primero). */
    private List<Integer> subtree(int root) {
        List<Integer> out = new ArrayList<>();
        out.add(root);
        for (int i = 0; i < def.parts.size(); i++) {
            if (i != root && PoseSolver.isDescendant(def.parts, i, root)) {
                out.add(i);
            }
        }
        return out;
    }

    private void duplicateSelected(boolean mirror) {
        if (sel() == null) {
            return;
        }
        List<Integer> nodes = subtree(selected);
        if (def.parts.size() + nodes.size() > CreatureDefinition.MAX_PARTS) {
            showNotice(Ui.tr("editor.too_many_parts", CreatureDefinition.MAX_PARTS), false);
            return;
        }
        Map<Integer, Integer> remap = new HashMap<>();
        int base = def.parts.size();
        for (int k = 0; k < nodes.size(); k++) {
            remap.put(nodes.get(k), base + k);
        }
        for (int index : nodes) {
            CreaturePart src = def.parts.get(index);
            CreaturePart copy = mirror ? src.mirroredX() : src.copy();
            if (!mirror) {
                copy.translate(2f, 0f, 0f);
            }
            copy.parent = remap.getOrDefault(src.parent, src.parent);
            def.parts.add(copy);
        }
        selected = base;
        ensureVisible(selected);
        markChanged();
        requestRebuild();
    }

    private void deleteSelected() {
        int i = selected;
        if (i < 0 || i >= def.parts.size()) {
            return;
        }
        int parentOfDeleted = def.parts.get(i).parent;
        def.parts.remove(i);
        for (CreaturePart p : def.parts) {
            if (p.parent == i) {
                p.parent = parentOfDeleted;
            }
            if (p.parent > i) {
                p.parent--;
            }
        }
        selected = Math.min(i, def.parts.size() - 1);
        markChanged();
        requestRebuild();
    }

    private void moveSubtree(int root, float dx, float dy, float dz) {
        if (root < 0 || root >= def.parts.size() || (dx == 0f && dy == 0f && dz == 0f)) {
            return;
        }
        for (int index : subtree(root)) {
            def.parts.get(index).translate(dx, dy, dz);
        }
    }

    private void centerModel() {
        def.centerOnGround();
        preview.fit(def);
        markChanged();
        requestRebuild();
    }

    private void cycleParent(CreaturePart p) {
        List<Integer> options = new ArrayList<>();
        options.add(-1);
        for (int j = 0; j < def.parts.size(); j++) {
            if (j != selected && !PoseSolver.isDescendant(def.parts, j, selected)) {
                options.add(j);
            }
        }
        int current = Math.max(0, options.indexOf(p.parent));
        int step = hasShiftDown() ? options.size() - 1 : 1;
        p.parent = options.get((current + step) % options.size());
    }

    private void applyTemplate(Templates.Template template) {
        CreatureDefinition fresh = template.create();
        fresh.id = def.id;
        fresh.owner = def.owner;
        fresh.revision = def.revision;
        if (nameEdited || !isNew) {
            fresh.name = def.name;
        } else {
            fresh.name = Component.translatable("template.creaturestudio." + template.key()).getString();
        }
        def = fresh;
        selected = def.parts.isEmpty() ? -1 : 0;
        listScroll = 0;
        preview.fit(def);
        markChanged();
        requestRebuild();
    }

    /** Filas de la lista de piezas en orden de arbol: {indice, profundidad}. */
    private List<int[]> displayRows() {
        int n = def.parts.size();
        int[] parents = PoseSolver.safeParents(def.parts);
        List<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            children.add(new ArrayList<>());
        }
        List<Integer> roots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (parents[i] >= 0) {
                children.get(parents[i]).add(i);
            } else {
                roots.add(i);
            }
        }
        List<int[]> rows = new ArrayList<>();
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        for (int r = roots.size() - 1; r >= 0; r--) {
            stack.push(new int[]{roots.get(r), 0});
        }
        while (!stack.isEmpty()) {
            int[] node = stack.pop();
            rows.add(node);
            List<Integer> kids = children.get(node[0]);
            for (int k = kids.size() - 1; k >= 0; k--) {
                stack.push(new int[]{kids.get(k), node[1] + 1});
            }
        }
        return rows;
    }

    private void ensureVisible(int index) {
        if (mode != Mode.MODEL || listBottom <= listTop) {
            return;
        }
        List<int[]> rows = displayRows();
        int row = -1;
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r)[0] == index) {
                row = r;
                break;
            }
        }
        if (row < 0) {
            return;
        }
        int visible = Math.max(1, (listBottom - listTop) / LIST_ROW);
        if (row < listScroll) {
            listScroll = row;
        } else if (row >= listScroll + visible) {
            listScroll = row - visible + 1;
        }
    }

    // =======================================================================================
    // Deshacer / guardar
    // =======================================================================================

    private void markChanged() {
        dirty = true;
        String now = CreatureJson.write(def, false);
        if (!now.equals(committed)) {
            long t = Util.getMillis();
            if (t - lastChange > 700 || undoStack.isEmpty()) {
                undoStack.push(committed);
                while (undoStack.size() > 100) {
                    undoStack.removeLast();
                }
            }
            redoStack.clear();
            committed = now;
            lastChange = t;
        }
        for (NumberField f : numberFields) {
            f.refresh();
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            showNotice(Ui.tr("editor.nothing_to_undo"), false);
            return;
        }
        redoStack.push(committed);
        committed = undoStack.pop();
        loadState(committed);
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(committed);
        committed = redoStack.pop();
        loadState(committed);
    }

    private void loadState(String json) {
        try {
            def = CreatureJson.read(json);
        } catch (RuntimeException e) {
            return;
        }
        if (selected >= def.parts.size()) {
            selected = def.parts.size() - 1;
        }
        dirty = true;
        lastChange = 0L;
        requestRebuild();
    }

    private void save() {
        CreatureDefinition copy = def.copy();
        copy.sanitize();
        if (copy.id.isEmpty()) {
            def.id = CreatureDefinition.newId();
            copy.id = def.id;
        }
        byte[] data = DefCodec.encode(copy);
        if (data.length > ModNetwork.MAX_UPLOAD_BYTES) {
            showNotice(Ui.tr("editor.too_big"), false);
            return;
        }
        PacketDistributor.sendToServer(new Payloads.SaveCreature(data));
        dirty = false;
        isNew = false;
        showNotice(Ui.tr("editor.saving"), true);
    }

    public void showNotice(Component message, boolean success) {
        status = message;
        statusColor = success ? Ui.OK : Ui.ERROR;
        statusUntil = Util.getMillis() + 3500;
        if (!success) {
            dirty = true; // p. ej. el servidor rechazo el guardado: sigue habiendo cambios sin guardar
        }
    }

    private void returnToParent() {
        if (parent instanceof LibraryScreen library) {
            library.selectCreature(def.id);
        }
        minecraft.setScreen(parent);
    }

    private void playSound() {
        SoundPreset s = def.sound;
        String id = !s.ambient.isEmpty() ? s.ambient : s.hurt;
        if (id.isEmpty()) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return;
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(rl)
                .ifPresent(event -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f)));
    }

    private AnimState currentAnim() {
        long now = Util.getMillis();
        float t = animate ? (now % 3_600_000L) / 50f : 0f;
        float elapsed = (now - attackStart) / 350f;
        float attack = elapsed >= 0f && elapsed < 1f ? elapsed : 0f;
        boolean flyer = def.movement == Movement.FLY;
        float headYaw = animate ? Mth.sin(t * 0.04f) * 20f : 0f;
        float headPitch = animate ? Mth.sin(t * 0.03f) * 8f : 0f;
        return new AnimState(walk ? t * 0.35f : 0f, walk ? 0.8f : 0f, t, headYaw, headPitch, attack,
                flyer && animate);
    }

    // =======================================================================================
    // Dibujado
    // =======================================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Ui.BG);
        g.fill(0, 0, width, TOP, Ui.PANEL_DARK);
        g.hLine(0, width, TOP - 1, Ui.BORDER);
        g.fill(0, TOP, leftW, height, Ui.PANEL);
        g.vLine(leftW - 1, TOP, height, Ui.BORDER);
        g.fill(rightX, TOP, width, height, Ui.PANEL);
        g.vLine(rightX, TOP, height, Ui.BORDER);
        g.fillGradient(preview.x, preview.y, preview.x + preview.w, preview.y + preview.h,
                Ui.PREVIEW_TOP, Ui.PREVIEW_BOTTOM);
        preview.render(g, def, currentAnim(), mode == Mode.MODEL ? selected : -1, true);
        if (mode == Mode.MODEL) {
            drawPartsList(g, mouseX, mouseY);
        }
    }

    private void drawPartsList(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Ui.tr("editor.parts", def.parts.size(), CreatureDefinition.MAX_PARTS), 5, TOP + 3,
                Ui.MUTED, false);
        List<int[]> rows = displayRows();
        int visible = Math.max(1, (listBottom - listTop) / LIST_ROW);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, rows.size() - visible));
        g.fill(2, listTop - 1, leftW - 3, listBottom + 1, Ui.PANEL_DARK);
        g.enableScissor(2, listTop, leftW - 3, listBottom);
        for (int r = 0; r < visible + 1 && r + listScroll < rows.size(); r++) {
            int[] row = rows.get(r + listScroll);
            int index = row[0];
            int depth = row[1];
            int y = listTop + r * LIST_ROW;
            boolean isSelected = index == selected;
            boolean hover = Ui.inside(mouseX, mouseY, 2, y, leftW - 5, LIST_ROW);
            if (isSelected) {
                g.fill(2, y, leftW - 3, y + LIST_ROW, Ui.SELECT);
            } else if (hover) {
                g.fill(2, y, leftW - 3, y + LIST_ROW, 0x30FFFFFF);
            }
            CreaturePart p = def.parts.get(index);
            int x = 5 + Math.min(depth, 6) * 6;
            g.fill(x, y + 2, x + 7, y + 9, 0xFF000000 | p.color);
            g.renderOutline(x, y + 2, 7, 7, 0xFF000000);
            int textW = leftW - x - 22;
            String name = font.plainSubstrByWidth(p.name, Math.max(10, textW));
            g.drawString(font, name, x + 10, y + 2, isSelected ? 0xFFFFFFFF : Ui.TEXT, false);
            if (p.anim != PartAnim.NONE) {
                g.drawString(font, "~", leftW - 12, y + 1, Ui.ACCENT, false);
            }
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            Ui.wrapped(g, font, Ui.tr("editor.no_parts"), 6, listTop + 4, leftW - 12, Ui.MUTED);
        }
        if (rows.size() > visible) {
            int trackH = listBottom - listTop;
            int barH = Math.max(8, trackH * visible / rows.size());
            int barY = listTop + (trackH - barH) * listScroll / Math.max(1, rows.size() - visible);
            g.fill(leftW - 5, barY, leftW - 3, barY + barH, 0xFF6A7080);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildWidgets();
        }
        boolean blocked = overlay != null;
        int mx = blocked ? -10000 : mouseX;
        int my = blocked ? -10000 : mouseY;
        super.render(g, mx, my, partialTick);

        g.fill(activeTabX, 4 + FIELD_H, activeTabX + activeTabW, 4 + FIELD_H + 2, Ui.ACCENT);
        if (dirty && saveButton != null) {
            g.fill(saveButton.getX() + 2, saveButton.getY() + 2, saveButton.getX() + 5, saveButton.getY() + 5, 0xFFFFA040);
        }

        for (Label l : labels) {
            g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
        }
        for (Hint h : hints) {
            Ui.wrapped(g, font, h.text(), h.x(), h.y(), h.w(), 0xFF7D8491);
        }
        for (SwatchArea s : swatches) {
            Ui.swatch(g, s.x(), s.y(), s.size(), s.color().getAsInt(), Ui.inside(mx, my, s.x(), s.y(), s.size(), s.size()));
        }

        CreaturePart p = sel();
        if (mode == Mode.MODEL && modelTab == 1 && p != null) {
            drawPalette(g, mx, my, p);
            if (materialIconX >= 0) {
                float r = ((p.color >> 16) & 255) / 255f;
                float gg = ((p.color >> 8) & 255) / 255f;
                float b = (p.color & 255) / 255f;
                g.fill(materialIconX - 1, materialIconY - 1, materialIconX + 17, materialIconY + 17, 0xFF000000);
                g.blit(materialIconX, materialIconY, 0, 16, 16, Materials.sprite(p.material), r, gg, b, 1f);
                String name = font.plainSubstrByWidth(Materials.displayName(p.material).getString(), rightW - 14);
                g.drawString(font, name, rightX + 5, materialIconY + ROW * 2 + 4, Ui.ACCENT, false);
            }
        }
        if (mode == Mode.BEHAVIOR && behaviorTab == 0 && sizeInfoY >= 0) {
            float[] hb = def.hitbox();
            g.drawString(font, Ui.tr("editor.size_info", Ui.fmt(Math.round(hb[0] * 100f) / 100f),
                    Ui.fmt(Math.round(hb[1] * 100f) / 100f)), sizeInfoX, sizeInfoY, Ui.ACCENT, false);
        }

        // Ayuda en la parte inferior de la vista previa
        Component help = Ui.tr(mode == Mode.MODEL ? "editor.preview.help" : "editor.preview.help_behavior");
        List<FormattedCharSequence> lines = font.split(help, Math.max(20, preview.w - 8));
        int hy = preview.y + preview.h - 4 - lines.size() * 10;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, preview.x + 4, hy, 0xFF8A919E, false);
            hy += 10;
        }

        if (status != null && Util.getMillis() < statusUntil) {
            int sw = Math.min(preview.w - 8, font.width(status) + 12);
            int sx = preview.x + (preview.w - sw) / 2;
            int sy = preview.y + preview.h - 18 - lines.size() * 10;
            g.fill(sx, sy, sx + sw, sy + 14, 0xD0101216);
            g.renderOutline(sx, sy, sw, 14, statusColor);
            g.drawCenteredString(font, status, sx + sw / 2, sy + 3, statusColor);
        }

        if (overlay != null) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 400f);
            g.fill(0, 0, width, height, 0x60000000);
            overlay.render(g, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    private void drawPalette(GuiGraphics g, int mx, int my, CreaturePart p) {
        if (paletteX < 0) {
            return;
        }
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = paletteX + (i % 8) * 14;
            int sy = paletteY + (i / 8) * 14;
            boolean hover = Ui.inside(mx, my, sx, sy, 12, 12);
            Ui.swatch(g, sx, sy, 12, PALETTE[i], hover || PALETTE[i] == p.color);
        }
    }

    // =======================================================================================
    // Entrada
    // =======================================================================================

    private boolean textFocused() {
        return getFocused() instanceof EditBox box && box.isFocused();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overlay != null) {
            if (!overlay.inside(mouseX, mouseY) && button == 0) {
                closeOverlay();
                return true;
            }
            return overlay.mouseClicked(mouseX, mouseY, button);
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (SwatchArea s : swatches) {
            if (Ui.inside(mouseX, mouseY, s.x(), s.y(), s.size(), s.size())) {
                openOverlay(new ColorOverlay(s.color().getAsInt(), s.onPick()));
                return true;
            }
        }
        CreaturePart p = sel();
        if (mode == Mode.MODEL && modelTab == 1 && p != null && paletteX >= 0) {
            for (int i = 0; i < PALETTE.length; i++) {
                int sx = paletteX + (i % 8) * 14;
                int sy = paletteY + (i / 8) * 14;
                if (Ui.inside(mouseX, mouseY, sx, sy, 12, 12)) {
                    p.color = PALETTE[i];
                    markChanged();
                    requestRebuild();
                    return true;
                }
            }
            if (materialIconX >= 0 && Ui.inside(mouseX, mouseY, materialIconX, materialIconY, 16, 16)) {
                openOverlay(new MaterialOverlay(p));
                return true;
            }
        }
        if (mode == Mode.MODEL && Ui.inside(mouseX, mouseY, 2, listTop, leftW - 5, listBottom - listTop)) {
            int row = (int) ((mouseY - listTop) / LIST_ROW) + listScroll;
            List<int[]> rows = displayRows();
            if (row >= 0 && row < rows.size()) {
                select(rows.get(row)[0]);
            }
            setFocused(null);
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            dragging = true;
            dragButton = button;
            pressX = mouseX;
            pressY = mouseY;
            moved = false;
            setFocused(null);
            return true;
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (overlay != null) {
            return overlay.mouseDragged(mouseX, mouseY, button);
        }
        if (dragging) {
            if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) {
                moved = true;
            }
            if (dragButton == 0) {
                preview.rotate(dragX, dragY);
            } else {
                preview.pan(dragX, dragY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (overlay != null) {
            return overlay.mouseReleased(mouseX, mouseY, button);
        }
        if (dragging && button == dragButton) {
            dragging = false;
            if (!moved && button == 0 && mode == Mode.MODEL) {
                int hit = preview.pick(def, currentAnim(), mouseX, mouseY);
                if (hit >= 0) {
                    select(hit);
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overlay != null) {
            return overlay.mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (scrollY != 0) {
            for (NumberField f : numberFields) {
                if (f.visible && f.isMouseOver(mouseX, mouseY)) {
                    f.nudge(Math.signum(scrollY));
                    return true;
                }
            }
        }
        if (mode == Mode.MODEL && Ui.inside(mouseX, mouseY, 0, listTop, leftW, listBottom - listTop)) {
            listScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            preview.zoomBy(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlay != null) {
            return overlay.keyPressed(keyCode);
        }
        if (hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (hasShiftDown()) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_S) {
                save();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_D && !textFocused() && mode == Mode.MODEL) {
                duplicateSelected(false);
                return true;
            }
        }
        if (!textFocused() && mode == Mode.MODEL && sel() != null) {
            float step = hasShiftDown() ? 0.25f : (hasControlDown() ? 4f : 1f);
            switch (keyCode) {
                case GLFW.GLFW_KEY_DELETE -> {
                    deleteSelected();
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    nudgeSelected(-step, 0f, 0f);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    nudgeSelected(step, 0f, 0f);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    nudgeSelected(0f, 0f, -step);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    nudgeSelected(0f, 0f, step);
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_UP -> {
                    nudgeSelected(0f, step, 0f);
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_DOWN -> {
                    nudgeSelected(0f, -step, 0f);
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void nudgeSelected(float dx, float dy, float dz) {
        moveSubtree(selected, dx, dy, dz);
        markChanged();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (overlay != null) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (overlay != null) {
            closeOverlay();
            return;
        }
        if (dirty) {
            minecraft.setScreen(new ConfirmScreen(discard -> {
                if (discard) {
                    returnToParent();
                } else {
                    minecraft.setScreen(this);
                }
            }, Ui.tr("editor.discard_title"), Ui.tr("editor.discard_message")));
        } else {
            returnToParent();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =======================================================================================
    // Paneles flotantes
    // =======================================================================================

    private void openOverlay(Overlay o) {
        overlay = o;
        o.layout();
        setFocused(null);
    }

    private void closeOverlay() {
        overlay = null;
    }

    private abstract class Overlay {
        protected int ox;
        protected int oy;
        protected int ow;
        protected int oh;

        abstract void layout();

        abstract void render(GuiGraphics g, int mx, int my);

        boolean mouseClicked(double mx, double my, int button) {
            return true;
        }

        boolean mouseDragged(double mx, double my, int button) {
            return true;
        }

        boolean mouseReleased(double mx, double my, int button) {
            return true;
        }

        boolean mouseScrolled(double mx, double my, double amount) {
            return true;
        }

        boolean keyPressed(int key) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeOverlay();
            }
            return true;
        }

        boolean inside(double mx, double my) {
            return Ui.inside(mx, my, ox, oy, ow, oh);
        }
    }

    /** Lista de plantillas para empezar rapido. */
    private final class TemplateOverlay extends Overlay {
        @Override
        void layout() {
            ow = Math.min(280, width - 20);
            oh = 26 + Templates.ALL.size() * TEMPLATE_ROW + 26;
            ox = (width - ow) / 2;
            oy = Math.max(4, (height - oh) / 2);
        }

        private int cancelX() {
            return ox + ow / 2 - 45;
        }

        private int cancelY() {
            return oy + oh - 22;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Ui.popup(g, ox, oy, ow, oh);
            g.drawCenteredString(font, Ui.tr("templates.title"), ox + ow / 2, oy + 7, Ui.TEXT);
            int y = oy + 22;
            for (Templates.Template t : Templates.ALL) {
                boolean hover = Ui.inside(mx, my, ox + 4, y, ow - 8, TEMPLATE_ROW - 2);
                g.fill(ox + 4, y, ox + ow - 4, y + TEMPLATE_ROW - 2, hover ? 0xFF46506A : 0xFF333946);
                Component name = Component.translatable("template.creaturestudio." + t.key());
                g.drawString(font, name, ox + 10, y + 4, Ui.TEXT, false);
                int nameW = font.width(name);
                String desc = Component.translatable("template.creaturestudio." + t.key() + ".desc").getString();
                desc = font.plainSubstrByWidth(desc, Math.max(10, ow - nameW - 30));
                g.drawString(font, desc, ox + ow - 10 - font.width(desc), y + 4, Ui.MUTED, false);
                y += TEMPLATE_ROW;
            }
            Ui.flatButton(g, font, cancelX(), cancelY(), 90, 16, Ui.tr("templates.cancel"),
                    Ui.inside(mx, my, cancelX(), cancelY(), 90, 16));
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            int y = oy + 22;
            for (Templates.Template t : Templates.ALL) {
                if (Ui.inside(mx, my, ox + 4, y, ow - 8, TEMPLATE_ROW - 2)) {
                    applyTemplate(t);
                    closeOverlay();
                    return true;
                }
                y += TEMPLATE_ROW;
            }
            if (Ui.inside(mx, my, cancelX(), cancelY(), 90, 16)) {
                closeOverlay();
            }
            return true;
        }
    }

    /** Selector de color HSV con cuadrado de saturacion/brillo y barra de tono. */
    private final class ColorOverlay extends Overlay {
        private float hue;
        private float sat;
        private float val;
        private final int original;
        private final IntConsumer apply;
        private boolean dragSv;
        private boolean dragHue;

        ColorOverlay(int rgb, IntConsumer apply) {
            this.original = rgb & 0xFFFFFF;
            this.apply = apply;
            float[] hsv = Ui.rgbToHsv(rgb);
            this.hue = hsv[0];
            this.sat = hsv[1];
            this.val = hsv[2];
        }

        private int current() {
            return Ui.hsvToRgb(hue, sat, val);
        }

        @Override
        void layout() {
            ow = 200;
            oh = 176;
            ox = (width - ow) / 2;
            oy = Math.max(4, (height - oh) / 2);
        }

        private int svX() {
            return ox + 8;
        }

        private int svY() {
            return oy + 20;
        }

        private int hueX() {
            return ox + 114;
        }

        private int paletteY() {
            return oy + 126;
        }

        private int buttonsY() {
            return oy + oh - 22;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Ui.popup(g, ox, oy, ow, oh);
            g.drawString(font, Ui.tr("color.title"), ox + 8, oy + 7, Ui.TEXT, false);
            int sx = svX();
            int sy = svY();
            for (int i = 0; i < 100; i += 2) {
                int top = 0xFF000000 | Ui.hsvToRgb(hue, (i + 1) / 100f, 1f);
                g.fillGradient(sx + i, sy, sx + i + 2, sy + 100, top, 0xFF000000);
            }
            g.renderOutline(sx - 1, sy - 1, 102, 102, 0xFF000000);
            int cx = sx + Math.round(sat * 99f);
            int cy = sy + Math.round((1f - val) * 99f);
            g.renderOutline(cx - 2, cy - 2, 5, 5, 0xFFFFFFFF);

            int hx = hueX();
            for (int k = 0; k < 6; k++) {
                int c1 = 0xFF000000 | Ui.hsvToRgb(k / 6f, 1f, 1f);
                int c2 = 0xFF000000 | Ui.hsvToRgb((k + 1) / 6f, 1f, 1f);
                int y0 = sy + k * 100 / 6;
                int y1 = sy + (k + 1) * 100 / 6;
                g.fillGradient(hx, y0, hx + 12, y1, c1, c2);
            }
            g.renderOutline(hx - 1, sy - 1, 14, 102, 0xFF000000);
            int hy = sy + Math.round(hue * 99f);
            g.fill(hx - 2, hy - 1, hx + 14, hy + 1, 0xFFFFFFFF);

            int px = ox + 134;
            g.drawString(font, Ui.tr("color.new"), px, sy, Ui.MUTED, false);
            g.fill(px, sy + 10, px + 58, sy + 32, 0xFF000000 | current());
            g.renderOutline(px, sy + 10, 58, 22, 0xFF000000);
            g.drawString(font, Ui.tr("color.old"), px, sy + 38, Ui.MUTED, false);
            g.fill(px, sy + 48, px + 58, sy + 70, 0xFF000000 | original);
            g.renderOutline(px, sy + 48, 58, 22, 0xFF000000);
            g.drawString(font, CreatureJson.hex(current()), px, sy + 78, Ui.TEXT, false);

            for (int i = 0; i < PALETTE.length; i++) {
                int qx = ox + 8 + i * 11;
                Ui.swatch(g, qx, paletteY(), 10, PALETTE[i], Ui.inside(mx, my, qx, paletteY(), 10, 10));
            }

            int by = buttonsY();
            Ui.flatButton(g, font, ox + ow - 8 - 120 - 4, by, 60, 16, Ui.tr("color.ok"),
                    Ui.inside(mx, my, ox + ow - 8 - 124, by, 60, 16));
            Ui.flatButton(g, font, ox + ow - 8 - 60, by, 60, 16, Ui.tr("color.cancel"),
                    Ui.inside(mx, my, ox + ow - 8 - 60, by, 60, 16));
        }

        private void updateSv(double mx, double my) {
            sat = Mth.clamp((float) (mx - svX()) / 99f, 0f, 1f);
            val = 1f - Mth.clamp((float) (my - svY()) / 99f, 0f, 1f);
        }

        private void updateHue(double my) {
            hue = Mth.clamp((float) (my - svY()) / 99f, 0f, 0.999f);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (Ui.inside(mx, my, svX(), svY(), 100, 100)) {
                dragSv = true;
                updateSv(mx, my);
                return true;
            }
            if (Ui.inside(mx, my, hueX() - 2, svY(), 16, 100)) {
                dragHue = true;
                updateHue(my);
                return true;
            }
            for (int i = 0; i < PALETTE.length; i++) {
                int qx = ox + 8 + i * 11;
                if (Ui.inside(mx, my, qx, paletteY(), 10, 10)) {
                    float[] hsv = Ui.rgbToHsv(PALETTE[i]);
                    hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                    return true;
                }
            }
            int by = buttonsY();
            if (Ui.inside(mx, my, ox + ow - 8 - 124, by, 60, 16)) {
                apply.accept(current());
                closeOverlay();
                return true;
            }
            if (Ui.inside(mx, my, ox + ow - 8 - 60, by, 60, 16)) {
                closeOverlay();
            }
            return true;
        }

        @Override
        boolean mouseDragged(double mx, double my, int button) {
            if (dragSv) {
                updateSv(mx, my);
            } else if (dragHue) {
                updateHue(my);
            }
            return true;
        }

        @Override
        boolean mouseReleased(double mx, double my, int button) {
            dragSv = false;
            dragHue = false;
            return true;
        }

        @Override
        boolean keyPressed(int key) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                apply.accept(current());
                closeOverlay();
                return true;
            }
            return super.keyPressed(key);
        }
    }

    /** Cuadricula con todas las texturas predefinidas, tenidas con el color de la pieza. */
    private final class MaterialOverlay extends Overlay {
        private final CreaturePart part;

        MaterialOverlay(CreaturePart part) {
            this.part = part;
        }

        @Override
        void layout() {
            int rows = (Materials.PRESETS.size() + MAT_COLS - 1) / MAT_COLS;
            ow = MAT_COLS * MAT_CELL + 14;
            oh = rows * MAT_CELL + 60;
            ox = (width - ow) / 2;
            oy = Math.max(4, (height - oh) / 2);
        }

        private int cancelX() {
            return ox + ow / 2 - 40;
        }

        private int cancelY() {
            return oy + oh - 20;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Ui.popup(g, ox, oy, ow, oh);
            g.drawCenteredString(font, Ui.tr("material.title"), ox + ow / 2, oy + 6, Ui.TEXT);
            String current = CreatureDefinition.cleanMaterial(part.material);
            float r = ((part.color >> 16) & 255) / 255f;
            float gg = ((part.color >> 8) & 255) / 255f;
            float b = (part.color & 255) / 255f;
            Component hovered = null;
            for (int i = 0; i < Materials.PRESETS.size(); i++) {
                Materials.Preset preset = Materials.PRESETS.get(i);
                int cx = ox + 7 + (i % MAT_COLS) * MAT_CELL;
                int cy = oy + 18 + (i / MAT_COLS) * MAT_CELL;
                boolean hover = Ui.inside(mx, my, cx, cy, MAT_CELL - 2, MAT_CELL - 2);
                g.fill(cx, cy, cx + MAT_CELL - 2, cy + MAT_CELL - 2, hover ? 0xFF5A6478 : 0xFF1E2128);
                g.blit(cx + 1, cy + 1, 0, 16, 16, Materials.sprite(preset.id()), r, gg, b, 1f);
                if (preset.id().equals(current)) {
                    g.renderOutline(cx - 1, cy - 1, MAT_CELL, MAT_CELL, Ui.ACCENT);
                }
                if (hover) {
                    hovered = Component.translatable("material.creaturestudio." + preset.key());
                }
            }
            int rows = (Materials.PRESETS.size() + MAT_COLS - 1) / MAT_COLS;
            Ui.wrapped(g, font, Ui.tr("material.hint"), ox + 7, oy + 20 + rows * MAT_CELL, ow - 14, Ui.MUTED);
            Ui.flatButton(g, font, cancelX(), cancelY(), 80, 16, Ui.tr("templates.cancel"),
                    Ui.inside(mx, my, cancelX(), cancelY(), 80, 16));
            if (hovered != null) {
                g.renderTooltip(font, hovered, mx, my);
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            for (int i = 0; i < Materials.PRESETS.size(); i++) {
                int cx = ox + 7 + (i % MAT_COLS) * MAT_CELL;
                int cy = oy + 18 + (i / MAT_COLS) * MAT_CELL;
                if (Ui.inside(mx, my, cx, cy, MAT_CELL - 2, MAT_CELL - 2)) {
                    part.material = Materials.PRESETS.get(i).id();
                    markChanged();
                    requestRebuild();
                    closeOverlay();
                    return true;
                }
            }
            if (Ui.inside(mx, my, cancelX(), cancelY(), 80, 16)) {
                closeOverlay();
            }
            return true;
        }
    }
}
