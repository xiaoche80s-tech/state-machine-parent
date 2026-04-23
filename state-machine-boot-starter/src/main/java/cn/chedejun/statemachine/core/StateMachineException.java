package cn.chedejun.statemachine.core;

public class StateMachineException extends RuntimeException {
    public StateMachineException(String message) { super(message); }
    public StateMachineException(String message, Throwable cause) { super(message, cause); }

    public static class NoMatchingTransitionException extends StateMachineException {
        public NoMatchingTransitionException(String state) { super("No matching transition from state: " + state); }
    }

    public static class StateNotFoundException extends StateMachineException {
        public StateNotFoundException(String state) { super("State not found: " + state); }
    }

    public static class TerminalStateException extends StateMachineException {
        public TerminalStateException(String status) { super("Instance is in terminal status: " + status); }
    }
}
