package com.creaturestudio.entity;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.BreakPlan;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.RangedType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Romper bloques (pestana Atributos): si no consigue acercarse a quien ataca porque algo le cierra el paso (una pared,
 * una puerta, el suelo...), rompe lo que le estorba, con el tiempo y la cantidad de bloques de la definicion. No usa
 * el movimiento: sigue persiguiendo mientras rompe. Necesita la regla mobGriefing (como el creeper y el enderman).
 */
public class CreatureBreakBlockGoal extends Goal {
    /** Ticks sin acercarse a su objetivo para pensar que esta atascada. */
    private static final int STUCK_TICKS = 20;
    /** Lo que se tiene que acercar (bloques) para que cuente como que avanza. */
    private static final double PROGRESS = 0.3;

    private final CustomCreature mob;
    @Nullable
    private BlockPos pos;
    @Nullable
    private BlockState state;
    private int progress;
    private int needed;
    private int lastStage = -1;

    @Nullable
    private LivingEntity lastTarget;
    private double bestDist = Double.MAX_VALUE;
    private int stuck;
    private int lastCheck;
    private int searchDelay;

    /** La lista de bloques de la definicion, ya leida (se rehace si cambia la definicion). */
    private int filterRevision = Integer.MIN_VALUE;
    private String filterId = "";
    private final Set<String> ids = new HashSet<>();
    private final List<TagKey<Block>> tags = new ArrayList<>();

    public CreatureBreakBlockGoal(CustomCreature mob) {
        this.mob = mob; // sin "flags": va a la vez que perseguir
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = mob.def();
        LivingEntity t = mob.getTarget();
        if (d == null || !d.breakBlocks || !(mob.level() instanceof ServerLevel level) || !griefing(level)
                || t == null || !t.isAlive()) {
            forget();
            return false;
        }
        if (!stuck(d, t) || --searchDelay > 0) {
            return false;
        }
        searchDelay = 5;
        BlockPos p = find(level, d, t);
        if (p == null) {
            return false;
        }
        this.pos = p;
        this.state = level.getBlockState(p);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        CreatureDefinition d = mob.def();
        LivingEntity t = mob.getTarget();
        return pos != null && d != null && d.breakBlocks && mob.isAlive() && t != null && t.isAlive()
                && mob.level() instanceof ServerLevel level && griefing(level)
                && level.getBlockState(pos) == state && near(pos, 1.0);
    }

    @Override
    public void start() {
        CreatureDefinition d = mob.def();
        progress = 0;
        lastStage = -1;
        needed = 0;
        if (d != null && pos != null && state != null) {
            needed = BreakPlan.ticks(d.breakTime, d.breakByHardness, state.getDestroySpeed(mob.level(), pos));
        }
    }

    @Override
    public void tick() {
        CreatureDefinition d = mob.def();
        if (d == null || pos == null || state == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        progress++;
        if (progress % 4 == 1) {
            SoundType sound = state.getSoundType();
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound.getHitSound(),
                    mob.getSoundSource(), (sound.getVolume() + 1f) / 8f, sound.getPitch() * 0.5f);
        }
        if (progress % 10 == 1) {
            mob.swing(InteractionHand.MAIN_HAND);
            mob.playAction(AnimAction.ATTACK);
        }
        if (progress < needed) {
            int stage = Mth.clamp(progress * 10 / Math.max(1, needed), 0, 9);
            if (stage != lastStage) {
                level.destroyBlockProgress(mob.getId(), pos, stage);
                lastStage = stage;
            }
            return;
        }
        breakNow(level, d);
    }

    @Override
    public void stop() {
        if (pos != null) {
            mob.level().destroyBlockProgress(mob.getId(), pos, -1);
        }
        pos = null;
        state = null;
    }

    // ---------------------------------------------------------------------------------------

