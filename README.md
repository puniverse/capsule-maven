# Maven Capsule
[![Build Status](https://travis-ci.org/puniverse/capsule-maven.svg)](https://travis-ci.org/puniverse/capsule-maven) [![Dependency Status](https://www.versioneye.com/user/projects/54fa8f404f3108b7d2000407/badge.svg?style=flat)](https://www.versioneye.com/user/projects/54fa8f404f3108b7d2000407) [![Version](https://img.shields.io/maven-central/v/co.paralleluniverse/capsule-maven.svg?style=flat)](https://github.com/puniverse/capsule-maven/releases) [![License](http://img.shields.io/badge/license-EPL-blue.svg?style=flat)](https://www.eclipse.org/legal/epl-v10.html)

Maven Capsule is a [caplet](https://github.com/puniverse/capsule) that allows the creations of capsules that, instead of embedding their dependencies, download all or some of them from a Maven repository. The dependencies are downloaded, cached locally, and shared among other capsules that also depend on them. In addition, this caplet allows specifying capsule metadata in a POM file in addition to the manifest.

A capsule with the Maven caplet that has all (or almost all) of its dependencies downloaded rather than embedded is known as a "thin" capsule (as opposed to a "fat" capsule, which embeds all of its dependencies). In fact, a capsule may not contain any of the application's classes/JARs at all. Instead, the capsule's manifest may contain these attributes alone (and no files in the capsule JAR besides the manifest):

    Main-Class: Capsule
    Caplets: MavenCapsule
    Application: com.acme:foo

And when the capsule is launched, the newest available version of the application will be downloaded, cached and launched. In this use-case, the capsule JAR looks like this:

    foo.jar
    |__ Capsule.class
    |__ MavenCapsule.class
    |__ capsule/
    |    \__ [Maven capsule classes]
    \__ MANIFEST/
        \__ MANIFEST.MF

Note how none of the application's classes are actually found in the JAR.

The empty Maven capsule ( (i.e., it's manifest has no `Application-Class`, `Application`, `Application-Name` etc.) even lets you launch any capsule or executable JAR stored in a Maven repository like so:

        java -jar capsule.jar com.acme:fooapp

### Applying the Maven Caplet

1. Embed all the Maven capsule files in your JAR (using your build tool).

2. Add `MavenCapsule` to the `Caplets` manifest attribute.

### Performance Impact

The Maven caplet's classes add an extra 1.5MB to the capsule (but hopefully save more by not embedding dependencies). Once the artifacts have been downloaded and cached, resolving them against the local cache adds about 0.5 seconds to the startup time.


### POM Support

This caplet allows specifying capsule metadata in a POM file (in addition to that in the manifest). The POM file must be named `pom.xml` and placed in the capsule's root.

If the `Application-ID` and `Application-Version` attributes are not defined, these, too, will be obtained from the POM file (with the ID being `<group>.<artifact>`, and the version being the version in the POM).

If the `Dependencies` manifest attribute is not defined, those non-optional dependencies declared in the POM file are treated as though they were listed in the `Dependencies` attribute.

### Maven Dependencies

The Maven caplet changes the artifact resolution strategy of the capsule, such that artifacts that are not embedded in the capsule will be downloaded from a Maven repository. This works for any attribute that can specify artifact coordinates, including (but not limited to) `Application`, `Dependencies`, `Native-Dependencies`, `Java-Agents`, and even `Caplets`.

By default, the caplet will look for dependencies on Maven Central. If other repositories are needed (or if you don't want to access Maven Central), the `Repositories` attribute is a space-separated list of Maven repositories formatted as `URL` or `NAME(URL)`. The repositories will be searched in the order they are listed. If the `Repositories` attribute is found in the manifest, then Maven Central will not be searched.

Instead of specifying explicit URLs, the following well-known repository names can be listed in the `Repositories` attribute:

* `central` - Maven central, HTTPS
* `central-http` - Maven central, HTTP
* `jcenter` - jCenter, HTTPS
* `jcenter-http` - jCenter, HTTP
* `local` - Default local Maven repository (`userdir/.m2/repository`). You should only use local repositories in tests.

The caplet also automatically uses the information in Maven's `settings.xml` file to access repositories that require authentication. The `settings.xml` used is the user's settings (in the `.m2` directory in the user's home directory), or in the global settings (in the `conf` subdirectory of the Maven installation). A `settings.xml` file is absolutely not required for the Maven caplet's operation, and is mostly useful when accessing private organizational Maven repositories for dependencies.

Unlike the default capsule, the Maven caplet also pulls transitive dependencies. Exclusions can be given as a comma separated list within parentheses, immediately following the main artifact, of `groupId:artifactId` coordinates, where the artifact can be the wildcard `*`. For example:

    Dependencies : com.esotericsoftware.kryo:kryo:2.23.0(org.ow2.asm:*)

The `CAPSULE_REPOS` environment variable can be set to a *comma-* (`,`) or a whitespace-separated list of Maven repository URLS or well-known repository names (see above), which will be prepended to those specified in the manifest or the POM.

By default, SNAPSHOT dependencies are not allowed, unless the `Allow-Snapshots` is set to `true`.

Maven [version ranges](http://maven.apache.org/enforcer/enforcer-rules/versionRanges.html) (as well as `LATEST` and `RELEASE`) are supported. For example: `Application: com.acme:foo:[1.0,2.0)`. The newest version matching the range (or the newest version if no range is given), will be downloaded, cached and launched. Not specifying version information at all will result in a failure during dependency resolution. If the application's main artifact is a capsule, then all configurations will be taken based on those in the artifact capsule.

### Dependency Caching

Maven artifacts are downloaded the first time the capsule is launched, and placed in the `deps` subdirectory of the Capsule cache, where they are shared among all capsules using the Maven caplet.

### Miscellany

Adding `-Dcapsule.reset=true`, can force a re-download of SNAPSHOT versions.

`-Dcapsule.resolve` will download all dependencies (those that are not cached yet) without launching the capsule.

The command: `java -Dcapsule.tree -jar app.jar`, will print the dependency tree for the capsule, and then quit without launching the app.

Two more system properties affect the way Capsule searches for dependencies. If `capsule.offline` is defined or set to `true` (`-Dcapsule.offline` or `-Dcapsule.offline=true`), Capsule will not attempt to contact online repositories for dependencies (instead, it will use the local Maven repository/cache only). `capsule.local` determines the path for the local Maven repository/cache Capsule will use (which, by default, is the `deps` subdirectory of the Capsule cache).

### Build Tool Utilities

The [Dependencies class](https://github.com/puniverse/capsule-maven/blob/master/src/main/java/capsule/Dependencies.java) contains utility methods that used by build-tool plugins that create capsules.

## Reference

### Manifest Attributes

* `Repositories`: a list of Maven repositories formatted as `URL` or `NAME(URL)`
* `Allow-Snapshots`: If `true`, allows for SNAPSHOT dependencies (default: `false`)
* `Managed-Dependencies`: A list of managed dependencies, forcing versions in transitive dependencies *if* they depend on any of these, each formatted as `group:artifact:type:classifier:version`. Note that the format is different from that of dependencies.

### Actions

Actions are system properties that, if defined, perform an action *other* than launching the application.

* `capsule.tree`: if set, the capsule will print the app's dependency tree, and then quit without launching the app
* `capsule.resolve`: all external dependencies, if any, will be downloaded (if not cached already), and/or the capsule will be extracted if necessary, but the application will not be launched

### System Properties

* `capsule.reset`: if set, forces re-extraction of the capsule, where applies, and/or re-downloading of SNAPSHOT dependencies
* `capsule.offline`: if defined (without a value) or set to `true`, Capsule will not attempt to contact online repositories for dependencies
* `capsule.local`: the path for the local Maven repository; defaults to CAPSULE_CACHE/deps
* `capsule.connect.timeout`: The maximum amount of time (in milliseconds) to wait for a successful connection to a remote repository. Non-positive values indicate no timeout.
* `capsule.request.timeout`: The maximum amount of time (in milliseconds) to wait for remaining data to arrive from a remote repository. Note that this timeout does not restrict the overall duration of a request, it only restricts the duration of inactivity between consecutive data packets. Non-positive values indicate no timeout.


### Environment Variables

* `CAPSULE_REPOS`: sets the list -- comma (`,`) or whitespace separated -- of Maven repositories that the capsule will use; overrides those specified in the manifest or the POM.
* `CAPSULE_CONNECT_TIMEOUT`: The maximum amount of time (in milliseconds) to wait for a successful connection to a remote repository. Non-positive values indicate no timeout.
* `CAPSULE_REQUEST_TIMEOUT`: The maximum amount of time (in milliseconds) to wait for remaining data to arrive from a remote repository. Note that this timeout does not restrict the overall duration of a request, it only restricts the duration of inactivity between consecutive data packets. Non-positive values indicate no timeout.
