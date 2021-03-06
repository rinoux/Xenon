/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.xenon.adaptors.shared.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.sshd.agent.local.ProxyAgentFactory;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.DefaultConfigFileHostEntryResolver;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;

import nl.esciencecenter.xenon.InvalidCredentialException;
import nl.esciencecenter.xenon.InvalidLocationException;
import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.XenonPropertyDescription;
import nl.esciencecenter.xenon.credentials.CertificateCredential;
import nl.esciencecenter.xenon.credentials.Credential;
import nl.esciencecenter.xenon.credentials.DefaultCredential;
import nl.esciencecenter.xenon.credentials.PasswordCredential;

public class SSHUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSHUtil.class);

    public static final int DEFAULT_SSH_PORT = 22;

    static class PasswordProvider implements FilePasswordProvider {

        private final char[] password;

        public PasswordProvider(char[] password) {
            this.password = password.clone();
        }

        @Override
        public String getPassword(String resourceKey) throws IOException {
            return new String(password);
        }
    }

    /**
     * Create a new {@link SshClient} with a default configuration similar to a stand-alone SSH client.
     * <p>
     * The default configuration loads the SSH config file, uses strict host key checking, and adds unseen hosts keys to the known_hosts file.
     * </p>
     *
     * @return the configured {@link SshClient}
     **/
    public static SshClient createSSHClient() {
        return createSSHClient(true, true, true, false, false);
    }

    /**
     * Create a new {@link SshClient} with the desired configuration.
     * <p>
     * SSH clients have a significant number of options. This method will create a <code>SshClient</code> providing the most important settings.
     * </p>
     *
     * @param loadSSHConfig
     *            Load the SSH config file in the default location (for OpenSSH this is typically found in $HOME/.ssh/config).
     * @param stricHostCheck
     *            Perform a strict host key check. When setting up a connection, the key presented by the server is compared to the default known_hosts file
     *            (for OpenSSH this is typically found in $HOME/.ssh/known_hosts).
     * @param addHostKey
     *            When setting up a connection, add a previously unknown server server key to the default known_hosts file (for OpenSSH this is typically found
     *            in $HOME/.ssh/known_hosts).
     * @param useSSHAgent
     *            When setting up a connection, handoff authentication to a separate SSH agent process.
     * @param useAgentForwarding
     *            Support agent forwarding, allowing remote SSH servers to use the local SSH agent process to authenticate connections to other servers.
     * @return the configured {@link SshClient}
     */
    public static SshClient createSSHClient(boolean loadSSHConfig, boolean stricHostCheck, boolean addHostKey, boolean useSSHAgent,
            boolean useAgentForwarding) {

        SshClient client = SshClient.setUpDefaultClient();

        if (stricHostCheck) {
            if (addHostKey) {
                client.setServerKeyVerifier(new DefaultKnownHostsServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE, true));
                // client.setServerKeyVerifier(new KnownHostsServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE, null));
            } else {
                client.setServerKeyVerifier(new DefaultKnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, true));
                // client.setServerKeyVerifier(new KnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, null));
            }
        } else {
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        }

        if (loadSSHConfig) {
            client.setHostConfigEntryResolver(DefaultConfigFileHostEntryResolver.INSTANCE);
        }

        if (useSSHAgent) {
            client.setAgentFactory(new ProxyAgentFactory());
        }

        if (useAgentForwarding) {
            // Enable ssh-agent-forwarding
            LOGGER.debug("(UNIMPLEMENTED) Enabling ssh-agent-forwarding");
        }

        client.start();

        return client;
    }

    /**
     * Weak validation of a host string containing either a hostame of IP adres.
     *
     * @param adaptorName
     *            the name of the adaptor using this method.
     * @param host
     *            the hostname to validate
     * @return the value of <code>host</code> if the validation succeeded.
     * @throws InvalidLocationException
     *             if the validation failed
     */
    public static String validateHost(String adaptorName, String host) throws InvalidLocationException {
        if (host == null || host.isEmpty()) {
            throw new InvalidLocationException(adaptorName, "Failed to parse host: " + host);
        }

        return host;
    }

    public static String getHost(String adaptorName, String location) throws InvalidLocationException {
        // Parse locations of the format: hostname[:port] and return the host
        try {
            return validateHost(adaptorName, HostAndPort.fromString(location).getHostText().trim());
        } catch (Exception e) {
            // TODO: could be a name in ssh_config instead ??
            throw new InvalidLocationException(adaptorName, "Failed to parse location: " + location);
        }
    }

    public static int getPort(String adaptorName, String location) throws InvalidLocationException {
        // Parse locations of the format: hostname[:port] and return the host
        try {
            return HostAndPort.fromString(location).getPortOrDefault(DEFAULT_SSH_PORT);
        } catch (Exception e) {
            // TODO: could be a name in ssh_config instead ??
            throw new InvalidLocationException(adaptorName, "Failed to parse location: " + location);
        }
    }

    /**
     * Connect an existing {@link SshClient} to the server at <code>location</code> and authenticate using the given <code>credential</code>.
     *
     * @param adaptorName
     *            the adaptor where this method was called from.
     * @param client
     *            the client to connect.
     * @param location
     *            the server to connect to
     * @param credential
     *            the credential to authenticate with.
     * @param timeout
     *            the timeout to use in connection setup (in milliseconds).
     * @return the connected {@link ClientSession}
     * @throws XenonException
     *             if the connection setup or authentication failed.
     */
    public static ClientSession connect(String adaptorName, SshClient client, String location, Credential credential, long timeout) throws XenonException {

        // location should be hostname or hostname:port. If port unset it
        // defaults to port 22

        // Credential may be DEFAULT with username, username/password or
        // username / certificate / passphrase.

        // TODO: Add option DEFAULT with password ?

        if (credential == null) {
            throw new IllegalArgumentException("Credential may not be null");
        }

        if (timeout <= 0) {
            throw new IllegalArgumentException("Invalid timeout: " + timeout);
        }

        String username = credential.getUsername();

        // TODO: Are there cases where we do not want a user name ?
        if (username == null) {
            throw new XenonException(adaptorName, "Failed to retrieve username from credential");
        }

        URI uri;

        try {
            uri = new URI("sftp://" + location);
        } catch (Exception e) {
            throw new InvalidLocationException(adaptorName, "Failed to parse location: " + location, e);
        }

        String host = uri.getHost();
        int port = uri.getPort();

        if (port == -1) {
            port = DEFAULT_SSH_PORT;
        }

        // String host = getHost(adaptorName, location);
        // int port = getPort(adaptorName, location);

        ClientSession session = null;

        try {
            // Connect to remote machine and retrieve a session. Will throw
            // exception on timeout
            session = client.connect(username, host, port).verify(timeout).getSession();
        } catch (IOException e) {
            throw new XenonException(adaptorName, "Connection setup timeout: " + host + ":" + port, e);
        }

        // Figure out which type of credential we are using
        if (credential instanceof DefaultCredential) {
            // do nothing

        } else if (credential instanceof CertificateCredential) {

            CertificateCredential c = (CertificateCredential) credential;

            String certfile = c.getCertificateFile();

            Path path = Paths.get(certfile).toAbsolutePath().normalize();

            if (!Files.exists(path)) {
                throw new CertificateNotFoundException(adaptorName, "Certificate not found: " + path);
            }

            KeyPair pair = null;

            try (InputStream inputStream = Files.newInputStream(path, StandardOpenOption.READ)) {

                char[] password = c.getPassword();

                if (password.length == 0) {
                    pair = SecurityUtils.loadKeyPairIdentity(path.toString(), inputStream, null);
                } else {
                    pair = SecurityUtils.loadKeyPairIdentity(path.toString(), inputStream, new PasswordProvider(password));

                }

            } catch (Exception e) {
                throw new XenonException(adaptorName, "Failed to load certificate: " + path, e);
            }

            session.addPublicKeyIdentity(pair);

        } else if (credential instanceof PasswordCredential) {
            PasswordCredential c = (PasswordCredential) credential;
            session.addPasswordIdentity(new String(c.getPassword()));

        } else {
            throw new InvalidCredentialException(adaptorName, "Unsupported credential type: " + credential.getClass().getName());
        }

        // Will throw exception on timeout
        try {
            session.auth().verify(timeout);
        } catch (IOException e) {
            throw new XenonException(adaptorName, "Connection authentication timeout", e);
        }

        return session;
    }

    public static Map<String, String> translateProperties(Map<String, String> providedProperties, String orginalPrefix,
            XenonPropertyDescription[] supportedProperties, String newPrefix) {

        Set<String> valid = validProperties(supportedProperties);

        HashMap<String, String> result = new HashMap<>();

        int start = orginalPrefix.length();

        for (Map.Entry<String, String> e : providedProperties.entrySet()) {

            String key = e.getKey();

            if (key.startsWith(orginalPrefix)) {

                String newKey = newPrefix + key.substring(start, key.length());

                if (valid.contains(newKey)) {
                    result.put(newKey, e.getValue());
                }
            }
        }

        return result;
    }

    public static Set<String> validProperties(XenonPropertyDescription[] properties) {

        HashSet<String> result = new HashSet<>();

        for (XenonPropertyDescription p : properties) {
            result.add(p.getName());
        }

        return result;
    }
}
