package com.creaturestudio.client.screen;

import com.creaturestudio.CreatureStudio;
import com.creaturestudio.client.ClientFiles;
import com.creaturestudio.client.EditorSettings;
import com.creaturestudio.client.VanillaModels;
import com.creaturestudio.client.render.AnimState;
import com.creaturestudio.client.render.CreatureModelRenderer;
import com.creaturestudio.client.render.PaintAtlas;
import com.creaturestudio.client.render.Materials;
import com.creaturestudio.data.Abilities;
import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.AnimPlayer;
import com.creaturestudio.data.CustomAnims;
import com.creaturestudio.data.Behavior;
import com.creaturestudio.data.CreatureDrop;
import com.creaturestudio.data.DoorMode;
import com.creaturestudio.data.ExplosionSize;
import com.creaturestudio.data.IndirectFire;
import com.creaturestudio.data.RangedPresets;
import com.creaturestudio.data.ShotSound;
import com.creaturestudio.data.TrailBlock;
import com.creaturestudio.data.VoxelTemplate;
import com.creaturestudio.data.Voxels;
import com.creaturestudio.data.TrailType;
import com.creaturestudio.data.BodyParts;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureJson;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.CreaturePart;
import com.creaturestudio.data.DefCodec;
import com.creaturestudio.data.HitEffect;
import com.creaturestudio.data.MaterialIds;
import com.creaturestudio.data.PaintLayout;
import com.creaturestudio.data.Movement;
import com.creaturestudio.data.PartAnim;
import com.creaturestudio.data.PoseSolver;
import com.creaturestudio.data.RangedType;
import com.creaturestudio.data.SkinLayout;
import com.creaturestudio.data.SoundPreset;
import com.creaturestudio.data.SpawnPlace;
import com.creaturestudio.data.SpawnStructure;
import com.creaturestudio.data.SpawnTime;
import com.creaturestudio.data.Templates;
import com.creaturestudio.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Editor de criaturas. La vista 3D ocupa casi toda la pantalla (como en un programa de CAD) y a la derecha
 * hay un panel con una fila de pestanas con iconos: Plantillas, Cuerpo, Piezas, Forma, Pintar, Animar,
 * Atributos, Conducta y Aparicion. El panel se puede plegar (tecla H) y su ancho se cambia arrastrando el
 * borde. En el modo facil solo se ven Cuerpo, Plantillas y Pintar. No hay ventanas emergentes: el color y las
 * texturas se eligen dentro de su pestana y las listas largas se abren como desplegables.
 */
public class EditorScreen extends Screen {

    /** Pestanas del editor (el orden en pantalla esta en EASY_TABS y FULL_TABS). */
    private enum Tab {
        EASY("easy"),
        TEMPLATE("template"),
        PARTS("parts"),
        SHAPE("shape"),
        PAINT("paint"),
        ANIMATE("animate"),
        STATS("stats"),
        INVENTORY("inventory"),
        BEHAVIOR("behavior"),
        ABILITIES("abilities"),
        SPAWN("spawn");

        private final String key;
        @Nullable
        private ItemStack icon;

        Tab(String key) {
            this.key = key;
        }

        /** Pestanas que trabajan sobre la pieza seleccionada. */
        boolean usesPart() {
            return this == EASY || this == PARTS || this == SHAPE || this == PAINT || this == ANIMATE;
        }

        MutableComponent title() {
            return Ui.tr("tab." + key);
        }

        MutableComponent description() {
            return Ui.tr("tab." + key + ".desc");
        }

        ItemStack icon() {
            if (icon == null) {
                icon = new ItemStack(switch (this) {
                    case EASY -> Items.ARMOR_STAND;
                    case TEMPLATE -> Items.BOOK;
                    case PARTS -> Items.CRAFTING_TABLE;
                    case SHAPE -> Items.SCAFFOLDING;
                    case PAINT -> Items.BRUSH;
                    case ANIMATE -> Items.CLOCK;
                    case STATS -> Items.GOLDEN_APPLE;
                    case INVENTORY -> Items.CHEST;
                    case BEHAVIOR -> Items.IRON_SWORD;
                    case ABILITIES -> Items.NETHER_STAR;
                    case SPAWN -> Items.GRASS_BLOCK;
                });
            }
            return icon;
        }
    }

    /** Herramientas del raton en la vista 3D (boton "Raton"). */
    private enum Tool {
        MOVE("move"),
        RESIZE("resize"),
        BRUSH("brush"),
        ERASER("eraser"),
        PICKER("picker"),
        /** Animaciones propias: arrastrar una pieza la gira en la pose elegida. */
        POSE("pose");

        private final String key;

        Tool(String key) {
            this.key = key;
        }

        boolean paints() {
            return this == BRUSH || this == ERASER || this == PICKER;
        }

        MutableComponent title() {
            return Ui.tr("tool." + key);
        }

        MutableComponent help() {
            return Ui.tr("tool." + key + ".help");
        }
    }

    private static final Tab[] TABS = Tab.values();
    /** Pestanas que se ven en el modo facil y en el completo, en orden (teclas 1, 2, 3...). */
    private static final Tab[] EASY_TABS = {Tab.EASY, Tab.TEMPLATE, Tab.PAINT};
    private static final Tab[] FULL_TABS = {Tab.TEMPLATE, Tab.EASY, Tab.PARTS, Tab.SHAPE, Tab.PAINT, Tab.ANIMATE,
            Tab.STATS, Tab.INVENTORY, Tab.BEHAVIOR, Tab.ABILITIES, Tab.SPAWN};
    /** Opciones Si/No para los grupos de botones. */
    private static final Boolean[] NO_YES = {Boolean.FALSE, Boolean.TRUE};
    private static final Boolean[] YES_NO = {Boolean.TRUE, Boolean.FALSE};
    private static final Tool[] SHAPE_TOOLS = {Tool.MOVE, Tool.RESIZE};
    private static final Tool[] PAINT_TOOLS = {Tool.BRUSH, Tool.ERASER, Tool.PICKER};
    private static final Integer[] BRUSH_SIZES = {1, 2, 3, 4, 6, 8};
    private static final int[] AXIS_COLORS = {0xFFFF5A5A, 0xFF5AE05A, 0xFF5A9CFF};
    /** Eje (0=X, 1=Y, 2=Z) y sentido de cada cara de {@link PaintLayout}. */
    private static final int[] FACE_AXIS = {2, 2, 0, 0, 1, 1};
    private static final int[] FACE_SIGN = {-1, 1, 1, -1, 1, -1};
    private static final int HEADER_H = 20;
    /** Fila de iconos de las pestanas, encima del panel. */
    private static final int TAB_H = 20;
    /** Titulo de la pestana, debajo de los iconos. */
    private static final int TITLE_H = 14;
    /** Panel plegado: ancho de la columna de iconos y alto de cada icono. */
    private static final int STRIP_W = 22;
    private static final int STRIP_CELL = 20;
    /** Boton de plegar el panel, a la izquierda de la fila de pestanas. */
    private static final int FOLD_W = 12;
    private static final int MIN_PANEL = 190;
    private static final int PAD = 6;
    private static final int SCROLLBAR = 4;
    private static final int ROW = 18;
    private static final int FIELD_H = 16;
    private static final int CHIP_H = 14;
    private static final int LIST_ROW = 12;
    private static final int GAP = 3;
    private static final int CELL = 18;
    private static final int CARD_H = 24;
    /** Alto de los botones de las partes del cuerpo (modo facil). */
    private static final int BODY_CELL_H = 20;
    /** Texturas que se ensenan en el modo facil (las primeras de la lista: las de criatura). */
    private static final int EASY_TEXTURES = 16;
    private static final int HINT = 0xFF7D8491;
    private static final int WARN = 0xFFE0B050;
    private static final int UNSAVED = 0xFFFFA040;
    private static final int[] PALETTE = {
            0xFFFFFF, 0xD9D9D9, 0x7F7F7F, 0x2B2B2B, 0xE53935, 0xFB8C00, 0xFDD835, 0x7CB342,
            0x2E7D32, 0x26A69A, 0x29B6F6, 0x1E5BD8, 0x7E57C2, 0xD81B60, 0x8D6E63, 0xF2C79B
    };
    private static final ResourceLocation EGG_BASE = ResourceLocation.withDefaultNamespace("item/spawn_egg");
    private static final ResourceLocation EGG_SPOTS = ResourceLocation.withDefaultNamespace("item/spawn_egg_overlay");

    private final Screen parent;
    private CreatureDefinition def;
    private boolean isNew;
    private boolean dirty;
    private boolean nameEdited;
    private int selected;
    private Tab tab;
    private final int[] tabScroll = new int[TABS.length];
    private int partsScroll;
    private int eggSlot;
    private boolean animate = true;
    private boolean walk;
    private long attackStart = -100000L;
    /** Pestana Animar: true = "Mis animaciones" (poses) en vez de las automaticas. */
    private boolean customAnimMode;
    private AnimAction animAction = AnimAction.WALK;
    private int animPose;
    /** Mis animaciones: ver la animacion moviendose (si no, se ve la pose elegida quieta). */
    private boolean animPlay;
    private long animPlayStart;
    /** Herramienta Posar: arrastrando una pieza (gira alrededor de su pivote, en el plano de la pantalla). */
    private boolean posing;
    private boolean poseChanged;
    private double poseCx;
    private double poseCy;
    private double poseLastAngle;
    private double poseTurned;
    private float poseSign = 1f;
    @Nullable
    private float[] poseStart;
    @Nullable
    private float[] poseParentRot;
    @Nullable
    private float[] poseAxis;

    private final Preview3D preview = new Preview3D();
    private final Preview3D templatePreview = new Preview3D();
    private boolean viewFitted;
    private int hoveredTemplate = -1;
    private int fittedTemplate = -1;
    @Nullable
    private CreatureDefinition[] templateModels;
    private boolean needsRebuild;
    private boolean refreshing;

    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private String committed;
    private long lastChange;

    private boolean dragging;
    private int dragButton;
    private double pressX;
    private double pressY;
    private boolean moved;
    private boolean draggingScrollbar;
    private double scrollbarGrab;

    @Nullable
    private Component status;
    private int statusColor = Ui.TEXT;
    private long statusUntil;

    // Disposicion (se recalcula en init)
    private int panelX;
    private int panelW;
    private int contentW;
    private int viewTop;
    private int viewBottom;
    private int contentHeight;
    private int nameLabelX;
    private int unsavedX;
    private int headerRight;
    @Nullable
    private Button saveButton;
    @Nullable
    private Button undoButton;
    @Nullable
    private Button redoButton;
    private final List<NumberField> numberFields = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<PanelWidget> panelWidgets = new ArrayList<>();
    private final List<Element> elements = new ArrayList<>();
    @Nullable
    private Element activeElement;
    @Nullable
    private TemplateGrid templateGrid;
    @Nullable
    private PartsList partsList;
    @Nullable
    private Dropdown dropdown;

    // Modo raton
    private boolean mouseMode;
    private Tool tool = Tool.MOVE;
    private int brushSize = 1;
    private int brushColor = 0xFFE53935;
    private final List<ViewChip> viewChips = new ArrayList<>();
    /** Asa que se esta arrastrando: 0-2 flecha X/Y/Z, 3 = la pieza entera, 10-15 = cara (10 + cara). */
    private int gizmoHandle = -1;
    private double gizmoX0;
    private double gizmoY0;
    private float[] gizmoA = new float[3];
    private float[] gizmoB = new float[3];
    private int gizmoAxisA;
    private int gizmoAxisB;
    private final float[] gizmoApplied = new float[3];
    @Nullable
    private CreaturePart gizmoStart;
    private boolean painting;
    private boolean strokeChanged;
    /** Pestana Pintar: true = se dibuja con minibloques en vez de pintar pixeles. */
    private boolean voxelMode;
    /** Minibloques: poner tambien en el otro lado (espejo). */
    private boolean voxelMirror = true;
    /** Minibloques: el pincel cambia el color de los que ya hay en vez de poner nuevos. */
    private boolean voxelRecolor;
    /** Tamano de los minibloques que se ponen (ver Voxels.SIZE_KEYS). */
    private int voxelSize = Voxels.DEFAULT_SIZE;
    /** Minibloques puestos en el trazo actual (no cuentan al apuntar, asi se pinta una capa). */
    private final java.util.Set<Long> strokeVoxels = new java.util.HashSet<>();
    private double paintLastX;
    private double paintLastY;

    // Pintura en la vista 3D
    private final PaintAtlas editorPaint = new PaintAtlas(
            ResourceLocation.fromNamespaceAndPath(CreatureStudio.MODID, "paint/editor"));
    private final PaintAtlas previewPaint = new PaintAtlas(
            ResourceLocation.fromNamespaceAndPath(CreatureStudio.MODID, "paint/preview"));
    private int paintVersion = 1;
    private int paintBuilt;
    private boolean paintShown;
    @Nullable
    private CreatureDefinition previewPaintDef;
    private boolean previewPaintShown;

    // Panel y modo facil
    private boolean easyMode;
    private boolean panelCollapsed;
    private boolean resizingPanel;
    private int resizeX;
    /** Color de las partes del cuerpo nuevas (modo facil). */
    private int easyColor = 0x8BC34A;
    /** Modo facil: los cambios se aplican tambien a la pieza del otro lado. */
    private boolean symmetry = true;
    /** Ayuda de la parte de abajo de la vista (una linea; si no cabe, entera en el tooltip). */
    @Nullable
    private Component helpText;
    private boolean helpCut;
    private int helpX;
    private int helpY;
    private int helpW;

    // Modelos de Minecraft
    @Nullable
    private VanillaList vanillaList;
    private String vanillaQuery = "";
    private int vanillaScroll;
    @Nullable
    private VanillaModels.Entry hoveredVanilla;
    @Nullable
    private CreatureDefinition hoveredVanillaDef;
    @Nullable
    private String fittedVanilla;

    /** Widget de Minecraft colocado dentro del panel con desplazamiento (y en coordenadas del contenido). */
    private record PanelWidget(AbstractWidget widget, int contentY) {
    }

    /** Boton dibujado en la barra de la vista 3D (action = null: solo texto; swatch: muestra el color del pincel). */
    private record ViewChip(int x, int y, int w, Component label, BooleanSupplier chosen, @Nullable Runnable action,
                            @Nullable Component tip, boolean swatch) {
    }

    /** Fila de un desplegable. color = -1 si no lleva muestra de color. */
    private record DropItem(Component label, int color, int indent, boolean selected) {
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
        EditorSettings.load();
        this.easyMode = EditorSettings.easyMode;
        this.panelCollapsed = EditorSettings.panelCollapsed;
        this.tab = showTemplates ? Tab.TEMPLATE : easyMode ? Tab.EASY : Tab.PARTS;
    }

    // =======================================================================================
    // Construccion de la interfaz
    // =======================================================================================

    @Override
    protected void init() {
        numberFields.clear();
        refreshers.clear();
        panelWidgets.clear();
        elements.clear();
        activeElement = null;
        templateGrid = null;
        partsList = null;
        vanillaList = null;
        dropdown = null;
        draggingScrollbar = false;
        resizingPanel = false;
        saveButton = null;
        undoButton = null;
        redoButton = null;

        Tab[] visible = visibleTabs();
        if (indexOf(visible, tab) < 0) {
            tab = visible[0];
        }
        if (panelCollapsed) {
            panelW = STRIP_W;
        } else {
            int auto = Mth.clamp(width * 3 / 10, MIN_PANEL, 240);
            panelW = Mth.clamp(EditorSettings.panelWidth > 0 ? EditorSettings.panelWidth : auto, MIN_PANEL, maxPanel());
        }
        panelX = width - panelW;
        buildIndent = 0;
        contentW = panelW - PAD * 2 - SCROLLBAR;
        viewTop = HEADER_H + TAB_H + TITLE_H;
        viewBottom = height;
        preview.setArea(0, HEADER_H, panelX, height - HEADER_H);
        templatePreview.setArea(0, HEADER_H, panelX, height - HEADER_H);
        fittedTemplate = -1;
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

        buildHeader();
        buildViewBar();
        if (panelCollapsed) {
            contentHeight = 0;
            return;
        }
        int y = 4;
        int bottom = switch (tab) {
            case EASY -> buildEasyTab(y);
            case TEMPLATE -> buildTemplateTab(y);
            case PARTS -> buildPartsTab(y);
            case SHAPE -> buildShapeTab(y);
            case PAINT -> buildPaintTab(y);
            case ANIMATE -> buildAnimateTab(y);
            case STATS -> buildStatsTab(y);
            case INVENTORY -> buildInventoryTab(y);
            case BEHAVIOR -> buildBehaviorTab(y);
            case ABILITIES -> buildAbilitiesTab(y);
            case SPAWN -> buildSpawnTab(y);
        };
        contentHeight = bottom + PAD;
        setScroll(scroll());
    }

    private void requestRebuild() {
        needsRebuild = true;
    }

    private void buildHeader() {
        Component exitText = Ui.tr("editor.exit");
        Component saveText = Ui.tr("editor.save");
        int bh = HEADER_H - 4;
        int wExit = Math.max(40, font.width(exitText) + 12);
        int wSave = Math.max(50, font.width(saveText) + 16);
        int x = width - 3 - wExit;
        addRenderableWidget(Button.builder(exitText, b -> onClose()).bounds(x, 2, wExit, bh).build());
        x -= wSave + 3;
        saveButton = addRenderableWidget(Button.builder(saveText, b -> save()).bounds(x, 2, wSave, bh)
                .tooltip(Tooltip.create(Ui.tr("editor.save_tooltip"))).build());
        x -= 20 + 8;
        redoButton = addRenderableWidget(Button.builder(Component.literal("\u21B7"), b -> redo())
                .bounds(x, 2, 20, bh).tooltip(Tooltip.create(Ui.tr("editor.redo"))).build());
        x -= 20 + 2;
        undoButton = addRenderableWidget(Button.builder(Component.literal("\u21B6"), b -> undo())
                .bounds(x, 2, 20, bh).tooltip(Tooltip.create(Ui.tr("editor.undo"))).build());

        nameLabelX = 6;
        int nx = nameLabelX + font.width(Ui.tr("editor.name_label")) + 4;
        // Boton del modo facil (con un texto mas corto si no cabe)
        Component easyText = Ui.onOff("editor.easy", easyMode);
        if (x - 8 - (font.width(easyText) + 12) - nx < 90) {
            easyText = Ui.onOff("editor.easy_short", easyMode);
        }
        int wEasy = font.width(easyText) + 12;
        x -= wEasy + 8;
        addRenderableWidget(Button.builder(easyText, b -> setEasyMode(!easyMode)).bounds(x, 2, wEasy, bh)
                .tooltip(Tooltip.create(Ui.tr("editor.easy_tooltip"))).build());
        headerRight = x;

        int nameW = Mth.clamp(headerRight - 12 - nx, 50, 150);
        EditBox nameBox = new EditBox(font, nx, 2, nameW, bh, Ui.tr("editor.name"));
        nameBox.setMaxLength(CreatureDefinition.NAME_MAX);
        nameBox.setValue(def.name);
        nameBox.setHint(Ui.tr("editor.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(s -> {
            def.name = s;
            nameEdited = true;
            markChanged();
        });
        addRenderableWidget(nameBox);
        unsavedX = nx + nameW + 8;
    }

    /** Barra de la vista 3D: opciones de la vista y, con el raton activado, sus herramientas. */
    private void buildViewBar() {
        viewChips.clear();
        int left = preview.x + 4;
        int maxX = preview.x + preview.w - 4;
        int[] pos = {left, preview.y + 4};
        viewChip(pos, left, maxX, Ui.onOff("editor.preview.animate", animate), () -> animate,
                () -> setAnimate(!animate), Ui.tr("editor.preview.animate_tip"), false);
        viewChip(pos, left, maxX, Ui.onOff("editor.preview.walk", walk), () -> walk,
                () -> setWalk(!walk), Ui.tr("editor.preview.walk_tip"), false);
        viewChip(pos, left, maxX, Ui.tr("editor.preview.attack"), () -> false,
                () -> attackStart = Util.getMillis(), Ui.tr("editor.preview.attack_tip"), false);
        viewChip(pos, left, maxX, Ui.tr("editor.preview.reset"), () -> false,
                () -> preview.fit(def), Ui.tr("editor.preview.reset_tip"), false);
        viewChip(pos, left, maxX, Ui.onOff("editor.preview.mouse", mouseMode), () -> mouseMode,
                () -> setMouseMode(!mouseMode), Ui.tr("editor.preview.mouse_tooltip"), false);
        if (!mouseMode) {
            return;
        }
        pos[0] = left;
        pos[1] += 15;
        for (Tool t : Tool.values()) {
            viewChip(pos, left, maxX, t.title(), () -> tool == t, () -> setTool(t), t.help(), false);
        }
        if (tool == Tool.BRUSH || tool == Tool.ERASER) {
            pos[0] += 6;
            viewChip(pos, left, maxX, Component.literal("-"), () -> false, () -> changeBrush(-1), Ui.tr("tool.size_down"), false);
            viewChip(pos, left, maxX, Ui.tr("tool.size", brushSize), () -> false, null, null, false);
            viewChip(pos, left, maxX, Component.literal("+"), () -> false, () -> changeBrush(1), Ui.tr("tool.size_up"), false);
            if (tool == Tool.BRUSH) {
                viewChip(pos, left, maxX, Component.literal("    "), () -> false, () -> {
                            setPanelCollapsed(false);
                            switchTab(Tab.PAINT);
                        },
                        Ui.tr("tool.color_tip"), true);
            }
        }
    }

    private void viewChip(int[] pos, int left, int maxX, Component label, BooleanSupplier chosen,
                          @Nullable Runnable action, @Nullable Component tip, boolean swatch) {
        int w = font.width(label) + 10;
        if (pos[0] + w > maxX && pos[0] > left) {
            pos[0] = left;
            pos[1] += 15;
        }
        viewChips.add(new ViewChip(pos[0], pos[1], w, label, chosen, action, tip, swatch));
        pos[0] += w + 2;
    }

    private void setMouseMode(boolean on) {
        endMouseAction();
        mouseMode = on;
        requestRebuild();
    }

    private void setTool(Tool t) {
        endMouseAction();
        if (t == Tool.POSE) {
            customAnimMode = true;
            if (tab != Tab.ANIMATE) {
                setPanelCollapsed(false);
                switchTab(Tab.ANIMATE);
            }
        }
        tool = t;
        mouseMode = true;
        requestRebuild();
    }

    /** Pestana Animar: automaticas o mis animaciones (con el raton, la herramienta va con el modo). */
    private void setCustomAnimMode(boolean on) {
        endMouseAction();
        customAnimMode = on;
        animPlay = false;
        if (mouseMode) {
            if (on && tool != Tool.POSE) {
                tool = Tool.POSE;
            } else if (!on && tool == Tool.POSE) {
                tool = Tool.MOVE;
            }
        }
        requestRebuild();
    }

    private void changeBrush(int direction) {
        int i = 0;
        for (int k = 0; k < BRUSH_SIZES.length; k++) {
            if (BRUSH_SIZES[k] == brushSize) {
                i = k;
            }
        }
        brushSize = BRUSH_SIZES[Mth.clamp(i + direction, 0, BRUSH_SIZES.length - 1)];
        buildViewBar();
    }

    private void setAnimate(boolean value) {
        animate = value;
        buildViewBar();
    }

    private void setWalk(boolean value) {
        walk = value;
        buildViewBar();
    }

    // ---------------------------------------------------------------------------------------
    // Pestanas visibles, panel plegable y modo facil
    // ---------------------------------------------------------------------------------------

    private Tab[] visibleTabs() {
        return easyMode ? EASY_TABS : FULL_TABS;
    }

