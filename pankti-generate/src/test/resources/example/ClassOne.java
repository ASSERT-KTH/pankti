package se.kth.castor.pankti.generate.example;

import java.util.LinkedList;
import java.util.List;

public class ClassOne {
    ClassThree classThreeAsField;
    List<String> scandinavia = new LinkedList<>();

    public ClassOne() {
        classThreeAsField = new ClassThree();
    }

    // Method that takes a non-primitive param and has no nested invocations
    public String methodWithNoNestedInvocation(String aParam) {
        return String.format("Never gonna %s", aParam);
    }

    // Overloaded method
    public String methodWithNoNestedInvocation() {
        return String.format("Never gonna give, never gonna give!");
    }

    // Nested method call on object of external class which is a field
    public double methodWithNestedInvocationOnField() {
        return 6 + classThreeAsField.someOtherMethod();
    }

    // Nested method call on object of external class passed as param
    public float methodWithNestedInvocationOnParam(ClassTwo classTwo) {
        return 2.0F + classTwo.someMethod();
    }

    // Call to library method that takes a non-primitive param
    // (java.util.List.addAll(java.util.Collection))
    public void addToScandinavia(List<String> members) {
        scandinavia.addAll(members);
    }

    // Call to library method that takes no param
    // (java.util.List.size())
    public int getScandinaviaMemberCount() {
        return scandinavia.size();
    }
}
