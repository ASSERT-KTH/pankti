package se.kth.castor.pankti.extract.selector;

import java.util.Objects;

enum TargetType {
    FIELD,
    PARAMETER
}

enum InvocationMode {
    LIBRARY,
    DOMAIN
}

public class NestedTarget {
    String nestedInvocationReturnType;
    TargetType nestedInvocationTargetType;
    String nestedInvocationFieldName;
    Integer nestedInvocationParameterIndex;
    String nestedInvocationDeclaringType;
    String nestedInvocationMethod;
    String nestedInvocationParams;
    String nestedInvocationSignature;
    InvocationMode mode;

    public NestedTarget(String nestedInvocationReturnType,
                        TargetType nestedInvocationTargetType,
                        String nestedInvocationFieldName,
                        Integer nestedInvocationParameterIndex,
                        String nestedInvocationDeclaringType,
                        String nestedInvocationMethod,
                        String nestedInvocationParams,
                        String nestedInvocationSignature) {
        this.nestedInvocationReturnType = nestedInvocationReturnType;
        this.nestedInvocationTargetType = nestedInvocationTargetType;
        this.nestedInvocationFieldName = nestedInvocationFieldName;
        this.nestedInvocationParameterIndex = nestedInvocationParameterIndex;
        this.nestedInvocationDeclaringType = nestedInvocationDeclaringType;
        this.nestedInvocationMethod = nestedInvocationMethod;
        this.nestedInvocationParams = nestedInvocationParams;
        this.nestedInvocationSignature = nestedInvocationSignature;
        this.mode = (nestedInvocationDeclaringType.contains("java")) ? InvocationMode.LIBRARY : InvocationMode.DOMAIN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NestedTarget that = (NestedTarget) o;
        return nestedInvocationReturnType.equals(that.nestedInvocationReturnType) && nestedInvocationTargetType == that.nestedInvocationTargetType && Objects.equals(nestedInvocationFieldName, that.nestedInvocationFieldName) && nestedInvocationDeclaringType.equals(that.nestedInvocationDeclaringType) && nestedInvocationSignature.equals(that.nestedInvocationSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nestedInvocationReturnType, nestedInvocationTargetType, nestedInvocationFieldName, nestedInvocationDeclaringType, nestedInvocationSignature);
    }

    @Override
    public String toString() {
        return "{" +
                "nestedInvocationMode='" + mode + '\'' +
                ", nestedInvocationReturnType='" + nestedInvocationReturnType + '\'' +
                ", nestedInvocationTargetType=" + nestedInvocationTargetType +
                (nestedInvocationTargetType.equals(TargetType.FIELD) ? ", nestedInvocationFieldName='" + nestedInvocationFieldName + '\'' : "") +
                (nestedInvocationTargetType.equals(TargetType.PARAMETER) ? ", nestedInvocationParameterIndex='" + nestedInvocationParameterIndex + '\'' : "") +
                ", nestedInvocationDeclaringType='" + nestedInvocationDeclaringType + '\'' +
                ", nestedInvocationMethod='" + nestedInvocationMethod + '\'' +
                ", nestedInvocationParams='" + nestedInvocationParams + '\'' +
                ", nestedInvocationSignature='" + nestedInvocationSignature + '\'' +
                '}';
    }

    public String getNestedInvocationReturnType() {
        return nestedInvocationReturnType;
    }

    public TargetType getNestedInvocationTargetType() {
        return nestedInvocationTargetType;
    }

    public String getNestedInvocationFieldName() {
        return nestedInvocationFieldName;
    }

    public String getNestedInvocationDeclaringType() {
        return nestedInvocationDeclaringType;
    }

    public String getNestedInvocationMethod() {
        return nestedInvocationMethod;
    }

    public String getNestedInvocationParams() {
        return nestedInvocationParams;
    }

    public String getNestedInvocationSignature() {
        return nestedInvocationSignature;
    }

    public InvocationMode getMode() {
        return mode;
    }
}
