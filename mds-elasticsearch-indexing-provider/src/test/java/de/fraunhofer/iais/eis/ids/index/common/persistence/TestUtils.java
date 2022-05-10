package de.fraunhofer.iais.eis.ids.index.common.persistence;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import de.fraunhofer.iais.eis.ModelClass;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
class TestUtils {

    private static final Serializer SERIALIZER = new Serializer();

    @SneakyThrows
    static <T extends ModelClass> T getTestResource( String pathName,
                                                     Class<T> clazz ) {
        var path = new File("src/test/resources/" + pathName);
        var modelClassAsString =
                Files.readString(path.toPath(), StandardCharsets.UTF_8);
        return SERIALIZER
                .deserialize(modelClassAsString, clazz);

    }

    @SneakyThrows
    static String getTestResourceAsString( String pathName ) {
        var path = new File("src/test/resources/" + pathName);
        return Files.readString(path.toPath(), StandardCharsets.UTF_8);
    }

}
