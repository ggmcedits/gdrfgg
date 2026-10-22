package com.lt.bloodyfiend.entity;

import com.lt.bloodyfiend.event.CommonEvents;
import com.lt.bloodyfiend.network.ModNetwork;
import com.lt.bloodyfiend.registry.ModDamageTypes;
import com.lt.bloodyfiend.registry.ModEffects;
import com.lt.bloodyfiend.registry.ModEntities;
import com.lt.bloodyfiend.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

/**
 * lt:bloody_fiend. Server logic is a direct transcription of
 * entities/blueprint/boss_mob/bloody_fiend.json (component groups / timers / events)
 * and scripts/entities/boss_mobs/bloody_fiend.js (all impact functions).
 * Client animation selection mirrors the three root animation controllers.
 */
public class BloodyFiendEntity extends Monster implements GeoEntity {
    public static final float SCALE = 1.7638F;                       // minecraft:scale
    private static final float[][] BOXES = { {1.25F, 3.55F}, {1.25F, 5.05F}, {1.75F, 1.15F}, {3.15F, 0.95F} };
    private static final int BOX_SLUMBER = 0, BOX_STANDING = 1, BOX_FLOAT = 2, BOX_CORPSE = 3;
    private static final String HIGH_TENACITY = "lt:mob_attribute:high_tenacity";

    /** property lt:state */
    public enum State { SLUMBER, AWAKEN, STANDING, PLAY_DEAD, CROUCH, CORPSE, OPENED_MOUTH, FADING_BODY }
    /** property lt:skill_id */
    public enum Skill { INITIAL, ATTACK, COMBO, ROAR, SMASH, THUMP, STOMP, BITE, DEVOUR }

    private enum Ph {
        IDLE,
        ATTACK_BEGIN, ATTACK_FINISH,
        COMBO_BEGIN, COMBO_MIDDLE, COMBO_FINISH_PRE, COMBO_FINISH_POST,
        ROAR_BEGIN, ROAR_MIDDLE, ROAR_FINISH,
        SMASH_BEGIN, SMASH_FINISH,
        THUMP_BEGIN, THUMP_MIDDLE, THUMP_FINISH,
        STOMP_APPROACH, STOMP_MIDDLE, STOMP_FINISH,
        BITE_APPROACH, BITE_BEGIN, BITE_FINISH,
        DEVOUR_BEGIN, DEVOUR_MIDDLE, DEVOUR_FINISH,
        CROUCH_ATTACK_BEGIN, CROUCH_ATTACK_FINISH,
        CROUCH_COMBO_BEGIN, CROUCH_COMBO_MIDDLE, CROUCH_COMBO_FINISH,
        CROUCH_THUMP_BEGIN, CROUCH_THUMP_FINISH,
        CROUCH_SMASH_BEGIN, CROUCH_SMASH_MIDDLE, CROUCH_SMASH_FINISH
    }

