package se.kth.castor.pankti.extract.util;

public class MethodUtil {
    // Finds JVM method signature representation for a parameter
    public static String findMethodParamSignature(String paramType) {
        StringBuilder paramSignature = new StringBuilder();
        if (paramType.contains("["))
            paramSignature.append('[');
        if (paramType.contains("boolean"))
            paramSignature.append('Z');
        else if (paramType.contains("byte"))
            paramSignature.append('B');
        else if (paramType.contains("char"))
            paramSignature.append('C');
        else if (paramType.contains("short"))
            paramSignature.append('S');
        else if (paramType.contains("int"))
            paramSignature.append('I');
        else if (paramType.contains("long"))
            paramSignature.append('J');
        else if (paramType.contains("float"))
            paramSignature.append('F');
        else if (paramType.contains("double"))
            paramSignature.append('D');
        else paramSignature.append('L').append(paramType.replace('.', '/')).append(';');
        return paramSignature.toString();
    }
}
