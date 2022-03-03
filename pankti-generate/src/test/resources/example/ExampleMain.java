package se.kth.castor.pankti.generate.example;

import java.util.List;

public class ExampleMain {

    public void sing(ClassOne classOne) {
        List<String> lyrics = List.of("give you up", "make you cry");
        for (String lyric : lyrics) {
            System.out.println(classOne.methodWithNoNestedInvocation(lyric));
        }
    }

    public void setUpScandinavia(ClassOne classOne) {
        List<String> members = List.of("Denmark", "Norway", "Sweden");
        classOne.addToScandinavia(members);

    }

    public static void main(String[] args) {
        ExampleMain example = new ExampleMain();
        ClassOne classOne = new ClassOne();
        ClassTwo classTwo = new ClassTwo();
        System.out.println("Called methodWithNestedInvocationOnField(): " +
                classOne.methodWithNestedInvocationOnField());
        System.out.println("Called methodWithNestedInvocationOnParam(): " +
                classOne.methodWithNestedInvocationOnParam(classTwo));
        System.out.println("Setting up Scandinavia...");
        example.setUpScandinavia(classOne);
        System.out.println("Scandinavia members: " + classOne.getScandinaviaMemberCount());
        System.out.println("Singing now...");
        example.sing(classOne);
    }
}