    private static int indexOf(Tab[] tabs, Tab t) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == t) {
                return i;
            }
        }
        return -1;
    }

    private int maxPanel() {
        return Math.max(MIN_PANEL, Math.min(340, width - 200));
    }

    private void setPanelCollapsed(boolean collapsed) {
        if (panelCollapsed == collapsed) {
            return;
        }
        panelCollapsed = collapsed;
        EditorSettings.panelCollapsed = collapsed;
        EditorSettings.save();
        dropdown = null;
        activeElement = null;
        setFocused(null);
        requestRebuild();
    }

    private void setEasyMode(boolean on) {
        easyMode = on;
        EditorSettings.easyMode = on;
        if (on && tab != Tab.TEMPLATE) {
            tab = Tab.EASY;
        }
        if (panelCollapsed) {
            panelCollapsed = false;
            EditorSettings.panelCollapsed = false;
        }
        EditorSettings.save();
        dropdown = null;
        activeElement = null;
        setFocused(null);
        requestRebuild();
        showNotice(Ui.tr(on ? "editor.easy_on" : "editor.easy_off"), true);
    }

    /** El raton esta sobre el borde del panel (se arrastra para cambiar su ancho). */
    private boolean dividerHit(double mx, double my) {
        return !panelCollapsed && my >= HEADER_H && mx >= panelX - 2 && mx <= panelX + 2;
    }

    // ---------------------------------------------------------------------------------------
    // Contenido de cada pestana (coordenadas del contenido: x desde el borde izquierdo del panel,
    // y desde arriba del contenido sin desplazar)
    // ---------------------------------------------------------------------------------------

    private int buildEasyTab(int y) {
        y = intro(y);
        y = section(y, Ui.tr("easy.add"));
        BodyGrid grid = add(new BodyGrid(0, y, contentW));
        y += grid.h + 6;
        y = smallLabel(y, Ui.tr("easy.new_color"));
        Palette newColor = add(new Palette(0, y, contentW, () -> easyColor, c -> easyColor = c & 0xFFFFFF));
        y += newColor.h + 8;

        CreaturePart p = sel();
        if (p == null) {
            return text(y, Ui.tr("easy.empty"), HINT);
        }
        y = section(y, Ui.tr("easy.selected"));
        y = pieceSelector(y);
        add(new Toggle(0, y, contentW, Ui.tr("easy.symmetry"), () -> symmetry, v -> symmetry = v,
                Ui.tr("easy.symmetry_tip"), false));
        y += CHIP_H + 4;

        int half = (contentW - GAP) / 2;
        int third = (contentW - GAP * 2) / 3;
        int lastX = (third + GAP) * 2;
        int lastW = contentW - lastX;
        y = smallLabel(y, Ui.tr("easy.size"));
        button(0, y, half, Ui.tr("easy.bigger"), b -> easyScale(1.2f, 1.2f, 1.2f), Ui.tr("easy.bigger_tip"));
        button(half + GAP, y, contentW - half - GAP, Ui.tr("easy.smaller"), b -> easyScale(1f / 1.2f, 1f / 1.2f, 1f / 1.2f),
                Ui.tr("easy.smaller_tip"));
        y += ROW;
        button(0, y, third, Ui.tr("easy.wider"), b -> easyScale(1.25f, 1f, 1f), Ui.tr("easy.wider_tip"));
        button(third + GAP, y, third, Ui.tr("easy.taller"), b -> easyScale(1f, 1.25f, 1f), Ui.tr("easy.taller_tip"));
        button(lastX, y, lastW, Ui.tr("easy.longer"), b -> easyScale(1f, 1f, 1.25f), Ui.tr("easy.longer_tip"));
        y += ROW;
        button(0, y, third, Ui.tr("easy.narrower"), b -> easyScale(0.8f, 1f, 1f), Ui.tr("easy.narrower_tip"));
        button(third + GAP, y, third, Ui.tr("easy.lower"), b -> easyScale(1f, 0.8f, 1f), Ui.tr("easy.lower_tip"));
        button(lastX, y, lastW, Ui.tr("easy.shorter"), b -> easyScale(1f, 1f, 0.8f), Ui.tr("easy.shorter_tip"));
        y += ROW + 4;

        boolean paired = BodyParts.partner(def, selected) >= 0;
        float outward = p.x + p.w / 2f >= 0f ? 1f : -1f;
        y = smallLabel(y, Ui.tr("easy.move"));
        button(0, y, third, Ui.tr("easy.up"), b -> easyMove(0f, 1f, 0f), Ui.tr("easy.step_tip"));
        button(third + GAP, y, third, Ui.tr("easy.down"), b -> easyMove(0f, -1f, 0f), Ui.tr("easy.step_tip"));
        button(lastX, y, lastW, Ui.tr("easy.forward"), b -> easyMove(0f, 0f, -1f), Ui.tr("easy.forward_tip"));
        y += ROW;
        if (paired) {
            button(0, y, third, Ui.tr("easy.apart"), b -> easyMove(outward, 0f, 0f), Ui.tr("easy.apart_tip"));
            button(third + GAP, y, third, Ui.tr("easy.together"), b -> easyMove(-outward, 0f, 0f), Ui.tr("easy.together_tip"));
        } else {
            button(0, y, third, Ui.tr("easy.left"), b -> easyMove(1f, 0f, 0f), Ui.tr("easy.left_tip"));
            button(third + GAP, y, third, Ui.tr("easy.right"), b -> easyMove(-1f, 0f, 0f), Ui.tr("easy.right_tip"));
        }
        button(lastX, y, lastW, Ui.tr("easy.back"), b -> easyMove(0f, 0f, 1f), Ui.tr("easy.back_tip"));
        y += ROW + 4;

        y = smallLabel(y, Ui.tr("easy.color"));
        Palette colors = add(new Palette(0, y, contentW, () -> p.color, this::easyColor));
        y += colors.h + 6;
        if (p.hasPaint()) {
            // Las piezas de los modelos de Minecraft (o con skin) llevan la textura pintada encima del color.
            y = text(y, Ui.tr("easy.painted"), WARN) + 3;
            button(0, y, contentW, Ui.tr("easy.clear_paint"), b -> easyClearPaint(), Ui.tr("easy.clear_paint_tip"));
            y += ROW + 4;
        }
        y = smallLabel(y, Ui.tr("easy.texture"));
        // en el modo facil solo las texturas de criatura (piel, escamas, plumas, ojos...)
        TextureGrid textures = add(new TextureGrid(0, y, contentW, p, this::easyMaterial, EASY_TEXTURES));
        y += textures.h + 6;

        boolean offCenter = Math.abs(p.x + p.w / 2f) > 0.5f;
        button(0, y, half, Ui.tr("easy.mirror"), b -> duplicateSelected(true), Ui.tr("easy.mirror_tip")).active =
                offCenter && !paired;
        button(half + GAP, y, contentW - half - GAP, Ui.tr("easy.delete"), b -> easyDelete(), Ui.tr("easy.delete_tip"));
        y += ROW + 6;
        return text(y, Ui.tr("easy.hint"), HINT);
    }

    private int buildTemplateTab(int y) {
        y = intro(y);
        y = text(y, Ui.tr("templates.warning"), WARN) + 6;
        templateGrid = add(new TemplateGrid(y));
        y += templateGrid.h + 6;
        y = text(y, Ui.tr("templates.hint"), HINT) + 10;

        y = section(y, Ui.tr("vanilla.title", VanillaModels.entries().size()));
        y = text(y, Ui.tr("vanilla.hint"), HINT) + 4;
        textBox(0, y, contentW, 64, vanillaQuery, s -> {
            vanillaQuery = s;
            vanillaScroll = 0;
        }, Ui.tr("vanilla.search"));
        y += ROW + 2;
        int listH = LIST_ROW * 12 + 4;
        vanillaList = add(new VanillaList(y, listH));
        return y + listH;
    }

    private int buildPartsTab(int y) {
        y = intro(y);
        CreaturePart p = sel();
        boolean has = p != null;
        int bw = (contentW - GAP * 2) / 3;
        button(0, y, bw, Ui.tr("editor.part.add"), b -> addPart(), Ui.tr("editor.part.add_tooltip"));
        button(bw + GAP, y, bw, Ui.tr("editor.part.duplicate"), b -> duplicateSelected(false),
                Ui.tr("editor.part.duplicate_tooltip")).active = has;
        button((bw + GAP) * 2, y, contentW - (bw + GAP) * 2, Ui.tr("editor.part.mirror"), b -> duplicateSelected(true),
                Ui.tr("editor.part.mirror_tooltip")).active = has;
        y += ROW;
        int half = (contentW - GAP) / 2;
        button(0, y, half, Ui.tr("editor.part.delete"), b -> deleteSelected(),
                Ui.tr("editor.part.delete_tooltip")).active = has;
        button(half + GAP, y, contentW - half - GAP, Ui.tr("editor.part.center_model"), b -> centerModel(),
                Ui.tr("editor.part.center_tooltip")).active = !def.parts.isEmpty();
        y += ROW + 4;
        if (p != null) {
            int lw = labelWidth("editor.field.name", "editor.field.parent");
            label(0, y, Ui.tr("editor.field.name"));
            textBox(lw, y, contentW - lw, 24, p.name, s -> {
                p.name = s;
                markChanged();
            }, null);
            y += ROW;
            label(0, y, Ui.tr("editor.field.parent"));
            add(new DropButton(lw, y, contentW - lw, () -> parentLabel(p), () -> parentColor(p),
                    Ui.tr("editor.field.parent_tooltip"), this::openParentDropdown));
            y += ROW + 4;
        }
        y = section(y, Ui.tr("parts.list", def.parts.size(), CreatureDefinition.MAX_PARTS));
        int listH = Math.max(LIST_ROW * 5 + 4, viewBottom - viewTop - y - PAD);
        partsList = add(new PartsList(y, listH));
        return y + listH;
    }

    private int buildShapeTab(int y) {
        y = intro(y);
        CreaturePart p = sel();
        if (p == null) {
            return noParts(y);
        }
        y = pieceSelector(y);
        y = mouseHelper(y, SHAPE_TOOLS, "tool.shape_off");
        int lw = labelWidth("editor.field.position", "editor.field.size", "editor.field.pivot", "editor.field.rotation");
        y = axisHeader(y, lw);
        final int index = selected;
        y = vecRow(y, lw, "editor.field.position",
                () -> p.x, v -> moveSubtree(index, v - p.x, 0f, 0f),
                () -> p.y, v -> moveSubtree(index, 0f, v - p.y, 0f),
                () -> p.z, v -> moveSubtree(index, 0f, 0f, v - p.z),
                -512f, 512f, 1f);
        y = vecRow(y, lw, "editor.field.size",
                () -> p.w, v -> resize(p, v, p.h, p.d),
                () -> p.h, v -> resize(p, p.w, v, p.d),
                () -> p.d, v -> resize(p, p.w, p.h, v),
                0f, 256f, 1f);
        y = vecRow(y, lw, "editor.field.pivot",
                () -> p.px, v -> p.px = v,
                () -> p.py, v -> p.py = v,
                () -> p.pz, v -> p.pz = v,
                -512f, 512f, 1f);
        y = vecRow(y, lw, "editor.field.rotation",
                () -> p.rx, v -> p.rx = v,
                () -> p.ry, v -> p.ry = v,
                () -> p.rz, v -> p.rz = v,
                -360f, 360f, 5f);
        int half = (contentW - GAP) / 2;
        button(0, y, half, Ui.tr("editor.pivot.center"), b -> {
            p.centerPivot();
            markChanged();
        }, Ui.tr("editor.pivot.center_tooltip"));
        button(half + GAP, y, contentW - half - GAP, Ui.tr("editor.rotation.reset"), b -> {
            p.rx = 0f;
            p.ry = 0f;
            p.rz = 0f;
            markChanged();
        }, null);
        y += ROW + 6;
        return text(y, Ui.tr("editor.hint.fields"), HINT);
    }

    private int buildPaintTab(int y) {
        y = intro(y);
        CreaturePart p = sel();
        if (p == null) {
            return noParts(y);
        }
        y = pieceSelector(y);
        y = smallLabel(y, Ui.tr("paint.mode"));
        y = chips(y, NO_YES, 2, () -> voxelMode, v -> {
            voxelMode = v;
            endMouseAction();
            requestRebuild();
        }, v -> Ui.tr(v ? "paint.mode.voxels" : "paint.mode.pixels"),
                v -> Ui.tr(v ? "paint.mode.voxels_tip" : "paint.mode.pixels_tip")) + 6;
        if (voxelMode) {
            y = buildVoxelSection(y, p);
        } else {
            y = buildPixelSection(y, p);
        }
        return buildPartLook(y, p);
    }

    private int buildPixelSection(int y, CreaturePart p) {
        y = section(y, Ui.tr("skin.section"));
        int halfS = (contentW - GAP) / 2;
        button(0, y, halfS, Ui.tr("skin.import"), b -> openSkinPicker(), Ui.tr("skin.import_tip"));
        button(halfS + GAP, y, contentW - halfS - GAP, Ui.tr("skin.export"), b -> exportSkin(), Ui.tr("skin.export_tip"));
        y += ROW;
        button(0, y, contentW, Ui.tr("library.folder"), b -> ClientFiles.openFolder(), Ui.tr("skin.folder_tip"));
        y += ROW;
        int mapped = SkinLayout.mapped(def);
        Component skinInfo = mapped > 0
                ? Ui.tr("skin.status", def.texW, def.texH, mapped, mapped + SkinLayout.unmapped(def))
                : Ui.tr("skin.none");
        y = text(y, skinInfo, HINT) + 8;

        y = section(y, Ui.tr("paint.brush_section"));
        y = mouseHelper(y, PAINT_TOOLS, "paint.mouse_off");
        y = smallLabel(y, Ui.tr("paint.size"));
        y = chips(y, BRUSH_SIZES, BRUSH_SIZES.length, () -> brushSize, v -> {
            brushSize = v;
            buildViewBar();
        }, v -> Component.literal(String.valueOf(v)), null) + 6;
        y = smallLabel(y, Ui.tr("paint.brush_color"));
        IntSupplier brush = () -> brushColor & 0xFFFFFF;
        IntConsumer setBrush = c -> {
            brushColor = 0xFF000000 | (c & 0xFFFFFF);
            refreshFields();
        };
        add(new ColorPicker(0, y, contentW, 46, brush, setBrush));
        y += 46 + 5;
        add(new Swatch(0, y, FIELD_H, brush));
        hexBox(FIELD_H + 4, y, 58, brush, setBrush);
        y += ROW;
        Palette brushPalette = add(new Palette(0, y, contentW, brush, setBrush));
        y += brushPalette.h + 6;
        int halfW = (contentW - GAP) / 2;
        button(0, y, halfW, Ui.tr("paint.clear_part"), b -> {
            p.clearPaint();
            markChanged();
        }, Ui.tr("paint.clear_part_tip"));
        button(halfW + GAP, y, contentW - halfW - GAP, Ui.tr("paint.clear_all"), b -> clearAllPaint(),
                Ui.tr("paint.clear_all_tip"));
        y += ROW + 4;
        return text(y, Ui.tr("paint.hint"), HINT) + 10;
    }

    /** Color de la pieza y su textura (en los dos modos de la pestana Pintar). */
    private int buildPartLook(int y, CreaturePart p) {
        IntConsumer setColor = c -> {
            p.color = c & 0xFFFFFF;
            markChanged();
        };
        y = section(y, Ui.tr("paint.color"));
        int pickerH = 58;
        add(new ColorPicker(0, y, contentW, pickerH, () -> p.color, setColor));
        y += pickerH + 5;
        add(new Swatch(0, y, FIELD_H, () -> p.color));
        hexBox(FIELD_H + 4, y, 58, () -> p.color, setColor);
        y += ROW;
        Palette palette = add(new Palette(0, y, contentW, () -> p.color, setColor));
        y += palette.h + 8;

        y = section(y, () -> Ui.tr("paint.texture", Materials.displayName(p.material)));
        TextureGrid grid = add(new TextureGrid(0, y, contentW, p));
        y += grid.h + 6;
        add(new Toggle(0, y, contentW, Ui.tr("editor.field.stretch"), () -> p.stretch, v -> p.stretch = v,
                Ui.tr("paint.stretch_tip"), true));
        y += CHIP_H + 2;
        add(new Toggle(0, y, contentW, Ui.tr("editor.field.glow"), () -> p.glow, v -> p.glow = v,
                Ui.tr("paint.glow_tip"), true));
        y += CHIP_H + 6;
        y = smallLabel(y, Ui.tr("paint.custom"));
        EditBox material = textBox(0, y, contentW, 128, p.material, s -> {
            p.material = s;
            markChanged();
        }, null);
        refreshers.add(() -> {
            if (!material.isFocused() && !material.getValue().equals(p.material)) {
                material.setValue(p.material);
                material.moveCursorToStart(false);
            }
            material.setTextColor(Materials.exists(p.material) ? Ui.TEXT : Ui.ERROR);
        });
        material.setTextColor(Materials.exists(p.material) ? Ui.TEXT : Ui.ERROR);
        y += ROW + 4;
        return text(y, Ui.tr("editor.hint.look"), HINT);
    }

    // ---------------------------------------------------------------------------------------
    // Minibloques
    // ---------------------------------------------------------------------------------------

    private int buildVoxelSection(int y, CreaturePart p) {
        y = section(y, Ui.tr("voxel.section"));
        if (!mouseMode) {
            y = text(y, Ui.tr("voxel.mouse_off"), Ui.ACCENT) + 4;
            button(0, y, Math.min(contentW, 150), Ui.tr("tool.enable"), b -> setTool(Tool.BRUSH), null);
            y += ROW + 6;
        } else {
            y = chips(y, PAINT_TOOLS, PAINT_TOOLS.length, () -> tool, this::setTool, Tool::title,
                    t -> Ui.tr("voxel.tool." + t.key + ".help")) + 4;
            y = text(y, Ui.tr("voxel.tool." + tool.key + ".help"), HINT) + 6;
        }
        y = smallLabel(y, Ui.tr("voxel.brush_does"));
        y = chips(y, NO_YES, 2, () -> voxelRecolor, v -> voxelRecolor = v,
                v -> Ui.tr(v ? "voxel.recolor" : "voxel.place"), v -> Ui.tr(v ? "voxel.recolor_tip" : "voxel.place_tip")) + 4;
        y = smallLabel(y, Ui.tr("voxel.size"));
        y = chips(y, Voxels.SIZE_ORDER, Voxels.SIZE_ORDER.length, () -> voxelSize, v -> voxelSize = v,
                v -> Ui.tr("voxel.size." + Voxels.SIZE_KEYS[v]), v -> Ui.tr("voxel.size." + Voxels.SIZE_KEYS[v] + ".tip")) + 4;
        add(new Toggle(0, y, contentW, Ui.tr("voxel.mirror"), () -> voxelMirror, v -> voxelMirror = v,
                Ui.tr("voxel.mirror_tip"), false));
        y += CHIP_H + 6;
        y = smallLabel(y, Ui.tr("voxel.color"));
        IntSupplier brush = () -> brushColor & 0xFFFFFF;
        IntConsumer setBrush = c -> {
            brushColor = 0xFF000000 | (c & 0xFFFFFF);
            refreshFields();
        };
        add(new ColorPicker(0, y, contentW, 46, brush, setBrush));
        y += 46 + 5;
        add(new Swatch(0, y, FIELD_H, brush));
        hexBox(FIELD_H + 4, y, 58, brush, setBrush);
        y += ROW;
        Palette palette = add(new Palette(0, y, contentW, brush, setBrush));
        y += palette.h + 8;

        y = section(y, Ui.tr("voxel.templates"));
        y = text(y, Ui.tr("voxel.templates_hint", p.name), HINT) + 3;
        VoxelTemplate[] all = VoxelTemplate.values();
        int half = (contentW - GAP) / 2;
        for (int i = 0; i < all.length; i++) {
            VoxelTemplate t = all[i];
            boolean left = i % 2 == 0;
            button(left ? 0 : half + GAP, y, left ? half : contentW - half - GAP, Ui.tr("voxel.template." + t.key()),
                    b -> applyTemplate(t), Ui.tr("voxel.template." + t.key() + ".tip"));
            if (!left || i == all.length - 1) {
                y += ROW;
            }
        }
        y += 4;
        button(0, y, half, Ui.tr("voxel.clear_part"), b -> {
            p.voxels = null;
            markChanged();
        }, Ui.tr("voxel.clear_part_tip"));
        button(half + GAP, y, contentW - half - GAP, Ui.tr("voxel.clear_all"), b -> {
            for (CreaturePart q : def.parts) {
                q.voxels = null;
            }
            markChanged();
        }, Ui.tr("voxel.clear_all_tip"));
        y += ROW + 2;
        add(new TextLine(0, y, () -> Ui.tr("voxel.count", Voxels.count(p.voxels), Voxels.total(def), Voxels.MAX_TOTAL),
                Ui.MUTED));
        y += 13;
        return text(y, Ui.tr("voxel.hint"), HINT) + 10;
    }

    /** Pone un accesorio de minibloques en la pieza elegida (y en su pareja del otro lado si el espejo esta puesto). */
    private void applyTemplate(VoxelTemplate t) {
        CreaturePart p = sel();
        if (p == null) {
            return;
        }
        int rgb = brushColor & 0xFFFFFF;
        int[] mine = t.build(p, rgb, voxelSize);
        int partner = voxelMirror ? BodyParts.partner(def, selected) : -1;
        int[] theirs = partner >= 0 ? t.build(def.parts.get(partner), rgb, voxelSize) : null;
        int extra = Voxels.added(p.voxels, mine) + (theirs == null ? 0 : Voxels.added(def.parts.get(partner).voxels, theirs));
        if (Voxels.total(def) + extra > Voxels.MAX_TOTAL) {
            warn(Ui.tr("voxel.too_many", Voxels.MAX_TOTAL));
            return;
        }
        p.voxels = Voxels.merge(p.voxels, mine);
        if (theirs != null) {
            CreaturePart q = def.parts.get(partner);
            q.voxels = Voxels.merge(q.voxels, theirs);
        }
        markChanged();
        showNotice(Ui.tr("voxel.template_done", Ui.tr("voxel.template." + t.key()), p.name), true);
    }

    /** Hueco de minibloque: pieza a la que se pega, posicion empaquetada y tamano. */
    private record VoxelTarget(int part, int pos, int size) {
    }

    /** Lo que hay bajo el raton para la herramienta: el hueco donde poner o el minibloque que borrar/pintar. */
    @Nullable
    private VoxelTarget voxelTarget(double mx, double my, Matrix4f[] mats) {
        boolean existing = tool == Tool.ERASER || voxelRecolor;
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my, existing ? null : strokeVoxels, existing);
        if (hit == null || hit.part() >= def.parts.size()) {
            return null;
        }
        CreaturePart p = def.parts.get(hit.part());
        int index = hit.isVoxel() ? Voxels.indexOf(p.voxels, hit.voxel()) : -1;
        if (existing) {
            return index >= 0 ? new VoxelTarget(hit.part(), hit.voxel(), Voxels.sizeAt(p.voxels, index)) : null;
        }
        if (hit.face() < 0 || hit.isVoxel() && index < 0) {
            return null;
        }
        int pos = Voxels.beside(p.w, p.h, p.d, hit.face(), hit.x() - p.x, hit.y() - p.y, hit.z() - p.z,
                index >= 0 ? hit.voxel() : -1, index >= 0 ? Voxels.sizeAt(p.voxels, index) : 0, voxelSize);
        return pos < 0 ? null : new VoxelTarget(hit.part(), pos, voxelSize);
    }

    /** El mismo hueco al otro lado (en la misma pieza si esta centrada, o en su pareja). null si no tiene. */
    @Nullable
    private VoxelTarget mirrorTarget(VoxelTarget t) {
        CreaturePart p = def.parts.get(t.part());
        int qi;
        if (Math.abs(p.x + p.w / 2f) < 0.05f) {
            qi = t.part();
        } else {
            qi = BodyParts.partner(def, t.part());
            if (qi < 0) {
                return null;
            }
        }
        CreaturePart q = def.parts.get(qi);
        float fx = (-(p.x + Voxels.px(t.pos()) + Voxels.pixels(t.size())) - q.x) * Voxels.UNIT;
        int x = Math.round(fx);
        if (Math.abs(fx - x) > 0.2f) {
            return null; // no cae en la rejilla del otro lado
        }
        int y = Math.round((p.y + Voxels.py(t.pos()) - q.y) * Voxels.UNIT);
        int z = Math.round((p.z + Voxels.pz(t.pos()) - q.z) * Voxels.UNIT);
        return Voxels.inRange(x, y, z) ? new VoxelTarget(qi, Voxels.pack(x, y, z), t.size()) : null;
    }

    private void voxelAt(double mx, double my, Matrix4f[] mats) {
        VoxelTarget t = voxelTarget(mx, my, mats);
        if (t == null) {
            return;
        }
        boolean changed = applyVoxel(t);
        VoxelTarget m = voxelMirror ? mirrorTarget(t) : null;
        if (m != null && !m.equals(t)) {
            changed |= applyVoxel(m);
        }
        if (changed) {
            strokeChanged = true;
            dirty = true;
        }
    }

    /** Pone, borra o pinta un minibloque segun la herramienta. true si algo cambio. */
    private boolean applyVoxel(VoxelTarget t) {
        CreaturePart p = def.parts.get(t.part());
        int[] before = p.voxels;
        if (tool == Tool.ERASER) {
            p.voxels = Voxels.without(before, t.pos());
            return p.voxels != before;
        }
        int rgb = brushColor & 0xFFFFFF;
        boolean exists = Voxels.indexOf(before, t.pos()) >= 0;
        if (voxelRecolor) {
            if (!exists) {
                return false;
            }
            p.voxels = Voxels.recolor(before, t.pos(), rgb);
            return p.voxels != before;
        }
        strokeVoxels.add(Preview3D.voxelKey(t.part(), t.pos()));
        if (exists) {
            return false;
        }
        if (Voxels.total(def) >= Voxels.MAX_TOTAL) {
            warn(Ui.tr("voxel.too_many", Voxels.MAX_TOTAL));
            return false;
        }
        p.voxels = Voxels.with(before, t.pos(), rgb, t.size());
        return true;
    }

    /** Cuentagotas de los minibloques: el color del minibloque (o de la pieza) bajo el raton. */
    private boolean pickVoxelColor(double mx, double my, Matrix4f[] mats) {
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my, null, false);
        if (hit == null) {
            return false;
        }
        if (!hit.isVoxel()) {
            return pickColor(mx, my, mats);
        }
        CreaturePart p = def.parts.get(hit.part());
        int i = Voxels.indexOf(p.voxels, hit.voxel());
        if (i < 0) {
            return false;
        }
        brushColor = 0xFF000000 | Voxels.colorAt(p.voxels, i);
        voxelSize = Voxels.sizeAt(p.voxels, i);
        showNotice(Ui.tr("tool.picked", CreatureJson.hex(brushColor)), true);
        voxelRecolor = false;
        setTool(Tool.BRUSH);
        return true;
    }

    /** Contorno del hueco bajo el raton en la vista 3D. */
    private void updateVoxelCursor(int mx, int my) {
        preview.cursorPart = -1;
        if (!voxelMode || tab != Tab.PAINT || !mouseMode || !tool.paints() || tool == Tool.PICKER
                || dragging || !preview.contains(mx, my)) {
            return;
        }
        VoxelTarget t = voxelTarget(mx, my, restMats());
        if (t == null) {
            return;
        }
        preview.cursorPart = t.part();
        preview.cursorPos = t.pos();
        preview.cursorSize = t.size();
        preview.cursorColor = tool == Tool.ERASER ? 0xFFFF5050 : voxelRecolor ? brushColor | 0xFF000000 : 0xFFFFFFFF;
    }

    private int buildAnimateTab(int y) {
        y = intro(y);
        CreaturePart p = sel();
        if (p == null) {
            return noParts(y);
        }
        y = smallLabel(y, Ui.tr("animate.mode"));
        y = chips(y, NO_YES, 2, () -> customAnimMode, this::setCustomAnimMode,
                v -> Ui.tr(v ? "animate.mode.custom" : "animate.mode.auto"),
                v -> Ui.tr(v ? "animate.mode.custom_tip" : "animate.mode.auto_tip")) + 6;
        if (customAnimMode) {
            return buildCustomAnim(y, p);
        }
        y = pieceSelector(y);
        if (movesInAny(p)) {
            y = text(y, Ui.tr("animate.custom_wins"), Ui.ACCENT) + 4;
        }
        y = section(y, Ui.tr("animate.type"));
        y = chips(y, PartAnim.values(), 2, () -> p.anim, v -> {
            p.anim = v;
            requestRebuild();
        }, v -> Ui.tr("anim." + v.key()), v -> Ui.tr("anim." + v.key() + ".desc")) + 5;
        y = text(y, Ui.tr("anim." + p.anim.key() + ".desc"), Ui.ACCENT) + 6;
        int lw = labelWidth("editor.field.amount", "editor.field.speed");
        y = numRow(y, lw, "editor.field.amount", () -> p.animAmount, v -> p.animAmount = v, 0f, 5f, 0.1f, false);
        y = numRow(y, lw, "editor.field.speed", () -> p.animSpeed, v -> p.animSpeed = v, 0f, 5f, 0.1f, false);
        y += 4;
        y = section(y, Ui.tr("animate.test"));
        int half = (contentW - GAP) / 2;
        add(new Toggle(0, y, half, Ui.tr("editor.preview.animate"), () -> animate, this::setAnimate, null, false));
        add(new Toggle(half + GAP, y, contentW - half - GAP, Ui.tr("editor.preview.walk"), () -> walk, this::setWalk,
                null, false));
        y += CHIP_H + 4;
        button(0, y, half, Ui.tr("editor.preview.attack"), b -> attackStart = Util.getMillis(), null);
        y += ROW + 6;
        return text(y, Ui.tr("editor.hint.anim"), HINT);
    }

    // ---------------------------------------------------------------------------------------
    // Mis animaciones (poses)
    // ---------------------------------------------------------------------------------------

    private static final String[] AXES = {"X", "Y", "Z"};
    private static final int[] AXIS_TEXT = {0xFFFF9A9A, 0xFF9AFF9A, 0xFF9AC4FF};

    private boolean posingView() {
        return tab == Tab.ANIMATE && customAnimMode;
    }

    private boolean movesInAny(CreaturePart p) {
        for (AnimAction a : AnimAction.ALL) {
            if (p.movesIn(a, def.customAnims.poses(a))) {
                return true;
            }
        }
        return false;
    }

    private MutableComponent actionName(AnimAction a) {
        return Ui.tr("custom.action." + a.key());
    }

    private int buildCustomAnim(int y, CreaturePart p) {
        AnimAction a = animAction;
        y = section(y, Ui.tr("custom.action"));
        y = chips(y, AnimAction.ALL, 2, () -> animAction, v -> {
            animAction = v;
            animPose = 0;
            animPlay = false;
            requestRebuild();
        }, v -> def.customAnims.has(v) ? actionName(v).append(" \u2022") : actionName(v),
                v -> Ui.tr("custom.action." + v.key() + ".tip")) + 4;
        y = text(y, Ui.tr("custom.action." + a.key() + ".tip"), Ui.ACCENT) + 6;
        if (!def.customAnims.has(a)) {
            y = text(y, Ui.tr("custom.none"), Ui.TEXT) + 4;
            button(0, y, contentW, Ui.tr("custom.create", actionName(a)), b -> {
                CustomAnims.create(def, a);
                animPose = 0;
                animPlay = false;
                markChanged();
                requestRebuild();
            }, Ui.tr("custom.create_tip"));
            y += ROW + 6;
            return text(y, Ui.tr("custom.how"), HINT);
        }
        int n = def.customAnims.poses(a);
        animPose = Mth.clamp(animPose, 0, n - 1);

        // Poses
        y = section(y, () -> Ui.tr("custom.poses", animPose + 1, def.customAnims.poses(animAction)));
        Integer[] poses = new Integer[n];
        for (int i = 0; i < n; i++) {
            poses[i] = i;
        }
        y = chips(y, poses, CustomAnims.MAX_POSES, () -> animPose, v -> {
            animPose = v;
            animPlay = false;
        }, v -> Component.literal(String.valueOf(v + 1)), v -> Ui.tr("custom.pose_tip", v + 1)) + 4;
        int third = (contentW - GAP * 2) / 3;
        button(0, y, third, Ui.tr("custom.add_pose"), b -> {
            int at = CustomAnims.insertPose(def, a, animPose, true);
            if (at < 0) {
                warn(Ui.tr("custom.too_many", CustomAnims.MAX_POSES));
                return;
            }
            animPose = at;
            animPlay = false;
            markChanged();
            requestRebuild();
        }, Ui.tr("custom.add_pose_tip"));
        button(third + GAP, y, third, Ui.tr("custom.opposite"), b -> {
            int at = CustomAnims.addOpposite(def, a, animPose);
            if (at < 0) {
                warn(Ui.tr("custom.too_many", CustomAnims.MAX_POSES));
                return;
            }
            animPose = at;
            animPlay = false;
            markChanged();
            requestRebuild();
        }, Ui.tr("custom.opposite_tip"));
        button((third + GAP) * 2, y, contentW - (third + GAP) * 2, Ui.tr("custom.remove_pose"), b -> {
            CustomAnims.removePose(def, a, animPose);
            animPose = Math.max(0, Math.min(animPose, def.customAnims.poses(a) - 1));
            animPlay = false;
            markChanged();
            requestRebuild();
        }, Ui.tr("custom.remove_pose_tip"));
        y += ROW + 6;

        // La pieza en esta pose
        y = section(y, Ui.tr("custom.piece"));
        y = pieceSelector(y);
        if (!mouseMode || tool != Tool.POSE) {
            y = text(y, Ui.tr("custom.mouse_off"), Ui.ACCENT) + 4;
            button(0, y, Math.min(contentW, 170), Ui.tr("custom.mouse_on"), b -> setTool(Tool.POSE), null);
            y += ROW + 4;
        } else {
            y = text(y, Ui.tr("tool.pose.help"), HINT) + 4;
        }
        int lw = 12;
        y = smallLabel(y, Ui.tr("custom.turn"));
        for (int k = 0; k < 3; k++) {
            int c = k;
            y = sliderRow(y, lw, Component.literal(AXES[k]), AXIS_TEXT[k], () -> p.move(animAction, animPose, c),
                    v -> p.setMove(animAction, animPose, c, v), -180f, 180f, CustomAnims.MAX_ANGLE, 1f, 15f,
                    Ui.tr("custom.turn_tip"));
        }
        y = smallLabel(y, Ui.tr("custom.move"));
        for (int k = 0; k < 3; k++) {
            int c = 3 + k;
            y = sliderRow(y, lw, Component.literal(AXES[k]), AXIS_TEXT[k], () -> p.move(animAction, animPose, c),
                    v -> p.setMove(animAction, animPose, c, v), -16f, 16f, CustomAnims.MAX_OFFSET, 0.25f, 1f,
                    Ui.tr("custom.move_tip"));
        }
        button(0, y, contentW, Ui.tr("custom.reset_piece"), b -> {
            p.clearMove(animAction, animPose);
            markChanged();
        }, Ui.tr("custom.reset_piece_tip"));
        y += ROW + 6;

        // Probar y ajustes
        y = section(y, Ui.tr("custom.test"));
        add(new Toggle(0, y, contentW, Ui.tr("custom.play"), () -> animPlay, v -> {
            animPlay = v;
            animPlayStart = Util.getMillis();
        }, Ui.tr("custom.play_tip"), false));
        y += CHIP_H + 4;
        int sw = Math.max(lw, font.width(Ui.tr("custom.speed")) + 6);
        y = sliderRow(y, sw, Ui.tr("custom.speed"), Ui.MUTED, () -> def.customAnims.speed(animAction),
                v -> def.customAnims.speed[animAction.ordinal()] = v, 0.25f, 4f, 0f, 0.05f, 0.25f,
                Ui.tr("custom.speed_tip"));
        add(new TextLine(0, y, () -> durationText(animAction), HINT));
        y += 14;
        button(0, y, contentW, Ui.tr("custom.delete", actionName(a)), b -> {
            CustomAnims.clear(def, a);
            animPose = 0;
            animPlay = false;
            markChanged();
            requestRebuild();
        }, Ui.tr("custom.delete_tip"));
        y += ROW + 6;
        return text(y, Ui.tr("custom.hint"), HINT);
    }

    private Component durationText(AnimAction a) {
        if (a == AnimAction.WALK) {
            return Ui.tr("custom.walk_time");
        }
        float s = a.seconds() / Math.max(CustomAnims.MIN_SPEED, def.customAnims.speed(a));
        if (a == AnimAction.DEATH) {
            s = Math.min(s, 1f);
        }
        return Ui.tr(a.loop() ? "custom.loop_time" : "custom.once_time", Ui.fmt(Math.round(s * 100f) / 100f));
    }

    /**
     * Barra para arrastrar un valor y su numero al lado (se puede escribir). fieldLimit: hasta donde se puede escribir
     * (0 = lo mismo que la barra; si no, de -fieldLimit a fieldLimit).
     */
    private int sliderRow(int y, int lw, Component label, int color, Supplier<Float> getter, Consumer<Float> setter,
                          float min, float max, float fieldLimit, float step, float bigStep, @Nullable Component tip) {
        int fieldW = 44;
        add(new ValueSlider(0, y + 1, contentW - fieldW - GAP, lw, label, color, getter, setter, min, max, step,
                bigStep, tip));
        float lo = fieldLimit > 0f ? -fieldLimit : min;
        float hi = fieldLimit > 0f ? fieldLimit : max;
        number(contentW - fieldW, y, fieldW, getter, setter, lo, hi, step, false);
        return y + ROW;
    }

    /** Lo que se ve en la vista 3D mientras se edita la criatura. */
    private AnimState editorAnim() {
        if (posingView()) {
            return animPlay && def.customAnims.has(animAction) ? playAnim() : pinnedAnim();
        }
        return mouseMode ? AnimState.REST : currentAnim(def);
    }

    /** La postura normal mas la pose que se esta editando. */
    private AnimState pinnedAnim() {
        int n = def.customAnims.poses(animAction);
        return AnimState.pinned(animAction.ordinal(), Mth.clamp(animPose, 0, Math.max(0, n - 1)));
    }

    /** Mis animaciones: la accion elegida como se veria en el juego (las de una vez se repiten). */
    private AnimState playAnim() {
        float t = (Util.getMillis() - animPlayStart) / 50f;
        AnimAction a = animAction;
        float limb = 0f;
        float amount = 0f;
        boolean flying = false;
        float attack = 0f;
        float[] ages = null;
        switch (a) {
            case WALK -> {
                limb = t * 0.6f;
                amount = 1f;
            }
            case FLY -> flying = true;
            case IDLE -> {
            }
            default -> {
                float dur = AnimPlayer.durationTicks(def.customAnims, a);
                float age = t % (dur + (a == AnimAction.DEATH ? 20f : 12f));
                ages = new float[AnimAction.COUNT];
                java.util.Arrays.fill(ages, -1f);
                ages[a.ordinal()] = age;
                if ((a == AnimAction.ATTACK || a == AnimAction.SHOOT) && age < 6f) {
                    attack = age / 6f; // el golpe de las animaciones automaticas (brazos)
                }
            }
        }
        return new AnimState(limb, amount, t, 0f, 0f, attack, flying, ages, -1, -1);
    }

    /** Herramienta Posar: empieza a girar la pieza elegida alrededor de su pivote. */
    private void beginPose(double mx, double my, Matrix4f view, Matrix4f[] mats) {
        CreaturePart p = sel();
        if (p == null || selected >= mats.length || !def.customAnims.has(animAction)) {
            return;
        }
        float[] c = Preview3D.project(view, mats[selected], p.px, p.py, p.pz);
        // La pieza gira en el plano de la pantalla: alrededor del eje que sale de ella.
        float[] axis = Preview3D.viewAxis(view);
        if (axis == null) {
            return;
        }
        poseAxis = axis;
        poseSign = Preview3D.turnSign(view, axis);
        poseCx = c[0];
        poseCy = c[1];
        int pose = Mth.clamp(animPose, 0, def.customAnims.poses(animAction) - 1);
        animPose = pose;
        poseStart = new float[]{p.move(animAction, pose, 0), p.move(animAction, pose, 1), p.move(animAction, pose, 2)};
        poseParentRot = Preview3D.rotationOf(parentMatrix(mats, selected));
        double dx = mx - poseCx;
        double dy = my - poseCy;
        poseLastAngle = dx * dx + dy * dy < 16 ? Double.NaN : Math.atan2(dy, dx);
        poseTurned = 0;
        posing = true;
        poseChanged = false;
    }

    private void updatePose(double mx, double my) {
        CreaturePart p = sel();
        if (p == null || poseStart == null || poseAxis == null || poseParentRot == null) {
            return;
        }
        double dx = mx - poseCx;
        double dy = my - poseCy;
        if (dx * dx + dy * dy < 16) {
            return; // muy cerca del pivote: el angulo no es fiable
        }
        double ang = Math.atan2(dy, dx);
        if (Double.isNaN(poseLastAngle)) {
            poseLastAngle = ang;
            return;
        }
        double d = ang - poseLastAngle;
        while (d > Math.PI) {
            d -= Math.PI * 2;
        }
        while (d < -Math.PI) {
            d += Math.PI * 2;
        }
        poseTurned += d;
        poseLastAngle = ang;
        float angle = (float) poseTurned * poseSign;
        if (hasShiftDown()) {
            float snap = (float) Math.toRadians(15);
            angle = Math.round(angle / snap) * snap;
        }
        float[] delta = PoseSolver.rotateDelta(p, poseStart, poseParentRot, poseAxis[0], poseAxis[1], poseAxis[2],
                angle);
        for (int k = 0; k < 3; k++) {
            p.setMove(animAction, animPose, k, delta[k]);
        }
        poseChanged = true;
        refreshFields();
    }

    private int buildStatsTab(int y) {
        y = intro(y);
        y = section(y, Ui.tr("stats.general"));
        y = numField(y, "stat.health", () -> def.health, v -> def.health = v, 1f, 1024f, 1f, false);
        y = numField(y, "stat.speed", () -> def.speed, v -> def.speed = v, 0.01f, 1.5f, 0.01f, false);
        y = numField(y, "stat.armor", () -> def.armor, v -> def.armor = v, 0f, 30f, 1f, false);
        y = numField(y, "stat.scale", () -> def.scale, v -> def.scale = v, 0.1f, 8f, 0.1f, false);
        y = numField(y, "stat.xp", () -> (float) def.xp, v -> def.xp = Math.round(v), 0f, 1000f, 1f, true);
        add(new TextLine(0, y + 2, () -> {
            float[] hb = def.hitbox();
            return Ui.tr("editor.size_info", Ui.fmt(Math.round(hb[0] * 100f) / 100f),
                    Ui.fmt(Math.round(hb[1] * 100f) / 100f));
        }, Ui.ACCENT));
        y += 18;

        // Reaparicion
        y = section(y, Ui.tr("stats.respawn"));
        y = toggleRow(y, "respawn.enabled", () -> def.respawn, v -> {
            def.respawn = v;
            requestRebuild();
        }, "respawn.enabled_tip");
        if (def.respawn) {
            y = smallLabel(y, Ui.tr("respawn.when"));
            y = chips(y, SpawnTime.values(), 3, () -> def.respawnTime, v -> def.respawnTime = v,
                    v -> Ui.tr("respawn_time." + v.key()), null) + 4;
            y = numField(y, "respawn.delay", () -> (float) def.respawnDelay, v -> def.respawnDelay = Math.round(v),
                    1f, 3600f, 5f, true);
            y = smallLabel(y, Ui.tr("respawn.where"));
            y = chips(y, NO_YES, 2, () -> def.respawnAtHome, v -> def.respawnAtHome = v,
                    v -> Ui.tr(v ? "respawn.home" : "respawn.death"), null) + 4;
            y = numField(y, "respawn.limit", () -> (float) def.respawnLimit, v -> def.respawnLimit = Math.round(v),
                    0f, 1000f, 1f, true);
            y = text(y, Ui.tr("respawn.hint"), HINT);
        }
        y += 6;

        // Cuerpo a cuerpo
        y = section(y, Ui.tr("stats.melee"));
        y = numField(y, "melee.strength", () -> def.damage, v -> def.damage = v, 0f, 200f, 0.5f, false);
        y = numField(y, "melee.reach", () -> def.meleeReach, v -> def.meleeReach = v, 0f, 8f, 0.1f, false);
        y = numField(y, "melee.speed", () -> (float) def.meleeInterval, v -> def.meleeInterval = Math.round(v),
                4f, 200f, 1f, true);
        y = numField(y, "melee.knockback", () -> def.meleeKnockback, v -> def.meleeKnockback = v, 0f, 10f, 0.5f, false);
        y = enumRow(y, "melee.effect", HitEffect.values(), () -> def.hitEffect, v -> def.hitEffect = v,
                v -> Ui.tr("hit_effect." + v.key()));
        y = text(y, Ui.tr("melee.hint"), HINT) + 6;

        // A distancia
        y = section(y, Ui.tr("stats.ranged"));
        y = chips(y, RangedType.values(), 0, () -> def.ranged, v -> {
            RangedPresets.apply(def, v);
            requestRebuild();
        }, v -> Ui.tr("ranged." + v.key()), null) + 4;
        if (def.ranged != RangedType.NONE) {
            y = text(y, Ui.tr("ranged.preset_hint"), HINT) + 4;
            y = numField(y, "ranged.accuracy", () -> (float) def.rangedAccuracy, v -> def.rangedAccuracy = Math.round(v),
                    0f, 100f, 5f, true);
            y = numField(y, "ranged.shots", () -> (float) def.shotCount, v -> def.shotCount = Math.round(v),
                    1f, 10f, 1f, true);
            y = numField(y, "ranged.range", () -> def.rangedRange, v -> def.rangedRange = v, 2f, 64f, 1f, false);
            y = numField(y, "ranged.melee_range", () -> def.rangedMeleeRange, v -> def.rangedMeleeRange = v,
                    0f, 16f, 0.5f, false);
            y = numField(y, "ranged.min_delay", () -> (float) def.rangedMinDelay, v -> {
                def.rangedMinDelay = Math.round(v);
                def.rangedMaxDelay = Math.max(def.rangedMaxDelay, def.rangedMinDelay);
            }, 1f, 400f, 5f, true);
            y = numField(y, "ranged.max_delay", () -> (float) def.rangedMaxDelay,
                    v -> def.rangedMaxDelay = Math.max(def.rangedMinDelay, Math.round(v)), 1f, 400f, 5f, true);
            y = numField(y, "ranged.burst", () -> (float) def.burstCount, v -> def.burstCount = Math.round(v),
                    1f, 20f, 1f, true);
            y = numField(y, "ranged.burst_rate", () -> (float) def.burstRate, v -> def.burstRate = Math.round(v),
                    1f, 100f, 1f, true);
            y = soundRow(y, "ranged.fire_sound", () -> def.fireSound, v -> def.fireSound = v);
            y = soundRow(y, "ranged.hit_sound", () -> def.hitSound, v -> def.hitSound = v);
            y = soundRow(y, "ranged.ground_sound", () -> def.groundSound, v -> def.groundSound = v);
            y = toggleRow(y, "ranged.aim", () -> def.aimWhileShooting, v -> def.aimWhileShooting = v, "ranged.aim_tip");
            y = smallLabel(y, Ui.tr("ranged.indirect"));
            y = chips(y, IndirectFire.values(), 2, () -> def.indirect, v -> def.indirect = v,
                    v -> Ui.tr("indirect." + v.key()), v -> Ui.tr("indirect." + v.key() + ".tip")) + 2;
            y = text(y, Ui.tr("ranged.hint"), HINT) + 6;

            // Proyectil
            y = section(y, Ui.tr("stats.projectile"));
            y = smallLabel(y, Ui.tr("proj.item"));
            itemField(0, y, contentW, () -> def.projItem, v -> def.projItem = v);
            y += ROW;
            y = numField(y, "proj.strength", () -> def.projStrength, v -> def.projStrength = v, 0f, 200f, 0.5f, false);
            y = numField(y, "proj.size", () -> (float) def.projSize, v -> def.projSize = Math.round(v), 1f, 20f, 1f, true);
            y = numField(y, "proj.speed", () -> (float) def.projSpeed, v -> def.projSpeed = Math.round(v), 1f, 50f, 1f, true);
            y = toggleRow(y, "proj.gravity", () -> def.projGravity, v -> def.projGravity = v, "proj.gravity_tip");
            y = smallLabel(y, Ui.tr("proj.motion"));
            y = chips(y, NO_YES, 2, () -> def.projAccelerate, v -> def.projAccelerate = v,
                    v -> Ui.tr(v ? "proj.accelerate" : "proj.constant"), null) + 4;
            y = smallLabel(y, Ui.tr("proj.explode"));
            y = chips(y, ExplosionSize.values(), 2, () -> def.projExplode, v -> def.projExplode = v,
                    v -> Ui.tr("explode." + v.key()), null) + 4;
            y = enumRow(y, "proj.effect", HitEffect.values(), () -> def.projEffect, v -> def.projEffect = v,
                    v -> Ui.tr("hit_effect." + v.key()));
            y = enumRow(y, "proj.trail", TrailType.values(), () -> def.projTrail, v -> def.projTrail = v,
                    v -> Ui.tr("trail." + v.key()));
            y = toggleRow(y, "proj.glows", () -> def.projGlows, v -> def.projGlows = v, null);
            y = numField(y, "proj.knockback", () -> def.projKnockback, v -> def.projKnockback = v, 0f, 10f, 0.5f, false);
            y = smallLabel(y, Ui.tr("proj.shape"));
            y = chips(y, YES_NO, 2, () -> def.proj3D, v -> {
                def.proj3D = v;
                requestRebuild();
            }, v -> Ui.tr(v ? "proj.3d" : "proj.2d"), v -> Ui.tr(v ? "proj.3d_tip" : "proj.2d_tip")) + 4;
            if (def.proj3D) {
                // como en el original: girar y clavarse son opciones del proyectil 3D
                y = toggleRow(y, "proj.spins", () -> def.projSpins, v -> def.projSpins = v, null);
                y = toggleRow(y, "proj.sticks", () -> def.projSticks, v -> def.projSticks = v, "proj.sticks_tip");
            }
            y = text(y, Ui.tr("proj.hint"), HINT);
        }
        y += 6;

        // Resistencias
        y = section(y, Ui.tr("stats.resist"));
        y = numField(y, "resist.knockback", () -> Math.round(def.knockbackResistance * 100f) * 1f,
                v -> def.knockbackResistance = v / 100f, 0f, 100f, 5f, true);
        y = numField(y, "resist.arrow", () -> (float) def.resistArrow, v -> def.resistArrow = Math.round(v),
                0f, 100f, 5f, true);
        y = numField(y, "resist.melee", () -> (float) def.resistMelee, v -> def.resistMelee = Math.round(v),
                0f, 100f, 5f, true);
        y = numField(y, "resist.explosion", () -> (float) def.resistExplosion, v -> def.resistExplosion = Math.round(v),
                0f, 100f, 5f, true);
        y = text(y, Ui.tr("resist.hint"), HINT) + 6;

        // Regeneracion
        y = section(y, Ui.tr("stats.regen"));
        y = numField(y, "regen.health", () -> def.healthRegen, v -> def.healthRegen = v, 0f, 100f, 0.5f, false);
        y = numField(y, "regen.combat", () -> def.combatRegen, v -> def.combatRegen = v, 0f, 100f, 0.5f, false);
        y = text(y, Ui.tr("regen.hint"), HINT) + 6;

        // Alcance
        y = section(y, Ui.tr("stats.range"));
        y = numField(y, "range.vision", () -> def.visionRange, v -> def.visionRange = v, 1f, 128f, 1f, false);
        y = toggleRow(y, "range.walls", () -> def.seeThroughWalls, v -> {
            def.seeThroughWalls = v;
            requestRebuild();
        }, "range.walls_tip");
        if (def.seeThroughWalls) {
            y = numField(y, "range.wall_range", () -> def.wallRange, v -> def.wallRange = v, 1f, 128f, 1f, false);
        }
        y = numField(y, "range.follow", () -> def.followRange, v -> def.followRange = v, 4f, 128f, 1f, false);
        y = text(y, Ui.tr("range.hint"), HINT) + 6;

        // Otros
        y = section(y, Ui.tr("stats.other"));
        y = toggleRow(y, "other.can_drown", () -> def.canDrown, v -> def.canDrown = v, null);
        y = toggleRow(y, "other.no_fall", () -> def.noFallDamage, v -> def.noFallDamage = v, null);
        y = toggleRow(y, "other.cobweb", () -> def.cobwebAffected, v -> def.cobwebAffected = v, null);
        y = toggleRow(y, "other.potion_immune", () -> def.potionImmune, v -> def.potionImmune = v, null);
        y = toggleRow(y, "ai.fire_immune", () -> def.fireImmune, v -> def.fireImmune = v, null);
        y = toggleRow(y, "ai.burns_in_sun", () -> def.burnsInSun, v -> def.burnsInSun = v, null);
        y += 4;
        return text(y, Ui.tr("editor.hint.stats"), HINT);
    }

    // ---------------------------------------------------------------------------------------
    // Inventario
    // ---------------------------------------------------------------------------------------

    private int buildInventoryTab(int y) {
        y = intro(y);
        y = section(y, Ui.tr("inv.equipment"));
        // nombre del hueco a la izquierda si queda sitio para el campo; si no, encima
        int lw = labelWidth("inv.head", "inv.chest", "inv.legs", "inv.feet", "inv.main_hand", "inv.off_hand",
                "inv.shield", "inv.projectile");
        if (contentW - lw < 110) {
            lw = 0;
        }
        y = equipRow(y, lw, "inv.head", () -> def.equipHead, v -> def.equipHead = v);
        y = equipRow(y, lw, "inv.chest", () -> def.equipChest, v -> def.equipChest = v);
        y = equipRow(y, lw, "inv.legs", () -> def.equipLegs, v -> def.equipLegs = v);
        y = equipRow(y, lw, "inv.feet", () -> def.equipFeet, v -> def.equipFeet = v);
        y = equipRow(y, lw, "inv.main_hand", () -> def.equipMainHand, v -> def.equipMainHand = v);
        y = equipRow(y, lw, "inv.off_hand", () -> def.equipOffHand, v -> def.equipOffHand = v);
        y = equipRow(y, lw, "inv.shield", () -> def.equipShield, v -> def.equipShield = v);
        y = equipRow(y, lw, "inv.projectile", () -> def.projItem, v -> def.projItem = v);
        y = numField(y, "inv.equip_drop", () -> (float) def.equipDropChance, v -> def.equipDropChance = Math.round(v),
                0f, 100f, 5f, true);
        y = text(y, Ui.tr("inv.equipment_hint"), HINT) + 8;

        y = section(y, () -> Ui.tr("inv.drops", def.drops.size(), CreatureDefinition.MAX_DROPS));
        for (int i = 0; i < def.drops.size(); i++) {
            y = dropRow(y, def.drops.get(i));
        }
        if (def.drops.size() < CreatureDefinition.MAX_DROPS) {
            button(0, y, contentW, Ui.tr("inv.add_drop"), b -> {
                def.drops.add(new CreatureDrop("", 1, 1, 100));
                markChanged();
                requestRebuild();
            }, null);
            y += ROW;
        }
        y += 4;
        return text(y, Ui.tr("inv.hint"), HINT);
    }

    /** Un hueco del equipo: su nombre (a la izquierda, o encima si lw = 0) y el objeto. */
    private int equipRow(int y, int lw, String key, Supplier<String> getter, Consumer<String> setter) {
        if (lw > 0) {
            label(0, y, Ui.tr(key));
        } else {
            y = smallLabel(y, Ui.tr(key));
        }
        itemField(lw, y, contentW - lw, getter, setter);
        return y + ROW + (lw > 0 ? 0 : 2);
    }

    /** Un objeto del botin: el objeto (con boton de quitar) y debajo su cantidad y su probabilidad. */
    private int dropRow(int y, CreatureDrop drop) {
        int delW = 16;
        itemField(0, y, contentW - delW - GAP, () -> drop.item, v -> drop.item = v);
        button(contentW - delW, y, delW, Component.literal("\u2715"), b -> {
            def.drops.remove(drop);
            markChanged();
            requestRebuild();
        }, Ui.tr("inv.remove_drop"));
        y += ROW;
        Component amount = Ui.tr("inv.amount");
        Component to = Ui.tr("loot.to");
        Component chance = Ui.tr("inv.chance");
        int aw = font.width(amount) + 4;
        int tw = font.width(to) + 6;
        int cw = font.width(chance) + 4;
        int fw = Math.max(22, (contentW - aw - tw - cw - 6) / 3);
        int x = 0;
        add(new TextLine(x, y + 4, () -> amount, Ui.MUTED));
        x += aw;
        number(x, y, fw, () -> (float) drop.min, v -> {
            drop.min = Math.round(v);
            drop.max = Math.max(drop.max, drop.min);
        }, 0f, 64f, 1f, true);
        x += fw;
        add(new TextLine(x + 3, y + 4, () -> to, Ui.MUTED));
        x += tw;
        number(x, y, fw, () -> (float) drop.max, v -> drop.max = Math.max(drop.min, Math.round(v)), 0f, 64f, 1f, true);
        x += fw + 6;
        add(new TextLine(x, y + 4, () -> chance, Ui.MUTED));
        x += cw;
        number(x, y, Math.max(22, contentW - x), () -> (float) drop.chance, v -> drop.chance = Math.round(v),
                1f, 100f, 5f, true);
        return y + ROW + 5;
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes de las pestanas Atributos e Inventario
    // ---------------------------------------------------------------------------------------

    /** Numero con su nombre a la izquierda; si el nombre no cabe, va encima. */
    private int numField(int y, String key, Supplier<Float> getter, Consumer<Float> setter, float min, float max,
                         float step, boolean integer) {
        Component label = Ui.tr(key);
        int fieldW = Mth.clamp(contentW / 3, 46, 70);
        if (font.width(label) + 6 + fieldW <= contentW) {
            label(0, y, label);
            number(contentW - fieldW, y, fieldW, getter, setter, min, max, step, integer);
            return y + ROW;
        }
        y = smallLabel(y, label);
        number(0, y, contentW, getter, setter, min, max, step, integer);
        return y + ROW;
    }

    private int toggleRow(int y, String key, BooleanSupplier getter, Consumer<Boolean> setter, @Nullable String tipKey) {
        add(new Toggle(0, y, contentW, Ui.tr(key), getter, setter, tipKey == null ? null : Ui.tr(tipKey), true));
        return y + CHIP_H + 2;
    }

    /** Lista desplegable para elegir un valor de una lista (efecto, estela...). */
    private <E> int enumRow(int y, String key, E[] values, Supplier<E> getter, Consumer<E> setter,
                            Function<E, Component> name) {
        Component label = Ui.tr(key);
        int lw = font.width(label) + 6;
        if (lw + 80 > contentW) {
            y = smallLabel(y, label);
            lw = 0;
        } else {
            label(0, y, label);
        }
        add(new DropButton(lw, y, contentW - lw, () -> name.apply(getter.get()), () -> -1, null,
                anchor -> openValueDropdown(anchor, values, getter, setter, name)));
        return y + ROW;
    }

    private <E> void openValueDropdown(Element anchor, E[] values, Supplier<E> getter, Consumer<E> setter,
                                       Function<E, Component> name) {
        List<DropItem> items = new ArrayList<>();
        E current = getter.get();
        for (E v : values) {
            items.add(new DropItem(name.apply(v), -1, 0, Objects.equals(v, current)));
        }
        openDropdown(anchor, items, i -> {
            setter.accept(values[i]);
            markChanged();
        });
    }

    /** Sonido de la lista con un boton para escucharlo. */
    private int soundRow(int y, String key, Supplier<ShotSound> getter, Consumer<ShotSound> setter) {
        y = smallLabel(y, Ui.tr(key));
        int testW = 18;
        add(new DropButton(0, y, contentW - testW - GAP, () -> Ui.tr("shot_sound." + getter.get().key()), () -> -1, null,
                anchor -> openValueDropdown(anchor, ShotSound.values(), getter, v -> {
                    setter.accept(v);
                    playSoundId(v.id);
                }, v -> Ui.tr("shot_sound." + v.key()))));
        button(contentW - testW, y, testW, Component.literal("\u266A"), b -> playSoundId(getter.get().id),
                Ui.tr("ai.sound_test"));
        return y + ROW;
    }

    private void playSoundId(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            BuiltInRegistries.SOUND_EVENT.getOptional(rl)
                    .ifPresent(event -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f)));
        }
    }

    /** Campo de un objeto con su icono y un boton para buscarlo en la lista de objetos del juego. */
    private void itemField(int x, int y, int w, Supplier<String> getter, Consumer<String> setter) {
        int pickW = 14;
        EditBox box = textBox(x, y, Math.max(20, w - FIELD_H - pickW - 4), 128, getter.get(), v -> {
            setter.accept(v.trim());
            markChanged();
        }, Ui.tr("field.item_hint"));
        box.setTextColor(itemColor(getter.get()));
        refreshers.add(() -> box.setTextColor(itemColor(getter.get())));
        add(new ItemIcon(x + w - FIELD_H - pickW - 2, y, getter));
        add(new DropButton(x + w - pickW, y, pickW, Component::empty, () -> -1, Ui.tr("field.item_pick"),
                anchor -> openItemDropdown(anchor.left() - (w - pickW), anchor.top(), w, anchor.h, box.getValue(),
                        id -> {
                            setter.accept(id);
                            box.setValue(id);
                            markChanged();
                        })));
    }

    @Nullable
    private static List<ResourceLocation> itemIds;

    private static List<ResourceLocation> itemIds() {
        if (itemIds == null) {
            List<ResourceLocation> ids = new ArrayList<>(BuiltInRegistries.ITEM.keySet());
            ids.sort((a, b) -> a.getPath().compareTo(b.getPath()));
            itemIds = ids;
        }
        return itemIds;
    }

    /**
     * Lista de objetos del juego. Si el campo tiene un objeto que existe, se ve la lista entera con ese marcado;
     * si tiene otra cosa, solo los objetos que la contienen (en el id o en el nombre).
     */
    private void openItemDropdown(int ax, int ay, int aw, int ah, String filter, Consumer<String> onPick) {
        String current = resolveItem(filter).isEmpty() ? "" : CreatureDefinition.cleanResource(filter);
        String q = current.isEmpty() ? filter.trim().toLowerCase(Locale.ROOT) : "";
        if (q.startsWith("minecraft:")) {
            q = q.substring("minecraft:".length());
        }
        List<String> ids = new ArrayList<>();
        List<DropItem> items = new ArrayList<>();
        for (ResourceLocation rl : itemIds()) {
            if (items.size() >= 2000) {
                break;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) {
                continue;
            }
            String id = rl.toString();
            String name = item.getDescription().getString();
            if (!q.isEmpty() && !id.contains(q) && !name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            ids.add(id);
            String shown = rl.getNamespace().equals("minecraft") ? rl.getPath() : id;
            items.add(new DropItem(Component.literal(name + "  \u00B7 " + shown), -1, 0, id.equals(current)));
        }
        if (items.isEmpty()) {
            warn(Ui.tr("field.item_no_match", filter.trim()));
            return;
        }
        dropdown = new Dropdown(ax, ay, aw, ah, items, i -> onPick.accept(ids.get(i)));
        setFocused(null);
    }

    // ---------------------------------------------------------------------------------------
    // Habilidades
    // ---------------------------------------------------------------------------------------

    private static final int SUB = 14;
    private static final ExplosionSize[] BLAST_SIZES = {ExplosionSize.SMALL, ExplosionSize.MEDIUM, ExplosionSize.BIG};

    private int buildAbilitiesTab(int y) {
        y = intro(y);
        Abilities a = def.abilities;
        add(new TextLine(0, y, () -> Ui.tr("ability.active", def.abilities.activeCount()), Ui.ACCENT));
        y += 14;

        // Creeper
        y = abilityRow(y, Items.CREEPER_SPAWN_EGG, "ability.explode", () -> a.explode, v -> a.explode = v);
        if (a.explode) {
            indent(SUB);
            y = smallLabel(y, Ui.tr("ability.explode.size"));
            y = chips(y, BLAST_SIZES, 3, () -> a.explodeSize, v -> a.explodeSize = v,
                    v -> Ui.tr("explode." + v.key()), null) + 4;
            y = numField(y, "ability.explode.range", () -> a.explodeRange, v -> a.explodeRange = v, 1f, 16f, 0.5f, false);
            y = numField(y, "ability.explode.fuse", () -> (float) a.explodeFuse, v -> a.explodeFuse = Math.round(v),
                    5f, 200f, 5f, true);
            y = toggleRow(y, "ability.explode.dies", () -> a.explodeDies, v -> {
                a.explodeDies = v;
                requestRebuild();
            }, "ability.explode.dies_tip");
            if (!a.explodeDies) {
                y = numField(y, "ability.explode.cooldown", () -> (float) a.explodeCooldown,
                        v -> a.explodeCooldown = Math.round(v), 1f, 120f, 1f, true);
            }
            y = text(y, Ui.tr("ability.explode.hint"), HINT) + 4;
            indent(-SUB);
        }

        // Warden
        y = abilityRow(y, Items.WARDEN_SPAWN_EGG, "ability.blind", () -> a.blind, v -> a.blind = v);
        if (a.blind) {
            indent(SUB);
            y = numField(y, "ability.blind.range", () -> a.hearRange, v -> a.hearRange = v, 2f, 64f, 1f, false);
            y = text(y, Ui.tr("ability.blind.hint"), HINT) + 4;
            indent(-SUB);
        }
        y = abilityRow(y, Items.WARDEN_SPAWN_EGG, "ability.sonic", () -> a.sonicBoom, v -> a.sonicBoom = v);
        if (a.sonicBoom) {
            indent(SUB);
            y = numField(y, "ability.damage", () -> a.sonicDamage, v -> a.sonicDamage = v, 0f, 200f, 1f, false);
            y = numField(y, "ability.range", () -> a.sonicRange, v -> a.sonicRange = v, 2f, 48f, 1f, false);
            y = numField(y, "ability.cooldown_s", () -> (float) a.sonicCooldown, v -> a.sonicCooldown = Math.round(v),
                    1f, 120f, 1f, true);
            y = text(y, Ui.tr("ability.sonic.hint"), HINT) + 4;
            indent(-SUB);
        }

        // Enderman
        y = abilityRow(y, Items.ENDERMAN_SPAWN_EGG, "ability.teleport", () -> a.teleport, v -> a.teleport = v);
        if (a.teleport) {
            indent(SUB);
            y = numField(y, "ability.range", () -> a.teleportRange, v -> a.teleportRange = v, 2f, 64f, 1f, false);
            y = toggleRow(y, "ability.teleport.dodge", () -> a.teleportDodge, v -> a.teleportDodge = v, null);
            y = toggleRow(y, "ability.teleport.chase", () -> a.teleportChase, v -> a.teleportChase = v, null);
            y += 4;
            indent(-SUB);
        }
        y = abilityRow(y, Items.ENDERMAN_SPAWN_EGG, "ability.stare", () -> a.stareAnger, v -> a.stareAnger = v);
        if (a.stareAnger) {
            indent(SUB);
            y = text(y, Ui.tr("ability.stare.hint"), HINT) + 4;
            indent(-SUB);
        }

        // Guardian
        y = abilityRow(y, Items.GUARDIAN_SPAWN_EGG, "ability.beam", () -> a.beam, v -> a.beam = v);
        if (a.beam) {
            indent(SUB);
            y = numField(y, "ability.damage", () -> a.beamDamage, v -> a.beamDamage = v, 0f, 200f, 1f, false);
            y = numField(y, "ability.range", () -> a.beamRange, v -> a.beamRange = v, 2f, 48f, 1f, false);
            y = numField(y, "ability.beam.charge", () -> (float) a.beamCharge, v -> a.beamCharge = Math.round(v),
                    10f, 200f, 5f, true);
            y = text(y, Ui.tr("ability.beam.hint"), HINT) + 4;
            indent(-SUB);
        }
        y = abilityRow(y, Items.GUARDIAN_SPAWN_EGG, "ability.thorns", () -> a.thorns, v -> a.thorns = v);
        if (a.thorns) {
            indent(SUB);
            y = numField(y, "ability.damage", () -> a.thornsDamage, v -> a.thornsDamage = v, 0f, 100f, 0.5f, false) + 4;
            indent(-SUB);
        }
        y = abilityRow(y, Items.ELDER_GUARDIAN_SPAWN_EGG, "ability.aura", () -> a.aura, v -> a.aura = v);
        if (a.aura) {
            indent(SUB);
            HitEffect[] effects = java.util.Arrays.stream(HitEffect.values()).filter(e -> e != HitEffect.NONE)
                    .toArray(HitEffect[]::new);
            y = enumRow(y, "ability.aura.effect", effects, () -> a.auraEffect, v -> a.auraEffect = v,
                    v -> Ui.tr("hit_effect." + v.key()));
            y = numField(y, "ability.range", () -> a.auraRange, v -> a.auraRange = v, 2f, 48f, 1f, false);
            y = numField(y, "ability.aura.interval", () -> (float) a.auraInterval, v -> a.auraInterval = Math.round(v),
                    1f, 600f, 1f, true);
            y = numField(y, "ability.aura.duration", () -> (float) a.auraDuration, v -> a.auraDuration = Math.round(v),
                    1f, 600f, 1f, true);
            y += 4;
            indent(-SUB);
        }

        // Slime
        y = abilityRow(y, Items.SLIME_SPAWN_EGG, "ability.split", () -> a.split, v -> a.split = v);
        if (a.split) {
            indent(SUB);
            y = numField(y, "ability.split.count", () -> (float) a.splitCount, v -> a.splitCount = Math.round(v),
                    2f, 4f, 1f, true);
            y = numField(y, "ability.split.generations", () -> (float) a.splitGenerations,
                    v -> a.splitGenerations = Math.round(v), 1f, 3f, 1f, true);
            y = text(y, Ui.tr("ability.split.hint"), HINT) + 4;
            indent(-SUB);
        }

        // Arana
        y = abilityRow(y, Items.SPIDER_SPAWN_EGG, "ability.climb", () -> a.climb, v -> a.climb = v);
        y = abilityRow(y, Items.SPIDER_SPAWN_EGG, "ability.leap", () -> a.leap, v -> a.leap = v);

        // Golem de hierro
        y = abilityRow(y, Items.IRON_GOLEM_SPAWN_EGG, "ability.launch", () -> a.launch, v -> a.launch = v);
        if (a.launch) {
            indent(SUB);
            y = numField(y, "ability.launch.power", () -> a.launchPower, v -> a.launchPower = v, 0.1f, 5f, 0.1f, false) + 4;
            indent(-SUB);
        }

        // Bruja
        y = abilityRow(y, Items.WITCH_SPAWN_EGG, "ability.heal", () -> a.selfHeal, v -> a.selfHeal = v);
        if (a.selfHeal) {
            indent(SUB);
            y = numField(y, "ability.heal.amount", () -> a.selfHealAmount, v -> a.selfHealAmount = v, 1f, 1024f, 1f, false);
            y = numField(y, "ability.cooldown_s", () -> (float) a.selfHealCooldown,
                    v -> a.selfHealCooldown = Math.round(v), 1f, 600f, 1f, true);
            y = text(y, Ui.tr("ability.heal.hint"), HINT) + 4;
            indent(-SUB);
        }

        // Invocador
        y = abilityRow(y, Items.EVOKER_SPAWN_EGG, "ability.summon", () -> a.summon, v -> a.summon = v);
        if (a.summon) {
            indent(SUB);
            y = smallLabel(y, Ui.tr("ability.summon.entity"));
            entityField(0, y, contentW, () -> a.summonEntity, v -> a.summonEntity = v);
            y += ROW;
            y = numField(y, "ability.summon.count", () -> (float) a.summonCount, v -> a.summonCount = Math.round(v),
                    1f, 6f, 1f, true);
            y = numField(y, "ability.summon.max", () -> (float) a.summonMax, v -> a.summonMax = Math.round(v),
                    1f, 16f, 1f, true);
            y = numField(y, "ability.cooldown_s", () -> (float) a.summonCooldown, v -> a.summonCooldown = Math.round(v),
                    1f, 600f, 1f, true);
            y = text(y, Ui.tr("ability.summon.hint"), HINT) + 4;
            indent(-SUB);
        }

        // Devastador
        y = abilityRow(y, Items.RAVAGER_SPAWN_EGG, "ability.charge", () -> a.charge, v -> a.charge = v);
        if (a.charge) {
            indent(SUB);
            y = numField(y, "ability.damage", () -> a.chargeDamage, v -> a.chargeDamage = v, 0f, 200f, 1f, false);
            y = numField(y, "ability.charge.speed", () -> a.chargeSpeed, v -> a.chargeSpeed = v, 0.3f, 3f, 0.1f, false);
            y = numField(y, "ability.cooldown_s", () -> (float) a.chargeCooldown, v -> a.chargeCooldown = Math.round(v),
                    1f, 120f, 1f, true);
            y += 4;
            indent(-SUB);
        }

        // Lobo
        y = abilityRow(y, Items.WOLF_SPAWN_EGG, "ability.pack", () -> a.pack, v -> a.pack = v);
        if (a.pack) {
            indent(SUB);
            y = numField(y, "ability.range", () -> a.packRange, v -> a.packRange = v, 2f, 64f, 1f, false) + 4;
            indent(-SUB);
        }

        // Calamar, breeze
        y = abilityRow(y, Items.SQUID_SPAWN_EGG, "ability.ink", () -> a.ink, v -> a.ink = v);
        y = abilityRow(y, Items.BREEZE_SPAWN_EGG, "ability.deflect", () -> a.deflect, v -> a.deflect = v);
        if (a.deflect) {
            indent(SUB);
            y = numField(y, "ability.deflect.chance", () -> (float) a.deflectChance, v -> a.deflectChance = Math.round(v),
                    1f, 100f, 5f, true) + 4;
            indent(-SUB);
        }

        // Gallina
        y = abilityRow(y, Items.CHICKEN_SPAWN_EGG, "ability.slow_fall", () -> a.slowFall, v -> a.slowFall = v);
        y = abilityRow(y, Items.CHICKEN_SPAWN_EGG, "ability.lay", () -> a.layItems, v -> a.layItems = v);
        if (a.layItems) {
            indent(SUB);
            y = smallLabel(y, Ui.tr("ability.lay.item"));
            itemField(0, y, contentW, () -> a.layItem, v -> a.layItem = v);
            y += ROW;
            y = numField(y, "ability.lay.interval", () -> (float) a.layInterval, v -> a.layInterval = Math.round(v),
                    5f, 3600f, 10f, true) + 4;
            indent(-SUB);
        }

        // Vaca
        y = abilityRow(y, Items.COW_SPAWN_EGG, "ability.milk", () -> a.milkable, v -> a.milkable = v);
        if (a.milkable) {
            indent(SUB);
            y = smallLabel(y, Ui.tr("ability.milk.with"));
            itemField(0, y, contentW, () -> a.milkWith, v -> a.milkWith = v);
            y += ROW;
            y = smallLabel(y, Ui.tr("ability.milk.gives"));
            itemField(0, y, contentW, () -> a.milkGives, v -> a.milkGives = v);
            y += ROW;
            y = toggleRow(y, "ability.milk.uses", () -> a.milkUsesItem, v -> a.milkUsesItem = v, "ability.milk.uses_tip");
            y = numField(y, "ability.cooldown_s", () -> (float) a.milkCooldown, v -> a.milkCooldown = Math.round(v),
                    0f, 3600f, 5f, true) + 4;
            indent(-SUB);
        }

        // Zombi y aldeano
        add(new AbilityHeader(0, y, contentW, new ItemStack(Items.ZOMBIE_SPAWN_EGG), Ui.tr("ability.doors"),
                Ui.tr("ability.doors.tip")));
        y += ROW;
        indent(SUB);
        y = chips(y, DoorMode.values(), 3, () -> a.doors, v -> a.doors = v, v -> Ui.tr("door." + v.key()), null) + 6;
        indent(-SUB);

        // Golem de nieve
        y = abilityRow(y, Items.SNOW_GOLEM_SPAWN_EGG, "ability.trail", () -> a.trail, v -> a.trail = v);
        if (a.trail) {
            indent(SUB);
            y = chips(y, TrailBlock.values(), 2, () -> a.trailBlock, v -> a.trailBlock = v,
                    v -> Ui.tr("trail_block." + v.key()), null) + 2;
            y = text(y, Ui.tr("ability.trail.hint"), HINT) + 4;
            indent(-SUB);
        }

        // Calamar luminoso, zorro
        y = abilityRow(y, Items.GLOW_SQUID_SPAWN_EGG, "ability.glows", () -> a.glows, v -> a.glows = v);
        y = abilityRow(y, Items.FOX_SPAWN_EGG, "ability.pick_up", () -> a.pickUp, v -> a.pickUp = v);

        // Miedo
        y = abilityRow(y, Items.CAT_SPAWN_EGG, "ability.fear", () -> a.fear, v -> a.fear = v);
        if (a.fear) {
            indent(SUB);
            y = smallLabel(y, Ui.tr("ability.fear.entity"));
            entityField(0, y, contentW, () -> a.fearEntity, v -> a.fearEntity = v);
            y += ROW;
            y = numField(y, "ability.range", () -> a.fearRange, v -> a.fearRange = v, 2f, 32f, 1f, false) + 4;
            indent(-SUB);
        }
        y += 4;
        return text(y, Ui.tr("ability.hint"), HINT);
    }

    /** Fila de una habilidad: el huevo del mob, la casilla y el nombre. Al cambiarla se ven u ocultan sus opciones. */
    private int abilityRow(int y, Item icon, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        add(new AbilityToggle(0, y, contentW, new ItemStack(icon), Ui.tr(key), Ui.tr(key + ".tip"), getter, v -> {
            setter.accept(v);
            requestRebuild();
        }));
        return y + ROW + 1;
    }

    /** Campo del id de un mob (de Minecraft o una criatura propia) con su lista para elegir. */
    private void entityField(int x, int y, int w, Supplier<String> getter, Consumer<String> setter) {
        int pickW = 14;
        EditBox box = textBox(x, y, Math.max(20, w - pickW - 2), 64, getter.get(), v -> {
            setter.accept(v.trim());
            markChanged();
        }, Ui.tr("field.entity_hint"));
        add(new DropButton(x + w - pickW, y, pickW, Component::empty, () -> -1, Ui.tr("field.entity_pick"),
                anchor -> openEntityDropdown(anchor.left() - (w - pickW), anchor.top(), w, anchor.h, box.getValue(),
                        id -> {
                            setter.accept(id);
                            box.setValue(id);
                            markChanged();
                        })));
    }

    /** Mobs de Minecraft (los que no son "varios", como flechas o barcas) y las criaturas propias. */
    private void openEntityDropdown(int ax, int ay, int aw, int ah, String filter, Consumer<String> onPick) {
        String q = filter.trim().toLowerCase(Locale.ROOT);
        if (q.startsWith("minecraft:")) {
            q = q.substring("minecraft:".length());
        }
        List<String> ids = new ArrayList<>();
        List<DropItem> items = new ArrayList<>();
        for (CreatureDefinition other : CreatureStore.CLIENT.sorted()) {
            if (other.id.equals(def.id)) {
                continue;
            }
            if (!q.isEmpty() && !other.id.contains(q) && !other.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            ids.add(other.id);
            items.add(new DropItem(Component.literal(other.name + "  \u00B7 " + other.id), 0xFF000000 | other.eggColor1, 0,
                    false));
        }
        List<ResourceLocation> keys = new ArrayList<>(BuiltInRegistries.ENTITY_TYPE.keySet());
        keys.sort((a, b) -> a.getPath().compareTo(b.getPath()));
        for (ResourceLocation rl : keys) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (type.getCategory() == MobCategory.MISC && type != EntityType.VILLAGER && type != EntityType.IRON_GOLEM
                    && type != EntityType.SNOW_GOLEM) {
                continue;
            }
            String id = rl.toString();
            String name = type.getDescription().getString();
            if (!q.isEmpty() && !id.contains(q) && !name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            ids.add(id);
            items.add(new DropItem(Component.literal(name + "  \u00B7 " + rl.getPath()), -1, 0, false));
        }
        if (items.isEmpty()) {
            warn(Ui.tr("field.entity_no_match", filter.trim()));
            return;
        }
        dropdown = new Dropdown(ax, ay, aw, ah, items, i -> onPick.accept(ids.get(i)));
        setFocused(null);
    }

    private int buildBehaviorTab(int y) {
        y = intro(y);
        y = section(y, Ui.tr("ai.behavior"));
        y = chips(y, Behavior.values(), 3, () -> def.behavior, v -> {
            def.behavior = v;
            requestRebuild();
        }, v -> Ui.tr("behavior." + v.key()), null) + 4;
        y = text(y, Ui.tr("behavior." + def.behavior.key() + ".desc"), Ui.ACCENT) + 8;

        y = section(y, Ui.tr("ai.movement"));
        y = chips(y, Movement.values(), 2, () -> def.movement, v -> def.movement = v,
                v -> Ui.tr("movement." + v.key()), null) + 8;

        y = section(y, Ui.tr("ai.sound"));
        Component listen = Component.literal("\u266A ").append(Ui.tr("ai.sound_test"));
        int listenW = Math.min(contentW / 2, Math.max(56, font.width(listen) + 12));
        add(new DropButton(0, y, contentW - listenW - GAP, () -> Ui.tr("sound." + def.sound.key()), () -> -1,
                Ui.tr("ai.sound_tooltip"), this::openSoundDropdown));
        button(contentW - listenW, y, listenW, listen, b -> playSound(), null);
        y += ROW + 6;

        y = section(y, Ui.tr("ai.extra"));
        y = smallLabel(y, Ui.tr("ai.tempt_item"));
        itemField(0, y, contentW, () -> def.temptItem, v -> def.temptItem = v);
        y += ROW;
        y = text(y, Ui.tr("ai.moved_to_stats"), HINT) + 4;
        y += 4;
        return text(y, Ui.tr("editor.hint.ai"), HINT);
    }

    private int buildSpawnTab(int y) {
        y = intro(y);
        y = section(y, Ui.tr("spawn.section"));
        add(new Toggle(0, y, contentW, Ui.tr("spawn.natural"), () -> def.naturalSpawn, v -> def.naturalSpawn = v,
                Ui.tr("spawn.natural_tip"), true));
        y += CHIP_H + 4;
        y = smallLabel(y, Ui.tr("spawn.time"));
        y = chips(y, SpawnTime.values(), 3, () -> def.spawnTime, v -> def.spawnTime = v,
                v -> Ui.tr("spawn_time." + v.key()), null) + 6;
        int lw = labelWidth("spawn.biomes", "spawn.chance", "spawn.max");
        label(0, y, Ui.tr("spawn.biomes"));
        textBox(lw, y, contentW - lw, 512, def.spawnBiomes, s -> {
            def.spawnBiomes = s;
            markChanged();
        }, Ui.tr("spawn.biomes_hint"));
        y += ROW;
        y = numRow(y, lw, "spawn.chance", () -> (float) def.spawnChance, v -> def.spawnChance = Math.round(v),
                1f, 100f, 1f, true);
        y = numRow(y, lw, "spawn.max", () -> (float) def.spawnMax, v -> def.spawnMax = Math.round(v),
                1f, 32f, 1f, true);
        y += 6;

        y = section(y, Ui.tr("spawn.where_section"));
        y = text(y, Ui.tr("spawn.where_intro"), Ui.MUTED) + 4;
        y = smallLabel(y, Ui.tr("spawn.place"));
        y = chips(y, SpawnPlace.values(), 2, () -> def.spawnPlace, v -> def.spawnPlace = v,
                v -> Ui.tr("spawn_place." + v.key()), v -> Ui.tr("spawn_place." + v.key() + ".tip")) + 6;
        y = smallLabel(y, Ui.tr("spawn.structures"));
        y = toggleChips(y, SpawnStructure.values(), 2, v -> def.spawnStructures.contains(v), v -> {
            if (!def.spawnStructures.remove(v)) {
                def.spawnStructures.add(v);
            }
        }, v -> Ui.tr("structure." + v.key()), v -> Ui.tr("structure." + v.key() + ".tip")) + 4;
        int lw2 = labelWidth("spawn.structure_ids");
        label(0, y, Ui.tr("spawn.structure_ids"));
        textBox(lw2, y, contentW - lw2, 512, def.spawnStructureIds, s -> {
            def.spawnStructureIds = s;
            markChanged();
        }, Ui.tr("spawn.structure_ids_hint"));
        y += ROW;
        button(0, y, contentW, Ui.tr("spawn.structures_clear"), b -> {
            def.spawnStructures.clear();
            def.spawnStructureIds = "";
            markChanged();
            requestRebuild();
        }, Ui.tr("spawn.structures_clear_tip"));
        y += ROW;
        y = text(y, Ui.tr("spawn.where_hint"), HINT) + 8;

        y = text(y, Ui.tr("spawn.loot_moved"), HINT) + 8;

        y = section(y, Ui.tr("egg.section"));
        IntSupplier eggColor = () -> eggSlot == 0 ? def.eggColor1 : def.eggColor2;
        IntConsumer setEggColor = c -> {
            if (eggSlot == 0) {
                def.eggColor1 = c & 0xFFFFFF;
            } else {
                def.eggColor2 = c & 0xFFFFFF;
            }
            markChanged();
        };
        int eggX = contentW - 32;
        add(new EggPreview(eggX, y));
        add(new EggSlots(0, y, eggX - 6));
        y += CHIP_H + 4;
        add(new Swatch(0, y, FIELD_H, eggColor));
        hexBox(FIELD_H + 4, y, 58, eggColor, setEggColor);
        y += ROW + 2;
        int pickerH = 50;
        add(new ColorPicker(0, y, contentW, pickerH, eggColor, setEggColor));
        y += pickerH + 5;
        Palette palette = add(new Palette(0, y, contentW, eggColor, setEggColor));
        y += palette.h + 8;
        return text(y, Ui.tr("editor.hint.spawn"), HINT);
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes para construir el panel
    // ---------------------------------------------------------------------------------------

    private <T extends Element> T add(T element) {
        element.x += buildIndent;
        elements.add(element);
        return element;
    }

    /** Sangria (en pixeles) de lo que se va anadiendo al panel, para las opciones de cada habilidad. */
    private int buildIndent;

    private void indent(int px) {
        buildIndent += px;
        contentW -= px;
    }

    private <T extends AbstractWidget> T panelWidget(T widget, int contentY) {
        panelWidgets.add(new PanelWidget(widget, contentY));
        addWidget(widget);
        return widget;
    }

    /** Ancho para las etiquetas de una pestana: el de la mas larga (maximo medio panel). */
    private int labelWidth(String... keys) {
        int w = 0;
        for (String key : keys) {
            w = Math.max(w, font.width(Ui.tr(key)));
        }
        return Math.min(contentW / 2, w + 6);
    }

    private int intro(int y) {
        return text(y, tab.description(), Ui.MUTED) + 8;
    }

    private int text(int y, Component text, int color) {
        TextBlock block = add(new TextBlock(0, y, contentW, text, color));
        return y + block.h;
    }

    private void label(int x, int y, Component text) {
        add(new TextLine(x, y + 4, () -> text, Ui.MUTED));
    }

    private int smallLabel(int y, Component text) {
        add(new TextLine(0, y, () -> text, Ui.MUTED));
        return y + 11;
    }

    private int section(int y, Component text) {
        return section(y, () -> text);
    }

    private int section(int y, Supplier<Component> text) {
        add(new Section(y, text));
        return y + 14;
    }

    private int noParts(int y) {
        y = text(y, Ui.tr("editor.no_parts_here"), Ui.TEXT) + 6;
        int half = (contentW - GAP) / 2;
        // en el modo facil no esta la pestana Piezas: se ofrece la de Cuerpo
        if (!easyMode) {
            button(0, y, half, Ui.tr("editor.go_parts"), b -> switchTab(Tab.PARTS), null);
        }
        button(easyMode ? 0 : half + GAP, y, easyMode ? contentW : contentW - half - GAP, Ui.tr("editor.go_body"),
                b -> switchTab(Tab.EASY), null);
        return y + ROW;
    }

    /** Fila para usar el raton: un boton para activarlo, o las herramientas de esta pestana. */
    private int mouseHelper(int y, Tool[] tools, String offKey) {
        if (!mouseMode) {
            y = text(y, Ui.tr(offKey), Ui.ACCENT) + 4;
            button(0, y, Math.min(contentW, 150), Ui.tr("tool.enable"), b -> setTool(tools[0]), null);
            return y + ROW + 6;
        }
        y = chips(y, tools, tools.length, () -> tool, this::setTool, Tool::title, Tool::help) + 4;
        return text(y, tool.help(), HINT) + 8;
    }

    /** Cambia el tamano de una pieza desde los campos (la pintura se adapta). */
    private void resize(CreaturePart p, float w, float h, float d) {
        float ow = p.w;
        float oh = p.h;
        float od = p.d;
        p.w = w;
        p.h = h;
        p.d = d;
        p.onResized(ow, oh, od);
    }

    private void clearAllPaint() {
        for (CreaturePart q : def.parts) {
            q.clearPaint();
        }
        markChanged();
    }

    private int paintTexels() {
        int total = 0;
        for (CreaturePart q : def.parts) {
            if (q.validPaint()) {
                total += q.paint.length;
            }
        }
        return total;
    }

    private int pieceSelector(int y) {
        int lw = labelWidth("editor.piece");
        label(0, y, Ui.tr("editor.piece"));
        add(new PieceSelector(lw, y, contentW - lw));
        return y + ROW + 6;
    }

    private int axisHeader(int y, int lw) {
        int fw = (contentW - lw - GAP * 2) / 3;
        String[] axes = {"X", "Y", "Z"};
        int[] colors = {0xFFFF9A9A, 0xFF9AFF9A, 0xFF9AC4FF};
        for (int i = 0; i < 3; i++) {
            Component axis = Component.literal(axes[i]);
            int ax = lw + i * (fw + GAP) + (fw - font.width(axis)) / 2;
            add(new TextLine(ax, y, () -> axis, colors[i]));
        }
        return y + 11;
    }

    private Button button(int x, int y, int w, Component text, Button.OnPress onPress, @Nullable Component tooltip) {
        Button.Builder builder = Button.builder(text, onPress).bounds(cx0() + x, sy(y), w, FIELD_H);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(tooltip));
        }
        return panelWidget(builder.build(), y);
    }

    private EditBox textBox(int x, int y, int w, int maxLength, String value, Consumer<String> responder,
                            @Nullable MutableComponent hint) {
        EditBox box = new EditBox(font, cx0() + x, sy(y), w, FIELD_H, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.moveCursorToStart(false);
        if (hint != null) {
            box.setHint(hint.withStyle(ChatFormatting.DARK_GRAY));
        }
        box.setResponder(s -> {
            if (!refreshing) {
                responder.accept(s);
            }
        });
        return panelWidget(box, y);
    }

    private NumberField number(int x, int y, int w, Supplier<Float> getter, Consumer<Float> setter,
                               float min, float max, float step, boolean integer) {
        NumberField field = new NumberField(font, cx0() + x, sy(y), w, FIELD_H, getter, setter, min, max, step,
                integer, this::markChanged);
        numberFields.add(field);
        return panelWidget(field, y);
    }

    private int numRow(int y, int lw, String key, Supplier<Float> getter, Consumer<Float> setter,
                       float min, float max, float step, boolean integer) {
        label(0, y, Ui.tr(key));
        number(lw, y, contentW - lw, getter, setter, min, max, step, integer);
        return y + ROW;
    }

    private int vecRow(int y, int lw, String key,
                       Supplier<Float> getX, Consumer<Float> setX,
                       Supplier<Float> getY, Consumer<Float> setY,
                       Supplier<Float> getZ, Consumer<Float> setZ,
                       float min, float max, float step) {
        int fw = (contentW - lw - GAP * 2) / 3;
        label(0, y, Ui.tr(key));
        number(lw, y, fw, getX, setX, min, max, step, false).setTextColor(0xFFFF9A9A);
        number(lw + fw + GAP, y, fw, getY, setY, min, max, step, false).setTextColor(0xFF9AFF9A);
        number(lw + (fw + GAP) * 2, y, contentW - lw - (fw + GAP) * 2, getZ, setZ, min, max, step, false)
                .setTextColor(0xFF9AC4FF);
        return y + ROW;
    }

    private <E> int chips(int y, E[] values, int columns, Supplier<E> getter, Consumer<E> setter,
                          Function<E, Component> name, @Nullable Function<E, Component> tip) {
        Chips<E> group = add(new Chips<>(0, y, contentW, values, columns, getter, setter, name, tip));
        return y + group.h;
    }

    /** Fichas en las que se pueden marcar varias a la vez (cada clic marca o desmarca). */
    private <E> int toggleChips(int y, E[] values, int columns, Predicate<E> isOn, Consumer<E> toggle,
                                Function<E, Component> name, @Nullable Function<E, Component> tip) {
        Chips<E> group = add(new Chips<>(0, y, contentW, values, columns, () -> null, toggle, name, tip));
        group.multi = isOn;
        return y + group.h;
    }

    private EditBox hexBox(int x, int y, int w, IntSupplier getter, IntConsumer setter) {
        EditBox box = textBox(x, y, w, 7, CreatureJson.hex(getter.getAsInt()), s -> {
            int c = CreatureJson.parseHex(s);
            if (c >= 0 && c != (getter.getAsInt() & 0xFFFFFF)) {
                setter.accept(c);
            }
        }, null);
        refreshers.add(() -> {
            if (!box.isFocused()) {
                String hex = CreatureJson.hex(getter.getAsInt());
                if (!hex.equalsIgnoreCase(box.getValue())) {
                    box.setValue(hex);
                }
            }
        });
        return box;
    }

    private static int itemColor(String id) {
        if (id == null || id.isBlank()) {
            return Ui.TEXT;
        }
        return resolveItem(id).isEmpty() ? Ui.ERROR : Ui.OK;
    }

    private static ItemStack resolveItem(String id) {
        String clean = CreatureDefinition.cleanResource(id);
        if (clean.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(clean);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    private Component parentLabel(CreaturePart p) {
        if (p.parent < 0 || p.parent >= def.parts.size()) {
            return Ui.tr("editor.field.parent_none");
        }
        return Component.literal(def.parts.get(p.parent).name);
    }

    private int parentColor(CreaturePart p) {
        return p.parent < 0 || p.parent >= def.parts.size() ? -1 : def.parts.get(p.parent).color;
    }

    private List<FormattedCharSequence> tip(Component text) {
        return font.split(text, 190);
    }

    // ---------------------------------------------------------------------------------------
    // Desplazamiento del panel
    // ---------------------------------------------------------------------------------------

    private int cx0() {
        return panelX + PAD + buildIndent;
    }

    private int sy(int contentY) {
        return viewTop + contentY - scroll();
    }

    private int scroll() {
        return tabScroll[tab.ordinal()];
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewBottom - viewTop));
    }

    private void setScroll(int value) {
        tabScroll[tab.ordinal()] = Mth.clamp(value, 0, maxScroll());
        for (PanelWidget pw : panelWidgets) {
            AbstractWidget w = pw.widget();
            int y = sy(pw.contentY());
            w.setY(y);
            w.visible = y + w.getHeight() > viewTop && y < viewBottom;
        }
    }

    private boolean inPanelView(double mx, double my) {
        return !panelCollapsed && mx >= panelX && mx < width && my >= viewTop && my < viewBottom;
    }

    private int trackX() {
        return width - SCROLLBAR - 1;
    }

    private int trackY() {
        return viewTop + 2;
    }

    private int trackH() {
        return Math.max(1, viewBottom - viewTop - 4);
    }

    private int barH() {
        return Mth.clamp(trackH() * (viewBottom - viewTop) / Math.max(1, contentHeight), 12, trackH());
    }

    private int barY() {
        int max = maxScroll();
        return trackY() + (max == 0 ? 0 : (trackH() - barH()) * scroll() / max);
    }

    private boolean scrollbarHit(double mx, double my) {
        return maxScroll() > 0 && mx >= trackX() - 2 && mx < width && my >= trackY() && my < trackY() + trackH();
    }

    private void dragScrollbar(double my) {
        int range = Math.max(1, trackH() - barH());
        int pos = (int) Math.round(my - scrollbarGrab - trackY());
        setScroll(pos * maxScroll() / range);
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

    /** Pasa a la pieza anterior o siguiente en el orden de la lista. */
    private void selectStep(int direction) {
        List<int[]> rows = displayRows();
        if (rows.isEmpty()) {
            return;
        }
        int row = 0;
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r)[0] == selected) {
                row = r;
                break;
            }
        }
        select(rows.get(Math.floorMod(row + direction, rows.size()))[0]);
    }

    private void switchTab(Tab newTab) {
        if (newTab == tab) {
            return;
        }
        endMouseAction();
        if (tool == Tool.POSE && newTab != Tab.ANIMATE) {
            tool = Tool.MOVE;
        } else if (newTab == Tab.ANIMATE && customAnimMode && mouseMode && !tool.paints()) {
            tool = Tool.POSE;
        }
        animPlay = false;
        tab = newTab;
        dropdown = null;
        activeElement = null;
        setFocused(null);
        requestRebuild();
    }

    private void addPart() {
        if (def.parts.size() >= CreatureDefinition.MAX_PARTS) {
            warn(Ui.tr("editor.too_many_parts", CreatureDefinition.MAX_PARTS));
            return;
        }
        CreaturePart p = CreaturePart.box(Ui.tr("editor.part.default_name", def.parts.size() + 1).getString(),
                -2, 0, -2, 4, 4, 4);
        p.color = PALETTE[(def.parts.size() * 5 + 4) % PALETTE.length];
        p.material = MaterialIds.SMOOTH;
        CreaturePart s = sel();
        if (s != null) {
            // Nuevo cubo encima de la pieza seleccionada y unido a ella.
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
            warn(Ui.tr("editor.too_many_parts", CreatureDefinition.MAX_PARTS));
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

    private void nudgeSelected(float dx, float dy, float dz) {
        // en la pestana Cuerpo la pieza del otro lado se mueve reflejada (simetria)
        int other = tab == Tab.EASY ? mirrorPartner() : -1;
        moveSubtree(selected, dx, dy, dz);
        if (other >= 0) {
            moveSubtree(other, -dx, dy, dz);
        }
        markChanged();
    }

    private void centerModel() {
        def.centerOnGround();
        preview.fit(def);
        markChanged();
    }

    private CreatureDefinition templateModel(int index) {
        if (templateModels == null) {
            templateModels = new CreatureDefinition[Templates.ALL.size()];
        }
        if (templateModels[index] == null) {
            templateModels[index] = Templates.ALL.get(index).create();
        }
        return templateModels[index];
    }

    private void applyTemplate(Templates.Template template) {
        CreatureDefinition fresh = template.create();
        fresh.id = def.id;
        fresh.owner = def.owner;
        fresh.revision = def.revision;
        Component templateName = Component.translatable("template.creaturestudio." + template.key());
        if (nameEdited || !isNew) {
            fresh.name = def.name;
        } else {
            fresh.name = templateName.getString();
        }
        def = fresh;
        selected = def.parts.isEmpty() ? -1 : 0;
        partsScroll = 0;
        preview.fit(def);
        markChanged();
        requestRebuild();
        showNotice(Ui.tr("templates.applied", templateName), true);
    }

    /** Reemplaza las piezas por las de un modelo de Minecraft (los demas ajustes se conservan). */
    private void importVanilla(VanillaModels.Entry entry) {
        CreatureDefinition src = VanillaModels.load(entry);
        if (src == null) {
            warn(Ui.tr("vanilla.failed"));
            return;
        }
        List<CreaturePart> parts = new ArrayList<>();
        for (CreaturePart p : src.parts) {
            parts.add(p.copy());
        }
        def.parts = parts;
        def.texW = src.texW;
        def.texH = src.texH;
        if (isNew && !nameEdited) {
            def.name = CreatureDefinition.cleanText(entry.name().getString(), CreatureDefinition.NAME_MAX);
        }
        selected = parts.isEmpty() ? -1 : 0;
        partsScroll = 0;
        preview.fit(def);
        markChanged();
        requestRebuild();
        showNotice(Ui.tr("vanilla.imported", entry.name()), true);
    }

    // ---------------------------------------------------------------------------------------
    // Modo facil
    // ---------------------------------------------------------------------------------------

    private void addBodyPart(BodyParts.Kind kind) {
        boolean wasEmpty = def.parts.isEmpty();
        BodyParts.Result r = BodyParts.add(def, kind, easyColor);
        Component name = Ui.tr("easy.part." + kind.key);
        if (r == null) {
            warn(Ui.tr("editor.too_many_parts", CreatureDefinition.MAX_PARTS));
            return;
        }
        if (r.index() >= 0 && r.index() < def.parts.size()) {
            selected = r.index();
            ensureVisible(selected);
        }
        if (r.created()) {
            if (wasEmpty) {
                preview.fit(def);
            }
            markChanged();
            showNotice(Ui.tr("easy.added", name), true);
        } else {
            showNotice(Ui.tr("easy.exists", name), true);
        }
        requestRebuild();
    }

    /** La pieza del otro lado de la elegida (si la simetria esta activada y no cuelga una de la otra), o -1. */
    private int mirrorPartner() {
        if (!symmetry) {
            return -1;
        }
        int other = BodyParts.partner(def, selected);
        if (other < 0 || PoseSolver.isDescendant(def.parts, other, selected)
                || PoseSolver.isDescendant(def.parts, selected, other)) {
            return -1;
        }
        return other;
    }

    /** Agranda o achica la pieza elegida con todo lo que cuelga de ella (y la del otro lado). */
    private void easyScale(float sx, float sy, float sz) {
        if (sel() == null) {
            return;
        }
        int other = mirrorPartner();
        float low = BodyParts.lowest(def);
        BodyParts.scaleGroup(def, selected, sx, sy, sz);
        if (other >= 0) {
            BodyParts.scaleGroup(def, other, sx, sy, sz);
        }
        BodyParts.ground(def, low);
        markChanged();
    }

    /** Mueve la pieza elegida 1 pixel (0,25 con Mayus); la del otro lado se mueve reflejada. */
    private void easyMove(float dx, float dy, float dz) {
        if (sel() == null) {
            return;
        }
        float step = hasShiftDown() ? 0.25f : 1f;
        int other = mirrorPartner();
        moveSubtree(selected, dx * step, dy * step, dz * step);
        if (other >= 0) {
            moveSubtree(other, -dx * step, dy * step, dz * step);
        }
        markChanged();
    }

    private void easyColor(int rgb) {
        CreaturePart p = sel();
        if (p == null) {
            return;
        }
        recolor(p, rgb);
        int other = symmetry ? BodyParts.partner(def, selected) : -1;
        if (other >= 0) {
            recolor(def.parts.get(other), rgb);
        }
        markChanged();
    }

    /** Cambia el color; una pieza invisible sin pintura pasa a verse lisa (si no, el color no se notaria). */
    private static void recolor(CreaturePart p, int rgb) {
        p.color = rgb & 0xFFFFFF;
        if (MaterialIds.CLEAR.equals(p.material) && !p.hasPaint() && p.w > 0f && p.h > 0f && p.d > 0f) {
            p.material = MaterialIds.SMOOTH;
        }
    }

    /** Quita la pintura (o skin) de la pieza para que se vean su color y su textura. */
    private void easyClearPaint() {
        CreaturePart p = sel();
        if (p == null) {
            return;
        }
        unpaint(p);
        int other = symmetry ? BodyParts.partner(def, selected) : -1;
        if (other >= 0) {
            unpaint(def.parts.get(other));
        }
        markChanged();
        requestRebuild();
    }

    private static void unpaint(CreaturePart p) {
        boolean wasPainted = p.hasPaint();
        p.clearPaint();
        if (wasPainted && MaterialIds.CLEAR.equals(p.material)) {
            p.material = MaterialIds.SMOOTH;
        }
    }

    private void easyMaterial(String material) {
        CreaturePart p = sel();
        if (p == null) {
            return;
        }
        p.material = material;
        int other = symmetry ? BodyParts.partner(def, selected) : -1;
        if (other >= 0) {
            def.parts.get(other).material = material;
        }
        markChanged();
    }

    /** Borra la pieza elegida (y la del otro lado si la simetria esta activada). */
    private void easyDelete() {
        int other = symmetry ? BodyParts.partner(def, selected) : -1;
        if (other >= 0 && other != selected) {
            int first = Math.max(selected, other);
            int second = Math.min(selected, other);
            selected = first;
            deleteSelected();
            selected = second;
            deleteSelected();
        } else {
            deleteSelected();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Skins (PNG)
    // ---------------------------------------------------------------------------------------

    private void openSkinPicker() {
        minecraft.setScreen(new SkinPickScreen(this, def.texW, def.texH, this::applySkin));
    }

    /** Pone la imagen elegida como skin de la criatura. */
    private void applySkin(String fileName, ClientFiles.Image image) {
        boolean laidOut = false;
        if (SkinLayout.mapped(def) == 0) {
            // La criatura no viene de un modelo de Minecraft: se usa el reparto de su propia plantilla
            // (el mismo que al exportar la skin).
            SkinLayout.autoLayout(def);
            laidOut = true;
        }
        int painted = SkinLayout.apply(def, image.argb(), image.width(), image.height());
        if (painted == 0) {
            warn(Ui.tr("skin.no_parts"));
            requestRebuild();
            return;
        }
        markChanged();
        requestRebuild();
        int left = SkinLayout.unmapped(def);
        if (laidOut) {
            showNotice(Ui.tr("skin.applied_layout", fileName), true);
        } else if (left > 0) {
            showNotice(Ui.tr("skin.applied_some", fileName, left), true);
        } else {
            showNotice(Ui.tr("skin.applied", fileName), true);
        }
    }

    /** Guarda la skin de la criatura como PNG (repartiendo antes las piezas que no tienen sitio). */
    private void exportSkin() {
        int placed = SkinLayout.autoLayout(def);
        if (def.texW <= 0 || def.texH <= 0 || SkinLayout.mapped(def) == 0) {
            warn(Ui.tr("skin.no_parts"));
            return;
        }
        if (placed > 0) {
            markChanged();
        }
        try {
            String name = def.name.isBlank() ? def.id : def.name;
            Path file = ClientFiles.writeSkin(name, SkinLayout.export(def), def.texW, def.texH);
            showNotice(Ui.tr("skin.exported", file.getFileName().toString()), true);
        } catch (IOException e) {
            warn(Ui.tr("skin.failed_write"));
        }
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
        int visible = partsList != null ? partsList.visibleRows() : 8;
        if (row < partsScroll) {
            partsScroll = row;
        } else if (row >= partsScroll + visible) {
            partsScroll = row - visible + 1;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Desplegables
    // ---------------------------------------------------------------------------------------

    private void openDropdown(Element anchor, List<DropItem> items, IntConsumer onPick) {
        if (items.isEmpty()) {
            return;
        }
        dropdown = new Dropdown(anchor.left(), anchor.top(), anchor.w, anchor.h, items, onPick);
        setFocused(null);
    }

    private void openPartsDropdown(Element anchor) {
        List<int[]> rows = displayRows();
        List<DropItem> items = new ArrayList<>();
        for (int[] row : rows) {
            CreaturePart p = def.parts.get(row[0]);
            items.add(new DropItem(Component.literal(p.name), p.color, row[1], row[0] == selected));
        }
        openDropdown(anchor, items, i -> select(rows.get(i)[0]));
    }

    private void openParentDropdown(Element anchor) {
        CreaturePart p = sel();
        if (p == null) {
            return;
        }
        List<Integer> options = new ArrayList<>();
        List<DropItem> items = new ArrayList<>();
        options.add(-1);
        items.add(new DropItem(Ui.tr("editor.field.parent_none"), -1, 0, p.parent < 0));
        for (int[] row : displayRows()) {
            int j = row[0];
            if (j != selected && !PoseSolver.isDescendant(def.parts, j, selected)) {
                CreaturePart q = def.parts.get(j);
                options.add(j);
                items.add(new DropItem(Component.literal(q.name), q.color, row[1], p.parent == j));
            }
        }
        openDropdown(anchor, items, i -> {
            p.parent = options.get(i);
            markChanged();
        });
    }

    private void openSoundDropdown(Element anchor) {
        SoundPreset[] values = SoundPreset.values();
        List<DropItem> items = new ArrayList<>();
        for (SoundPreset s : values) {
            items.add(new DropItem(Ui.tr("sound." + s.key()), -1, 0, s == def.sound));
        }
        openDropdown(anchor, items, i -> {
            def.sound = values[i];
            markChanged();
            playSound();
        });
    }

    // =======================================================================================
    // Deshacer / guardar
    // =======================================================================================

    private void markChanged() {
        String now = CreatureJson.write(def, false);
        if (!now.equals(committed)) {
            dirty = true;
            paintVersion++;
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
        refreshFields();
    }

    /** Vuelve a leer los valores en los campos de texto que el usuario no esta editando. */
    private void refreshFields() {
        refreshing = true;
        try {
            for (NumberField f : numberFields) {
                f.refresh();
            }
            for (Runnable r : refreshers) {
                r.run();
            }
        } finally {
            refreshing = false;
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            warn(Ui.tr("editor.nothing_to_undo"));
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
        paintVersion++;
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
        if (data.length > ModNetwork.MAX_TOTAL_UPLOAD) {
            showNotice(Ui.tr("editor.too_big"), false);
            return;
        }
        ModNetwork.sendSave(data);
        dirty = false;
        isNew = false;
        showNotice(Ui.tr("editor.saving"), true);
    }

    /** Aviso en rojo de algo que no se pudo hacer aqui (no cambia la criatura: no la marca sin guardar). */
    private void warn(Component message) {
        status = message;
        statusColor = Ui.ERROR;
        statusUntil = Util.getMillis() + 3500;
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

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private AnimState currentAnim(CreatureDefinition shown) {
        long now = Util.getMillis();
        float t = animate ? (now % 3_600_000L) / 50f : 0f;
        float elapsed = (now - attackStart) / 350f;
        float attack = elapsed >= 0f && elapsed < 1f ? elapsed : 0f;
        boolean flyer = shown.movement == Movement.FLY;
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
        g.fill(0, 0, width, HEADER_H, Ui.PANEL_DARK);
        g.hLine(0, width, HEADER_H - 1, Ui.BORDER);
        g.fillGradient(preview.x, preview.y, preview.x + preview.w, preview.y + preview.h,
                Ui.PREVIEW_TOP, Ui.PREVIEW_BOTTOM);
        boolean showTemplate = hoveredTemplate >= 0;
        boolean showVanilla = !showTemplate && hoveredVanillaDef != null;
        boolean editing = !showTemplate && !showVanilla;
        CreatureDefinition shown = showTemplate ? templateModel(hoveredTemplate)
                : showVanilla ? hoveredVanillaDef : def;
        Preview3D view = editing ? preview : templatePreview;
        PaintAtlas paint;
        if (editing) {
            paint = editorPaint();
        } else {
            templatePreview.yaw = 0.6f + (Util.getMillis() % 62832L) * 0.0006f;
            paint = showVanilla ? previewPaintFor(shown) : null;
        }
        AnimState anim = editing ? editorAnim() : currentAnim(shown);
        if (editing) {
            updateVoxelCursor(mouseX, mouseY);
        } else {
            preview.cursorPart = -1;
        }
        int outline = editing && tab.usesPart() && !(mouseMode && tool.paints()) ? selected : -1;
        view.render(g, shown, anim, outline, true, paint);
        if (editing && mouseMode) {
            renderMouseOverlay(g, mouseX, mouseY);
        }
        g.fill(panelX, HEADER_H, width, height, panelCollapsed ? Ui.PANEL_DARK : Ui.PANEL);
        g.vLine(panelX, HEADER_H - 1, height, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildWidgets();
        }
        updateTemplateHover(mouseX, mouseY);
        boolean blocked = dropdown != null && dropdown.inside(mouseX, mouseY);
        int mx = blocked ? -10000 : mouseX;
        int my = blocked ? -10000 : mouseY;
        if (undoButton != null) {
            undoButton.active = !undoStack.isEmpty();
        }
        if (redoButton != null) {
            redoButton.active = !redoStack.isEmpty();
        }
        super.render(g, mx, my, partialTick);
        renderViewChips(g, mx, my);

        renderHeader(g);
        renderTabs(g, mx, my);
        if (!panelCollapsed) {
            renderPanel(g, mx, my, partialTick);
        }
        renderPreviewOverlay(g);
        if (resizingPanel) {
            g.fill(resizeX - 1, HEADER_H, resizeX + 1, height, Ui.ACCENT);
        } else if (dividerHit(mx, my) && !dragging && gizmoHandle < 0 && !painting && !posing) {
            g.fill(panelX - 1, HEADER_H, panelX + 1, height, 0xFF8A93A6);
        }

        if (dropdown != null) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 400f);
            dropdown.render(g, mouseX, mouseY);
            g.pose().popPose();
        } else if (activeElement == null && !dragging && !draggingScrollbar && !resizingPanel
                && gizmoHandle < 0 && !painting && !posing) {
            renderTabTooltip(g, mx, my);
            renderElementTooltip(g, mx, my);
            renderViewChipTooltip(g, mx, my);
            if (helpCut && helpText != null && Ui.inside(mx, my, helpX, helpY - 1, helpW, 10)) {
                g.renderTooltip(font, font.split(helpText, 240), mx, my);
            } else if (dividerHit(mx, my)) {
                g.renderTooltip(font, tip(Ui.tr("editor.panel.resize")), mx, my);
            }
        }
    }

    private void renderViewChips(GuiGraphics g, int mx, int my) {
        for (ViewChip c : viewChips) {
            if (c.action() == null) {
                g.drawString(font, c.label(), c.x() + 5, c.y() + 3, Ui.TEXT, true);
                continue;
            }
            boolean hover = Ui.inside(mx, my, c.x(), c.y(), c.w(), 13);
            chip(g, c.x(), c.y(), c.w(), 13, c.label(), c.chosen().getAsBoolean(), hover);
            if (c.swatch()) {
                g.fill(c.x() + 3, c.y() + 3, c.x() + c.w() - 3, c.y() + 10, 0xFF000000 | brushColor);
            }
        }
    }

    private void renderViewChipTooltip(GuiGraphics g, int mx, int my) {
        for (ViewChip c : viewChips) {
            if (c.tip() != null && Ui.inside(mx, my, c.x(), c.y(), c.w(), 13)) {
                g.renderTooltip(font, tip(c.tip()), mx, my);
                return;
            }
        }
    }

    @Nullable
    private PaintAtlas editorPaint() {
        if (paintBuilt != paintVersion) {
            paintShown = editorPaint.rebuild(def);
            paintBuilt = paintVersion;
        }
        return paintShown ? editorPaint : null;
    }

    @Nullable
    private PaintAtlas previewPaintFor(CreatureDefinition d) {
        if (previewPaintDef != d) {
            previewPaintShown = previewPaint.rebuild(d);
            previewPaintDef = d;
        }
        return previewPaintShown ? previewPaint : null;
    }

    @Override
    public void removed() {
        editorPaint.close();
        previewPaint.close();
        paintBuilt = 0;
        previewPaintDef = null;
        super.removed();
    }

    private void updateTemplateHover(int mouseX, int mouseY) {
        hoveredTemplate = -1;
        hoveredVanilla = null;
        boolean over = tab == Tab.TEMPLATE && dropdown == null && inPanelView(mouseX, mouseY);
        if (over && templateGrid != null) {
            hoveredTemplate = templateGrid.indexAt(mouseX, mouseY);
        }
        if (over && hoveredTemplate < 0 && vanillaList != null) {
            hoveredVanilla = vanillaList.entryAt(mouseX, mouseY);
        }
        hoveredVanillaDef = hoveredVanilla == null ? null : VanillaModels.load(hoveredVanilla);
        if (hoveredTemplate >= 0 && hoveredTemplate != fittedTemplate) {
            templatePreview.fit(templateModel(hoveredTemplate));
            fittedTemplate = hoveredTemplate;
            fittedVanilla = null;
        } else if (hoveredVanilla != null && hoveredVanillaDef != null && !hoveredVanilla.id().equals(fittedVanilla)) {
            templatePreview.fit(hoveredVanillaDef);
            fittedVanilla = hoveredVanilla.id();
            fittedTemplate = -1;
        }
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(font, Ui.tr("editor.name_label"), nameLabelX, 6, Ui.MUTED, false);
        if (dirty) {
            Component unsaved = Ui.tr("editor.unsaved");
            if (unsavedX + font.width(unsaved) <= headerRight - 6) {
                g.drawString(font, unsaved, unsavedX, 6, UNSAVED, false);
            }
            if (saveButton != null) {
                int sx = saveButton.getX();
                int sy = saveButton.getY();
                g.fill(sx + 2, sy + 2, sx + 5, sy + 5, UNSAVED);
            }
        }
    }

    private int stripLeft() {
        return panelX + 1 + FOLD_W;
    }

    private int tabCellW() {
        return Math.max(1, (width - stripLeft()) / visibleTabs().length);
    }

    /**
     * Que hay debajo del raton en la fila (o columna, con el panel plegado) de pestanas: el indice de la pestana
     * en {@link #visibleTabs()}, -2 = boton de plegar/desplegar el panel, -1 = nada.
     */
    /** Alto de cada casilla de la columna de pestanas plegada (mas bajas si no caben todas). */
    private int stripCell() {
        int rows = visibleTabs().length + 1;
        return Math.max(16, Math.min(STRIP_CELL, (height - HEADER_H) / rows));
    }

    private int tabAt(double mx, double my) {
        Tab[] visible = visibleTabs();
        if (mx < panelX || mx >= width || my < HEADER_H) {
            return -1;
        }
        if (panelCollapsed) {
            int row = (int) ((my - HEADER_H) / stripCell());
            if (row == 0) {
                return -2;
            }
            return row - 1 < visible.length ? row - 1 : -1;
        }
        if (my >= HEADER_H + TAB_H) {
            return -1;
        }
        if (mx < stripLeft()) {
            return -2;
        }
        return Math.min(visible.length - 1, (int) ((mx - stripLeft()) / tabCellW()));
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        Tab[] visible = visibleTabs();
        int hovered = tabAt(mx, my);
        if (panelCollapsed) {
            int x = panelX + 1;
            int w = width - x;
            boolean hf = hovered == -2;
            int cell = stripCell();
            if (hf) {
                g.fill(x, HEADER_H, width, HEADER_H + cell, 0xFF2A2E38);
            }
            g.drawCenteredString(font, "\u00AB", x + w / 2, HEADER_H + (cell - 8) / 2, hf ? 0xFFFFFFFF : Ui.MUTED);
            for (int i = 0; i < visible.length; i++) {
                int y = HEADER_H + (i + 1) * cell;
                boolean active = visible[i] == tab;
                if (active) {
                    g.fill(x, y, width, y + cell, Ui.PANEL);
                    g.fill(x, y, x + 2, y + cell, Ui.ACCENT);
                } else if (hovered == i) {
                    g.fill(x, y, width, y + cell, 0xFF2A2E38);
                }
                g.renderItem(visible[i].icon(), x + (w - 16) / 2, y + (cell - 16) / 2);
            }
            return;
        }
        int top = HEADER_H;
        g.fill(panelX + 1, top, width, top + TAB_H, 0xFF17191E);
        boolean hf = hovered == -2;
        if (hf) {
            g.fill(panelX + 1, top, stripLeft(), top + TAB_H, 0xFF2A2E38);
        }
        g.drawString(font, "\u00BB", panelX + 4, top + 6, hf ? 0xFFFFFFFF : Ui.MUTED, false);
        int cw = tabCellW();
        for (int i = 0; i < visible.length; i++) {
            Tab t = visible[i];
            int x = stripLeft() + i * cw;
            int w = i == visible.length - 1 ? width - x : cw;
            boolean active = t == tab;
            if (active) {
                g.fill(x, top, x + w, top + TAB_H, Ui.PANEL);
                g.fill(x, top, x + w, top + 2, Ui.ACCENT);
            } else if (hovered == i) {
                g.fill(x, top, x + w, top + TAB_H, 0xFF2A2E38);
            }
            g.renderItem(t.icon(), x + (w - 16) / 2, top + 2);
        }
        g.hLine(panelX + 1, width, top + TAB_H, Ui.BORDER);
        // titulo de la pestana
        int ty = top + TAB_H + 1;
        g.fill(panelX + 1, ty, width, viewTop, Ui.PANEL);
        String title = tab.title().getString();
        int maxTitle = Math.max(10, panelW - PAD * 2);
        Component bold = Component.literal(title).withStyle(ChatFormatting.BOLD);
        while (title.length() > 1 && font.width(bold) > maxTitle) {
            title = title.substring(0, title.length() - 1);
            bold = Component.literal(title).withStyle(ChatFormatting.BOLD);
        }
        g.drawString(font, bold, panelX + PAD, ty + 3, 0xFFFFFFFF, false);
        g.hLine(panelX + PAD, width - PAD, viewTop - 1, 0xFF30343E);
    }

    private void renderPanel(GuiGraphics g, int mx, int my, float partialTick) {
        boolean inView = inPanelView(mx, my) && !draggingScrollbar;
        int emx = inView ? mx : -10000;
        int emy = inView ? my : -10000;
        g.enableScissor(panelX + 1, viewTop, width, viewBottom);
        for (Element e : elements) {
            if (e.onScreen()) {
                e.render(g, emx, emy);
            }
        }
        for (PanelWidget pw : panelWidgets) {
            if (pw.widget().visible) {
                pw.widget().render(g, emx, emy, partialTick);
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int x = trackX();
            g.fill(x, trackY(), x + SCROLLBAR, trackY() + trackH(), 0xFF1A1C21);
            int by = barY();
            int color = draggingScrollbar ? 0xFFA9B3C6 : (scrollbarHit(mx, my) ? 0xFF8A93A6 : 0xFF5E6575);
            g.fill(x, by, x + SCROLLBAR, by + barH(), color);
        }
    }

    private void renderPreviewOverlay(GuiGraphics g) {
        Component help = mouseMode ? tool.help()
                : Ui.tr(tab.usesPart() ? "editor.preview.help" : "editor.preview.help_behavior");
        int avail = Math.max(20, preview.w - 8);
        String full = help.getString();
        String line = full;
        helpCut = font.width(full) > avail;
        if (helpCut) {
            line = font.plainSubstrByWidth(full, Math.max(10, avail - font.width("..."))) + "...";
        }
        int hy = preview.y + preview.h - 12;
        g.drawString(font, line, preview.x + 4, hy, 0xFF8A919E, false);
        helpText = help;
        helpX = preview.x + 4;
        helpY = hy;
        helpW = font.width(line);
        int toastY = hy - 17;
        if (hoveredTemplate >= 0) {
            Component name = Component.translatable("template.creaturestudio." + Templates.ALL.get(hoveredTemplate).key());
            toast(g, Ui.tr("templates.previewing", name), Ui.ACCENT, toastY);
            toastY -= 18;
        } else if (hoveredVanilla != null) {
            boolean ok = hoveredVanillaDef != null;
            toast(g, ok ? Ui.tr("templates.previewing", hoveredVanilla.name()) : Ui.tr("vanilla.failed"),
                    ok ? Ui.ACCENT : Ui.ERROR, toastY);
            toastY -= 18;
        }
        if (status != null && Util.getMillis() < statusUntil) {
            toast(g, status, statusColor, toastY);
        }
    }

    private void toast(GuiGraphics g, Component text, int color, int y) {
        String s = font.plainSubstrByWidth(text.getString(), Math.max(10, preview.w - 20));
        int sw = font.width(s) + 12;
        int sx = preview.x + (preview.w - sw) / 2;
        g.fill(sx, y, sx + sw, y + 14, 0xD0101216);
        g.renderOutline(sx, y, sw, 14, color);
        g.drawString(font, s, sx + 6, y + 3, color, false);
    }

    private void renderTabTooltip(GuiGraphics g, int mx, int my) {
        int i = tabAt(mx, my);
        if (i == -2) {
            g.renderTooltip(font, tip(Ui.tr(panelCollapsed ? "editor.panel.expand" : "editor.panel.collapse")), mx, my);
            return;
        }
        if (i < 0) {
            return;
        }
        Tab t = visibleTabs()[i];
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(t.title().withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        lines.addAll(font.split(t.description(), 190));
        if (i < 10) {
            lines.add(Ui.tr("editor.tab_key", (i + 1) % 10).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        g.renderTooltip(font, lines, mx, my);
    }

    private void renderElementTooltip(GuiGraphics g, int mx, int my) {
        if (!inPanelView(mx, my)) {
            return;
        }
        for (Element e : elements) {
            if (e.onScreen() && e.hit(mx, my)) {
                List<FormattedCharSequence> lines = e.tooltip(mx, my);
                if (lines != null && !lines.isEmpty()) {
                    g.renderTooltip(font, lines, mx, my);
                    return;
                }
            }
        }
    }

    /** Recuadro con borde (flechas del selector de piezas, desplegables). */
    private void box(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, hover ? 0xFF3A404D : 0xFF2B2F38);
        g.renderOutline(x, y, w, h, hover ? 0xFF8A93A6 : Ui.BORDER);
    }

    /** Opcion seleccionable: la elegida se resalta en azul. */
    private void chip(GuiGraphics g, int x, int y, int w, int h, Component text, boolean chosen, boolean hover) {
        int bg = chosen ? 0xFF2D5A8A : hover ? 0xFF3A404D : 0xFF2E323C;
        int border = chosen ? Ui.ACCENT : hover ? 0xFF7A8294 : 0xFF464C59;
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        String s = font.plainSubstrByWidth(text.getString(), w - 6);
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, chosen ? 0xFFFFFFFF : Ui.TEXT, false);
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 255) / 255f;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 255) / 255f;
    }

    private static float blue(int rgb) {
        return (rgb & 255) / 255f;
    }

    // =======================================================================================
    // Entrada
    // =======================================================================================

    private boolean textFocused() {
        return getFocused() instanceof EditBox box && box.isFocused();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdown != null) {
            if (dropdown.inside(mouseX, mouseY)) {
                if (button == 0) {
                    dropdown.click(mouseX, mouseY);
                }
            } else {
                dropdown = null;
            }
            return true;
        }
        int t = tabAt(mouseX, mouseY);
        if (t != -1) {
            if (button == 0) {
                if (t == -2) {
                    setPanelCollapsed(!panelCollapsed);
                } else {
                    Tab target = visibleTabs()[t];
                    setPanelCollapsed(false);
                    switchTab(target);
                }
                playClick();
            }
            return true;
        }
        if (button == 0 && dividerHit(mouseX, mouseY)) {
            resizingPanel = true;
            resizeX = panelX;
            setFocused(null);
            return true;
        }
        for (ViewChip c : viewChips) {
            if (c.action() != null && Ui.inside(mouseX, mouseY, c.x(), c.y(), c.w(), 13)) {
                if (button == 0) {
                    c.action().run();
                    playClick();
                }
                return true;
            }
        }
        if (button == 0 && scrollbarHit(mouseX, mouseY)) {
            int by = barY();
            if (mouseY >= by && mouseY < by + barH()) {
                scrollbarGrab = mouseY - by;
            } else {
                scrollbarGrab = barH() / 2.0;
                dragScrollbar(mouseY);
            }
            draggingScrollbar = true;
            setFocused(null);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (inPanelView(mouseX, mouseY)) {
            setFocused(null);
            if (button == 0) {
                for (Element e : elements) {
                    if (e.onScreen() && e.hit(mouseX, mouseY) && e.click(mouseX, mouseY, button)) {
                        activeElement = e;
                        return true;
                    }
                }
            }
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            setFocused(null);
            if (mouseMode && button == 0 && startMouseAction(mouseX, mouseY)) {
                return true;
            }
            dragging = true;
            dragButton = button;
            pressX = mouseX;
            pressY = mouseY;
            moved = false;
            return true;
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dropdown != null) {
            return true;
        }
        if (resizingPanel) {
            resizeX = Mth.clamp((int) Math.round(mouseX), width - maxPanel(), width - MIN_PANEL);
            return true;
        }
        if (draggingScrollbar) {
            dragScrollbar(mouseY);
            return true;
        }
        if (activeElement != null) {
            activeElement.drag(mouseX, mouseY);
            return true;
        }
        if (gizmoHandle >= 0) {
            updateGizmo(mouseX, mouseY);
            return true;
        }
        if (posing) {
            updatePose(mouseX, mouseY);
            return true;
        }
        if (painting) {
            strokeTo(mouseX, mouseY);
            return true;
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
        if (resizingPanel) {
            resizingPanel = false;
            EditorSettings.panelWidth = width - resizeX;
            EditorSettings.save();
            requestRebuild();
            return true;
        }
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (activeElement != null) {
            activeElement.release();
            activeElement = null;
            return true;
        }
        if ((gizmoHandle >= 0 || painting || posing) && button == 0) {
            endMouseAction();
            return true;
        }
        if (dragging && button == dragButton) {
            dragging = false;
            if (!moved && button == 0 && tab.usesPart()) {
                int hit = preview.pick(def, editorAnim(), mouseX, mouseY);
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
        if (dropdown != null) {
            if (dropdown.inside(mouseX, mouseY)) {
                dropdown.scrollBy(scrollY);
            }
            return true;
        }
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (inPanelView(mouseX, mouseY)) {
            // La rueda solo cambia un numero si ese campo esta activo (se ha hecho clic en el);
            // si no, desplaza el panel.
            for (NumberField f : numberFields) {
                if (f.visible && f.isFocused() && f.isMouseOver(mouseX, mouseY)) {
                    f.nudge(Math.signum(scrollY));
                    return true;
                }
            }
            for (Element e : elements) {
                if (e.onScreen() && e.hit(mouseX, mouseY) && e.scrolled(scrollY)) {
                    return true;
                }
            }
            setScroll(scroll() - (int) Math.round(scrollY * 24));
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            // como en AutoCAD: se acerca o aleja hacia donde esta el raton
            preview.zoomAt(scrollY, mouseX, mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dropdown != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                dropdown = null;
            }
            return true;
        }
        if (hasControlDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_Z -> {
                    if (hasShiftDown()) {
                        redo();
                    } else {
                        undo();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    redo();
                    return true;
                }
                case GLFW.GLFW_KEY_S -> {
                    save();
                    return true;
                }
                case GLFW.GLFW_KEY_D -> {
                    if (!textFocused() && tab.usesPart()) {
                        duplicateSelected(false);
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        if (!textFocused() && !hasControlDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_M -> {
                    setMouseMode(!mouseMode);
                    return true;
                }
                case GLFW.GLFW_KEY_H -> {
                    setPanelCollapsed(!panelCollapsed);
                    return true;
                }
                case GLFW.GLFW_KEY_F -> {
                    preview.fit(def);
                    return true;
                }
                case GLFW.GLFW_KEY_G -> {
                    setTool(Tool.MOVE);
                    return true;
                }
                case GLFW.GLFW_KEY_T -> {
                    setTool(Tool.RESIZE);
                    return true;
                }
                case GLFW.GLFW_KEY_B -> {
                    setTool(Tool.BRUSH);
                    return true;
                }
                case GLFW.GLFW_KEY_E -> {
                    setTool(Tool.ERASER);
                    return true;
                }
                case GLFW.GLFW_KEY_I -> {
                    setTool(Tool.PICKER);
                    return true;
                }
                case GLFW.GLFW_KEY_P -> {
                    setTool(Tool.POSE);
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_KEY_MINUS -> {
                    changeBrush(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_EQUAL -> {
                    changeBrush(1);
                    return true;
                }
                default -> {
                }
            }
        }
        if (!textFocused()) {
            Tab[] visible = visibleTabs();
            int number = keyCode == GLFW.GLFW_KEY_0 ? 9 : keyCode - GLFW.GLFW_KEY_1;
            if (!hasControlDown() && (keyCode == GLFW.GLFW_KEY_0 || keyCode >= GLFW.GLFW_KEY_1)
                    && number >= 0 && number < Math.min(visible.length, 10) && keyCode <= GLFW.GLFW_KEY_9) {
                setPanelCollapsed(false);
                switchTab(visible[number]);
                return true;
            }
            if (tab.usesPart() && sel() != null) {
                float step = hasShiftDown() ? 0.25f : (hasControlDown() ? 4f : 1f);
                switch (keyCode) {
                    case GLFW.GLFW_KEY_DELETE -> {
                        if (tab == Tab.EASY) {
                            easyDelete();
                        } else {
                            deleteSelected();
                        }
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
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (dropdown != null) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (dropdown != null) {
            dropdown = null;
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
    // Raton: mover, estirar y pintar sobre la vista 3D
    // =======================================================================================

    private Matrix4f[] restMats() {
        return CreatureModelRenderer.pose(def, posingView() ? pinnedAnim() : AnimState.REST);
    }

    private static float[] boxCenter(CreaturePart p) {
        return new float[]{p.x + p.w / 2f, p.y + p.h / 2f, p.z + p.d / 2f};
    }

    private static float[] faceCenter(CreaturePart p, int face) {
        float[] c = boxCenter(p);
        boolean max = FACE_SIGN[face] > 0;
        switch (FACE_AXIS[face]) {
            case 0 -> c[0] = max ? p.x + p.w : p.x;
            case 1 -> c[1] = max ? p.y + p.h : p.y;
            default -> c[2] = max ? p.z + p.d : p.z;
        }
        return c;
    }

    /** Matriz del padre: las coordenadas de la pieza se mueven en ese espacio. */
    private Matrix4f parentMatrix(Matrix4f[] mats, int index) {
        int parent = PoseSolver.safeParents(def.parts)[index];
        return parent >= 0 && parent < mats.length ? mats[parent] : new Matrix4f();
    }

    /** Cuanto avanza en pantalla (x, y y profundidad) un pixel del modelo en el eje dado. */
    private static float[] screenAxis(Matrix4f view, Matrix4f m, float[] at, int axis) {
        float[] a = Preview3D.project(view, m, at[0], at[1], at[2]);
        float[] b = Preview3D.project(view, m, at[0] + (axis == 0 ? 1f : 0f), at[1] + (axis == 1 ? 1f : 0f),
                at[2] + (axis == 2 ? 1f : 0f));
        return new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]};
    }

    private static float len2d(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1]);
    }

    /** Punta de cada flecha de mover (null si ese eje apunta hacia la camara). */
    private float[][] moveArrows(Matrix4f view, Matrix4f[] mats, CreaturePart p, float[] origin) {
        float[] c = boxCenter(p);
        Matrix4f pm = parentMatrix(mats, selected);
        float[][] tips = new float[3][];
        for (int k = 0; k < 3; k++) {
            float[] v = screenAxis(view, pm, c, k);
            float len = len2d(v);
            if (len > 0.05f) {
                tips[k] = new float[]{origin[0] + v[0] / len * 32f, origin[1] + v[1] / len * 32f};
            }
        }
        return tips;
    }

    /** true si la cara mira hacia la camara (en la vista, mas profundidad = mas cerca). */
    private boolean faceVisible(Matrix4f view, Matrix4f[] mats, CreaturePart p, int face) {
        float[] v = screenAxis(view, mats[selected], faceCenter(p, face), FACE_AXIS[face]);
        return v[2] * FACE_SIGN[face] > 0f;
    }

    /** Asa bajo el raton (ver {@link #gizmoHandle}) o -1. */
    private int handleAt(double mx, double my, Matrix4f view, Matrix4f[] mats) {
        CreaturePart p = sel();
        if (p == null || selected >= mats.length) {
            return -1;
        }
        if (tool == Tool.MOVE) {
            float[] c = boxCenter(p);
            float[] o = Preview3D.project(view, mats[selected], c[0], c[1], c[2]);
            double toCenter = Math.hypot(mx - o[0], my - o[1]);
            float[][] tips = moveArrows(view, mats, p, o);
            for (int k = 0; k < 3; k++) {
                if (tips[k] != null && toCenter > 7 && distToSegment(mx, my, o, tips[k]) < 4.5) {
                    return k;
                }
            }
            return toCenter <= 7 ? 3 : -1;
        }
        if (tool == Tool.RESIZE) {
            int best = -1;
            double bestD = 6.5;
            for (int f = 0; f < PaintLayout.FACES; f++) {
                if (!faceVisible(view, mats, p, f)) {
                    continue;
                }
                float[] fc = faceCenter(p, f);
                float[] h = Preview3D.project(view, mats[selected], fc[0], fc[1], fc[2]);
                double d = Math.hypot(mx - h[0], my - h[1]);
                if (d < bestD) {
                    bestD = d;
                    best = 10 + f;
                }
            }
            return best;
        }
        return -1;
    }

    private static double distToSegment(double px, double py, float[] a, float[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double l2 = dx * dx + dy * dy;
        double t = l2 < 1.0e-6 ? 0 : Mth.clamp(((px - a[0]) * dx + (py - a[1]) * dy) / l2, 0.0, 1.0);
        return Math.hypot(px - (a[0] + t * dx), py - (a[1] + t * dy));
    }

    /** Empieza a usar la herramienta en la vista 3D. false = no hay nada debajo (se gira la camara). */
    private boolean startMouseAction(double mx, double my) {
        Matrix4f view = preview.viewMatrix();
        Matrix4f[] mats = restMats();
        switch (tool) {
            case MOVE, RESIZE -> {
                if (sel() != null) {
                    int handle = handleAt(mx, my, view, mats);
                    if (handle >= 0) {
                        beginGizmo(handle, mx, my, view, mats);
                        return true;
                    }
                }
                Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
                if (hit == null) {
                    return false;
                }
                if (hit.part() != selected) {
                    select(hit.part());
                }
                if (tool == Tool.MOVE) {
                    beginGizmo(3, mx, my, view, mats);
                }
                return true;
            }
            case PICKER -> {
                return voxelMode && tab == Tab.PAINT ? pickVoxelColor(mx, my, mats) : pickColor(mx, my, mats);
            }
            case POSE -> {
                Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
                if (hit == null) {
                    return false;
                }
                if (hit.part() != selected) {
                    select(hit.part());
                }
                if (posingView()) {
                    animPlay = false;
                    beginPose(mx, my, view, mats);
                }
                return true;
            }
            default -> {
                boolean voxels = voxelMode && tab == Tab.PAINT;
                if (preview.pickHit(def, mats, mx, my, null, voxels && (tool == Tool.ERASER || voxelRecolor)) == null) {
                    return false;
                }
                painting = true;
                strokeChanged = false;
                strokeVoxels.clear();
                paintLastX = mx;
                paintLastY = my;
                if (voxels) {
                    voxelAt(mx, my, mats);
                } else {
                    paintAt(mx, my, mats);
                }
                return true;
            }
        }
    }

    private void beginGizmo(int handle, double mx, double my, Matrix4f view, Matrix4f[] mats) {
        CreaturePart p = sel();
        if (p == null || selected >= mats.length) {
            return;
        }
        gizmoHandle = handle;
        gizmoX0 = mx;
        gizmoY0 = my;
        gizmoApplied[0] = 0f;
        gizmoApplied[1] = 0f;
        gizmoApplied[2] = 0f;
        gizmoStart = p.copy();
        float[] c = boxCenter(p);
        if (handle < 3) {
            gizmoA = screenAxis(view, parentMatrix(mats, selected), c, handle);
            gizmoAxisA = handle;
        } else if (handle == 3) {
            Matrix4f pm = parentMatrix(mats, selected);
            float[][] axes = {screenAxis(view, pm, c, 0), screenAxis(view, pm, c, 1), screenAxis(view, pm, c, 2)};
            // Los dos ejes que mas se ven en pantalla forman el plano en el que se arrastra la pieza.
            int skip = 0;
            for (int k = 1; k < 3; k++) {
                if (len2d(axes[k]) < len2d(axes[skip])) {
                    skip = k;
                }
            }
            gizmoAxisA = skip == 0 ? 1 : 0;
            gizmoAxisB = skip == 2 ? 1 : 2;
            gizmoA = axes[gizmoAxisA];
            gizmoB = axes[gizmoAxisB];
        } else {
            int face = handle - 10;
            float[] v = screenAxis(view, mats[selected], faceCenter(p, face), FACE_AXIS[face]);
            int sign = FACE_SIGN[face];
            gizmoA = new float[]{v[0] * sign, v[1] * sign, v[2] * sign};
            gizmoAxisA = FACE_AXIS[face];
        }
    }

    private static float snap(float v, float step) {
        return Math.round(v / step) * step;
    }

    private void updateGizmo(double mx, double my) {
        CreaturePart p = sel();
        CreaturePart s0 = gizmoStart;
        if (p == null || s0 == null) {
            return;
        }
        float dx = (float) (mx - gizmoX0);
        float dy = (float) (my - gizmoY0);
        float step = hasShiftDown() ? 0.25f : 1f;
        if (gizmoHandle <= 3) {
            float[] want = new float[3];
            if (gizmoHandle < 3) {
                float l2 = gizmoA[0] * gizmoA[0] + gizmoA[1] * gizmoA[1];
                if (l2 < 1.0e-4f) {
                    return;
                }
                want[gizmoAxisA] = snap((dx * gizmoA[0] + dy * gizmoA[1]) / l2, step);
            } else {
                float det = gizmoA[0] * gizmoB[1] - gizmoA[1] * gizmoB[0];
                if (Math.abs(det) < 1.0e-4f) {
                    return;
                }
                want[gizmoAxisA] = snap((dx * gizmoB[1] - dy * gizmoB[0]) / det, step);
                want[gizmoAxisB] = snap((gizmoA[0] * dy - gizmoA[1] * dx) / det, step);
            }
            moveSubtree(selected, want[0] - gizmoApplied[0], want[1] - gizmoApplied[1], want[2] - gizmoApplied[2]);
            System.arraycopy(want, 0, gizmoApplied, 0, 3);
        } else {
            float l2 = gizmoA[0] * gizmoA[0] + gizmoA[1] * gizmoA[1];
            if (l2 < 1.0e-4f) {
                return;
            }
            float s = snap((dx * gizmoA[0] + dy * gizmoA[1]) / l2, step);
            int face = gizmoHandle - 10;
            boolean positive = FACE_SIGN[face] > 0;
            float size0 = gizmoAxisA == 0 ? s0.w : gizmoAxisA == 1 ? s0.h : s0.d;
            s = Mth.clamp(s, -size0, 256f - size0);
            p.x = s0.x;
            p.y = s0.y;
            p.z = s0.z;
            p.w = s0.w;
            p.h = s0.h;
            p.d = s0.d;
            p.paint = s0.paint == null ? null : s0.paint.clone();
            p.pw = s0.pw;
            p.ph = s0.ph;
            p.pd = s0.pd;
            switch (gizmoAxisA) {
                case 0 -> {
                    p.w = size0 + s;
                    if (!positive) {
                        p.x = s0.x - s;
                    }
                }
                case 1 -> {
                    p.h = size0 + s;
                    if (!positive) {
                        p.y = s0.y - s;
                    }
                }
                default -> {
                    p.d = size0 + s;
                    if (!positive) {
                        p.z = s0.z - s;
                    }
                }
            }
            p.onResized(s0.w, s0.h, s0.d);
            paintVersion++;
        }
        dirty = true;
        refreshFields();
    }

    /** Termina el arrastre o el trazo actual (un solo paso para deshacer). */
    private void endMouseAction() {
        boolean changed = gizmoHandle >= 0 || (painting && strokeChanged) || (posing && poseChanged);
        posing = false;
        poseChanged = false;
        gizmoHandle = -1;
        gizmoStart = null;
        painting = false;
        strokeChanged = false;
        strokeVoxels.clear();
        if (changed) {
            markChanged();
        }
    }

    /** Texel bajo el punto de impacto: {cara, i, j, ancho de la cara, alto de la cara}, o null. */
    @Nullable
    private int[] texelAt(Preview3D.Hit hit) {
        if (hit.face() < 0 || hit.part() >= def.parts.size()) {
            return null;
        }
        CreaturePart p = def.parts.get(hit.part());
        int pw = p.validPaint() ? p.pw : CreaturePart.paintDim(p.w);
        int ph = p.validPaint() ? p.ph : CreaturePart.paintDim(p.h);
        int pd = p.validPaint() ? p.pd : CreaturePart.paintDim(p.d);
        int fw = PaintLayout.faceW(hit.face(), pw, pd);
        int fh = PaintLayout.faceH(hit.face(), ph, pd);
        float[] st = PaintLayout.st(hit.face(), hit.x(), hit.y(), hit.z(),
                p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d);
        int i = Mth.clamp((int) Math.floor(st[0] * fw), 0, fw - 1);
        int j = Mth.clamp((int) Math.floor(st[1] * fh), 0, fh - 1);
        return new int[]{hit.face(), i, j, fw, fh};
    }

    private void paintAt(double mx, double my, Matrix4f[] mats) {
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
        if (hit == null) {
            return;
        }
        int[] tx = texelAt(hit);
        if (tx == null) {
            return;
        }
        CreaturePart p = def.parts.get(hit.part());
        boolean erase = tool == Tool.ERASER;
        if (!p.validPaint()) {
            if (erase) {
                return;
            }
            int pd = CreaturePart.paintDim(p.d);
            int need = PaintLayout.imageW(CreaturePart.paintDim(p.w), pd) * PaintLayout.imageH(CreaturePart.paintDim(p.h), pd);
            if (paintTexels() + need > CreatureDefinition.MAX_PAINT_TEXELS) {
                warn(Ui.tr("paint.too_much"));
                return;
            }
            p.ensurePaint();
        }
        int color = erase ? 0 : 0xFF000000 | (brushColor & 0xFFFFFF);
        int i0 = tx[1] - (brushSize - 1) / 2;
        int j0 = tx[2] - (brushSize - 1) / 2;
        boolean changed = false;
        for (int j = j0; j < j0 + brushSize; j++) {
            for (int i = i0; i < i0 + brushSize; i++) {
                if (i >= 0 && j >= 0 && i < tx[3] && j < tx[4] && p.texel(tx[0], i, j) != color) {
                    p.setTexel(tx[0], i, j, color);
                    changed = true;
                }
            }
        }
        if (changed) {
            strokeChanged = true;
            dirty = true;
            paintVersion++;
        }
    }

    /** Pinta a lo largo del movimiento del raton para que el trazo no tenga huecos. */
    private void strokeTo(double mx, double my) {
        Matrix4f[] mats = restMats();
        double dx = mx - paintLastX;
        double dy = my - paintLastY;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / 2.0));
        boolean voxels = voxelMode && tab == Tab.PAINT;
        for (int k = 1; k <= steps; k++) {
            if (voxels) {
                voxelAt(paintLastX + dx * k / steps, paintLastY + dy * k / steps, mats);
            } else {
                paintAt(paintLastX + dx * k / steps, paintLastY + dy * k / steps, mats);
            }
        }
        paintLastX = mx;
        paintLastY = my;
    }

    /** Cuentagotas: copia el color pintado (o el color de la pieza) y vuelve al pincel. */
    private boolean pickColor(double mx, double my, Matrix4f[] mats) {
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
        if (hit == null) {
            return false;
        }
        CreaturePart p = def.parts.get(hit.part());
        int[] tx = texelAt(hit);
        int c = tx == null ? 0 : p.texel(tx[0], tx[1], tx[2]);
        if ((c >>> 24) == 0) {
            c = p.color;
        }
        brushColor = 0xFF000000 | (c & 0xFFFFFF);
        showNotice(Ui.tr("tool.picked", CreatureJson.hex(brushColor)), true);
        setTool(Tool.BRUSH);
        return true;
    }

    /** Flechas, asas o el cuadrado del pincel encima de la vista 3D. */
    private void renderMouseOverlay(GuiGraphics g, int mx, int my) {
        Matrix4f view = preview.viewMatrix();
        Matrix4f[] mats = restMats();
        g.enableScissor(preview.x, preview.y, preview.x + preview.w, preview.y + preview.h);
        CreaturePart p = sel();
        if ((tool == Tool.MOVE || tool == Tool.RESIZE) && p != null && selected < mats.length) {
            int hover = gizmoHandle >= 0 ? gizmoHandle : (preview.contains(mx, my) ? handleAt(mx, my, view, mats) : -1);
            if (tool == Tool.MOVE) {
                float[] c = boxCenter(p);
                float[] o = Preview3D.project(view, mats[selected], c[0], c[1], c[2]);
                float[][] tips = moveArrows(view, mats, p, o);
                for (int k = 0; k < 3; k++) {
                    if (tips[k] != null) {
                        int color = hover == k ? 0xFFFFFFFF : AXIS_COLORS[k];
                        line(g, o[0], o[1], tips[k][0], tips[k][1], hover == k ? 3 : 2, color);
                        square(g, tips[k][0], tips[k][1], 3, color);
                    }
                }
                square(g, o[0], o[1], hover == 3 ? 4 : 3, hover == 3 ? 0xFFFFFFFF : 0xFFFFD84A);
            } else {
                for (int f = 0; f < PaintLayout.FACES; f++) {
                    if (hover != 10 + f && !faceVisible(view, mats, p, f)) {
                        continue;
                    }
                    float[] fc = faceCenter(p, f);
                    float[] h = Preview3D.project(view, mats[selected], fc[0], fc[1], fc[2]);
                    float[] v = screenAxis(view, mats[selected], fc, FACE_AXIS[f]);
                    float len = len2d(v);
                    int color = hover == 10 + f ? 0xFFFFFFFF : AXIS_COLORS[FACE_AXIS[f]];
                    if (len > 0.05f) {
                        float ex = h[0] + FACE_SIGN[f] * v[0] / len * 10f;
                        float ey = h[1] + FACE_SIGN[f] * v[1] / len * 10f;
                        line(g, h[0], h[1], ex, ey, 2, color);
                    }
                    square(g, h[0], h[1], hover == 10 + f ? 4 : 3, color);
                }
            }
        } else if (tool == Tool.POSE && posingView() && !animPlay && p != null && selected < mats.length
                && def.customAnims.has(animAction)) {
            float[] c = Preview3D.project(view, mats[selected], p.px, p.py, p.pz);
            if (posing) {
                line(g, c[0], c[1], mx, my, 1, 0xC0FFD84A);
            }
            square(g, c[0], c[1], posing ? 4 : 3, 0xFFFFD84A);
        } else if ((tool == Tool.BRUSH || tool == Tool.ERASER) && !(voxelMode && tab == Tab.PAINT)
                && preview.contains(mx, my) && !dragging) {
            Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
            int[] tx = hit == null ? null : texelAt(hit);
            if (hit != null && tx != null) {
                CreaturePart q = def.parts.get(hit.part());
                int i0 = Mth.clamp(tx[1] - (brushSize - 1) / 2, 0, tx[3]);
                int i1 = Mth.clamp(tx[1] - (brushSize - 1) / 2 + brushSize, 0, tx[3]);
                int j0 = Mth.clamp(tx[2] - (brushSize - 1) / 2, 0, tx[4]);
                int j1 = Mth.clamp(tx[2] - (brushSize - 1) / 2 + brushSize, 0, tx[4]);
                float[] fr = PaintLayout.frame(tx[0], q.x, q.y, q.z, q.x + q.w, q.y + q.h, q.z + q.d);
                float[][] st = {
                        {i0 / (float) tx[3], j0 / (float) tx[4]}, {i1 / (float) tx[3], j0 / (float) tx[4]},
                        {i1 / (float) tx[3], j1 / (float) tx[4]}, {i0 / (float) tx[3], j1 / (float) tx[4]}
                };
                float[][] pts = new float[4][];
                for (int k = 0; k < 4; k++) {
                    float s = st[k][0];
                    float t = st[k][1];
                    pts[k] = Preview3D.project(view, mats[hit.part()], fr[0] + fr[3] * s + fr[6] * t,
                            fr[1] + fr[4] * s + fr[7] * t, fr[2] + fr[5] * s + fr[8] * t);
                }
                int color = tool == Tool.ERASER ? 0xFFFF6060 : 0xFFFFFFFF;
                for (int k = 0; k < 4; k++) {
                    line(g, pts[k][0], pts[k][1], pts[(k + 1) % 4][0], pts[(k + 1) % 4][1], 1, color);
                }
            }
        }
        g.disableScissor();
    }

    /** Linea de pantalla hecha con cuadraditos (no hace falta nada mas que fill). */
    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        int a = thickness / 2;
        for (int k = 0; k <= steps; k++) {
            int px = Math.round(x0 + dx * k / steps) - a;
            int py = Math.round(y0 + dy * k / steps) - a;
            g.fill(px, py, px + thickness, py + thickness, color);
        }
    }

    private static void square(GuiGraphics g, float cx, float cy, int half, int color) {
        int x = Math.round(cx);
        int y = Math.round(cy);
        g.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, 0xFF101216);
        g.fill(x - half, y - half, x + half, y + half, color);
    }

    // =======================================================================================
    // Elementos dibujados a mano dentro del panel (coordenadas del contenido)
    // =======================================================================================

    private abstract class Element {
        protected int x;
        protected int y;
        protected int w;
        protected int h;

        Element(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        int left() {
            return panelX + PAD + x; // la sangria ya esta sumada en x (ver add)
        }

        int top() {
            return sy(y);
        }

        boolean hit(double mx, double my) {
            return Ui.inside(mx, my, left(), top(), w, h);
        }

        boolean onScreen() {
            int t = top();
            return t + h > viewTop && t < viewBottom;
        }

        abstract void render(GuiGraphics g, int mx, int my);

        boolean click(double mx, double my, int button) {
            return false;
        }

        void drag(double mx, double my) {
        }

        void release() {
        }

        boolean scrolled(double amount) {
            return false;
        }

        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return null;
        }
    }

    /** Texto partido en varias lineas. */
    private final class TextBlock extends Element {
        private final List<FormattedCharSequence> lines;
        private final int color;

        TextBlock(int x, int y, int w, Component text, int color) {
            super(x, y, w, 0);
            this.lines = font.split(text, Math.max(10, w));
            this.h = lines.size() * 10;
            this.color = color;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int ty = top();
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, left(), ty, color, false);
                ty += 10;
            }
        }
    }

    /** Una linea de texto (puede cambiar mientras se edita). */
    private final class TextLine extends Element {
        private final Supplier<Component> text;
        private final int color;

        TextLine(int x, int y, Supplier<Component> text, int color) {
            super(x, y, 0, 9);
            this.text = text;
            this.color = color;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Component c = text.get();
            int max = Math.max(10, contentW - x);
            if (font.width(c) <= max) {
                g.drawString(font, c, left(), top(), color, false);
            } else {
                g.drawString(font, font.plainSubstrByWidth(c.getString(), max), left(), top(), color, false);
            }
        }
    }

    /** Titulo de seccion con una linea a la derecha. */
    private final class Section extends Element {
        private final Supplier<Component> text;

        Section(int y, Supplier<Component> text) {
            super(0, y, contentW, 12);
            this.text = text;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Component t = text.get();
            int l = left();
            int ty = top() + 2;
            g.drawString(font, t, l, ty, Ui.TEXT, false);
            int lineX = l + font.width(t) + 5;
            if (lineX < l + w) {
                g.hLine(lineX, l + w - 1, ty + 4, Ui.BORDER);
            }
        }
    }

    /** Cuadrado con el color actual. */
    private final class Swatch extends Element {
        private final IntSupplier color;

        Swatch(int x, int y, int size, IntSupplier color) {
            super(x, y, size, size);
            this.color = color;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Ui.swatch(g, left(), top(), w, color.getAsInt(), false);
        }
    }

    /** [\u25C0] [\u25A0 nombre \u25BE] [\u25B6]: cambia la pieza que se esta editando. */
    private final class PieceSelector extends Element {
        PieceSelector(int x, int y, int w) {
            super(x, y, w, FIELD_H);
        }

        /** 0 = anterior, 1 = elegir de la lista, 2 = siguiente. */
        private int zone(double mx) {
            double rel = mx - left();
            if (rel < 16) {
                return 0;
            }
            return rel >= w - 16 ? 2 : 1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            int z = hit(mx, my) ? zone(mx) : -1;
            box(g, l, t, 14, h, z == 0);
            g.drawCenteredString(font, "\u25C0", l + 7, t + 4, z == 0 ? 0xFFFFFFFF : Ui.MUTED);
            box(g, l + w - 14, t, 14, h, z == 2);
            g.drawCenteredString(font, "\u25B6", l + w - 7, t + 4, z == 2 ? 0xFFFFFFFF : Ui.MUTED);
            int bx = l + 16;
            int bw = w - 32;
            box(g, bx, t, bw, h, z == 1);
            CreaturePart p = sel();
            if (p != null) {
                Ui.swatch(g, bx + 4, t + 4, 8, p.color, false);
                String name = font.plainSubstrByWidth(p.name, Math.max(10, bw - 28));
                g.drawString(font, name, bx + 16, t + 4, Ui.TEXT, false);
            }
            g.drawString(font, "\u25BE", bx + bw - 9, t + 4, Ui.MUTED, false);
        }

        @Override
        boolean click(double mx, double my, int button) {
            switch (zone(mx)) {
                case 0 -> selectStep(-1);
                case 2 -> selectStep(1);
                default -> openPartsDropdown(this);
            }
            playClick();
            return true;
        }

        @Override
        List<FormattedCharSequence> tooltip(double mx, double my) {
            String key = switch (zone(mx)) {
                case 0 -> "editor.piece_prev";
                case 2 -> "editor.piece_next";
                default -> "editor.piece_pick";
            };
            return tip(Ui.tr(key));
        }
    }

    /** Boton que abre una lista desplegable. */
    private final class DropButton extends Element {
        private final Supplier<Component> text;
        private final IntSupplier color;
        @Nullable
        private final Component tip;
        private final Consumer<Element> open;

        DropButton(int x, int y, int w, Supplier<Component> text, IntSupplier color, @Nullable Component tip,
                   Consumer<Element> open) {
            super(x, y, w, FIELD_H);
            this.text = text;
            this.color = color;
            this.tip = tip;
            this.open = open;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            box(g, l, t, w, h, hit(mx, my));
            int tx = l + 4;
            int c = color.getAsInt();
            if (c >= 0) {
                Ui.swatch(g, tx, t + 4, 8, c, false);
                tx += 12;
            }
            String s = font.plainSubstrByWidth(text.get().getString(), Math.max(10, l + w - 12 - tx));
            g.drawString(font, s, tx, t + 4, Ui.TEXT, false);
            g.drawString(font, "\u25BE", l + w - 9, t + 4, Ui.MUTED, false);
        }

        @Override
        boolean click(double mx, double my, int button) {
            open.accept(this);
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return tip == null ? null : tip(tip);
        }
    }

    /** Grupo de opciones: una sola elegida, resaltada. columns = 0 coloca las opciones seguidas. */
    private final class Chips<E> extends Element {
        private final E[] values;
        private final Supplier<E> getter;
        private final Consumer<E> setter;
        private final Function<E, Component> name;
        @Nullable
        private final Function<E, Component> tipFor;
        private final int[] rx;
        private final int[] ry;
        private final int[] rw;
        /** Si no es null, se pueden marcar varias: cada clic llama al setter, que la marca o desmarca. */
        @Nullable
        Predicate<E> multi;

        Chips(int x, int y, int w, E[] values, int columns, Supplier<E> getter, Consumer<E> setter,
              Function<E, Component> name, @Nullable Function<E, Component> tipFor) {
            super(x, y, w, 0);
            this.values = values;
            this.getter = getter;
            this.setter = setter;
            this.name = name;
            this.tipFor = tipFor;
            int n = values.length;
            rx = new int[n];
            ry = new int[n];
            rw = new int[n];
            if (columns > 0) {
                int cw = (w - GAP * (columns - 1)) / columns;
                for (int i = 0; i < n; i++) {
                    int col = i % columns;
                    rx[i] = col * (cw + GAP);
                    ry[i] = (i / columns) * (CHIP_H + 2);
                    rw[i] = col == columns - 1 ? w - rx[i] : cw;
                }
                h = ((n + columns - 1) / columns) * (CHIP_H + 2) - 2;
            } else {
                int cx = 0;
                int cy = 0;
                for (int i = 0; i < n; i++) {
                    int cw = Math.min(w, font.width(name.apply(values[i])) + 12);
                    if (cx > 0 && cx + cw > w) {
                        cx = 0;
                        cy += CHIP_H + 2;
                    }
                    rx[i] = cx;
                    ry[i] = cy;
                    rw[i] = cw;
                    cx += cw + GAP;
                }
                h = cy + CHIP_H;
            }
        }

        private int indexAt(double mx, double my) {
            for (int i = 0; i < values.length; i++) {
                if (Ui.inside(mx, my, left() + rx[i], top() + ry[i], rw[i], CHIP_H)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            E current = getter.get();
            int hovered = indexAt(mx, my);
            for (int i = 0; i < values.length; i++) {
                boolean on = multi != null ? multi.test(values[i]) : Objects.equals(values[i], current);
                chip(g, left() + rx[i], top() + ry[i], rw[i], CHIP_H, name.apply(values[i]), on, i == hovered);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            if (multi != null || !Objects.equals(values[i], getter.get())) {
                setter.accept(values[i]);
                markChanged();
            }
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            int i = indexAt(mx, my);
            return tipFor == null || i < 0 ? null : tip(tipFor.apply(values[i]));
        }
    }

    /** Casilla de verificacion con texto. model = false si no cambia la criatura (opciones de la vista). */
    /** Casilla de una habilidad con el huevo del mob al lado (como el Toggle, pero mas alto y con icono). */
    private final class AbilityToggle extends Element {
        private final ItemStack icon;
        private final Component label;
        private final Component tip;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        AbilityToggle(int x, int y, int w, ItemStack icon, Component label, Component tip, BooleanSupplier getter,
                      Consumer<Boolean> setter) {
            super(x, y, w, ROW);
            this.icon = icon;
            this.label = label;
            this.tip = tip;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            boolean hover = hit(mx, my);
            boolean on = getter.getAsBoolean();
            g.fill(l, t, l + w, t + h, on ? 0x2A6FA8FF : (hover ? 0x20FFFFFF : 0x10FFFFFF));
            g.renderItem(icon, l + 1, t + 1);
            int bx = l + 20;
            g.fill(bx, t + 4, bx + 10, t + 14, 0xFF1A1C21);
            g.renderOutline(bx, t + 4, 10, 10, on ? Ui.ACCENT : (hover ? 0xFF8A93A6 : 0xFF5A6070));
            if (on) {
                g.fill(bx + 2, t + 6, bx + 8, t + 12, Ui.ACCENT);
            }
            String s = font.plainSubstrByWidth(label.getString(), Math.max(10, w - 36));
            g.drawString(font, s, l + 34, t + 5, on ? 0xFFFFFFFF : Ui.TEXT, false);
        }

        @Override
        boolean click(double mx, double my, int button) {
            setter.accept(!getter.getAsBoolean());
            markChanged();
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(label.copy().withStyle(ChatFormatting.BOLD).getVisualOrderText());
            lines.addAll(font.split(tip, 220));
            return lines;
        }
    }

    /** Titulo con icono (para las opciones que no se encienden con una casilla, como las puertas). */
    private final class AbilityHeader extends Element {
        private final ItemStack icon;
        private final Component label;
        private final Component tip;

        AbilityHeader(int x, int y, int w, ItemStack icon, Component label, Component tip) {
            super(x, y, w, ROW);
            this.icon = icon;
            this.label = label;
            this.tip = tip;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            g.fill(l, t, l + w, t + h, 0x10FFFFFF);
            g.renderItem(icon, l + 1, t + 1);
            String s = font.plainSubstrByWidth(label.getString(), Math.max(10, w - 22));
            g.drawString(font, s, l + 20, t + 5, Ui.TEXT, false);
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return font.split(tip, 220);
        }
    }

    /** Barra que se arrastra para cambiar un valor (Mayus: saltos grandes). */
    private final class ValueSlider extends Element {
        private final int labelW;
        private final Component label;
        private final int color;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final float min;
        private final float max;
        private final float step;
        private final float bigStep;
        @Nullable
        private final Component tip;
        private boolean held;
        private boolean changed;

        ValueSlider(int x, int y, int w, int labelW, Component label, int color, Supplier<Float> getter,
                    Consumer<Float> setter, float min, float max, float step, float bigStep, @Nullable Component tip) {
            super(x, y, w, CHIP_H);
            this.labelW = labelW;
            this.label = label;
            this.color = color;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
            this.bigStep = bigStep;
            this.tip = tip;
        }

        private int trackL() {
            return left() + labelW;
        }

        private int trackW() {
            return Math.max(10, w - labelW - 3);
        }

        private int xOf(float v) {
            return trackL() + Math.round((Mth.clamp(v, min, max) - min) / (max - min) * (trackW() - 1));
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            int tl = trackL();
            int tw = trackW();
            boolean hover = held || hit(mx, my);
            g.drawString(font, label, l, t + 3, color, false);
            int cy = t + h / 2;
            g.fill(tl, cy - 1, tl + tw, cy + 1, hover ? 0xFF8A93A6 : 0xFF4A505C);
            if (min < 0f && max > 0f) {
                int zx = xOf(0f);
                g.fill(zx, cy - 3, zx + 1, cy + 4, 0xFF8A93A6);
                int kx = xOf(getter.get());
                g.fill(Math.min(zx, kx), cy - 1, Math.max(zx, kx) + 1, cy + 1, color);
            }
            int kx = xOf(getter.get());
            g.fill(kx - 2, t + 2, kx + 3, t + h - 2, 0xFF101216);
            g.fill(kx - 1, t + 3, kx + 2, t + h - 3, held ? Ui.ACCENT : (hover ? 0xFFFFFFFF : 0xFFD0D4DC));
        }

        @Override
        boolean click(double mx, double my, int button) {
            if (mx < trackL() - 4) {
                return false;
            }
            held = true;
            changed = false;
            drag(mx, my);
            return true;
        }

        @Override
        void drag(double mx, double my) {
            if (!held) {
                return;
            }
            float f = (float) ((mx - trackL()) / Math.max(1, trackW() - 1));
            float v = min + Mth.clamp(f, 0f, 1f) * (max - min);
            float st = hasShiftDown() ? bigStep : step;
            v = Mth.clamp(Math.round(v / st) * st, min, max);
            v = Math.round(v * 1000f) / 1000f; // lo mismo que se ve en el numero
            if (v != getter.get()) {
                setter.accept(v);
                changed = true;
                refreshFields();
            }
        }

        @Override
        void release() {
            held = false;
            if (changed) {
                markChanged();
            }
            changed = false;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return tip == null ? null : tip(tip);
        }
    }

    private final class Toggle extends Element {
        private final Component label;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        @Nullable
        private final Component tip;
        private final boolean model;

        Toggle(int x, int y, int w, Component label, BooleanSupplier getter, Consumer<Boolean> setter,
               @Nullable Component tip, boolean model) {
            super(x, y, w, CHIP_H);
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.tip = tip;
            this.model = model;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            boolean hover = hit(mx, my);
            boolean on = getter.getAsBoolean();
            if (hover) {
                g.fill(l, t, l + w, t + h, 0x20FFFFFF);
            }
            g.fill(l + 2, t + 2, l + 12, t + 12, 0xFF1A1C21);
            g.renderOutline(l + 2, t + 2, 10, 10, on ? Ui.ACCENT : (hover ? 0xFF8A93A6 : 0xFF5A6070));
            if (on) {
                g.fill(l + 4, t + 4, l + 10, t + 10, Ui.ACCENT);
            }
            String s = font.plainSubstrByWidth(label.getString(), Math.max(10, w - 18));
            g.drawString(font, s, l + 16, t + 3, on ? 0xFFFFFFFF : Ui.TEXT, false);
        }

        @Override
        boolean click(double mx, double my, int button) {
            setter.accept(!getter.getAsBoolean());
            if (model) {
                markChanged();
            }
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return tip == null ? null : tip(tip);
        }
    }

    /** Selector de color HSV: cuadrado de saturacion/brillo y barra de tono. Se arrastra. */
    private final class ColorPicker extends Element {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private float hue;
        private float sat;
        private float val;
        private int known = -1;
        private boolean dragSv;
        private boolean dragHue;

        ColorPicker(int x, int y, int w, int h, IntSupplier getter, IntConsumer setter) {
            super(x, y, w, h);
            this.getter = getter;
            this.setter = setter;
            sync();
        }

        private int svW() {
            return w - 14;
        }

        private void sync() {
            int c = getter.getAsInt() & 0xFFFFFF;
            if (c != known) {
                float[] hsv = Ui.rgbToHsv(c);
                if (hsv[1] > 0.02f && hsv[2] > 0.02f) {
                    hue = hsv[0]; // en grises se conserva el tono elegido
                }
                sat = hsv[1];
                val = hsv[2];
                known = c;
            }
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            sync();
            int l = left();
            int t = top();
            int sw = svW();
            for (int i = 0; i < sw; i += 2) {
                int topColor = 0xFF000000 | Ui.hsvToRgb(hue, (i + 1) / (float) sw, 1f);
                g.fillGradient(l + i, t, Math.min(l + i + 2, l + sw), t + h, topColor, 0xFF000000);
            }
            g.renderOutline(l - 1, t - 1, sw + 2, h + 2, 0xFF000000);
            int cx = l + Math.round(sat * (sw - 1));
            int cy = t + Math.round((1f - val) * (h - 1));
            g.renderOutline(cx - 2, cy - 2, 5, 5, val > 0.6f && sat < 0.4f ? 0xFF000000 : 0xFFFFFFFF);
            int hx = l + sw + 4;
            for (int k = 0; k < 6; k++) {
                int c1 = 0xFF000000 | Ui.hsvToRgb(k / 6f, 1f, 1f);
                int c2 = 0xFF000000 | Ui.hsvToRgb((k + 1) / 6f, 1f, 1f);
                g.fillGradient(hx, t + k * h / 6, hx + 10, t + (k + 1) * h / 6, c1, c2);
            }
            g.renderOutline(hx - 1, t - 1, 12, h + 2, 0xFF000000);
            int hy = t + Math.round(hue * (h - 1));
            g.fill(hx - 2, hy - 1, hx + 12, hy + 1, 0xFFFFFFFF);
        }

        @Override
        boolean click(double mx, double my, int button) {
            int l = left();
            int t = top();
            int sw = svW();
            if (Ui.inside(mx, my, l, t, sw, h)) {
                dragSv = true;
            } else if (Ui.inside(mx, my, l + sw + 2, t, 14, h)) {
                dragHue = true;
            } else {
                return false;
            }
            drag(mx, my);
            return true;
        }

        @Override
        void drag(double mx, double my) {
            int l = left();
            int t = top();
            int sw = svW();
            if (dragSv) {
                sat = Mth.clamp((float) (mx - l) / Math.max(1, sw - 1), 0f, 1f);
                val = 1f - Mth.clamp((float) (my - t) / Math.max(1, h - 1), 0f, 1f);
            } else if (dragHue) {
                hue = Mth.clamp((float) (my - t) / Math.max(1, h - 1), 0f, 0.999f);
            } else {
                return;
            }
            int rgb = Ui.hsvToRgb(hue, sat, val);
            known = rgb;
            if (rgb != (getter.getAsInt() & 0xFFFFFF)) {
                setter.accept(rgb);
            }
        }

        @Override
        void release() {
            dragSv = false;
            dragHue = false;
        }
    }

    /** Colores rapidos. */
    private final class Palette extends Element {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int perRow;

        Palette(int x, int y, int w, IntSupplier getter, IntConsumer setter) {
            super(x, y, w, 0);
            this.getter = getter;
            this.setter = setter;
            this.perRow = Math.max(1, (w + 2) / 14);
            this.h = ((PALETTE.length + perRow - 1) / perRow) * 14 - 2;
        }

        private int indexAt(double mx, double my) {
            for (int i = 0; i < PALETTE.length; i++) {
                if (Ui.inside(mx, my, left() + (i % perRow) * 14, top() + (i / perRow) * 14, 12, 12)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int current = getter.getAsInt() & 0xFFFFFF;
            int hovered = indexAt(mx, my);
            for (int i = 0; i < PALETTE.length; i++) {
                Ui.swatch(g, left() + (i % perRow) * 14, top() + (i / perRow) * 14, 12, PALETTE[i],
                        i == hovered || PALETTE[i] == current);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            setter.accept(PALETTE[i]);
            playClick();
            return true;
        }
    }

    /** Todas las texturas, tenidas con el color de la pieza. */
    private final class TextureGrid extends Element {
        private final CreaturePart part;
        private final int cols;
        /** Cuantas texturas se ensenan (las primeras de la lista). */
        private final int count;
        /** Que hacer al elegir una textura (null = ponersela a la pieza). */
        @Nullable
        private final Consumer<String> onPick;

        TextureGrid(int x, int y, int w, CreaturePart part) {
            this(x, y, w, part, null, Materials.PRESETS.size());
        }

        TextureGrid(int x, int y, int w, CreaturePart part, @Nullable Consumer<String> onPick, int limit) {
            super(x, y, w, 0);
            this.part = part;
            this.onPick = onPick;
            this.count = Math.max(1, Math.min(limit, Materials.PRESETS.size()));
            this.cols = Math.max(1, w / CELL);
            this.h = ((count + cols - 1) / cols) * CELL;
        }

        private int indexAt(double mx, double my) {
            if (!hit(mx, my)) {
                return -1;
            }
            int c = (int) ((mx - left()) / CELL);
            int r = (int) ((my - top()) / CELL);
            int i = r * cols + c;
            return c < cols && i < count ? i : -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Function<ResourceLocation, TextureAtlasSprite> atlas = Materials.atlas();
            float r = red(part.color);
            float gg = green(part.color);
            float b = blue(part.color);
            String current = CreatureDefinition.cleanMaterial(part.material);
            int hovered = indexAt(mx, my);
            for (int i = 0; i < count; i++) {
                Materials.Preset preset = Materials.PRESETS.get(i);
                int cx = left() + (i % cols) * CELL;
                int cy = top() + (i / cols) * CELL;
                g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1A1C21);
                g.blit(cx + 1, cy + 1, 0, 16, 16, Materials.sprite(atlas, preset.id()), r, gg, b, 1f);
                if (preset.id().equals(current)) {
                    g.renderOutline(cx, cy, CELL, CELL, Ui.ACCENT);
                } else if (i == hovered) {
                    g.renderOutline(cx, cy, CELL, CELL, 0xFFFFFFFF);
                }
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            String id = Materials.PRESETS.get(i).id();
            if (onPick != null) {
                onPick.accept(id);
            } else {
                part.material = id;
                markChanged();
            }
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            int i = indexAt(mx, my);
            return i < 0 ? null
                    : tip(Component.translatable("material.creaturestudio." + Materials.PRESETS.get(i).key()));
        }
    }

    /** Botones con icono para anadir cada parte del cuerpo ya colocada (modo facil). */
    private final class BodyGrid extends Element {
        private final BodyParts.Kind[] kinds = BodyParts.Kind.values();
        private final int cols;

        BodyGrid(int x, int y, int w) {
            super(x, y, w, 0);
            this.cols = w >= 260 ? 3 : 2;
            int rows = (kinds.length + cols - 1) / cols;
            this.h = rows * (BODY_CELL_H + 2) - 2;
        }

        private int cellW() {
            return (w - (cols - 1) * 2) / cols;
        }

        private int indexAt(double mx, double my) {
            if (!hit(mx, my)) {
                return -1;
            }
            int cw = cellW();
            int c = (int) ((mx - left()) / (cw + 2));
            int r = (int) ((my - top()) / (BODY_CELL_H + 2));
            int i = r * cols + c;
            return c < cols && i < kinds.length ? i : -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int hovered = indexAt(mx, my);
            int cw = cellW();
            for (int i = 0; i < kinds.length; i++) {
                int cx = left() + (i % cols) * (cw + 2);
                int cy = top() + (i / cols) * (BODY_CELL_H + 2);
                boolean hover = i == hovered;
                g.fill(cx, cy, cx + cw, cy + BODY_CELL_H, hover ? 0xFF3A4458 : 0xFF2E323C);
                g.renderOutline(cx, cy, cw, BODY_CELL_H, hover ? Ui.ACCENT : 0xFF454B58);
                g.renderItem(bodyIcon(kinds[i]), cx + 2, cy + 2);
                String label = Ui.tr("easy.part." + kinds[i].key).getString();
                int avail = Math.max(8, cw - 22);
                int color = hover ? 0xFFFFFFFF : Ui.TEXT;
                if (font.width(label) <= avail) {
                    g.drawString(font, label, cx + 20, cy + 6, color, false);
                } else {
                    // si no cabe, letra un poco mas pequena
                    float sc = 0.75f;
                    String fit = font.plainSubstrByWidth(label, (int) (avail / sc));
                    g.pose().pushPose();
                    g.pose().translate(cx + 20, cy + 7, 0f);
                    g.pose().scale(sc, sc, 1f);
                    g.drawString(font, fit, 0, 0, color, false);
                    g.pose().popPose();
                }
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            addBodyPart(kinds[i]);
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return null;
            }
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Ui.tr("easy.part." + kinds[i].key).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
            lines.addAll(font.split(Ui.tr("easy.part." + kinds[i].key + ".desc"), 190));
            return lines;
        }
    }

    private static final Map<BodyParts.Kind, ItemStack> BODY_ICONS = new HashMap<>();

    private static ItemStack bodyIcon(BodyParts.Kind kind) {
        return BODY_ICONS.computeIfAbsent(kind, k -> new ItemStack(switch (k) {
            case BODY_BIPED -> Items.LEATHER_CHESTPLATE;
            case BODY_QUADRUPED -> Items.SADDLE;
            case HEAD -> Items.PLAYER_HEAD;
            case EYES -> Items.ENDER_EYE;
            case EARS -> Items.RABBIT_FOOT;
            case HORNS -> Items.GOAT_HORN;
            case SNOUT -> Items.PORKCHOP;
            case ANTENNAE -> Items.END_ROD;
            case ARMS -> Items.STICK;
            case LEGS -> Items.LEATHER_BOOTS;
            case SPIDER_LEGS -> Items.COBWEB;
            case TAIL -> Items.STRING;
            case WINGS -> Items.ELYTRA;
            case FINS -> Items.TROPICAL_FISH;
        }));
    }

    /** Tarjetas de plantillas: al pasar el raton se ven en 3D, con un clic se aplican. */
    private final class TemplateGrid extends Element {
        private final int cols;

        TemplateGrid(int y) {
            super(0, y, contentW, 0);
            this.cols = contentW >= 230 ? 2 : 1;
            int rows = (Templates.ALL.size() + cols - 1) / cols;
            this.h = rows * (CARD_H + 3) - 3;
        }

        private int cardW() {
            return (w - GAP * (cols - 1)) / cols;
        }

        private int cardX(int i) {
            return left() + (i % cols) * (cardW() + GAP);
        }

        private int cardY(int i) {
            return top() + (i / cols) * (CARD_H + 3);
        }

        int indexAt(double mx, double my) {
            for (int i = 0; i < Templates.ALL.size(); i++) {
                if (Ui.inside(mx, my, cardX(i), cardY(i), cardW(), CARD_H)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int hovered = indexAt(mx, my);
            int cw = cardW();
            for (int i = 0; i < Templates.ALL.size(); i++) {
                Templates.Template t = Templates.ALL.get(i);
                int cx = cardX(i);
                int cy = cardY(i);
                boolean hover = i == hovered;
                g.fill(cx, cy, cx + cw, cy + CARD_H, hover ? 0xFF3A4458 : 0xFF2E323C);
                g.renderOutline(cx, cy, cw, CARD_H, hover ? Ui.ACCENT : 0xFF454B58);
                g.drawString(font, Component.translatable("template.creaturestudio." + t.key()), cx + 6, cy + 4,
                        0xFFFFFFFF, false);
                String desc = Component.translatable("template.creaturestudio." + t.key() + ".desc").getString();
                g.drawString(font, font.plainSubstrByWidth(desc, Math.max(10, cw - 12)), cx + 6, cy + 14, Ui.MUTED,
                        false);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            applyTemplate(Templates.ALL.get(i));
            playClick();
            return true;
        }
    }

    /** Lista en arbol de todas las piezas, con su propio desplazamiento. */
    private final class PartsList extends Element {
        PartsList(int y, int h) {
            super(0, y, contentW, h);
        }

        int visibleRows() {
            return Math.max(1, (h - 4) / LIST_ROW);
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            g.fill(l, t, l + w, t + h, Ui.PANEL_DARK);
            g.renderOutline(l, t, w, h, Ui.BORDER);
            List<int[]> rows = displayRows();
            int visible = visibleRows();
            partsScroll = Mth.clamp(partsScroll, 0, Math.max(0, rows.size() - visible));
            g.enableScissor(l + 1, t + 1, l + w - 1, t + h - 1);
            for (int r = 0; r < visible + 1 && r + partsScroll < rows.size(); r++) {
                int[] row = rows.get(r + partsScroll);
                int index = row[0];
                int depth = Math.min(row[1], 8);
                int ry = t + 2 + r * LIST_ROW;
                boolean isSelected = index == selected;
                boolean hover = Ui.inside(mx, my, l + 1, ry, w - 2, LIST_ROW);
                if (isSelected) {
                    g.fill(l + 1, ry, l + w - 1, ry + LIST_ROW, Ui.SELECT);
                } else if (hover) {
                    g.fill(l + 1, ry, l + w - 1, ry + LIST_ROW, 0x30FFFFFF);
                }
                CreaturePart p = def.parts.get(index);
                int px = l + 4 + depth * 7;
                if (depth > 0) {
                    g.vLine(px - 4, ry - 1, ry + 6, 0xFF4A5060);
                    g.hLine(px - 4, px - 2, ry + 6, 0xFF4A5060);
                }
                Ui.swatch(g, px, ry + 2, 8, p.color, false);
                String name = font.plainSubstrByWidth(p.name, Math.max(10, l + w - 16 - (px + 11)));
                g.drawString(font, name, px + 11, ry + 2, isSelected ? 0xFFFFFFFF : Ui.TEXT, false);
                if (p.anim != PartAnim.NONE) {
                    g.drawString(font, "~", l + w - 11, ry + 1, Ui.ACCENT, false);
                }
            }
            g.disableScissor();
            if (rows.isEmpty()) {
                Ui.wrapped(g, font, Ui.tr("editor.no_parts"), l + 5, t + 5, w - 10, Ui.MUTED);
            }
            if (rows.size() > visible) {
                int trackH = h - 4;
                int barH = Math.max(8, trackH * visible / rows.size());
                int barY = t + 2 + (trackH - barH) * partsScroll / Math.max(1, rows.size() - visible);
                g.fill(l + w - 3, barY, l + w - 1, barY + barH, 0xFF6A7080);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int row = (int) ((my - top() - 2) / LIST_ROW) + partsScroll;
            List<int[]> rows = displayRows();
            if (row >= 0 && row < rows.size() && my >= top() + 2) {
                select(rows.get(row)[0]);
            }
            return true;
        }

        @Override
        boolean scrolled(double amount) {
            partsScroll -= (int) Math.signum(amount);
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            int row = (int) ((my - top() - 2) / LIST_ROW) + partsScroll;
            List<int[]> rows = displayRows();
            if (row < 0 || row >= rows.size()) {
                return null;
            }
            CreaturePart p = def.parts.get(rows.get(row)[0]);
            if (p.anim == PartAnim.NONE) {
                return null;
            }
            return tip(Ui.tr("parts.animated", Ui.tr("anim." + p.anim.key())));
        }
    }

    /** Icono del item escrito en el campo de al lado. */
    private final class ItemIcon extends Element {
        private final Supplier<String> id;
        @Nullable
        private String cachedId;
        private ItemStack cached = ItemStack.EMPTY;

        ItemIcon(int x, int y, Supplier<String> id) {
            super(x, y, FIELD_H, FIELD_H);
            this.id = id;
        }

        private ItemStack stack() {
            String s = id.get();
            if (!s.equals(cachedId)) {
                cachedId = s;
                cached = resolveItem(s);
            }
            return cached;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            g.fill(l, t, l + w, t + h, 0xFF1A1C21);
            g.renderOutline(l, t, w, h, Ui.BORDER);
            ItemStack stack = stack();
            if (!stack.isEmpty()) {
                g.renderItem(stack, l, t);
            } else if (!id.get().isBlank()) {
                g.drawCenteredString(font, "?", l + w / 2, t + 4, Ui.ERROR);
            }
        }

        @Override
        List<FormattedCharSequence> tooltip(double mx, double my) {
            ItemStack stack = stack();
            if (!stack.isEmpty()) {
                return tip(stack.getHoverName());
            }
            return tip(Ui.tr(id.get().isBlank() ? "field.item_none" : "field.item_unknown"));
        }
    }

    /** Elige que color del huevo se esta editando. */
    private final class EggSlots extends Element {
        EggSlots(int x, int y, int w) {
            super(x, y, w, CHIP_H);
        }

        private int slotW() {
            return (w - GAP) / 2;
        }

        private int slotAt(double mx) {
            return mx < left() + slotW() + GAP / 2.0 ? 0 : 1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int hovered = hit(mx, my) ? slotAt(mx) : -1;
            for (int s = 0; s < 2; s++) {
                int sx = left() + s * (slotW() + GAP);
                int sw = s == 0 ? slotW() : w - slotW() - GAP;
                chip(g, sx, top(), sw, h, Component.empty(), eggSlot == s, hovered == s);
                Ui.swatch(g, sx + 3, top() + 3, 8, s == 0 ? def.eggColor1 : def.eggColor2, false);
                String label = font.plainSubstrByWidth(Ui.tr(s == 0 ? "egg.base" : "egg.spots").getString(),
                        Math.max(10, sw - 18));
                g.drawString(font, label, sx + 14, top() + 3, eggSlot == s ? 0xFFFFFFFF : Ui.TEXT, false);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int s = slotAt(mx);
            if (s != eggSlot) {
                eggSlot = s;
                refreshFields();
            }
            playClick();
            return true;
        }
    }

    /** Vista del huevo con sus dos colores (texturas del huevo generador de Minecraft). */
    private final class EggPreview extends Element {
        EggPreview(int x, int y) {
            super(x, y, 32, 32);
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Function<ResourceLocation, TextureAtlasSprite> atlas = Materials.atlas();
            int c1 = def.eggColor1;
            int c2 = def.eggColor2;
            g.blit(left(), top(), 0, 32, 32, atlas.apply(EGG_BASE), red(c1), green(c1), blue(c1), 1f);
            g.blit(left(), top(), 0, 32, 32, atlas.apply(EGG_SPOTS), red(c2), green(c2), blue(c2), 1f);
        }

        @Override
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return tip(Ui.tr("egg.preview"));
        }
    }

    /** Lista de modelos de Minecraft: al pasar el raton se ven en 3D, con un clic se cargan. */
    private final class VanillaList extends Element {
        @Nullable
        private List<VanillaModels.Entry> filtered;
        @Nullable
        private String filteredQuery;

        VanillaList(int y, int h) {
            super(0, y, contentW, h);
        }

        private List<VanillaModels.Entry> items() {
            if (filtered == null || !vanillaQuery.equals(filteredQuery)) {
                filtered = VanillaModels.search(vanillaQuery);
                filteredQuery = vanillaQuery;
            }
            return filtered;
        }

        private int visibleRows() {
            return Math.max(1, (h - 4) / LIST_ROW);
        }

        @Nullable
        VanillaModels.Entry entryAt(double mx, double my) {
            if (!hit(mx, my) || my < top() + 2) {
                return null;
            }
            int r = (int) ((my - top() - 2) / LIST_ROW);
            int row = r + vanillaScroll;
            List<VanillaModels.Entry> items = items();
            return r < visibleRows() && row >= 0 && row < items.size() ? items.get(row) : null;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            g.fill(l, t, l + w, t + h, Ui.PANEL_DARK);
            g.renderOutline(l, t, w, h, Ui.BORDER);
            List<VanillaModels.Entry> items = items();
            int visible = visibleRows();
            vanillaScroll = Mth.clamp(vanillaScroll, 0, Math.max(0, items.size() - visible));
            VanillaModels.Entry hovered = entryAt(mx, my);
            g.enableScissor(l + 1, t + 1, l + w - 1, t + h - 1);
            for (int r = 0; r < visible && r + vanillaScroll < items.size(); r++) {
                VanillaModels.Entry e = items.get(r + vanillaScroll);
                int ry = t + 2 + r * LIST_ROW;
                if (e == hovered) {
                    g.fill(l + 1, ry, l + w - 1, ry + LIST_ROW, Ui.SELECT);
                }
                String id = e.id().replace("minecraft:", "").replace("#main", "");
                int idW = Math.min(font.width(id), (w - 12) / 2);
                String idText = font.plainSubstrByWidth(id, idW);
                String name = font.plainSubstrByWidth(e.name().getString(), Math.max(10, w - 14 - idW - 6));
                g.drawString(font, name, l + 4, ry + 2, e == hovered ? 0xFFFFFFFF : Ui.TEXT, false);
                g.drawString(font, idText, l + w - 6 - font.width(idText), ry + 2, 0xFF6C7380, false);
            }
            g.disableScissor();
            if (items.isEmpty()) {
                Ui.wrapped(g, font, Ui.tr("vanilla.none"), l + 5, t + 5, w - 10, Ui.MUTED);
            }
            if (items.size() > visible) {
                int trackH = h - 4;
                int barH = Math.max(8, trackH * visible / items.size());
                int barY = t + 2 + (trackH - barH) * vanillaScroll / Math.max(1, items.size() - visible);
                g.fill(l + w - 3, barY, l + w - 1, barY + barH, 0xFF6A7080);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            VanillaModels.Entry e = entryAt(mx, my);
            if (e != null) {
                importVanilla(e);
                playClick();
            }
            return true;
        }

        @Override
        boolean scrolled(double amount) {
            vanillaScroll -= (int) Math.signum(amount);
            return true;
        }
    }

    // =======================================================================================
    // Lista desplegable (se dibuja por encima de todo)
    // =======================================================================================

    private final class Dropdown {
        private final List<DropItem> items;
        private final IntConsumer onPick;
        private final int x;
        private final int y;
        private final int w;
        private final int rows;
        private int scroll;

        Dropdown(int ax, int ay, int aw, int ah, List<DropItem> items, IntConsumer onPick) {
            this.items = items;
            this.onPick = onPick;
            int maxW = aw;
            for (DropItem it : items) {
                maxW = Math.max(maxW, font.width(it.label()) + 18 + Math.min(it.indent(), 6) * 6
                        + (it.color() >= 0 ? 11 : 0));
            }
            this.w = Math.min(maxW, width - 8);
            int below = height - (ay + ah) - 3;
            int above = ay - HEADER_H - 3;
            int wanted = items.size() * LIST_ROW + 4;
            boolean down = below >= wanted || below >= above;
            int space = down ? below : above;
            this.rows = Mth.clamp((space - 4) / LIST_ROW, 1, items.size());
            int dh = rows * LIST_ROW + 4;
            this.y = down ? ay + ah + 1 : ay - dh - 1;
            this.x = Mth.clamp(ax, 2, Math.max(2, width - w - 2));
            int current = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).selected()) {
                    current = i;
                    break;
                }
            }
            this.scroll = Mth.clamp(current - rows / 2, 0, Math.max(0, items.size() - rows));
        }

        private int boxH() {
            return rows * LIST_ROW + 4;
        }

        boolean inside(double mx, double my) {
            return Ui.inside(mx, my, x, y, w, boxH());
        }

        void render(GuiGraphics g, int mx, int my) {
            Ui.popup(g, x, y, w, boxH());
            boolean scrollable = items.size() > rows;
            for (int r = 0; r < rows && r + scroll < items.size(); r++) {
                DropItem it = items.get(r + scroll);
                int ry = y + 2 + r * LIST_ROW;
                boolean hover = Ui.inside(mx, my, x + 1, ry, w - 2, LIST_ROW);
                if (it.selected()) {
                    g.fill(x + 1, ry, x + w - 1, ry + LIST_ROW, Ui.SELECT);
                }
                if (hover) {
                    g.fill(x + 1, ry, x + w - 1, ry + LIST_ROW, 0x40FFFFFF);
                }
                int tx = x + 4 + Math.min(it.indent(), 6) * 6;
                if (it.color() >= 0) {
                    Ui.swatch(g, tx, ry + 2, 8, it.color(), false);
                    tx += 11;
                }
                String s = font.plainSubstrByWidth(it.label().getString(), Math.max(10, x + w - 6 - (scrollable ? 4 : 0) - tx));
                g.drawString(font, s, tx, ry + 2, it.selected() || hover ? 0xFFFFFFFF : Ui.TEXT, false);
            }
            if (scrollable) {
                int trackH = boxH() - 4;
                int barH = Math.max(8, trackH * rows / items.size());
                int barY = y + 2 + (trackH - barH) * scroll / Math.max(1, items.size() - rows);
                g.fill(x + w - 4, barY, x + w - 2, barY + barH, 0xFF8A93A6);
            }
        }

        void click(double mx, double my) {
            int r = (int) ((my - y - 2) / LIST_ROW);
            int i = r + scroll;
            if (my >= y + 2 && r >= 0 && r < rows && i < items.size()) {
                dropdown = null;
                onPick.accept(i);
                playClick();
            }
        }

        void scrollBy(double amount) {
            int step = items.size() > 200 ? 8 : items.size() > 60 ? 4 : 1;
            scroll = Mth.clamp(scroll - (int) Math.signum(amount) * step, 0, Math.max(0, items.size() - rows));
        }
    }
}
