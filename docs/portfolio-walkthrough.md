# Game AI interview walkthrough

## 60-second explanation

I implemented three Java agents: risk-weighted A* for stealth, bounded stochastic adversarial search for Pokemon, and a neural Q-value model for Tetris. The common engineering problem was choosing a useful representation and bounding computation while preserving decision quality.

## Deep-dive questions

1. Show the A* priority queue, best-cost map, and enemy-distance penalties. Why might the safest path be longer?
2. When can a cached path become stale? Explain the replanning checks and cooldown.
3. Trace a Pokemon move through chance outcomes, opponent minimization, and terminal/HP utility.
4. What does outcome truncation discard, and how does renormalization affect expectations?
5. Explain the actual two-ply search instead of inferring depth from a configuration variable.
6. Why combine the board image and seven explicit Tetris features?
7. What behaviors do line-clear rewards and hole/height penalties encourage? Treat ablations as proposed experiments, not completed results.
8. Distinguish training reward from evaluation score and generalization across seeds.

## Evidence

All three agents compiled on JDK 8 with the supplied libraries. No new training run or win-rate benchmark was conducted during cleanup. Keep agent implementations, framework code, and historical results separate when explaining ownership.
