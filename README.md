The server listens on port 8080 by default (configurable).

### Running the Client

1. Open `src/main/resources/game.html` in your browser.
2. Connect to the server using the provided UI.

## Configuration

Server settings can be customized via environment variables or system properties. See `src/main/java/com/fullsteam/Config.java` for all options (e.g., `game.width`, `game.max_players_per_team`, etc).

## Controls

- **WASD:** Move
- **Mouse:** Aim
- **Left Click:** Shoot
- **R:** Reload
- **Space:** Alternate action (if available)

## Game Modes

- **Team Deathmatch:** Eliminate the opposing team to score points.
- **King of the Hill:** Control the central hill to accumulate points.
- **Juggernaut:** Each team has a powerful juggernaut; eliminate the enemy juggernaut to score.
- **Oddball:** Hold the ball to score points for your team.
- **Lone Wolf:** One player is the wolf, others are hunters. The wolf grows stronger with each death.
- **Zombie Defense:** Survive waves of AI zombies as a team.

## Development

- Server code: `src/main/java/com/fullsteam/`
- Client code: `src/main/resources/game.html` and related assets
