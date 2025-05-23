package src.pas.stealth.agents;

// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;




import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;


// JAVA PROJECT IMPORTS
import edu.bu.pas.stealth.agents.AStarAgent;                // the base class of your class
import edu.bu.pas.stealth.agents.AStarAgent.AgentPhase;     // INFILTRATE/EXFILTRATE enums for your state machine
import edu.bu.pas.stealth.agents.AStarAgent.ExtraParams;    // base class for creating your own params objects
import edu.bu.pas.stealth.distance.DistanceMetric;
import edu.bu.pas.stealth.graph.Vertex;                     // Vertex = coordinate
import edu.bu.pas.stealth.graph.Path;                     // see the documentation...a Path is a linked list



public class StealthAgent
    extends AStarAgent
{
    // Enemy's maximum Chebyshev vision range (determined dynamically).
    private int enemyChebyshevSightLimit;

    // Tracks if we’ve destroyed the enemy base
    private boolean townhallDestroyed;
    
    // Our overall A* path plan (head of the reversed path)
    private Path currentPath;   

    // Used to "walk" along the path from goal back to source
    private Path pathCursor;    

    // INFILTRATE or EXFILTRATE
    private AgentPhase phase; 

    // Stack of waypoints for movement (reversed path from A*)
    private Stack<Vertex> pathStack = null; 
    
    /**
     * Initializes a new StealthAgent.
     *
     * @param playerNum The agent's player number in the game.
     */
    public StealthAgent(int playerNum)
    {
        super(playerNum);
        
        // We haven't destroyed the base
        this.townhallDestroyed = false;
        this.currentPath = null;
        // None path rn
        this.pathCursor = null;

        // begin in infiltration mode
        this.phase = AgentPhase.INFILTRATE; 
        this.enemyChebyshevSightLimit = -1; // invalid value....we won't know this until initialStep()
    }

    // @return Enemy's maximum vision range.
    public final int getEnemyChebyshevSightLimit() { return this.enemyChebyshevSightLimit; }
    
    // Sets the enemy's vision range
    public void setEnemyChebyshevSightLimit(int i) { this.enemyChebyshevSightLimit = i; }
    
    // @return True if the townhall has been destroyed, false otherwise.
    public boolean isTownhallDestroyed() { return this.townhallDestroyed; }

    // @return The current infiltration/exfiltration phase of the agent.
    public AgentPhase getPhase() { return this.phase; }
   


    ///////////////////////////////////////// Sepia methods to override ///////////////////////////////////

    /**
     * Runs once at the beginning of the game. Initializes enemy vision range.
     *
     * @param state   The initial state of the game.
     * @param history The game's history up to this point.
     * @return Null (no actions are taken in this step).
     */
    @Override
    public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
    {
        super.initialStep(state, history); // call AStarAgent's initialStep() to set helpful fields and stuff

        // now some fields are set for us b/c we called AStarAgent's initialStep()
        // let's calculate how far away enemy units can see us...this will be the same for all units (except the base)
        // which doesn't have a sight limit (nor does it care about seeing you)
        // iterate over the "other" (i.e. not the base) enemy units until we get a UnitView that is not null
        UnitView otherEnemyUnitView = null;
        Iterator<Integer> otherEnemyUnitIDsIt = this.getOtherEnemyUnitIDs().iterator();
        while(otherEnemyUnitIDsIt.hasNext() && otherEnemyUnitView == null)
        {
            otherEnemyUnitView = state.getUnit(otherEnemyUnitIDsIt.next());
        }

        if(otherEnemyUnitView == null)
        {
            System.err.println("[ERROR] StealthAgent.initialStep: could not find a non-null 'other' enemy UnitView??");
            System.exit(-1); // // Exit if no enemies are detected
        }

        // lookup an attribute from the unit's "template" (which you can find in the map .xml files)
        // When I specify the unit's (i.e. "footman"'s) xml template, I will use the "range" attribute
        // as the enemy sight limit
        this.setEnemyChebyshevSightLimit(otherEnemyUnitView.getTemplateView().getRange());

        return null;
    }

    /**
     * Called every turn. Determines movement, replanning, and attacking behavior.
     *
     * @param state   The current state of the game.
     * @param history The game's history up to this point.
     * @return A map of unit actions for this turn.
     */
    @Override
    public Map<Integer, Action> middleStep(StateView state, HistoryView history) {
    Map<Integer, Action> actions = new HashMap<>();
    
    // Get footman
    UnitView myUnit = state.getUnit(this.getMyUnitID());
    if (myUnit == null) {
        return actions;
    }
    int x = myUnit.getXPosition();
    int y = myUnit.getYPosition();

    // Check enemy townhall status
    UnitView townhall = state.getUnit(this.getEnemyBaseUnitID());
    if (townhall == null) {
        if (!this.townhallDestroyed) {
            this.townhallDestroyed = true;
            this.phase = AgentPhase.EXFILTRATE;
            this.currentPath = null;
            this.pathStack = null;
        }
    }

    // Detect if enemies are too close (danger radius of 6 tiles)
    boolean dangerDetected = false;
    for (Integer enemyID : this.getOtherEnemyUnitIDs()) {
        UnitView enemy = state.getUnit(enemyID);
        if (enemy == null) continue;
        int ex = enemy.getXPosition();
        int ey = enemy.getYPosition();
        // 检测范围设为3格（可根据情况调整）
        if (Math.abs(ex - x) <= 6 && Math.abs(ey - y) <= 6) {
            dangerDetected = true;
            break;
        }
    }
    // If danger is detected, clear the current plan to replan in the next turn
    if (dangerDetected) {
        // 当敌人靠近时，清空当前规划，强制下回合重新规划
        this.currentPath = null;
        this.pathStack = null;
    }

    
    // Replan if needed or if pathStack is empty
    if (this.shouldReplacePlan(state, null) || this.pathStack == null || this.pathStack.isEmpty()) {
        Vertex src = new Vertex(x, y);
        Vertex dst;
        if (this.phase == AgentPhase.INFILTRATE && townhall != null) {
            dst = new Vertex(townhall.getXPosition(), townhall.getYPosition());
        } else {
            dst = this.getStartingVertex();
        }
        this.currentPath = this.aStarSearch(src, dst, state, null);
        if (this.currentPath == null) {
            // No path found, skip turn.
            return actions;
        }
        // Convert the reversed linked list from A* into an ArrayList.
        ArrayList<Vertex> vertexList = new ArrayList<>();
        Path temp = this.currentPath;
        while (temp != null) {
            vertexList.add(temp.getDestination());
            temp = temp.getParentPath();
        }
        // The list is now [goal, ..., start]. Reverse it to get [start, ..., goal].
        Collections.reverse(vertexList);
        
        // Build a stack so that the top is the first tile (i.e., current position).
        // To do this, iterate the list from end to beginning so that the first element is pushed last.
        Stack<Vertex> orderedStack = new Stack<>();
        for (int i = vertexList.size() - 1; i >= 0; i--) {
            orderedStack.push(vertexList.get(i));
        }
        this.pathStack = orderedStack;
    }
    
    // If in INFILTRATE and adjacent to townhall, attack.
    if (this.phase == AgentPhase.INFILTRATE && townhall != null) {
        int tx = townhall.getXPosition();
        int ty = townhall.getYPosition();
        if (Math.abs(x - tx) <= 1 && Math.abs(y - ty) <= 1) {
            actions.put(myUnit.getID(), Action.createCompoundAttack(myUnit.getID(), townhall.getID()));
            return actions;
        }
    }
    
    // Follow the path step-by-step using the stack.
    if (this.pathStack != null && !this.pathStack.isEmpty()) {
        // Pop off any tiles that equal the current position.
        while (!this.pathStack.isEmpty() &&
               x == this.pathStack.peek().getXCoordinate() &&
               y == this.pathStack.peek().getYCoordinate()) {
            this.pathStack.pop();
        }
        // If still non-empty, the top of the stack is our next move.
        if (!this.pathStack.isEmpty()) {
            Vertex nextTile = this.pathStack.peek();
            // Check that the next tile is adjacent. If not, log an error and force replan.
            if (Math.abs(nextTile.getXCoordinate() - x) > 1 ||
                Math.abs(nextTile.getYCoordinate() - y) > 1) {
                System.err.println("ERROR: cannot go from src=Vertex(x=" + x + ", y=" + y + 
                                   ") to dst=" + nextTile + " in one move.");
                this.pathStack = null;
                return actions;
            }
            // Get move direction.
            Direction d = this.getDirectionToMoveTo(new Vertex(x, y), nextTile);
            if (d != null) {
                actions.put(myUnit.getID(), Action.createPrimitiveMove(myUnit.getID(), d));
            } else {
                // If no direction found, force a replan.
                this.pathStack = null;
            }
        }
    }
    
    return actions;
}


    ////////////////////////////////// End of Sepia methods to override //////////////////////////////////

    /////////////////////////////////// AStarAgent methods to override ///////////////////////////////////
    /**
     * Finds all valid neighboring tiles that the agent can move to.
     * 
     * @param v            The current position of the agent.
     * @param state        The current game state.
     * @param extraParams  Additional parameters (not used here).
     * @return A collection of valid neighboring vertices.
     */
    @Override
    public Collection<Vertex> getNeighbors(Vertex v, StateView state, ExtraParams extraParams) {

        //store valid neighbour HERE
        List<Vertex> neighbors = new ArrayList<>();
        int x = v.getXCoordinate();
        int y = v.getYCoordinate();
        int maxX = state.getXExtent();
        int maxY = state.getYExtent();
    
        // Consider all 8 directions (including diagonals)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue; // Skip (dx=0, dy=0) => that's the tile we are on
                }
                int nx = x + dx; // neighbor's x 
                int ny = y + dy; //neighbor's y
    
                // Check map bounds
                if (nx < 0 || ny < 0 || nx >= maxX || ny >= maxY) {
                    continue;
                }
    
                // Check if there's a blocking resource 
                boolean blocked = false; // boolean check block
                for (ResourceNode.ResourceView A : state.getAllResourceNodes()) {

                    // If the resource is at (nx, ny) and is a tree or mine, skip it
                    if (A.getXPosition() == nx && A.getYPosition() == ny) {
                        if (A.getType() == ResourceNode.Type.TREE 
                            || A.getType() == ResourceNode.Type.GOLD_MINE) {
                            blocked = true;
                            break;
                        }
                    }
                }
                // If blocked, skip this neighbor
                if (blocked) {
                    continue; 
                }
    
                // Collect any units at (nx, ny)! 
                List<Integer> unitsAtTile = new ArrayList<>();
                for (Integer candidateID : state.getAllUnitIds()) {
                    UnitView uv = state.getUnit(candidateID);
                    if (uv != null 
                        && uv.getXPosition() == nx 
                        && uv.getYPosition() == ny) {
                        unitsAtTile.add(candidateID);
                    }
                }
    
                // If there's occupant(s), see if any occupant is NOT the enemy base
                if (!unitsAtTile.isEmpty()) {
                    for (Integer uID : unitsAtTile) {
                        if (uID == this.getEnemyBaseUnitID()) {

                            // Base is allowed. 
                            continue;
                        }

                        // Some other occupant => blocked
                        blocked = true;
                        break;
                    }
                }
                if (blocked) {
                    continue;
                }
    
                // Otherwise, tile is valid. and add (nx, ny) as a valid neighbor
                neighbors.add(new Vertex(nx, ny));
            }
        }
    
        return neighbors;
    }

    /**
     * Implements A* search algorithm to find an optimal path from source to destination.
     *
     * @param src          The starting position of the agent.
     * @param dst          The goal position.
     * @param state        The current game state.
     * @param extraParams  Additional parameters (not used here).
     * @return A linked list (Path) of vertices representing the optimal path.
     */   
    @Override
    public Path aStarSearch(Vertex src,
                            Vertex dst,
                            StateView state,
                            ExtraParams extraParams)
    {
    // THe PriorityQueue of Path objects, sorted by (true cost + heuristic)
    PriorityQueue<Path> openSet = new PriorityQueue<>(Comparator.comparingDouble(
            p -> p.getTrueCost() + p.getEstimatedPathCostToGoal()
        ));
    
        // Map from Vertex => "cost so far"
        Map<Vertex, Float> bestCost = new HashMap<>();

        // Initialize a path for our src node
        Path startPath = new Path(src); // tail of the path
        
        float h = this.getHeuristicValue(src, dst, state);

        // Store the heuristic
        startPath.setEstimatedPathCostToGoal(h);

        // Enqueue 
        openSet.add(startPath);

        // cost-so-far is 0 at src
        bestCost.put(src, 0f);

        // Our Standard A* loop start here
        while(!openSet.isEmpty()) {

            // Pop the path with the smallest (g + h)
            Path current = openSet.poll();
            Vertex curV = current.getDestination();

            // If we've reached dst, we can return the path
            if(curV.equals(dst)) {
                return current;
            }
            // current path cost so far
            float g = current.getTrueCost();

            // For each neighbor, see if we can improve cost
            for(Vertex nbr : this.getNeighbors(curV, state, extraParams)) {
                float edgeW = this.getEdgeWeight(curV, nbr, state, extraParams);
                float newCost = g + edgeW;

                // If better route found
                if(newCost < bestCost.getOrDefault(nbr, Float.MAX_VALUE)) {
                    Path newPath = new Path(nbr, edgeW, current);

                // Estimate h with Euclidean
                    float hVal = this.getHeuristicValue(nbr, dst, state);
                    newPath.setEstimatedPathCostToGoal(hVal);

                    bestCost.put(nbr, newCost);
                    openSet.add(newPath);
                }
            }
        }

        // no path
        return null;
    }

    /**
     * Computes movement cost between two tiles, considering enemy proximity.
     *
     * @param src          The source tile.
     * @param dst          The destination tile.
     * @param state        The current game state.
     * @param extraParams  Additional parameters (not used here).
     * @return The movement cost.
     */
    @Override
    public float getEdgeWeight(Vertex src,
                               Vertex dst,
                               StateView state,
                               ExtraParams extraParams)
    {   
        // Base step cost of 1
    float weight = 1.0f;

    // For each enemy, we see how close 'dst' is and adjust cost accordingly.
    for (Integer enemyID : this.getOtherEnemyUnitIDs()) {
        UnitView uv = state.getUnit(enemyID);
        if (uv == null) {
            continue; // That enemy is dead
        }
        int ex = uv.getXPosition();
        int ey = uv.getYPosition();

        float dist = DistanceMetric.chebyshevDistance(dst, new Vertex(ex, ey));

        // If within 1 tile, add a big penalty
        if (dist <= 1.0f) {
            weight += 300f;  

        // Enough to strongly discourage stepping adjacent
        } else if (dist <= 3.0f) {
            weight += 70f; 

         // Some penalty if within 3 tiles
        } else if (dist <= 5.0f) {
            weight += 20.0f;   // Slight penalty if somewhat close
        }

        // also check if 'dst' is literally within their attack range
        int attacker = uv.getTemplateView().getRange();
        if (dist <= (attacker + 1)) {
            weight += 30.0f;  // Another mild penalty
        }
    }

    return weight;
}

    // I create this int to check how many turns we must wait before replanning again
    private int replanCooldown = 0;

    /** 
     * Determines if the agent should replan its path.
     *
     * @param state        The current game state.
     * @param extraParams  Additional parameters (not used here).
     * @return True if a new plan is needed, false otherwise.
     */
    public boolean shouldReplacePlan(StateView state,
                                     ExtraParams extraParams)
    { 
        // If we still have a cooldown, skip replanning
    if (this.replanCooldown > 0) {
        this.replanCooldown--;
        return false;
    }

    // If no path or used it up, we need a new plan
    if (this.currentPath == null || this.pathCursor == null) {
        // Reset cooldown to 5 turns (or 10, your choice)
        this.replanCooldown = 5;
        return true;
    }

    // Otherwise, we do the 10-step threat check
    int numVerticesToCheck = 10; 
    int count = 0;
    Path iter = this.pathCursor;
    while (iter != null && count < numVerticesToCheck) {
        Vertex v = iter.getDestination();
        int vx = v.getXCoordinate();
        int vy = v.getYCoordinate();

        // Check if any enemy threatens the planned path
        for (Integer enemyID : this.getOtherEnemyUnitIDs()) {
            UnitView uv = state.getUnit(enemyID);
            if (uv == null) {
                continue; 
            }
            int ex = uv.getXPosition();
            int ey = uv.getYPosition();
            int range = uv.getTemplateView().getRange();

            if (Math.abs(vx - ex) <= (range + 1) && Math.abs(vy - ey) <= (range + 1)) {
                // If we see a threatened tile
                this.replanCooldown = 5;  // We'll replan, then wait 5 turns
                return true;
            }
        }

        count++;
        iter = iter.getParentPath();
    }
    return false;
    }

    //////////////////////////////// End of AStarAgent methods to override ///////////////////////////////

}


