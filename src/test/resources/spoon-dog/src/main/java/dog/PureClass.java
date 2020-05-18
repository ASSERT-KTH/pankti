package dog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public abstract class PureClass {

    public int field = 10;

    public int pureField() {
        return field;
    }

    public int pureConstant() {
        return 1;
    }
    public int pureMethod() {
        return pureConstant();
    }

    public void nonpureFileSystem() throws IOException {
        Files.write(Paths.get(""), "".getBytes());
    }

    public void nonpureFieldSet() throws IOException {
        field = 14;
    }
}