    private enum Cause { ENTITY_ATTACK, CONTACT, OVERRIDE, SELF_DESTRUCT }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(BloodyFiendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKILL = SynchedEntityData.defineId(BloodyFiendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOX = SynchedEntityData.defineId(BloodyFiendEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    // ---- server-only runtime state (component groups) ----
    private Ph phase = Ph.IDLE;
    private int phaseTicks;
    private int subTicks;
    private int stateTicks;
    private int maxTurn = 20;            // movement.basic max_turn
    private boolean faceTarget;          // towards_target group
    private boolean pushableFlag;        // pushable_initial group
    private boolean roaring;             // lt:roaring
    private boolean invulnerable;        // lt:invulnerable
    private int smashCd;                 // lt:crawl.smash_cd (animation timeline 3.99s)
    private int resistanceLevel;         // lt:resistance_level
    private int quakeMode, quakeT;       // earthquake animation controller
    private final List<Object[]> pendingReflect = new ArrayList<>();

    // ---- client-only ----
    private String lastAnim = "";

    private static final int[] DEAD_AT = cumulative(1.33, 0.34, 0.37, 0.63, 0.33, 0.54, 0.46, 0.54, 0.97, 5.23);

    public BloodyFiendEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.moveControl = new FiendMoveControl(this);
        this.addTag(HIGH_TENACITY);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 660.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D);
    }

    // ------------------------------------------------------------------ data / helpers
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, State.SLUMBER.ordinal());
        entityData.define(DATA_SKILL, Skill.INITIAL.ordinal());
        entityData.define(DATA_BOX, BOX_SLUMBER);
    }

    public State fiendState() { return State.values()[Mth.clamp(entityData.get(DATA_STATE), 0, State.values().length - 1)]; }
    public Skill skill() { return Skill.values()[Mth.clamp(entityData.get(DATA_SKILL), 0, Skill.values().length - 1)]; }
    private void setFState(State s) { entityData.set(DATA_STATE, s.ordinal()); }
    private void setSkill(Skill s) { entityData.set(DATA_SKILL, s.ordinal()); }
    private void setBox(int b) { entityData.set(DATA_BOX, b); }
    private void speed(double v) { getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(v); }
    private static int t(double sec) { return Math.max(1, (int) Math.round(sec * 20.0)); }
    private static int[] cumulative(double... d) { int[] r = new int[d.length]; int s = 0; for (int i = 0; i < d.length; i++) { s += t(d[i]); r[i] = s; } return r; }

    /** general_preset is active (boss bar, targeting, skills). */
    public boolean combatActive() { State s = fiendState(); return s == State.STANDING || s == State.CROUCH; }
    private boolean inanimate() { State s = fiendState(); return s == State.PLAY_DEAD || s == State.CORPSE || s == State.OPENED_MOUTH || s == State.FADING_BODY; }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_BOX.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        float[] d = BOXES[Mth.clamp(entityData.get(DATA_BOX), 0, 3)];
        return EntityDimensions.fixed(d[0] * SCALE, d[1] * SCALE);
    }
    @Override public AABB getBoundingBoxForCulling() { // visible_bounds 7 x 8, offset y 3
        double hw = 3.5 * SCALE, lo = (3 - 4) * SCALE, hi = (3 + 4) * SCALE;
        return new AABB(getX() - hw, getY() + lo, getZ() - hw, getX() + hw, getY() + hi, getZ() + hw);
    }

    private Vec3 vH() { Vec3 v = getViewVector(1.0F); return new Vec3(v.x, 0, v.z); }   // not renormalised, as in the script
    private Vec3 vK(LivingEntity tg) {
        if (tg == null) return Vec3.ZERO;
        Vec3 d = tg.position().subtract(position());
        double l = d.length(); if (l == 0) return Vec3.ZERO;
        return new Vec3(d.x / l, 0, d.z / l);
    }
    private Vec3 vD(LivingEntity tg) {
        if (tg == null) return Vec3.ZERO;
        Vec3 d = tg.position().subtract(position());
        double l = d.length(); return l == 0 ? Vec3.ZERO : d.scale(1.0 / l);
    }
    private double eDist() { LivingEntity tg = getTarget(); return tg == null ? 0 : position().distanceTo(tg.position()); }
    private float hpp() { return getHealth() / getMaxHealth(); }
    private float dyn(float dmg) { return dmg * (1.7F - hpp() * 0.7F); }
    private static double cosin(Vec3 a, Vec3 b) { double la = a.length(), lb = b.length(); return (la == 0 || lb == 0) ? 0 : a.dot(b) / (la * lb); }
    private boolean inFront(Vec3 a, Vec3 b, Vec3 vec, double std) {
        Vec3 n = new Vec3(b.x - a.x, 0, b.z - a.z);
        double l = n.length(); if (l == 0) return false;
        return cosin(vec, n.scale(1.0 / l)) > std;
    }
    /** LTsMath.offset: r = left, s = up, t = forward (vd horizontal). */
    private Vec3 offset(Vec3 pos, Vec3 vh, double r, double s, double t) {
        double l = vh.length(); Vec3 vd = l == 0 ? Vec3.ZERO : vh.scale(1.0 / l);
        return pos.add(vd.x * t + vd.z * r, s, vd.z * t - vd.x * r);
    }

    // ------------------------------------------------------------------ AI
    @Override protected void registerGoals() {
        this.goalSelector.addGoal(1, new ChaseGoal(this));
        this.goalSelector.addGoal(9, new RandomStrollGoal(this, 1.0D) {
            @Override public boolean canUse() { return combatActive() && super.canUse(); }
        });
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return combatActive() && super.canUse(); }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<LivingEntity>(this, LivingEntity.class, 10, true, false,
                e -> !e.getType().is(ModEntities.FIENDS) && !(e instanceof ArmorStand)) {
            @Override public boolean canUse() { return combatActive() && super.canUse(); }
            @Override public boolean canContinueToUse() { return combatActive() && super.canContinueToUse(); }
        });
    }

    /** melee_attack with cooldown 999 / damage 0: it only exists to path toward the target. */
    static class ChaseGoal extends MeleeAttackGoal {
        ChaseGoal(BloodyFiendEntity m) { super(m, 1.0D, true); }
        @Override public boolean canUse() { return ((BloodyFiendEntity) mob).combatActive() && super.canUse(); }
        @Override public boolean canContinueToUse() { return ((BloodyFiendEntity) mob).combatActive() && super.canContinueToUse(); }
        @Override protected void checkAndPerformAttack(LivingEntity t, double d) { }
    }

    /** movement.basic max_turn (deg/tick). */
    static class FiendMoveControl extends MoveControl {
        private final BloodyFiendEntity f;
        FiendMoveControl(BloodyFiendEntity f) { super(f); this.f = f; }
        @Override public void tick() {
            float before = f.getYRot();
            super.tick();
            float after = f.getYRot();
            if (before != after) f.setYRot(Mth.approachDegrees(before, after, f.maxTurn));
        }
    }
    @Override protected BodyRotationControl createBodyControl() { return new BodyRotationControl(this) { @Override public void clientTick() { } }; }

    // ------------------------------------------------------------------ misc overrides
    @Override public boolean isPushable() { return pushableFlag && super.isPushable(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }                // minecraft:is_collidable
    @Override public boolean canBeAffected(MobEffectInstance e) { return false; }     // environment sensor clears all effects every tick
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canBeSeenAsEnemy() { return !inanimate() && super.canBeSeenAsEnemy(); }
    @Override public SoundSource getSoundSource() { return SoundSource.NEUTRAL; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.AMBIENT.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return null; }                   // played manually (volume 2) on each death stage
    @Override public void setCustomName(Component n) { super.setCustomName(n); bossEvent.setName(getDisplayName()); }
    @Override public void onRemovedFromWorld() { super.onRemovedFromWorld(); bossEvent.removeAllPlayers(); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LtState", fiendState().ordinal());
        tag.putInt("LtStateTicks", stateTicks);
        tag.putInt("LtSmashCd", smashCd);
        tag.putBoolean("LtInvulnerable", invulnerable);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LtState")) setFState(State.values()[Mth.clamp(tag.getInt("LtState"), 0, State.values().length - 1)]);
        stateTicks = tag.getInt("LtStateTicks");
        smashCd = tag.getInt("LtSmashCd");
        invulnerable = tag.getBoolean("LtInvulnerable");
        applyStateComponents();
    }

    private void applyStateComponents() {
        phase = Ph.IDLE; setSkill(Skill.INITIAL); faceTarget = false; roaring = false;
        switch (fiendState()) {
            case SLUMBER, AWAKEN -> { speed(0); setBox(BOX_SLUMBER); pushableFlag = false; }
            case STANDING -> { setBox(BOX_STANDING); maxTurn = 20; speed(0.20); pushableFlag = true; }
            case PLAY_DEAD -> { setBox(BOX_STANDING); speed(0); pushableFlag = false; }
            case CROUCH -> { setBox(BOX_STANDING); maxTurn = 20; speed(0.30); pushableFlag = true; }
            default -> { // corpse family
                getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0D); setHealth(1.0F);
                setBox(stateTicks >= t(0.79) ? BOX_CORPSE : BOX_STANDING); speed(0); pushableFlag = false;
            }
        }
    }

    // ------------------------------------------------------------------ damage sensor / death
    private float resistanceMultiplier() { return resistanceLevel >= 1 ? 1.0F - 0.1F * resistanceLevel : 1.0F; }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        switch (fiendState()) {
            case SLUMBER -> { if (!bypass) { wake(); return false; } }                       // on_damage -> awaken, deals_damage false
            case AWAKEN, CORPSE, OPENED_MOUTH, FADING_BODY -> { if (!bypass) return false; }
            case STANDING, CROUCH -> {
                if (!bypass) { if (invulnerable) return false; amount *= resistanceMultiplier(); }
            }
            default -> { }
        }
        float before = getHealth();
        boolean r = super.hurt(source, amount);
        if (r && source.is(ModDamageTypes.SELF_DESTRUCT) && source.getEntity() instanceof LivingEntity src && src != this) {
            float dealt = Math.max(0, before - getHealth());
            setHealth(Math.min(getMaxHealth(), getHealth() + dealt * 0.5F));       // heal half, reflect half one tick later
            pendingReflect.add(new Object[] { src, dealt * 0.5F, 1 });
        }
        return r;
    }

    @Override public void die(DamageSource source) {
        if (level().isClientSide) return;
        switch (fiendState()) {
            case SLUMBER, AWAKEN, STANDING -> nextLife();
            case PLAY_DEAD, CROUCH -> toCorpse();
            default -> super.die(source);
        }
    }

    private void nextLife() {                      // death -> next_life -> transformation to <play_dead>
        playSound(ModSounds.DEATH.get(), 2.0F, 1.0F);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(660.0D);
        setHealth(getMaxHealth()); deathTime = 0;
        setTarget(null); getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
        setFState(State.PLAY_DEAD); stateTicks = 0; setBox(BOX_STANDING);
        phase = Ph.IDLE; setSkill(Skill.INITIAL); faceTarget = false; roaring = false; pushableFlag = false; speed(0);
    }

    private void toCorpse() {                      // death -> transform_into_corpse
        playSound(ModSounds.DEATH.get(), 2.0F, 1.0F);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0D); setHealth(1.0F); deathTime = 0;
        setTarget(null); getNavigation().stop();
        setFState(State.CORPSE); stateTicks = 0; setBox(BOX_STANDING);
        phase = Ph.IDLE; setSkill(Skill.INITIAL); faceTarget = false; roaring = false; pushableFlag = false; speed(0);
    }

    private void wake() { setFState(State.AWAKEN); stateTicks = 0; }

    private void enterStanding() {
        setFState(State.STANDING); setBox(BOX_STANDING); stateTicks = 0;
        behaviorReset(); invulnerable = false;
    }
    private void enterCrouch() {
        setFState(State.CROUCH); stateTicks = 0;
        phase = Ph.IDLE; setSkill(Skill.INITIAL); faceTarget = false; pushableFlag = true; maxTurn = 20; speed(0.30);   // crawl_move
    }

    @Override public boolean killedEntity(ServerLevel lvl, LivingEntity victim) {   // entityDie -> heal max(10% of max hp, 10)
        setHealth(Math.min(getMaxHealth(), getHealth() + Math.max(victim.getMaxHealth() * 0.1F, 10.0F)));
        return super.killedEntity(lvl, victim);
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        State s = fiendState();
        if (s == State.CORPSE || s == State.OPENED_MOUTH) {
            ItemStack st = player.getItemInHand(hand);
            boolean blaze = st.is(Items.BLAZE_POWDER), soul = s == State.OPENED_MOUTH && st.is(Items.SOUL_SAND);
            if (blaze || soul) {
                if (!level().isClientSide) {
                    if (!player.getAbilities().instabuild) st.shrink(1);
                    if (blaze && random.nextBoolean()) setFState(State.OPENED_MOUTH);   // randomize {1: nothing, 1: opened_mouth}
                    // soul sand: event "seize_bloody_pearl" is an empty event in the add-on
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------ tick
    @Override public void tick() {
        super.tick();
        this.yBodyRot = getYRot();
        this.yHeadRot = getYRot();
        if (!level().isClientSide) serverTick();
    }

    private void serverTick() {
        resistanceLevel = Mth.clamp((int) Math.floor((1.0F - hpp()) * 10.0F), 0, 9);   // environment_sensor script
        if (roaring) roaringImpact();
        if (smashCd > 0) smashCd--;
        for (Iterator<Object[]> it = pendingReflect.iterator(); it.hasNext(); ) {
            Object[] p = it.next();
            if ((int) p[2] <= 0) {
                LivingEntity src = (LivingEntity) p[0];
                if (src.isAlive()) { src.invulnerableTime = 0; src.hurt(ModDamageTypes.source(level(), ModDamageTypes.OVERRIDE, null), (float) p[1]); }
                it.remove();
            } else p[2] = (int) p[2] - 1;
        }
        switch (fiendState()) {
            case SLUMBER -> { setTarget(null); }
            case AWAKEN -> {
                setTarget(null); stateTicks++;
                if (stateTicks == t(1.77)) awakenAndThenRoar();
                if (stateTicks >= t(3.41)) enterStanding();
            }
            case STANDING, CROUCH -> combatTick();
            case PLAY_DEAD -> {
                setTarget(null); stateTicks++;
                for (int i = 0; i < DEAD_AT.length; i++) if (stateTicks == DEAD_AT[i]) deadEvent(i);
            }
            default -> {
                setTarget(null); stateTicks++;
                if (stateTicks == t(0.79)) { fallDown(); setBox(BOX_CORPSE); }
                if (stateTicks >= 6000) discard();                                     // 300 s despawn timer
            }
        }
        updateBossBar();
    }

    private void combatTick() {
        LivingEntity tg = getTarget();
        if (faceTarget && tg != null) {
            double dx = tg.getX() - getX(), dz = tg.getZ() - getZ();
            float want = (float) (Mth.atan2(dz, dx) * 57.29577951308232) - 90.0F;
            setYRot(Mth.approachDegrees(getYRot(), want, maxTurn));
            yBodyRot = yHeadRot = getYRot();
        }
        if (phase == Ph.CROUCH_SMASH_MIDDLE && subTicks > 0 && --subTicks == 0) { maxTurn = 0; faceTarget = false; }  // max_turn_zero after 0.05 s
        breakBlocks();
        footstepQuake();
        tickPhase();
    }

    private void updateBossBar() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (!combatActive()) { bossEvent.removeAllPlayers(); return; }
        bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        for (ServerPlayer p : sl.players()) {                                          // hud_range 64
            boolean in = p.distanceToSqr(this) <= 64.0 * 64.0, has = bossEvent.getPlayers().contains(p);
            if (in && !has) bossEvent.addPlayer(p); else if (!in && has) bossEvent.removePlayer(p);
        }
    }

    /** "earthquake" behaviour animation controller: footstep camera shakes while walking / crawling. */
    private void footstepQuake() {
        boolean moving = Math.hypot(getX() - xo, getZ() - zo) * 20.0 > 0.07 && skill() == Skill.INITIAL;
        int mode = !moving ? 0 : (fiendState() == State.STANDING ? 1 : 2);
        if (mode != quakeMode) { quakeMode = mode; quakeT = 0; }
        if (mode == 0) return;
        quakeT++;
        if (mode == 1) { if (quakeT == 10 || quakeT == 25) cameraShake(0.3F, 0.2F); else if (quakeT >= 30) quakeT = 0; }
        else { if (quakeT == 13 || quakeT == 28) cameraShake(0.3F, 0.2F); else if (quakeT >= 31) quakeT = 0; }
    }

    // ------------------------------------------------------------------ skill state machine
    private void setPhase(Ph p, double sec) { phase = p; phaseTicks = t(sec); }

    private void behaviorReset() {  // event behavior_reset (last add wins -> 20 deg / 0.20 in every state)
        setSkill(Skill.INITIAL); phase = Ph.IDLE; faceTarget = false; pushableFlag = true; maxTurn = 20; speed(0.20);
    }
    private void beginSkill(Skill s, boolean zeroTurn) {
        setSkill(s); pushableFlag = false; speed(0); if (zeroTurn) maxTurn = 0;
    }

    private void tickPhase() {
        LivingEntity tg = getTarget();
        if (phase == Ph.IDLE) { triggerRandomEvent(); return; }
        if (phase == Ph.STOMP_APPROACH && tg != null && distanceTo(tg) <= 3.0F) { evStompMiddle(); return; }
        if (phase == Ph.BITE_APPROACH && tg != null && distanceTo(tg) <= 7.0F) { evBiteOrDevourPost(); return; }
        if (--phaseTicks > 0) return;
        switch (phase) {
            case ATTACK_BEGIN -> { attackImpact(); setPhase(Ph.ATTACK_FINISH, 0.87); }
            case COMBO_BEGIN -> { faceTarget = true; comboImpactA(); setPhase(Ph.COMBO_MIDDLE, 0.36); }
            case COMBO_MIDDLE -> { comboDash(); setPhase(Ph.COMBO_FINISH_PRE, 0.62); }
            case COMBO_FINISH_PRE -> { faceTarget = false; comboImpactB(); setPhase(Ph.COMBO_FINISH_POST, 0.93); }
            case ROAR_BEGIN -> { roarImpact(); roaring = true; setPhase(Ph.ROAR_MIDDLE, 2.03); }
            case ROAR_MIDDLE -> { roaring = false; setPhase(Ph.ROAR_FINISH, 0.71); }
            case SMASH_BEGIN -> { smashImpact(); setPhase(Ph.SMASH_FINISH, 0.99); }
            case THUMP_BEGIN -> { thumpDash(); setPhase(Ph.THUMP_MIDDLE, 0.05); }
            case THUMP_MIDDLE -> { thumpImpact(); setPhase(Ph.THUMP_FINISH, 1.46); }
            case STOMP_MIDDLE -> { stompImpact(); setPhase(Ph.STOMP_FINISH, 1.29); }
            case BITE_BEGIN -> { biteImpact(); setPhase(Ph.BITE_FINISH, 0.96); }
            case DEVOUR_BEGIN -> { devourImpactA(); setPhase(Ph.DEVOUR_MIDDLE, 0.13); }
            case DEVOUR_MIDDLE -> { devourImpactB(); setPhase(Ph.DEVOUR_FINISH, 3.79); }
            case CROUCH_ATTACK_BEGIN -> { crouchAttackImpact(); setPhase(Ph.CROUCH_ATTACK_FINISH, 0.79); }
            case CROUCH_COMBO_BEGIN -> { crouchComboImpact(38, 7); setPhase(Ph.CROUCH_COMBO_MIDDLE, 0.92); }
            case CROUCH_COMBO_MIDDLE -> { crouchComboImpact(39, 7); setPhase(Ph.CROUCH_COMBO_FINISH, 0.95); }
            case CROUCH_THUMP_BEGIN -> { crouchThumpImpact(); setPhase(Ph.CROUCH_THUMP_FINISH, 0.96); }
            case CROUCH_SMASH_BEGIN -> { crouchSmashDash(); setBox(BOX_FLOAT); setPhase(Ph.CROUCH_SMASH_MIDDLE, 0.91); subTicks = 1; }
            case CROUCH_SMASH_MIDDLE -> { setBox(BOX_STANDING); crouchSmashImpact(); setPhase(Ph.CROUCH_SMASH_FINISH, 0.85); }
            default -> behaviorReset();   // every *_FINISH phase and the approach time-outs (3.04 s)
        }
    }

    // ---- random_event_trigger (script triggerRandomEvent) ----
    private void triggerRandomEvent() {
        LivingEntity tg = getTarget();
        if (tg == null) return;
        double d = eDist();
        if (d > 23 || !inFront(position(), tg.position(), vH(), 0.7)) return;
        List<Runnable> opts = new ArrayList<>(); List<Float> w = new ArrayList<>();
        if (fiendState() == State.STANDING) {
            if (d < 6.7) {
                for (Runnable r : new Runnable[] { this::evAttackBegin, this::evComboBegin, this::evRoarBegin, this::evSmashBegin,
                        this::evStompBegin, this::evThumpBegin, this::evBiteOrDevourPre }) { opts.add(r); w.add(1F); }
            } else if (d < 9.5) {
                for (Runnable r : new Runnable[] { this::evThumpBegin, this::evBiteOrDevourPre, this::behaviorReset }) { opts.add(r); w.add(1F); }
            } else return;
        } else {
            if (d < 5.2) {
                for (Runnable r : new Runnable[] { this::evCrouchAttack, this::evCrouchCombo, this::evCrouchThump, this::evCrouchSmash }) { opts.add(r); w.add(1F); }
            } else if (d < 17.6) {
                opts.add(this::evCrouchSmash); w.add(smashCd > 0 ? 0F : 1F);
                opts.add(this::behaviorReset); w.add(1F);
            } else return;
        }
        pick(opts, w).run();
    }
    private Runnable pick(List<Runnable> o, List<Float> w) {
        float total = 0; for (float f : w) total += f;
        if (total <= 0) return this::behaviorReset;
        float r = random.nextFloat() * total, acc = 0;
        for (int i = 0; i < o.size(); i++) { acc += w.get(i); if (w.get(i) > 0 && r < acc) return o.get(i); }
        return o.get(o.size() - 1);
    }

    // ---- events ----
    private void evAttackBegin() { beginSkill(Skill.ATTACK, true); setPhase(Ph.ATTACK_BEGIN, 0.63); }
    private void evComboBegin() { beginSkill(Skill.COMBO, false); setPhase(Ph.COMBO_BEGIN, 0.63); }
    private void evRoarBegin() { beginSkill(Skill.ROAR, true); setPhase(Ph.ROAR_BEGIN, 0.76); }
    private void evSmashBegin() { beginSkill(Skill.SMASH, true); setPhase(Ph.SMASH_BEGIN, 1.51); }
    private void evThumpBegin() { beginSkill(Skill.THUMP, false); faceTarget = true; setPhase(Ph.THUMP_BEGIN, 1.99); }
    private void evStompBegin() {
        if (eDist() >= 3.4) setPhase(Ph.STOMP_APPROACH, 3.04); else evStompMiddle();
    }
    private void evStompMiddle() { beginSkill(Skill.STOMP, true); setPhase(Ph.STOMP_MIDDLE, 0.63); }
    private void evBiteOrDevourPre() {
        double d = eDist();
        if (d >= 6.7) setPhase(Ph.BITE_APPROACH, 3.04);
        else if (d >= 3.4) evBiteOrDevourPost();
        else behaviorReset();
    }
    private void evBiteOrDevourPost() {
        LivingEntity tg = getTarget();
        float devour = 1F + ((tg != null && tg.getHealth() < 100) ? 9F : 0F), bite = 3F;
        if (random.nextFloat() * (bite + devour) < bite) { beginSkill(Skill.BITE, true); setPhase(Ph.BITE_BEGIN, 1.08); }
        else { beginSkill(Skill.DEVOUR, true); setPhase(Ph.DEVOUR_BEGIN, 1.08); }
    }
    private void evCrouchAttack() { beginSkill(Skill.ATTACK, true); setPhase(Ph.CROUCH_ATTACK_BEGIN, 0.71); }
    private void evCrouchCombo() { beginSkill(Skill.COMBO, true); setPhase(Ph.CROUCH_COMBO_BEGIN, 0.71); }
    private void evCrouchThump() { beginSkill(Skill.THUMP, true); setPhase(Ph.CROUCH_THUMP_BEGIN, 1.54); }
    private void evCrouchSmash() {
        beginSkill(Skill.SMASH, false); maxTurn = 10; faceTarget = true; smashCd = 80;   // half_turn + towards_target + crawl.smash_cd
        setPhase(Ph.CROUCH_SMASH_BEGIN, 0.74);
    }

    // ---- awaken / play_dead / corpse events ----
    private void awakenAndThenRoar() {
        cameraShake(0.4F, 1.3F);
        for (LivingEntity e : entsIn(position(), 0, 7)) {
            applyDamage(e, 9, Cause.CONTACT);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 2, 5);
        }
    }
    private void deadEvent(int i) {
        switch (i) {
            case 0 -> stepDash(-0.2); case 2 -> stepDash(-0.3); case 4 -> stepDash(-0.4); case 6 -> stepDash(-0.3);
            case 1, 3, 5, 7 -> { cameraShake(0.2F, 0.1F); setDeltaMovement(Vec3.ZERO); hurtMarked = true; }
            case 8 -> cameraShake(0.5F, 0.2F);
            case 9 -> enterCrouch();
            default -> { }
        }
    }
    private void stepDash(double k) { Vec3 v = vH().scale(k); setDeltaMovement(getDeltaMovement().add(v)); hurtMarked = true; }
    private void fallDown() {
        cameraShake(0.7F, 0.3F);
        Vec3 c = offset(position(), vH(), 0, 0.1, -2.3);
        for (LivingEntity e : entsIn(c, 0, 5))
            applyDamage(e, position().distanceTo(e.position()) < 4 ? 101 : 49, Cause.CONTACT, false);
    }

    // ------------------------------------------------------------------ impact functions (script)
    private void hit(LivingEntity e, float main, float selfDestruct) {
        applyDamage(e, main, Cause.ENTITY_ATTACK);
        applyDamage(e, selfDestruct, Cause.SELF_DESTRUCT);
    }
    private boolean big(LivingEntity e) { return e.getMaxHealth() > 100; }

    private void attackImpact() {
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 6)) {
            if (loc.distanceTo(e.position()) > 1.5 && !inFront(loc, e.position(), vh, 0.7)) continue;
            hit(e, dyn(37), big(e) ? dyn(7) : 0);
            impulse(e, vh, 2.3, 0.4, 1.7, 0.6);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
        }
    }
    private void comboImpactA() {
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 6)) {
            if (loc.distanceTo(e.position()) > 1.5 && !inFront(loc, e.position(), vh, 0.7)) continue;
            hit(e, dyn(41), big(e) ? dyn(9) : 0);
            impulse(e, vh, 3.6, 0.6, 2.7, 0.8);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
        }
    }
    private void comboDash() { double d = eDist(); if (d > 6) push(vK(getTarget()).scale(Math.min(d / 17.0, 1.0) * 5.0)); }
    private void comboImpactB() {
        cameraShake(0.4F, 0.1F);
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 6)) {
            if (loc.distanceTo(e.position()) > 1.5 && !inFront(loc, e.position(), vh, 0.7)) continue;
            hit(e, dyn(43), big(e) ? dyn(9) : 0);
            impulse(e, vh, 4.2, 1.1, 2.1, 0.9);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
        }
    }
    private void roarImpact() {
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 8)) {
            if (loc.distanceTo(e.position()) > 3.4 && !inFront(loc, e.position(), vh, 0.5)) continue;
            applyDamage(e, 29 * (1.5F - hpp() * 0.5F), Cause.CONTACT);
        }
    }
    private void roaringImpact() {   // every tick of roar_middle
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 8)) {
            float ehd = e.getMaxHealth();
            if (loc.distanceTo(e.position()) > 3.4 && !inFront(loc, e.position(), vh, 0.5)) continue;
            applyDamage(e, dyn(0.9F + ehd * 0.01F), Cause.OVERRIDE);
            applyDamage(e, ehd > 100 ? dyn(0.7F) : 0, Cause.SELF_DESTRUCT);
            Vec3 n = e.position().subtract(loc); double l = n.length();
            Vec3 vn = l == 0 ? Vec3.ZERO : n.scale(1.0 / l);
            impulse(e, vn, 0.45, 0.36, 0.09, 0.12);
            effect(e, MobEffects.CONFUSION, 3, 5);
        }
    }
    private void smashImpact() {
        playSound(ModSounds.SMASH.get(), 5.0F, 1.0F);
        cameraShake(0.4F, 0.8F);
        Vec3 c = offset(position(), vH(), 0, 0.5, 3.9);
        for (LivingEntity e : entsIn(c, 0, 5.5)) {
            hit(e, dyn(73), big(e) ? dyn(15) : 0);
            impulse(e, Vec3.ZERO, 0, 1.7, 0, 1.4);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
        }
    }
    private void thumpDash() { double d = eDist(); if (d > 5.6) push(vK(getTarget()).scale(Mth.clamp(d / 11.0 * 7.1, 0, 10.7))); }
    private void thumpImpact() {
        setDeltaMovement(Vec3.ZERO); hurtMarked = true;
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 6)) {
            double d = loc.distanceTo(e.position());
            if (!(inFront(loc, e.position(), vh, 0.7) || d < 2.3)) continue;
            hit(e, dyn(52), big(e) ? dyn(11) : 0);
            impulse(e, vh, 3.1, 0.5, 2.3, 0.7);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
        }
    }
    private void stompImpact() {
        cameraShake(0.8F, 0.6F);
        Vec3 c = offset(position(), vH(), 0.6, 0, 1.7);
        for (LivingEntity e : entsIn(c, 0, 3.5)) {
            hit(e, dyn(31), big(e) ? dyn(5) : 0);
            impulse(e, Vec3.ZERO, 0, 0.9, 0, 0.7);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
        }
    }
    private void biteImpact() {
        Vec3 c = offset(position(), vH(), 0, 2.1, 3.9);
        for (LivingEntity e : entsIn(c, 0, 4.5)) {
            float ehd = e.getMaxHealth(), ehc = e.getHealth();
            float sd = ehd > 100 ? (ehc <= ehd * 0.5F ? (ehd - ehc) * 0.2F : ehd * 0.1F) : (ehd > 20 ? ehd * 0.1F : 6F);
            hit(e, dyn(57), sd);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
            effect(e, MobEffects.BLINDNESS, 1, 5);
        }
    }
    private void devourImpactA() {
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 9)) {
            if (!inFront(loc, e.position(), vh, 0.7)) continue;
            // the add-on's second hit passes an undefined value (NaN) and therefore never lands; omitted on purpose
            applyDamage(e, dyn(62), Cause.ENTITY_ATTACK);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
            effect(e, MobEffects.BLINDNESS, 1, 5);
            effect(e, ModEffects.ANTI_HEALING.get(), 5, 7);
            effect(e, ModEffects.BLOOD_CURSE.get(), 10, 1);
        }
    }
    private void devourImpactB() {
        cameraShake(0.7F, 0.4F);
        Vec3 c = offset(position(), vH(), 0, 0.5, 3.9);
        for (LivingEntity e : entsIn(c, 0, 6.5)) {
            hit(e, 94 * (2.6F - hpp() * 1.6F), big(e) ? dyn(17) : 0);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 1, 5);
        }
    }
    private void crouchAttackImpact() {
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 6)) {
            if (inFront(loc, e.position(), vh, 0.1)) continue;          // as written in the script: hits what is NOT in front
            hit(e, dyn(35), big(e) ? dyn(7) : 0);
            impulse(e, vh, 1.1, 0.2, 0.8, 0.3);
        }
    }
    private void crouchComboImpact(float main, float sd) {
        cameraShake(0.8F, 0.4F);
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 6)) {
            if (!inFront(loc, e.position(), vh, 0.1)) continue;
            hit(e, dyn(main), big(e) ? dyn(sd) : 0);
        }
    }
    private void crouchThumpImpact() {
        cameraShake(0.8F, 0.4F);
        Vec3 loc = position(), vh = vH();
        for (LivingEntity e : entsIn(loc, 0, 6)) {
            if (!inFront(loc, e.position(), vh, 0.4)) continue;
            hit(e, dyn(46), big(e) ? dyn(9) : 0);
        }
    }
    private void crouchSmashDash() {
        double d = eDist(); double m = Math.min(d / 17.6, 1.0) * 2.5;
        if (d > 1.9) { Vec3 v = vD(getTarget()); push(new Vec3(v.x * m, 0.3 * m, v.z * m)); }
    }
    private void crouchSmashImpact() {
        cameraShake(0.9F, 0.5F);
        Vec3 c = offset(position(), vH(), 0, 0.2, 1.7);
        for (LivingEntity e : entsIn(c, 0, 5)) hit(e, dyn(94), big(e) ? dyn(17) : 0);
    }

    // ------------------------------------------------------------------ damage / knockback / effects
    private List<LivingEntity> entsIn(Vec3 center, double min, double max) {
        LivingEntity tg = getTarget();
        AABB box = new AABB(center, center).inflate(max);
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> {
            if (e == this || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) return false;
            if (e instanceof BloodyFiendEntity && e != tg) return false;
            double d = e.position().distanceTo(center);
            return d >= min && d <= max;
        });
    }

    private float applyDamage(LivingEntity target, float amount, Cause cause) { return applyDamage(target, amount, cause, true); }

    private float applyDamage(LivingEntity target, float amount, Cause cause, boolean withAttacker) {
        if (!(amount > 0) || !target.isAlive()) return 0;                      // NaN-safe
        DamageSource src = switch (cause) {
            case ENTITY_ATTACK -> damageSources().mobAttack(this);
            case CONTACT -> withAttacker ? damageSources().mobAttack(this) : ModDamageTypes.source(level(), ModDamageTypes.CONTACT, null);
            case OVERRIDE -> ModDamageTypes.source(level(), ModDamageTypes.OVERRIDE, this);
            case SELF_DESTRUCT -> ModDamageTypes.source(level(), ModDamageTypes.SELF_DESTRUCT, this);
        };
        float before = target.getHealth() + target.getAbsorptionAmount();
        target.invulnerableTime = 0;
        CommonEvents.suppressKnockback = true;
        try { target.hurt(src, amount); } finally { CommonEvents.suppressKnockback = false; }
        float dealt = Math.max(0, before - (target.getHealth() + target.getAbsorptionAmount()));
        if (dealt > 0 && withAttacker) onHurtTarget(target, dealt);
        return dealt;
    }

    /** script hurtTarget: devour lifesteal and blood_curse lifesteal. */
    private void onHurtTarget(LivingEntity target, float dmg) {
        float heal = 0;
        if (skill() == Skill.DEVOUR) heal += Mth.clamp(dmg * 0.5F, 1F, 92F);
        if (target.hasEffect(ModEffects.BLOOD_CURSE.get())) heal += Mth.clamp(dmg * 0.7F, 0F, 18F);
        if (heal > 0) setHealth(Math.min(getMaxHealth(), getHealth() + heal));
    }

    private void effect(LivingEntity e, MobEffect type, int seconds, int level) {
        e.addEffect(new MobEffectInstance(type, seconds * 20, level - 1, false, false, true), this);
    }

    /** LTsEntity.impulse: players get applyKnockback(h, v) semantics, other mobs applyImpulse unless high_tenacity. */
    private void impulse(LivingEntity e, Vec3 vn, double pH, double pV, double nH, double nV) {
        if (e instanceof Player) {
            e.setDeltaMovement(vn.x * pH, pV, vn.z * pH);
            e.hurtMarked = true;
        } else if (!e.getTags().contains(HIGH_TENACITY)) {
            e.setDeltaMovement(e.getDeltaMovement().add(vn.x * nH, nV, vn.z * nH));
            e.hurtMarked = true;
        }
    }
    private void push(Vec3 v) { setDeltaMovement(getDeltaMovement().add(v)); hurtMarked = true; }

    private void cameraShake(float seconds, float intensity) {          // @a[r=24]
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) <= 24.0 * 24.0) ModNetwork.sendShake(p, intensity, seconds);
    }

    // ------------------------------------------------------------------ minecraft:break_blocks
    private static final Set<Block> BREAKABLE = new HashSet<>(Arrays.asList(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.BAMBOO, Blocks.BAMBOO_SAPLING,
            Blocks.BEETROOTS, Blocks.BROWN_MUSHROOM, Blocks.CARROTS, Blocks.CARVED_PUMPKIN, Blocks.CHORUS_FLOWER, Blocks.CHORUS_PLANT,
            Blocks.DEAD_BUSH, Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY,
            Blocks.HAY_BLOCK, Blocks.JACK_O_LANTERN, Blocks.MELON, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM, Blocks.POTATOES,
            Blocks.PUMPKIN, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.RED_MUSHROOM, Blocks.CRIMSON_FUNGUS,
            Blocks.WARPED_FUNGUS, Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS, Blocks.SUGAR_CANE, Blocks.KELP,
            Blocks.KELP_PLANT, Blocks.BEE_NEST, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.SEA_PICKLE, Blocks.SNOW,
            Blocks.SWEET_BERRY_BUSH, Blocks.GRASS, Blocks.FERN, Blocks.TURTLE_EGG, Blocks.FROG_SPAWN, Blocks.VINE, Blocks.LILY_PAD,
            Blocks.WHEAT, Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.WEEPING_VINES,
            Blocks.WEEPING_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.SMALL_DRIPLEAF, Blocks.BIG_DRIPLEAF,
            Blocks.BIG_DRIPLEAF_STEM, Blocks.SPORE_BLOSSOM, Blocks.HANGING_ROOTS, Blocks.MANGROVE_ROOTS, Blocks.MUDDY_MANGROVE_ROOTS,
            Blocks.MOSS_CARPET, Blocks.NETHER_WART, Blocks.MOSS_BLOCK, Blocks.SCULK_VEIN, Blocks.TORCHFLOWER_CROP, Blocks.PINK_PETALS,
            Blocks.PITCHER_CROP, Blocks.PITCHER_PLANT, Blocks.MANGROVE_PROPAGULE));

    private static boolean breakable(BlockState s) {
        return !s.isAir() && (BREAKABLE.contains(s.getBlock()) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.SMALL_FLOWERS));
    }
    private void breakBlocks() {
        if (!ForgeEventFactory.getMobGriefingEvent(level(), this)) return;
        AABB b = getBoundingBox();
        for (BlockPos p : BlockPos.betweenClosed(Mth.floor(b.minX), Mth.floor(b.minY), Mth.floor(b.minZ),
                Mth.floor(b.maxX), Mth.floor(b.maxY), Mth.floor(b.maxZ))) {
            if (breakable(level().getBlockState(p))) level().destroyBlock(p.immutable(), true, this);
        }
    }

    // ------------------------------------------------------------------ GeckoLib (client animation state machines)
    private static final String A = "animation.lt_bloody_fiend.";

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animate));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    private PlayState animate(AnimationState<BloodyFiendEntity> st) {
        AnimationController<BloodyFiendEntity> c = st.getController();
        boolean moving = Math.hypot(getX() - xo, getZ() - zo) * 20.0 > 0.07;        // query.ground_speed > 0.07
        Skill k = skill();
        String a = switch (fiendState()) {
            case SLUMBER -> "slumber";
            case AWAKEN -> "awaken";
            case STANDING -> switch (k) {
                case ATTACK -> "attack"; case COMBO -> "combo"; case ROAR -> "roar"; case SMASH -> "smash";
                case THUMP -> "thump"; case STOMP -> "stomp"; case BITE -> "bite"; case DEVOUR -> "devour";
                default -> moving ? "walk" : "idle";
            };
            case PLAY_DEAD -> "play_dead";
            case CROUCH -> switch (k) {
                case ATTACK -> "crouch.attack"; case COMBO -> "crouch.combo"; case THUMP -> "crouch.thump"; case SMASH -> "crouch.smash";
                default -> moving ? "crouch.crawl" : "crouch";
            };
            case CORPSE, FADING_BODY -> "corpse";
            case OPENED_MOUTH -> "opened_mouth";
        };
        if (!a.equals(lastAnim)) {
            // blend_transition 0.2 s is only defined on idle / walk / crouch / crouch.crawl
            boolean blend = lastAnim.equals("idle") || lastAnim.equals("walk") || lastAnim.equals("crouch") || lastAnim.equals("crouch.crawl");
            c.transitionLength(blend ? 4 : 0);
            lastAnim = a;
        }
        c.setAnimationSpeed(a.equals("opened_mouth") ? 0.3D : 1.0D);                   // anim_time_update: +0.3 * delta_time
        c.setAnimation(RawAnimation.begin().then(A + a, Animation.LoopType.DEFAULT));  // loop / hold_on_last_frame come from the JSON
        return PlayState.CONTINUE;
    }
}
