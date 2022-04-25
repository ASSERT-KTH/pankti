package se.kth.castor.pankti.generate.data;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * This class represents an element of an object profile
 * It may correspond to a (nested) receiving object,
 * a (nested) returned object, or a (nested) parameter object array
 * <p>
 * It is useful when creating a serialized object from raw XML
 */
public class ObjectProfileElement {
    String rawXML;
    String uuid;
    Instant timestamp;

    public ObjectProfileElement() {}

    public String getRawXML() {
        return rawXML;
    }

    public String getUuid() {
        return uuid;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setRawXML(String rawXML) {
        this.rawXML = rawXML;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
