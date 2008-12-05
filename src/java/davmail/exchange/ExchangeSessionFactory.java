package davmail.exchange;

import davmail.Settings;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Create ExchangeSession instances.
 */
public class ExchangeSessionFactory {
    private static final Object LOCK = new Object();
    private static final Map<PoolKey, ExchangeSessionStack> poolMap = new HashMap<PoolKey, ExchangeSessionStack>();

    static class PoolKey {
        public String url;
        public String userName;
        public String password;

        public PoolKey(String url, String userName, String password) {
            this.url = url;
            this.userName = userName;
            this.password = password;
        }

        @Override
        public boolean equals(Object object) {
            return object == this ||
                    object instanceof PoolKey &&
                            ((PoolKey) object).url.equals(this.url) &&
                            ((PoolKey) object).userName.equals(this.userName) &&
                            ((PoolKey) object).password.equals(this.password);
        }

        @Override
        public int hashCode() {
            return url.hashCode() + userName.hashCode() + password.hashCode();
        }
    }

    static class ExchangeSessionStack extends Stack<ExchangeSession> {
        // 15 minutes expire delay
        protected static final long EXPIRE_DELAY = 1000 * 60 * 15;
        protected long timestamp = System.currentTimeMillis();

        @Override
        public ExchangeSession pop() throws EmptyStackException {
            timestamp = System.currentTimeMillis();
            return super.pop();
        }

        @Override
        public ExchangeSession push(ExchangeSession session) {
            timestamp = System.currentTimeMillis();
            return super.push(session);
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > EXPIRE_DELAY;
        }

        public void clean() {
            while (!isEmpty()) {
                pop().close();
            }
        }
    }

    private ExchangeSessionFactory() {
    }

    /**
     * Create authenticated Exchange session
     *
     * @param userName user login
     * @param password user password
     * @return authenticated session
     * @throws java.io.IOException on error
     */
    public static ExchangeSession getInstance(String userName, String password) throws IOException {
        try {
            String baseUrl = Settings.getProperty("davmail.url");
            PoolKey poolKey = new PoolKey(baseUrl, userName, password);

            ExchangeSession session = null;
            synchronized (LOCK) {
                Stack<ExchangeSession> sessionStack = poolMap.get(poolKey);
                if (sessionStack != null && !sessionStack.isEmpty()) {
                    session = sessionStack.pop();
                    ExchangeSession.LOGGER.debug("Got session " + session + " from pool");
                }
            }

            if (session != null && session.isExpired()) {
                ExchangeSession.LOGGER.debug("Session " + session + " expired");
                session.close();
                session = null;
            }

            if (session == null) {
                session = new ExchangeSession(poolKey);
                session.login();
                ExchangeSession.LOGGER.debug("Created new session: " + session);
            }
            return session;
        } catch (IOException e) {
            if (checkNetwork()) {
                throw e;
            } else {
                throw new NetworkDownException("All network interfaces down !");
            }
        }
    }

    /**
     * Close (or pool) session.
     *
     * @param session exchange session
     */
    public static void close(ExchangeSession session) {
        synchronized (LOCK) {
            if (session != null) {
                PoolKey poolKey = session.getPoolKey();
                ExchangeSessionStack sessionStack = poolMap.get(poolKey);
                if (sessionStack == null) {
                    sessionStack = new ExchangeSessionStack();
                    poolMap.put(poolKey, sessionStack);
                }
                // keep httpClient, but close HTTP connection
                session.close();
                sessionStack.push(session);
                ExchangeSession.LOGGER.debug("Pooled session: " + session);
            }

            // clean pool
            List<PoolKey> toDeleteKeys = new ArrayList<PoolKey>();
            for (Map.Entry<PoolKey, ExchangeSessionStack> entry : poolMap.entrySet()) {
                if (entry.getValue().isExpired()) {
                    ExchangeSession.LOGGER.debug("Session pool for " + entry.getKey().userName + " expired");
                    entry.getValue().clean();
                    toDeleteKeys.add(entry.getKey());
                }
            }
            for (PoolKey toDeleteKey : toDeleteKeys) {
                poolMap.remove(toDeleteKey);
            }
        }
    }


    public static void checkConfig() throws IOException {
        String url = Settings.getProperty("davmail.url");
        HttpClient httpClient = DavGatewayHttpClientFacade.getInstance();
        HttpMethod testMethod = new GetMethod(url);
        try {
            // get webmail root url (will not follow redirects)
            testMethod.setFollowRedirects(false);
            testMethod.setDoAuthentication(false);
            int status = httpClient.executeMethod(testMethod);
            ExchangeSession.LOGGER.debug("Test configuration status: " + status);
            if (status != HttpStatus.SC_OK && status != HttpStatus.SC_UNAUTHORIZED
                    && status != HttpStatus.SC_MOVED_TEMPORARILY) {
                throw new IOException("Unable to connect to OWA at " + url + ", status code " +
                        status + ", check configuration");
            }

        } catch (UnknownHostException exc) {
            String message = "DavMail configuration exception: \n";
            if (checkNetwork()) {
                message += "Unknown host " + exc.getMessage();
                ExchangeSession.LOGGER.error(message, exc);
                throw new IOException(message);
            } else {
                message += "All network interfaces down !";
                ExchangeSession.LOGGER.error(message, exc);
                throw new NetworkDownException(message);
            }

        } catch (NetworkDownException exc) {
            throw exc;
        } catch (Exception exc) {
            ExchangeSession.LOGGER.error("DavMail configuration exception: \n" + exc.getMessage(), exc);
            throw new IOException("DavMail configuration exception: \n" + exc.getMessage());
        } finally {
            testMethod.releaseConnection();
        }

    }

    /**
     * Check if at least one network interface is up and active (i.e. has an address)
     *
     * @return true if network available
     */
    protected static boolean checkNetwork() {
        boolean up = false;
        Enumeration<NetworkInterface> enumeration;
        try {
            enumeration = NetworkInterface.getNetworkInterfaces();
            while (!up && enumeration.hasMoreElements()) {
                NetworkInterface networkInterface = enumeration.nextElement();
                up = networkInterface.isUp() && !networkInterface.isLoopback()
                        && networkInterface.getInetAddresses().hasMoreElements();
            }
        } catch (NoSuchMethodError error) {
            ExchangeSession.LOGGER.debug("Unable to test network interfaces (not available under Java 1.5)");
            up = true;
        } catch (SocketException exc) {
            ExchangeSession.LOGGER.error("DavMail configuration exception: \n Error listing network interfaces " + exc.getMessage(), exc);
        }
        return up;
    }
}
