package app;

import dog.Dog;
import dog.LabradorColours;
import horse.Horse;

public class App {
    public static void main(String[] args) {
        Dog dog = new Dog("canine", "Kaaju", "Labrador", 4, LabradorColours.YELLOW);
        System.out.println(dog.speak());
        dog.defineAnimal();
        System.out.println(dog);
        dog.incrementAge();
        System.out.println(dog.getName() + " is now " + dog.getAge());
        Horse horse = new Horse("Equidae", 3, "Chester");
        System.out.println(horse);
        System.out.println(horse.speak());
    }
}
