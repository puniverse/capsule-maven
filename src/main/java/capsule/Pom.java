/*
 * Capsule
 * Copyright (c) 2014-2016, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.aether.util.artifact.JavaScopes;

public final class Pom {
    private final Model pom;
    private final Pom root;
    private final MavenCapsule capsule;
    private Pom parent;
    private Map<String, Dependency> managedDependencies;

    public Pom(InputStream is, Pom root, MavenCapsule capsule) {
        try {
            this.pom = new MavenXpp3Reader().read(is);
            this.root = root;
            this.capsule = capsule;
        } catch (Exception e) {
            throw new RuntimeException("Error trying to read pom.", e);
        }
    }

//    public PomReader(InputStream is) {
//        this(is, null, null);
//    }
    
    private Pom getParent() {
        if (parent == null)
            parent = resolveParent();
        return parent;
    }

    private Pom resolveParent() {
        if (pom.getParent() == null)
            return null;

        final String group = pom.getParent().getGroupId();
        final String artifactId = pom.getParent().getArtifactId();
        final String version = pom.getParent().getVersion();

        if (root != null
            && Objects.equals(group, root.getGroupId())
            && Objects.equals(artifactId, root.getArtifactId())
            && Objects.equals(version, root.getVersion()))
            return root;

        if (capsule != null) {
            try {
                final String coords = group + ":" + artifactId + ":" + version;
                final List<Path> ps = capsule.lookupAndResolve(coords, "pom");
                if (!ps.isEmpty())
                    return new Pom(Files.newInputStream(ps.get(0)), root, capsule);
            } catch (Exception e) {
                capsule.log1(MavenCapsule.LOG_QUIET1, "Exception while resolving parent " + pom.getParent() + " of pom " + pom + " : " + e.getMessage());
                capsule.log1(MavenCapsule.LOG_VERBOSE1, e);
            }
        }

        return null;
    }

    public String getArtifactId() {
        return pom.getArtifactId();
    }

    public String getGroupId() {
        return pom.getGroupId() != null ? pom.getGroupId() : (pom.getParent() != null ? pom.getParent().getGroupId() : null);
    }

    public String getVersion() {
        return pom.getVersion() != null ? pom.getVersion() : (pom.getParent() != null ? pom.getParent().getVersion() : null);
    }

    public String getId() {
        return pom.getId();
    }

    public Properties getProperties() {
        return pom.getProperties();
    }

    public List<String> getRepositories() {
        final List<Repository> repos = pom.getRepositories();
        if (repos == null)
            return Collections.emptyList();

        final List<String> repositories = new ArrayList<>(repos.size());
        for (Repository repo : repos)
            repositories.add(repoToString(repo));
        return repositories;
    }

    public List<String> getDependencies(String type) {
        final List<Dependency> deps = pom.getDependencies();
        if (deps == null)
            return Collections.emptyList();

        final List<String> dependencies = new ArrayList<>(deps.size());
        for (Dependency dep : deps) {
            if (includeDependency(dep) && type.equals(dep.getType()))
                dependencies.add(resolve(depToString(manageVersion(dep))));
        }
        return dependencies;
    }
    
    public List<String> getManagedDependencies() {
        final Map<String, Dependency> deps = getManagedDependencies0();
        if (deps == null)
            return Collections.emptyList();

        final List<String> dependencies = new ArrayList<>(deps.size());
        for (Dependency dep : deps.values())
            dependencies.add(resolve(depManagement2coords(dep)));
        return dependencies;
    }
    
    private Map<String, Dependency> getManagedDependencies0() {
        if (managedDependencies == null) {
            managedDependencies = new HashMap<>();
            if (getParent() != null)
                managedDependencies.putAll(getParent().getManagedDependencies0());
            
            if (pom.getDependencyManagement() != null) {
                for (Dependency d : pom.getDependencyManagement().getDependencies())
                    managedDependencies.put(d.getManagementKey(), d);
            }
        } 
        return managedDependencies;
    }

    /**
     * Applies dependency management to dependency versions
     */
    private Dependency manageVersion(Dependency dep) {
        if (dep.getVersion() != null)
            return dep;
        
        final Dependency md = getManagedDependencies0().get(dep.getManagementKey());
        if (md != null)
            dep.setVersion(md.getVersion());

        return dep;
    }

    private static boolean includeDependency(Dependency dep) {
        if (dep.isOptional())
            return false;
        if (dep.getScope() == null || dep.getScope().isEmpty())
            return true;
        switch (dep.getScope().toLowerCase()) {
            case JavaScopes.COMPILE:
            case JavaScopes.RUNTIME:
                return true;
            default:
                return false;
        }
    }

    private static String depToString(Dependency dep) {
        return dep2coords(dep) + exclusions2desc(dep);
    }

    private static String dep2coords(Dependency dep) {
        return dep.getGroupId() + ":" + dep.getArtifactId()
               + ":" + (dep.getVersion() != null ? dep.getVersion() : "")
               + (dep.getClassifier() != null && !dep.getClassifier().isEmpty() ? ":" + dep.getClassifier() : "");
    }

    private static String exclusions2desc(Dependency dep) {
        List<Exclusion> exclusions = dep.getExclusions();
        if (exclusions == null || exclusions.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Exclusion ex : exclusions)
            sb.append(exclusion2coord(ex)).append(',');
        sb.delete(sb.length() - 1, sb.length());
        sb.append(')');

        return sb.toString();
    }

    private static String exclusion2coord(Exclusion ex) {
        return ex.getGroupId() + ":" + ex.getArtifactId();
    }

    private static String depManagement2coords(Dependency dep) {
        return dep.getGroupId() + ":" + dep.getArtifactId()
               + ":" + (dep.getType() != null ? dep.getType() : "")
               + ":" + (dep.getClassifier() != null ? dep.getClassifier() : "")
               + ":" + (dep.getVersion() != null ? dep.getVersion() : "");
    }

    private static String repoToString(Repository repo) {
        if (repo.getId() != null && !repo.getId().isEmpty())
            return repo.getId() + "(" + repo.getUrl() + ")";
        return repo.getUrl();
    }

    private String resolve(String s) {
        if (s == null)
            return null;

        final Properties ps = getProperties();
        String ret = s;
        if (getGroupId() != null)
            ret = ret.replace("${project.groupId}", getGroupId()).replace("${pom.groupId}", getGroupId());
        if (getVersion() != null)
            ret = ret
                    .replace("${project.version}", getVersion())
                    .replace("${pom.version}", getVersion())
                    .replace("${version}", getVersion());
        for (String pName : ps.stringPropertyNames())
            ret = ret.replace("${" + pName + "}", ps.getProperty(pName));
        return ret;
    }
}
