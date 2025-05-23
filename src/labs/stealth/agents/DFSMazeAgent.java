package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;


import java.util.HashSet;   // will need for dfs
import java.util.Stack;     // will need for dfs
import java.util.Set;       // will need for dfs


// JAVA PROJECT IMPORTS


public class DFSMazeAgent
    extends MazeAgent
{

    public DFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }

    @Override
    public Path search(Vertex src,
                       Vertex goal,
                       StateView state)
    {
        // Track visited vertices
        Set<Vertex> visited = new HashSet<>();

        Stack<Path> stack = new Stack<>();
        stack.push(new Path(src));

        // movement directions: up/down/left/right, then diagonals)
        int[][] directions = {
            {0, 1}, {0, -1}, {1, 0}, {-1, 0},  
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1} 
        };

        // DFS loop
        while (!stack.isEmpty()) {
            Path currentPath = stack.pop();
            Vertex currentVertex = currentPath.getDestination();

            // if visited, skip
            if (visited.contains(currentVertex)) {
                continue;
            }
            visited.add(currentVertex);

            if (currentVertex.equals(goal)) {
                return currentPath;
            }

            int tileX = currentVertex.getXCoordinate();
            int tileY = currentVertex.getYCoordinate();

            // neighbors in the defined order
            for (int[] dir : directions) {
                int newX = tileX + dir[0];
                int newY = tileY + dir[1];
                Vertex newVertex = new Vertex(newX, newY);

                // bounds or blocked
                if (!state.inBounds(newX, newY) || state.isResourceAt(newX, newY) || state.isUnitAt(newX, newY)) {
                    continue;
                }

                // unvisted vertex
                if (!visited.contains(newVertex)) {
                    stack.push(new Path(newVertex, 1f, currentPath));
                }
            }
        }

        // If no path is found
        return null;
    }

}
