package cn.chedejun.statemachine.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "state-machine")
public class StateMachineProperties {
    private String ddlAuto = "update";
    private final Retry retry = new Retry();
    private final Management management = new Management();
    private final Console console = new Console();

    public String getDdlAuto() { return ddlAuto; }
    public void setDdlAuto(String ddlAuto) { this.ddlAuto = ddlAuto; }
    public Retry getRetry() { return retry; }
    public Management getManagement() { return management; }
    public Console getConsole() { return console; }

    public static class Retry {
        private int defaultMaxAttempts = 3;
        private long defaultInitialDelayMs = 1000;
        private long defaultMaxDelayMs = 30000;
        private double defaultBackoffFactor = 2.0;
        public int getDefaultMaxAttempts() { return defaultMaxAttempts; }
        public void setDefaultMaxAttempts(int v) { this.defaultMaxAttempts = v; }
        public long getDefaultInitialDelayMs() { return defaultInitialDelayMs; }
        public void setDefaultInitialDelayMs(long v) { this.defaultInitialDelayMs = v; }
        public long getDefaultMaxDelayMs() { return defaultMaxDelayMs; }
        public void setDefaultMaxDelayMs(long v) { this.defaultMaxDelayMs = v; }
        public double getDefaultBackoffFactor() { return defaultBackoffFactor; }
        public void setDefaultBackoffFactor(double v) { this.defaultBackoffFactor = v; }
    }
    public static class Management { private boolean enabled = true; public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { this.enabled = v; } }
    public static class Console { private boolean enabled = true; public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { this.enabled = v; } }
}
