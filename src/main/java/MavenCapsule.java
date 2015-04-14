/*
 * Copyright (c) 2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManager;
import capsule.PomReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map.Entry;
import static java.util.Arrays.asList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.graph.Dependency;

/**
 *
 * @author pron
 */
public class MavenCapsule extends Capsule {
    private static final String PROP_TREE = OPTION("capsule.tree", "false", "printDependencyTree", "Prints the capsule's dependency tree.");
    private static final String PROP_RESOLVE = OPTION("capsule.resolve", "false", "resolve", "Downloads all un-cached dependencies.");
    private static final String PROP_USE_LOCAL_REPO = OPTION("capsule.local", null, null, "Sets the path of the local Maven repository to use.");
    private static final String PROP_RESET = "capsule.reset";
    private static final String PROP_USER_HOME = "user.home";

    private static final Entry<String, List<String>> ATTR_REPOSITORIES = ATTRIBUTE("Repositories", T_LIST(T_STRING()), asList("central"), true, "A list of Maven repositories, each formatted as URL or NAME(URL)");
    private static final Entry<String, Boolean> ATTR_ALLOW_SNAPSHOTS = ATTRIBUTE("Allow-Snapshots", T_BOOL(), false, true, "Whether or not SNAPSHOT dependencies are allowed");

    private static final String ENV_CAPSULE_REPOS = "CAPSULE_REPOS";
    private static final String ENV_CAPSULE_LOCAL_REPO = "CAPSULE_LOCAL_REPO";

    private static final String POM_FILE = "pom.xml";
    private static final String DEPS_CACHE_NAME = "deps";

    private DependencyManager dependencyManager;
    private PomReader pom;
    private Path localRepo;
    private String version; // app version cache

    private static final List<Path> UNRESOLVED = new ArrayList<>();
    private final Map<Dependency, List<Path>> dependencies = new HashMap<>();

    //<editor-fold defaultstate="collapsed" desc="Constructors">
    /////////// Constructors ///////////////////////////////////
    public MavenCapsule(Path jarFile) {
        super(jarFile);
    }

    public MavenCapsule(Capsule pred) {
        super(pred);
    }

