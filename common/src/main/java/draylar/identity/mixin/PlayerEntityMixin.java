package draylar.identity.mixin;

import draylar.identity.Identity;
import draylar.identity.api.PlayerIdentity;
import draylar.identity.api.platform.IdentityConfig;
import draylar.identity.api.variant.IdentityType;
import draylar.identity.mixin.accessor.EntityAccessor;
import draylar.identity.mixin.accessor.IronGolemEntityAccessor;
import draylar.identity.mixin.accessor.LivingEntityAccessor;
import draylar.identity.mixin.accessor.MobEntityAccessor;
import draylar.identity.mixin.accessor.RavagerEntityAccessor;
import draylar.identity.registry.IdentityEntityTags;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntityMixin {

    @Shadow
    public abstract boolean isSpectator();

    @Shadow
    public abstract EntityDimensions getDimensions(EntityPose pose);

    @Shadow
    public abstract boolean isSwimming();

    private PlayerEntityMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Unique
    private float identity_prevWidthScale = Float.NaN;
    @Unique
    private float identity_prevHeightScale = Float.NaN;
    @Unique
    private float identity_prevEyeScale = Float.NaN;
    @Unique
    private float identity_prevBaseScale = Float.NaN;
    @Unique
    private float identity_prevHitboxWidthScale = Float.NaN;
    @Unique
    private float identity_prevHitboxHeightScale = Float.NaN;

    @Inject(method = "tick", at = @At("HEAD"))
    private void identity$loadForcedIdentity(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity serverPlayerEntity) {
            @Nullable LivingEntity active = PlayerIdentity.getIdentity(serverPlayerEntity);
            if (active == null) {
                @Nullable String forced = IdentityConfig.getInstance().getForcedIdentity();
                if (forced != null) {
                    EntityType foundType = Registries.ENTITY_TYPE.get(new Identifier(forced));
                    if (foundType != null) {
                        PlayerIdentity.updateIdentity(serverPlayerEntity, new IdentityType<LivingEntity>(
                                foundType
                        ), (LivingEntity) foundType.create(getWorld()));
                    }
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void identity$watchPehkuiScaleChanges(CallbackInfo ci) {
        // Watch for Pehkui scale changes and refresh dimensions when they change
        PlayerEntity player = (PlayerEntity) (Object) this;

        float w = 1.0F;
        float h = 1.0F;
        float e = 1.0F;
        float b = 1.0F;
        float hbw = Float.NaN;
        float hbh = Float.NaN;
        try {
            ScaleData sb = ScaleTypes.BASE.getScaleData(player);
            ScaleData sw = ScaleTypes.WIDTH.getScaleData(player);
            ScaleData sh = ScaleTypes.HEIGHT.getScaleData(player);
            ScaleData se = ScaleTypes.EYE_HEIGHT.getScaleData(player);
            // Try hitbox-specific types when available
            try {
                ScaleData shbw = ScaleTypes.HITBOX_WIDTH.getScaleData(player);
                ScaleData shbh = ScaleTypes.HITBOX_HEIGHT.getScaleData(player);
                if (shbw != null) hbw = shbw.getScale();
                if (shbh != null) hbh = shbh.getScale();
            } catch (Throwable ignored2) {}
            if (sb != null) b = sb.getScale();
            if (sw != null) w = sw.getScale();
            if (sh != null) h = sh.getScale();
            if (se != null) e = se.getScale();
        } catch (Throwable ignored) {
            // Pehkui not present or API changed; do nothing
        }

        boolean changed = false;
        final float EPS = 1.0e-4f;
        if (Float.isNaN(identity_prevBaseScale) || Math.abs(identity_prevBaseScale - b) > EPS) {
            identity_prevBaseScale = b;
            changed = true;
        }
        if (Float.isNaN(identity_prevWidthScale) || Math.abs(identity_prevWidthScale - w) > EPS) {
            identity_prevWidthScale = w;
            changed = true;
        }
        if (Float.isNaN(identity_prevHeightScale) || Math.abs(identity_prevHeightScale - h) > EPS) {
            identity_prevHeightScale = h;
            changed = true;
        }
        if (Float.isNaN(identity_prevEyeScale) || Math.abs(identity_prevEyeScale - e) > EPS) {
            identity_prevEyeScale = e;
            changed = true;
        }
        if (!Float.isNaN(hbw)) {
            if (Float.isNaN(identity_prevHitboxWidthScale) || Math.abs(identity_prevHitboxWidthScale - hbw) > EPS) {
                identity_prevHitboxWidthScale = hbw;
                changed = true;
            }
        }
        if (!Float.isNaN(hbh)) {
            if (Float.isNaN(identity_prevHitboxHeightScale) || Math.abs(identity_prevHitboxHeightScale - hbh) > EPS) {
                identity_prevHitboxHeightScale = hbh;
                changed = true;
            }
        }

        if (changed) {
            if (this instanceof draylar.identity.impl.DimensionsRefresher refresher) {
                refresher.identity_refreshDimensions();
            } else {
                this.calculateDimensions();
            }
        }
    }

    @Inject(
            method = "getDimensions",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity entity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (entity != null) {
            // Base dimensions come from the active Identity for the given pose
            EntityDimensions base = entity.getDimensions(pose);

            // Apply Pehkui scaling factors for width/height so Vanilla dimension recalculation
            // (including pose changes like standing/crouching) uses scaled dimensions directly.
            float wScale = 1.0F;
            float hScale = 1.0F;
            float baseScale = 1.0F;
            // Optional hitbox-specific scales (Pehkui >= 3.x). Prefer these when present.
            float hbW = Float.NaN;
            float hbH = Float.NaN;
            try {
                PlayerEntity self = (PlayerEntity) (Object) this;
                ScaleData sb = ScaleTypes.BASE.getScaleData(self);
                ScaleData sw = ScaleTypes.WIDTH.getScaleData(self);
                ScaleData sh = ScaleTypes.HEIGHT.getScaleData(self);
                // Try hitbox-specific scale types if available
                try {
                    ScaleData shbw = ScaleTypes.HITBOX_WIDTH.getScaleData(self);
                    ScaleData shbh = ScaleTypes.HITBOX_HEIGHT.getScaleData(self);
                    if (shbw != null) hbW = shbw.getScale();
                    if (shbh != null) hbH = shbh.getScale();
                } catch (Throwable ignored2) {
                    // Older Pehkui versions may not have these types
                }
                if (sb != null) baseScale = sb.getScale();
                if (sw != null) wScale = sw.getScale();
                if (sh != null) hScale = sh.getScale();
            } catch (Throwable ignored) {
                // Pehkui absent or API changed; fall back to base dimensions
            }

            // Prefer hitbox-specific scale if provided; otherwise use WIDTH/HEIGHT
            float effW = !Float.isNaN(hbW) ? hbW : wScale;
            float effH = !Float.isNaN(hbH) ? hbH : hScale;

            float w = base.width * effW * baseScale;
            float h = base.height * effH * baseScale;

            // Preserve the fixed/changing nature of the original dimensions
            if (base.fixed) {
                cir.setReturnValue(EntityDimensions.fixed(w, h));
            } else {
                cir.setReturnValue(EntityDimensions.changing(w, h));
            }
        }
    }

    /**
     * When a player turns into an Aquatic identity, they lose breath outside water.
     *
     * @param ci mixin callback info
     */
    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void tickAquaticBreathingOutsideWater(CallbackInfo ci) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (identity != null) {
            if (Identity.isAquatic(identity)) {
                int air = this.getAir();

                // copy of WaterCreatureEntity#tickWaterBreathingAir
                if (this.isAlive() && !this.isInsideWaterOrBubbleColumn()) {
                    int i = EnchantmentHelper.getRespiration((LivingEntity) (Object) this);

                    // If the player has respiration, 50% chance to not consume air
                    if (i > 0) {
                        if (random.nextInt(i + 1) <= 0) {
                            this.setAir(air - 1);
                        }
                    }

                    // No respiration, decrease air as normal
                    else {
                        this.setAir(air - 1);
                    }

                    // Air has ran out, start drowning
                    if (this.getAir() == -20) {
                        this.setAir(0);
                        this.damage(getDamageSources().drown(), 2.0F);
                    }
                } else {
                    this.setAir(300);
                }
            }
        }
    }

    @Inject(method = "getActiveEyeHeight", at = @At("HEAD"), cancellable = true)
    private void identity_getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        PlayerEntity playerEntity = (PlayerEntity) (Object) this;

        // cursed
        try {
            LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);
            // Return base identity active eye height when morphed; leave Pehkui scaling to EntityMixin#getStandingEyeHeight
            if (identity != null) {
                if (Identity.PEHKUI_SCALES_CACHE.containsKey(this.getUuidAsString())) {
                    ScaleData cacheScale = Identity.PEHKUI_SCALES_CACHE.get(this.getUuidAsString());
                    if (cacheScale.getScaleType() == ScaleTypes.EYE_HEIGHT) {
                        cir.setReturnValue(cacheScale.getScale());
                    }
                }

                cir.setReturnValue(((LivingEntityAccessor) identity).callGetActiveEyeHeight(getPose(), getDimensions(getPose())));
            }
        } catch (Exception ignored) {

        }
    }

    @Environment(EnvType.CLIENT)
    @Override
    public float getEyeHeight(EntityPose pose) {
        PlayerEntity playerEntity = (PlayerEntity) (Object) this;
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (identity != null) {
            return identity.getEyeHeight(pose);
        } else {
            return this.getEyeHeight(pose, this.getDimensions(pose));
        }
    }

    @Inject(
            method = "getHurtSound",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (IdentityConfig.getInstance().useIdentitySounds() && identity != null) {
            cir.setReturnValue(((LivingEntityAccessor) identity).callGetHurtSound(source));
        }
    }


    // todo: separate mixin for ambient sounds
    private int identity_ambientSoundChance = 0;

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void tickAmbientSounds(CallbackInfo ci) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (!getWorld().isClient && IdentityConfig.getInstance().playAmbientSounds() && identity instanceof MobEntity) {
            MobEntity mobIdentity = (MobEntity) identity;

            if (this.isAlive() && this.random.nextInt(1000) < this.identity_ambientSoundChance++) {
                // reset sound delay
                this.identity_ambientSoundChance = -mobIdentity.getMinAmbientSoundDelay();

                // play ambient sound
                SoundEvent sound = ((MobEntityAccessor) mobIdentity).callGetAmbientSound();
                if (sound != null) {
                    float volume = ((LivingEntityAccessor) mobIdentity).callGetSoundVolume();
                    float pitch = ((LivingEntityAccessor) mobIdentity).callGetSoundPitch();

                    // By default, players can not hear their own ambient noises.
                    // This is because ambient noises can be very annoying.
                    if (IdentityConfig.getInstance().hearSelfAmbient()) {
                        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundCategory(), volume, pitch);
                    } else {
                        this.getWorld().playSound((PlayerEntity) (Object) this, this.getX(), this.getY(), this.getZ(), sound, this.getSoundCategory(), volume, pitch);
                    }
                }
            }
        }
    }

    @Inject(
            method = "getDeathSound",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (IdentityConfig.getInstance().useIdentitySounds() && identity != null) {
            cir.setReturnValue(((LivingEntityAccessor) identity).callGetDeathSound());
        }
    }

    @Inject(
            method = "getFallSounds",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getFallSounds(CallbackInfoReturnable<LivingEntity.FallSounds> cir) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (IdentityConfig.getInstance().useIdentitySounds() && identity != null) {
            cir.setReturnValue(identity.getFallSounds());
        }
    }

    @Inject(method = "attack", at = @At("HEAD"))
    protected void identity_tryAttack(Entity target, CallbackInfo ci) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (identity instanceof IronGolemEntity golem) {
            ((IronGolemEntityAccessor) golem).setAttackTicksLeft(10);
        }

        if (identity instanceof WardenEntity warden) {
            warden.attackingAnimationState.start(age);
        }

        if (identity instanceof RavagerEntity ravager) {
            ((RavagerEntityAccessor) ravager).setAttackTick(10);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickGolemAttackTicks(CallbackInfo ci) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (identity instanceof IronGolemEntity golem) {
            IronGolemEntityAccessor accessor = (IronGolemEntityAccessor) golem;
            if (accessor.getAttackTicksLeft() > 0) {
                accessor.setAttackTicksLeft(accessor.getAttackTicksLeft() - 1);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickRavagerAttackTicks(CallbackInfo ci) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (identity instanceof RavagerEntity ravager) {
            RavagerEntityAccessor accessor = (RavagerEntityAccessor) ravager;
            if (accessor.getAttackTick() > 0) {
                accessor.setAttackTick(accessor.getAttackTick() - 1);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickWardenSneakingAnimation(CallbackInfo ci) {
        LivingEntity identity = PlayerIdentity.getIdentity((PlayerEntity) (Object) this);

        if (identity instanceof WardenEntity warden) {
            if (isSneaking()) {
                if (!warden.sniffingAnimationState.isRunning()) {
                    warden.sniffingAnimationState.start(age);
                }
            } else {
                warden.sniffingAnimationState.stop();
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickFire(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        LivingEntity identity = PlayerIdentity.getIdentity(player);

        if (!player.getWorld().isClient && !player.isCreative() && !player.isSpectator()) {
            // check if the player is identity
            if (identity != null) {
                EntityType<?> type = identity.getType();

                // check if the player's current identity burns in sunlight
                if (type.isIn(IdentityEntityTags.BURNS_IN_DAYLIGHT)) {
                    boolean bl = this.isInDaylight();
                    if (bl) {

                        // Can't burn in the rain
                        if (player.getWorld().isRaining()) {
                            return;
                        }

                        // check for helmets to negate burning
                        ItemStack itemStack = player.getEquippedStack(EquipmentSlot.HEAD);
                        if (!itemStack.isEmpty()) {
                            if (itemStack.isDamageable()) {

                                // damage stack instead of burning player
                                itemStack.setDamage(itemStack.getDamage() + player.getRandom().nextInt(2));
                                if (itemStack.getDamage() >= itemStack.getMaxDamage()) {
                                    player.sendEquipmentBreakStatus(EquipmentSlot.HEAD);
                                    player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
                                }
                            }

                            bl = false;
                        }

                        // set player on fire
                        if (bl) {
                            player.setOnFireFor(8);
                        }
                    }
                }
            }
        }
    }

    @Unique
    private boolean isInDaylight() {
        if (getWorld().isDay() && !getWorld().isClient) {
            float brightnessAtEyes = getBrightnessAtEyes();
            BlockPos daylightTestPosition = new BlockPos((int) getX(), (int) Math.round(getY()), (int) getZ());

            // move test position up one block for boats
            if (getVehicle() instanceof BoatEntity) {
                daylightTestPosition = daylightTestPosition.up();
            }

            return brightnessAtEyes > 0.5F && random.nextFloat() * 30.0F < (brightnessAtEyes - 0.4F) * 2.0F && getWorld().isSkyVisible(daylightTestPosition);
        }

        return false;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickTemperature(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        LivingEntity identity = PlayerIdentity.getIdentity(player);

        if (!player.isCreative() && !player.isSpectator()) {
            // check if the player is identity
            if (identity != null) {
                EntityType<?> type = identity.getType();

                // damage player if they are an identity that gets hurt by high temps (eg. snow golem in nether)
                if (type.isIn(IdentityEntityTags.HURT_BY_HIGH_TEMPERATURE)) {
                    Biome biome = getWorld().getBiome(getBlockPos()).value();
                    if (!biome.isCold(getBlockPos())) {
                        player.damage(getDamageSources().onFire(), 1.0f);
                    }
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickIdentity(CallbackInfo ci) {
        if (!getWorld().isClient) {
            PlayerEntity player = (PlayerEntity) (Object) this;
            LivingEntity identity = PlayerIdentity.getIdentity(player);

            // assign basic data to entity from player on server; most data transferring occurs on client
            if (identity != null) {
                identity.setPos(player.getX(), player.getY(), player.getZ());
                identity.setHeadYaw(player.getHeadYaw());
                identity.setJumping(((LivingEntityAccessor) player).isJumping());
                identity.setSprinting(player.isSprinting());
                identity.setStuckArrowCount(player.getStuckArrowCount());
                identity.setInvulnerable(true);
                identity.setNoGravity(true);
                identity.setSneaking(player.isSneaking());
                identity.setSwimming(player.isSwimming());
                identity.setCurrentHand(player.getActiveHand());
                identity.setPose(player.getPose());

                if (identity instanceof TameableEntity) {
                    ((TameableEntity) identity).setInSittingPose(player.isSneaking());
                    ((TameableEntity) identity).setSitting(player.isSneaking());
                }

                ((EntityAccessor) identity).identity_callSetFlag(7, player.isFallFlying());

                ((LivingEntityAccessor) identity).callTickActiveItemStack();
                PlayerIdentity.sync((ServerPlayerEntity) player); // safe cast - context is server world
            }
        }
    }
}
