package com.cyberday1.riftfall.block.holycore;

import com.cyberday1.riftfall.init.RiftfallBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** Interaction with the visible orb, independently of the shallow floor collision. */
public final class WorldCoreTarget {
    // Native concept: center Z=2.70, exported to Y with scale 2.8/3.1 and a .06 offset.
    public static final double HEIGHT=2.76*2.8/3.1;
    public static final double RADIUS=1.02;
    private WorldCoreTarget() {}

    public static Optional<BlockPos> find(Level level,Player player) { return find(level,player,player.getLookAngle()); }

    /** Along {@code look} from the player's own eye; the server passes a client's reported aim through here. */
    public static Optional<BlockPos> find(Level level,Player player,Vec3 look) {
        Vec3 eye=player.getEyePosition();
        double reach=player.blockInteractionRange();
        if(!Double.isFinite(reach) || reach<=0)return Optional.empty();
        // A local query only. Never generate chunks while aiming at an item target.
        reach=Math.min(reach,16);
        Vec3 end=eye.add(look.scale(reach));
        var wall=level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        double limit=wall.getType()==HitResult.Type.MISS?reach:eye.distanceTo(wall.getLocation());
        var search=new AABB(eye,end).inflate(RADIUS).expandTowards(0,-HEIGHT,0);
        BlockPos nearest=null;double best=limit;
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(search.minX,search.minY,search.minZ),
                BlockPos.containing(search.maxX,search.maxY,search.maxZ))) {
            if(!level.hasChunkAt(pos) || !level.getBlockState(pos).is(RiftfallBlocks.HOLY_CORE.get()))continue;
            double hit=intersection(eye,look,Vec3.atLowerCornerOf(pos).add(.5,HEIGHT,.5),RADIUS,best);
            if(hit>=0){best=hit;nearest=pos.immutable();}
        }
        return Optional.ofNullable(nearest);
    }

    /** First forward ray/sphere intersection, bounded by normal reach and the nearest solid surface. */
    public static double intersection(Vec3 eye,Vec3 look,Vec3 center,double radius,double limit) {
        var direction=look.normalize();var offset=eye.subtract(center);
        double b=offset.dot(direction),c=offset.lengthSqr()-radius*radius,disc=b*b-c;
        if(disc<0)return -1;
        double near=-b-Math.sqrt(disc),far=-b+Math.sqrt(disc);
        double distance=near>=0?near:c<=0?0:far;
        return distance>=0 && distance<=limit?distance:-1;
    }
}
