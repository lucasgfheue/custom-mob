package com.creaturestudio.entity;

import com.creaturestudio.data.Abilities;
import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.HitEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que hacen las habilidades de una criatura que no son objetivos de IA: teletransportes, aura, curarse,
 * invocar, poner objetos, dejar rastro, espinas, tinta, manada... Un objeto por criatura (guarda sus tiempos).
 */
final class CreatureAbilities {
    private static final BlockState[] FLOWERS = {
            Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.ALLIUM.defaultBlockState()
    };

    private final CustomCreature mob;
    private int teleportTimer;
    private int auraTimer;
    private int healTimer;
    private int summonTimer;
    /** Ticks hasta el proximo objeto (-1 = sin empezar). Se guarda en el mundo. */
    int layTimer = -1;
    private final List<UUID> summons = new ArrayList<>();

    CreatureAbilities(CustomCreature mob) {
        this.mob = mob;
    }

    private RandomSource random() {
        return mob.getRandom();
    }

    // ---------------------------------------------------------------------------------------
    // Cada tick (servidor)
    // ---------------------------------------------------------------------------------------

    void serverTick(CreatureDefinition d, ServerLevel level) {
        Abilities a = d.abilities;
        if (a.teleport) {
            tickTeleport(a);
        }
        if (a.stareAnger && mob.tickCount % 5 == 0) {
            tickStare(level);
        }
        if (a.aura) {
            tickAura(a, level);
        }
        if (a.selfHeal) {
            tickHeal(a, level);
        }
        if (a.summon && !mob.isSummoned()) {
            tickSummon(a, level);
        }
        if (a.layItems) {
            tickLay(a);
        }
        if (a.trail) {
            tickTrail(a, level);
        }
        if (a.slowFall) {
            Vec3 v = mob.getDeltaMovement();
            if (!mob.onGround() && v.y < 0.0) {
                mob.setDeltaMovement(v.multiply(1.0, 0.6, 1.0));
            }
        }
    }

    // --- Enderman ---

    private void tickTeleport(Abilities a) {
        if (--teleportTimer > 0) {
            return;
        }
        teleportTimer = 20 + random().nextInt(20);
        LivingEntity target = mob.getTarget();
        if (a.teleportChase && target != null && target.isAlive()) {
            double dist = mob.distanceTo(target);
            if (dist > 12.0 && random().nextInt(2) == 0) {
                teleportTowards(target, (float) Math.min(a.teleportRange, dist - 3.0));
            }
        }
    }

    /** Teletransporte al azar dentro del alcance. */
    boolean teleportRandomly(float range) {
        double x = mob.getX() + (random().nextDouble() - 0.5) * range * 2.0;
        double y = mob.getY() + (random().nextInt((int) range + 1) - range / 2.0);
        double z = mob.getZ() + (random().nextDouble() - 0.5) * range * 2.0;
        return teleportTo(x, y, z);
    }

    private boolean teleportTowards(Entity target, float distance) {
        Vec3 away = new Vec3(mob.getX() - target.getX(), mob.getY(0.5) - target.getEyeY(), mob.getZ() - target.getZ())
                .normalize();
        double x = mob.getX() + (random().nextDouble() - 0.5) * 8.0 - away.x * distance;
        double y = mob.getY() + (random().nextInt(16) - 8) - away.y * distance;
        double z = mob.getZ() + (random().nextDouble() - 0.5) * 8.0 - away.z * distance;
        return teleportTo(x, y, z);
    }

    /** Como el enderman: busca suelo debajo del punto y no aparece en el agua. */
    private boolean teleportTo(double x, double y, double z) {
        Level level = mob.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (pos.getY() > level.getMinBuildHeight() && !level.getBlockState(pos).blocksMotion()) {
            pos.move(Direction.DOWN);
        }
        BlockState ground = level.getBlockState(pos);
        if (!ground.blocksMotion() || ground.getFluidState().is(FluidTags.WATER)) {
            return false;
        }
        double ox = mob.getX();
        double oy = mob.getY();
        double oz = mob.getZ();
        if (!mob.randomTeleport(x, y, z, true)) {
            return false;
        }
        SoundEvent sound = CustomCreature.soundById("entity.enderman.teleport");
        if (sound != null && !mob.isSilent()) {
            level.playSound(null, ox, oy, oz, sound, mob.getSoundSource(), 1.0f, 1.0f);
            mob.playSound(sound, 1.0f, 1.0f);
        }
        return true;
    }

