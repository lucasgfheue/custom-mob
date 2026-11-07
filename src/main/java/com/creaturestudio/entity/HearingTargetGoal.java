package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Warden: no ve, pero oye. Solo encuentra a los jugadores que hacen ruido (andar, correr, golpear) dentro de su
 * oido, aunque haya paredes en medio. Agachado y sin moverse no te oye; si te quedas en silencio unos segundos,
 * te pierde.
 */
class HearingTargetGoal extends TargetGoal {
    /** Ticks de silencio tras los que pierde a su objetivo. */
    private static final int FORGET = 100;
    private final CustomCreature creature;
    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, Long> lastNoise = new HashMap<>();
    private long lastScan = Long.MIN_VALUE;
    @Nullable
    private Player found;

    HearingTargetGoal(CustomCreature mob) {
        super(mob, false);
        this.creature = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    private float range() {
        CreatureDefinition d = creature.def();
        return d == null ? 16f : d.abilities.hearRange;
    }

    /** Apunta quien ha hecho ruido desde la ultima vez. */
    private void scan() {
        long now = this.mob.level().getGameTime();
        if (now == lastScan) {
            return;
        }
        lastScan = now;
        double r = range();
        AABB box = this.mob.getBoundingBox().inflate(r, r, r);
        List<Player> players = this.mob.level().getEntitiesOfClass(Player.class, box, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        Set<UUID> near = new HashSet<>();
        for (Player p : players) {
            UUID id = p.getUUID();
            near.add(id);
            Vec3 pos = p.position();
            Vec3 before = lastPos.put(id, pos);
            boolean moved = before != null && before.distanceToSqr(pos) > 0.0025;
            boolean noisy = !p.isShiftKeyDown() && (moved || p.isSprinting() || p.swinging);
            if (noisy && this.mob.distanceToSqr(p) <= r * r) {
                lastNoise.put(id, now);
            }
        }
        lastPos.keySet().retainAll(near);
        lastNoise.keySet().retainAll(near);
    }

    private boolean heard(LivingEntity e, int within) {
        Long t = lastNoise.get(e.getUUID());
        return t != null && this.mob.level().getGameTime() - t <= within;
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = creature.def();
        if (d == null || !d.abilities.blind || !creature.hostileNow()) {
            return false;
        }
        scan();
        if (this.mob.getRandom().nextInt(3) != 0) {
            return false;
        }
        double r = range();
        TargetingConditions conditions = TargetingConditions.forCombat().range(r).ignoreLineOfSight();
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : this.mob.level().players()) {
            if (!heard(p, 10) || !conditions.test(this.mob, p)) {
                continue;
            }
            double dist = this.mob.distanceToSqr(p);
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        this.found = best;
        return best != null;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.found);
        creature.playSoundId("entity.warden.listening_angry", 1.0f, 1.0f);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = this.mob.getTarget();
        if (t == null || !t.isAlive()) {
            return false;
        }
        scan();
        return heard(t, FORGET) && super.canContinueToUse();
    }

    @Override
    protected double getFollowDistance() {
        return range() * 1.5;
    }
}
