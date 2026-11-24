
package com.k.sosxbiomc.client.rendering.mutations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.model.geom.ModelPart;

import org.joml.Vector3f;

public final class surface_sampler {

    public record Face(
            ModelPart part,
            Vector3f a,
            Vector3f b,
            Vector3f c,
            Vector3f d,
            Vector3f normal,
            float areaABC,
            float areaACD,
            float totalArea
    ) {}

    public record Placement(
            ModelPart part,
            Vector3f position,
            Vector3f normal
    ) {}

    private static final Field CUBES_FIELD =
            findField(ModelPart.class, List.class);

    private static final Field POLYGONS_FIELD =
            findArrayField(ModelPart.Cube.class);

    private static final Field VERTICES_FIELD =
            findArrayField(POLYGONS_FIELD.getType().getComponentType());

    private static final Field VERTEX_POSITION_FIELD =
            findVectorField(VERTICES_FIELD.getType().getComponentType());

    private surface_sampler() {}

    private static Field findField(Class<?> owner, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }

        throw new RuntimeException(
                "Could not find field of type " + type.getName()
                        + " in " + owner.getName()
        );
    }

    private static Field findArrayField(Class<?> owner) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType().isArray()) {
                field.setAccessible(true);
                return field;
            }
        }

        throw new RuntimeException(
                "Could not find array field in " + owner.getName()
        );
    }

    private static Field findVectorField(Class<?> owner) {
        for (Field field : owner.getDeclaredFields()) {
            if (Vector3f.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }

        throw new RuntimeException(
                "Could not find Vector3f field in " + owner.getName()
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getCubes(ModelPart part) {
        try {
            return (List<Object>) CUBES_FIELD.get(part);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Object[] getArray(Field field, Object instance) {
        try {
            return (Object[]) field.get(instance);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Vector3f getVertexPosition(Object vertex) {
        try {
            return new Vector3f(
                    (Vector3f) VERTEX_POSITION_FIELD.get(vertex)
            );
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static float triangleArea(
            Vector3f a,
            Vector3f b,
            Vector3f c
    ) {
        Vector3f ab = new Vector3f(b).sub(a);
        Vector3f ac = new Vector3f(c).sub(a);

        return ab.cross(ac).length() * 0.5F;
    }

    public static List<Face> buildFaces(List<ModelPart> parts) {
        List<Face> faces = new ArrayList<>();

        for (ModelPart part : parts) {
            for (Object cube : getCubes(part)) {

                Object[] polygons = getArray(POLYGONS_FIELD, cube);

                List<Vector3f> cubeVertices = new ArrayList<>();

                for (Object polygon : polygons) {
                    Object[] vertices = getArray(
                            VERTICES_FIELD,
                            polygon
                    );

                    for (Object vertex : vertices) {
                        cubeVertices.add(getVertexPosition(vertex));
                    }
                }

                if (cubeVertices.isEmpty()) {
                    continue;
                }

                float minX = Float.MAX_VALUE;
                float minY = Float.MAX_VALUE;
                float minZ = Float.MAX_VALUE;

                float maxX = -Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                float maxZ = -Float.MAX_VALUE;

                for (Vector3f vertex : cubeVertices) {
                    minX = Math.min(minX, vertex.x);
                    minY = Math.min(minY, vertex.y);
                    minZ = Math.min(minZ, vertex.z);

                    maxX = Math.max(maxX, vertex.x);
                    maxY = Math.max(maxY, vertex.y);
                    maxZ = Math.max(maxZ, vertex.z);
                }

                Vector3f cubeCenter = new Vector3f(
                        (minX + maxX) * 0.5F,
                        (minY + maxY) * 0.5F,
                        (minZ + maxZ) * 0.5F
                );

                for (Object polygon : polygons) {
                    Object[] vertices = getArray(
                            VERTICES_FIELD,
                            polygon
                    );

                    if (vertices.length != 4) {
                        continue;
                    }

                    Vector3f a = getVertexPosition(vertices[0]);
                    Vector3f b = getVertexPosition(vertices[1]);
                    Vector3f c = getVertexPosition(vertices[2]);
                    Vector3f d = getVertexPosition(vertices[3]);

                    float areaABC = triangleArea(a, b, c);
                    float areaACD = triangleArea(a, c, d);
                    float totalArea = areaABC + areaACD;

                    if (totalArea <= 0.0001F) {
                        continue;
                    }

                    Vector3f normal = new Vector3f(b).sub(a)
                            .cross(new Vector3f(c).sub(a))
                            .normalize();

                    Vector3f faceCenter = new Vector3f(a)
                            .add(b)
                            .add(c)
                            .add(d)
                            .mul(0.25F);

                    Vector3f outward = new Vector3f(faceCenter)
                            .sub(cubeCenter);

                    if (normal.dot(outward) < 0) {
                        normal.negate();
                    }

                    faces.add(new Face(
                            part,
                            a, b, c, d,
                            normal,
                            areaABC,
                            areaACD,
                            totalArea
                    ));
                }
            }
        }

        return faces;
    }

    public static Placement sample(
            List<Face> faces,
            Random random,
            float embedPixels
    ) {
        if (faces == null || faces.isEmpty()) {
            return null;
        }

        float totalArea = 0;

        for (Face face : faces) {
            totalArea += face.totalArea();
        }

        if (totalArea <= 0) {
            return null;
        }

        float selection = random.nextFloat() * totalArea;
        Face chosen = faces.get(faces.size() - 1);

        for (Face face : faces) {
            selection -= face.totalArea();

            if (selection <= 0) {
                chosen = face;
                break;
            }
        }

        Vector3f position;

        if (random.nextFloat() * chosen.totalArea()
                < chosen.areaABC()) {
            position = sampleTriangle(
                    chosen.a(),
                    chosen.b(),
                    chosen.c(),
                    random
            );
        } else {
            position = sampleTriangle(
                    chosen.a(),
                    chosen.c(),
                    chosen.d(),
                    random
            );
        }

        position.sub(
                new Vector3f(chosen.normal()).mul(embedPixels)
        );

        return new Placement(
                chosen.part(),
                position,
                new Vector3f(chosen.normal())
        );
    }

    private static Vector3f sampleTriangle(
            Vector3f a,
            Vector3f b,
            Vector3f c,
            Random random
    ) {
        float r1 = (float) Math.sqrt(random.nextFloat());
        float r2 = random.nextFloat();

        return new Vector3f(a)
                .mul(1.0F - r1)
                .add(new Vector3f(b).mul(r1 * (1.0F - r2)))
                .add(new Vector3f(c).mul(r1 * r2));
    }
}
