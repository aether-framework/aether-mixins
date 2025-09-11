package e2e.api;

public class ServiceImpl implements e2e.api.IService {
    public String ping() {
        return "ORIG-IF";
    }
}