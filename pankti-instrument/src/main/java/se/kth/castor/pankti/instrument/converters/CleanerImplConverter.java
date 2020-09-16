package se.kth.castor.pankti.instrument.converters;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.lang.ref.Cleaner;

public class CleanerImplConverter implements Converter {
    @Override
    public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
        return null;
    }

    @Override
    public boolean canConvert(Class aClass) {
        return aClass.getCanonicalName().equals("java.lang.ref.Cleaner") ||
                aClass.getCanonicalName().equals("java.lang.ref.Cleaner.Cleanable") ||
                aClass.getCanonicalName().equals("jdk.internal.ref.CleanerImpl") ||
                aClass.getCanonicalName().equals("jdk.internal.ref.CleanerImpl.PhantomCleanableRef") ||
                Cleaner.class.isAssignableFrom(aClass) ||
                Cleaner.Cleanable.class.isAssignableFrom(aClass);
    }
}
