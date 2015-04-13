/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManager;
import co.paralleluniverse.capsule.Jar;
import co.paralleluniverse.capsule.test.CapsuleTestUtils;
import static co.paralleluniverse.capsule.test.CapsuleTestUtils.*;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 * @author pron
 */
public class MavenCapsuleTest {
    private final FileSystem fs = Jimfs.newFileSystem();
    private final Path cache = fs.getPath("/cache");
    private final Path tmp = fs.getPath("/tmp");
    private Map<String, List<Path>> deps;
    private Properties props;

    @Before
    public void setUp() throws Exception {
        deps = null;
        props = new Properties(System.getProperties());
        setProperties(props);
        setCacheDir(cache);
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

        assertEquals("com.acme.foo_1.0", capsule.getAppId());
    }

    @Test
    public void testPomDependencies() throws Exception {
        List<String> ds = list("com.acme:bar:1.2", "com.acme:baz:3.4:jdk8(org.asd:qqq,com.gogo:bad)");

        Model pom = newPom();
        pom.setGroupId("com.acme");
        pom.setArtifactId("foo");
        pom.setVersion("1.0");
        for (String d : ds)
            pom.addDependency(coordsToDependency(d));

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("pom.xml", toInputStream(pom));

        Capsule capsule = newCapsule(jar);
        for (String d : ds)
            assert_().that(capsule.getAttribute(Capsule.ATTR_DEPENDENCIES)).has().item(DependencyManager.toDependency(d, "jar"));
    }

    //<editor-fold defaultstate="collapsed" desc="POM Utilities">
    /////////// POM Utilities ///////////////////////////////////
    private Model newPom() {
        return new Model();
    }

    private InputStream toInputStream(Model model) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new MavenXpp3Writer().write(baos, model);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static final Pattern PAT_DEPENDENCY = Pattern.compile("(?<groupId>[^:\\(\\)]+):(?<artifactId>[^:\\(\\)]+)(:(?<version>[^:\\(\\)]*))?(:(?<classifier>[^:\\(\\)]+))?(\\((?<exclusions>[^\\(\\)]*)\\))?");

    static Dependency coordsToDependency(String depString) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        final Dependency d = new Dependency();
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

    //<editor-fold defaultstate="collapsed" desc="Dependency Manager Utilities">
    /////////// Dependency Manager Utilities ///////////////////////////////////
    private Path mockDep(DependencyManager dm, String dep, String type) {
        return mockDep(dm, dep, type, dep).get(0);
    }

    private List<Path> mockDep(DependencyManager dm, String dep, String type, String... artifacts) {
        List<Path> as = new ArrayList<>(artifacts.length);
        for (String a : artifacts)
            as.add(artifact(a, type));

        if (deps == null)
            this.deps = new HashMap<>();
        deps.put(dep, as);

        when(dm.resolveDependency(dep, type)).thenReturn(as);
        when(dm.resolveDependencies(anyList(), eq(type))).thenAnswer(new Answer<List<Path>>() {
            @Override
            public List<Path> answer(InvocationOnMock invocation) throws Throwable {
                List<String> coords = (List<String>) invocation.getArguments()[0];
                List<Path> res = new ArrayList<>();
                for (String dep : coords)
                    res.addAll(deps.get(dep));

                return res;
            }
        });

        return as;
    }

    private Path artifact(String x, String type) {
        String[] coords = x.split(":");
        String group = coords[0];
        String artifact = coords[1];
        String artifactDir = artifact.split("-")[0]; // arbitrary
        String version = coords[2] + (coords.length > 3 ? "-" + coords[3] : "");
        return cache.resolve("deps").resolve(group).resolve(artifactDir).resolve(artifact + "-" + version + '.' + type);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    // may be called once per test (always writes jar into /capsule.jar)
    private Capsule newCapsule(Jar jar) {
        return (Capsule) CapsuleTestUtils.newCapsule(jar, path("capsule.jar"));
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
    //</editor-fold>
}