    private static boolean griefing(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private void forget() {
        lastTarget = null;
        bestDist = Double.MAX_VALUE;
        stuck = 0;
    }

    /** true si lleva un rato sin acercarse a su objetivo y no le puede atacar desde donde esta. */
    private boolean stuck(CreatureDefinition d, LivingEntity t) {
        if (t != lastTarget) {
            forget();
            lastTarget = t;
        }
        int now = mob.tickCount;
        int dt = Mth.clamp(now - lastCheck, 1, 5);
        lastCheck = now;
        double dist = Math.sqrt(mob.distanceToSqr(t));
        if (dist < bestDist - PROGRESS) {
            bestDist = dist;
            stuck = 0;
            return false;
        }
        stuck += dt;
        if (stuck < STUCK_TICKS || mob.isWithinMeleeAttackRange(t)) {
            return false;
        }
        if (d.ranged != RangedType.NONE && mob.getSensing().hasLineOfSight(t)
                && dist <= d.rangedRange) {
            return false; // le dispara desde donde esta
        }
        Path path = mob.getNavigation().getPath();
        return mob.horizontalCollision || mob.getNavigation().isDone() || path == null || !path.canReach();
    }

    /** El bloque que le estorba: el que tiene delante (hacia su objetivo) o el primero entre sus ojos y los de el. */
    @Nullable
    private BlockPos find(ServerLevel level, CreatureDefinition d, LivingEntity t) {
        AABB box = mob.getBoundingBox();
        int feet = Mth.floor(box.minY + 0.01);
        int head = Mth.floor(box.maxY - 0.01);
        double dy = t.getY() - mob.getY();
        int fromY = dy < -1.5 ? feet - 1 : feet;  // va hacia abajo: tambien el suelo
        int toY = dy > 1.5 ? head + 1 : head;     // va hacia arriba: tambien lo de encima
        for (int[] c : BreakPlan.ahead(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                t.getX() - mob.getX(), t.getZ() - mob.getZ(), 1.0, fromY, toY)) {
            BlockPos p = new BlockPos(c[0], c[1], c[2]);
            if (canBreak(level, d, p)) {
                return p;
            }
        }
        Vec3 from = mob.getEyePosition();
        Vec3 to = t.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos p = hit.getBlockPos();
            if (near(p, 0.0) && canBreak(level, d, p)) {
                return p;
            }
        }
        return null;
    }

    /** Si el bloque esta a su alcance (un par de bloques alrededor de su cuerpo). */
    private boolean near(BlockPos p, double slack) {
        double dx = p.getX() + 0.5 - mob.getX();
        double dz = p.getZ() + 0.5 - mob.getZ();
        double reach = mob.getBbWidth() * 0.5 + 2.0 + slack;
        double dy = p.getY() + 0.5 - mob.getY();
        return dx * dx + dz * dz <= reach * reach && dy >= -2.5 - slack && dy <= mob.getBbHeight() + 2.0 + slack;
    }

    /** Se puede romper: no es aire ni algo que se atraviesa, no es irrompible y esta en la lista (o valen todos). */
    private boolean canBreak(ServerLevel level, CreatureDefinition d, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || s.getCollisionShape(level, p).isEmpty()) {
            return false;
        }
        if (s.getDestroySpeed(level, p) < 0f || s.is(BlockTags.WITHER_IMMUNE)) {
            return false;
        }
        return d.breakAll || listed(d, s);
    }

    private boolean listed(CreatureDefinition d, BlockState s) {
        if (d.revision != filterRevision || !d.id.equals(filterId)) {
            filterRevision = d.revision;
            filterId = d.id;
            ids.clear();
            tags.clear();
            for (String e : d.breakList) {
                if (e.startsWith("#")) {
                    ResourceLocation rl = ResourceLocation.tryParse(e.substring(1));
                    if (rl != null) {
                        tags.add(TagKey.create(Registries.BLOCK, rl));
                    }
                } else {
                    ids.add(e);
                }
            }
        }
        if (ids.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString())) {
            return true;
        }
        for (TagKey<Block> tag : tags) {
            if (s.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Rompe el bloque y, si rompe varios de un golpe, los de alrededor (primero los que le cierran el paso). */
    private void breakNow(ServerLevel level, CreatureDefinition d) {
        BlockPos main = pos;
        if (main == null) {
            return;
        }
        AABB box = mob.getBoundingBox();
        int feet = Mth.floor(box.minY + 0.01);
        // si lo que rompe es el suelo es que va hacia abajo: entonces tambien vale lo de debajo
        int minY = main.getY() < feet ? Integer.MIN_VALUE : feet;
        Set<Long> tunnel = new HashSet<>();
        LivingEntity t = mob.getTarget();
        if (t != null && d.breakCount > 1) {
            double length = 1.0 + Math.cbrt(d.breakCount);
            for (int[] c : BreakPlan.ahead(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    t.getX() - mob.getX(), t.getZ() - mob.getZ(), length, feet, Mth.floor(box.maxY - 0.01))) {
                tunnel.add(BreakPlan.key(c[0], c[1], c[2]));
            }
        }
        List<int[]> blocks = BreakPlan.cluster(main.getX(), main.getY(), main.getZ(), d.breakCount, minY, tunnel,
                (x, y, z) -> canBreak(level, d, new BlockPos(x, y, z)));
        level.destroyBlockProgress(mob.getId(), main, -1);
        for (int[] b : blocks) {
            level.destroyBlock(new BlockPos(b[0], b[1], b[2]), true, mob);
        }
        // a volver a medir si avanza (el camino puede haberse abierto)
        bestDist = Double.MAX_VALUE;
        stuck = 0;
        pos = null;
        state = null;
    }
}
