package com.fullsteam.physics;

import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.geometry.MassType;

import java.util.ArrayList;
import java.util.List;

/**
 * CompoundObstacle represents a complex multi-body structure in the game world.
 * Unlike simple obstacles, these are composed of multiple physics bodies
 * arranged to form interesting structures like buildings, fortifications, etc.
 */
@Getter
public class CompoundObstacle {
    private final String type;
    private final Vector2 centerPosition;
    private final List<Body> bodies;
    private final double width;
    private final double height;
    private final String biomeType;
    
    public CompoundObstacle(String type, Vector2 centerPosition, String biomeType) {
        this.type = type;
        this.centerPosition = centerPosition.copy();
        this.biomeType = biomeType;
        this.bodies = new ArrayList<>();
        
        // Build the compound structure based on type
        switch (type.toLowerCase()) {
            case "building_complex":
                buildingComplex();
                break;
            case "fortification":
                fortification();
                break;
            case "vehicle_wreck":
                vehicleWreck();
                break;
            case "rock_formation":
                rockFormation();
                break;
            case "industrial_complex":
                industrialComplex();
                break;
            case "ruins":
                ruins();
                break;
            default:
                simpleStructure();
                break;
        }
        
        // Calculate bounding dimensions
        this.width = calculateWidth();
        this.height = calculateHeight();
    }
    
    /**
     * Get all physics bodies for adding to the world.
     * The GameManager will handle adding them to the physics world.
     */
    public List<Body> getBodies() {
        return new ArrayList<>(bodies);
    }
    
    /**
     * Check if a point is within the compound obstacle's bounds.
     */
    public boolean containsPoint(Vector2 point) {
        return point.x >= centerPosition.x - width/2 && 
               point.x <= centerPosition.x + width/2 &&
               point.y >= centerPosition.y - height/2 && 
               point.y <= centerPosition.y + height/2;
    }
    
    /**
     * Check if this obstacle overlaps with another circular area.
     */
    public boolean overlapsWithCircle(Vector2 center, double radius) {
        // Simple bounding box check first
        double dx = Math.abs(center.x - centerPosition.x);
        double dy = Math.abs(center.y - centerPosition.y);
        
        if (dx > (width/2 + radius) || dy > (height/2 + radius)) {
            return false;
        }
        
        if (dx <= width/2 || dy <= height/2) {
            return true;
        }
        
        double cornerDistance = Math.pow(dx - width/2, 2) + Math.pow(dy - height/2, 2);
        return cornerDistance <= radius * radius;
    }
    
    private void buildingComplex() {
        // Main building (large rectangle)
        Body mainBuilding = createRectangleBody(0, 0, 120, 80);
        bodies.add(mainBuilding);
        
        // Side annexes
        Body leftAnnex = createRectangleBody(-80, -20, 40, 40);
        bodies.add(leftAnnex);
        
        Body rightAnnex = createRectangleBody(80, 15, 40, 50);
        bodies.add(rightAnnex);
        
        // Entrance pillars
        Body leftPillar = createRectangleBody(-20, -60, 8, 20);
        bodies.add(leftPillar);
        
        Body rightPillar = createRectangleBody(20, -60, 8, 20);
        bodies.add(rightPillar);
    }
    
    private void fortification() {
        // Central keep
        Body keep = createRectangleBody(0, 0, 60, 60);
        bodies.add(keep);
        
        // Corner towers
        Body nwTower = createCircleBody(-50, 50, 20);
        bodies.add(nwTower);
        
        Body neTower = createCircleBody(50, 50, 20);
        bodies.add(neTower);
        
        Body swTower = createCircleBody(-50, -50, 20);
        bodies.add(swTower);
        
        Body seTower = createCircleBody(50, -50, 20);
        bodies.add(seTower);
        
        // Connecting walls
        Body northWall = createRectangleBody(0, 50, 80, 8);
        bodies.add(northWall);
        
        Body southWall = createRectangleBody(0, -50, 80, 8);
        bodies.add(southWall);
        
        Body eastWall = createRectangleBody(50, 0, 8, 80);
        bodies.add(eastWall);
        
        Body westWall = createRectangleBody(-50, 0, 8, 80);
        bodies.add(westWall);
    }
    
    private void vehicleWreck() {
        // Main chassis
        Body chassis = createRectangleBody(0, 0, 80, 35);
        bodies.add(chassis);
        
        // Engine block
        Body engine = createRectangleBody(-25, 0, 30, 25);
        bodies.add(engine);
        
        // Scattered debris
        Body debris1 = createCircleBody(40, 20, 8);
        bodies.add(debris1);
        
        Body debris2 = createCircleBody(-45, 25, 6);
        bodies.add(debris2);
        
        Body debris3 = createRectangleBody(35, -25, 15, 10);
        bodies.add(debris3);
        
        // Wheels (if any remaining)
        Body wheel1 = createCircleBody(-20, -25, 12);
        bodies.add(wheel1);
        
        Body wheel2 = createCircleBody(20, 25, 12);
        bodies.add(wheel2);
    }
    
    private void rockFormation() {
        // Large central rock
        Body centralRock = createCircleBody(0, 0, 45);
        bodies.add(centralRock);
        
        // Smaller surrounding rocks
        Body rock1 = createCircleBody(-35, 30, 20);
        bodies.add(rock1);
        
        Body rock2 = createCircleBody(40, 25, 25);
        bodies.add(rock2);
        
        Body rock3 = createCircleBody(-25, -40, 18);
        bodies.add(rock3);
        
        Body rock4 = createCircleBody(30, -35, 22);
        bodies.add(rock4);
        
        // Small scattered stones
        Body stone1 = createCircleBody(-60, 10, 8);
        bodies.add(stone1);
        
        Body stone2 = createCircleBody(55, -10, 10);
        bodies.add(stone2);
        
        Body stone3 = createCircleBody(10, 55, 7);
        bodies.add(stone3);
    }
    
