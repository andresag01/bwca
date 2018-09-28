package Driver;

public class WCETEdgeCost
{
    private int falseBranch;

    public WCETEdgeCost()
    {
        this.falseBranch = 0;
    }

    public int getNegativeCost()
    {
        return falseBranch;
    }

    public void subFalseBranch(int cost)
    {
        falseBranch += cost;
    }
}
