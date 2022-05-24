package se.kth.castor.pankti.instrument.plugins;

import org.glowroot.agent.plugin.api.*;
import org.glowroot.agent.plugin.api.weaving.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This aspect class counts the number of invocations
 * of a specific method
 */
public class CounterAspect {
    private static int INVOCATION_COUNT;
    private static boolean fileSizeWithinLimits = true;

    @Pointcut(className = "fully.qualified.path.to.class",
            methodName = "methodToInstrument",
            methodParameterTypes = {"param1", "param2"},
            timerName = "Timer - name")
    public static class TargetMethodAdvice implements AdviceTemplate {
        private static final TimerName timer = Agent.getTimerName(TargetMethodAdvice.class);
        private static final String transactionType = "Target";
        private static final int COUNT = 0;
        private static String invocationCountFilePath;
        private static String invokedMethodsCSVFilePath;
        private static final Logger logger = Logger.getLogger(TargetMethodAdvice.class);
        private static final String methodParamTypesString = String.join(",", TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodParameterTypes());
        private static final String postfix = methodParamTypesString.isEmpty() ? "" : "_" + methodParamTypesString;
        public static final String methodFQN = TargetMethodAdvice.class.getAnnotation(Pointcut.class).className() + "."
                + TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodName() + postfix;
        static UUID invocationUuid = null;
        private static final String invocationString = String.format("Invocation count for %s: ", methodFQN.replaceAll("\\[\\]", "%5b%5d"));

        private static void setup() {
            AdviceTemplate.setUpXStream();
            Map<Type, String> fileNameMap = AdviceTemplate.setUpFiles(methodFQN);
            invocationCountFilePath = fileNameMap.get(Type.INVOCATION_COUNT);
            invokedMethodsCSVFilePath = fileNameMap.get(Type.INVOKED_METHODS);
        }

        public static synchronized void writeInvocationCountToFile() {
            try {
                File invocationCountFile = new File(invocationCountFilePath);

                if (!invocationCountFile.exists()) {
                    FileWriter objectFileWriter = new FileWriter(invocationCountFilePath);
                    objectFileWriter.write(invocationString + INVOCATION_COUNT);
                    objectFileWriter.close();
                } else {
                    String content = Files.readString(Paths.get(invocationCountFilePath));
                    Pattern pattern = Pattern.compile(invocationString + "(\\d+$)");
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        int count = Integer.parseInt(matcher.group(1)) + 1;
                        content = matcher.replaceAll(invocationString + count);
                        Files.write(invocationCountFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static synchronized void appendRowToInvokedCSVFile() {
            try {
                File invokedMethodsCSVFile = new File(invokedMethodsCSVFilePath);
                Scanner scanner = new Scanner(invokedMethodsCSVFile);
                boolean found = false;
                while (scanner.hasNextLine()) {
                    String lineFromFile = scanner.nextLine();
                    if (lineFromFile.contains(methodFQN)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    FileWriter fr = new FileWriter(invokedMethodsCSVFile, true);
                    fr.write("\n");
                    fr.write(methodFQN);
                    fr.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @OnBefore
        public static TraceEntry onBefore(OptionalThreadContext context,
                                          @BindReceiver Object receivingObject,
                                          @BindParameterArray Object parameterObjects,
                                          @BindMethodName String methodName) {
            setup();
            if (fileSizeWithinLimits) {
                invocationUuid = UUID.randomUUID();
                Thread.currentThread().setName(methodFQN);

            }
            MessageSupplier messageSupplier = MessageSupplier.create(
                    "className: {}, methodName: {}",
                    TargetMethodAdvice.class.getAnnotation(Pointcut.class).className(),
                    methodName
            );
            return context.startTransaction(transactionType, methodName, messageSupplier, timer, OptionalThreadContext.AlreadyInTransactionBehavior.CAPTURE_NEW_TRANSACTION);
        }

        @OnReturn
        public static void onReturn(@BindReturn Object returnedObject,
                                    @BindTraveler TraceEntry traceEntry) {
            INVOCATION_COUNT++;
            if (INVOCATION_COUNT == 1) {
                appendRowToInvokedCSVFile();
            }
            writeInvocationCountToFile();
            invocationUuid = null;
            traceEntry.end();
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable throwable,
                                   @BindTraveler TraceEntry traceEntry) {
            traceEntry.endWithError(throwable);
        }
    }
}
