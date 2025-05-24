# Artificial Intelligence, Neural Networks, Reinforcement Learning - AI Game Agents

## **Folder Overview**
This directory (`/Game-AI-Agent/`) contains three core programming Projects (PAs) for : Artificial Intelligence. Each PA focuses on implementing AI agents for distinct games, leveraging algorithms like A*, tree traversal, and Q-learning with neural networks. Below is a summary of the goals, content, and key games covered.

---

## **Objectives**
1. **Apply AI Concepts**: Implement algorithms taught in class (e.g., search, reinforcement learning) to solve real-world game scenarios.
2. **Hands-on Learning**: Develop agents that interact with stochastic environments, adversarial opponents, and complex reward systems.
3. **Performance Optimization**: Meet autograder criteria for win rates, scores, and efficiency.

---

## **Content Summary**
### 1. **PA1: Infiltration & Exfiltration**
- **Game**: Stealth-based strategy (Sepia engine).
- **Goal**: Navigate a maze, destroy an enemy townhall, and escape undetected by archers.
- **Key AI**: 
  - **A*** for pathfinding with risk-weighted edges.
  - Reward engineering for "danger" (e.g., proximity to enemies).
- **Files**:
  - `StealthAgent.java` (main agent).
  - Maze configurations (e.g., `BigMaze.xml`).

### 2. **PA2: Stochastic Pokémon**
- **Game**: Turn-based Pokémon battles with randomness.
- **Goal**: Defeat opponents using tree-search agents that handle stochastic outcomes (e.g., move priority, status effects).
- **Key AI**:
  - **Expectiminimax** with chance nodes.
  - Utility heuristics for infinite game trees.
- **Files**:
  - `TreeTraversalAgent.java` (implements battle logic).
  - Difficulty tiers (Easy/Medium/Hard opponent teams).

### 3. **PA3: Tetris**
- **Game**: Tetris with accelerated piece placement.
- **Goal**: Maximize score using Q-learning and neural networks.
- **Key AI**:
  - **Q-learning** with replay buffers.
  - Neural network for Q-value approximation.
  - Reward function engineering (e.g., penalizing gaps).
- **Files**:
  - `TetrisQAgent.java` (RL agent).
  - `params.model` (trained network weights).

---

## **Key Games Covered**
| Game | Type | Core Challenge | AI Technique |
|------|------|----------------|--------------|
| **Stealth Maze** | Real-time strategy | Avoid detection, optimize path under risk | A* with custom edge weights |
| **Stochastic Pokémon** | Turn-based battle | Handle randomness (priority, status effects) | Expectiminimax + chance nodes |
| **Tetris** | Puzzle | Maximize score through piece placement | Q-learning + neural networks |

---

## **Outcomes**
- **PA1**: Demonstrated search algorithm adaptability in adversarial environments.
- **PA2**: Mastered stochastic game trees and heuristic design.
- **PA3**: Implemented RL from scratch, including neural network training.

For detailed instructions, refer to individual PA documentation in the `/pas/` subdirectories.

# PA1: Stealth Agent 

## Core Algorithm: Risk-Aware A*

### Key Idea
Modifies standard A* to prioritize **safety over shortest distance** by:
1. **Danger Scoring**: Each map position gets a danger value based on:
   ```python
   danger_score = Σ (1 / distance_to_archer) for all archers
   ```

2. **Weighted Path Cost:**
    
    ```python
    total_cost = base_distance + (danger_factor * danger_score)
    ```

## How It Works

1. **Infiltration Phase**:
   - Finds path to townhall avoiding high-danger zones
   - Uses conservative danger weights (λ=0.7)

2. **Exfiltration Phase**:
   - After destroying townhall, uses aggressive weights (λ=0.3)
   - Prioritizes speed over safety

## Optimization Tricks

- **Path Caching**: Stores safe routes for reuse
- **Lazy Updates**: Only recalculates danger for changed areas
- **Adaptive λ**: Automatically adjusts based on remaining HP

## Key Methods

| Method             | Purpose                          |
|--------------------|----------------------------------|
| `findSafePath()`   | Main A* implementation           |
| `getDangerScore()` | Calculates threat levels         |
| `updateWeights()`  | Adjusts λ dynamically            |

## Performance

- **Success Rates:**
  - Small maps: 100%
  - Large maps: ~20% (due to time constraints)

- **Speed:** <50ms per decision

```bash
# Quick Run
java -cp "./lib/*:." Main data/small_map.xml
```

# PA2: Stochastic Pokémon Battle Agent

## Core Algorithm: Expectiminimax with Chance Nodes

