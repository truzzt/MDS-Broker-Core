package de.fraunhofer.iais.eis.ids.index.common.persistence;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.util.*;

import com.fasterxml.jackson.core.JsonGenerationException;
import de.fraunhofer.iais.eis.InfrastructureComponent;
import de.fraunhofer.iais.eis.ModelClass;
import de.fraunhofer.iais.eis.ids.component.core.util.CalendarUtil;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import lombok.experimental.UtilityClass;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.util.ReflectionUtils;

@UtilityClass
public class MaximumGenerator {


    private final Class<InfrastructureComponent> COMPONENT_CLASS =
            InfrastructureComponent.class;


    private final Package             COMPONENT_PACKAGE
                                                    =
            COMPONENT_CLASS.getPackage();
    private final Class<TypedLiteral> LITERAL_CLASS =
            TypedLiteral.class;
    private final Package             UTIL_PACKAGE  =
            LITERAL_CLASS.getPackage();
    private final Class<String>       LANG_CLASS    = String.class;
    private final Package             LANG_PACKAGE  = LANG_CLASS.getPackage();


    <T extends ModelClass> T generate( Class clazz, List<String> generated )
            throws
            IOException,
            InvocationTargetException,
            IllegalAccessException,
            NoSuchMethodException,
            ClassNotFoundException,
            InstantiationException {
        return toJson(clazz, null, null, new Stack<>(), false);
    }


    <T extends ModelClass> T generate( Class clazz )
            throws
            IOException,
            InvocationTargetException,
            IllegalAccessException,
            NoSuchMethodException,
            ClassNotFoundException,
            InstantiationException {
        return toJson(clazz, null, null, new Stack<>(), false);
    }

    /**
     * @param clazz        the class to be index in form of a json document
     * @param inputBuilder already existing builder if recursive
     * @param parent       field under which the object shall be listed
     * @param generated    list of field names to be generatedd
     * @param fromEnum     flag that indicates if list of enums are parsed
     *
     * @return XContentBuilder with the json
     *
     * @throws IOException if the input object cannot be written to json
     */
    private <T extends ModelClass> T toJson( final Class clazz,
                                             final Object inputBuilder,
                                             final Field parent,
                                             final Stack<Class> generated,
                                             final boolean fromEnum )
            throws
            IllegalArgumentException,
            IOException,
            InvocationTargetException,
            IllegalAccessException,
            NoSuchMethodException,
            ClassNotFoundException, InstantiationException {
        final var isRoot = ( inputBuilder == null );
        try {

            var builder = startObject(inputBuilder, clazz, parent);

            final var fields = getFields(clazz);
            generated.add(clazz);
            for( final var field : fields ) {
                ReflectionUtils.makeAccessible(field);
                try {
                    var method =
                            builder.getClass().getMethod(
                                    String.format("%s_", field.getName()),
                                    field.getType());
                    //final var value = field.get(clazz);
                    //if( !generatedField(field, generated) ) {
                    if( List.class.isAssignableFrom(field.getType()) ) {
                        handleList(builder, field, method, generated);
                    } else if( clazz == byte[].class ) {
                        handleByteArray(builder, field);
                    } else if( clazz.isEnum() ) {
                        builder = method.invoke(builder,
                                                List.of(clazz.getEnumConstants())
                                                    .get(0));
                       /* builder.field(getKey(field),
                                      List.of(clazz.getEnumConstants()).get(0));*/
                    } else if( ModelClass.class
                            .isAssignableFrom(field.getType()) ) {
                        //builder.field(getKey(field), field.getType().getName());
                    } else if( field.getType() == URI.class ) {
                        //builder.field(getKey(field),
                        builder = method.invoke(builder,
                                                URI.create("http://exapmle.org")
                        );
                    } else if( field.getType() == String.class ) {
                        builder = method.invoke(builder, "String");
                    } else if( field.getType() == int.class ) {
                        builder = method.invoke(builder, 1);
                    } else if( field.getType() == long.class ) {
                        builder = method.invoke(builder, 1000);
                    } else if( field.getType() == boolean.class
                               || field.getType() == Boolean.class ) {
                        builder = method.invoke(builder, true);
                    } else if( field.getType()
                               == XMLGregorianCalendar.class ) {
                        builder = method.invoke(builder, CalendarUtil.now());
                    } else if( field.getType() == Map.class ) {
                        //builder = method.invoke(builder, "Map");
                    } else if( field.getType() == Duration.class ) {
                        var duration = DatatypeFactory
                                .newDefaultInstance()
                                .newDurationYearMonth(
                                        true,
                                        0,
                                        2);
                        builder = method.invoke(builder, duration);
                    } else if( field.getType() == TypedLiteral.class ) {
                        toJson(clazz, builder, field, generated, fromEnum);
                    } else {
                        System.out.println(
                                field.getType() + " not in toJson Field: "
                                + field.getName() + "Class " + clazz
                                + " list:"
                                + generated.toString());
                    }
                } catch( NoSuchMethodException e ) {
                    System.out.println(
                            "Field " + field.getName() + "Type: " + field
                                    .getType());
                    continue;
                }
            }
            //}
            generated.pop();
            //closeBuilder(builder, isRoot);
            var buildMethod =
                    builder.getClass().getMethod("build");
            return (T) buildMethod.invoke(builder);
        } catch( ClassNotFoundException e ) {
            return null;
        }
    }



/*        private  boolean generatedField( final Field field, final List<String> generated )
                throws IOException {
            var generatedd = generated.contains(field.toString());
            try {
                generatedd |= generated.contains(field.toString().substring(
                        field.toString().lastIndexOf(".") + 1));

            } catch( IndexOutOfBoundsException e ) {
                throw new IOException( "Exclude ends with full stop!", e);
            }
            return generatedd;
        }*/

