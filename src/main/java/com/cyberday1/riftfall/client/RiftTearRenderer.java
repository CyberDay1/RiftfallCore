package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.breaker.WorldBreakerMotion;
import com.cyberday1.riftfall.block.breaker.WorldBreakerPhase;
import com.cyberday1.riftfall.block.breaker.WorldBreakerBlock;
import com.cyberday1.riftfall.block.breaker.WorldBreakerBlockEntity;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.rift.RiftPortalBlock;
import com.cyberday1.riftfall.rift.RiftTearBlock;
import com.cyberday1.riftfall.rift.RiftTearBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the rip as a crack in space, standing in the world along its own axis.
 *
 * <p>
 * Drawn once from the bottom block of the three. The picture is a single image of the whole tear
 * rather than three stacked slices, which is only possible here: a block model may not reach past
 * one block on each side, and this is not a block model.
 *
 * <h2>⭐⭐ The billboard came back, and it was never the problem</h2>
 * Up to rev 12 the tear yaw-followed the camera, and that was blamed for capping every attempt at
 * depth: a surface seen along its own axis cannot change silhouette, show parallax, or occlude
 * itself. It follows again at {@link #FOLLOW} of one, and it has depth, so the diagnosis was wrong.
 * ⚠ What rev 12 lacked was an <b>interior</b>. It drew the biconvex lens &mdash; itself a bug &mdash;
 * so squaring up left nothing to look into. The funnel takes its depth from inside the mouth: walls
 * converging on the throat, the near rim covering the far wall, facets shaded apart. None of that
 * needs the outline to move. ⛔ Do not re-derive "billboarding kills depth"; the thing missing was
 * geometry worth seeing down. {@link RiftPortalBlock#AXIS} is now only the resting plane.
 *
 * <h2>⭐ The pillow is gone too</h2>
 * Up to rev 15 both walls pushed their ragged inner ring <em>away</em> from the rip's plane, one each
 * way, which makes a biconvex lens: points sticking out at the viewer from whichever side they stand
 * on. Two rounds of paint could not fix that, because it was never paint. The rim ring cannot leave
 * {@code z = 0} &mdash; the rim, halo and cracks are drawn on a flat quad there and the wall's outer
 * edge has to stay on the painted aperture &mdash; so the wall can only run one way or the other, and
 * "receding" for one side is "protruding" for the other. There is no shape that recedes from both.
 * {@link RiftTearCrack.Form#FUNNEL} answers that by choosing the side per frame instead: one wall,
 * running to a throat on the far side of the plane from the camera, so it recedes from wherever it is
 * actually being looked at. {@link RiftTearCrack#FORM} still reaches the old solid.
 *
 * <h2>What is drawn</h2>
 * <ol>
 *   <li><b>A wall and a core</b> per side the form asks for, on {@code entityCutoutNoCull}. That type
 *       writes depth, so a nearer facet genuinely hides a further one; no-cull, so both faces of
 *       every facet are visible.
 *   <li><b>The rim, halo and cracks</b>, on {@code entityTranslucentEmissive}, on a flat quad in the
 *       rip's own plane. These vanish edge-on, which is correct &mdash; a glow has no thickness.
 * </ol>
 *
 * <p>
 * Order is not left to chance. Both types share the buffer source's spare buffer, so asking for the
 * second flushes the first: the solid walls are drawn, and the glow then depth-tests against them.
 */
public class RiftTearRenderer implements BlockEntityRenderer<RiftTearBlockEntity> {

    /**
     * Stitched onto the block atlas by {@code assets/riftfall/atlases/blocks.json}, because nothing
     * else asks for them &mdash; the tear has no model to reference them. They have to be on an
     * atlas rather than loaded on their own: an animated texture is animated by its atlas sprite's
     * ticker, and a standalone one would show frame zero forever.
     *
     * <p>
     * ⭐ The three share a frame count and a {@code frametime}, which is what keeps them together.
     * Both tickers start at atlas load and step at the same rate over the same length, so they are
     * on the same frame forever. Driving one from game time is the thing that drifts.
     */
    public static final Material TEXTURE =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/rift_tear"));

    /** The same picture with the aperture punched out, so the crack shows through it. */
    public static final Material FRAME =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/rift_tear_frame"));

    /** The crack's walls, unwrapped: U around the rim, V from the rim to the inner edge. */
    public static final Material WALL =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/rift_tear_wall"));

    /**
     * The fractures a World Breaker throws into the floor around itself, drawn a block at a time.
     *
     * <p>
     * Drawn from the tear so the floor effect exists only while the tear is present. The breaker's
     * own renderer handles its key, charge glow and floating shards independently.
     */
    public static final Material GROUND =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/world_breaker_cracks"));
    private static final Material GROUND_LIGHT =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/world_breaker_ground_light"));

    /** Near then far, the walls' own convention: &minus;Z is the viewer's side of the rip's plane. */
    private static final float[] SIDES = {-1.0F, 1.0F};

    /**
     * How many blocks out from the plate the ground decal reaches, in each direction.
     *
     * <p>
     * Must match {@code CELLS} in {@code tools/models/build_world_breaker_art.py},
     * which is {@code 2 * this + 1}.
     * The drawing is cut into one cell per block by UV, so a disagreement does not fail loudly &mdash;
     * it silently draws the wrong part of the picture on every block.
     */
    private static final int GROUND_REACH = 2;

    /**
     * How far over the floor the decal sits, in blocks. Small enough to read as lying on it, large
     * enough that it is not fighting the floor's own top face for the same depth value.
     */
    private static final float GROUND_LIFT = 0.01F;

    /**
     * ⭐ How close to the rip's own plane, in blocks, the funnel starts flattening toward it.
     *
     * <p>
     * The throat has to be on the far side of the plane from the camera, so it swaps sides the
     * instant the camera crosses. Nothing about that crossing is gentle on its own: the tear is not
     * flat, it is {@link RiftTearCrack#DEPTH} thick, so at the crossing the silhouette is a wedge
     * half a block deep and the swap mirrors that wedge in one frame. Real, and you would see it.
     *
     * <p>
     * ⚠ The received wisdom &mdash; "the flip is free because edge-on is where the tear is thinnest"
     * &mdash; is only half true and the wrong half. Edge-on is where the tear's <em>painted</em> part
     * vanishes, because the rim quad is genuinely flat. Depth is the one thing that does not vanish
     * there; grazing is where a wedge reads most, not least. So the flip is paid for rather than
     * argued away.
     *
     * <p>
     * The scale goes smoothly to zero at the plane, so at the moment of the swap there is no depth
     * left to swap and the two forms are the same flat ring. Continuous by construction: the sign is
     * multiplied by a magnitude that is zero exactly where the sign is undefined, so which branch the
     * comparison takes at {@code 0.0} cannot matter. Half a block, matching {@code DEPTH * 0.5},
     * because the flattening should be over by the time the camera is a wall's depth off the plane.
     */
    private static final double TAPER = RiftTearCrack.DEPTH * 0.5;

    private final BlockEntityRendererProvider.Context context;
    private final java.util.Map<WorldBreakerBlockEntity, Long> sparkTicks =
            new java.util.WeakHashMap<>();

    public RiftTearRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    /**
     * ⭐⭐ How far the tear turns toward the camera, as a fraction of how far the camera has moved
     * around it. Zero is a rift welded to its block's axis; one is a rift that always squares up.
     *
     * <p>
     * ⛔⛔ <b>Only 0, 0.5 and 1 are legal, and that is a theorem rather than a shortlist.</b> The
     * camera's bearing is periodic and so is the plane, so the map from one to the other has a whole
     * number of half-turns in it &mdash; a winding number, which cannot change by degrees. Turning by
     * a fraction {@code s} of a full orbit advances the plane {@code 360s}, and the plane is its own
     * mirror every {@code 180}, so the tear snaps by {@code 360s mod 180} at the seam. That is zero
     * only at those three values. Measured, not just argued: 0.2, 0.3 and 0.7 all snap by 72&deg;.
     *
     * <p>
     * ⛔ So there is no dial to walk up, and the "turn it until it flattens" plan does not exist.
     *
     * <p>
     * ⚠ A half was shipped first and it looked right, but only from the front: it sweeps
     * {@code ±90°} about the block's plane, so directly behind the tear the view is exactly edge-on
     * and the rift stops answering. That is <em>half</em> a rift, and the ask was a whole one.
     *
     * <p>
     * ⭐⭐ At one it squares up from everywhere, and the depth has to come from <b>inside</b> the mouth
     * rather than from the outline: the walls converge on the throat, and the near rim covers the far
     * wall. ⚠ The class note warns that a surface seen along its own axis cannot change silhouette,
     * and that is still true &mdash; but it was written of <b>rev 12, which had no funnel</b>, only the
     * biconvex lens that was itself a bug. Square-on with a real receding tunnel behind it is new.
     */
    public static final float FOLLOW = 1.0F;

    /**
     * The turn, in degrees about +Y, that stands the crack in the block's own plane.
     *
     * <p>
     * The geometry is built spanning local X with its face along &minus;Z, which is
     * {@link Direction.Axis#X} already. {@code Axis.YP} by a quarter turn takes local X onto world
     * &minus;Z, which is the other one. Static and taking its argument plainly so it can be checked
     * without a game running.
     */
    public static float turn(Direction.Axis plane) {
        return plane == Direction.Axis.Z ? 90.0F : 0.0F;
    }

    /** The camera's bearing from the tear, in degrees about +Y, on the same dial {@link #turn} uses. */
    public static double azimuth(BlockPos pos, Vec3 camera) {
        return Math.toDegrees(Math.atan2(camera.x - (pos.getX() + 0.5),
                camera.z - (pos.getZ() + 0.5)));
    }

    /**
     * Where the tear actually stands this frame: its block's plane, leaned {@link #FOLLOW} of the way
     * toward the camera.
     *
     * <p>
     * ⚠ The lean is taken on the <em>shortest</em> way round, so the seam sits directly behind the
     * tear rather than beside it. At {@code FOLLOW} of a half the plane is a mirror of itself there,
     * so the seam is invisible &mdash; see the theorem on {@link #FOLLOW}.
     */
    public static double renderYaw(double base, double azimuth, double follow) {
        double toCamera = Math.IEEEremainder(azimuth - base, 360.0);
        return base + follow * toCamera;
    }

    /**
     * Where the camera stands relative to the rip's plane, in blocks, along the crack's own +Z.
     *
     * <p>
     * ⭐ Taken against the yaw the geometry is <em>actually drawn at</em>, not against the block's
     * axis. Those were the same number until the tear started leaning and are not any more, and the
     * throat reads this to decide which side to open on &mdash; measuring it on the old axis would put
     * the tunnel's mouth and the tunnel's depth in two different frames, which is precisely the fault
     * that cost revs 28 to 30.
     *
     * <p>
     * The turn about +Y that carries local X onto world &minus;Z carries local +Z onto
     * {@code (sin yaw, 0, cos yaw)}, so that is the direction this projects onto. At {@code yaw = 0}
     * it is world +Z and at {@code yaw = 90} world +X, which are the two the axis version tabulated.
     */
    public static double offPlane(double yaw, BlockPos pos, Vec3 camera) {
        double radians = Math.toRadians(yaw);
        return (camera.x - (pos.getX() + 0.5)) * Math.sin(radians)
                + (camera.z - (pos.getZ() + 0.5)) * Math.cos(radians);
    }

    /**
     * ⭐ Where the funnel's throat goes, and how far: a signed scale on {@link RiftTearCrack#reach}.
     *
     * <p>
     * The sign puts the throat on the far side of the plane from the camera, which is what makes the
     * tear recede from wherever it is looked at instead of bulging at both sides at once. The
     * magnitude is a smoothstep that reaches zero at the plane, so the swap of sign lands on a shape
     * that has no depth to swap &mdash; see {@link #TAPER}.
     *
     * <p>
     * ⭐ Both halves come off the one number, so they cannot disagree. A separate side test and a
     * separate fade would be two things agreeing by coincidence, which is the rev-15 bug.
     */
    public static float throat(double offPlane) {
        float toward = offPlane < 0.0 ? 1.0F : -1.0F;
        float near = (float) Math.min(1.0, Math.abs(offPlane) / TAPER);
        return toward * near * near * (3.0F - 2.0F * near);
    }

    @Override
    public void render(RiftTearBlockEntity tear, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = tear.getLevel();
        if (level != null) groundCracks(level, tear.getBlockPos(), pose, buffers, partialTick);
        drawTear(tear.getBlockPos(), tear.getBlockState().getValue(RiftPortalBlock.AXIS), ripDrop(tear),
                new WorldBreakerMotion.Opening(1, 1, 1), pose, buffers);
    }

    /** The same geometry and camera alignment as the finished tear, before a portal exists. */
    public void renderOpening(WorldBreakerBlockEntity breaker,
                              double tick, PoseStack pose, MultiBufferSource buffers) {
        var opening = WorldBreakerMotion.opening(tick);
        if (opening.height() <= 0) return;
        openingSparks(breaker, tick, opening);
        pose.pushPose();
        // Breaker renderer starts one block below the tear renderer.
        pose.translate(0, 1, 0);
        drawTear(breaker.getBlockPos().above(), breaker.axis(),
                WorldBreakerBlock.RIP_DROP, opening, pose, buffers);
        pose.popPose();
    }

    /** Once per game tick, at the actual camera-facing cut tips rather than at the pedestal. */
    private void openingSparks(WorldBreakerBlockEntity breaker,
                               double tick, WorldBreakerMotion.Opening opening) {
        Level level = breaker.getLevel();
        if (level == null || tick >= WorldBreakerPhase.CHARGING.ticks()) return;
        long now = level.getGameTime();
        if (java.util.Objects.equals(sparkTicks.put(breaker, now), now)) return;
        BlockPos base = breaker.getBlockPos().above();
        double yaw = Math.toRadians(yawFor(breaker.axis(), base));
        var outline = RiftTearCrack.of().outline();
        int low = 0, high = 0;
        for (int i = 1; i < outline.points(); i++) {
            if (outline.y(i) < outline.y(low)) low = i;
            if (outline.y(i) > outline.y(high)) high = i;
        }
        float centre = -0.5F - RiftTearCrack.DROP + RiftTearCrack.HEIGHT * 0.5F;
        for (int tip : new int[]{low, high}) {
            double x = RiftTearCrack.across(outline.x(tip)) * opening.width();
            double y = centre + (RiftTearCrack.up(outline.y(tip)) - centre) * opening.height();
            double wy = base.getY() + 0.5 - WorldBreakerBlock.RIP_DROP + y;
            double wx = base.getX() + 0.5 + x * Math.cos(yaw);
            double wz = base.getZ() + 0.5 - x * Math.sin(yaw);
            // Most sparks belong to the incision; a few linger while its sides pull apart.
            if (tick > 50 && now % 2 == 0) continue;
            for (int i = 0; i < 2; i++) {
                double sideways = (level.random.nextDouble() - 0.5) * 0.09;
                double rise = Math.signum(y - centre) * (0.025 + level.random.nextDouble() * 0.035);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, wx, wy, wz,
                        sideways * Math.cos(yaw), rise, -sideways * Math.sin(yaw));
            }
            level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.7F, 0.35F, 1.0F), 0.55F),
                    wx, wy, wz, 0, 0.01, 0);
        }
    }

    private void drawTear(BlockPos base, Direction.Axis plane, float drop,
                          WorldBreakerMotion.Opening opening,
                          PoseStack pose, MultiBufferSource buffers) {
        TextureAtlasSprite rip = TEXTURE.sprite();
        TextureAtlasSprite wall = WALL.sprite();
        TextureAtlasSprite frame = FRAME.sprite();
        RiftTearCrack crack = RiftTearCrack.of();
        double yaw = yawFor(plane, base);
        pose.pushPose();
        pose.translate(0.5F, 0.5F - drop, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees((float) yaw));
        float centre = -0.5F - RiftTearCrack.DROP + RiftTearCrack.HEIGHT * 0.5F;
        pose.translate(0, centre, 0);
        pose.scale(opening.width(), opening.height(), opening.depth());
        pose.translate(0, -centre, 0);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(rip.atlasLocation()));

        // 1 and 2, the core and the walls. The vertices come out of build(), which is the only place
        // they are worked out; all this end does is put the normalised UV onto the right sprite.
        build(crack, RiftTearCrack.FORM, throatFor(yaw, base),
                (skin, x, y, z, u, v, shade, nx, ny, nz) -> {
                    TextureAtlasSprite sprite = skin == Skin.WALL ? wall : rip;
                    vertex(solid, matrix, x, y, z, u(sprite, u), v(sprite, v), shade, nx, ny, nz);
                });

        // 3. The rim, the halo and the cracks, on the flat quad they have always been on. Asking for
        //    this buffer draws everything above first, so the glow depth-tests against the walls.
        VertexConsumer glow =
                buffers.getBuffer(RenderType.entityTranslucentEmissive(frame.atlasLocation()));

        float half = RiftTearCrack.WIDTH * 0.5F;
        float bottom = -0.5F - RiftTearCrack.DROP;
        float top = bottom + RiftTearCrack.HEIGHT;
        glowVertex(glow, matrix, -half, bottom, frame.getU0(), frame.getV1());
        glowVertex(glow, matrix, half, bottom, frame.getU1(), frame.getV1());
        glowVertex(glow, matrix, half, top, frame.getU1(), frame.getV0());
        glowVertex(glow, matrix, -half, top, frame.getU0(), frame.getV0());
        pose.popPose();
    }

    /**
     * The fractures spreading out of the plate into the floor, one cell of the decal per block.
     *
     * <p>
     * ⭐ Cut up per block rather than laid down as one big quad, because the floor around a breaker is
     * whatever the room was built out of: a step, a gap, a chest. One quad would hang in the air over
     * the holes and sink into anything standing proud. A cell is drawn only where there is a flat top
     * face at the plate's own level with nothing on it, so the cracks stop at the edge of the floor
     * they are in rather than floating past it.
     *
     * <p>
     * The plate's own cell is skipped: it draws itself, and the fractures start under its rim so they
     * read as coming out from beneath it.
     */
    private void groundCracks(Level level, BlockPos base, PoseStack pose,
                             MultiBufferSource buffers, float partialTick) {
        BlockPos plate = base.below();
        if (!level.getBlockState(plate).is(RiftfallBlocks.WORLD_BREAKER.get())) {
            return;
        }
        TextureAtlasSprite sprite = GROUND.sprite();
        VertexConsumer into =
                buffers.getBuffer(RenderType.entityTranslucentEmissive(sprite.atlasLocation()));
        Matrix4f matrix = pose.last().pose();
        boolean[][] supported = new boolean[GROUND_REACH * 2 + 1][GROUND_REACH * 2 + 1];
        for (int dx = -GROUND_REACH; dx <= GROUND_REACH; dx++) {
            for (int dz = -GROUND_REACH; dz <= GROUND_REACH; dz++) {
                if ((dx != 0 || dz != 0) && floored(level, plate, dx, dz)) {
                    supported[dx + GROUND_REACH][dz + GROUND_REACH] = true;
                    groundCell(into, matrix, sprite, dx, dz);
                }
            }
        }
        groundLight(level, base, matrix, buffers, supported, partialTick);
    }

    /** Camera-facing veils, rooted on the actual fractures and faded out above the floor. */
    private void groundLight(Level level, BlockPos base, Matrix4f matrix, MultiBufferSource buffers,
                             boolean[][] supported, float partialTick) {
        Camera camera = context.getBlockEntityRenderDispatcher().camera;
        if (camera == null) return;
        Vec3 eye = camera.getPosition();
        TextureAtlasSprite sprite = GROUND_LIGHT.sprite();
        VertexConsumer into = buffers.getBuffer(RenderType.entityTranslucentEmissive(sprite.atlasLocation()));
        double ticks = level.getGameTime() + partialTick;
        for (var anchor : WorldBreakerGroundLight.INSTANCE.anchors()) {
            int dx = (int) Math.floor(anchor.x());
            int dz = (int) Math.floor(anchor.z());
            if (!supported[dx + GROUND_REACH][dz + GROUND_REACH]) continue;
            double cx = eye.x - base.getX() - anchor.x();
            double cz = eye.z - base.getZ() - anchor.z();
            double length = Math.hypot(cx, cz);
            double rightX = length > 1e-6 ? cz / length : 1.0;
            double rightZ = length > 1e-6 ? -cx / length : 0.0;
            var glow = WorldBreakerGroundLight.glow(anchor, ticks);
            double width = WorldBreakerGroundLight.clippedHalfWidth(anchor, rightX, rightZ, glow.halfWidth());
            if (width < 0.006) continue;
            float x0 = (float) (anchor.x() - rightX * width);
            float z0 = (float) (anchor.z() - rightZ * width);
            float x1 = (float) (anchor.x() + rightX * width);
            float z1 = (float) (anchor.z() + rightZ * width);
            float bottom = GROUND_LIFT - 1.0F;
            float top = bottom + glow.height();
            lightVertex(into, matrix, x0, bottom, z0, sprite.getU0(), sprite.getV1(), glow.alpha());
            lightVertex(into, matrix, x1, bottom, z1, sprite.getU1(), sprite.getV1(), glow.alpha());
            lightVertex(into, matrix, x1, top, z1, sprite.getU1(), sprite.getV0(), glow.alpha());
            lightVertex(into, matrix, x0, top, z0, sprite.getU0(), sprite.getV0(), glow.alpha());
        }
    }

    private static void lightVertex(VertexConsumer into, Matrix4f matrix, float x, float y, float z,
                                    float u, float v, float alpha) {
        into.addVertex(matrix, x, y, z).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /**
     * Whether the block at this offset is floor the decal can lie on: a solid top face level with the
     * plate, and nothing standing on it.
     *
     * <p>
     * ⚠ The support is a block <em>below</em> the offset, not at it. The plate is a 1px carpet lying
     * at the bottom of its own block, so the floor it rests on is the layer under that block and the
     * surrounding floor is that same layer &mdash; measuring at the plate's level instead finds air.
     */
    private static boolean floored(Level level, BlockPos plate, int dx, int dz) {
        BlockPos support = plate.offset(dx, -1, dz);
        BlockPos over = plate.offset(dx, 0, dz);
        return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                && level.getBlockState(over).canBeReplaced();
    }

    /** One block's worth of the decal, face up, taking its own square out of the picture. */
    private static void groundCell(VertexConsumer into, Matrix4f matrix, TextureAtlasSprite sprite,
                                   int dx, int dz) {
        int cells = GROUND_REACH * 2 + 1;
        float x0 = dx;
        float x1 = dx + 1.0F;
        float z0 = dz;
        float z1 = dz + 1.0F;
        // The floor is a block below the base this pose is standing on, plus the lift off its face.
        float y = GROUND_LIFT - 1.0F;
        float u0 = u(sprite, (dx + GROUND_REACH) / (float) cells);
        float u1 = u(sprite, (dx + GROUND_REACH + 1) / (float) cells);
        float v0 = v(sprite, (dz + GROUND_REACH) / (float) cells);
        float v1 = v(sprite, (dz + GROUND_REACH + 1) / (float) cells);

        vertex(into, matrix, x0, y, z1, u0, v1, 1.0F, 0.0F, 1.0F, 0.0F);
        vertex(into, matrix, x1, y, z1, u1, v1, 1.0F, 0.0F, 1.0F, 0.0F);
        vertex(into, matrix, x1, y, z0, u1, v0, 1.0F, 0.0F, 1.0F, 0.0F);
        vertex(into, matrix, x0, y, z0, u0, v0, 1.0F, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Where the camera is, from the dispatcher that is driving this very call.
     *
     * <p>
     * ⭐ Off {@code context}, not off {@code Minecraft.getInstance()}. The dispatcher's {@code camera}
     * is set in its own {@code prepare} at the top of the frame it then hands to this renderer, and it
     * is the position it already uses to decide whether to call us at all, so taking it from there is
     * reading the same value the decision was made on. The singleton's main camera is the same object
     * in practice today and would be the thing to drift if it ever stopped being &mdash; a second
     * source agreeing by coincidence, which is the shape of bug this class has already shipped once.
     *
     * <p>
     * Null only between frames, when the dispatcher clears it, so never during a call to
     * {@code render}. Answered anyway, with the throat on the far side from where the walls' own
     * convention says a viewer stands, rather than with a stand-in position that would land the
     * camera wherever the origin happens to be.
     */
    private float throatFor(double yaw, BlockPos pos) {
        Camera camera = context.getBlockEntityRenderDispatcher().camera;
        return camera == null ? 1.0F : throat(offPlane(yaw, pos, camera.getPosition()));
    }

    /**
     * Which way the tear stands this frame: its block's plane, leaned {@link #FOLLOW} toward the
     * camera. Falls back to the block's own plane between frames, when the dispatcher has no camera.
     */
    private double yawFor(Direction.Axis plane, BlockPos pos) {
        Camera camera = context.getBlockEntityRenderDispatcher().camera;
        return camera == null ? turn(plane)
                : renderYaw(turn(plane), azimuth(pos, camera.getPosition()), FOLLOW);
    }

    /** Which of the two pictures a vertex is on, so the sink can pick its own sprite for it. */
    public enum Skin {

        /** The rip's own picture: the swirl, on the core. */
        CORE,

        /** The unwrapped wall strip: U around the rim, V from the rim to the inner edge. */
        WALL
    }

    /**
     * ⭐ Where a vertex goes. One vertex, in the crack's own frame, with its UV still normalised.
     *
     * <p>
     * This exists so that {@link #build} is the <em>only</em> place the solid is worked out and
     * anything that wants to look at it &mdash; the renderer, a preview, a test &mdash; is looking at
     * the same vertices rather than at a second construction that agrees for now. A previous round of
     * this work shipped a bug that a preview would have caught had the preview not been drawing its
     * own idea of the shape.
     */
    @FunctionalInterface
    public interface Sink {

        void put(Skin skin, float x, float y, float z, float u, float v,
                 float shade, float nx, float ny, float nz);
    }

    /**
     * ⭐ The solid, as quads, four vertices at a time. Free of every rendering class: give it the
     * crack, the form and the throat and it will tell you the shape, with no game running.
     *
     * <p>
     * {@code throat} is the signed scale from {@link #throat(double)}, and is ignored by
     * {@link RiftTearCrack.Form#LENS}, which builds both sides at full reach whatever the camera is
     * doing.
     */
    public static void build(RiftTearCrack crack, RiftTearCrack.Form form, float throat, Sink sink) {
        int points = crack.points();
        int rows = crack.outline().rows();
        float[] sides = form == RiftTearCrack.Form.FUNNEL ? new float[] {throat} : SIDES;
        float core = RiftTearCrack.coreShade(form);

        for (float sign : sides) {
            // 1. The core: the swirl, plugging the mouth its wall ends in, at that wall's own depth
            //    -- the same sign * reach(at) on the same ring index, so the two meet by
            //    construction and cannot part when DEPTH is turned, or when the funnel's throat
            //    tapers toward the plane.
            for (int row = 0; row + 1 < rows; row++) {
                int rightNear = row;
                int rightFar = row + 1;
                int leftNear = points - 1 - row;
                int leftFar = points - 2 - row;
                // Mirror images, so the order that faces out on one side faces in on the other.
                if (sign < 0.0F) {
                    coreVertex(sink, crack, leftNear, sign, core);
                    coreVertex(sink, crack, rightNear, sign, core);
                    coreVertex(sink, crack, rightFar, sign, core);
                    coreVertex(sink, crack, leftFar, sign, core);
                } else {
                    coreVertex(sink, crack, leftFar, sign, core);
                    coreVertex(sink, crack, rightFar, sign, core);
                    coreVertex(sink, crack, rightNear, sign, core);
                    coreVertex(sink, crack, leftNear, sign, core);
                }
            }

            // 2. The wall, rim to inner edge. Flat-shaded per facet: one colour on all four corners,
            //    so the faces catch the baked light at different values and read as shards rather
            //    than as a cone.
            for (int at = 0; at < points; at++) {
                wallFacet(sink, crack, at, (at + 1) % points, sign,
                        crack.wallShade(form, at, sign));
            }
        }
    }

    private static void coreVertex(Sink sink, RiftTearCrack crack, int at, float sign, float shade) {
        sink.put(Skin.CORE,
                RiftTearCrack.across(crack.innerU(at)), RiftTearCrack.up(crack.innerV(at)),
                sign * crack.reach(at),
                crack.innerU(at), crack.innerV(at),
                shade, 0.0F, 0.0F, side(sign));
    }

    /**
     * The unit side {@code sign} is on. The emitted vertex normal has to stay unit length: the
     * funnel's throat scale is tapered and lands on zero at the plane, and a zero normal would take
     * the shader's own diffuse term to zero with it and darken the tear as the camera crossed.
     *
     * <p>
     * ⭐ Which side is safe to hand it, and that is not luck. The level's two diffuse lights are
     * mirror images in Z &mdash; (0.2, 1, &minus;0.7) and (&minus;0.2, 1, 0.7) &mdash; so a normal
     * along the axis gets the same total from them either way up, and the throat swapping sides
     * cannot change what the shader adds on top of the baked shade.
     */
    private static float side(float sign) {
        return sign < 0.0F ? -1.0F : 1.0F;
    }

    /**
     * One facet of one wall. {@code sign} picks the side of the plane and scales the reach: under
     * {@link RiftTearCrack.Form#LENS} it is &plusmn;1 and the two walls are mirror images, and under
     * {@link RiftTearCrack.Form#FUNNEL} there is one of them and it is the tapered throat.
     *
     * <p>
     * ⭐ The rim end stays at {@code z = 0} in every form. It has to: the rim, the halo and the
     * cracks are painted on a flat quad in that plane, and a wall whose outer edge left it would come
     * away from the aperture it is supposed to be the inside of &mdash; which is the rev-15 complaint
     * that the inside and the part coming out did not look connected.
     */
    private static void wallFacet(Sink sink, RiftTearCrack crack, int at, int next, float sign,
                                  float shade) {
        int points = crack.points();
        float uNear = (float) at / points;
        float uFar = (float) (at + 1) / points;
        RiftTearOutline edge = crack.outline();
        // V stops where the wall stops. The rev-11 lesson in geometry: run it to the far end
        // regardless and the tips take the full throat and read dark instead of sharp.
        float vAt = crack.depthAt(at);
        float vNext = crack.depthAt(next);
        float nx = 0.0F;
        float ny = 0.0F;
        float nz = side(sign);

        sink.put(Skin.WALL, RiftTearCrack.across(edge.x(at)), RiftTearCrack.up(edge.y(at)),
                0.0F, uNear, 0.0F, shade, nx, ny, nz);
        sink.put(Skin.WALL, RiftTearCrack.across(edge.x(next)), RiftTearCrack.up(edge.y(next)),
                0.0F, uFar, 0.0F, shade, nx, ny, nz);
        sink.put(Skin.WALL, RiftTearCrack.across(crack.innerU(next)),
                RiftTearCrack.up(crack.innerV(next)), sign * crack.reach(next), uFar, vNext,
                shade, nx, ny, nz);
        sink.put(Skin.WALL, RiftTearCrack.across(crack.innerU(at)),
                RiftTearCrack.up(crack.innerV(at)), sign * crack.reach(at), uNear, vAt,
                shade, nx, ny, nz);
    }

    private static void glowVertex(VertexConsumer consumer, Matrix4f matrix,
                                   float x, float y, float u, float v) {
        vertex(consumer, matrix, x, y, 0.0F, u, v, 1.0F, 0.0F, 0.0F, -1.0F);
    }

    private static float u(TextureAtlasSprite sprite, float normalised) {
        return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * normalised;
    }

    private static float v(TextureAtlasSprite sprite, float normalised) {
        return sprite.getV0() + (sprite.getV1() - sprite.getV0()) * normalised;
    }

    /**
     * Always full-bright, and never mind the {@code light} the dispatcher handed us.
     *
     * <p>
     * The tear is a hole through to somewhere else. Shading it by the room it is standing in would
     * put this room's darkness on the other world's light, and would take the rim &mdash; the one
     * thing that has to stay legible &mdash; down with it.
     *
     * <p>
     * ⭐ The form is carried by {@code shade} on the vertex colour instead. Baked geometric shading
     * and world light are different things, and the crack wants the first without the second.
     */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float u, float v, float shade, float nx, float ny, float nz) {
        int value = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, shade)));
        consumer.addVertex(matrix, x, y, z)
                .setColor(value, value, value, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }

    /**
     * The whole picture, and the crack behind it.
     *
     * <p>
     * The default box is the one block the entity is in, which would cull the tear the moment that
     * block left the frustum &mdash; and the tear is four blocks tall, so most of what a player is
     * looking at is not in it. Square in plan because the tear may stand on either axis.
     *
     * <p>
     * ⚠ The floor decal is in here too, which is why the reach is a maximum and the box runs a whole
     * block below the carrier. The cracks lie further out and lower than the crack itself does, and a
     * box drawn to the crack alone drops them the moment a player looks down at their own feet.
     */
    @Override
    public AABB getRenderBoundingBox(RiftTearBlockEntity tear) {
        BlockPos pos = tear.getBlockPos();
        double reach = Math.max(RiftTearCrack.WIDTH * 0.5, GROUND_REACH + 1.0);
        return new AABB(pos).inflate(reach, 0.0, reach)
                .expandTowards(0.0, RiftTearCrack.HEIGHT, 0.0)
                .expandTowards(0.0, -Math.max(RiftTearCrack.DROP + ripDrop(tear), 1.0), 0.0);
    }

    /**
     * How far this tear's picture comes down from its column's floor, from
     * {@link RiftTearBlock#ripDrop}. Zero for everything except a tear on a World Breaker, where it is
     * negative: the picture is raised clear of the breaker's dais and floating shards.
     *
     * <p>
     * ⚠ It belongs here rather than in {@code RiftTearCrack.DROP}, which is global: lowering that
     * would sink every boss-drop and anchor tear into the floor, since none of them has anything
     * built underneath.
     */
    private static float ripDrop(RiftTearBlockEntity tear) {
        Level level = tear.getLevel();
        return level == null ? 0.0F : RiftTearBlock.ripDrop(level, tear.getBlockPos());
    }

    /**
     * Out to the player's own render distance rather than the default 64 blocks (ruled 2026-10-12): a
     * tear is the landmark a party walks towards. Only live tears have this renderer, one or two at a
     * time, so the reach costs next to nothing beside the chunks it is drawn among.
     */
    @Override
    public int getViewDistance() {
        return RiftfallClientDistances.renderBlocks();
    }
}
