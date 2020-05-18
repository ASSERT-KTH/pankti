package dog;

public abstract class VisibilityClass {
    public void publicMethod() {
        System.out.println("It is public");
    }
    private void privateMethod() {
        System.out.println("It is private");
    }
    private static void privateStaticMethod() {
        System.out.println("It is private static");
    }
    public abstract void publicAbstractMethod();
}
