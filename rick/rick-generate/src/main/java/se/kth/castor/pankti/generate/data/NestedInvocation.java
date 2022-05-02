package se.kth.castor.pankti.generate.data;

import java.util.Map;

public class NestedInvocation {
    String invocation;
    String invocationReturnType;
    String invocationTargetType;
    Map<String, String> invocationFieldsVisibilityMap;
    Integer invocationParamIndex;
    String invocationMode;

    public NestedInvocation(String invocation,
                            String invocationReturnType,
                            String invocationTargetType,
                            Map<String, String> invocationFieldsVisibilityMap,
                            Integer invocationParamIndex,
                            String invocationMode) {
        this.invocation = invocation;
        this.invocationReturnType = invocationReturnType;
        this.invocationTargetType = invocationTargetType;
        this.invocationFieldsVisibilityMap = invocationFieldsVisibilityMap;
        this.invocationParamIndex = invocationParamIndex;
        this.invocationMode = invocationMode;
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

    public String getTargetFieldName(Map.Entry<String, String> fieldVisibilityEntry) {
        return fieldVisibilityEntry.getKey();
    }

    public boolean isTargetFieldPrivate(Map.Entry<String, String> fieldVisibilityEntry) {
        return fieldVisibilityEntry.getValue().equals("private");
    }

    public Map<String, String> getInvocationFieldsVisibilityMap() {
        return invocationFieldsVisibilityMap;
    }

    public Integer getInvocationParamIndex() {
        return invocationParamIndex;
    }

    public String getInvocationMode() {
        return invocationMode;
    }

    @Override
    public String toString() {
        return "NestedInvocation{" +
                "invocationMode='" + invocationMode + '\'' +
                ", invocation='" + invocation + '\'' +
                ", invocationReturnType='" + invocationReturnType + '\'' +
                ", invocationTargetType='" + invocationTargetType + '\'' +
                ", invocationFieldsVisibilityMap=" + invocationFieldsVisibilityMap +
                ", invocationParamIndex=" + invocationParamIndex +
                '}';
    }
}
