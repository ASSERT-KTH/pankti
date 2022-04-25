package se.kth.castor.pankti.extract.util;

import se.kth.castor.pankti.extract.logging.CustomLogger;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.reference.CtFieldReferenceImpl;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MethodUtil {
    private static final Logger LOGGER =
            CustomLogger.log(MethodUtil.class.getName());

    /**
     * Returns the type signature representation for method parameters.
     *
     * <p>See <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/types.html#wp16432">Oracle Java SE Documentation - Type Signatures</a>
     *
     * @param paramType A parameter passed to a method
     * @return The signature representation for the parameter
     */
    public static String findMethodParamSignature(String paramType) {
        StringBuilder paramSignature = new StringBuilder();
        if (paramType.contains("["))
            paramSignature.append('[');
        paramType = paramType.replace("[", "")
                .replace("]", "");
        // For generics
        if (paramType.length() == 1)
            paramType = Object.class.getCanonicalName();
        if (paramType.equals("boolean"))
            paramSignature.append('Z');
        else if (paramType.equals("byte"))
            paramSignature.append('B');
        else if (paramType.equals("char"))
            paramSignature.append('C');
        else if (paramType.equals("short"))
            paramSignature.append('S');
        else if (paramType.equals("int"))
            paramSignature.append('I');
        else if (paramType.equals("long"))
            paramSignature.append('J');
        else if (paramType.equals("float"))
            paramSignature.append('F');
        else if (paramType.equals("double"))
            paramSignature.append('D');
        else paramSignature.append('L').append(paramType.replace('.', '/')).append(';');
        return paramSignature.toString();
    }
}
