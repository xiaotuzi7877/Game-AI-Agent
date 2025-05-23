package src.pas.pokemon.agents;

// SYSTEM IMPORTS....
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.utils.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class TreeTraversalAgent extends Agent
{
    private class StochasticTreeSearcher
            extends Object
            implements Callable<Pair<MoveView, Long>>
    {
        // ----------------- 关键参数：截断分支数 -----------------
        // 如果 getPotentialEffects() 返回的分支数大于此上限，则截断只取前N个
        private static final int MAX_OUTCOMES_PER_MOVE = 8;

        private final BattleView rootView;
        private final int maxDepth;
        private final int myTeamIdx;

        public StochasticTreeSearcher(BattleView rootView, int maxDepth, int myTeamIdx)
        {
            this.rootView = rootView;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

        public BattleView getRootView() { return this.rootView; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }

        /**
         * 主体：被线程池调用
         */
        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            double startTime = System.nanoTime();
            MoveView move = this.stochasticTreeSearch(this.rootView);
            double endTime = System.nanoTime();
            long durationMs = (long)((endTime - startTime) / 1_000_000);

            return new Pair<>(move, durationMs);
        }

        /**
         * 核心入口：2-ply 搜索，选取期望收益最高的招式
         */
        public MoveView stochasticTreeSearch(BattleView rootView)
        {
            // 若对战已结束
            if (rootView.isOver()) {
                return null;
            }
            // 我方当前宝可梦
            TeamView myTeamView = (this.myTeamIdx == 0)
                    ? rootView.getTeam1View()
                    : rootView.getTeam2View();
            PokemonView myActive = myTeamView.getActivePokemonView();

            // 若我方当前宝可梦已晕厥或不存在，则无行动可选
            if (myActive == null || myActive.hasFainted()) {
                return null;
            }

            // 获取我方可用招式
            List<MoveView> myMoves = myActive.getAvailableMoves();
            if (myMoves == null || myMoves.isEmpty()) {
                return null;
            }

            double bestVal = Double.NEGATIVE_INFINITY;
            MoveView bestMove = null;

            // 遍历我方所有招式，计算期望收益
            for (MoveView myMv : myMoves) {
                double moveValue = computeMyMoveValue(rootView, myMv, 0);
                if (moveValue > bestVal) {
                    bestVal = moveValue;
                    bestMove = myMv;
                }
            }

            return bestMove;
        }

        /**
         * 计算“使用某招式”后的期望收益
         * @param state 当前状态
         * @param myMove 我方招式
         * @param depth 当前搜索深度
         */
        private double computeMyMoveValue(BattleView state, MoveView myMove, int depth)
        {
            if (depth >= maxDepth || state.isOver()) {
                return evaluateHPDiff(state);
            }

            // 我方招式可能带来的多种后续状态
            List<Pair<Double, BattleView>> myOutcomes = myMove.getPotentialEffects(
                    state, this.myTeamIdx, getOpponentIdx()
            );
            // 如果没潜在分支，就直接评估
            if (myOutcomes == null || myOutcomes.isEmpty()) {
                return evaluateHPDiff(state);
            }

            // 对分支结果做截断抽样
            myOutcomes = trimOutcomes(myOutcomes, MAX_OUTCOMES_PER_MOVE);

            // 然后让对手走
            double totalValue = 0.0;
            for (Pair<Double, BattleView> outcome : myOutcomes) {
                double prob = outcome.getFirst();
                BattleView afterMyMove = outcome.getSecond();
                // 对手行动 => 我方收益最小化
                double oppValue = computeOpponentMinValue(afterMyMove, depth + 1);
                totalValue += prob * oppValue;
            }
            return totalValue;
        }

        /**
         * 对手回合：对手会选让我们收益最小的招式
         */
        private double computeOpponentMinValue(BattleView state, int depth)
        {
            if (depth >= maxDepth || state.isOver()) {
                return evaluateHPDiff(state);
            }

            int oppIdx = getOpponentIdx();
            TeamView oppTeam = state.getTeamView(oppIdx);
            PokemonView oppActive = oppTeam.getActivePokemonView();

            // 对手无法行动就直接回合末
            if (oppActive == null || oppActive.hasFainted()) {
                return doEndOfTurnAndEvaluate(state);
            }
            List<MoveView> oppMoves = oppActive.getAvailableMoves();
            if (oppMoves == null || oppMoves.isEmpty()) {
                return doEndOfTurnAndEvaluate(state);
            }

            // 对手遍历所有招式，找能让我们收益最小的
            double minVal = Double.POSITIVE_INFINITY;
            for (MoveView oppMv : oppMoves) {
                List<Pair<Double, BattleView>> oppOutcomes = oppMv.getPotentialEffects(
                        state, oppIdx, this.myTeamIdx
                );
                if (oppOutcomes == null || oppOutcomes.isEmpty()) {
                    double val = doEndOfTurnAndEvaluate(state);
                    if (val < minVal) {
                        minVal = val;
                    }
                    continue;
                }

                // 截断
                oppOutcomes = trimOutcomes(oppOutcomes, MAX_OUTCOMES_PER_MOVE);

                // 对各分支做期望
                double tmpVal = 0.0;
                for (Pair<Double, BattleView> oc : oppOutcomes) {
                    double p2 = oc.getFirst();
                    BattleView st2 = oc.getSecond();
                    double postVal = doEndOfTurnAndEvaluate(st2);
                    tmpVal += p2 * postVal;
                }
                if (tmpVal < minVal) {
                    minVal = tmpVal;
                }
            }

            return minVal;
        }

        /**
         * 回合结束后调用 applyPostTurnConditions()，并计算期望收益
         */
        private double doEndOfTurnAndEvaluate(BattleView st)
        {
            List<BattleView> postList = st.applyPostTurnConditions();
            if (postList == null || postList.isEmpty()) {
                return evaluateHPDiff(st);
            }

            double eachProb = 1.0 / postList.size();
            double sum = 0.0;
            for (BattleView sub : postList) {
                sum += eachProb * evaluateHPDiff(sub);
            }
            return sum;
        }

        /**
         * 简单评估：如果对战结束 => ±999999，否则计算我方HP-对手HP
         */
        private double evaluateHPDiff(BattleView st)
        {
            if (st.isOver()) {
                // 检查我方是否全晕
                TeamView me = (this.myTeamIdx == 0) ? st.getTeam1View() : st.getTeam2View();
                boolean allDead = true;
                for (int i = 0; i < me.size(); i++) {
                    PokemonView p = me.getPokemonView(i);
                    if (!p.hasFainted()) {
                        allDead = false;
                        break;
                    }
                }
                return allDead ? -999999.0 : 999999.0;
            }

            // 未结束 => HP差
            TeamView myT = (this.myTeamIdx == 0) ? st.getTeam1View() : st.getTeam2View();
            TeamView opT = (this.myTeamIdx == 0) ? st.getTeam2View() : st.getTeam1View();

            double myHP = 0.0;
            double oppHP = 0.0;
            for (int i = 0; i < myT.size(); i++) {
                PokemonView pp = myT.getPokemonView(i);
                if (!pp.hasFainted()) {
                    myHP += pp.getCurrentStat(Stat.HP);
                }
            }
            for (int i = 0; i < opT.size(); i++) {
                PokemonView pp = opT.getPokemonView(i);
                if (!pp.hasFainted()) {
                    oppHP += pp.getCurrentStat(Stat.HP);
                }
            }
            return myHP - oppHP;
        }

        /**
         * 将 outcome 分支数限制在一个上限，并重新归一化概率。
         * @param outcomes 原始分支
         * @param limit    保留的最大分支数
         */
        private List<Pair<Double, BattleView>> trimOutcomes(
                List<Pair<Double, BattleView>> outcomes, int limit
        ) {
            if (outcomes == null || outcomes.size() <= limit) {
                return outcomes;
            }
            // 根据概率大小降序排序
            outcomes.sort((a, b) -> Double.compare(b.getFirst(), a.getFirst()));

            // 取前N个
            List<Pair<Double, BattleView>> top = outcomes.subList(0, limit);

            // 计算总概率
            double sumProb = 0.0;
            for (Pair<Double, BattleView> p : top) {
                sumProb += p.getFirst();
            }

            // 重新归一化
            List<Pair<Double, BattleView>> trimmed = new ArrayList<>();
            for (Pair<Double, BattleView> p : top) {
                double newP = p.getFirst() / sumProb;
                trimmed.add(new Pair<>(newP, p.getSecond()));
            }
            return trimmed;
        }

        private int getOpponentIdx() {
            return (this.myTeamIdx == 0) ? 1 : 0;
        }
    }

    // ========== 以下部分不要动即可 ==========

    private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

    public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000 * 2; // 6 min/move
        this.maxDepth = 1000; // set this however you want
    }

    public int getMaxDepth() {
        return this.maxDepth;
    }
    public long getMaxThinkingTimePerMoveInMS() {
        return this.maxThinkingTimePerMoveInMS;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // 简单地选第一个未晕厥的宝可梦
        TeamView tv = this.getMyTeamView(view);
        for(int i=0; i<tv.size(); i++){
            if (!tv.getPokemonView(i).hasFainted()) {
                return i;
            }
        }
        return null;
    }

    @Override
    public MoveView getMove(BattleView battleView)
    {
        // 重要：本方法内容不改
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();

        MoveView move = null;
        long durationInMs = 0;

        StochasticTreeSearcher searcherObject = new StochasticTreeSearcher(
                battleView,
                this.getMaxDepth(),
                this.getMyTeamIdx()
        );

        Future<Pair<MoveView, Long>> future = backgroundThreadManager.submit(searcherObject);

        try
        {
            Pair<MoveView, Long> moveAndDuration = future.get(
                    this.getMaxThinkingTimePerMoveInMS(),
                    TimeUnit.MILLISECONDS
            );

            move = moveAndDuration.getFirst();
            durationInMs = moveAndDuration.getSecond();
        }
        catch(TimeoutException e)
        {
            System.err.println("Timeout!");
            System.err.println("Team [" + (this.getMyTeamIdx()+1) + "] loses!");
            System.exit(-1);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        catch(ExecutionException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        finally
        {
            backgroundThreadManager.shutdownNow();
        }

        return move;
    }
}
