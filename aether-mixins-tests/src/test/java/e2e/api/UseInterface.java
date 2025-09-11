package e2e.api;

public class UseInterface {
    public String callIface() {
        e2e.api.IService s = new ServiceImpl();
        return s.ping(); // INVOKEINTERFACE
    }
}