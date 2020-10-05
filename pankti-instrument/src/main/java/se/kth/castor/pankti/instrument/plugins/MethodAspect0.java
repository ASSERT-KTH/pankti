package se.kth.castor.pankti.instrument.plugins;

import org.glowroot.agent.plugin.api.*;
import org.glowroot.agent.plugin.api.weaving.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodAspect0 {
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
        private static String receivingObjectFilePath;
        private static String paramObjectsFilePath;
        private static String returnedObjectFilePath;
        private static String invocationCountFilePath;
        private static String invokedMethodsCSVFilePath;
        private static Logger logger = Logger.getLogger(TargetMethodAdvice.class);
        private static String rowInCSVFile = "";
        private static final String methodParamTypesString = String.join(",", TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodParameterTypes());
        private static final String postfix = methodParamTypesString.isEmpty() ? "" : "_" + methodParamTypesString;
        private static final String methodFQN = TargetMethodAdvice.class.getAnnotation(Pointcut.class).className() + "."
                + TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodName() + postfix;
        private static final String invocationString = String.format("Invocation count for %s: ", methodFQN);

        private static void setup() {
            AdviceTemplate.setUpXStream();
            String[] fileNames = AdviceTemplate.setUpFiles(methodFQN);
            receivingObjectFilePath = fileNames[0];
            paramObjectsFilePath = fileNames[1];
            returnedObjectFilePath = fileNames[2];
            invocationCountFilePath = fileNames[3];
            invokedMethodsCSVFilePath = fileNames[4];
            checkFileSizeLimit();
        }

        // Limit object XML files to ~200 MB
        public static void checkFileSizeLimit() {
            File[] files = {new File(receivingObjectFilePath), new File(returnedObjectFilePath), new File(paramObjectsFilePath)};
            for (File file : files) {
                if (file.exists() && (file.length() / (1024 * 1024) >= 200)) {
                    fileSizeWithinLimits = false;
                    break;
                }
            }
        }

        public static synchronized void writeObjectXMLToFile(Object objectToWrite, String objectFilePath) {
            try {
                FileWriter objectFileWriter = new FileWriter(objectFilePath, true);
                String xml = xStream.toXML(objectToWrite);
                xml = xml.replaceAll("(&#x)(\\w+;)", "&amp;#x$2");
                BufferedReader reader = new BufferedReader(new StringReader(xml));
                BufferedWriter writer = new BufferedWriter(objectFileWriter);
                while ((xml = reader.readLine()) != null) {
                    writer.write(xml);
                    writer.newLine();
                }
                writer.flush();
                writer.close();
            } catch (Exception e) {
                logger.info("MethodAspect" + COUNT);
                e.printStackTrace();
            }
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
                    if (lineFromFile.contains(rowInCSVFile)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    FileWriter fr = new FileWriter(invokedMethodsCSVFile, true);
                    fr.write("\n");
                    fr.write(rowInCSVFile);
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
                writeObjectXMLToFile(receivingObject, receivingObjectFilePath);
                writeObjectXMLToFile(parameterObjects, paramObjectsFilePath);
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
            if (fileSizeWithinLimits) {
                writeObjectXMLToFile(returnedObject, returnedObjectFilePath);
                checkFileSizeLimit();
            }
            INVOCATION_COUNT++;
            if (INVOCATION_COUNT == 1) {
                appendRowToInvokedCSVFile();
            }
            writeInvocationCountToFile();
            traceEntry.end();
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable throwable,
                                   @BindTraveler TraceEntry traceEntry) {
            traceEntry.endWithError(throwable);
        }
    }
}
