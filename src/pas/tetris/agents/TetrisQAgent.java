package src.pas.tetris.agents;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import edu.bu.pas.tetris.agents.QAgent;
import edu.bu.pas.tetris.agents.TrainerAgent.GameCounter;
import edu.bu.pas.tetris.game.Board;
import edu.bu.pas.tetris.game.Game.GameView;
import edu.bu.pas.tetris.game.minos.Mino;
import edu.bu.pas.tetris.game.Block;
import edu.bu.pas.tetris.linalg.Matrix;
import edu.bu.pas.tetris.nn.Model;
import edu.bu.pas.tetris.nn.LossFunction;
import edu.bu.pas.tetris.nn.Optimizer;
import edu.bu.pas.tetris.nn.models.Sequential;
import edu.bu.pas.tetris.nn.layers.Dense;
import edu.bu.pas.tetris.nn.layers.ReLU;
import edu.bu.pas.tetris.training.data.Dataset;
import edu.bu.pas.tetris.utils.Pair;

public class TetrisQAgent extends QAgent {
    private Random random;
    private static final double INITIAL_EXPLORATION_PROB = 0.9;
    private static final double MIN_EXPLORATION_PROB = 0.03;
    private static final double EXPLORATION_DECAY = 0.95;

    public TetrisQAgent(String name) {
        super(name);
        this.random = new Random(12345);
    }

    public Random getRandom() { return this.random; }

