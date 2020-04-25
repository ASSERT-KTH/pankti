package animal;

public abstract class Animal {
  public String type;

  public Animal(String type) {
    this.type = type;
  }

  public void defineAnimal() {
    System.out.println("This is an animal of type " + this.type);
  }

  public abstract String speak();

  public abstract void sleep() {
    System.out.println("ZZZ");
  }
}
