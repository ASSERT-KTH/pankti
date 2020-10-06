package se.kth.castor.pankti.generate.parsers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SerializedObject {
    Map<String, String> receivingObject = new HashMap<>();
    Map<String, String> returnedObject = new HashMap<>();
    Map<String, String> paramObjects = new HashMap<>();

    public SerializedObject(String receivingObject, String returnedObject, String paramObjects) {
        this.receivingObject.put("receivingObject", receivingObject);
        this.returnedObject.put("returnedObject", returnedObject);
        this.paramObjects.put("paramObjects", paramObjects);
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

    public String getObjectType(String objectJSON) {
        String typeString = null;
        JSONObject jsonObject = JSON.parseObject(objectJSON);
        if (!jsonObject.keySet().isEmpty()) {
            typeString = jsonObject.keySet().iterator().next();
        }
        return typeString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerializedObject that = (SerializedObject) o;
        return receivingObject.equals(that.receivingObject) &&
                returnedObject.equals(that.returnedObject) &&
                paramObjects.equals(that.paramObjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receivingObject, returnedObject, paramObjects);
    }

    @Override
    public String toString() {
        return "SerializedObject{" +
                "receivingObject=" + receivingObject +
                ", returnedObject=" + returnedObject +
                ", paramObjects=" + paramObjects +
                '}';
    }
}
