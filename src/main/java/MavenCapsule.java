/*
 * Copyright (c) 2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManager;
import capsule.DependencyManagerImpl;
import capsule.PomReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import java.util.List;

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

    private static final String ATTR_REPOSITORIES = ATTRIBUTE("Repositories", "central", true, "A list of Maven repositories, each formatted as URL or NAME(URL)");
    private static final String ATTR_ALLOW_SNAPSHOTS = ATTRIBUTE("Allow-Snapshots", "false", true, "Whether or not SNAPSHOT dependencies are allowed");
    private static final String ATTR_APP_NAME = "Application-Name";
    private static final String ATTR_APP_ARTIFACT = "Application";
    private static final String ATTR_APP_CLASS_PATH = "App-Class-Path";
    private static final String ATTR_BOOT_CLASS_PATH = "Boot-Class-Path";
    private static final String ATTR_BOOT_CLASS_PATH_A = "Boot-Class-Path-A";
    private static final String ATTR_BOOT_CLASS_PATH_P = "Boot-Class-Path-P";
    private static final String ATTR_JAVA_AGENTS = "Java-Agents";
    private static final String ATTR_NATIVE_AGENTS = "Native-Agents";

    private static final String ENV_CAPSULE_REPOS = "CAPSULE_REPOS";
    private static final String ENV_CAPSULE_LOCAL_REPO = "CAPSULE_LOCAL_REPO";

    private static final String POM_FILE = "pom.xml";
    private static final String DEPS_CACHE_NAME = "deps";

    private DependencyManager dependencyManager;
    private PomReader pom;
    private Path localRepo;

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
            setDependencyRepositories(getRepositories());

        super.finalizeCapsule();
    }

    //<editor-fold defaultstate="collapsed" desc="Main Operations">
    /////////// Main Operations ///////////////////////////////////
    void printDependencyTree(List<String> args) {
        verifyNonEmpty("Cannot print dependencies of a wrapper capsule.");
        System.out.println("Dependencies for " + getAppId());

        if (hasAttribute(ATTR_APP_ARTIFACT)) {
            final String appArtifact = getAttribute(ATTR_APP_ARTIFACT);
            if (appArtifact == null)
                throw new IllegalStateException("capsule " + getJarFile() + " has nothing to run");
            getDependencyManager().printDependencyTree(appArtifact, "jar", System.out);
        } else {
            final List<String> deps = getAllDependencies();
            if (deps.isEmpty())
                System.out.println("No external dependencies.");
            else
                getDependencyManager().printDependencyTree(deps, "jar", System.out);
        }

        final List<String> nativeDeps = getAllNativeDependencies();
        if (!nativeDeps.isEmpty()) {
            System.out.println("\nNative Dependencies:");
            getDependencyManager().printDependencyTree(nativeDeps, getNativeLibExtension(), System.out);
        }
    }

    void resolve(List<String> args) throws IOException, InterruptedException {
        verifyNonEmpty("Cannot resolve a wrapper capsule.");

        final List<String> deps = new ArrayList<>();
        deps.add(ATTR_APP_ARTIFACT);
        addAllIfNotContained(deps, getAllDependencies());
        resolveDependencies(deps, "jar");

        resolveDependencies(getAllNativeDependencies(), getNativeLibExtension());

        log(LOG_QUIET, "Capsule resolved");
    }

    private void verifyNonEmpty(String message) {
        if (isEmptyCapsule())
            throw new IllegalArgumentException(message);
    }

    private List<String> getAllDependencies() {
        final List<String> deps = new ArrayList<>();
        for (List<String> xs : asList(getDependencies(),
                getListAttribute(ATTR_APP_CLASS_PATH),
                getListAttribute(ATTR_BOOT_CLASS_PATH),
                getListAttribute(ATTR_BOOT_CLASS_PATH_P),
                getListAttribute(ATTR_BOOT_CLASS_PATH_A),
                getListAttribute(ATTR_JAVA_AGENTS),
                getListAttribute(ATTR_NATIVE_AGENTS)))
            addAllIfNotContained(deps, nullToEmpty(filterArtifacts(xs)));

        return deps;
    }

    private List<String> getAllNativeDependencies() {
        final List<String> deps = new ArrayList<>();

        final List<String> depsAndRename = getNativeDependencies();
        if (depsAndRename != null && !depsAndRename.isEmpty()) {
            for (String x : depsAndRename) {
                final String dep = x.split(",")[0].trim();
                if (isDependency(dep))
                    deps.add(x);
            }
        }

        return deps;
    }
    //</editor-fold>

    @Override
    protected String[] buildAppId() {
        String name;
        String version = null;

        name = getAttribute(ATTR_APP_NAME);

        if (name == null) {
            final String appArtifact = getAttribute(ATTR_APP_ARTIFACT);
            if (appArtifact != null) {
                if (isDependency(appArtifact)) {
                    @SuppressWarnings("deprecation")
                    final String[] nameAndVersion = getAppArtifactId(getDependencyManager().getLatestVersion(appArtifact, "jar"));
                    name = nameAndVersion[0];
                    version = nameAndVersion[1];
                } else
                    return null;
            }
        }
        if (name == null) {
            if (pom != null) {
                final String[] nameAndVersion = getPomAppNameAndVersion();
                name = nameAndVersion[0];
                version = nameAndVersion[1];
            }
        }

        if (name == null)
            return super.buildAppId();

        return new String[]{name, version};
    }

    @Override
    @SuppressWarnings("deprecation")
    protected List<Path> resolveDependencies(List<String> dependencies, String type) {
        if (dependencies == null)
            return null;
        final List<Path> res = getDependencyManager().resolveDependencies(dependencies, type);
        return res;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected List<Path> resolveDependency(String coords, String type) {
        if (coords == null)
            return null;
        List<Path> res;
        res = super.resolveDependency(coords, type);
        if (res == null || res.isEmpty())
            res = getDependencyManager().resolveDependency(coords, type);
        return res;
    }

    @Override
    protected List<String> getDependencies() {
        List<String> deps = super.getDependencies();
        if ((deps == null || deps.isEmpty()) && pom != null)
            deps = pom.getDependencies();

        return (deps != null && !deps.isEmpty()) ? unmodifiableList(deps) : null;
    }

    private List<String> getRepositories() {
        final List<String> repos = new ArrayList<String>();
        repos.addAll(split(getenv(ENV_CAPSULE_REPOS), "[,\\s]\\s*"));
        repos.addAll(getListAttribute(ATTR_REPOSITORIES));
        if (pom != null)
            addAllIfNotContained(repos, nullToEmpty(pom.getRepositories()));

        return !repos.isEmpty() ? unmodifiableList(repos) : null;
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
                setDependencyRepositories(getRepositories());
        }
        return dependencyManager;
    }

    private DependencyManager createDependencyManager() {
        final boolean reset = systemPropertyEmptyOrTrue(PROP_RESET);
        return createDependencyManager(getLocalRepo().toAbsolutePath(), reset, getLogLevel());
    }

    /**
     * @deprecated marked deprecated to exclude from javadoc.
     */
    protected DependencyManager createDependencyManager(Path localRepo, boolean reset, int logLevel) {
        return new DependencyManagerImpl(localRepo, reset, logLevel);
    }

    private void setDependencyRepositories(List<String> repositories) {
        getDependencyManager().setRepos(repositories, Boolean.parseBoolean(getAttribute(ATTR_ALLOW_SNAPSHOTS)));
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
                        e.printStackTrace(System.err);
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

    private static List<String> filterArtifacts(List<String> xs) {
        if (xs == null)
            return null;
        final List<String> res = new ArrayList<>();
        for (String x : xs) {
            if (isDependency(x))
                res.add(x);
        }
        return res;
    }

    private PomReader createPomReader() {
        try (InputStream is = getEntryInputStream(getJarFile(), POM_FILE)) {
            return is != null ? new PomReader(is) : null;
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + POM_FILE, e);
        }
    }

    private String[] getPomAppNameAndVersion() {
        return new String[]{pom.getGroupId() + "_" + pom.getArtifactId(), pom.getVersion()};
    }

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

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : (List<T>) emptyList();
    }

    private static <C extends Collection<T>, T> C addAllIfNotContained(C c, Collection<T> c1) {
        for (T e : c1) {
            if (!c.contains(e))
                c.add(e);
        }
        return c;
    }

    private static String emptyToNull(String s) {
        if (s == null)
            return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
    //</editor-fold>
}
