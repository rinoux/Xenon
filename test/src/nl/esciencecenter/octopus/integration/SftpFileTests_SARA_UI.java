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
package nl.esciencecenter.octopus.integration;

import java.net.URI;

import nl.esciencecenter.octopus.credentials.Credential;
import nl.esciencecenter.octopus.credentials.Credentials;
import nl.esciencecenter.octopus.exceptions.OctopusException;
import nl.esciencecenter.octopus.files.FileSystem;

public class SftpFileTests_SARA_UI extends AbstractFileTests {
    public String getTestUser() {
        // actual test user 
        return System.getProperty("user.name");
    }

    public java.net.URI getTestLocation() throws Exception {

        String user = getTestUser();
        return new URI("sftp://" + user + "@ui.grid.sara.nl/tmp/");
    }

    public Credential getSSHCredentials() throws OctopusException {

        Credentials creds = octopus.credentials();
        String user = getTestUser();
        Credential cred =
                creds.newCertificateCredential("ssh", null, "/home/" + user + "/.ssh/id_rsa", "/home/" + user
                        + "/.ssh/id_rsa.pub", user, "");
        return cred;
    }

    /**
     * Get actual FileSystem implementation to run test on. Test this before other tests:
     */
    protected FileSystem getFileSystem() throws Exception {

        synchronized (this) {
            if (fileSystem == null) {
                URI uri = getTestLocation();
                URI fsURI = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
                fileSystem = getFiles().newFileSystem(fsURI, getSSHCredentials(), null);
            }

            return fileSystem;
        }
    }

    // ===
    // Sftp Specific tests here: 
    // === 

}
