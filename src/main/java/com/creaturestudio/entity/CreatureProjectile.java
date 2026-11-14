package com.creaturestudio.entity;

import com.creaturestudio.data.Ballistics;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.HitEffect;
import com.creaturestudio.data.ShotSound;
import com.creaturestudio.data.TrailType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Proyectil de las criaturas: se ve como cualquier objeto (en 3D orientado hacia donde vuela o plano mirando a la
 * camara) y tiene fuerza, tamano, gravedad, aceleracion, explosion, efecto, estela, brillo, empuje y puede girar o
 * clavarse. Todo sale de la definicion de la criatura que lo dispara.
 */
public class CreatureProjectile extends Projectile {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(CreatureProjectile.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_FLAGS =
            SynchedEntityData.defineId(CreatureProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(CreatureProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TRAIL =
            SynchedEntityData.defineId(CreatureProjectile.class, EntityDataSerializers.INT);

    private static final int FLAG_3D = 1;
    private static final int FLAG_SPINS = 2;
    private static final int FLAG_GLOWS = 4;
    private static final int FLAG_STUCK = 8;
    private static final int FLAG_GRAVITY = 16;
    private static final int FLAG_ACCELERATE = 32;

    /** Gravedad por tick (la de las bolas de nieve). */
    public static final double GRAVITY = Ballistics.GRAVITY;
    /** Lo que se acelera por tick un proyectil que acelera, y hasta cuantas veces su velocidad inicial. */
    private static final double ACCELERATION = Ballistics.ACCELERATION;
    private static final double MAX_BOOST = Ballistics.MAX_BOOST;
    private static final int MAX_LIFE = Ballistics.MAX_LIFE;
    private static final int STUCK_LIFE = 100;

    // Solo en el servidor
    private float damage = 4f;
    private float knockback = 0f;
    private float explosion = 0f;
    private HitEffect effect = HitEffect.NONE;
    private boolean sticks;
    private ShotSound hitSound = ShotSound.NONE;
    private ShotSound groundSound = ShotSound.NONE;
    private int life;
    private int stuckTicks;
    /** Velocidad al salir (para limitar lo que acelera). */
    private double startSpeed;

    public CreatureProjectile(EntityType<? extends CreatureProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_FLAGS, FLAG_3D | FLAG_GRAVITY);
        builder.define(DATA_SCALE, 1f);
        builder.define(DATA_TRAIL, 0);
    }

    /** Copia del proyectil de la definicion todo lo que hace falta. */
    public void configure(CreatureDefinition d) {
        Item item = Items.SNOWBALL;
        ResourceLocation id = ResourceLocation.tryParse(d.projItem);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            Item found = BuiltInRegistries.ITEM.get(id);
            if (found != Items.AIR) {
                item = found;
            }
        }
        this.entityData.set(DATA_ITEM, new ItemStack(item));
        int flags = 0;
        if (d.proj3D) {
            flags |= FLAG_3D;
        }
        if (d.projSpins && d.proj3D) {
            flags |= FLAG_SPINS;
        }
        if (d.projGlows) {
            flags |= FLAG_GLOWS;
        }
        if (d.projGravity) {
            flags |= FLAG_GRAVITY;
        }
        if (d.projAccelerate) {
            flags |= FLAG_ACCELERATE;
        }
        this.entityData.set(DATA_FLAGS, flags);
        this.entityData.set(DATA_SCALE, d.projSize / 5f);
        this.entityData.set(DATA_TRAIL, d.projTrail.ordinal());
        this.damage = d.projStrength;
        this.knockback = d.projKnockback;
        this.explosion = d.projExplode.power;
        this.effect = d.projEffect;
        this.sticks = d.projSticks && d.proj3D; // solo los 3D se clavan (como en el editor)
        this.hitSound = d.hitSound;
        this.groundSound = d.groundSound;
        this.refreshDimensions();
    }

    // ---------------------------------------------------------------------------------------
    // Datos para el dibujado
    // ---------------------------------------------------------------------------------------

    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM);
    }

    private boolean flag(int f) {
        return (this.entityData.get(DATA_FLAGS) & f) != 0;
    }

    private void setFlag(int f, boolean on) {
        int flags = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, on ? flags | f : flags & ~f);
    }

    public boolean is3D() {
        return flag(FLAG_3D);
    }

    public boolean spins() {
        return flag(FLAG_SPINS);
    }

    public boolean glows() {
        return flag(FLAG_GLOWS);
    }

    public boolean isStuck() {
        return flag(FLAG_STUCK);
    }

    public float visualScale() {
        return this.entityData.get(DATA_SCALE);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(Math.max(0.2f, visualScale()));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected double getDefaultGravity() {
        return flag(FLAG_GRAVITY) ? GRAVITY : 0.0;
    }

    // ---------------------------------------------------------------------------------------
    // Vuelo
    // ---------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        boolean client = this.level().isClientSide();
        if (isStuck()) {
            this.setDeltaMovement(Vec3.ZERO);
            if (!client && ++this.stuckTicks > STUCK_LIFE) {
                this.discard();
            }
            return;
        }
        Vec3 v = this.getDeltaMovement();
        if (this.startSpeed <= 0.0) {
            this.startSpeed = Math.max(0.05, v.length());
        }
        if (!client) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.hitTargetOrDeflectSelf(hit);
                if (this.isRemoved() || isStuck()) {
                    return;
                }
                v = this.getDeltaMovement(); // puede haber rebotado (cargas de viento)
            }
        }
        this.checkInsideBlocks();
        double nx = this.getX() + v.x;
        double ny = this.getY() + v.y;
        double nz = this.getZ() + v.z;
        this.updateRotation();
        if (client) {
            spawnTrail(v);
        }
        double factor;
        if (flag(FLAG_ACCELERATE)) {
            factor = this.isInWater() ? 0.9 : ACCELERATION;
        } else {
            factor = this.isInWater() ? 0.8 : Ballistics.DRAG;
        }
        v = v.scale(factor);
        double max = this.startSpeed * MAX_BOOST;
        if (v.lengthSqr() > max * max) {
            v = v.normalize().scale(max);
        }
        this.setDeltaMovement(v);
        this.applyGravity();
        this.setPos(nx, ny, nz);
        if (!client && ++this.life > MAX_LIFE) {
            this.discard();
        }
    }

    private void spawnTrail(Vec3 v) {
        int trail = this.entityData.get(DATA_TRAIL);
        TrailType[] all = TrailType.values();
        if (trail <= 0 || trail >= all.length) {
            return;
        }
        ParticleOptions particle = particle(all[trail]);
        if (particle == null) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            double f = i / 2.0;
            this.level().addParticle(particle, this.getX() - v.x * f, this.getY() + this.getBbHeight() / 2 - v.y * f,
                    this.getZ() - v.z * f, 0.0, 0.0, 0.0);
        }
    }

    @Nullable
    private static ParticleOptions particle(TrailType t) {
        return switch (t) {
            case SMOKE -> ParticleTypes.SMOKE;
            case LARGE_SMOKE -> ParticleTypes.LARGE_SMOKE;
            case FLAME -> ParticleTypes.FLAME;
            case SOUL_FLAME -> ParticleTypes.SOUL_FIRE_FLAME;
            case PORTAL -> ParticleTypes.PORTAL;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case CRIT -> ParticleTypes.CRIT;
            case MAGIC_CRIT -> ParticleTypes.ENCHANTED_HIT;
            case WITCH -> ParticleTypes.WITCH;
            case ENCHANT -> ParticleTypes.ENCHANT;
            case HEART -> ParticleTypes.HEART;
            case SNOWFLAKE -> ParticleTypes.SNOWFLAKE;
            case END_ROD -> ParticleTypes.END_ROD;
            case CLOUD -> ParticleTypes.CLOUD;
            case NOTE -> ParticleTypes.NOTE;
            case DRAGON_BREATH -> ParticleTypes.DRAGON_BREATH;
            case GLOW -> ParticleTypes.GLOW;
            case ASH -> ParticleTypes.ASH;
            case CHERRY -> ParticleTypes.CHERRY_LEAVES;
            case LAVA -> ParticleTypes.DRIPPING_LAVA;
            case SPIT -> ParticleTypes.SPIT;
            default -> null;
        };
    }

    // ---------------------------------------------------------------------------------------
    // Impactos
    // ---------------------------------------------------------------------------------------

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        // No le da a los de su misma especie ni a sus aliados (para que un grupo no se mate entre si)
        Entity owner = this.getOwner();
        return !(owner instanceof CustomCreature shooter && shooter.isAllyOf(target));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null);
        // Si lo para (escudo, invulnerable...) no hay empuje ni efecto, como con las flechas
        float before = target instanceof LivingEntity l ? CustomCreature.lifeOf(l) : 0f;
        boolean hit = this.damage <= 0f || target.hurt(source, this.damage);
        if (hit && owner instanceof CustomCreature shooter && target instanceof LivingEntity victim) {
            shooter.stealLife(victim, before); // roba vida tambien con los proyectiles
        }
        if (hit && target instanceof LivingEntity living && living.isAlive()) {
            if (this.knockback > 0f) {
                Vec3 dir = this.getDeltaMovement().multiply(1.0, 0.0, 1.0);
                if (dir.lengthSqr() > 1.0e-6) {
                    double resist = Math.max(0.0, 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    dir = dir.normalize().scale(this.knockback * 0.6 * resist);
                    living.push(dir.x, 0.1, dir.z);
                    living.hurtMarked = true; // para que el jugador reciba el empuje
                }
            }
            HitEffects.apply(this.effect, living, owner);
        }
        playSound(this.hitSound);
        if (this.explosion > 0f) {
            explode();
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        playSound(this.groundSound);
        if (this.explosion > 0f) {
            explode();
            this.discard();
            return;
        }
        if (this.sticks) {
            Vec3 at = result.getLocation();
            Vec3 back = this.getDeltaMovement().normalize().scale(0.06);
            this.setPos(at.x - back.x, at.y - back.y, at.z - back.z);
            this.setDeltaMovement(Vec3.ZERO);
            setFlag(FLAG_STUCK, true);
            return;
        }
        this.discard();
    }

    private void explode() {
        this.level().explode(this, this.getX(), this.getY(), this.getZ(), this.explosion, Level.ExplosionInteraction.MOB);
    }

    private void playSound(ShotSound sound) {
        SoundEvent event = CustomCreature.soundById(sound.id);
        if (event != null) {
            this.playSound(event, 1.0f, 1.1f / (this.random.nextFloat() * 0.2f + 0.9f));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Guardado (por si el mundo se guarda con el proyectil en el aire)
    // ---------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Item", BuiltInRegistries.ITEM.getKey(getItem().getItem()).toString());
        tag.putInt("Flags", this.entityData.get(DATA_FLAGS));
        tag.putFloat("Scale", visualScale());
        tag.putInt("Trail", this.entityData.get(DATA_TRAIL));
        tag.putFloat("Damage", this.damage);
        tag.putFloat("Knockback", this.knockback);
        tag.putFloat("Explosion", this.explosion);
        tag.putString("Effect", this.effect.name());
        tag.putBoolean("Sticks", this.sticks);
        tag.putString("HitSound", this.hitSound.name());
        tag.putString("GroundSound", this.groundSound.name());
        tag.putInt("Life", this.life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Item"));
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            this.entityData.set(DATA_ITEM, new ItemStack(BuiltInRegistries.ITEM.get(id)));
        }
        this.entityData.set(DATA_FLAGS, tag.getInt("Flags"));
        this.entityData.set(DATA_SCALE, Math.max(0.2f, Math.min(4f, tag.getFloat("Scale"))));
        this.entityData.set(DATA_TRAIL, tag.getInt("Trail"));
        this.damage = tag.getFloat("Damage");
        this.knockback = tag.getFloat("Knockback");
        this.explosion = Math.min(4f, tag.getFloat("Explosion"));
        this.effect = enumOr(HitEffect.class, tag.getString("Effect"), HitEffect.NONE);
        this.sticks = tag.getBoolean("Sticks");
        this.hitSound = enumOr(ShotSound.class, tag.getString("HitSound"), ShotSound.NONE);
        this.groundSound = enumOr(ShotSound.class, tag.getString("GroundSound"), ShotSound.NONE);
        this.life = tag.getInt("Life");
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
