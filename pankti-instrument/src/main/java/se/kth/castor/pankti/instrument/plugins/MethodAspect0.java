package se.kth.castor.pankti.instrument.plugins;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.glowroot.agent.plugin.api.*;
import org.glowroot.agent.plugin.api.weaving.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
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
        private static long profileSizePre;
        private static String receivingObjectFilePath;
        private static String receivingObjectPostFilePath;
        private static String paramObjectsFilePath;
        private static String returnedObjectFilePath;
        private static String invocationCountFilePath;
        private static String invokedMethodsCSVFilePath;
        private static String objectProfileSizeFilePath;
        private static Logger logger = Logger.getLogger(TargetMethodAdvice.class);
        private static String rowInCSVFile = "";
        private static final boolean isReturnTypeVoid = false;
        private static final boolean hasMockableInvocations = false;
        private static final String methodParamTypesString = String.join(",", TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodParameterTypes());
        private static final String postfix = methodParamTypesString.isEmpty() ? "" : "_" + methodParamTypesString;
        private static final String methodFQN = TargetMethodAdvice.class.getAnnotation(Pointcut.class).className() + "."
                + TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodName() + postfix;
        static UUID invocationUuid = null;
        private static final String invocationString = String.format("Invocation count for %s: ", methodFQN);
        private static File[] allObjectFiles;

        private static void setup() {
            AdviceTemplate.setUpXStream();
            Map<Type, String> fileNameMap = AdviceTemplate.setUpFiles(methodFQN);
            receivingObjectFilePath = fileNameMap.get(Type.RECEIVING_PRE);
            receivingObjectPostFilePath = fileNameMap.get(Type.RECEIVING_POST);
            paramObjectsFilePath = fileNameMap.get(Type.PARAMS);
            returnedObjectFilePath = fileNameMap.get(Type.RETURNED);
            invocationCountFilePath = fileNameMap.get(Type.INVOCATION_COUNT);
            invokedMethodsCSVFilePath = fileNameMap.get(Type.INVOKED_METHODS);
            objectProfileSizeFilePath = fileNameMap.get(Type.OBJECT_PROFILE_SIZE);
            allObjectFiles = new File[]{
                    new File(receivingObjectFilePath),
                    new File(receivingObjectPostFilePath),
                    new File(returnedObjectFilePath),
                    new File(paramObjectsFilePath)};
            checkFileSizeLimit();
        }

        public static long getObjectProfileSize() {
            long objectProfileSize = 0L;
            for (File file : allObjectFiles) {
                objectProfileSize += file.length();
            }
            return objectProfileSize;
        }

        // Limit object XML files to ~200 MB
        public static void checkFileSizeLimit() {
            for (File file : allObjectFiles) {
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
                // Add attributes if method has mockable invocations
                if (hasMockableInvocations) {
                    xml = xml.replaceFirst("(\\/*)>", " uuid=\"" + invocationUuid + "\"$1>");
                }
                BufferedReader reader = new BufferedReader(new StringReader(xml));
                BufferedWriter writer = new BufferedWriter(objectFileWriter);
                while ((xml = reader.readLine()) != null) {
                    writer.write(xml);
                    writer.newLine();
                }
                writer.flush();
                writer.close();
            } catch (Exception e) {
                logger.info("Exception when writing XML for MethodAspect" + COUNT + " to file " + objectFilePath);
                e.printStackTrace();
                if (e.getMessage().startsWith("No converter specified") || e.getMessage().startsWith("No converter available")) {
                    String className = extractClassNameFromTheExceptionMessage(e.getMessage());
                    registerConverterAtRuntime(className);
                    logger.info("Automatically register a converter for: " + className);
                }
            }
        }

        // Write size (in bytes) of individual object profile to file
        public static synchronized void writeObjectProfileSizeToFile(long size) {
            try {
                FileWriter objectFileWriter = new FileWriter(objectProfileSizeFilePath, true);
                objectFileWriter.write(size + "\n");
                objectFileWriter.close();
            } catch (Exception e) {
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

        @IsEnabled
        public static boolean enableProfileCollection() {
            return INVOCATION_COUNT < 2;
        }

        @OnBefore
        public static TraceEntry onBefore(OptionalThreadContext context,
                                          @BindReceiver Object receivingObject,
                                          @BindParameterArray Object parameterObjects,
                                          @BindMethodName String methodName) {
            setup();
            if (fileSizeWithinLimits & INVOCATION_COUNT <= 1) {
                if (hasMockableInvocations) {
                    invocationUuid = null;
                    invocationUuid = UUID.randomUUID();
                }
                profileSizePre = getObjectProfileSize();
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

        // Replaced with @BindReceiver for void methods
        @OnReturn
        public static void onReturn(@BindReturn Object returnedObject,
                                    @BindTraveler TraceEntry traceEntry) {
            if (fileSizeWithinLimits & INVOCATION_COUNT <= 1) {
                writeObjectXMLToFile(returnedObject, returnedObjectFilePath);
                writeObjectProfileSizeToFile(getObjectProfileSize() - profileSizePre);
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

        public static String extractClassNameFromTheExceptionMessage(String exceptionMessage) {
            String className = "";

            String pattern = "type\\s+:\\s+(\\S*)\\n";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(exceptionMessage);
            if (m.find()) {
                className = m.group(1);
            }

            return className;
        }

        public static void registerConverterAtRuntime(String className) {
            xStream.registerConverter(new Converter() {
                @Override
                public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
                }

                @Override
                public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
                    return null;
                }

                @Override
                public boolean canConvert(Class aClass) {
                    return aClass.getCanonicalName().equals(className);
                }
            });
        }
    }
}
