package e2e.statics.api;

public class ServiceImpl implements e2e.statics.api.IService {
    public String ping() {
        return "ORIG-IF";
    }
}