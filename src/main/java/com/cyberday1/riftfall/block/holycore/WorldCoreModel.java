package com.cyberday1.riftfall.block.holycore;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.joml.Quaternionf;

/** Painted geometry, optional texture coordinates and one twelve-second motion loop. */
public record WorldCoreModel(int frames, int loopTicks, List<Part> parts) {
    public record Part(String name, float[] vertices, int[] colors, byte[] materials, float[] visibility, float[] motion, float[] uvs) {
        public int vertexCount() { return colors.length; }
    }

    /** Reused by the renderer: no allocations per vertex or motion sample. */
    public static final class Transform {
        public float x, y, z, sx, sy, sz;
        public final Quaternionf rotation = new Quaternionf();
        private final Quaternionf next = new Quaternionf();
    }

    public void sample(Part part, double ticks, Transform into) {
        double position = ((ticks % loopTicks + loopTicks) % loopTicks) * frames / loopTicks;
        int frame = (int) position;
        float fraction = (float) (position - frame);
        int a = frame * 10, b = (frame + 1) * 10;
        float[] m = part.motion;
        into.x = lerp(m[a], m[b], fraction);
        into.y = lerp(m[a+1], m[b+1], fraction);
        into.z = lerp(m[a+2], m[b+2], fraction);
        into.rotation.set(m[a+3], m[a+4], m[a+5], m[a+6]);
        into.next.set(m[b+3], m[b+4], m[b+5], m[b+6]);
        into.rotation.slerp(into.next, fraction);
        into.sx = lerp(m[a+7], m[b+7], fraction);
        into.sy = lerp(m[a+8], m[b+8], fraction);
        into.sz = lerp(m[a+9], m[b+9], fraction);
    }

    private static float lerp(float a, float b, float t) { return a + (b-a)*t; }

    /** Same stage-gated double heartbeat as the native concept, once per four seconds. */
    public static double heartbeat(int stage, double ticks) {
        double strength = switch (stage) { case 2 -> .60; case 3 -> .85; case 4 -> 1; default -> 0; };
        double phase = ((ticks % 80) + 80) % 80 / 80;
        double first = Math.IEEEremainder(phase - .17, 1) / .055;
        double second = Math.IEEEremainder(phase - .36, 1) / .080;
        return strength * (Math.exp(-first*first) + .55*Math.exp(-second*second));
    }

    public static WorldCoreModel read(InputStream input) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(input)))) {
            int version = in.readInt();
            if (version != 0x57434D32 && version != 0x57434D33)
                throw new IOException("Invalid World Core mesh header");
            int frames = in.readInt(), ticks = in.readInt(), count = in.readInt();
            if (frames != 240 || ticks != 240 || count < 1 || count > 64)
                throw new IOException("Unsupported World Core motion layout");
            List<Part> parts = new ArrayList<>();
            int total = 0;
            for (int p = 0; p < count; p++) {
                String name = in.readUTF();
                int size = in.readInt();
                if (size < 0 || size % 3 != 0 || size > 300_000 || (total += size) > 600_000)
                    throw new IOException("Invalid World Core vertex count");
                float[] vertices = new float[size * 6];
                int[] colors = new int[size];
                byte[] materials = new byte[size];
                float[] visibility = new float[size];
                float[] uvs = version == 0x57434D33 ? new float[size * 2] : null;
                for (int v = 0; v < size; v++) {
                    for (int k = 0; k < 6; k++) vertices[v*6+k] = finite(in);
                    colors[v] = in.readUnsignedByte()<<16 | in.readUnsignedByte()<<8 | in.readUnsignedByte();
                    materials[v] = in.readByte();
                    visibility[v] = in.readUnsignedByte()/255f;
                    if (materials[v] < 0 || materials[v] > 2) throw new IOException("Unknown Core material");
                    if (uvs != null) for (int k=0;k<2;k++) {
                        float uv=finite(in);
                        if (uv<0 || uv>1) throw new IOException("Invalid Core texture coordinate");
                        uvs[v*2+k]=uv;
                    }
                }
                float[] motion = new float[(frames+1)*10];
                for (int k = 0; k < motion.length; k++) motion[k] = finite(in);
                parts.add(new Part(name, vertices, colors, materials, visibility, motion, uvs));
            }
            if (in.read() != -1) throw new IOException("Trailing Core mesh data");
            return new WorldCoreModel(frames, ticks, List.copyOf(parts));
        }
    }

    private static float finite(DataInputStream in) throws IOException {
        float value = in.readFloat();
        if (!Float.isFinite(value) || Math.abs(value) > 128) throw new IOException("Invalid Core mesh coordinate");
        return value;
    }
}
