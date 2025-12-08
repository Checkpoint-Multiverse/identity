package draylar.identity.mixin;

import draylar.identity.Identity;
import draylar.identity.api.PlayerIdentity;
import draylar.identity.impl.DimensionsRefresher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

@Mixin(Entity.class)
public abstract class EntityMixin implements DimensionsRefresher {

    @Shadow
    private EntityDimensions dimensions;

    @Shadow
    public abstract EntityPose getPose();

    @Shadow
    public abstract EntityDimensions getDimensions(EntityPose pose);

    @Shadow
    public abstract Box getBoundingBox();

    @Shadow
    public abstract void setBoundingBox(Box boundingBox);

    @Shadow
    protected boolean firstUpdate;
    @Shadow
    public World world;

    @Shadow
    public abstract void move(MovementType type, Vec3d movement);

    @Shadow
    private float standingEyeHeight;

    @Shadow
    protected abstract float getEyeHeight(EntityPose pose, EntityDimensions dimensions);

    @Inject(
            method = "getWidth",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getWidth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player) {
            // Base width should come from current dimensions (already identity-aware via PlayerEntityMixin)
            EntityDimensions dims = this.getDimensions(this.getPose());
            float baseWidth = dims.width;
            // Width scaling is baked into dimensions in PlayerEntityMixin#getDimensions
            ScaleData scaleData = ScaleTypes.HITBOX_WIDTH.getScaleData(player);
            if (scaleData != null) {
                cir.setReturnValue(baseWidth * scaleData.getScale());
            } else {
                cir.setReturnValue(baseWidth);
            }
        }
    }

    @Inject(
            method = "getHeight",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getHeight(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player) {
            // Base height should come from current dimensions (already identity-aware via PlayerEntityMixin)
            EntityDimensions dims = this.getDimensions(this.getPose());
            float baseHeight = dims.height;
            // Height scaling is baked into dimensions in PlayerEntityMixin#getDimensions
            ScaleData scaleData = ScaleTypes.HITBOX_HEIGHT.getScaleData(player);
            if (scaleData != null) {
                cir.setReturnValue(baseHeight * scaleData.getScale());
            } else {
                cir.setReturnValue(baseHeight);
            }
        }
    }

    @Override
    public void identity_refreshDimensions() {
        EntityDimensions currentDimensions = this.dimensions;
        EntityPose entityPose = this.getPose();
        EntityDimensions newDimensions = this.getDimensions(entityPose);

        // Apply only eye height scaling here; width/height scaling is baked into getDimensions for players
        float eyeScale = 1.0F;
        if ((Object) this instanceof PlayerEntity player) {
            ScaleData e = ScaleTypes.EYE_HEIGHT.getScaleData(player);
            if (e != null) eyeScale = e.getScale();
        }

        this.dimensions = newDimensions;
        float baseEye = this.getEyeHeight(entityPose, newDimensions);
        this.standingEyeHeight = baseEye * eyeScale;

        Box box = this.getBoundingBox();
        double scaledWidth = (double) (newDimensions.width);
        double scaledHeight = (double) (newDimensions.height);

        // Center the new bounding box around the current center X/Z and extend Y by scaled height
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double halfW = scaledWidth * 0.5D;
        double minX = centerX - halfW;
        double maxX = centerX + halfW;
        double minZ = centerZ - halfW;
        double maxZ = centerZ + halfW;
        double minY = box.minY;
        double maxY = box.minY + scaledHeight;
        this.setBoundingBox(new Box(minX, minY, minZ, maxX, maxY, maxZ));

        // When centering around the current center, no corrective movement is needed
    }

    @Inject(at = @At("HEAD"), method = "getStandingEyeHeight", cancellable = true)
    public void getStandingEyeHeight(CallbackInfoReturnable<Float> cir) {
        if ((Entity) (Object) this instanceof PlayerEntity player) {
            // Only override when morphed or when Pehkui eye-height scale is applied.
            boolean hasIdentity = PlayerIdentity.getIdentity(player) != null;
            ScaleData scaleData = ScaleTypes.EYE_HEIGHT.getScaleData(player);
            float scale = scaleData != null ? scaleData.getScale() : 1.0F;

            if (hasIdentity || Math.abs(scale - 1.0F) > 1.0e-4f) {
                EntityPose pose = this.getPose();
                EntityDimensions dims = this.getDimensions(pose);
                float baseEyeHeight = this.getEyeHeight(pose, dims);

                if (Identity.PEHKUI_SCALES_CACHE.containsKey(player.getUuidAsString())) {
                    ScaleData cacheScale = Identity.PEHKUI_SCALES_CACHE.get(player.getUuidAsString());
                    if (cacheScale.getScaleType() == ScaleTypes.EYE_HEIGHT) {
                        scale = cacheScale.getScale();
                    }
                }

                cir.setReturnValue(baseEyeHeight * scale);
            }
        }
    }

    @Inject(
            method = "isFireImmune",
            at = @At("HEAD"),
            cancellable = true
    )
    private void isFireImmune(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player) {
            LivingEntity Identity = PlayerIdentity.getIdentity(player);

            if (Identity != null) {
                cir.setReturnValue(Identity.getType().isFireImmune());
            }
        }
    }
}
