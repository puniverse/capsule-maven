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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public final class PomReader {
    private final Model pom;

    public PomReader(InputStream is) {
        try {
            this.pom = new MavenXpp3Reader().read(is);
        } catch (Exception e) {
            throw new RuntimeException("Error trying to read pom.", e);
        }
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
            repositories.add(convert(repo));
        return repositories;
    }

    public List<String> getDependencies(String type) {
        final List<Dependency> deps = pom.getDependencies();
        if (deps == null)
            return Collections.emptyList();

        final List<String> dependencies = new ArrayList<>(deps.size());
        for (Dependency dep : deps) {
            if (includeDependency(dep) && type.equals(dep.getType()))
                dependencies.add(convert(dep));
        }
        return dependencies;
    }

    private static boolean includeDependency(Dependency dep) {
        if (dep.isOptional())
            return false;
        if (dep.getScope() == null || dep.getScope().isEmpty())
            return true;
        switch (dep.getScope().toLowerCase()) {
            case "compile":
            case "runtime":
                return true;
            default:
                return false;
        }
    }

    private static String convert(Dependency dep) {
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

    private static String convert(Repository repo) {
        if (repo.getId() != null && !repo.getId().isEmpty())
            return repo.getId() + "(" + repo.getUrl() + ")";
        return repo.getUrl();
    }

    public String resolve(String s) {
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
