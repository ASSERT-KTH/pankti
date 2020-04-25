package dog;

import animal.Animal;

public class Dog extends Animal {
  String name;
  String breed;
  int age;
  LabradorColours colour;

  public Dog(String type, String name, String breed, int age, LabradorColours colour) {
    super(type);
    this.name = name;
    this.breed = breed;
    this.age = age;
    this.colour = colour;
  }

  // a pure but uninteresting getter method
  public int getAge() {
    return this.age;
  }

  // another pure but uninteresting getter method
  public String getName() {
    return this.name;
  }

  // a setter (impure) method
  public void setName(String name) {
    this.name = name;
  }

  @Override
  // a public pure method, defines how a dog "speaks"
  public String speak() {
    try {
      if (this.name.equals("Kaaju"))
         throw new ArithmeticException();
      }
    catch (Exception e) {
      // empty catch block
    }
    return woof();
  }

  @Override
  public void sleep() {
    super.sleep();
    System.out.println("This pupper is sleeping");
  }

  // a private pure method
  private String woof() {
    return "Woof!";
  }

  public void anEmptyMethod() {
  }

  // an impure method
  public void incrementAge() {
    this.age += 1;
  }

  @Override
  public String toString() {
    return "My name is " + this.name +
           " and I am a " + this.age + 
           "-year old " + this.colour + 
           " " + this.breed + ".";
  }

}

