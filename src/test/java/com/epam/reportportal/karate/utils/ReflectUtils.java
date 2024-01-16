/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate.utils;

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
