package cn.chedejun.statemachine.core;

import java.util.HashMap;
import java.util.Map;

public class Context {
    private final Map<String, Object> data = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) data.get(key); }

    public Context put(String key, Object value) { data.put(key, value); return this; }

    public boolean containsKey(String key) { return data.containsKey(key); }

    public Map<String, Object> toMap() { return new HashMap<>(data); }

    public static Context fromMap(Map<String, Object> map) {
        Context ctx = new Context();
        if (map != null) ctx.data.putAll(map);
        return ctx;
    }
}
