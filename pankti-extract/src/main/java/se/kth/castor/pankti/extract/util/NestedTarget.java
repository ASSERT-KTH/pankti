package se.kth.castor.pankti.extract.util;

import java.util.Objects;

enum TargetType {
    FIELD,
    PARAMETER
}

public class NestedTarget {
    String nestedInvocationReturnType;
    TargetType nestedInvocationTargetType;
    String nestedInvocationFieldName;
    String nestedInvocationDeclaringType;
    String nestedInvocationMethod;
    String nestedInvocationParams;
    String nestedInvocationSignature;

    public NestedTarget(String nestedInvocationReturnType,
                        TargetType nestedInvocationTargetType,
                        String nestedInvocationFieldName,
                        String nestedInvocationDeclaringType,
                        String nestedInvocationMethod,
                        String nestedInvocationParams,
                        String nestedInvocationSignature) {
        this.nestedInvocationReturnType = nestedInvocationReturnType;
        this.nestedInvocationTargetType = nestedInvocationTargetType;
        this.nestedInvocationFieldName = nestedInvocationFieldName;
        this.nestedInvocationDeclaringType = nestedInvocationDeclaringType;
        this.nestedInvocationMethod = nestedInvocationMethod;
        this.nestedInvocationParams = nestedInvocationParams;
        this.nestedInvocationSignature = nestedInvocationSignature;
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
                "nestedInvocationReturnType='" + nestedInvocationReturnType + '\'' +
                ", nestedInvocationTargetType=" + nestedInvocationTargetType +
                ", nestedInvocationFieldName='" + nestedInvocationFieldName + '\'' +
                ", nestedInvocationDeclaringType='" + nestedInvocationDeclaringType + '\'' +
                ", nestedInvocationMethod='" + nestedInvocationMethod + '\'' +
                ", nestedInvocationParams='" + nestedInvocationParams + '\'' +
                ", nestedInvocationSignature='" + nestedInvocationSignature + '\'' +
                '}';
    }
}
