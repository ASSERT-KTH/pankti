package horse;

import animal.Animal;

public class Horse extends Animal {
    int age;
    String name;

    public Horse(String type, int age, String name) {
        super(type);
        this.age = age;
        this.name = name;
    }

    @Override
    public String speak() {
        return "Neigh!";
    }

    @Override
    public void sleep() {
      super.sleep();
      System.out("This horse is sleeping");
    }

    @Override
    public String toString() {
        return "My name is " + this.name +
                " and I am a " + this.age +
                "-year old horse.";
    }

}