    @Override
    protected void finalizeCapsule() {
        this.pom = createPomReader();
        if (dependencyManager != null)
            setDependencyRepositories(getAttribute(ATTR_REPOSITORIES));

        super.finalizeCapsule();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Main Operations">
    /////////// Main Operations ///////////////////////////////////
    void printDependencyTree(List<String> args) {
        verifyNonEmpty("Cannot print dependencies of a wrapper capsule.");
        STDOUT.println("Dependencies for " + getAppId());

        if (hasAttribute(ATTR_APP_ARTIFACT)) {
            final String appArtifact = getAttribute(ATTR_APP_ARTIFACT);
            if (isDependency(appArtifact))
                getDependencyManager().printDependencyTree(appArtifact, "jar", STDOUT);
        } else {
            lookupAllDependencies();
            if (dependencies.isEmpty())
                STDOUT.println("No external dependencies.");
            else
                getDependencyManager().printDependencyTree(getUnresolved(), STDOUT);
        }
    }

    void resolve(List<String> args) throws IOException, InterruptedException {
        verifyNonEmpty("Cannot resolve a wrapper capsule.");

        if (hasAttribute(ATTR_APP_ARTIFACT)) {
            final String appArtifact = getAttribute(ATTR_APP_ARTIFACT);
            lookup(appArtifact);
        }
        lookupAllDependencies();

        getDependencyManager().resolveDependencies(getUnresolved());
        log(LOG_QUIET, "Capsule resolved");
    }

    private void verifyNonEmpty(String message) {
        if (isEmptyCapsule())
            throw new IllegalArgumentException(message);
    }

    private void lookupAllDependencies() {
        try {
            accessible(Capsule.class.getDeclaredMethod("lookupAllDependencies")).invoke(this);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
//        for (Map.Entry<String, ?> attr : asList(
//                ATTR_DEPENDENCIES,
//                ATTR_NATIVE_DEPENDENCIES,
//                ATTR_APP_CLASS_PATH,
//                ATTR_BOOT_CLASS_PATH,
//                ATTR_BOOT_CLASS_PATH_P,
//                ATTR_BOOT_CLASS_PATH_A,
//                ATTR_JAVA_AGENTS,
//                ATTR_NATIVE_AGENTS))
//            getAttribute(attr);
    }

    private List<Dependency> getUnresolved() {
        final List<Dependency> unresolved = new ArrayList<>();
        for (Map.Entry<Dependency, List<Path>> e : dependencies.entrySet()) {
            if (e.getValue() == UNRESOLVED)
                unresolved.add(e.getKey());
        }
        return unresolved;
    }
    //</editor-fold>

    //<editor-fold desc="Capsule Overrides">
    /////////// Capsule Overrides ///////////////////////////////////
    @Override
    @SuppressWarnings("unchecked")
    protected <T> T attribute(Entry<String, T> attr) {
        if (ATTR_APP_ID == attr) {
            String id = super.attribute(ATTR_APP_ID);
            if (id == null && pom != null)
                id = pom.getGroupId() + "." + pom.getArtifactId();
            return (T) id;
        }

        if (ATTR_APP_VERSION == attr) {
            String ver = super.attribute(ATTR_APP_VERSION);
            if (ver == null && version != null)
                ver = version;
            if (ver == null && hasAttribute(ATTR_APP_ARTIFACT) && isDependency(getAttribute(ATTR_APP_ARTIFACT)))
                ver = getAppArtifactVersion(getDependencyManager().getLatestVersion(getAttribute(ATTR_APP_VERSION), "jar"));
            if (ver == null && pom != null)
                ver = pom.getVersion();
            this.version = ver; // cache
            return (T) ver;
        }

        if (ATTR_DEPENDENCIES == attr) {
            List<Object> deps = super.attribute(ATTR_DEPENDENCIES);
            if ((deps == null || deps.isEmpty()) && pom != null) {
                deps = new ArrayList<>();
                for (String[] d : pom.getDependencies())
                    deps.add(lookup(d[0], d[1]));
            }
            return (T) deps;
        }

        if (ATTR_REPOSITORIES == attr) {
            final List<String> repos = new ArrayList<String>();
            repos.addAll(nullToEmpty(split(getenv(ENV_CAPSULE_REPOS), "[,\\s]\\s*")));
            repos.addAll(super.attribute(ATTR_REPOSITORIES));
            if (pom != null)
                addAllIfAbsent(repos, nullToEmpty(pom.getRepositories()));

            return (T) repos;
        }
        return super.attribute(attr);
    }

    @Override
    protected List<Path> resolve0(Object x) {
        if (x instanceof Dependency) {
            final Dependency d = (Dependency) x;
            if (dependencies.get(d) == UNRESOLVED)
                dependencies.putAll(getDependencyManager().resolveDependencies(getUnresolved()));
            assert dependencies.get(d) != UNRESOLVED : d;
            return dependencies.get(d);
        } else
            return super.resolve0(x); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Object lookup0(String x, String type) {
        Object res = super.lookup0(x, type);
        if (res == null && isDependency(x)) {
            final Dependency dep = DependencyManager.toDependency(x, type);
            if (!dependencies.containsKey(dep))
                dependencies.put(dep, UNRESOLVED);
            return dep;
        }
        return res;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Internal Methods">
    /////////// Internal Methods ///////////////////////////////////
    private PomReader createPomReader() {
        try (InputStream is = getEntryInputStream(getJarFile(), POM_FILE)) {
            return is != null ? new PomReader(is) : null;
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + POM_FILE, e);
        }
    }

    private DependencyManager getDependencyManager() {
        final DependencyManager dm = initDependencyManager();
        if (dm == null)
            throw new RuntimeException("Capsule " + getJarFile() + " uses dependencies, while the necessary dependency management classes are not found in the capsule JAR");
        return dm;
    }

    private DependencyManager initDependencyManager() {
        if (dependencyManager == null) {
            dependencyManager = createDependencyManager();
            if (dependencyManager != null)
                setDependencyRepositories(getAttribute(ATTR_REPOSITORIES));
        }
        return dependencyManager;
    }

    private DependencyManager createDependencyManager() {
        final boolean reset = systemPropertyEmptyOrTrue(PROP_RESET);
        return createDependencyManager(getLocalRepo().toAbsolutePath(), reset, getLogLevel());
    }

    protected DependencyManager createDependencyManager(Path localRepo, boolean reset, int logLevel) {
        MavenCapsule ct;
        return (ct = getCallTarget(MavenCapsule.class)) != null ? ct.createDependencyManager(localRepo, reset, logLevel) : createDependencyManager0(localRepo, reset, logLevel);
    }

    private DependencyManager createDependencyManager0(Path localRepo, boolean reset, int logLevel) {
        return new DependencyManager(localRepo, reset, logLevel);
    }

    private void setDependencyRepositories(List<String> repositories) {
        getDependencyManager().setRepos(repositories, getAttribute(ATTR_ALLOW_SNAPSHOTS));
    }

    private Path getLocalRepo() {
        if (localRepo == null) {
            Path repo;
            final String local = emptyToNull(expandCommandLinePath(propertyOrEnv(PROP_USE_LOCAL_REPO, ENV_CAPSULE_LOCAL_REPO)));
            if (local != null)
                repo = toAbsolutePath(Paths.get(local));
            else {
                repo = getCacheDir().resolve(DEPS_CACHE_NAME);
                try {
                    if (!Files.exists(repo))
                        Files.createDirectory(repo, getPermissions(repo.getParent()));
                    return repo;
                } catch (IOException e) {
                    log(LOG_VERBOSE, "Could not create local repo at " + repo);
                    if (isLogging(LOG_VERBOSE))
                        e.printStackTrace(STDERR);
                    repo = null;
                }
            }
            localRepo = repo;
        }
        return localRepo;
    }

    private static boolean isDependency(String lib) {
        return lib.contains(":") && !lib.contains(":\\");
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Utils">
    /////////// Utils ///////////////////////////////////
    private static boolean systemPropertyEmptyOrTrue(String property) {
        final String value = getProperty(property);
        if (value == null)
            return false;
        return value.isEmpty() || Boolean.parseBoolean(value);
    }

    private static String propertyOrEnv(String propName, String envVar) {
        String val = getProperty(propName);
        if (val == null)
            val = emptyToNull(getenv(envVar));
        return val;
    }

    private static String expandCommandLinePath(String str) {
        if (str == null)
            return null;
//        if (isWindows())
//            return str;
//        else
        return str.startsWith("~/") ? str.replace("~", getProperty(PROP_USER_HOME)) : str;
    }

    private static Path toAbsolutePath(Path p) {
        return p != null ? p.toAbsolutePath().normalize() : null;
    }

    private static <C extends Collection<T>, T> C addAllIfAbsent(C c, Collection<T> c1) {
        for (T e : c1) {
            if (!c.contains(e))
                c.add(e);
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : (List<T>) emptyList();
    }

    private static <T extends Collection<?>> T emptyToNull(T c) {
        return (c == null || c.isEmpty()) ? null : c;
    }

    private static String emptyToNull(String s) {
        if (s == null)
            return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static List<String> split(String str, String separator) {
        if (str == null)
            return null;
        final String[] es = str.split(separator);
        final List<String> list = new ArrayList<>(es.length);
        for (String e : es) {
            e = e.trim();
            if (!e.isEmpty())
                list.add(e);
        }
        return list;
    }

    private static <T extends AccessibleObject> T accessible(T obj) {
        if (obj == null)
            return null;
        obj.setAccessible(true);
        return obj;
    }
    //</editor-fold>
}
