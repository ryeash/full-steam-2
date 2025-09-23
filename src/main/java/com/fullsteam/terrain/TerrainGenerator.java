package com.fullsteam.terrain;

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
        FOREST("Forest", 0x3d6b4d, 0x2a4d35),        // Lighter, more muted green - better contrast with green projectiles
        DESERT("Desert", 0xd4a574, 0xa67c52),        // Keep desert - good contrast with most projectiles
        URBAN("Urban", 0x7a7a7a, 0x5a5a5a),          // Lighter gray - better contrast with dark cannonballs
        TUNDRA("Tundra", 0x9fc8e4, 0x7aa8c4),        // Lighter, less saturated blue - better contrast with plasma
        VOLCANIC("Volcanic", 0x6b4c3a, 0x4a3529),    // Lighter brown - better visibility for dark projectiles
        GRASSLAND("Grassland", 0x5a8c69, 0x3d6b4d);  // Lighter, more blue-green - better contrast with green projectiles
        
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
        return switch (terrainType) {
            case FOREST -> generateForestFeature(x, y);
            case DESERT -> generateDesertFeature(x, y);
            case URBAN -> generateUrbanFeature(x, y);
            case TUNDRA -> generateTundraFeature(x, y);
            case VOLCANIC -> generateVolcanicFeature(x, y);
            case GRASSLAND -> generateGrasslandFeature(x, y);
            default -> generateGenericFeature(x, y);
        };
    }
    
    private TerrainFeature generateForestFeature(double x, double y) {
        String[] forestFeatures = {"TreeCluster", "Clearing", "ThickBrush", "FallenLog", "RockOutcrop"};
        String featureType = forestFeatures[random.nextInt(forestFeatures.length)];
        double size = 30 + random.nextDouble() * 80;
        return new TerrainFeature(featureType, x, y, size, 0x3d6b4d);
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
        return new TerrainFeature(featureType, x, y, size, 0x7a7a7a);
    }
    
    private TerrainFeature generateTundraFeature(double x, double y) {
        String[] tundraFeatures = {"IceSheet", "Snowdrift", "FrozenPond", "IceRock", "WindSculpture"};
        String featureType = tundraFeatures[random.nextInt(tundraFeatures.length)];
        double size = 35 + random.nextDouble() * 85;
        return new TerrainFeature(featureType, x, y, size, 0x9fc8e4);
    }
    
    private TerrainFeature generateVolcanicFeature(double x, double y) {
        String[] volcanicFeatures = {"LavaRock", "CrystalFormation", "SteamVent", "ObsidianField", "LavaTube"};
        String featureType = volcanicFeatures[random.nextInt(volcanicFeatures.length)];
        double size = 30 + random.nextDouble() * 75;
        return new TerrainFeature(featureType, x, y, size, 0x6b4c3a);
    }
    
    private TerrainFeature generateGrasslandFeature(double x, double y) {
        String[] grasslandFeatures = {"FlowerPatch", "SmallHill", "StreamBed", "RockCircle", "TallGrass"};
        String featureType = grasslandFeatures[random.nextInt(grasslandFeatures.length)];
        double size = 25 + random.nextDouble() * 60;
        return new TerrainFeature(featureType, x, y, size, 0x5a8c69);
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
        ObstacleDensity density = getRandomObstacleDensity();
        int obstacleCount = calculateObstacleCountForWorldSize(density);
        
        // Store density info in metadata for client reference
        terrainMetadata.put("obstacleDensity", density.name());
        terrainMetadata.put("obstacleCount", obstacleCount);
        
        for (int i = 0; i < obstacleCount; i++) {
            generatedObstacles.add(generateChaoticObstacle());
        }
    }
    
    public enum ObstacleDensity {
        SPARSE,   // Fewer obstacles, more open space
        DENSE,    // Normal obstacle density
        CHOKED    // Many obstacles, cramped battlefield
    }
    
    private ObstacleDensity getRandomObstacleDensity() {
        ObstacleDensity[] densities = ObstacleDensity.values();
        return densities[random.nextInt(densities.length)];
    }
    
    private int calculateObstacleCountForWorldSize(ObstacleDensity density) {
        // Calculate base count based on world area
        double worldArea = worldWidth * worldHeight;
        double baseObstaclesPerUnit = 0.000008; // Base density per square unit
        
        // Adjust density multiplier based on selected density
        double densityMultiplier = switch (density) {
            case SPARSE -> 0.4 + random.nextDouble() * 0.3;  // 0.4-0.7x
            case DENSE -> 1.2 + random.nextDouble() * 0.6;   // 1.2-1.8x
            case CHOKED -> 2.0 + random.nextDouble() * 1.0;  // 2.0-3.0x
        };
        
        // Apply terrain-specific modifier
        double terrainModifier = getTerrainObstacleModifier();
        
        int baseCount = (int) (worldArea * baseObstaclesPerUnit * densityMultiplier * terrainModifier);
        
        // Add some randomness (±20%)
        int variation = (int) (baseCount * 0.2);
        int finalCount = baseCount + random.nextInt(variation * 2 + 1) - variation;
        
        // Ensure minimum and maximum bounds
        return Math.max(3, Math.min(finalCount, (int) (worldArea * 0.0001))); // Max 1 obstacle per 10,000 square units
    }
    
    private double getTerrainObstacleModifier() {
        return switch (terrainType) {
            case FOREST -> 1.3;      // More natural obstacles
            case DESERT -> 0.8;      // Fewer obstacles, more open
            case URBAN -> 1.5;       // Lots of buildings and debris
            case TUNDRA -> 0.6;      // Sparse, mostly ice formations
            case VOLCANIC -> 1.2;    // Rock formations and lava obstacles
            case GRASSLAND -> 0.7;   // Open plains with scattered obstacles
            default -> 1.0;
        };
    }
    
    
    private Obstacle generateTerrainSpecificObstacle() {
        double x = (random.nextDouble() - 0.5) * (worldWidth - 200);
        double y = (random.nextDouble() - 0.5) * (worldHeight - 200);
        
        // Generate terrain-appropriate obstacle type
        Obstacle.ObstacleType obstacleType = selectTerrainAppropriateObstacleType();
        
        return Obstacle.createObstacle(x, y, obstacleType);
    }
    
    /**
     * Generate an extra chaotic obstacle with maximum randomization.
     */
    private Obstacle generateChaoticObstacle() {
        // Allow chaotic obstacles to spawn closer to edges for more dramatic placement
        double x = (random.nextDouble() - 0.5) * (worldWidth - 100);
        double y = (random.nextDouble() - 0.5) * (worldHeight - 100);
        
        return Obstacle.createChaoticObstacle(x, y);
    }
    
    /**
     * Select obstacle types appropriate for the current terrain.
     */
    private Obstacle.ObstacleType selectTerrainAppropriateObstacleType() {
        List<Obstacle.ObstacleType> terrainObstacles = getTerrainObstacleTypes();
        
        // Add weighted selection for more realistic distribution
        List<Obstacle.ObstacleType> weightedList = new ArrayList<>();
        
        switch (terrainType) {
            case FOREST:
                // Forest: More natural obstacles, some structures
                addWeighted(weightedList, Obstacle.ObstacleType.BOULDER, 3);
                addWeighted(weightedList, Obstacle.ObstacleType.TRIANGLE_ROCK, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.POLYGON_DEBRIS, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.HOUSE, 1);
                break;
                
            case DESERT:
                // Desert: Rock formations and ancient structures
                addWeighted(weightedList, Obstacle.ObstacleType.BOULDER, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.TRIANGLE_ROCK, 3);
                addWeighted(weightedList, Obstacle.ObstacleType.DIAMOND_STONE, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.POLYGON_DEBRIS, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.WALL_SEGMENT, 1);
                break;
                
            case URBAN:
                // Urban: Buildings and artificial structures
                addWeighted(weightedList, Obstacle.ObstacleType.HOUSE, 4);
                addWeighted(weightedList, Obstacle.ObstacleType.WALL_SEGMENT, 3);
                addWeighted(weightedList, Obstacle.ObstacleType.L_SHAPED_WALL, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.CROSS_BARRIER, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.POLYGON_DEBRIS, 1);
                break;
                
            case TUNDRA:
                // Tundra: Ice formations and crystals
                addWeighted(weightedList, Obstacle.ObstacleType.HEXAGON_CRYSTAL, 3);
                addWeighted(weightedList, Obstacle.ObstacleType.DIAMOND_STONE, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.TRIANGLE_ROCK, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.BOULDER, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.POLYGON_DEBRIS, 1);
                break;
                
            case VOLCANIC:
                // Volcanic: Jagged rocks and formations
                addWeighted(weightedList, Obstacle.ObstacleType.TRIANGLE_ROCK, 4);
                addWeighted(weightedList, Obstacle.ObstacleType.POLYGON_DEBRIS, 3);
                addWeighted(weightedList, Obstacle.ObstacleType.BOULDER, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.DIAMOND_STONE, 1);
                break;
                
            case GRASSLAND:
                // Grassland: Natural obstacles with some structures
                addWeighted(weightedList, Obstacle.ObstacleType.BOULDER, 4);
                addWeighted(weightedList, Obstacle.ObstacleType.TRIANGLE_ROCK, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.HOUSE, 2);
                addWeighted(weightedList, Obstacle.ObstacleType.WALL_SEGMENT, 1);
                addWeighted(weightedList, Obstacle.ObstacleType.POLYGON_DEBRIS, 1);
                break;
                
            default:
                // Default: Mixed selection
                return terrainObstacles.get(random.nextInt(terrainObstacles.size()));
        }
        
        return weightedList.get(random.nextInt(weightedList.size()));
    }
    
    /**
     * Get all obstacle types appropriate for the current terrain.
     */
    private List<Obstacle.ObstacleType> getTerrainObstacleTypes() {
        List<Obstacle.ObstacleType> types = new ArrayList<>();
        
        switch (terrainType) {
            case FOREST:
                types.addAll(Arrays.asList(
                    Obstacle.ObstacleType.BOULDER,
                    Obstacle.ObstacleType.TRIANGLE_ROCK,
                    Obstacle.ObstacleType.POLYGON_DEBRIS,
                    Obstacle.ObstacleType.HOUSE
                ));
                break;
                
            case DESERT:
                types.addAll(Arrays.asList(
                    Obstacle.ObstacleType.BOULDER,
                    Obstacle.ObstacleType.TRIANGLE_ROCK,
                    Obstacle.ObstacleType.DIAMOND_STONE,
                    Obstacle.ObstacleType.POLYGON_DEBRIS,
                    Obstacle.ObstacleType.WALL_SEGMENT
                ));
                break;
                
            case URBAN:
                types.addAll(Arrays.asList(
                    Obstacle.ObstacleType.HOUSE,
                    Obstacle.ObstacleType.WALL_SEGMENT,
                    Obstacle.ObstacleType.L_SHAPED_WALL,
                    Obstacle.ObstacleType.CROSS_BARRIER,
                    Obstacle.ObstacleType.POLYGON_DEBRIS
                ));
                break;
                
            case TUNDRA:
                types.addAll(Arrays.asList(
                    Obstacle.ObstacleType.HEXAGON_CRYSTAL,
                    Obstacle.ObstacleType.DIAMOND_STONE,
                    Obstacle.ObstacleType.TRIANGLE_ROCK,
                    Obstacle.ObstacleType.BOULDER,
                    Obstacle.ObstacleType.POLYGON_DEBRIS
                ));
                break;
                
            case VOLCANIC:
                types.addAll(Arrays.asList(
                    Obstacle.ObstacleType.TRIANGLE_ROCK,
                    Obstacle.ObstacleType.POLYGON_DEBRIS,
                    Obstacle.ObstacleType.BOULDER,
                    Obstacle.ObstacleType.DIAMOND_STONE
                ));
                break;
                
            case GRASSLAND:
                types.addAll(Arrays.asList(
                    Obstacle.ObstacleType.BOULDER,
                    Obstacle.ObstacleType.TRIANGLE_ROCK,
                    Obstacle.ObstacleType.HOUSE,
                    Obstacle.ObstacleType.WALL_SEGMENT,
                    Obstacle.ObstacleType.POLYGON_DEBRIS
                ));
                break;
                
            default:
                // Include all types for unknown terrain
                types.addAll(Arrays.asList(Obstacle.ObstacleType.values()));
                break;
        }
        
        return types;
    }
    
    /**
     * Add multiple instances of an obstacle type to create weighted selection.
     */
    private void addWeighted(List<Obstacle.ObstacleType> list, Obstacle.ObstacleType type, int weight) {
        for (int i = 0; i < weight; i++) {
            list.add(type);
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
        return switch (terrainType) {
            case FOREST -> 2; // Rock formations, fallen tree clusters
            case DESERT -> 2; // Mesa formations, ancient ruins
            case URBAN -> 4;  // Buildings, vehicle wrecks, industrial complexes
            case TUNDRA -> 1; // Ice formations
            case VOLCANIC -> 2; // Rock formations, volcanic structures
            case GRASSLAND -> 1; // Natural rock formations
            default -> 2;
        };
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
        return switch (terrainType) {
            case FOREST -> {
                String[] forestTypes = {"rock_formation", "ruins"};
                yield forestTypes[random.nextInt(forestTypes.length)];
            }
            case DESERT -> {
                String[] desertTypes = {"rock_formation", "ruins", "fortification"};
                yield desertTypes[random.nextInt(desertTypes.length)];
            }
            case URBAN -> {
                String[] urbanTypes = {"building_complex", "vehicle_wreck", "industrial_complex", "ruins"};
                yield urbanTypes[random.nextInt(urbanTypes.length)];
            }
            case TUNDRA -> {
                String[] tundraTypes = {"rock_formation", "fortification"};
                yield tundraTypes[random.nextInt(tundraTypes.length)];
            }
            case VOLCANIC -> {
                String[] volcanicTypes = {"rock_formation", "ruins", "industrial_complex"};
                yield volcanicTypes[random.nextInt(volcanicTypes.length)];
            }
            case GRASSLAND -> {
                String[] grasslandTypes = {"rock_formation", "ruins"};
                yield grasslandTypes[random.nextInt(grasslandTypes.length)];
            }
            default -> {
                String[] defaultTypes = {"rock_formation", "ruins", "vehicle_wreck"};
                yield defaultTypes[random.nextInt(defaultTypes.length)];
            }
        };
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
        return switch (terrainType) {
            case FOREST -> 0.3;
            case DESERT -> 0.1;
            case URBAN -> 0.4;
            case TUNDRA -> 0.2;
            case VOLCANIC -> 0.6;
            case GRASSLAND -> 0.1;
            default -> 0.2;
        };
    }
    
    private double getAmbientLight() {
        return switch (terrainType) {
            case FOREST -> 0.7;
            case DESERT -> 1.0;
            case URBAN -> 0.6;
            case TUNDRA -> 0.8;
            case VOLCANIC -> 0.5;
            case GRASSLAND -> 0.9;
            default -> 0.8;
        };
    }
    
    private int getTemperature() {
        return switch (terrainType) {
            case FOREST -> 18 + random.nextInt(10); // 18-27°C
            case DESERT -> 35 + random.nextInt(15); // 35-49°C
            case URBAN -> 20 + random.nextInt(12); // 20-31°C
            case TUNDRA -> -15 + random.nextInt(10); // -15 to -6°C
            case VOLCANIC -> 45 + random.nextInt(20); // 45-64°C
            case GRASSLAND -> 15 + random.nextInt(15); // 15-29°C
            default -> 20;
        };
    }
    
    private String getParticleType() {
        return switch (terrainType) {
            case FOREST -> "leaves";
            case DESERT -> "sand";
            case URBAN -> "dust";
            case TUNDRA -> "snow";
            case VOLCANIC -> "ash";
            case GRASSLAND -> "pollen";
            default -> "dust";
        };
    }
    
    private double getParticleDensity() {
        return 0.1 + random.nextDouble() * 0.4; // 0.1-0.5
    }
    
    private String getParticleColor() {
        return switch (terrainType) {
            case FOREST -> "#4a7c59";
            case DESERT -> "#d4a574";
            case URBAN -> "#888888";
            case TUNDRA -> "#ffffff";
            case VOLCANIC -> "#666666";
            case GRASSLAND -> "#ffff88";
            default -> "#cccccc";
        };
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
