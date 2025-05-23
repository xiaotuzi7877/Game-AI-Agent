/**
 * @fileoverview
 * Game AI Agent – CS440 Artificial Intelligence Projects
 *
 * This repository contains multiple AI agents written in Java for solving complex game environments
 * The agents apply a range of core AI techniques, including A* search, stochastic tree traversal,
 * and deep reinforcement learning.
 *
 * The project is structured into modules under the `src/pas/` directory, where each subfolder
 * contains a different programming assignment (PA). Additional assets such as libraries,
 * documentation, and testing files are provided to support development.
 *
 * Key components:
 * - `src/pas/stealth/StealthAgent.java`: A* search agent for stealthy map navigation
 * - `src/pas/pokemon/TreeTraversalAgent.java`: Stochastic tree search for Pokémon combat
 * - `src/pas/tetris/TetrisQAgent.java`: Deep Q-learning agent for high-score Tetris play
 *
 * Author: [Your Name]
 * Course: CS440 - Artificial Intelligence
 * Semester: Spring 2025
 * Language: Java 17
 */

/**
 * StealthAgent implements an A* search agent for navigating a hostile grid-world
 * to destroy an enemy Townhall and return safely, while avoiding enemy archers.
 *
 * <p>This agent operates in a Sepia environment, and uses an edge-weighted graph
 * with custom heuristic design to represent "danger" zones near enemy archers.
 * A successful mission consists of infiltrating, destroying the Townhall, and exfiltrating
 * to the start tile before being detected or killed.
 *
 * <p>Key features:
 * - A* pathfinding with dynamic edge weights for risk modeling.
 * - Pre-destruction and post-destruction path planning (escape after alarm).
 * - Gold-theft logic implemented for extra credit consideration.
 *
 * <p>Performance goals (for full credit):
 * - Win 100% of OneUnitSmallMaze
 * - Win ≥95% of TwoUnitSmallMaze
 * - Win ≥20% of BigMaze
 *
 * <p>Extra credit: collect gold before destroying Townhall and still escape successfully.
 *
 * Author: [Your Name]
 * Date: February 2025
 */

/**
 * TreeTraversalAgent implements a stochastic tree search agent
 * for turn-based Pokémon battles with probabilistic outcomes.
 *
 * <p>This agent explores all possible action outcomes using a game tree
 * that incorporates CHANCE, MIN, and MAX nodes to account for move
 * order randomness, status effects (e.g., paralysis, sleep), and uncertain
 * move results.
 *
 * <p>Core logic includes:
 * - Tree construction with chance nodes and partial rollouts.
 * - Heuristic pruning for large state spaces.
 * - Expectiminimax-style traversal with customizable evaluation functions.
 *
 * <p>Evaluation criteria:
 * - ≥80% win on EASY (Brock)
 * - ≥50% win on MEDIUM (Sabrina)
 * - ≥25% win on HARD (Lance)
 *
 * <p>Extra credit: beat an elite-level hidden agent to receive bonus points.
 *
 * Author: [Your Name]
 * Date: March 2025
 */


/**
 * TetrisQAgent implements a Q-learning based reinforcement learning agent
 * for playing a custom-engineered version of Tetris using a neural network
 * to approximate the Q-function.
 *
 * <p>The agent trains by playing thousands of games, storing transitions
 * in a replay buffer, and learning from Bellman-updated ground truths.
 * Actions represent the final resting position of a piece, rather than guided descent.
 *
 * <p>Implemented features:
 * - Custom reward function based on line clears, t-spins, board clearing.
 * - Feature vector engineering from board states and Mino placements.
 * - Deep Q-network (DQN) with tunable layers and activation functions.
 * - Epsilon-greedy exploration policy with customizable randomness.
 *
 * <p>Training process:
 * 1. Collect game transitions via simulated plays.
 * 2. Use Bellman updates to compute target Q-values.
 * 3. Train neural network in batches from replay buffer.
 * 4. Evaluate on frozen model every N cycles.
 *
 * <p>Full credit threshold: ≥20 points averaged over 500 evaluation games.
 * Extra credit threshold: ≥40 points.
 *
 * Author: [Your Name]
 * Date: April 2025
 */
