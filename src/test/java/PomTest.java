/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import co.paralleluniverse.capsule.Jar;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.google.common.truth.Truth.*;
import java.util.Properties;
import org.junit.Before;
import static org.mockito.Mockito.verify;

/**
 *
 * @author pron
 */
public class PomTest {
    private final FileSystem fs = Jimfs.newFileSystem();
    private final Path cache = fs.getPath("/cache");
    private final Path tmp = fs.getPath("/tmp");
    private Properties props;

    @Before
    public void setUp() throws Exception {
        accessible(Capsule.class.getDeclaredField("CACHE_DIR")).set(null, cache);

        props = new Properties(System.getProperties());
        accessible(Capsule.class.getDeclaredField("PROPERTIES")).set(null, props);
    }

    @Test
    public void whenNoNameAndPomTakeIdFromPom() throws Exception {
        Model pom = newPom();
        pom.setGroupId("com.acme");
        pom.setArtifactId("foo");
        pom.setVersion("1.0");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("pom.xml", toInputStream(pom));

        Capsule capsule = newCapsule(jar);

        assertEquals("com.acme_foo_1.0", capsule.getAppId());
    }

    @Test
    public void testPomDependencies1() throws Exception {
        List<String> deps = list("com.acme:bar:1.2", "com.acme:baz:3.4:jdk8(org.asd:qqq,com.gogo:bad)");

        Model pom = newPom();
        pom.setGroupId("com.acme");
        pom.setArtifactId("foo");
        pom.setVersion("1.0");
        for (String dep : deps)
            pom.addDependency(coordsToDependency(dep));

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("pom.xml", toInputStream(pom));

        Capsule capsule = newCapsule(jar);
        assert_().that(capsule.getDependencies()).has().allFrom(deps);
    }

    //<editor-fold defaultstate="collapsed" desc="POM Utilities">
    /////////// POM Utilities ///////////////////////////////////
    private Model newPom() {
        return new Model();
    }

    private InputStream toInputStream(Model model) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new MavenXpp3Writer().write(baos, model);
            baos.close();
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static final Pattern PAT_DEPENDENCY = Pattern.compile("(?<groupId>[^:\\(\\)]+):(?<artifactId>[^:\\(\\)]+)(:(?<version>[^:\\(\\)]*))?(:(?<classifier>[^:\\(\\)]+))?(\\((?<exclusions>[^\\(\\)]*)\\))?");

    static Dependency coordsToDependency(final String depString) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        Dependency d = new Dependency();
        d.setGroupId(m.group("groupId"));
        d.setArtifactId(m.group("artifactId"));
        String version = m.group("version");
        if (version == null || version.isEmpty())
            version = "[0,)";
        d.setVersion(version);
        d.setClassifier(m.group("classifier"));
        d.setScope("runtime");
        for (Exclusion ex : getExclusions(depString))
            d.addExclusion(ex);
        return d;
    }

    static Collection<Exclusion> getExclusions(String depString) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        if (m.group("exclusions") == null || m.group("exclusions").isEmpty())
            return Collections.emptyList();

        final List<String> exclusionPatterns = Arrays.asList(m.group("exclusions").split(","));
        final List<Exclusion> exclusions = new ArrayList<Exclusion>();
        for (String expat : exclusionPatterns) {
            String[] coords = expat.trim().split(":");
            if (coords.length != 2)
                throw new IllegalArgumentException("Illegal exclusion dependency coordinates: " + depString + " (in exclusion " + expat + ")");
            Exclusion ex = new Exclusion();
            ex.setGroupId(coords[0]);
            ex.setArtifactId(coords[1]);
            exclusions.add(ex);
        }
        return exclusions;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    // may be called once per test (always writes jar into /capsule.jar)
    private Capsule newCapsule(Jar jar) {
        try {
            Path capsuleJar = path("capsule.jar");
            jar.write(capsuleJar);
            final String mainClass = jar.getAttribute("Main-Class");
            final Class<?> clazz = Class.forName(mainClass);
            accessible(Capsule.class.getDeclaredField("PROFILE")).set(null, 10); // disable profiling even when log=debug

            Constructor<?> ctor = accessible(clazz.getDeclaredConstructor(Path.class));
            return (Capsule) ctor.newInstance(capsuleJar);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Jar newCapsuleJar() {
        return new Jar()
                .setAttribute("Manifest-Version", "1.0")
                .setAttribute("Main-Class", "Capsule")
                .setListAttribute("Caplets", list("MavenCapsule"));
    }

    private Path path(String first, String... more) {
        return fs.getPath(first, more);
    }

    @SafeVarargs
    private static <T> List<T> list(T... xs) {
        return Arrays.asList(xs);
    }

    private InputStream emptyInputStream() {
        return Jar.toInputStream("", UTF_8);
    }

    private static <T extends AccessibleObject> T accessible(T x) {
        if (!x.isAccessible())
            x.setAccessible(true);

        if (x instanceof Field) {
            Field field = (Field) x;
            if ((field.getModifiers() & Modifier.FINAL) != 0) {
                try {
                    Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }

        return x;
    }
    //</editor-fold>
}
