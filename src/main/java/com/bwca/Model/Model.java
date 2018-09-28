package Driver;

abstract public class Model
{
    public abstract void addLineCost(ISABlock block, ISALine inst);

    public abstract void addEdgeCost(BranchTarget edge);

    public abstract String getBlockSummary(ISABlock block);

    public abstract String getPositiveBlockCost(ISABlock block);

    public abstract String getNegativeEdgeCost(BranchTarget edge);

    public abstract String getInterceptCost();
}