    /** Se enoja con quien la mira a los ojos (salvo si lleva una calabaza tallada en la cabeza). */
    private void tickStare(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p == mob.getTarget() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)
                    || mob.distanceToSqr(p) > 64.0 * 64.0 || !isLookingAtMe(p)) {
                continue;
            }
            mob.setTarget(p);
            mob.playSoundId("entity.enderman.scream", 1.0f, 1.0f);
            return;
        }
    }

    private boolean isLookingAtMe(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
            return false;
        }
        Vec3 view = player.getViewVector(1.0f).normalize();
        Vec3 toMe = new Vec3(mob.getX() - player.getX(), mob.getEyeY() - player.getEyeY(), mob.getZ() - player.getZ());
        double dist = toMe.length();
        if (dist < 1.0e-4) {
            return false;
        }
        double dot = view.dot(toMe.normalize());
        return dot > 1.0 - 0.025 / dist && player.hasLineOfSight(mob);
    }

    // --- Guardian anciano ---

    private void tickAura(Abilities a, ServerLevel level) {
        if (--auraTimer > 0) {
            return;
        }
        auraTimer = a.auraInterval * 20;
        double r = a.auraRange;
        boolean any = false;
        for (Player p : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(r),
                EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
            if (mob.distanceToSqr(p) <= r * r) {
                HitEffects.applyFor(a.auraEffect, p, mob, a.auraDuration * 20);
                any = true;
            }
        }
        if (any) {
            level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getY(0.5), mob.getZ(), 24,
                    mob.getBbWidth(), mob.getBbHeight() * 0.5, mob.getBbWidth(), 0.05);
            mob.playSoundId(a.auraEffect == HitEffect.MINING_FATIGUE ? "entity.elder_guardian.curse"
                    : a.auraEffect == HitEffect.DARKNESS ? "entity.warden.heartbeat" : "entity.evoker.cast_spell",
                    0.8f, 1.0f);
        }
    }

    // --- Bruja ---

    private void tickHeal(Abilities a, ServerLevel level) {
        if (healTimer > 0) {
            healTimer--;
            return;
        }
        if (mob.getHealth() < mob.getMaxHealth() * 0.5f) {
            mob.heal(a.selfHealAmount);
            mob.playSoundId("entity.witch.drink", 1.0f, 1.0f);
            mob.playAction(AnimAction.ABILITY);
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getEyeY(), mob.getZ(), 4, 0.4, 0.3, 0.4, 0.0);
            healTimer = a.selfHealCooldown * 20;
        }
    }

    // --- Invocador ---

    private void tickSummon(Abilities a, ServerLevel level) {
        if (summonTimer > 0) {
            summonTimer--;
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        summons.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        int room = a.summonMax - summons.size();
        if (room <= 0) {
            summonTimer = 40;
            return;
        }
        int made = 0;
        for (int i = 0; i < Math.min(a.summonCount, room); i++) {
            Entity e = summonOne(a.summonEntity, level, target);
            if (e != null) {
                summons.add(e.getUUID());
                made++;
            }
        }
        summonTimer = a.summonCooldown * 20;
        if (made > 0) {
            mob.playSoundId("entity.evoker.prepare_summon", 1.0f, 1.0f);
            mob.playAction(AnimAction.ABILITY);
        }
    }

    @Nullable
    private Entity summonOne(String id, ServerLevel level, LivingEntity target) {
        double x = mob.getX() + (random().nextDouble() - 0.5) * 4.0;
        double y = mob.getY();
        double z = mob.getZ() + (random().nextDouble() - 0.5) * 4.0;
        float yaw = random().nextFloat() * 360.0f;
        Entity made = null;
        String typeId = id;
        if (!id.contains(":")) {
            CreatureDefinition other = CreatureStore.of(level).get(id);
            if (other != null) {
                CustomCreature c = CustomCreature.spawnChild(level, other, x, y, z, yaw, 0, true, false);
                if (c != null) {
                    c.setTarget(target);
                    made = c;
                }
            }
            typeId = "minecraft:" + id;
        }
        if (made == null) {
            Optional<EntityType<?>> type = EntityType.byString(typeId);
            if (type.isEmpty()) {
                return null;
            }
            Entity e = type.get().create(level);
            if (!(e instanceof Mob m)) {
                if (e != null) {
                    e.discard();
                }
                return null;
            }
            m.moveTo(x, y, z, yaw, 0.0f);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            m.setTarget(target);
            level.addFreshEntity(m);
            made = m;
        }
        level.sendParticles(ParticleTypes.POOF, x, y + 0.5, z, 8, 0.3, 0.3, 0.3, 0.02);
        return made;
    }

    // --- Gallina ---

    private void tickLay(Abilities a) {
        int base = a.layInterval * 20;
        if (layTimer < 0) {
            layTimer = base + random().nextInt(base + 1);
        }
        if (--layTimer > 0) {
            return;
        }
        layTimer = -1;
        ItemStack stack = CustomCreature.stackOf(a.layItem);
        if (!stack.isEmpty()) {
            mob.spawnAtLocation(stack);
            mob.playSoundId("entity.chicken.egg", 1.0f, (random().nextFloat() - random().nextFloat()) * 0.2f + 1.0f);
        }
    }

    // --- Golem de nieve ---

    private void tickTrail(Abilities a, ServerLevel level) {
        if (!mob.onGround() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        int every = switch (a.trailBlock) {
            case SNOW -> 1;
            case FLOWERS -> 8;
            case FIRE -> 10;
            case COBWEB -> 40;
        };
        if (mob.tickCount % every != 0) {
            return;
        }
        if (a.trailBlock == com.creaturestudio.data.TrailBlock.SNOW) {
            BlockState snow = Blocks.SNOW.defaultBlockState();
            for (int i = 0; i < 4; i++) {
                int x = (int) Math.floor(mob.getX() + (i % 2 * 2 - 1) * 0.25);
                int y = (int) Math.floor(mob.getY());
                int z = (int) Math.floor(mob.getZ() + (i / 2 % 2 * 2 - 1) * 0.25);
                place(level, new BlockPos(x, y, z), snow);
            }
            return;
        }
        BlockPos pos;
        if (a.trailBlock == com.creaturestudio.data.TrailBlock.FLOWERS) {
            pos = mob.blockPosition();
        } else {
            // fuego y telaranas, por detras (para no quemarse ni quedarse pegada)
            Vec3 look = mob.getLookAngle();
            Vec3 back = new Vec3(-look.x, 0.0, -look.z);
            back = back.lengthSqr() > 1.0e-6 ? back.normalize().scale(1.5) : back;
            pos = BlockPos.containing(mob.getX() + back.x, mob.getY(), mob.getZ() + back.z);
        }
        BlockState state = switch (a.trailBlock) {
            case FIRE -> BaseFireBlock.getState(level, pos);
            case COBWEB -> Blocks.COBWEB.defaultBlockState();
            default -> FLOWERS[random().nextInt(FLOWERS.length)];
        };
        place(level, pos, state);
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir() && state.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, state);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Al recibir dano / al golpear
    // ---------------------------------------------------------------------------------------

    /** Enderman: esquiva los proyectiles teletransportandose. true = no recibe el golpe. */
    boolean dodge(CreatureDefinition d, DamageSource source) {
        Abilities a = d.abilities;
        if (!a.teleport || !a.teleportDodge || !source.is(DamageTypeTags.IS_PROJECTILE)) {
            return false;
        }
        for (int i = 0; i < 16; i++) {
            if (teleportRandomly(a.teleportRange)) {
                return true;
            }
        }
        return false;
    }

    void afterHurt(CreatureDefinition d, DamageSource source) {
        Abilities a = d.abilities;
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (a.thorns && direct instanceof LivingEntity hitter && direct == attacker
                && !source.is(DamageTypes.THORNS) && !source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)) {
            hitter.hurt(mob.damageSources().thorns(mob), a.thornsDamage);
        }
        if (a.ink && mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY(0.5), mob.getZ(), 30,
                    mob.getBbWidth() * 0.5, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.5, 0.1);
            mob.playSoundId("entity.squid.squirt", 1.0f, 1.0f);
            if (attacker instanceof LivingEntity l && mob.distanceToSqr(l) < 36.0) {
                l.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), mob);
            }
        }
        if (a.pack && attacker instanceof LivingEntity l
                && !(l instanceof CustomCreature c && c.getCreatureId().equals(mob.getCreatureId()))) {
            alertPack(l, a.packRange);
        }
        if (a.teleport && !source.is(DamageTypeTags.IS_PROJECTILE) && random().nextInt(10) < 3) {
            teleportRandomly(a.teleportRange);
        }
    }

    /** Lobo: las de su misma especie que esten cerca y puedan pelear atacan tambien. */
    private void alertPack(LivingEntity attacker, float range) {
        for (CustomCreature c : mob.level().getEntitiesOfClass(CustomCreature.class,
                mob.getBoundingBox().inflate(range, 8.0, range))) {
            if (c != mob && c.getTarget() == null && c.getCreatureId().equals(mob.getCreatureId()) && c.canFightNow()) {
                c.setTarget(attacker);
            }
        }
    }

    /** Golem de hierro: lanza por los aires a quien golpea. */
    void afterAttack(CreatureDefinition d, Entity target) {
        if (!d.abilities.launch) {
            return;
        }
        double resist = target instanceof LivingEntity l ? l.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0;
        double up = 0.4 * d.abilities.launchPower * Math.max(0.0, 1.0 - resist);
        target.setDeltaMovement(target.getDeltaMovement().add(0.0, up, 0.0));
        target.hurtMarked = true;
        mob.playSoundId("entity.iron_golem.attack", 1.0f, 1.0f);
    }
}
