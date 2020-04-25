package dog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DogTest {
    static Dog dog;

    @BeforeEach
    void setupDog() {
        dog = new Dog("canine", "Kaaju", "Labrador", 4, LabradorColours.YELLOW);
    }

    @Test
    void incrementedAgeTest() {
        dog.incrementAge();
        assertEquals(5, dog.getAge(), "Dog's age should be incremented by 1");
    }
}
