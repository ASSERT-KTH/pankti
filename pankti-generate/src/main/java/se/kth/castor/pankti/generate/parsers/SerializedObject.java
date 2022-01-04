package se.kth.castor.pankti.generate.parsers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SerializedObject {
    Map<String, String> receivingObject = new HashMap<>();
    Map<String, String> returnedObject = new HashMap<>();
    Map<String, String> receivingPostObject = new HashMap<>();
    Map<String, String> paramObjects = new HashMap<>();
    Map<String, String> invocationUUID = new HashMap<>();
    List<SerializedObject> nestedSerializedObjects;
    String invocationFQN;

    public SerializedObject(String receivingObject, String returnedObject,
                            String receivingPostObject, String paramObjects,
                            String uuid, List<SerializedObject> nested,
                            String invocationFQN) {
        this.receivingObject.put("receivingObject", receivingObject);
        this.returnedObject.put("returnedObject", returnedObject);
        this.receivingPostObject.put("receivingPostObject", receivingPostObject);
        this.paramObjects.put("paramObjects", paramObjects);
        this.invocationUUID.put("invocationUUID", uuid);
        this.nestedSerializedObjects = nested;
        this.invocationFQN = invocationFQN;
    }

    public String getReceivingObject() {
        return this.receivingObject.get("receivingObject");
    }

    public String getParamObjects() {
        return this.paramObjects.get("paramObjects");
    }

    public String getReturnedObject() {
        return this.returnedObject.get("returnedObject");
    }

    public String getReceivingPostObject() {
        return this.receivingPostObject.get("receivingPostObject");
    }

    public String getUUID() {
        return this.invocationUUID.get("invocationUUID");
    }

    public String getInvocationFQN() {
        return invocationFQN;
    }

    public List<SerializedObject> getNestedSerializedObjects() {
        return nestedSerializedObjects;
    }

    public String getObjectType(String objectXML) {
        return objectXML
                .substring(1, objectXML.indexOf(">"))
                .replaceAll("_-", ".")
                .replace("/", "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerializedObject that = (SerializedObject) o;
        return receivingObject.equals(that.receivingObject) &&
                returnedObject.equals(that.returnedObject) &&
                receivingPostObject.equals(that.receivingPostObject) &&
                paramObjects.equals(that.paramObjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receivingObject, returnedObject, paramObjects, receivingPostObject);
    }

    @Override
    public String toString() {
        return "SerializedObject{" +
                "receivingObject=" + receivingObject +
                ", returnedObject=" + returnedObject +
                ", receivingPostObject=" + receivingPostObject +
                ", paramObjects=" + paramObjects +
                ", invocationUUID=" + invocationUUID +
                ", invocationFQN=" + invocationFQN +
                ", nestedSerializedObjects=" + nestedSerializedObjects +
                '}';
    }
}
