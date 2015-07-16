package capsule;

import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.DefaultProxySelector;

/**
 * A proxy selector that uses proxy configuration defined
 * in the system environment configuration and Java
 * system properties.
 *
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class SystemProxySelector implements ProxySelector {

    private static final int LOG_NONE = 0;
    private static final int LOG_QUIET = 1;
    private static final int LOG_VERBOSE = 2;
    private static final int LOG_DEBUG = 3;
    private static final String LOG_PREFIX = "CAPSULE: ";
    private final int logLevel;


    private final DefaultProxySelector target = new DefaultProxySelector();

    private final Map<String,String> env;

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
    public SystemProxySelector( int logLevel ) {
        this( System.getenv(), System.getProperties(), logLevel);
    }

    SystemProxySelector( Map env, Properties props, int logLevel ) {
        this.env = env;
        this.props = props;
        this.logLevel = logLevel;
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


    protected void addProxyFromEnv( String type ) {
        checkValidProtocol(type);

        String key = type + ( isUpper(type) ? "_PROXY" : "_proxy");
        String[] items = parseProxy( env.get(key), "http".equals(type) ? "80":"443" );
        if( items == null ) {
            return;
        }

        String noProxy = env.get( isUpper(type) ? "NO_PROXY" : "no_proxy" );

        Proxy proxy = new Proxy(type.toLowerCase(), items[0], Integer.parseInt(items[1]) );
        target.add(proxy, noProxy);
        count++;

        // dump proxy information to logger
        if( isLogging(LOG_VERBOSE) )
            log(logLevel, String.format("Adding `%s` proxy: %s [from system environment]", type, proxy));

    }

    protected void addProxyFromProperties( String type ) {
        checkValidProtocol(type);

        String host = props.getProperty(type + ".proxyHost");
        if( host == null || host.isEmpty() ) {
            // nothing to do
            return ;
        }

        String port = props.getProperty(type + ".proxyPort");
        if( port == null || port.isEmpty() ) {
            port = "http".equals(type) ? "80" : "443";
        }

        Proxy proxy = new Proxy(type, host, Integer.parseInt(port));
        String nonProxy = props.getProperty(type + ".nonProxyHosts");

        // append this proxy to the target selector
        target.add(proxy, nonProxy);
        count++;

        // dump proxy information to logger
        if( isLogging(LOG_VERBOSE) )
            log(logLevel, String.format("Adding `%s` proxy: %s [from Java system properties]", type, proxy));
    }

    /**
     * Given a proxy URL returns a two element arrays containing the host name and the port
     *
     * @param url The proxy host URL.
     * @param defPort The default proxy port
     * @return An array containing the host name and the proxy port or null when url is empty
     */
    static String[] parseProxy( String url, String defPort ) {
        String[] result = new String[2];

        if( url==null || url.isEmpty() ) {
            return null;
        }

        int p = url.indexOf("://");
        if( p != -1 ) {

            url = url.substring(p+3);
        }

        if( (p=url.indexOf(':')) != -1 ) {
            result[0] = url.substring(0,p);
            result[1] = url.substring(p+1);
        }
        else {
            result[0] = url;
            result[1] = defPort;
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
    public boolean isValid() { return count>0; }

    private boolean isUpper( String str ) {
        for( int i=0; i<str.length(); i++ ) {
            if( Character.isLowerCase(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    protected final boolean isLogging(int level) {
        return level <= logLevel;
    }

    protected final void log(int level, String str) {
        if (isLogging(level))
            System.err.println(LOG_PREFIX + str);
    }

    protected void checkValidProtocol( String protocol ) {
        if( "http".equalsIgnoreCase(protocol) ) return;
        if( "https".equalsIgnoreCase(protocol) ) return;
        throw new IllegalArgumentException(String.format("Illegal proxy protocol: '%s'", protocol));
    }

}
