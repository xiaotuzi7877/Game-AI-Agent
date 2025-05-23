package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;


import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.LinkedList;    // will need for bfs
import java.util.Set;           // will need for bfs


// JAVA PROJECT IMPORTS


public class BFSMazeAgent
    extends MazeAgent
{

    public BFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }

    @Override
    public Path search(Vertex src,
                       Vertex goal,
                       StateView state)
    {
        // record the visited vertex
        Set<Vertex> visited = new HashSet<>();

        // BFS begin 
        Queue<Path> queue = new LinkedList<>();
        queue.add(new Path(src));

        // Moving dirts: up/down/left/right, then diagonal
        int[][] directions = {
            {0, 1}, {0, -1}, {1, 0}, {-1, 0},  
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1} 
        };

        while (!queue.isEmpty()) {
            Path currentPath = queue.poll(); 
            Vertex currentTile = currentPath.getDestination();

            // if visted: skip
            if (visited.contains(currentTile)) {
                continue;
            }
            visited.add(currentTile); // label visited 

            // current coord
            int tileX = currentTile.getXCoordinate();
            int tileY = currentTile.getYCoordinate();

            // all possible moving directs
            for (int[] dir : directions) {
                int newX = tileX + dir[0];
                int newY = tileY + dir[1];
                Vertex newVertex = new Vertex(newX, newY);

                // target detect
                if (newX == goal.getXCoordinate() && newY == goal.getYCoordinate()) {
                    return new Path(goal, 1f, currentPath);
                }

                // border & barrier detect
                if (!state.inBounds(newX, newY) || state.isResourceAt(newX, newY) || state.isUnitAt(newX, newY)) {
                    continue;
                }

                // reset queue
                if (!visited.contains(newVertex)) {
                    queue.add(new Path(newVertex, 1f, currentPath));
                }
            }
        }

        // no path found
        return null;
    } 
}
