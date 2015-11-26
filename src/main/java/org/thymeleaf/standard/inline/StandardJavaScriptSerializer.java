/*
 * =============================================================================
 * 
 *   Copyright (c) 2011-2014, The THYMELEAF team (http://www.thymeleaf.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package org.thymeleaf.standard.inline;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.util.ClassLoaderUtils;
import org.thymeleaf.util.DateUtils;
import org.unbescape.json.JsonEscape;
import org.unbescape.json.JsonEscapeLevel;
import org.unbescape.json.JsonEscapeType;


/**
 * 
 * @author Daniel Fern&aacute;ndez
 * 
 * @since 1.0
 *
 */
public final class StandardJavaScriptSerializer implements IStandardJavaScriptSerializer {


    private IStandardJavaScriptSerializer delegate;



    private static boolean isJacksonPresent() {
        final ClassLoader classLoader = ClassLoaderUtils.getClassLoader(StandardJavaScriptSerializer.class);
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper", false, classLoader);
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }



    public StandardJavaScriptSerializer(final boolean useJacksonIfAvailable) {
        super();
        if (useJacksonIfAvailable && isJacksonPresent()) {
            this.delegate = new JacksonStandardJavaScriptSerializer();
        } else {
            this.delegate = new DefaultStandardJavaScriptSerializer();
        }
    }




    public void serializeValue(final Object object, final Writer writer) {
        this.delegate.serializeValue(object, writer);
    }







    private static final class JacksonStandardJavaScriptSerializer implements IStandardJavaScriptSerializer {

        private final ObjectMapper mapper;


        JacksonStandardJavaScriptSerializer() {
            super();
            this.mapper = new ObjectMapper();
            this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            this.mapper.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
            this.mapper.setDateFormat(new JacksonThymeleafISO8601DateFormat());
        }


        public void serializeValue(final Object object, final Writer writer) {
            try {
                this.mapper.writeValue(writer, object);
            } catch (final IOException e) {
                throw new TemplateProcessingException(
                        "An exception was raised while trying to serialize object using Jackson", e);
            }
        }

    }





    /*
     * This DateFormat implementation replaces the standard Jackson date serialization mechanism for ISO6801 dates,
     * with the aim of making Jackson output dates in a way that is at the same time ECMAScript-valid and also
     * as compatible with non-Jackson JavaScript serialization infrastructure in Thymeleaf as possible. For this:
     *
     *   * The default Jackson behaviour of outputting all dates as GMT is disabled.
     *   * The default Jackson format adding timezone as '+0800' is modified, as ECMAScript requires '+08:00'
     *
     * On the latter point, see https://github.com/FasterXML/jackson-databind/issues/1020
     */
    private static final class JacksonThymeleafISO8601DateFormat extends DateFormat {

        /*
         * This SimpleDateFormat defines an almost-ISO8601 formatter.
         *
         * The correct ISO8601 format would be "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", but the "X" pattern (which outputs the
         * timezone as "+02:00" or "Z" instead of "+0200") was not added until Java SE 7. So the use of this
         * SimpleDateFormat object requires additional post-processing.
         *
         * SimpleDateFormat objects are NOT thread-safe, but it is here being used from another DateFormat
         * implementation, so we must suppose that it is the use of this DateFormat wrapper that will be
         * adequately synchronized by Jackson.
         */
        private SimpleDateFormat dateFormat;


        JacksonThymeleafISO8601DateFormat() {
            super();
            this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZ");
            setCalendar(this.dateFormat.getCalendar());
            setNumberFormat(this.dateFormat.getNumberFormat());
        }


        @Override
        public StringBuffer format(final Date date, final StringBuffer toAppendTo, final FieldPosition fieldPosition) {
            final StringBuffer formatted = this.dateFormat.format(date, toAppendTo, fieldPosition);
            formatted.insert(26, ':');
            return formatted;
        }


        @Override
        public Date parse(final String source, final ParsePosition pos) {
            throw new UnsupportedOperationException(
                    "JacksonThymeleafISO8601DateFormat should never be asked for a 'parse' operation");
        }



    }



    private static final class DefaultStandardJavaScriptSerializer implements IStandardJavaScriptSerializer {


        public void serializeValue(final Object object, final Writer writer) {
            try {
                writeValue(writer, object);
            } catch (final IOException e) {
                throw new TemplateProcessingException(
                        "An exception was raised while trying to serialize object using Jackson", e);
            }
        }


        private static void writeValue(final Writer writer, final Object object) throws IOException {
            if (object == null) {
                writeNull(writer);
                return;
            }
            if (object instanceof CharSequence) {
                writeString(writer, object.toString());
                return;
            }
            if (object instanceof Character) {
                writeString(writer, object.toString());
                return;
            }
            if (object instanceof Number) {
                writeNumber(writer, (Number) object);
                return;
            }
            if (object instanceof Boolean) {
                writeBoolean(writer, (Boolean) object);
                return;
            }
            if (object instanceof Date) {
                writeDate(writer, (Date) object);
                return;
            }
            if (object instanceof Calendar) {
                writeDate(writer, ((Calendar) object).getTime());
                return;
            }
            if (object.getClass().isArray()) {
                writeArray(writer, object);
                return;
            }
            if (object instanceof Collection<?>) {
                writeCollection(writer, (Collection<?>) object);
                return;
            }
            if (object instanceof Map<?,?>) {
                writeMap(writer, (Map<?, ?>) object);
                return;
            }
            if (object.getClass().isEnum()) {
                writeEnum(writer, object);
                return;
            }
            writeObject(writer, object);
        }


