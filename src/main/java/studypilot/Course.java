package studypilot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Course {
    private final String courseId;
    private final String name;
    private final List<Material> materials;

    public Course(String name) {
        this(UUID.randomUUID().toString(), name, new ArrayList<Material>());
    }

    public Course(String courseId, String name, List<Material> materials) {
        this.courseId = courseId;
        this.name = name;
        this.materials = new ArrayList<>(materials);
    }

    public String getId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public List<Material> getMaterials() {
        return new ArrayList<>(materials);
    }

    public void addMaterial(Material material) {
        materials.add(material);
    }

    public Material getMaterial(String materialId) {
        for (Material material : materials) {
            if (material.getId().equals(materialId)) {
                return material;
            }
        }
        throw new IllegalArgumentException("Material not found in this course.");
    }

    public List<Chunk> getChunks() {
        List<Chunk> chunks = new ArrayList<>();
        for (Material material : materials) {
            chunks.addAll(material.getChunks());
        }
        return chunks;
    }

    public String toString() {
        return name + " [" + courseId.substring(0, 8) + "]";
    }
}
