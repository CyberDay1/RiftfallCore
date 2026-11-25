package com.cyberday1.riftfall.boss;

/** One attempt's shared re-entry budget: the same for any party size, never refilled, and the god does not heal (D3). */
final class UnseatedRetries {
    static final int REENTRIES=2, WAIT=200;
    static final float RETURN_HEALTH=.5f;
    private int left;
    UnseatedRetries(){this(REENTRIES);}
    UnseatedRetries(int charges){left=Math.max(0,charges);}
    /** Takes one charge for a knocked-out player; false once the budget is spent, and the knockout sends them home. */
    boolean spend(){if(left<=0)return false;left--;return true;}
    int left(){return left;}
}
