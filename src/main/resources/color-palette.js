const GameColors = {
  // ========== TEAMS ==========
  teams: {
    team1: {
      primary: '#4CAF50',      // Green - main team color
      secondary: '#81C784',    // Light green - secondary elements
      dark: '#2E7D32',         // Dark green - borders/shadows
      darker: '#388E3C',       // Darker green - additional contrast
      bright: '#00FF64'        // Bright green - laser effects
    },
    team2: {
      primary: '#F44336',      // Red - main team color
      secondary: '#E57373',    // Light red - secondary elements
      dark: '#C62828',         // Dark red - borders/shadows
      darker: '#B71C1C',       // Darker red - additional contrast
      bright: '#FF3232'        // Bright red - laser effects
    }
  },

  // ========== UI BACKGROUND & STRUCTURE ==========
  background: {
    primary: '#222222',        // Main page background
    secondary: '#333333',      // Canvas/game area background
    panel: '#2a2a2a',         // Scoreboard container
    card: '#444444',           // Input backgrounds, borders
    darker: '#111111'          // Deep shadows, borders
  },

  // ========== BORDERS & DIVIDERS ==========
  borders: {
    primary: '#444444',        // Main borders
    secondary: '#666666',      // Input borders
    light: '#555555',          // Light dividers
    lighter: '#3a3a3a',        // Subtle dividers
    accent: '#c7574e'          // Button borders
  },

  // ========== TEXT COLORS ==========
  text: {
    primary: '#ffffff',        // Main text (white)
    secondary: '#ccc',         // Secondary text (light gray)
    muted: '#aaa',            // Muted text (medium gray)
    disabled: '#666666'        // Disabled text
  },

  // ========== ACCENT & HIGHLIGHT ==========
  accent: {
    gold: '#FFC107',          // Gold - highlights, timer, local player
    yellow: '#FFD700',        // Bright yellow - oddball, events
    orange: '#FFA000',        // Orange - oddball rays
    blue: '#4FC3F7',          // Blue - spectator mode, events
    purple: '#7B1FA2'         // Purple - energy weapons
  },

  // ========== INTERACTIVE ELEMENTS ==========
  interactive: {
    button: {
      primary: '#a1322a',      // Button background
      hover: '#c7574e',        // Button hover
      border: '#c7574e'        // Button border
    },
    input: {
      background: '#444444',   // Input background
      border: '#666666',       // Input border
      text: '#ffffff'          // Input text
    }
  },

  // ========== GAME ENTITIES ==========
  entities: {
    // Power-ups
    powerUps: {
      clear: '#fff'
    },

    // Vehicles
    vehicles: {
      tank: {
        body: '#4A4A4A',
        stroke: '#2A2A2A',
        accent: '#5A5A5A'
      },
      mech: {
        body: '#2E7D32',
        stroke: '#1B5E20',
        accent: '#4CAF50'
      },
      jeep: {
        body: '#8D6E63',
        stroke: '#5D4037',
        accent: '#A1887F'
      },
      cannon: {
        body: '#616161',
        stroke: '#424242',
        accent: '#757575'
      },
      davinci: {
        body: '#8E24AA',        // Purple - artistic theme
        stroke: '#4A148C',      // Dark purple
        accent: '#BA68C8'       // Light purple accent
      }
    },

    // Weapons & Barrels
    weapons: {
      default: {
        barrel: '#2A2A2A',
        stroke: '#1A1A1A'
      },
      laser: {
        barrel: '#4A90E2',
        stroke: '#2E5C8A'
      },
      rocket: {
        barrel: '#D32F2F',
        stroke: '#B71C1C'
      },
      sniper: {
        barrel: '#1976D2',
        stroke: '#0D47A1'
      },
      minigun: {
        barrel: '#388E3C',
        stroke: '#1B5E20'
      },
      flamethrower: {
        barrel: '#F57C00',
        stroke: '#E65100'
      },
      energy: {
        barrel: '#7B1FA2',
        stroke: '#4A148C'
      }
    },

    // Game Objects
    gameObjects: {
      crates: {
        body: '#8B4513',        // Saddle brown
        border: '#5D2906'       // Darker brown
      },
      obstacles: '#888888',     // Gray obstacles
      payload: {
        body: '#B0BEC5',        // Blue gray
        stroke: '#546E7A'       // Darker blue gray
      },
      base: {
        primary: '#607D8B',     // Blue gray for defensive structure
        secondary: '#37474F',   // Darker outline
        inner: '#455A64',       // Inner details
        core: '#546E7A',        // Center core
        spikes: '#78909C'       // Defensive spikes
      }
    }
  },

  // ========== FIELD EFFECTS ==========
  effects: {
    explosion: {
      core: 'rgba(255, 255, 150, 0.9)',    // Bright center
      middle: 'rgba(255, 100, 0, 0.7)',    // Orange middle
      edge: 'rgba(200, 0, 0, 0)'           // Red fade to transparent
    },
    poison: {
      light: 'rgba(129, 199, 132, 0.6)',   // Light green
      medium: 'rgba(56, 142, 60, 0.5)',    // Medium green
      dark: 'rgba(27, 94, 32, 0)'          // Dark green fade
    },
    slowField: {
      team1: {
        light: 'rgba(173, 216, 230, 0.5)',  // Light blue
        medium: 'rgba(100, 149, 237, 0.4)', // Medium blue
        dark: 'rgba(65, 105, 225, 0)'       // Dark blue fade
      },
      team2: {
        light: 'rgba(255, 182, 193, 0.5)',  // Light pink
        medium: 'rgba(219, 112, 147, 0.4)', // Medium pink
        dark: 'rgba(199, 21, 133, 0)'       // Dark pink fade
      },
      neutral: {
        light: 'rgba(176, 196, 222, 0.5)',  // Light steel blue
        medium: 'rgba(119, 136, 153, 0.4)', // Light slate gray
        dark: 'rgba(70, 130, 180, 0)'       // Steel blue fade
      }
    },
    smoke: {
      light: 'rgba(180, 180, 180, 0.5)',   // Light gray
      medium: 'rgba(120, 120, 120, 0.4)',  // Medium gray
      dark: 'rgba(80, 80, 80, 0.3)',       // Dark gray
      fade: 'rgba(60, 60, 60, 0)',         // Fade to transparent
      swirl: 'rgba(160, 160, 160, 0.3)'    // Swirl effect
    }
  },

  // ========== HEALTH & STATUS ==========
  health: {
    high: '#4CAF50',          // Green (>50%)
    medium: '#FFC107',        // Yellow (20-50%)
    low: '#F44336',           // Red (<20%)
    background: '#333333',    // Health bar background
    border: '#111111'         // Health bar border
  },

  // ========== GAME MODES & SPECIAL ==========
  special: {
    hill: {
      contested: '#FFC107',    // Yellow when contested
      neutral: '#9E9E9E',      // Gray when neutral
      flag: '#616161'          // Flag pole color
    },
    mine: {
      team1: { base: '#4CAF50', dark: '#2E7D32' },
      team2: { base: '#F44336', dark: '#C62828' },
      neutral: { base: '#9E9E9E', dark: '#616161' }
    },
    turrets: {
      legs: '#616161',         // Dark gray legs
      barrel: '#424242',       // Darker gray barrel
      barrelStroke: '#212121'  // Very dark barrel outline
    }
  },

  // ========== TRANSPARENCY & OVERLAYS ==========
  overlays: {
    respawn: 'rgba(0, 0, 0, 0.7)',        // Respawn overlay
    preview: 'rgba(255, 255, 255, 0.1)',   // Crate preview fill
    previewStroke: 'rgba(255, 255, 255, 0.3)', // Crate preview stroke
    gridLines: 'rgba(255, 255, 255, 0.05)', // Background grid
    debug: 'rgba(0, 0, 0, 0.7)',          // Debug info background
    interaction: 'rgba(255, 255, 0, 0.8)'  // Vehicle interaction hint
  },

  // ========== LASER & ENERGY EFFECTS ==========
  laser: {
    team1: {
      core: 'rgba(0, 255, 100, 0.8)',     // Bright green core
      glow: 'rgba(0, 255, 100, 0.2)'      // Green glow
    },
    team2: {
      core: 'rgba(255, 50, 50, 0.8)',     // Bright red core
      glow: 'rgba(255, 50, 50, 0.2)'      // Red glow
    },
    neutral: {
      core: 'rgba(255, 255, 255, 0.8)',   // White core
      glow: 'rgba(255, 255, 255, 0.2)'    // White glow
    }
  },

  // ========== EVENT COLORS ==========
  events: {
    green: '#81C784',         // Green events
    red: '#F44336',           // Red events
    yellow: '#FFD700',        // Yellow events
    blue: '#4FC3F7',          // Blue events
    default: '#FFFFFF'        // Default white
  }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
  module.exports = GameColors;
} else if (typeof window !== 'undefined') {
  window.GameColors = GameColors;
}
