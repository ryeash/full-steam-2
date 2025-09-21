package com.fullsteam.terrain;

import com.fullsteam.physics.Boulder;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.CompoundObstacle;
import lombok.Getter;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Vector2;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Procedural terrain and obstacle generation system.
 * Creates varied terrain biomes with appropriate obstacles, cover, and visual elements.
 */
@Getter
public class TerrainGenerator {
    
    private final long seed;
    private final double worldWidth;
    private final double worldHeight;
    private final TerrainType terrainType;
    private final Random random;
    
    // Generated terrain data
    private final List<TerrainFeature> terrainFeatures = new ArrayList<>();
    private final List<Obstacle> generatedObstacles = new ArrayList<>();
    private final List<CompoundObstacle> compoundObstacles = new ArrayList<>();
    private final Map<String, Object> terrainMetadata = new HashMap<>();
    
    public enum TerrainType {
        FOREST("Forest", 0x2d5a3d, 0x1a3d1f),
        DESERT("Desert", 0xd4a574, 0xa67c52),
        URBAN("Urban", 0x606060, 0x404040),
        TUNDRA("Tundra", 0x7fb8d4, 0x5a9bc4),
        VOLCANIC("Volcanic", 0x4a2c2a, 0x2d1b19),
        GRASSLAND("Grassland", 0x4a7c59, 0x2d5a3d);
        
        private final String displayName;
        private final int primaryColor;
        private final int secondaryColor;
        
        TerrainType(String displayName, int primaryColor, int secondaryColor) {
            this.displayName = displayName;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
        }
        
        public String getDisplayName() { return displayName; }
        public int getPrimaryColor() { return primaryColor; }
        public int getSecondaryColor() { return secondaryColor; }
    }
    
    public TerrainGenerator(double worldWidth, double worldHeight, Long customSeed, TerrainType terrainType) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.seed = customSeed != null ? customSeed : ThreadLocalRandom.current().nextLong();
        this.terrainType = terrainType != null ? terrainType : getRandomTerrainType();
        this.random = new Random(this.seed);
        
