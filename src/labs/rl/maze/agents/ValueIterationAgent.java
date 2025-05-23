package src.labs.rl.maze.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS
import edu.bu.labs.rl.maze.agents.StochasticAgent;
import edu.bu.labs.rl.maze.agents.StochasticAgent.RewardFunction;
import edu.bu.labs.rl.maze.agents.StochasticAgent.TransitionModel;
import edu.bu.labs.rl.maze.utilities.Coordinate;
import edu.bu.labs.rl.maze.utilities.Pair;



public class ValueIterationAgent
    extends StochasticAgent
{

    public static final double GAMMA = 0.9; // feel free to change this around!
    public static final double EPSILON = 1e-6; // don't change this though

    private Map<Coordinate, Double> utilities;

	public ValueIterationAgent(int playerNum)
	{
		super(playerNum);
        this.utilities = null;
	}

    public Map<Coordinate, Double> getUtilities() { return this.utilities; }
    private void setUtilities(Map<Coordinate, Double> u) { this.utilities = u; }

    public boolean isTerminalState(Coordinate c)
    {
        return c.equals(StochasticAgent.POSITIVE_TERMINAL_STATE)
            || c.equals(StochasticAgent.NEGATIVE_TERMINAL_STATE);
    }

    public Map<Coordinate, Double> getZeroMap(StateView state)
    {
        Map<Coordinate, Double> m = new HashMap<Coordinate, Double>();
        for(int x = 0; x < state.getXExtent(); ++x)
        {
            for(int y = 0; y < state.getYExtent(); ++y)
            {
                if(!state.isResourceAt(x, y))
                {
                    // we can go here
                    m.put(new Coordinate(x, y), 0.0);
                }
            }
        }
        return m;
    }

    public void valueIteration(StateView state)
    {
        // Initialize the utility map 'U'.
        Map<Coordinate, Double> currentIterationUtils = getZeroMap(state);
        Map<Coordinate, Double> nextIterationUtils;

        // Calculate threshold using the original constant name GAMMA.
        double threshold = EPSILON * (1 - GAMMA) / GAMMA;
        double maxValChange;

 
        do
        {
            // Initialize map for next iteration's values 
            nextIterationUtils = new HashMap<>();
            // Reset max change tracker for this iteration
            maxValChange = 0.0;

            for (Coordinate gridPos : currentIterationUtils.keySet())
            {
                // Calculated utility for this state in the next iteration (local variable)
                double computedUtil;

                if (!isTerminalState(gridPos))
                {
                    // Apply Bellman update for non-terminal states.
                    double topActionValue = Double.NEGATIVE_INFINITY;

                    // Iterate through actions
                    for (Direction moveDir : TransitionModel.CARDINAL_DIRECTIONS)
                    {
                        // Expected utility for this action
                        double actionEV = 0.0;

                        // Sum over possible outcomes
                        // Use original method TransitionModel.getTransitionProbs
                        for (Pair<Coordinate, Double> outcomeProbPair :
                             TransitionModel.getTransitionProbs(state, gridPos, moveDir))
                        {
                            Coordinate nextGridPos = outcomeProbPair.getFirst();
                            double transitionProb = outcomeProbPair.getSecond();
                            // Use current iteration's local map for U(s')
                            actionEV += transitionProb * currentIterationUtils.get(nextGridPos);
                        }
                        // Update best Q-value using local variable
                        topActionValue = Math.max(topActionValue, actionEV);
                    }

                    // Calculate U'(s) using Bellman equation.
                    // Use original method RewardFunction.getReward and constant GAMMA.
                    computedUtil = RewardFunction.getReward(gridPos) + GAMMA * topActionValue;

                } else {
                    // Terminal state: U'(s) = R(s)
                    // Use original method RewardFunction.getReward
                    computedUtil = RewardFunction.getReward(gridPos);
                }

                // Store computed utility U'(s) 
                nextIterationUtils.put(gridPos, computedUtil);

                // Calculate change |U'(s) - U(s)| and update max delta (local variables)
                double diff = Math.abs(computedUtil - currentIterationUtils.get(gridPos));
                maxValChange = Math.max(maxValChange, diff);
            }

            // Update local map U for the next iteration: U <- U'
            currentIterationUtils = nextIterationUtils;

        // Continue loop while δ > threshold.
        } while (maxValChange > threshold);

        setUtilities(currentIterationUtils);
    }

    @Override
    public void computePolicy(StateView state,
                              HistoryView history)
    {
        this.valueIteration(state);

        // compute the policy from the utilities
        Map<Coordinate, Direction> policy = new HashMap<Coordinate, Direction>();

        for(Coordinate c : this.getUtilities().keySet())
        {
            // figure out what to do when in this state
            double maxActionUtility = Double.NEGATIVE_INFINITY;
            Direction bestDirection = null;

            for(Direction d : TransitionModel.CARDINAL_DIRECTIONS)
            {
                double thisActionUtility = 0.0;
                for(Pair<Coordinate, Double> transition : TransitionModel.getTransitionProbs(state, c, d))
                {
                    thisActionUtility += transition.getSecond() * this.getUtilities().get(transition.getFirst());
                }

                if(thisActionUtility > maxActionUtility)
                {
                    maxActionUtility = thisActionUtility;
                    bestDirection = d;
                }
            }

            policy.put(c, bestDirection);

        }

        this.setPolicy(policy);
    }

}