    private void closeBuilder( final XContentBuilder builder,
                               final boolean isRoot )
            throws IOException {
        builder.endObject();
        if( isRoot ) {
            builder.close();
        }
    }

    private void handleGeneric( final Class clazz,
                                final XContentBuilder builder,
                                final Field field )
            throws IOException {
        builder.field(getKey(field), clazz.getName());
    }

    private void handleObject(
            final XContentBuilder builder, final Object value,
            final Field field, final List<String> generated,
            final boolean fromEnum )
            throws IOException {
        builder.field(getKey(field), "objects");
    }

    private void handleByteArray(
            final Object builder,
            final Field field )
            throws IOException {
        //builder.field(getKey(field), new String("ByteArray"));
    }


    private static <T extends Object> void handleList(
            final Object builder, final Field field,
            final Method method, final Stack<Class> generated )
            throws
            IOException,
            IllegalAccessException,
            ClassNotFoundException,
            NoSuchMethodException,
            InstantiationException {
        try {
            var type = (ParameterizedType) field.getGenericType();
            Class<?> listClass =
                    (Class<T>) type.getActualTypeArguments()[0];

            if( ModelClass.class
                    .isAssignableFrom(listClass) ) {
                if( listClass.isEnum() ) {
                    addListOfEnums(builder, listClass, method, generated);
                } else {
                    addListOfObjects(builder, listClass, field, method,
                                     generated);
                }
            } else if( listClass == String.class ) {
                addListOfPrimitives(builder, listClass, field, method);
            } else {
                addListOfOthers(builder, listClass, field, method);
            }
        } catch( ClassCastException e ) {
            //System.out.println(field.getGenericType());
            return;
        } catch( InvocationTargetException e ) {
            e.printStackTrace();
        }
    }


    private <T> void addListOfPrimitives(
            final Object builder, final Class<?> clazz,
            final Field field, Method method )
            throws
            IOException,
            InvocationTargetException,
            IllegalAccessException {
        var valueList = new ArrayList<T>();
        if( clazz.getName().equals(Integer.class.getName()) ) {
            valueList = (ArrayList<T>) List.of(1);
            if( clazz.getName().equals(Boolean.class.getName()) ) {
                valueList = (ArrayList<T>) List.of(false);

            }
            method.invoke(builder, valueList);
        }
    }

