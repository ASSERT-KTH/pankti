package se.kth.castor.pankti.generate.util;

import java.util.Arrays;
import java.util.List;

/**
 * This class has util methods to extract data
 * from the fully qualified name of an invocation
 */
public class MethodInvocationUtil {
    public static String getDeclaringTypeFromInvocationFQN(String invocationFQN) {
        return invocationFQN.replaceAll("(.+)(\\.\\w+\\(.*\\))", "$1");
    }

    public static String getDeclaringTypeSimpleNameFromFQN(String declaringTypeFQN) {
        return declaringTypeFQN.replaceAll("(.+\\.)(\\w+)$", "$2");
    }

    public static String getMethodWithParamsFromInvocationFQN(String invocationFQN) {
        return invocationFQN.replaceAll("(.+)(\\.\\w+\\(.*\\))", "$2")
                .replaceFirst("\\.", "");
    }

    public static String getMethodName(String methodAndParams) {
        return methodAndParams.replaceAll("(\\w+)(\\(.*\\))", "$1");
    }

    public static List<String> getMethodParams(String methodAndParams) {
        String params = methodAndParams.replaceAll("(\\w+\\()(.*)(\\))", "$2");
        return Arrays.asList(params.split(","));
    }
}
