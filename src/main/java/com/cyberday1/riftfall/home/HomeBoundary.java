package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.network.HomeBoundaryPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Actual restored terrain, shared by collision and the client's proximity glow. */
public final class HomeBoundary {
    public record Edge(boolean xWall, double coordinate, double start, double end) {}
    private record State(List<ShatteredHomeTiles.GridPos> tiles, List<Edge> edges) {}
    private static final Map<Level, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private HomeBoundary() {}

    public static List<Edge> perimeter(List<ShatteredHomeTiles.GridPos> tiles) {
        Set<ShatteredHomeTiles.GridPos> occupied = new HashSet<>(tiles);
        List<Edge> edges = new ArrayList<>();
        int size = ShatteredHomeTiles.TILE_SIZE;
        for (var tile : occupied) {
            double x = tile.x() * size, z = tile.z() * size;
            if (!occupied.contains(new ShatteredHomeTiles.GridPos(tile.x()-1, tile.z()))) edges.add(new Edge(true, x, z, z+size));
            if (!occupied.contains(new ShatteredHomeTiles.GridPos(tile.x()+1, tile.z()))) edges.add(new Edge(true, x+size, z, z+size));
            if (!occupied.contains(new ShatteredHomeTiles.GridPos(tile.x(), tile.z()-1))) edges.add(new Edge(false, z, x, x+size));
            if (!occupied.contains(new ShatteredHomeTiles.GridPos(tile.x(), tile.z()+1))) edges.add(new Edge(false, z+size, x, x+size));
        }
        return List.copyOf(edges);
    }

    public static void update(Level level, List<ShatteredHomeTiles.GridPos> tiles) {
        State previous = STATES.get(level);
        if (previous != null && previous.tiles.equals(tiles)) return;
        STATES.put(level, new State(List.copyOf(tiles), perimeter(tiles)));
        if (level instanceof ServerLevel server) for (var player : server.players()) sendTo(player);
    }

    public static List<Edge> edges(Level level) {
        if (level instanceof ServerLevel server && !STATES.containsKey(level))
            update(level, ShatteredHomeProgress.of(server).unlockedTiles());
        State state = STATES.get(level);
        return state == null ? List.of() : state.edges;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new HomeBoundaryPayload(player.level().dimension().location(),
                ShatteredHomeProgress.of(player.serverLevel()).unlockedTiles()));
    }

    public static List<VoxelShape> collisions(Level level, AABB swept, List<VoxelShape> original) {
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension())) return original;
        List<VoxelShape> result = new ArrayList<>(original);
        for (Edge edge : edges(level)) {
            double c = edge.coordinate;
            AABB wall = edge.xWall
                    ? new AABB(c-0.001, swept.minY-2, edge.start, c+0.001, swept.maxY+2, edge.end)
                    : new AABB(edge.start, swept.minY-2, c-0.001, edge.end, swept.maxY+2, c+0.001);
            if (wall.intersects(swept)) result.add(Shapes.create(wall));
        }
        return result;
    }
}
