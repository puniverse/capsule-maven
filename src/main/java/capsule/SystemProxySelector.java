/*
 * Capsule
 * Copyright (c) 2014-2016, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultProxySelector;

import static capsule.DependencyManager.LOG_VERBOSE;

/**
 * A proxy selector that uses proxy configuration defined
 * in the system environment configuration and Java
 * system properties.
 *
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author adrien.lauer@gmail.com
 */
final class SystemProxySelector implements ProxySelector {
    private final DependencyManager dm;
    private final DefaultProxySelector target = new DefaultProxySelector();
    private final Map<String, String> env;
    private final Properties props;
    private int count;

    /**
     * Create a proxy selector object looking for proxy settings in the environment
     * configurations and the Java system properties. It checks the following properties:
     *
     * <li>http.proxyHost (Java system property) </li>
     * <li>https.proxyHost (Java system property) </li>
     * <li>http_proxy (environment variable)</li>
     * <li>https_proxy (environment variable)</li>
     * <li>HTTP_PROXY (environment variable)</li>
     * <li>HTTPS_PROXY (environment variable)</li>
     *
     * @param logLevel Capsule logging level
     */
    public SystemProxySelector(DependencyManager dm) {
        this(System.getenv(), System.getProperties(), dm);
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    SystemProxySelector(Map env, Properties props, DependencyManager dm) {
        this.env = env;
        this.props = props;
        this.dm = dm;
        init();
    }

    @Override
    public Proxy getProxy(RemoteRepository repository) {
        return target.getProxy(repository);
    }

    protected void init() {
        addProxyFromProperties("http");
        addProxyFromProperties("https");

        addProxyFromEnv("http");
        addProxyFromEnv("https");

        addProxyFromEnv("HTTP");
        addProxyFromEnv("HTTPS");
    }

    protected void addProxyFromEnv(String type) {
        checkValidProtocol(type);

        String key = type + (isUpper(type) ? "_PROXY" : "_proxy");
        String[] items = parseProxy(env.get(key), "http".equals(type) ? "80" : "443");
        if (items == null)
            return;

        String[] credentials = parseCredentials(env.get(key));

        String noProxy = env.get(isUpper(type) ? "NO_PROXY" : "no_proxy");

        if (noProxy != null)
            noProxy = noProxy.replace(',', '|');

        Proxy proxy;
        if (credentials != null) {
            AuthenticationBuilder authenticationBuilder = new AuthenticationBuilder();
            authenticationBuilder.addUsername(credentials[0]);
            if (credentials[1] != null)
                authenticationBuilder.addPassword(credentials[1]);

            proxy = new Proxy(type.toLowerCase(), items[0], Integer.parseInt(items[1]), authenticationBuilder.build());
        } else {
            proxy = new Proxy(type.toLowerCase(), items[0], Integer.parseInt(items[1]));
        }
        target.add(proxy, noProxy);
        count++;

        log(LOG_VERBOSE, String.format("Adding `%s` proxy: %s [from system environment]", type, proxy));

    }

    protected void addProxyFromProperties(String type) {
        checkValidProtocol(type);

        String host = props.getProperty(type + ".proxyHost");
        if (host == null || host.isEmpty())
            return; // nothing to do

        String port = props.getProperty(type + ".proxyPort");
        if (port == null || port.isEmpty())
            port = "http".equals(type) ? "80" : "443";

        String nonProxy = props.getProperty(type + ".nonProxyHosts");
        String username = props.getProperty(type + ".proxyUser");
        String password = props.getProperty(type + ".proxyPassword");

        Proxy proxy;
        if (username != null) {
            AuthenticationBuilder authenticationBuilder = new AuthenticationBuilder();
            authenticationBuilder.addUsername(username);
            if (password != null)
                authenticationBuilder.addPassword(password);

            proxy = new Proxy(type, host, Integer.parseInt(port), authenticationBuilder.build());
        } else {
            proxy = new Proxy(type, host, Integer.parseInt(port));
        }
        // append this proxy to the target selector
        target.add(proxy, nonProxy);
        count++;

        log(LOG_VERBOSE, String.format("Adding `%s` proxy: %s [from Java system properties]", type, proxy));
    }

    /**
     * Given a proxy URL returns a two element arrays containing the user name and the password. The second component
     * of the array is null if no password is specified.
     *
     * @param url   The proxy host URL.
     * @return An array containing the user name and the password or null when none are present or the url is empty.
     */
    static String[] parseCredentials(String url) {
        String[] result = new String[2];

        if (url == null || url.isEmpty())
            return null;

        int p = url.indexOf("://");
        if (p != -1)
            url = url.substring(p + 3);

        if ((p = url.indexOf('@')) != -1) {
            String credentials = url.substring(0, p);

            if ((p = credentials.indexOf(':')) != -1) {
                result[0] = credentials.substring(0, p);
                result[1] = credentials.substring(p + 1);
            } else {
                result[0] = credentials;
            }
        } else {
            return null;
        }

        return result;
    }

    /**
     * Given a proxy URL returns a two element arrays containing the host name and the port
     *
     * @param url     The proxy host URL.
     * @param defPort The default proxy port
     * @return An array containing the host name and the proxy port or null when url is empty
     */
    static String[] parseProxy(String url, String defPort) {
        String[] result = new String[2];

        if (url == null || url.isEmpty())
            return null;

        int p = url.indexOf("://");
        if (p != -1)
            url = url.substring(p + 3);

        if ((p = url.indexOf('@')) != -1)
            url = url.substring(p + 1);

        if ((p = url.indexOf(':')) != -1) {
            result[0] = url.substring(0, p);
            result[1] = url.substring(p + 1);
        } else {
            result[0] = url;
            result[1] = defPort;
        }

        // remove trailing slash from the host name
        p = result[0].indexOf("/");
        if( p != -1 ) {
            result[0] = result[0].substring(0,p);
        }

        // remove trailing slash from the port number
        p = result[1].indexOf("/");
        if( p != -1 ) {
            result[1] = result[1].substring(0,p);
        }

        return result;
    }

    /**
     * @return The number of defined proxy
     */
    int getCount() {
        return count;
    }

    /**
     * @return {@code true} if the selector defines at least one proxy server
     */
    public boolean isValid() {
        return count > 0;
    }

    private boolean isUpper(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (Character.isLowerCase(str.charAt(i)))
                return false;
        }
        return true;
    }

    private void log(int level, String str) {
        if (dm != null)
            dm.log(level, str);
    }

    protected void checkValidProtocol(String protocol) {
        if ("http".equalsIgnoreCase(protocol))
            return;
        if ("https".equalsIgnoreCase(protocol))
            return;
        throw new IllegalArgumentException(String.format("Illegal proxy protocol: '%s'", protocol));
    }
}
