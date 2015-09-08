package capsule;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author adrien.lauer@gmail.com
 */
public class SystemProxySelectorTest {

    @Test
    public void testProxyWithEnvProperties() {
        Map<String, String> env = new HashMap<>();
        env.put("http_proxy", "my.proxy.com:8080");
        env.put("https_proxy", "secure.proxy.com:8888");

        SystemProxySelector selector = new SystemProxySelector(env, new Properties(), 2);
        assertEquals(2, selector.getCount());

        RemoteRepository repo = new RemoteRepository.Builder("bar", null, "https://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        Proxy proxy = selector.getProxy(repo);
        assertEquals("https", proxy.getType());
        assertEquals("secure.proxy.com", proxy.getHost());
        assertEquals(8888, proxy.getPort());

        repo = new RemoteRepository.Builder("bar", null, "http://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        proxy = selector.getProxy(repo);
        assertEquals("http", proxy.getType());
        assertEquals("my.proxy.com", proxy.getHost());
        assertEquals(8080, proxy.getPort());
    }

    @Test
    public void testProxyWithEnvPropertiesAndDefaultPorts() {
        Map<String, String> env = new HashMap<>();
        env.put("http_proxy", "http://my.proxy.com");
        env.put("https_proxy", "https://secure.proxy.com");

        SystemProxySelector selector = new SystemProxySelector(env, new Properties(), 0);
        assertEquals(2, selector.getCount());

        RemoteRepository repo = new RemoteRepository.Builder("bar", null, "https://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        Proxy proxy = selector.getProxy(repo);
        assertEquals("https", proxy.getType());
        assertEquals("secure.proxy.com", proxy.getHost());
        assertEquals(443, proxy.getPort());

        repo = new RemoteRepository.Builder("bar", null, "http://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        proxy = selector.getProxy(repo);
        assertEquals("http", proxy.getType());
        assertEquals("my.proxy.com", proxy.getHost());
        assertEquals(80, proxy.getPort());
    }

    @Test
    public void testProxyWithEnvPropertiesAndNoProxyHosts() {
        Map<String, String> env = new HashMap<>();
        env.put("http_proxy", "http://my.proxy.com");
        env.put("https_proxy", "https://secure.proxy.com");
        env.put("no_proxy", "*.foo.com|localhost");

        SystemProxySelector selector = new SystemProxySelector(env, new Properties(), 2);
        assertEquals(2, selector.getCount());

        RemoteRepository repo = new RemoteRepository.Builder("bar", null, "http://localhost/service/foo-bar-0.14.3.jar").build();
        Proxy proxy = selector.getProxy(repo);
        assertNull(proxy);

        RemoteRepository repo2 = new RemoteRepository.Builder("bar", null, "https://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        Proxy proxy2 = selector.getProxy(repo2);
        assertEquals("https", proxy2.getType());
        assertEquals("secure.proxy.com", proxy2.getHost());
        assertEquals(443, proxy2.getPort());
    }

    @Test
    public void testProxyWithEnvPropertiesAndCredentials() {
        Map<String, String> env = new HashMap<>();
        env.put("https_proxy", "https://user:password@secure.proxy.com");

        SystemProxySelector selector = new SystemProxySelector(env, new Properties(), 2);
        assertEquals(1, selector.getCount());

        RemoteRepository repo = new RemoteRepository.Builder("bar", null, "https://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        Proxy proxy = selector.getProxy(repo);
        assertEquals("https", proxy.getType());
        assertEquals("secure.proxy.com", proxy.getHost());
        assertEquals(443, proxy.getPort());
        assertNotNull(proxy.getAuthentication());
        assertEquals("username=user, password=***", proxy.getAuthentication().toString()); // no way to retrieve password

    }

    @Test
    public void testProxyWithSystemPropertiesAndCredentials() {
        Properties props = new Properties();
        props.setProperty("https.proxyHost", "secure.host.name");
        props.setProperty("https.proxyUser", "user");
        props.setProperty("https.proxyPassword", "password");

        SystemProxySelector selector = new SystemProxySelector(new HashMap(), props, 0);
        assertEquals(1, selector.getCount());

        RemoteRepository repo = new RemoteRepository.Builder("bar", null, "https://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        Proxy proxy = selector.getProxy(repo);
        assertEquals("https", proxy.getType());
        assertEquals("secure.host.name", proxy.getHost());
        assertEquals(443, proxy.getPort());
        assertNotNull(proxy.getAuthentication());
        assertEquals("username=user, password=***", proxy.getAuthentication().toString()); // no way to retrieve password
    }

    @Test
    public void testProxyWithSystemPropertiesAndDefaultPorts() {
        Properties props = new Properties();
        props.setProperty("http.proxyHost", "foo.host.name");
        props.setProperty("https.proxyHost", "secure.host.name");

        SystemProxySelector selector = new SystemProxySelector(new HashMap(), props, 0);
        assertEquals(2, selector.getCount());

        RemoteRepository repo = new RemoteRepository.Builder("bar", null, "https://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        Proxy proxy = selector.getProxy(repo);
        assertEquals("https", proxy.getType());
        assertEquals("secure.host.name", proxy.getHost());
        assertEquals(443, proxy.getPort());

        repo = new RemoteRepository.Builder("bar", null, "http://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        proxy = selector.getProxy(repo);
        assertEquals("http", proxy.getType());
        assertEquals("foo.host.name", proxy.getHost());
        assertEquals(80, proxy.getPort());
    }

    @Test
    public void testProxyWithSystemPropertiesAndCustomPorts() {
        // environment conf
        Map<String, String> env = new HashMap<>();
        env.put("http_proxy", "env1.proxy.com:8080");
        env.put("https_proxy", "env2.secure.com:8888");

        // system properties
        Properties props = new Properties();
        props.setProperty("http.proxyHost", "prop1.host.name");
        props.setProperty("http.proxyPort", "8081");
        props.setProperty("https.proxyHost", "prop2.secure.name");
        props.setProperty("https.proxyPort", "9091");

        // it must use the proxy defined in the system properties
        // because it is supposed to override the one defined with environment variables
        SystemProxySelector selector = new SystemProxySelector(env, props, 2);
        assertEquals(4, selector.getCount());

        RemoteRepository repo = new RemoteRepository.Builder("bar", null, "https://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        Proxy proxy = selector.getProxy(repo);
        assertEquals("https", proxy.getType());
        assertEquals("prop2.secure.name", proxy.getHost());
        assertEquals(9091, proxy.getPort());

        repo = new RemoteRepository.Builder("bar", null, "http://oss.sonatype.org/service/foo-bar-0.14.3.jar").build();
        proxy = selector.getProxy(repo);
        assertEquals("http", proxy.getType());
        assertEquals("prop1.host.name", proxy.getHost());
        assertEquals(8081, proxy.getPort());
    }

    @Test
    public void testParseProxy() {
        assertArrayEquals(new String[]{"foo.bar.org", "81"}, SystemProxySelector.parseProxy("foo.bar.org", "81"));
        assertArrayEquals(new String[]{"foo.bar.org", "8080"}, SystemProxySelector.parseProxy("foo.bar.org:8080", "91"));
        assertArrayEquals(new String[]{"foo.com", "81"}, SystemProxySelector.parseProxy("http://foo.com", "81"));
        assertArrayEquals(new String[]{"foo.com", "8080"}, SystemProxySelector.parseProxy("http://foo.com:8080", "81"));
        assertArrayEquals(new String[]{"foo.com", "81"}, SystemProxySelector.parseProxy("http://user:password@foo.com", "81"));
        assertArrayEquals(new String[]{"foo.com", "8080"}, SystemProxySelector.parseProxy("http://user:password@foo.com:8080", "81"));
    }

    @Test
    public void testParseCredentials() {
        assertNull(SystemProxySelector.parseCredentials("foo.bar.org"));
        assertNull(SystemProxySelector.parseCredentials("foo.bar.org:8080"));
        assertNull(SystemProxySelector.parseCredentials("http://foo.com"));
        assertNull(SystemProxySelector.parseCredentials("http://foo.com:8080"));
        assertArrayEquals(new String[]{"user", "password"}, SystemProxySelector.parseCredentials("user:password@foo.com"));
        assertArrayEquals(new String[]{"user", "password"}, SystemProxySelector.parseCredentials("user:password@foo.com:8080"));
        assertArrayEquals(new String[]{"user", "password"}, SystemProxySelector.parseCredentials("http://user:password@foo.com"));
        assertArrayEquals(new String[]{"user", "password"}, SystemProxySelector.parseCredentials("http://user:password@foo.com:8080"));
        assertArrayEquals(new String[]{"user", null}, SystemProxySelector.parseCredentials("http://user@foo.com:8080"));
    }
}