### Key Idea
The agent implements a modified expectiminimax algorithm to handle Pokémon battles' stochastic nature. Unlike classic minimax, it incorporates:

1. **Chance nodes** for probabilistic outcomes (move accuracy, status effects)  
2. **Depth-limited search** due to the game's branching factor  
3. **Utility heuristics** for non-terminal states  

### Algorithm Structure
<img width="530" alt="截屏2025-05-24 下午1 43 32" src="https://github.com/user-attachments/assets/b26cbbe6-9896-42c2-afae-8f3c35f0856c" />

## Critical Components

1. **State Evaluation**
   - Primary heuristic: HP differential between teams
   - Terminal states:
     - Win: +999,999 utility
     - Loss: -999,999 utility
   - Non-terminal: Σ(active HP) - Σ(opponent HP)

2. **Chance Node Handling**
   - Uses `getPotentialEffects()` API to:
     - Generate all possible move outcomes
     - Get associated probabilities
   - Implements outcome trimming:
     - Sorts outcomes by probability
     - Keeps top N outcomes (configurable)
     - Renormalizes probabilities

3. **Opponent Modeling**
   - Assumes opponent minimizes our utility
   - Evaluates all opponent moves
   - Propagates worst-case (for us) scenario

## Key Optimizations

1. **Branch Pruning**
   - Limits evaluated outcomes per move (`MAX_OUTCOMES_PER_MOVE`)
   - Reduces computation from O(b^d) to O(k^d), where k = pruned branches

2. **Asynchronous Execution**
   - Runs search in background thread
   - Implements timeout (6 minutes/move)
   - Falls back to simple heuristic if timeout occurs

3. **Efficient State Representation**
   - Uses game engine's `BattleView`
   - Minimizes object creation
   - Reuses probability calculations
  
## Implementation Details

### Core Methods:

1. `stochasticTreeSearch(BattleView)`
   - Entry point for move selection
   - Orchestrates 2-ply search:
     - Our move → Possible outcomes → Opponent responses

2. `computeMyMoveValue()`
   - Calculates expected utility for our move
   - Aggregates weighted outcomes
   - Propagates to opponent turn

3. `computeOpponentMinValue()`
   - Simulates opponent's optimal play
   - Selects move minimizing our utility
   - Handles opponent's chance nodes

## Configuration Parameters:

- `MAX_OUTCOMES_PER_MOVE = 8`
- `maxDepth = 1000` (effectively 2-ply)
- `maxThinkingTimePerMoveInMS = 360,000` (6 minutes)

## Performance Characteristics

### Success Rates:

- Easy (Brock): ~90%
- Medium (Sabrina): ~60%
- Hard (Lance): ~30%

### Computation Time:

- Average: 2–3 minutes/move
- Worst-case: 6 minutes (timeout)

### Memory Usage:

- ~500MB heap space
- Linear in search depth

## Usage Example

```java
// Run against Brock's team (Easy)
java -cp "./lib/*:." edu.bu.labs.pokemon.Easy

// Use custom agent
java -cp "./lib/*:." edu.bu.labs.pokemon.Main --tilAgent src.labs.pokemon.agents.MinimaxAgent
```

## Extension Points

1. **Improved Heuristics**
   - Incorporate status effects
   - Add type advantage calculations
   - Consider stat boosts

2. **Advanced Pruning**
   - Alpha-beta for deterministic branches
   - Probability threshold cutting

3. **Opening Book**
   - Cache common early-game sequences
   - Reduce computation time

This design effectively balances computation time with decision quality, handling Pokémon's stochastic nature while remaining practical for turn-based play. The agent demonstrates competency across all difficulty levels while maintaining reasonable runtime performance.


# PA3: Tetris Q-Learning Agent

## Core Algorithm: Deep Q-Learning with Neural Network

### Key Idea
The agent implements a reinforcement learning system that:

1. Learns a Q-function approximation using a neural network  
2. Balances exploration vs exploitation with decaying ε-greedy policy  
3. Uses engineered features to represent board states and piece placements  

## Architecture Components

### Neural Network Model:

- 3-layer fully connected network  
- Input: Board state (flattened) + 7 engineered features  
- Hidden layers: 512 and 256 ReLU units  
- Output: Single Q-value (unbounded)
  
## Training Process:

1. **Experience Collection**:
   - Plays games with ε-greedy policy
   - Stores transitions (state, action, reward, next_state) in replay buffer

2. **Learning Phase**:
   - Samples mini-batches from replay buffer
   - Updates network using Bellman equation:

     ```
     Q(s, a) = R(s, a) + γ * max(Q(s', a'))
     ```

   - Uses Mean Squared Error loss

