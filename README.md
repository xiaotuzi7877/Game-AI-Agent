# Game AI Agents in Java

Three personal agent implementations on supplied game frameworks: **risk-aware A*, stochastic adversarial search, and neural Q-learning**. Agent code is under `src/pas/`; engines and teaching libraries under `lib/` remain supporting course infrastructure.

## Architecture and implemented features

| Agent | Decision pipeline | Implementation |
|---|---|---|
| Stealth | Map and enemy positions -> next path/action | Priority-queue A*, enemy-proximity edge penalties, path reuse and replanning cooldowns |
| Pokemon | Battle state -> selected move | Two-ply expected-value search, chance outcomes, adversarial response and HP-difference utility |
| Tetris | Candidate placement -> scalar Q value | Flattened board + 7 engineered features -> 512 / 256 ReLU hidden layers -> Q output |

### Stealth: distance versus exposure

[`StealthAgent.java`](src/pas/stealth/agents/StealthAgent.java) implements `aStarSearch`, `getEdgeWeight`, and `shouldReplacePlan`. Enemy-distance penalties favor safer paths; path reuse and cooldowns reduce repeated planning. The current code does **not** implement the adaptive lambda or `findSafePath` / `updateWeights` methods described in older documentation.

Trade-off: higher danger penalties may select longer paths, while cached paths must be reconsidered as the environment changes.

### Pokemon: bounded stochastic search

[`TreeTraversalAgent.java`](src/pas/pokemon/agents/TreeTraversalAgent.java) evaluates our moves, their chance outcomes, the opponent's minimizing response, and post-turn HP utility. It retains at most 8 outcomes per move and runs through a background executor with a time limit.

Trade-off: truncation limits branching but approximates the full probability distribution. The implemented search is two-ply; a large `maxDepth` value does not imply 1,000-ply search.

### Tetris: features and rewards

[`TetrisQAgent.java`](src/pas/tetris/agents/TetrisQAgent.java) adds stack height, holes, bottom-row completion, piece type, pivot position, rotation, and column balance to the board-image input. It defines the neural Q function, mini-batch training hook, reward shaping, and a decaying exploration probability bounded below at 0.03.

Trade-off: engineered features encode useful structure, but reward design introduces assumptions about desirable play. The course framework supplies the surrounding game/training lifecycle.

## Reproduce compilation

Verification used JDK 8 and the tracked teaching libraries:

```sh
git clone https://github.com/xiaotuzi7877/Game-AI-Agent.git
cd Game-AI-Agent
sh scripts/compile.sh
```

The script compiles all three core agents into `build/classes`; it passed on 2026-10-29. Compilation does not establish win rates or trained-model quality.

The bundled entry points include `edu.bu.pas.pokemon.Easy`, `.Medium`, `.Hard`, and `edu.bu.pas.tetris.Main`. Put `build/classes` before `lib/*` on the classpath. Stealth scenarios are in `data/pas/stealth/`; matching engine documentation is under `doc/`. Interactive runs may open a window and require the corresponding course setup.

## Evaluation boundaries

Older notes report win rates and timing, but there is no standardized seed-controlled benchmark reproducing every number. Those figures are not presented here as newly verified results. A reproducible evaluation should record engine/JDK versions, agent revision, seeds, trial count, score/win distributions, and decision-time distributions. Separate Tetris training from evaluation.

## Code map and attribution

- `src/pas/`: the three core personal agent implementations.
- `src/labs/`: additional exercises; `origin.java` is historical reference and is excluded from the core manifests.
- `lib/`, `data/`, `doc/`: supplied libraries, scenarios and documentation; preserve their attribution and usage terms.
- `*.srcs`: explicit compilation manifests.
- `learning_curve.py`: training-log parser and learning-curve plotter.
- `params.model`, `qFunction47.model`: historical model artifacts; their presence alone is not validation.
- [Interview walkthrough](docs/portfolio-walkthrough.md).

Generated class files, build directories and OS metadata are ignored. No live-user or production metrics are claimed.