    private void industrialComplex() {
        // Main factory building
        Body factory = createRectangleBody(0, 0, 100, 60);
        bodies.add(factory);
        
        // Smokestacks
        Body stack1 = createCircleBody(-30, 40, 8);
        bodies.add(stack1);
        
        Body stack2 = createCircleBody(30, 40, 8);
        bodies.add(stack2);
        
        // Storage tanks
        Body tank1 = createCircleBody(-60, -20, 25);
        bodies.add(tank1);
        
        Body tank2 = createCircleBody(60, -20, 25);
        bodies.add(tank2);
        
        // Loading dock
        Body dock = createRectangleBody(0, -50, 80, 20);
        bodies.add(dock);
        
        // Equipment scattered around
        Body equipment1 = createRectangleBody(-40, 20, 15, 10);
        bodies.add(equipment1);
        
        Body equipment2 = createRectangleBody(45, 15, 12, 12);
        bodies.add(equipment2);
    }
    
    private void ruins() {
        // Broken wall segments
        Body wall1 = createRectangleBody(-40, 20, 30, 8);
        bodies.add(wall1);
        
        Body wall2 = createRectangleBody(35, 25, 25, 8);
        bodies.add(wall2);
        
        Body wall3 = createRectangleBody(-20, -30, 8, 40);
        bodies.add(wall3);
        
        // Collapsed sections (rubble piles)
        Body rubble1 = createCircleBody(0, 0, 30);
        bodies.add(rubble1);
        
        Body rubble2 = createCircleBody(-50, -20, 20);
        bodies.add(rubble2);
        
        Body rubble3 = createCircleBody(40, -15, 18);
        bodies.add(rubble3);
        
        // Standing pillars
        Body pillar1 = createRectangleBody(-15, 40, 10, 25);
        bodies.add(pillar1);
        
        Body pillar2 = createRectangleBody(50, 10, 10, 30);
        bodies.add(pillar2);
        
        // Scattered stones
        Body stone1 = createCircleBody(20, 35, 8);
        bodies.add(stone1);
        
        Body stone2 = createCircleBody(-35, -45, 10);
        bodies.add(stone2);
    }
    
    private void simpleStructure() {
        // Fallback - create a simple multi-part structure
        Body main = createRectangleBody(0, 0, 60, 40);
        bodies.add(main);
        
        Body side1 = createRectangleBody(-40, 0, 20, 20);
        bodies.add(side1);
        
        Body side2 = createRectangleBody(40, 0, 20, 20);
        bodies.add(side2);
    }
    
    private Body createRectangleBody(double offsetX, double offsetY, double width, double height) {
        Body body = new Body();
        Rectangle rect = new Rectangle(width, height);
        BodyFixture fixture = new BodyFixture(rect);
        fixture.setDensity(0.0); // Static obstacle
        fixture.setFriction(0.7);
        fixture.setRestitution(0.3);
        body.addFixture(fixture);
        body.setMass(MassType.INFINITE); // Static
        body.translate(centerPosition.x + offsetX, centerPosition.y + offsetY);
        return body;
    }
    
    private Body createCircleBody(double offsetX, double offsetY, double radius) {
        Body body = new Body();
        Circle circle = new Circle(radius);
        BodyFixture fixture = new BodyFixture(circle);
        fixture.setDensity(0.0); // Static obstacle
        fixture.setFriction(0.7);
        fixture.setRestitution(0.3);
        body.addFixture(fixture);
        body.setMass(MassType.INFINITE); // Static
        body.translate(centerPosition.x + offsetX, centerPosition.y + offsetY);
        return body;
    }
    
    private double calculateWidth() {
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        
        for (Body body : bodies) {
            Vector2 pos = body.getTransform().getTranslation();
            // Approximate bounds - could be more precise
            double radius = 30; // Conservative estimate
            minX = Math.min(minX, pos.x - radius);
            maxX = Math.max(maxX, pos.x + radius);
        }
        
        return Math.max(100, maxX - minX); // Minimum width
    }
    
    private double calculateHeight() {
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (Body body : bodies) {
            Vector2 pos = body.getTransform().getTranslation();
            // Approximate bounds - could be more precise
            double radius = 30; // Conservative estimate
            minY = Math.min(minY, pos.y - radius);
            maxY = Math.max(maxY, pos.y + radius);
        }
        
        return Math.max(100, maxY - minY); // Minimum height
    }
    
    /**
     * Get data for client-side rendering.
     */
    public List<ObstacleData> getRenderingData() {
        List<ObstacleData> renderData = new ArrayList<>();
        
        for (Body body : bodies) {
            Vector2 pos = body.getTransform().getTranslation();
            
            if (body.getFixtureCount() > 0) {
                BodyFixture fixture = body.getFixture(0);
                
                if (fixture.getShape() instanceof Circle) {
                    Circle circle = (Circle) fixture.getShape();
                    renderData.add(new ObstacleData("circle", pos.x, pos.y, circle.getRadius(), 0, 0));
                } else if (fixture.getShape() instanceof Rectangle) {
                    Rectangle rect = (Rectangle) fixture.getShape();
                    renderData.add(new ObstacleData("rectangle", pos.x, pos.y, rect.getWidth(), rect.getHeight(), 0));
                }
            }
        }
        
        return renderData;
    }
    
    /**
     * Data class for client rendering.
     */
    public static class ObstacleData {
        public final String shape;
        public final double x, y;
        public final double width, height;
        public final double rotation;
        
        public ObstacleData(String shape, double x, double y, double width, double height, double rotation) {
            this.shape = shape;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
        }
    }
}
