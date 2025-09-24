package e2e.instance;

public class CtorTarget {
    private boolean flag = false;

    public CtorTarget() {
        // original constructor body
    }

    public boolean flag() {
        return flag;
    }

    public void setFlag(final boolean value) {
        this.flag = value;
    }
}