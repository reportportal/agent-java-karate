package com.epam.reportportal.utils;

import java.lang.reflect.Field;

public class ReflectUtils {

    private ReflectUtils() {
    }

    public static void setField(Object object, Field fld, Object value) {
        try {
            fld.setAccessible(true);
            fld.set(object, value);
        } catch (IllegalAccessException e) {
            String fieldName = fld.getName();
            throw new RuntimeException("Failed to set " + fieldName + " of object", e);
        }
    }
}