        private static void writeNull(final Writer writer) throws IOException {
            writer.write("null");
        }


        private static void writeString(final Writer writer, final String str) throws IOException {
        /*
         * Note we will be using JSON escape instead of JavaScript escape. They are basically (99%) interchangeable
         * once we have established that our literals use double-quotes (") and not single-quotes, and this allows us
         * to avoid escaping single-quotes and therefore be more consistent with Jackson-based serialization, which
         * is obviously JSON-based.
         */
            writer.write('"');
            writer.write(JsonEscape.escapeJson(str, JsonEscapeType.SINGLE_ESCAPE_CHARS_DEFAULT_TO_UHEXA, JsonEscapeLevel.LEVEL_2_ALL_NON_ASCII_PLUS_BASIC_ESCAPE_SET));
            writer.write('"');
        }


        private static void writeNumber(final Writer writer, final Number number) throws IOException {
            writer.write(number.toString());
        }


        private static void writeBoolean(final Writer writer, final Boolean bool) throws IOException {
            writer.write(bool.toString());
        }


        private static void writeDate(final Writer writer, final Date date) throws IOException {
            writer.write('"');
            writer.write(DateUtils.formatISO(date));
            writer.write('"');
        }


        private static void writeArray(final Writer writer, final Object arrayObj) throws IOException {
            writer.write('[');
            if (arrayObj instanceof Object[]) {
                final Object[] array = (Object[]) arrayObj;
                boolean first = true;
                for (final Object element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, element);
                }
            } else if (arrayObj instanceof boolean[]) {
                final boolean[] array = (boolean[]) arrayObj;
                boolean first = true;
                for (final boolean element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, Boolean.valueOf(element));
                }
            } else if (arrayObj instanceof byte[]) {
                final byte[] array = (byte[]) arrayObj;
                boolean first = true;
                for (final byte element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, Byte.valueOf(element));
                }
            } else if (arrayObj instanceof short[]) {
                final short[] array = (short[]) arrayObj;
                boolean first = true;
                for (final short element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, Short.valueOf(element));
                }
            } else if (arrayObj instanceof int[]) {
                final int[] array = (int[]) arrayObj;
                boolean first = true;
                for (final int element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, Integer.valueOf(element));
                }
            } else if (arrayObj instanceof long[]) {
                final long[] array = (long[]) arrayObj;
                boolean first = true;
                for (final long element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, Long.valueOf(element));
                }
            } else if (arrayObj instanceof float[]) {
                final float[] array = (float[]) arrayObj;
                boolean first = true;
                for (final float element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, Float.valueOf(element));
                }
            } else if (arrayObj instanceof double[]) {
                final double[] array = (double[]) arrayObj;
                boolean first = true;
                for (final double element: array) {
                    if (first) {
                        first = false;
                    } else {
                        writer.write(',');
                    }
                    writeValue(writer, Double.valueOf(element));
                }
            } else {
                throw new IllegalArgumentException("Cannot write value \"" + arrayObj + "\" of class " + arrayObj.getClass().getName() + " as an array");
            }
            writer.write(']');
        }


        private static void writeCollection(final Writer writer, final Collection<?> collection) throws IOException {
            writer.write('[');
            boolean first = true;
            for (final Object element: collection) {
                if (first) {
                    first = false;
                } else {
                    writer.write(',');
                }
                writeValue(writer, element);
            }
            writer.write(']');
        }


        private static void writeMap(final Writer writer, final Map<?,?> map) throws IOException {
            writer.write('{');
            boolean first = true;
            for (final Map.Entry<?,?> entry: map.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    writer.write(',');
                }
                writeKeyValue(writer, entry.getKey(), entry.getValue());
            }
            writer.write('}');
        }


        private static void writeKeyValue(final Writer writer, final Object key, final Object value) throws IOException {
            writeValue(writer, key);
            writer.write(':');
            writeValue(writer, value);
        }


        private static void writeObject(final Writer writer, final Object object) throws IOException {
            try {
                final PropertyDescriptor[] descriptors =
                        Introspector.getBeanInfo(object.getClass()).getPropertyDescriptors();
                final Map<String,Object> properties = new LinkedHashMap<String, Object>(descriptors.length + 1, 1.0f);
                for (final PropertyDescriptor descriptor : descriptors) {
                    final Method readMethod =  descriptor.getReadMethod();
                    if (readMethod != null) {
                        final String name = descriptor.getName();
                        if (!"class".equals(name.toLowerCase())) {
                            final Object value = readMethod.invoke(object);
                            properties.put(name, value);
                        }
                    }
                }
                writeMap(writer, properties);
            } catch (final IllegalAccessException e) {
                throw new IllegalArgumentException("Could not perform introspection on object of class " + object.getClass().getName(), e);
            } catch (final InvocationTargetException e) {
                throw new IllegalArgumentException("Could not perform introspection on object of class " + object.getClass().getName(), e);
            } catch (final IntrospectionException e) {
                throw new IllegalArgumentException("Could not perform introspection on object of class " + object.getClass().getName(), e);
            }
        }



        private static void writeEnum(final Writer writer, final Object object) throws IOException {

            final Enum<?> enumObject = (Enum<?>) object;
            final Class<?> enumClass = object.getClass();

            final Map<String,Object> properties = new LinkedHashMap<String, Object>(3, 1.0f);
            properties.put("$type", enumClass.getSimpleName());
            properties.put("$name", enumObject.name());

            writeMap(writer, properties);

        }


    }



}
