package se.kth.castor.pankti.generate.data;

public class NestedInvocation {
    String invocation;
    String invocationReturnType;
    String invocationTargetType;

    public NestedInvocation(String invocation, String invocationReturnType, String invocationTargetType) {
        this.invocation = invocation;
        this.invocationReturnType = invocationReturnType;
        this.invocationTargetType = invocationTargetType;
    }

    public String getInvocation() {
        return invocation;
    }

    public String getInvocationReturnType() {
        return invocationReturnType;
    }

    public String getInvocationTargetType() {
        return invocationTargetType;
    }

    @Override
    public String toString() {
        return "NestedInvocation{" +
                "invocation='" + invocation + '\'' +
                ", invocationReturnType='" + invocationReturnType + '\'' +
                ", invocationTargetType='" + invocationTargetType + '\'' +
                '}';
    }
}
