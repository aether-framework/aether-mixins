package e2e.statics.api;

public class UseInterface {
    public String callIface() {
        e2e.statics.api.IService s = new ServiceImpl();
        return s.ping(); // INVOKEINTERFACE
    }
}