        generateTerrain();
    }
    
    /**
     * Generate terrain with default random type and seed.
     */
    public TerrainGenerator(double worldWidth, double worldHeight) {
        this(worldWidth, worldHeight, null, null);
    }
    
    /**
     * Get a random terrain type.
     */
    public static TerrainType getRandomTerrainType() {
        TerrainType[] types = TerrainType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }
    
    /**
     * Main terrain generation method.
     */
    private void generateTerrain() {
        // Store metadata
        terrainMetadata.put("seed", seed);
        terrainMetadata.put("terrainType", terrainType.name());
        terrainMetadata.put("displayName", terrainType.getDisplayName());
        terrainMetadata.put("primaryColor", String.format("#%06X", terrainType.getPrimaryColor()));
        terrainMetadata.put("secondaryColor", String.format("#%06X", terrainType.getSecondaryColor()));
        
        // Generate terrain features
//        generateTerrainFeatures();
        
        // Generate obstacles based on terrain type
        generateObstacles();
        
        // Generate compound obstacles (complex structures)
//        generateCompoundObstacles();
        
        // Generate environmental details
//        generateEnvironmentalFeatures();
    }
    
    /**
     * Generate terrain features like hills, valleys, rock formations.
     */
    private void generateTerrainFeatures() {
        int featureCount = 8 + random.nextInt(12); // 8-19 features
        
        for (int i = 0; i < featureCount; i++) {
            double x = (random.nextDouble() - 0.5) * worldWidth * 0.8;
            double y = (random.nextDouble() - 0.5) * worldHeight * 0.8;
            
            TerrainFeature feature = generateFeatureForTerrain(x, y);
            if (feature != null) {
                terrainFeatures.add(feature);
            }
        }
    }
    
    /**
     * Generate terrain feature appropriate for the current terrain type.
     */
    private TerrainFeature generateFeatureForTerrain(double x, double y) {
        switch (terrainType) {
            case FOREST:
                return generateForestFeature(x, y);
            case DESERT:
                return generateDesertFeature(x, y);
            case URBAN:
                return generateUrbanFeature(x, y);
            case TUNDRA:
                return generateTundraFeature(x, y);
            case VOLCANIC:
                return generateVolcanicFeature(x, y);
            case GRASSLAND:
                return generateGrasslandFeature(x, y);
            default:
                return generateGenericFeature(x, y);
        }
    }
    
    private TerrainFeature generateForestFeature(double x, double y) {
        String[] forestFeatures = {"TreeCluster", "Clearing", "ThickBrush", "FallenLog", "RockOutcrop"};
        String featureType = forestFeatures[random.nextInt(forestFeatures.length)];
        double size = 30 + random.nextDouble() * 80;
        return new TerrainFeature(featureType, x, y, size, 0x2d5a3d);
    }
    
    private TerrainFeature generateDesertFeature(double x, double y) {
        String[] desertFeatures = {"SandDune", "RockMesa", "Oasis", "CactusGrove", "AncientRuins"};
        String featureType = desertFeatures[random.nextInt(desertFeatures.length)];
        double size = 40 + random.nextDouble() * 90;
        return new TerrainFeature(featureType, x, y, size, 0xd4a574);
    }
    
    private TerrainFeature generateUrbanFeature(double x, double y) {
        String[] urbanFeatures = {"Building", "Rubble", "CarWreck", "Alley", "Plaza"};
        String featureType = urbanFeatures[random.nextInt(urbanFeatures.length)];
        double size = 25 + random.nextDouble() * 70;
        return new TerrainFeature(featureType, x, y, size, 0x606060);
    }
    
    private TerrainFeature generateTundraFeature(double x, double y) {
        String[] tundraFeatures = {"IceSheet", "Snowdrift", "FrozenPond", "IceRock", "WindSculpture"};
        String featureType = tundraFeatures[random.nextInt(tundraFeatures.length)];
        double size = 35 + random.nextDouble() * 85;
        return new TerrainFeature(featureType, x, y, size, 0x7fb8d4);
    }
    
    private TerrainFeature generateVolcanicFeature(double x, double y) {
        String[] volcanicFeatures = {"LavaRock", "CrystalFormation", "SteamVent", "ObsidianField", "LavaTube"};
        String featureType = volcanicFeatures[random.nextInt(volcanicFeatures.length)];
        double size = 30 + random.nextDouble() * 75;
        return new TerrainFeature(featureType, x, y, size, 0x4a2c2a);
    }
    
    private TerrainFeature generateGrasslandFeature(double x, double y) {
        String[] grasslandFeatures = {"FlowerPatch", "SmallHill", "StreamBed", "RockCircle", "TallGrass"};
        String featureType = grasslandFeatures[random.nextInt(grasslandFeatures.length)];
        double size = 25 + random.nextDouble() * 60;
        return new TerrainFeature(featureType, x, y, size, 0x4a7c59);
    }
    
    private TerrainFeature generateGenericFeature(double x, double y) {
        String[] genericFeatures = {"Rock", "Vegetation", "Mound", "Crater", "Formation"};
        String featureType = genericFeatures[random.nextInt(genericFeatures.length)];
        double size = 30 + random.nextDouble() * 70;
        return new TerrainFeature(featureType, x, y, size, 0x808080);
    }
    
    /**
     * Generate obstacles appropriate for the terrain type.
     */
    private void generateObstacles() {
        int baseObstacleCount = getBaseObstacleCount();
        int obstacleCount = baseObstacleCount + random.nextInt(6); // Add 0-5 extra
        
        for (int i = 0; i < obstacleCount; i++) {
            Obstacle obstacle = generateTerrainSpecificObstacle();
            if (obstacle != null) {
                generatedObstacles.add(obstacle);
            }
        }
    }
    
    private int getBaseObstacleCount() {
        switch (terrainType) {
            case FOREST: return 6; // Reduced - compound obstacles will fill the rest
            case DESERT: return 4;  // Reduced - compound obstacles will fill the rest
            case URBAN: return 5;  // Reduced significantly - lots of compound structures
            case TUNDRA: return 3;  // Reduced - compound obstacles will fill the rest
            case VOLCANIC: return 5; // Reduced - compound obstacles will fill the rest
            case GRASSLAND: return 3; // Reduced - mostly compound natural features
            default: return 4;
        }
    }
    
    private Obstacle generateTerrainSpecificObstacle() {
        double x = (random.nextDouble() - 0.5) * (worldWidth - 200);
        double y = (random.nextDouble() - 0.5) * (worldHeight - 200);
        
        double radius = getObstacleRadius();
        
        return new Boulder(x, y, radius);
    }
    
    private double getObstacleRadius() {
        switch (terrainType) {
            case FOREST: return 15 + random.nextInt(25); // 15-39
            case DESERT: return 20 + random.nextInt(35); // 20-54
            case URBAN: return 12 + random.nextInt(20); // 12-31
            case TUNDRA: return 25 + random.nextInt(30); // 25-54
            case VOLCANIC: return 18 + random.nextInt(28); // 18-45
            case GRASSLAND: return 10 + random.nextInt(15); // 10-24
            default: return 15 + random.nextInt(20); // 15-34
        }
    }
    
    /**
     * Generate compound obstacles (complex multi-body structures).
     */
    private void generateCompoundObstacles() {
        int compoundCount = getCompoundObstacleCount();
        
        for (int i = 0; i < compoundCount; i++) {
            CompoundObstacle compoundObstacle = generateTerrainSpecificCompoundObstacle();
            if (compoundObstacle != null && isCompoundObstaclePositionClear(compoundObstacle)) {
                compoundObstacles.add(compoundObstacle);
            }
        }
    }
    
    private int getCompoundObstacleCount() {
        switch (terrainType) {
            case FOREST: return 2; // Rock formations, fallen tree clusters
            case DESERT: return 2; // Mesa formations, ancient ruins
            case URBAN: return 4;  // Buildings, vehicle wrecks, industrial complexes
            case TUNDRA: return 1; // Ice formations
            case VOLCANIC: return 2; // Rock formations, volcanic structures
            case GRASSLAND: return 1; // Natural rock formations
            default: return 2;
        }
    }
    
    private CompoundObstacle generateTerrainSpecificCompoundObstacle() {
        // Find a suitable position
        Vector2 position = findSuitableCompoundPosition();
        if (position == null) {
            return null;
        }
        
        String obstacleType = getCompoundObstacleType();
        return new CompoundObstacle(obstacleType, position, terrainType.name());
    }
    
    private Vector2 findSuitableCompoundPosition() {
        for (int attempt = 0; attempt < 30; attempt++) {
            double x = (random.nextDouble() - 0.5) * (worldWidth - 400);
            double y = (random.nextDouble() - 0.5) * (worldHeight - 400);
            Vector2 candidate = new Vector2(x, y);
            
            // Check if position is clear (increased radius for compound obstacles)
            if (isPositionClear(candidate, 150)) {
                return candidate;
            }
        }
        return null; // Could not find suitable position
    }
    
    private String getCompoundObstacleType() {
        switch (terrainType) {
            case FOREST:
                String[] forestTypes = {"rock_formation", "ruins"};
                return forestTypes[random.nextInt(forestTypes.length)];
            case DESERT:
                String[] desertTypes = {"rock_formation", "ruins", "fortification"};
                return desertTypes[random.nextInt(desertTypes.length)];
            case URBAN:
                String[] urbanTypes = {"building_complex", "vehicle_wreck", "industrial_complex", "ruins"};
                return urbanTypes[random.nextInt(urbanTypes.length)];
            case TUNDRA:
                String[] tundraTypes = {"rock_formation", "fortification"};
                return tundraTypes[random.nextInt(tundraTypes.length)];
            case VOLCANIC:
                String[] volcanicTypes = {"rock_formation", "ruins", "industrial_complex"};
                return volcanicTypes[random.nextInt(volcanicTypes.length)];
            case GRASSLAND:
                String[] grasslandTypes = {"rock_formation", "ruins"};
                return grasslandTypes[random.nextInt(grasslandTypes.length)];
            default:
                String[] defaultTypes = {"rock_formation", "ruins", "vehicle_wreck"};
                return defaultTypes[random.nextInt(defaultTypes.length)];
        }
    }
    
    private boolean isCompoundObstaclePositionClear(CompoundObstacle compoundObstacle) {
        // Check against terrain features
        for (TerrainFeature feature : terrainFeatures) {
            double distance = compoundObstacle.getCenterPosition().distance(new Vector2(feature.getX(), feature.getY()));
            if (distance < feature.getSize() + compoundObstacle.getWidth()/2 + 50) {
                return false;
            }
        }
        
        // Check against simple obstacles
        for (Obstacle obstacle : generatedObstacles) {
            double distance = compoundObstacle.getCenterPosition().distance(obstacle.getPosition());
            double obstacleRadius = getObstacleRadius(obstacle);
            if (distance < obstacleRadius + compoundObstacle.getWidth()/2 + 30) {
                return false;
            }
        }
        
        // Check against other compound obstacles
        for (CompoundObstacle other : compoundObstacles) {
            double distance = compoundObstacle.getCenterPosition().distance(other.getCenterPosition());
            double minDistance = (compoundObstacle.getWidth() + other.getWidth()) / 2 + 100;
            if (distance < minDistance) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Generate environmental details like ambient features.
     */
    private void generateEnvironmentalFeatures() {
        // Add weather/lighting information
        terrainMetadata.put("fogDensity", getFogDensity());
        terrainMetadata.put("ambientLight", getAmbientLight());
        terrainMetadata.put("windDirection", random.nextDouble() * 360);
        terrainMetadata.put("temperature", getTemperature());
        
        // Add particle effects data
        Map<String, Object> particles = new HashMap<>();
        particles.put("type", getParticleType());
        particles.put("density", getParticleDensity());
        particles.put("color", getParticleColor());
        terrainMetadata.put("particles", particles);
    }
    
    private double getFogDensity() {
        switch (terrainType) {
            case FOREST: return 0.3;
            case DESERT: return 0.1;
            case URBAN: return 0.4;
            case TUNDRA: return 0.2;
            case VOLCANIC: return 0.6;
            case GRASSLAND: return 0.1;
            default: return 0.2;
        }
    }
    
    private double getAmbientLight() {
        switch (terrainType) {
            case FOREST: return 0.7;
            case DESERT: return 1.0;
            case URBAN: return 0.6;
            case TUNDRA: return 0.8;
            case VOLCANIC: return 0.5;
            case GRASSLAND: return 0.9;
            default: return 0.8;
        }
    }
    
    private int getTemperature() {
        switch (terrainType) {
            case FOREST: return 18 + random.nextInt(10); // 18-27°C
            case DESERT: return 35 + random.nextInt(15); // 35-49°C
            case URBAN: return 20 + random.nextInt(12); // 20-31°C
            case TUNDRA: return -15 + random.nextInt(10); // -15 to -6°C
            case VOLCANIC: return 45 + random.nextInt(20); // 45-64°C
            case GRASSLAND: return 15 + random.nextInt(15); // 15-29°C
            default: return 20;
        }
    }
    
    private String getParticleType() {
        switch (terrainType) {
            case FOREST: return "leaves";
            case DESERT: return "sand";
            case URBAN: return "dust";
            case TUNDRA: return "snow";
            case VOLCANIC: return "ash";
            case GRASSLAND: return "pollen";
            default: return "dust";
        }
    }
    
    private double getParticleDensity() {
        return 0.1 + random.nextDouble() * 0.4; // 0.1-0.5
    }
    
    private String getParticleColor() {
        switch (terrainType) {
            case FOREST: return "#4a7c59";
            case DESERT: return "#d4a574";
            case URBAN: return "#888888";
            case TUNDRA: return "#ffffff";
            case VOLCANIC: return "#666666";
            case GRASSLAND: return "#ffff88";
            default: return "#cccccc";
        }
    }
    
    /**
     * Check if a position is suitable for placing objects (avoids terrain features).
     */
    public boolean isPositionClear(Vector2 position, double radius) {
        for (TerrainFeature feature : terrainFeatures) {
            double distance = position.distance(new Vector2(feature.getX(), feature.getY()));
            if (distance < feature.getSize() + radius) {
                return false;
            }
        }
        
        for (Obstacle obstacle : generatedObstacles) {
            double distance = position.distance(obstacle.getPosition());
            double obstacleRadius = getObstacleRadius(obstacle);
            if (distance < obstacleRadius + radius) {
                return false;
            }
        }
        
        for (CompoundObstacle compoundObstacle : compoundObstacles) {
            if (compoundObstacle.overlapsWithCircle(position, radius)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get a safe spawn position that avoids terrain features.
     */
    public Vector2 getSafeSpawnPosition(double radius) {
        for (int attempt = 0; attempt < 50; attempt++) {
            double x = (random.nextDouble() - 0.5) * worldWidth * 0.8;
            double y = (random.nextDouble() - 0.5) * worldHeight * 0.8;
            Vector2 candidate = new Vector2(x, y);
            
            if (isPositionClear(candidate, radius)) {
                return candidate;
            }
        }
        
        // Fallback to center if no clear position found
        return new Vector2(0, 0);
    }
    
    /**
     * Get terrain data for client rendering.
     */
    public Map<String, Object> getTerrainData() {
        Map<String, Object> data = new HashMap<>();
        data.put("metadata", terrainMetadata);
        
        List<Map<String, Object>> features = new ArrayList<>();
        for (TerrainFeature feature : terrainFeatures) {
            features.add(feature.toMap());
        }
        data.put("features", features);
        
        List<Map<String, Object>> obstacles = new ArrayList<>();
        for (Obstacle obstacle : generatedObstacles) {
            Map<String, Object> obstacleData = new HashMap<>();
            obstacleData.put("id", obstacle.getId());
            obstacleData.put("x", obstacle.getPosition().x);
            obstacleData.put("y", obstacle.getPosition().y);
            obstacleData.put("radius", getObstacleRadius(obstacle));
            obstacleData.put("type", "Boulder");
            obstacles.add(obstacleData);
        }
        data.put("obstacles", obstacles);
        
        // Add compound obstacles
        List<Map<String, Object>> compoundObstacleData = new ArrayList<>();
        for (CompoundObstacle compoundObstacle : compoundObstacles) {
            Map<String, Object> compoundData = new HashMap<>();
            compoundData.put("type", compoundObstacle.getType());
            compoundData.put("x", compoundObstacle.getCenterPosition().x);
            compoundData.put("y", compoundObstacle.getCenterPosition().y);
            compoundData.put("width", compoundObstacle.getWidth());
            compoundData.put("height", compoundObstacle.getHeight());
            compoundData.put("biome", compoundObstacle.getBiomeType());
            
            // Add rendering data for individual bodies
            List<Map<String, Object>> bodies = new ArrayList<>();
            for (CompoundObstacle.ObstacleData obstacleData : compoundObstacle.getRenderingData()) {
                Map<String, Object> bodyData = new HashMap<>();
                bodyData.put("shape", obstacleData.shape);
                bodyData.put("x", obstacleData.x);
                bodyData.put("y", obstacleData.y);
                bodyData.put("width", obstacleData.width);
                bodyData.put("height", obstacleData.height);
                bodyData.put("rotation", obstacleData.rotation);
                bodies.add(bodyData);
            }
            compoundData.put("bodies", bodies);
            compoundObstacleData.add(compoundData);
        }
        data.put("compoundObstacles", compoundObstacleData);
        
        return data;
    }
    
    /**
     * Extract radius from an obstacle's physics body.
     */
    private double getObstacleRadius(Obstacle obstacle) {
        // Get the first fixture from the obstacle's body
        if (obstacle.getBody().getFixtureCount() > 0) {
            Convex shape = obstacle.getBody().getFixture(0).getShape();
            if (shape instanceof Circle) {
                return ((Circle) shape).getRadius();
            }
        }
        return 20.0; // Default radius if we can't determine it
    }
}
