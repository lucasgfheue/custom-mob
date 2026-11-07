package com.creaturestudio.entity;

import com.creaturestudio.data.Ballistics;
import com.creaturestudio.data.Behavior;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureDrop;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.Movement;
import com.creaturestudio.data.RangedType;
import com.creaturestudio.item.CreatureEggItem;
import com.creaturestudio.registry.ModRegistry;
import com.creaturestudio.server.RespawnData;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Entidad unica que adopta la forma y el comportamiento de cualquier criatura creada por el usuario.
 * La definicion se busca por el identificador sincronizado {@link #DATA_CREATURE_ID}.
 */
public class CustomCreature extends PathfinderMob implements RangedAttackMob {
    private static final EntityDataAccessor<String> DATA_CREATURE_ID =
            SynchedEntityData.defineId(CustomCreature.class, EntityDataSerializers.STRING);
    /** Alcance de golpe de los mobs normales (Mob.DEFAULT_ATTACK_REACH). */
    private static final double VANILLA_REACH = Math.sqrt(2.04) - 0.6;

    private int appliedRevision = -1;
    private String appliedId = "";
    private Movement appliedMovement = Movement.WALK;

    @Nullable
    private EntityDimensions cachedDims;
    private int dimsRevision = -1;
    private String dimsId = "";

    // Reaparicion: donde aparecio por primera vez y cuantas veces ha vuelto ya
    private boolean hasHome;
    private double homeX;
    private double homeY;
    private double homeZ;
    private int respawnsUsed;

    // Escudo: ticks que lo tiene bajado para atacar
    private int shieldDown;

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

    private boolean isRanged() {
        CreatureDefinition d = def();
        return d != null && d.ranged != RangedType.NONE;
    }

    private boolean canFight() {
        return behavior() != Behavior.PASSIVE;
    }

    private boolean seesThroughWalls() {
        CreatureDefinition d = def();
        return d != null && d.seeThroughWalls;
    }

    private void applyDefinition(boolean fresh) {
        CreatureDefinition d = def();
        if (d == null) {
            return;
        }
        setBase(Attributes.MAX_HEALTH, d.health);
        setBase(Attributes.ATTACK_DAMAGE, d.damage);
        setBase(Attributes.ATTACK_KNOCKBACK, d.meleeKnockback);
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
        applyEquipment(d);
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
    // Equipo
    // ---------------------------------------------------------------------------------------

    private static ItemStack stackOf(String id) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void applyEquipment(CreatureDefinition d) {
        setSlot(EquipmentSlot.HEAD, stackOf(d.equipHead));
        setSlot(EquipmentSlot.CHEST, stackOf(d.equipChest));
        setSlot(EquipmentSlot.LEGS, stackOf(d.equipLegs));
        setSlot(EquipmentSlot.FEET, stackOf(d.equipFeet));
        setSlot(EquipmentSlot.MAINHAND, stackOf(d.equipMainHand));
        ItemStack off = stackOf(d.equipOffHand);
        setSlot(EquipmentSlot.OFFHAND, off.isEmpty() ? stackOf(d.equipShield) : off);
        float chance = d.equipDropChance / 100f;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            this.setDropChance(slot, chance);
        }
    }

    /** Solo cambia el objeto si es otro (para no repetir el sonido de equiparse). */
    private void setSlot(EquipmentSlot slot, ItemStack stack) {
        ItemStack now = this.getItemBySlot(slot);
        if (now.isEmpty() != stack.isEmpty() || (!stack.isEmpty() && now.getItem() != stack.getItem())) {
            this.setItemSlot(slot, stack);
        }
    }

    /** Baja el escudo unos ticks para poder golpear. */
    public void lowerShield(int ticks) {
        this.shieldDown = Math.max(this.shieldDown, ticks);
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }
    }

    /** Sube el escudo cuando el objetivo esta cerca; si tiene otra cosa en la mano izquierda, la cambia por el escudo. */
    private void updateShield(CreatureDefinition d) {
        ItemStack shield = stackOf(d.equipShield);
        if (shield.isEmpty()) {
            return;
        }
        if (this.shieldDown > 0) {
            this.shieldDown--;
        }
        LivingEntity target = this.getTarget();
        boolean block = target != null && target.isAlive() && this.shieldDown == 0
                && this.distanceToSqr(target) < 36.0 && this.getSensing().hasLineOfSight(target);
        ItemStack off = stackOf(d.equipOffHand);
        if (block) {
            setSlot(EquipmentSlot.OFFHAND, shield);
            if (!this.isUsingItem()) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else {
            if (this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND) {
                this.stopUsingItem();
            }
            setSlot(EquipmentSlot.OFFHAND, off.isEmpty() ? shield : off);
        }
    }

    // ---------------------------------------------------------------------------------------
    // IA
    // ---------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new ConditionalGoal(new FloatGoal(this), () -> !isFlyer()));
        this.goalSelector.addGoal(1, new ConditionalGoal(new PanicGoal(this, 1.4),
                () -> behavior() == Behavior.PASSIVE));
        this.goalSelector.addGoal(2, new ConditionalGoal(new CreatureRangedGoal(this),
                () -> canFight() && isRanged()));
        this.goalSelector.addGoal(2, new ConditionalGoal(new CreatureMeleeGoal(this, 1.2),
                () -> canFight() && (!isRanged() || meleeInsteadOfRanged())));
        this.goalSelector.addGoal(3, new ConditionalGoal(new TemptGoal(this, 1.1, this::isTemptItem, false),
                this::hasTemptItem));
        this.goalSelector.addGoal(5, new ConditionalGoal(new WaterAvoidingRandomStrollGoal(this, 1.0),
                () -> !isFlyer()));
        this.goalSelector.addGoal(5, new ConditionalGoal(new WaterAvoidingRandomFlyingGoal(this, 1.0),
                this::isFlyer));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new ConditionalGoal(new HurtByTargetGoal(this), this::canFight));
        this.targetSelector.addGoal(2, new ConditionalGoal(new CreatureTargetGoal(this, false),
                () -> hostileNow() && !seesThroughWalls()));
        this.targetSelector.addGoal(2, new ConditionalGoal(new CreatureTargetGoal(this, true),
                () -> hostileNow() && seesThroughWalls()));
    }

    private boolean hostileNow() {
        return behavior() == Behavior.HOSTILE && this.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    /** Un atacante a distancia pasa al cuerpo a cuerpo cuando el objetivo se le acerca mucho. */
    private boolean meleeInsteadOfRanged() {
        CreatureDefinition d = def();
        LivingEntity target = this.getTarget();
        return d != null && d.rangedMeleeRange > 0f && target != null
                && this.distanceTo(target) <= d.rangedMeleeRange + 1f;
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

    /** Alcance del golpe cuerpo a cuerpo (desde el borde del cuerpo). */
    @Override
    protected AABB getAttackBoundingBox() {
        AABB box = super.getAttackBoundingBox();
        CreatureDefinition d = def();
        if (d == null) {
            return box;
        }
        double extra = d.meleeReach - VANILLA_REACH;
        return box.inflate(extra, 0.0, extra);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        shootVolley(target, false);
    }

    /** Dispara una vez (los proyectiles que indique "disparos a la vez"). */
    public void shootVolley(LivingEntity target, boolean arc) {
        CreatureDefinition d = def();
        if (d == null || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        double ex = this.getX();
        double sy = this.getEyeY() - 0.1;
        double ez = this.getZ();
        double tx = target.getX() - ex;
        double tz = target.getZ() - ez;
        double flat = Math.sqrt(tx * tx + tz * tz);
        // Sale un poco por delante del cuerpo para no chocar consigo misma (y se apunta desde ahi)
        double out = Math.min(this.getBbWidth() * 0.5 + 0.2, flat * 0.5);
        double ox = flat > 1.0e-4 ? tx / flat * out : 0.0;
        double oz = flat > 1.0e-4 ? tz / flat * out : 0.0;
        double sx = ex + ox;
        double sz = ez + oz;
        double dx = target.getX() - sx;
        double dy = target.getY(0.5) - sy;
        double dz = target.getZ() - sz;
        float speed = d.projSpeed * 0.15f;
        double[] dir = Ballistics.aim(dx, dy, dz, speed, d.projGravity ? CreatureProjectile.GRAVITY : 0.0,
                d.projAccelerate, arc);
        float inaccuracy = (100 - d.rangedAccuracy) * 0.25f + (d.shotCount > 1 ? 1.5f : 0f);
        for (int i = 0; i < d.shotCount; i++) {
            CreatureProjectile p = ModRegistry.PROJECTILE.get().create(level);
            if (p == null) {
                return;
            }
            p.setOwner(this);
            p.configure(d);
            p.setPos(sx, sy - p.getBbHeight() / 2, sz);
            p.shoot(dir[0], dir[1], dir[2], speed, inaccuracy);
            level.addFreshEntity(p);
        }
        SoundEvent sound = soundById(d.fireSound.id);
        if (sound != null) {
            this.playSound(sound, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
        }
        this.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            CreatureDefinition d = def();
            if (d != null) {
                HitEffects.apply(d.hitEffect, living, this);
            }
        }
        return hit;
    }

    // ---------------------------------------------------------------------------------------
    // Resistencias y protecciones
    // ---------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        CreatureDefinition d = def();
        if (d != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float resist = resistance(d, source);
            if (resist >= 0.999f) {
                return false;
            }
            amount *= 1f - resist;
        }
        return super.hurt(source, amount);
    }

    /** Parte del dano (0-1) que se ahorra segun el tipo de ataque. */
    private static float resistance(CreatureDefinition d, DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return d.resistExplosion / 100f;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return d.resistArrow / 100f;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity && direct == source.getEntity()) {
            return d.resistMelee / 100f; // golpe directo de alguien
        }
        return 0f;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        CreatureDefinition d = def();
        if (isFlyer() || (d != null && d.noFallDamage)) {
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        CreatureDefinition d = def();
        if (d != null && !d.cobwebAffected && state.is(Blocks.COBWEB)) {
            return;
        }
        super.makeStuckInBlock(state, motionMultiplier);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        CreatureDefinition d = def();
        return !(d != null && d.potionImmune) && super.canBeAffected(effect);
    }

    @Override
    public boolean isAffectedByPotions() {
        CreatureDefinition d = def();
        return !(d != null && d.potionImmune) && super.isAffectedByPotions();
    }

    @Override
    public boolean fireImmune() {
        CreatureDefinition d = def();
        return (d != null && d.fireImmune) || super.fireImmune();
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
            if (!this.hasHome) {
                setHome(this.getX(), this.getY(), this.getZ());
            }
        } else if (d != null && (d.revision != this.dimsRevision || !d.id.equals(this.dimsId))) {
            this.refreshDimensions();
        }
    }

    @Override
    public void aiStep() {
        if (this.isAlive() && !this.level().isClientSide()) {
            CreatureDefinition d = def();
            if (d != null) {
                if (d.burnsInSun && !d.fireImmune && this.isSunBurnTick()) {
                    this.igniteForSeconds(8.0f);
                }
                if (!d.canDrown && this.getAirSupply() < this.getMaxAirSupply()) {
                    // "No se ahoga": en 1.21 canBreatheUnderwater() es final, asi que se le rellena el aire cada tick
                    this.setAirSupply(this.getMaxAirSupply());
                }
                regenerate(d);
                updateShield(d);
            }
        }
        super.aiStep();
    }

    /** Cada segundo recupera vida: la normal, o la de combate si esta peleando o la han herido hace poco. */
    private void regenerate(CreatureDefinition d) {
        if (this.tickCount % 20 != 0 || this.getHealth() >= this.getMaxHealth()) {
            return;
        }
        boolean combat = this.getTarget() != null
                || (this.getLastHurtByMob() != null && this.tickCount - this.getLastHurtByMobTimestamp() < 100);
        float amount = combat ? d.combatRegen : d.healthRegen;
        if (amount > 0f) {
            this.heal(amount);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        CreatureDefinition d = def();
        if (d == null || !d.respawn || (d.respawnLimit > 0 && this.respawnsUsed >= d.respawnLimit)) {
            return;
        }
        boolean home = d.respawnAtHome && this.hasHome;
        double x = home ? this.homeX : this.getX();
        double y = home ? this.homeY : this.getY();
        double z = home ? this.homeZ : this.getZ();
        RespawnData.get(level).add(new RespawnData.Entry(d.id, x, y, z, this.getYRot(),
                level.getGameTime() + d.respawnDelay * 20L, this.respawnsUsed + 1, this.isPersistenceRequired(),
                this.hasHome ? this.homeX : x, this.hasHome ? this.homeY : y, this.hasHome ? this.homeZ : z));
    }

    private void setHome(double x, double y, double z) {
        this.hasHome = true;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
    }

    /** Datos que hereda al reaparecer (cuantas veces ha vuelto y su sitio de origen). */
    public void setRespawnInfo(int used, double x, double y, double z) {
        this.respawnsUsed = used;
        setHome(x, y, z);
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
    protected Component getTypeName() {
        CreatureDefinition d = def();
        return d != null ? Component.literal(d.name) : super.getTypeName();
    }

    @Override
    public ItemStack getPickResult() {
        return CreatureEggItem.create(getCreatureId(), def());
    }

    /** Botin: cada objeto de la lista con su probabilidad y su cantidad. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        CreatureDefinition d = def();
        if (d == null) {
            return;
        }
        for (CreatureDrop drop : d.drops) {
            if (drop.max <= 0 || this.getRandom().nextInt(100) >= drop.chance) {
                continue;
            }
            ItemStack base = stackOf(drop.item);
            if (base.isEmpty()) {
                continue;
            }
            int count = drop.min + (drop.max > drop.min ? this.getRandom().nextInt(drop.max - drop.min + 1) : 0);
            while (count > 0) {
                int n = Math.min(count, base.getMaxStackSize());
                this.spawnAtLocation(base.copyWithCount(n));
                count -= n;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Sonidos
    // ---------------------------------------------------------------------------------------

    /** El sonido de Minecraft con ese identificador (o null si no existe o esta vacio). */
    @Nullable
    public static SoundEvent soundById(String id) {
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
        return d == null ? null : soundById(d.sound.ambient);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        CreatureDefinition d = def();
        return d == null ? null : soundById(d.sound.hurt);
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        CreatureDefinition d = def();
        return d == null ? null : soundById(d.sound.death);
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
        if (this.hasHome) {
            tag.putDouble("HomeX", this.homeX);
            tag.putDouble("HomeY", this.homeY);
            tag.putDouble("HomeZ", this.homeZ);
        }
        tag.putInt("RespawnsUsed", this.respawnsUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            setHome(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        }
        this.respawnsUsed = tag.getInt("RespawnsUsed");
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
        creature.setHome(x, y, z);
        creature.setCreatureId(d.id);
        if (persistent) {
            creature.setPersistenceRequired();
        }
        level.addFreshEntity(creature);
        return creature;
    }
}