    @Override
    public Model initQFunction() {
        int numFeatures = Board.NUM_ROWS * Board.NUM_COLS + 7;
        int hiddenDim1 = 512;
        int hiddenDim2 = 256;
        int outDim = 1;

        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(numFeatures, hiddenDim1));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(hiddenDim1, hiddenDim2));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(hiddenDim2, outDim));

        return qFunction;
    }

    @Override
    public Matrix getQFunctionInput(final GameView game, final Mino potentialAction) {
        try {
            Matrix grayscaleImage = game.getGrayscaleImage(potentialAction).flatten();
            int imageSize = Board.NUM_ROWS * Board.NUM_COLS;

            Board board = game.getBoard();
            double height = calculateMaxHeight(board);
            double holes = countHoles(board);
            double bottomRowCompletion = calculateBottomRowCompletion(board);
            double pieceType = encodePieceType(potentialAction.getType());
            double pivotX = potentialAction.getPivotBlockCoordinate().getXCoordinate() / (double)Board.NUM_COLS;
            double rotationState = encodeRotationState(potentialAction);
            double columnBalance = calculateColumnBalance(board);

            double[] features = new double[] {
                    height / Board.NUM_ROWS,
                    holes / 10.0,
                    bottomRowCompletion / 3.0,
                    pieceType / 7.0,
                    pivotX,
                    rotationState / 3.0,
                    columnBalance / Board.NUM_COLS
            };
        

            Matrix featureVector = Matrix.zeros(1, imageSize + features.length);
    

            for (int i = 0; i < imageSize; i++) {
                featureVector.set(0, i, grayscaleImage.get(0, i));
            }
            for (int i = 0; i < features.length; i++) {
                featureVector.set(0, imageSize + i, features[i]);
            }

            return featureVector;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
            return null;
        }
    }

    @Override
    public boolean shouldExplore(final GameView game, final GameCounter gameCounter) {
        double explorationProb = Math.max(MIN_EXPLORATION_PROB,
                INITIAL_EXPLORATION_PROB * Math.pow(EXPLORATION_DECAY, gameCounter.getCurrentCycleIdx()));
        return this.getRandom().nextDouble() <= explorationProb;
    }

    @Override
    public Mino getExplorationMove(final GameView game) {
        List<Mino> minos = game.getFinalMinoPositions();
        Mino bestMino = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        Board board = game.getBoard();

        for (Mino mino : minos) {
            double score = evaluateMino(board, mino);
            score += random.nextDouble() * 4.0;
            if (score > bestScore) {
                bestScore = score;
                bestMino = mino;
            }
        }

        if (bestMino == null) {
            int randIdx = this.getRandom().nextInt(minos.size());
            bestMino = minos.get(randIdx);
        }
        return bestMino;
    }

    @Override
    public void trainQFunction(Dataset dataset, LossFunction lossFunction, Optimizer optimizer, long numUpdates) {
        for (int epochIdx = 0; epochIdx < numUpdates; ++epochIdx) {
            dataset.shuffle();
            Iterator<Pair<Matrix, Matrix>> batchIterator = dataset.iterator();

            while (batchIterator.hasNext()) {
                Pair<Matrix, Matrix> batch = batchIterator.next();
                try {
                    Matrix YHat = this.getQFunction().forward(batch.getFirst());
                    optimizer.reset();
                    this.getQFunction().backwards(batch.getFirst(),
                            lossFunction.backwards(YHat, batch.getSecond()));
                    optimizer.step();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    @Override
    public double getReward(final GameView game) {
        double score = game.getScoreThisTurn();
        Board board = game.getBoard();
        double heightPenalty = -0.4 * calculateMaxHeight(board); 
        double holePenalty = -0.4 * countHoles(board); 
        double lineReward = score > 0 ? 500.0 * score : 0; 
        double gameOverPenalty = game.didAgentLose() ? -15.0 : 0; 
        double survivalReward = 2; 
        double bottomRowCompletionReward = calculateBottomRowCompletion(board) * 4.0; 
                double tspinReward = detectTSpin(board, game) ? 50.0 : 0.0; 

        double totalReward = lineReward + heightPenalty + holePenalty + gameOverPenalty + survivalReward + bottomRowCompletionReward + tspinReward;
        if (score > 0) {
            System.out.println("Cleared lines! Score: " + score);
        }
        if (tspinReward > 0) {
            System.out.println("T-Spin detected!");
        }
        return totalReward;
    }

    private double calculateMaxHeight(Board board) {
        int maxHeight = 0;
        for (int col = 0; col < Board.NUM_COLS; col++) {
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.isCoordinateOccupied(col, row)) {
                    maxHeight = Math.max(maxHeight, Board.NUM_ROWS - row);
                    break;
                }
            }
        }
        return maxHeight;
    }

    private double countHoles(Board board) {
        int holes = 0;
        for (int col = 0; col < Board.NUM_COLS; col++) {
            boolean foundBlock = false;
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.isCoordinateOccupied(col, row)) {
                    foundBlock = true;
                } else if (foundBlock && !board.isCoordinateOccupied(col, row)) {
                    holes++;
                }
            }
        }
        return holes;
    }

    private double calculateBottomRowCompletion(Board board) {
        double completion = 0;
        for (int row = Board.NUM_ROWS - 3; row < Board.NUM_ROWS; row++) {
            if (row >= 0) {
                int filled = 0;
                for (int col = 0; col < Board.NUM_COLS; col++) {
                    if (board.isCoordinateOccupied(col, row)) {
                        filled++;
                    }
                }
                if (filled >= 8) {
                    completion += filled / (double)Board.NUM_COLS;
                }
            }
        }
        return completion;
    }

    private double encodePieceType(Mino.MinoType type) {
        switch (type) {
            case I: return 0;
            case O: return 1;
            case T: return 2;
            case S: return 3;
            case Z: return 4;
            case J: return 5;
            case L: return 6;
            default: return 0;
        }
    }

    private double encodeRotationState(Mino mino) {
        Block[] blocks = mino.getBlocks();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Block block : blocks) {
            int x = block.getCoordinate().getXCoordinate();
            int y = block.getCoordinate().getYCoordinate();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        if (width > height) {
            return 0;
        }
        if (height > width) {
            return 1;
        }
        return 2;
    }

    private double calculateColumnBalance(Board board) {
        int[] heights = new int[Board.NUM_COLS];
        for (int col = 0; col < Board.NUM_COLS; col++) {
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.isCoordinateOccupied(col, row)) {
                    heights[col] = Board.NUM_ROWS - row;
                    break;
                }
            }
        }
        int minHeight = Board.NUM_ROWS;
        int maxHeight = 0;
        for (int height : heights) {
            minHeight = Math.min(minHeight, height);
            maxHeight = Math.max(maxHeight, height);
        }
        return maxHeight - minHeight;
    }

    private boolean detectTSpin(Board board, GameView game) {
        List<Mino.MinoType> nextMinos = game.getNextThreeMinoTypes();
        if (nextMinos.isEmpty() || nextMinos.get(0) != Mino.MinoType.T) {
            return false;
        }

        int row = Board.NUM_ROWS - 1;
        for (; row >= 0; row--) {
            boolean hasBlocks = false;
            for (int col = 0; col < Board.NUM_COLS; col++) {
                if (board.isCoordinateOccupied(col, row)) {
                    hasBlocks = true;
                    break;
                }
            }
            if (hasBlocks) {
                break;
            }
        }

        if (row < 0 || row >= Board.NUM_ROWS - 1) {
            return false;
        }

        for (int col = 0; col < Board.NUM_COLS; col++) {
            if (board.isCoordinateOccupied(col, row)) {
                int occupiedNeighbors = 0;
                if (col > 0 && board.isCoordinateOccupied(col - 1, row)) {
                    occupiedNeighbors++;
                }
                if (col < Board.NUM_COLS - 1 && board.isCoordinateOccupied(col + 1, row)) {
                    occupiedNeighbors++;
                }
                if (row > 0 && board.isCoordinateOccupied(col, row - 1)) {
                    occupiedNeighbors++;
                }
                if (row < Board.NUM_ROWS - 1 && board.isCoordinateOccupied(col, row + 1)) {
                    occupiedNeighbors++;
                }
                if (occupiedNeighbors >= 1) {
                    return true;
                }
            }
        }

        return false;
    }

    private double evaluateMino(Board board, Mino mino) {
        double score = 0;
        Block[] blocks = mino.getBlocks();


        int[] rowCounts = new int[Board.NUM_ROWS];
        for (Block block : blocks) {
            int y = block.getCoordinate().getYCoordinate();
            if (y >= 0 && y < Board.NUM_ROWS) {
                rowCounts[y]++;
            }
        }
        for (int row = Board.NUM_ROWS - 3; row < Board.NUM_ROWS; row++) {
            if (row >= 0 && rowCounts[row] > 0) {
                int existing = 0;
                for (int col = 0; col < Board.NUM_COLS; col++) {
                    if (board.isCoordinateOccupied(col, row)) {
                        existing++;
                    }
                }
                if (existing + rowCounts[row] >= 8) {
                    score += 50.0 * (existing + rowCounts[row]) / (double)Board.NUM_COLS; 
                }
            }
        }

        int filledHoles = 0;
        for (Block block : blocks) {
            int x = block.getCoordinate().getXCoordinate();
            int y = block.getCoordinate().getYCoordinate();
            if (y < Board.NUM_ROWS - 1 && !board.isCoordinateOccupied(x, y) && board.isCoordinateOccupied(x, y + 1)) {
                filledHoles++;
            }
            if (x > 0 && !board.isCoordinateOccupied(x, y) && board.isCoordinateOccupied(x - 1, y)) {
                filledHoles++;
            }
            if (x < Board.NUM_COLS - 1 && !board.isCoordinateOccupied(x, y) && board.isCoordinateOccupied(x + 1, y)) {
                filledHoles++;
            }
        }
        score += 20.0 * filledHoles;

        int neighbors = 0;
        for (Block block : blocks) {
            int x = block.getCoordinate().getXCoordinate();
            int y = block.getCoordinate().getYCoordinate();
            if (x > 0 && board.isCoordinateOccupied(x - 1, y)) {
                neighbors++;
            }
            if (x < Board.NUM_COLS - 1 && board.isCoordinateOccupied(x + 1, y)) {
                neighbors++;
            }
            if (y > 0 && board.isCoordinateOccupied(x, y - 1)) {
                neighbors++;
            }
            if (y < Board.NUM_ROWS - 1 && board.isCoordinateOccupied(x, y + 1)) {
                neighbors++;
            }
        }
        score += 5.0 * neighbors;

        double avgHeight = calculateMinoHeight(mino);
        score -= 3.0 * avgHeight;

       
        int[] colHeights = new int[Board.NUM_COLS];
        for (int col = 0; col < Board.NUM_COLS; col++) {
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.isCoordinateOccupied(col, row)) {
                    colHeights[col] = Board.NUM_ROWS - row;
                    break;
                }
            }
        }
        int minHeight = Board.NUM_ROWS;
        for (int height : colHeights) {
            minHeight = Math.min(minHeight, height);
        }
        for (Block block : blocks) {
            int x = block.getCoordinate().getXCoordinate();
            if (colHeights[x] <= minHeight) {
                score += 15.0;
            }
        }

        score -= 0.5 * calculateColumnBalance(board);


        if (mino.getType() == Mino.MinoType.T) {
            double rotationScore = encodeRotationState(mino);
            score += 15.0 * rotationScore;
        }

        score += random.nextDouble() * 4.0;

        return score;
    }

    private double calculateMinoHeight(Mino mino) {
        Block[] blocks = mino.getBlocks();
        double tHeight = 0;
        for (Block block : blocks) {
            tHeight += Board.NUM_ROWS - block.getCoordinate().getYCoordinate();
        }
        return tHeight / blocks.length;
    }
}