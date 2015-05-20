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
package nl.esciencecenter.xenon.adaptors.ftp;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.XenonPropertyDescription;
import nl.esciencecenter.xenon.XenonPropertyDescription.Component;
import nl.esciencecenter.xenon.XenonPropertyDescription.Type;
import nl.esciencecenter.xenon.credentials.Credentials;
import nl.esciencecenter.xenon.engine.Adaptor;
import nl.esciencecenter.xenon.engine.XenonEngine;
import nl.esciencecenter.xenon.engine.XenonProperties;
import nl.esciencecenter.xenon.engine.XenonPropertyDescriptionImplementation;
import nl.esciencecenter.xenon.engine.util.ImmutableArray;
import nl.esciencecenter.xenon.files.Files;
import nl.esciencecenter.xenon.jobs.Jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;

public class FtpAdaptor extends Adaptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FtpAdaptor.class);

    /** The name of this adaptor */
    public static final String ADAPTOR_NAME = "ftp";

    /** The default SSH port */
    protected static final int DEFAULT_PORT = 21;

    /** A description of this adaptor */
    private static final String ADAPTOR_DESCRIPTION = "The FTP adaptor implements all functionality with remove ftp servers.";

    /** The schemes supported by this adaptor */
    private static final ImmutableArray<String> ADAPTOR_SCHEME = new ImmutableArray<>("ftp");

    /** The locations supported by this adaptor */
    private static final ImmutableArray<String> ADAPTOR_LOCATIONS = new ImmutableArray<>("[user@]host[:port]");

    /** All our own properties start with this prefix. */
    public static final String PREFIX = XenonEngine.ADAPTORS + "ftp.";

    /** Enable strict host key checking. */
    public static final String STRICT_HOST_KEY_CHECKING = PREFIX + "strictHostKeyChecking";

    /** Load the known_hosts file by default. */
    public static final String LOAD_STANDARD_KNOWN_HOSTS = PREFIX + "loadKnownHosts";

    /** Enable strict host key checking. */
    public static final String AUTOMATICALLY_ADD_HOST_KEY = PREFIX + "autoAddHostKey";

    /** Add gateway to access machine. */
    public static final String GATEWAY = PREFIX + "gateway";

    /** All our own queue properties start with this prefix. */
    public static final String QUEUE = PREFIX + "queue.";

    /** Maximum history length for finished jobs */
    public static final String MAX_HISTORY = QUEUE + "historySize";

    /** Property for maximum history length for finished jobs */
    public static final String POLLING_DELAY = QUEUE + "pollingDelay";

    /** Local multi queue properties start with this prefix. */
    public static final String MULTIQ = QUEUE + "multi.";

    /** Property for the maximum number of concurrent jobs in the multi queue. */
    public static final String MULTIQ_MAX_CONCURRENT = MULTIQ + "maxConcurrentJobs";

    /** Ssh adaptor information start with this prefix. */
    public static final String INFO = PREFIX + "info.";

    /** Ssh job information start with this prefix. */
    public static final String JOBS = INFO + "jobs.";

    /** How many jobs have been submitted using this adaptor. */
    public static final String SUBMITTED = JOBS + "submitted";

    /** List of properties supported by this SSH adaptor */
    private static final ImmutableArray<XenonPropertyDescription> VALID_PROPERTIES = new ImmutableArray<XenonPropertyDescription>(
            new XenonPropertyDescriptionImplementation(AUTOMATICALLY_ADD_HOST_KEY, Type.BOOLEAN, EnumSet.of(Component.SCHEDULER,
                    Component.FILESYSTEM), "true", "Automatically add unknown host keys to known_hosts."),
            new XenonPropertyDescriptionImplementation(STRICT_HOST_KEY_CHECKING, Type.BOOLEAN, EnumSet.of(Component.SCHEDULER,
                    Component.FILESYSTEM), "true", "Enable strict host key checking."),
                            new XenonPropertyDescriptionImplementation(LOAD_STANDARD_KNOWN_HOSTS, Type.BOOLEAN, EnumSet.of(Component.XENON),
                                    "true", "Load the standard known_hosts file."), new XenonPropertyDescriptionImplementation(POLLING_DELAY,
                                            Type.LONG, EnumSet.of(Component.SCHEDULER), "1000",
                    "The polling delay for monitoring running jobs (in milliseconds)."),
            new XenonPropertyDescriptionImplementation(MULTIQ_MAX_CONCURRENT, Type.INTEGER, EnumSet.of(Component.SCHEDULER), "4",
                                                    "The maximum number of concurrent jobs in the multiq.."), new XenonPropertyDescriptionImplementation(GATEWAY,
                                                            Type.STRING, EnumSet.of(Component.SCHEDULER, Component.FILESYSTEM), null,
                                                            "The gateway machine used to create an SSH tunnel to the target."));

    private final FtpFiles filesAdaptor;
    private FtpCredentials credentialsAdaptor;

    public FtpAdaptor(XenonEngine xenonEngine, Map<String, String> properties) throws XenonException {
        this(xenonEngine, new JSch(), properties);
    }

    public FtpAdaptor(XenonEngine xenonEngine, JSch jsch, Map<String, String> properties) throws XenonException {
        super(xenonEngine, ADAPTOR_NAME, ADAPTOR_DESCRIPTION, ADAPTOR_SCHEME, ADAPTOR_LOCATIONS, VALID_PROPERTIES,
                new XenonProperties(VALID_PROPERTIES, Component.XENON, properties));

        filesAdaptor = new FtpFiles(this, xenonEngine);
        credentialsAdaptor = new FtpCredentials(getProperties(), this);
    }

    @Override
    public XenonPropertyDescription[] getSupportedProperties() {
        return VALID_PROPERTIES.asArray();
    }

    @Override
    public Files filesAdaptor() {
        return filesAdaptor;
    }

    @Override
    public Jobs jobsAdaptor() {
        return null;
    }

    @Override
    public Credentials credentialsAdaptor() {
        return credentialsAdaptor;
    }

    @Override
    public void end() {
        filesAdaptor.end();
    }

    @Override
    public Map<String, String> getAdaptorSpecificInformation() {
        Map<String, String> result = new HashMap<String, String>();
        //        jobsAdaptor.getAdaptorSpecificInformation(result);
        return result;
    }
}