    private void addListOfOthers(
            final Object builder, final Class<?> valueList,
            final Field field, Method method )
            throws
            InvocationTargetException,
            IllegalAccessException {
        var stringList = new ArrayList<String>();
        stringList.add("example");
        method.invoke(builder, stringList);
    }

    public <T extends Object> void addList( final Object builder,
                                            final Class<T> clazz,
                                            final Field field,
                                            final Method method,
                                            final Stack<Class> generated,
                                            final boolean fromEnum )
            throws
            IOException,
            ClassNotFoundException,
            InvocationTargetException,
            IllegalAccessException,
            NoSuchMethodException,
            InstantiationException {
        if( generated.contains(clazz) ) {
            //builder.value(clazz.getName());
            System.out.println("here");
            //builder.value(List.of(clazz.getName()));
        } else {
            var element = toJson(clazz, builder, null, generated, fromEnum);
            if( element != null ) {
                method.invoke(builder, List.of(element));
            } else {
                method.invoke(builder, new ArrayList<T>());
            }

        }

    }

    private void addListOfObjects(
            final Object builder, final Class<?> clazz,
            final Field field, final Method method,
            final Stack<Class> generated )
            throws
            IOException,
            ClassNotFoundException,
            InvocationTargetException,
            IllegalAccessException,
            NoSuchMethodException,
            InstantiationException {

        addList(builder, clazz, field, method, generated, false);

    }


    private void addListOfEnums(
            final Object builder, final Class<?> clazz,
            final Method method, final List<Class> generated )
            throws
            IOException,
            InvocationTargetException,
            IllegalAccessException {
        var aEnum = clazz.getEnumConstants()[0];
        var list = List.of(aEnum);
        method.invoke(builder,
                      list);
    }

    private <T extends ModelClass> Object startObject(
            final Object inputBuilder, final Class<T> clazz,
            final Field parent )
            throws
            IOException,
            ClassNotFoundException,
            InstantiationException,
            IllegalAccessException {

/*            var builder = inputBuilder;
            if( builder == null ) {
                builder = startRootObject();
            } else {
                if( parent == null ) {
                    startWithName(clazz, builder);
                } else {
                    startWithParent(parent, builder);
                }
            }*/
        var builderClass = Class.forName(clazz.getName() + "Builder");
        var builder = builderClass.newInstance();
        return builder;
    }

    private void startWithParent( final Field parent,
                                  final XContentBuilder builder )
            throws IOException {
        builder.startObject(getKey(parent));
    }

    private <T extends ModelClass> void startWithName( final Class<T> clazz,
                                                       final XContentBuilder builder )
            throws IOException {
        try {
            builder.startObject(clazz.getName());
        } catch( JsonGenerationException e ) {
            builder.startObject();
        }
    }

    private XContentBuilder startRootObject() throws IOException {
        final var builder = XContentFactory.jsonBuilder();
        builder.startObject();
        return builder;
    }

    private String getKey( final Field field ) {
        var key = field.getName();
        if( key.startsWith("_") ) {
            key = key.substring(1);
        }
        return key;
    }

    private List<Field> getFields( Class aClass ) {
        final var superClasses = new ArrayList<Class<?>>();
        final var fields = new ArrayList<Field>();

        if( aClass.isInterface() ) {

            try {
                aClass = Class.forName(aClass.getName() + "Impl");
            } catch( ClassNotFoundException ignored ) {
            }
        }

        while( aClass != null && aClass != Enum.class ) {
            superClasses.add(aClass);
            aClass = aClass.getSuperclass();
        }
        for( final var superClass : superClasses ) {
            fields.addAll(Arrays.asList(superClass.getDeclaredFields()));
        }

        return fields;
    }
}


