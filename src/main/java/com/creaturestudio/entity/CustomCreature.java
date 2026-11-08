package com.creaturestudio.entity;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.Ballistics;
import com.creaturestudio.data.Behavior;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureDrop;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.DoorMode;
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
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemUtils;
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
    /** Creeper: 1 = se hincha, -1 = se deshincha. */
    private static final EntityDataAccessor<Integer> DATA_SWELL_DIR =
            SynchedEntityData.defineId(CustomCreature.class, EntityDataSerializers.INT);
    /** Guardian: entidad a la que apunta el rayo (0 = ninguna). */
    private static final EntityDataAccessor<Integer> DATA_BEAM_TARGET =
            SynchedEntityData.defineId(CustomCreature.class, EntityDataSerializers.INT);
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

    // Habilidades
    private final CreatureAbilities abilities = new CreatureAbilities(this);
    /** Slime: 0 = la original; 1, 2... = pedazos (mas pequenos y sin reaparicion). */
    private int generation;
    /** Invocada por otra criatura: no invoca ni reaparece. */
    private boolean summoned;
    /** Ya exploto (creeper): no se divide. */
    private boolean exploded;
    private int swell;
    private int oldSwell;
    private int clientBeamTime;
    int explodeCooldown;
    int beamCooldown;
    int sonicCooldown;
    int chargeCooldown;
    private int milkTimer;

    /** Animaciones propias (cliente): tick en que empezo cada accion de una vez (atacar, disparar...). */
    private final int[] actionStart = newActionStarts();
    private static final int NEVER = Integer.MIN_VALUE;
    private int lastHurtTime;
    /** Eventos de entidad que mandan las acciones al cliente (EVENT_BASE + orden de la accion). */
    private static final byte EVENT_BASE = 100;

    // Para que las condiciones de los objetivos de IA no pregunten la definicion a cada momento
    private boolean appliedClimb;
    private boolean appliedDoors;

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
        builder.define(DATA_SWELL_DIR, -1);
        builder.define(DATA_BEAM_TARGET, 0);
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
        CreatureDefinition d = def();
        return behavior() != Behavior.PASSIVE || (d != null && d.abilities.stareAnger);
    }

    /** Puede atacar ahora (para las habilidades de ataque). */
    boolean canFightNow() {
        return canFight();
    }

    private boolean blind() {
        CreatureDefinition d = def();
        return d != null && d.abilities.blind;
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
        // Los pedazos de un slime son mas pequenos (y tienen menos vida y fuerza)
        double size = Math.pow(0.5, this.generation);
        setBase(Attributes.MAX_HEALTH, Math.max(1.0, d.health * size));
        setBase(Attributes.ATTACK_DAMAGE, d.damage * size);
        setBase(Attributes.SCALE, size);
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
        this.setCanPickUpLoot(d.abilities.pickUp);
        updateMovement(d.movement, d.abilities.climb, d.abilities.doors != DoorMode.NONE);
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

    private void updateMovement(Movement movement, boolean climb, boolean doors) {
        boolean wantClimb = climb && movement != Movement.FLY;
        if (movement == this.appliedMovement && wantClimb == this.appliedClimb && doors == this.appliedDoors) {
            return;
        }
        this.appliedClimb = wantClimb;
        this.appliedDoors = doors;
        if (movement == Movement.FLY) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            FlyingPathNavigation nav = new FlyingPathNavigation(this, this.level());
            nav.setCanFloat(true);
            this.navigation = nav;
            this.setNoGravity(true);
        } else {
            this.moveControl = new MoveControl(this);
            // Arana: navegacion que trepa paredes
            GroundPathNavigation nav = wantClimb ? new WallClimberNavigation(this, this.level())
                    : new GroundPathNavigation(this, this.level());
            nav.setCanFloat(true);
            nav.setCanOpenDoors(doors);
            this.navigation = nav;
            this.setNoGravity(false);
        }
        this.appliedMovement = movement;
    }

    // ---------------------------------------------------------------------------------------
    // Equipo
    // ---------------------------------------------------------------------------------------

    static ItemStack stackOf(String id) {
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
                () -> behavior() == Behavior.PASSIVE && !canFight()));
        // Habilidades con su propio movimiento (creeper, rayo, grito sonico, embestida)
        this.goalSelector.addGoal(1, new CreatureSwellGoal(this));
        this.goalSelector.addGoal(1, new CreatureBeamGoal(this));
        this.goalSelector.addGoal(1, new CreatureSonicBoomGoal(this));
        this.goalSelector.addGoal(1, new CreatureChargeGoal(this));
        this.goalSelector.addGoal(1, new ConditionalGoal(new BreakDoorGoal(this, difficulty -> true),
                () -> doorMode() == DoorMode.BREAK));
        // Por delante del ataque (como la arana y el creeper), si no el ataque no les deja moverse
        this.goalSelector.addGoal(1, new ConditionalGoal(new LeapAtTargetGoal(this, 0.4f),
                () -> canFight() && ability(a -> a.leap)));
        this.goalSelector.addGoal(1, new CreatureFleeGoal(this));
        this.goalSelector.addGoal(4, new ConditionalGoal(new OpenDoorGoal(this, true),
                () -> doorMode() == DoorMode.OPEN));
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
                () -> hostileNow() && !seesThroughWalls() && !blind()));
        this.targetSelector.addGoal(2, new ConditionalGoal(new CreatureTargetGoal(this, true),
                () -> hostileNow() && seesThroughWalls() && !blind()));
        // Warden: ciega, encuentra a los jugadores por el ruido
        this.targetSelector.addGoal(2, new HearingTargetGoal(this));
    }

    private boolean ability(java.util.function.Predicate<com.creaturestudio.data.Abilities> test) {
        CreatureDefinition d = def();
        return d != null && test.test(d.abilities);
    }

    private DoorMode doorMode() {
        CreatureDefinition d = def();
        return d == null || isFlyer() ? DoorMode.NONE : d.abilities.doors;
    }

    /** Creeper y gatos: huye de los mobs de ese tipo (o de las criaturas propias con ese id). */
    boolean fears(LivingEntity other) {
        CreatureDefinition d = def();
        if (d == null || !d.abilities.fear || other == this || this.distanceTo(other) > d.abilities.fearRange) {
            return false;
        }
        String id = d.abilities.fearEntity;
        if (other instanceof CustomCreature c && !id.contains(":")) {
            return c.getCreatureId().equals(id);
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(other.getType()).toString();
        return type.equals(id.contains(":") ? id : "minecraft:" + id);
    }

    boolean hostileNow() {
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
        playAction(AnimAction.SHOOT);
        this.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        CreatureDefinition d = def();
        if (hit && d != null) {
            if (target instanceof LivingEntity living) {
                HitEffects.apply(d.hitEffect, living, this);
            }
            abilities.afterAttack(d, target);
        }
        return hit;
    }

    // ---------------------------------------------------------------------------------------
    // Resistencias y protecciones
    // ---------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        CreatureDefinition d = def();
        boolean server = !this.level().isClientSide();
        if (d != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (server && !this.isInvulnerableTo(source) && abilities.dodge(d, source)) {
                return false; // enderman: se teletransporta antes de que llegue el proyectil
            }
            float resist = resistance(d, source);
            if (resist >= 0.999f) {
                return false;
            }
            amount *= 1f - resist;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && d != null && server) {
            abilities.afterHurt(d, source);
        }
        return hurt;
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
        if (isFlyer() || (d != null && (d.noFallDamage || d.abilities.slowFall))) {
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
        CreatureDefinition before = def();
        if (this.isAlive() && before != null && before.abilities.explode) {
            tickSwell(before);
        } else {
            this.oldSwell = this.swell;
        }
        super.tick();
        CreatureDefinition d = def();
        if (this.level().isClientSide()) {
            this.clientBeamTime = getBeamTarget() != 0 ? this.clientBeamTime + 1 : 0;
            if (this.hurtTime > this.lastHurtTime) {
                this.actionStart[AnimAction.HURT.ordinal()] = this.tickCount;
            }
            this.lastHurtTime = this.hurtTime;
        } else {
            if (this.explodeCooldown > 0) {
                this.explodeCooldown--;
            }
            if (this.beamCooldown > 0) {
                this.beamCooldown--;
            }
            if (this.sonicCooldown > 0) {
                this.sonicCooldown--;
            }
            if (this.chargeCooldown > 0) {
                this.chargeCooldown--;
            }
            if (this.milkTimer > 0) {
                this.milkTimer--;
            }
        }
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
                if (this.level() instanceof ServerLevel level) {
                    abilities.serverTick(d, level);
                }
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
        boolean wasDead = this.dead;
        super.die(source);
        // Solo la primera vez que muere de verdad (otro mod puede cancelar la muerte)
        if (wasDead || !this.dead || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        CreatureDefinition d = def();
        if (d == null) {
            return;
        }
        if (d.abilities.split && !this.exploded && this.generation < d.abilities.splitGenerations) {
            splitInto(level, d);
        }
        scheduleRespawn(level, d);
    }

    /** Slime: se divide en pedazos mas pequenos. */
    private void splitInto(ServerLevel level, CreatureDefinition d) {
        int n = d.abilities.splitCount;
        float w = this.getBbWidth();
        for (int i = 0; i < n; i++) {
            double ox = ((i % 2) - 0.5) * w * 0.5;
            double oz = ((i / 2) - 0.5) * w * 0.5;
            spawnChild(level, d, this.getX() + ox, this.getY() + 0.5, this.getZ() + oz,
                    this.getRandom().nextFloat() * 360.0f, this.generation + 1, this.summoned,
                    this.isPersistenceRequired());
        }
    }

    /** Programa la reaparicion (solo la original: ni los pedazos ni las invocadas). */
    private void scheduleRespawn(ServerLevel level, CreatureDefinition d) {
        if (this.generation > 0 || this.summoned || !d.respawn
                || (d.respawnLimit > 0 && this.respawnsUsed >= d.respawnLimit)) {
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
        } else if (DATA_BEAM_TARGET.equals(key)) {
            this.clientBeamTime = 0;
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
    // Habilidades
    // ---------------------------------------------------------------------------------------

    public int getSwellDir() {
        return this.entityData.get(DATA_SWELL_DIR);
    }

    void setSwellDir(int dir) {
        this.entityData.set(DATA_SWELL_DIR, dir);
    }

    /** Creeper: cuanto se ha hinchado (0 a 1) para el dibujado. */
    public float getSwelling(float partialTick) {
        CreatureDefinition d = def();
        int fuse = d == null ? 30 : d.abilities.explodeFuse;
        return Mth.lerp(partialTick, (float) this.oldSwell, (float) this.swell) / Math.max(1, fuse - 2);
    }

    private void tickSwell(CreatureDefinition d) {
        this.oldSwell = this.swell;
        int dir = getSwellDir();
        if (dir > 0 && this.swell == 0 && !this.level().isClientSide()) {
            playSoundId("entity.creeper.primed", 1.0f, 0.5f);
        }
        this.swell += dir;
        if (this.swell < 0) {
            this.swell = 0;
        }
        int fuse = d.abilities.explodeFuse;
        if (this.swell >= fuse) {
            this.swell = fuse;
            if (this.level() instanceof ServerLevel level) {
                explodeNow(level, d);
            }
        }
    }

    private void explodeNow(ServerLevel level, CreatureDefinition d) {
        float power = switch (d.abilities.explodeSize) {
            case SMALL -> 2.0f;
            case BIG -> 5.0f;
            default -> 3.0f; // como un creeper
        };
        level.explode(this, this.getX(), this.getY(), this.getZ(), power, Level.ExplosionInteraction.MOB);
        if (d.abilities.explodeDies) {
            this.exploded = true;
            scheduleRespawn(level, d);
            this.discard();
        } else {
            this.swell = 0;
            this.oldSwell = 0;
            setSwellDir(-1);
            this.explodeCooldown = d.abilities.explodeCooldown * 20;
        }
    }

    /** A quien hace "sss": su objetivo si esta cerca o, si no, el jugador mas cercano que se acerque. */
    @Nullable
    LivingEntity swellTrigger(CreatureDefinition d) {
        float r = d.abilities.explodeRange;
        LivingEntity t = this.getTarget();
        if (t != null && t.isAlive() && this.distanceToSqr(t) <= r * r && this.getSensing().hasLineOfSight(t)) {
            return t;
        }
        return this.level().getNearestPlayer(TargetingConditions.forCombat().range(r), this);
    }

    public int getBeamTarget() {
        return this.entityData.get(DATA_BEAM_TARGET);
    }

    void setBeamTarget(int id) {
        this.entityData.set(DATA_BEAM_TARGET, id);
    }

    /** Guardian: carga del rayo de 0 a 1 (en el cliente). */
    public float getBeamProgress(float partialTick) {
        CreatureDefinition d = def();
        int charge = d == null ? 60 : d.abilities.beamCharge;
        return Mth.clamp((this.clientBeamTime + partialTick) / charge, 0.0f, 1.0f);
    }

    /** Ticks que lleva el rayo encendido (en el cliente, para animar la textura). */
    public float getBeamTime(float partialTick) {
        return this.clientBeamTime + partialTick;
    }

    // ---------------------------------------------------------------------------------------
    // Animaciones propias
    // ---------------------------------------------------------------------------------------

    /** Servidor: avisa a los clientes de que empieza una accion (si la criatura tiene animacion para ella). */
    void playAction(AnimAction a) {
        CreatureDefinition d = def();
        if (d != null && d.customAnims.has(a) && !this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, (byte) (EVENT_BASE + a.ordinal()));
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        int a = id - EVENT_BASE;
        if (a >= 0 && a < AnimAction.COUNT) {
            this.actionStart[a] = this.tickCount;
            return;
        }
        super.handleEntityEvent(id);
    }

    /**
     * Cliente: ticks desde que empezo cada accion de una vez (para {@code AnimPlayer}); morir va con el tiempo
     * que lleva muerta. ageInTicks = tickCount + fraccion del tick.
     */
    public float[] actionAges(float ageInTicks) {
        float[] ages = new float[AnimAction.COUNT];
        float partial = ageInTicks - this.tickCount;
        for (int i = 0; i < ages.length; i++) {
            int start = this.actionStart[i];
            ages[i] = start == NEVER ? -1f : (this.tickCount - start) + partial;
        }
        ages[AnimAction.DEATH.ordinal()] = this.deathTime > 0 ? this.deathTime - 1 + partial : -1f;
        return ages;
    }

    private static int[] newActionStarts() {
        int[] a = new int[AnimAction.COUNT];
        java.util.Arrays.fill(a, NEVER);
        return a;
    }

    boolean isSummoned() {
        return this.summoned;
    }

    void playSoundId(String id, float volume, float pitch) {
        SoundEvent sound = soundById(id);
        if (sound != null && !this.isSilent()) {
            this.playSound(sound, volume, pitch);
        }
    }

    /** Arana: trepa cuando choca con una pared. */
    @Override
    public boolean onClimbable() {
        CreatureDefinition d = def();
        if (d != null && d.abilities.climb && !isFlyer() && this.horizontalCollision) {
            return true;
        }
        return super.onClimbable();
    }

    /** Breeze: devuelve los proyectiles (la misma decision en el cliente y en el servidor). */
    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        CreatureDefinition d = def();
        if (d != null && d.abilities.deflect
                && Math.floorMod(projectile.getUUID().hashCode(), 100) < d.abilities.deflectChance) {
            if (!this.level().isClientSide()) {
                playSoundId("entity.breeze.deflect", 1.0f, 1.0f);
            }
            return ProjectileDeflection.REVERSE;
        }
        return super.deflection(projectile);
    }

    /** Vaca: tocarla con un objeto da otro (cubo -> cubo de leche). */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        CreatureDefinition d = def();
        ItemStack held = player.getItemInHand(hand);
        if (d != null && d.abilities.milkable && !held.isEmpty() && !d.abilities.milkWith.isEmpty()
                && BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(d.abilities.milkWith)) {
            ItemStack result = stackOf(d.abilities.milkGives);
            if (result.isEmpty()) {
                return super.mobInteract(player, hand);
            }
            if (!this.level().isClientSide()) {
                if (this.milkTimer > 0) {
                    return InteractionResult.PASS;
                }
                SoundEvent sound = soundById("entity.cow.milk");
                if (sound != null) {
                    player.playSound(sound, 1.0f, 1.0f);
                }
                if (d.abilities.milkUsesItem) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, result));
                } else {
                    if (!player.getInventory().add(result)) {
                        player.drop(result, false);
                    }
                    if (held.isDamageableItem()) {
                        held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                    }
                }
                this.milkTimer = d.abilities.milkCooldown * 20;
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return super.mobInteract(player, hand);
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
        if (this.generation > 0) {
            tag.putInt("Generation", this.generation);
        }
        if (this.summoned) {
            tag.putBoolean("Summoned", true);
        }
        if (abilities.layTimer >= 0) {
            tag.putInt("LayTime", abilities.layTimer);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            setHome(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        }
        this.respawnsUsed = tag.getInt("RespawnsUsed");
        this.generation = Math.max(0, Math.min(3, tag.getInt("Generation")));
        this.summoned = tag.getBoolean("Summoned");
        abilities.layTimer = tag.contains("LayTime") ? tag.getInt("LayTime") : -1;
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
        return spawnChild(level, d, x, y, z, yaw, 0, false, persistent);
    }

    /** Como {@link #spawn}, para los pedazos de un slime (generation mayor que 0) y las criaturas invocadas. */
    @Nullable
    public static CustomCreature spawnChild(ServerLevel level, CreatureDefinition d, double x, double y, double z,
                                            float yaw, int generation, boolean summoned, boolean persistent) {
        CustomCreature creature = ModRegistry.CUSTOM_CREATURE.get().create(level);
        if (creature == null) {
            return null;
        }
        creature.generation = generation;
        creature.summoned = summoned;
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
