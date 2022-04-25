package se.kth.castor.pankti.generate.data;

import se.kth.castor.pankti.generate.util.MockGeneratorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    List<NestedInvocation> nestedInvocations = new ArrayList<>();

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
        if (hasMockableInvocations)
            setNestedInvocations();
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

    public void setNestedInvocations() {
        List<String> sanitizedInvocations = MockGeneratorUtil.sanitizeNestedInvocationMap(nestedMethodMap);
        List<String> nestedReturnTypes = MockGeneratorUtil.getReturnTypeFromInvocationMap(nestedMethodMap);
        List<String> invocationTargetTypes = MockGeneratorUtil.getNestedInvocationTargetTypesFromNestedMethodMap(nestedMethodMap);
        List<Map<String, String>> fieldVisibilityMaps = MockGeneratorUtil.getNestedInvocationTargetFieldVisibilityMap(invocationTargetTypes, nestedMethodMap);
        List<String> invocationMode = MockGeneratorUtil.getInvocationMode(nestedMethodMap);

        assert (sanitizedInvocations.size() == nestedReturnTypes.size() &
                nestedReturnTypes.size() == invocationTargetTypes.size() &
                invocationTargetTypes.size() == fieldVisibilityMaps.size());

        for (int i = 0; i < sanitizedInvocations.size(); i++) {
            nestedInvocations.add(new NestedInvocation(
                    sanitizedInvocations.get(i), nestedReturnTypes.get(i),
                    invocationTargetTypes.get(i),
                    !fieldVisibilityMaps.get(i).toString().equals("{=}") ? fieldVisibilityMaps.get(i): null,
                    invocationMode.get(i)));
        }
    }

    public List<NestedInvocation> getNestedInvocations() {
        return nestedInvocations;
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
                ", nestedInvocations='" + nestedInvocations + '\'' +
                '}';
    }
}