3. **Evaluation**:
   - Periodically tests current policy
   - Tracks average score over evaluation games

## State Representation:

1. Raw board state (20x10 grayscale image)  
2. Engineered features:
   - Max stack height
   - Number of holes
   - Bottom row completion
   - Piece type encoding
   - Pivot position
   - Rotation state
   - Column balance

## Reward Function:

- Line clears: +500 per line  
- Height penalty: -0.4 per row  
- Hole penalty: -0.4 per hole  
- Survival bonus: +2 per step  
- T-Spin bonus: +50  
- Game over penalty: -15  
- Bottom row completion: +4 per filled column  

## Exploration Strategy

### ε-Greedy Policy:

- Starts with ε = 0.9  
- Decays to ε = 0.03 over training  
- Exploration moves use heuristic evaluation with noise  

### Heuristic Evaluation (for exploration):

- Scores potential moves based on:
  - Line completion potential
  - Hole filling
  - Neighbor blocks
  - Height considerations
  - Column balancing
  - Special bonuses for T-pieces

---

## Performance Characteristics

### Training Metrics:

- Target performance: 20+ average score  
- Extra credit: 40+ average score  
- Typical training time: Days on SCC cluster  

### Key Parameters:

- Initial exploration: 90%  
- Min exploration: 3%  
- Exploration decay: 0.95 per cycle  
- Discount factor (γ): 0.99  
- Learning rate: 0.001 (via Adam optimizer)

## Implementation Highlights

### Critical Methods:

1. `initQFunction()` – Neural network architecture  
2. `getQFunctionInput()` – State feature engineering  
3. `getReward()` – Custom reward shaping  
4. `shouldExplore()` – ε-greedy policy  
5. `getExplorationMove()` – Heuristic move selection  

### Optimization Techniques:

- Experience replay buffer  
- Mini-batch training  
- Target network (optional extension)  
- Feature normalization  

## Usage Example

```bash
# Train the agent (with logging)
java -cp "./lib/*:." edu.bu.tetris.Main | tee training.log

# Plot learning curve
python learning_curve.py training.log
```

## Extension Points

1. **Advanced Features**:
   - Add lookahead for next pieces
   - Incorporate attack/defense metrics
   - Better T-Spin detection

2. **Network Improvements**:
   - Convolutional layers for spatial features
   - Dueling network architecture
   - Prioritized experience replay

3. **Training Enhancements**:
   - Curriculum learning
   - Dynamic reward shaping
   - Parallel self-play
  
# Summary and Reflection

## Technical Gains

### 1. Algorithm Implementation Skills

- **A\* Algorithm**: Through PA1’s stealth navigation task, I learned how to adapt classical algorithms—such as incorporating risk-aware weights—to solve real-world problems.  
- **Game Tree Search**: PA2’s Pokémon battle simulation deepened my understanding of how the Expectiminimax algorithm can be applied to stochastic game environments.  
- **Deep Reinforcement Learning**: PA3’s Tetris project taught me how to design neural network architectures and reward functions to implement Q-learning.

### 2. Engineering Practice

- **Java Development**: All three projects were implemented in Java, which strengthened my object-oriented programming skills.  
- **Performance Optimization**: I learned techniques such as pruning, caching, and asynchronous computation to improve performance in compute-intensive tasks.  
- **Debugging Techniques**: I analyzed game logs and used visualization tools to iteratively improve AI behavior.

---

## Cognitive Growth

### 1. On AI Development

- **Importance of Feature Engineering**: In the Tetris project, I realized that designing effective state representations is more critical than the complexity of the model architecture.  
- **The Art of Exploration vs. Exploitation**: Tuning the ε-greedy policy helped me understand how to balance short-term performance with long-term learning.  
- **Reward Shaping Sensitivity**: I discovered that poorly designed reward functions can lead to unintended AI behaviors.

### 2. On Problem Solving

- **Value of Incremental Development**: I learned the importance of first building a functional baseline before progressively refining the solution.  
- **Evaluation Metric Design**: Effective evaluation requires not only tracking final outcomes but also monitoring signals during the learning process.  
- **Resource-Aware Planning**: I practiced designing long-term training tasks under limited computational resources.

---

## Acknowledgments

I am deeply grateful for these three projects, which helped me move from theoretical knowledge to practical implementation. They not only improved my programming abilities but also shaped my thinking in solving complex problems. These experiences will become valuable assets in my future technical journey and strengthened my passion and respect for the field of Artificial Intelligence.

> "These projects were like three keys that opened the door to AI application development. From search algorithms to reinforcement learning, I witnessed the limitless potential of intelligent systems—and clearly realized how much more I have to learn."



