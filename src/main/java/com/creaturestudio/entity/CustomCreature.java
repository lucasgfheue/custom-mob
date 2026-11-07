package com.creaturestudio.entity;

import com.creaturestudio.data.Behavior;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.HitEffect;
import com.creaturestudio.data.Movement;
import com.creaturestudio.data.RangedType;
import com.creaturestudio.item.CreatureEggItem;
import com.creaturestudio.registry.ModRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Entidad unica que adopta la forma y el comportamiento de cualquier criatura creada por el usuario.
 * La definicion se busca por el identificador sincronizado {@link #DATA_CREATURE_ID}.
 */
public class CustomCreature extends PathfinderMob implements RangedAttackMob {
    private static final EntityDataAccessor<String> DATA_CREATURE_ID =
            SynchedEntityData.defineId(CustomCreature.class, EntityDataSerializers.STRING);

    private int appliedRevision = -1;
    private String appliedId = "";
    private Movement appliedMovement = Movement.WALK;

    @Nullable
    private EntityDimensions cachedDims;
    private int dimsRevision = -1;
    private String dimsId = "";

    public CustomCreature(EntityType<? extends CustomCreature> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CREATURE_ID, "");
    }

    // ---------------------------------------------------------------------------------------
    // Definicion
    // ---------------------------------------------------------------------------------------

    public String getCreatureId() {
        return this.entityData.get(DATA_CREATURE_ID);
    }

    public void setCreatureId(String id) {
        this.entityData.set(DATA_CREATURE_ID, id == null ? "" : id);
        if (!this.level().isClientSide()) {
            applyDefinition(true);
        }
    }

    @Nullable
    public CreatureDefinition def() {
        if (this.entityData == null) {
            return null; // todavia en el constructor
        }
        return CreatureStore.of(this.level()).get(getCreatureId());
    }

    public boolean isFlyer() {
        CreatureDefinition d = def();
        return d != null && d.movement == Movement.FLY;
    }

    private Behavior behavior() {
        CreatureDefinition d = def();
        return d == null ? Behavior.PASSIVE : d.behavior;
    }

    private RangedType rangedType() {
        CreatureDefinition d = def();
        return d == null ? RangedType.NONE : d.ranged;
    }

    private boolean canFight() {
        return behavior() != Behavior.PASSIVE;
    }

    private void applyDefinition(boolean fresh) {
        CreatureDefinition d = def();
        if (d == null) {
            return;
        }
        setBase(Attributes.MAX_HEALTH, d.health);
        setBase(Attributes.ATTACK_DAMAGE, d.damage);
        setBase(Attributes.MOVEMENT_SPEED, d.speed);
        setBase(Attributes.FLYING_SPEED, d.speed * 1.6);
        setBase(Attributes.ARMOR, d.armor);
        setBase(Attributes.KNOCKBACK_RESISTANCE, d.knockbackResistance);
        setBase(Attributes.FOLLOW_RANGE, d.followRange);
        if (fresh || this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
        this.xpReward = d.xp;
        updateMovement(d.movement);
        this.appliedRevision = d.revision;
        this.appliedId = d.id;
        this.refreshDimensions();
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void updateMovement(Movement movement) {
        if (movement == this.appliedMovement) {
            return;
        }
        if (movement == Movement.FLY) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            FlyingPathNavigation nav = new FlyingPathNavigation(this, this.level());
            nav.setCanFloat(true);
            this.navigation = nav;
            this.setNoGravity(true);
        } else {
            this.moveControl = new MoveControl(this);
            GroundPathNavigation nav = new GroundPathNavigation(this, this.level());
            nav.setCanFloat(true);
            this.navigation = nav;
            this.setNoGravity(false);
        }
        this.appliedMovement = movement;
    }

    // ---------------------------------------------------------------------------------------
    // IA
    // ---------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new ConditionalGoal(new FloatGoal(this), () -> !isFlyer()));
        this.goalSelector.addGoal(1, new ConditionalGoal(new PanicGoal(this, 1.4),
                () -> behavior() == Behavior.PASSIVE));
        this.goalSelector.addGoal(2, new ConditionalGoal(new RangedAttackGoal(this, 1.0, 30, 16.0f),
                () -> canFight() && rangedType() != RangedType.NONE));
        this.goalSelector.addGoal(2, new ConditionalGoal(new MeleeAttackGoal(this, 1.2, true),
                () -> canFight() && rangedType() == RangedType.NONE));
        this.goalSelector.addGoal(3, new ConditionalGoal(new TemptGoal(this, 1.1, this::isTemptItem, false),
                this::hasTemptItem));
        this.goalSelector.addGoal(5, new ConditionalGoal(new WaterAvoidingRandomStrollGoal(this, 1.0),
                () -> !isFlyer()));
        this.goalSelector.addGoal(5, new ConditionalGoal(new WaterAvoidingRandomFlyingGoal(this, 1.0),
                this::isFlyer));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new ConditionalGoal(new HurtByTargetGoal(this), this::canFight));
        this.targetSelector.addGoal(2, new ConditionalGoal(
                new NearestAttackableTargetGoal<>(this, Player.class, true),
                () -> behavior() == Behavior.HOSTILE && this.level().getDifficulty() != Difficulty.PEACEFUL));
    }

    private boolean hasTemptItem() {
        CreatureDefinition d = def();
        return d != null && !d.temptItem.isEmpty();
    }

    private boolean isTemptItem(ItemStack stack) {
        CreatureDefinition d = def();
        if (d == null || d.temptItem.isEmpty() || stack.isEmpty()) {
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(d.temptItem);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        CreatureDefinition d = def();
        if (d == null) {
            return;
        }
        Level level = this.level();
        double sx = this.getX();
        double sy = this.getEyeY() - 0.1;
        double sz = this.getZ();
        double dx = target.getX() - sx;
        double dy = target.getY(0.3333) - sy;
        double dz = target.getZ() - sz;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float inaccuracy = (float) (14 - level.getDifficulty().getId() * 4);
        float pitch = 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f);
        float power = 1.6f;
        Projectile projectile;
        switch (d.ranged) {
            case ARROW -> {
                Arrow arrow = EntityType.ARROW.create(level);
                if (arrow == null) {
                    return;
                }
                arrow.setBaseDamage(Math.max(0.5, d.damage * 0.5));
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                dy += horizontal * 0.2;
                this.playSound(SoundEvents.SKELETON_SHOOT, 1.0f, pitch);
                projectile = arrow;
            }
            case SNOWBALL -> {
                Snowball snowball = EntityType.SNOWBALL.create(level);
                if (snowball == null) {
                    return;
                }
                dy += horizontal * 0.2;
                this.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0f, pitch);
                projectile = snowball;
            }
            case FIREBALL -> {
                SmallFireball fireball = EntityType.SMALL_FIREBALL.create(level);
                if (fireball == null) {
                    return;
                }
                power = 1.0f;
                this.playSound(SoundEvents.BLAZE_SHOOT, 1.0f, pitch);
                projectile = fireball;
            }
            case SPIT -> {
                LlamaSpit spit = EntityType.LLAMA_SPIT.create(level);
                if (spit == null) {
                    return;
                }
                dy += horizontal * 0.2;
                this.playSound(SoundEvents.LLAMA_SPIT, 1.0f, pitch);
                projectile = spit;
            }
            default -> {
                return;
            }
        }
        projectile.setOwner(this);
        projectile.setPos(sx, sy, sz);
        projectile.shoot(dx, dy, dz, power, inaccuracy);
        level.addFreshEntity(projectile);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            CreatureDefinition d = def();
            if (d != null) {
                applyHitEffect(d.hitEffect, living);
            }
        }
        return hit;
    }

    private void applyHitEffect(HitEffect effect, LivingEntity target) {
        switch (effect) {
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), this);
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0), this);
            case SLOWNESS -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), this);
            case WEAKNESS -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), this);
            case FIRE -> target.igniteForSeconds(4.0f);
            case LEVITATION -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0), this);
            case BLINDNESS -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0), this);
            case NAUSEA -> target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), this);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        CreatureDefinition d = def();
        if (!this.level().isClientSide()) {
            if (d == null) {
                String id = getCreatureId();
                // Sin definicion: invocada sin datos o criatura borrada -> desaparece.
                if (id.isEmpty() ? this.tickCount > 20 : CreatureStore.SERVER.wasDeleted(id)) {
                    this.discard();
                }
            } else if (d.revision != this.appliedRevision || !d.id.equals(this.appliedId)) {
                applyDefinition(false);
            }
        } else if (d != null && (d.revision != this.dimsRevision || !d.id.equals(this.dimsId))) {
            this.refreshDimensions();
        }
    }

    @Override
    public void aiStep() {
        if (this.isAlive() && !this.level().isClientSide()) {
            CreatureDefinition d = def();
            if (d != null && d.burnsInSun && !d.fireImmune && this.isSunBurnTick()) {
                this.igniteForSeconds(8.0f);
            }
        }
        super.aiStep();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        CreatureDefinition d = def();
        if (d == null) {
            return super.getDefaultDimensions(pose);
        }
        if (this.cachedDims == null || this.dimsRevision != d.revision || !d.id.equals(this.dimsId)) {
            float[] hb = d.hitbox();
            this.cachedDims = EntityDimensions.scalable(hb[0], hb[1]);
            this.dimsRevision = d.revision;
            this.dimsId = d.id;
        }
        return this.cachedDims;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_CREATURE_ID.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public boolean fireImmune() {
        CreatureDefinition d = def();
        return (d != null && d.fireImmune) || super.fireImmune();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return !isFlyer() && super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    protected Component getTypeName() {
        CreatureDefinition d = def();
        return d != null ? Component.literal(d.name) : super.getTypeName();
    }

    @Override
    public ItemStack getPickResult() {
        return CreatureEggItem.create(getCreatureId(), def());
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        CreatureDefinition d = def();
        if (d == null || d.dropItem.isEmpty() || d.dropMax <= 0) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(d.dropItem);
        if (id == null) {
            return;
        }
        BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
            int count = d.dropMin + (d.dropMax > d.dropMin ? this.getRandom().nextInt(d.dropMax - d.dropMin + 1) : 0);
            if (count > 0) {
                ItemStack stack = new ItemStack(item, count);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(stack);
                }
            }
        });
    }

    // ---------------------------------------------------------------------------------------
    // Sonidos
    // ---------------------------------------------------------------------------------------

    @Nullable
    private static SoundEvent sound(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(rl).orElse(null);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        CreatureDefinition d = def();
        return d == null ? null : sound(d.sound.ambient);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        CreatureDefinition d = def();
        return d == null ? null : sound(d.sound.hurt);
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        CreatureDefinition d = def();
        return d == null ? null : sound(d.sound.death);
    }

    @Override
    protected float getSoundVolume() {
        CreatureDefinition d = def();
        return d == null ? 1.0f : Mth.clamp(0.6f + 0.4f * d.scale, 0.5f, 3.0f);
    }

    // ---------------------------------------------------------------------------------------
    // Guardado
    // ---------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CreatureId", getCreatureId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CreatureId")) {
            this.entityData.set(DATA_CREATURE_ID, tag.getString("CreatureId"));
            if (!this.level().isClientSide()) {
                applyDefinition(false);
            }
        }
    }

    /** Crea y coloca una criatura en el mundo. */
    @Nullable
    public static CustomCreature spawn(ServerLevel level, CreatureDefinition d, double x, double y, double z,
                                       float yaw, boolean persistent) {
        CustomCreature creature = ModRegistry.CUSTOM_CREATURE.get().create(level);
        if (creature == null) {
            return null;
        }
        creature.moveTo(x, y, z, yaw, 0.0f);
        creature.yHeadRot = yaw;
        creature.yBodyRot = yaw;
        creature.setCreatureId(d.id);
        if (persistent) {
            creature.setPersistenceRequired();
        }
        level.addFreshEntity(creature);
        return creature;
    }
}
