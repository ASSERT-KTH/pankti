package se.kth.castor.pankti.generate.data;

import java.util.List;

public class InstrumentedMethod {
    String parentFQN;
    String parentSimpleName;
    String methodName;
    List<String> paramList;
    String returnType;
    String visibility;
    boolean hasMockableInvocations;
    String nestedMethodMap;
    boolean isOverloaded;

    public InstrumentedMethod(
            String parentFQN,
            String methodName,
            List<String> paramList,
            String returnType,
            String visibility,
            boolean hasMockableInvocations,
            String nestedMethodMap) {
        this.parentFQN = parentFQN;
        this.parentSimpleName = setParentSimpleName();
        this.methodName = methodName;
        this.paramList = paramList;
        this.returnType = returnType.replaceAll("\\$", ".");
        this.visibility = visibility;
        this.hasMockableInvocations = hasMockableInvocations;
        this.nestedMethodMap = nestedMethodMap;
    }

    public String getParentFQN() {
        return parentFQN;
    }

    public String setParentSimpleName() {
        List<String> components = List.of(parentFQN.split("\\."));
        return components.get(components.size() - 1);
    }

    public String getParentSimpleName() {
        return parentSimpleName;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<String> getParamList() {
        return paramList;
    }

    public String getReturnType() {
        return returnType;
    }

    public boolean hasParams() {
        return this.getParamList().size() > 0;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getFullMethodPath() {
        return this.parentFQN + "." + this.getMethodName();
    }

    public boolean hasMockableInvocations() {
        return this.hasMockableInvocations;
    }

    public String getNestedMethodMap() {
        return this.nestedMethodMap;
    }

    public boolean isOverloaded() {
        return isOverloaded;
    }

    public void setOverloaded(boolean overloaded) {
        isOverloaded = overloaded;
    }


    @Override
    public String toString() {
        return "InstrumentedMethod{" +
                "parentFQN='" + parentFQN + '\'' +
                ", parentSimpleName='" + parentSimpleName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", paramList=" + paramList +
                ", returnType='" + returnType + '\'' +
                ", visibility='" + visibility + '\'' +
                ", hasMockableInvocations='" + hasMockableInvocations + '\'' +
                ", nestedMethodMap='" + nestedMethodMap + '\'' +
                '}';
    }
}