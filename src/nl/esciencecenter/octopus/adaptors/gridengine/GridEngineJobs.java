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
package nl.esciencecenter.octopus.adaptors.gridengine;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import nl.esciencecenter.octopus.credentials.Credential;
import nl.esciencecenter.octopus.engine.OctopusEngine;
import nl.esciencecenter.octopus.engine.OctopusProperties;
import nl.esciencecenter.octopus.engine.jobs.JobStatusImplementation;
import nl.esciencecenter.octopus.engine.jobs.SchedulerImplementation;
import nl.esciencecenter.octopus.exceptions.NoSuchSchedulerException;
import nl.esciencecenter.octopus.exceptions.OctopusException;
import nl.esciencecenter.octopus.exceptions.OctopusIOException;
import nl.esciencecenter.octopus.exceptions.OctopusRuntimeException;
import nl.esciencecenter.octopus.jobs.Job;
import nl.esciencecenter.octopus.jobs.JobDescription;
import nl.esciencecenter.octopus.jobs.JobStatus;
import nl.esciencecenter.octopus.jobs.Jobs;
import nl.esciencecenter.octopus.jobs.QueueStatus;
import nl.esciencecenter.octopus.jobs.Scheduler;
import nl.esciencecenter.octopus.jobs.Streams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GridEngineJobs implements Jobs {

    private static final Logger logger = LoggerFactory.getLogger(GridEngineJobs.class);

    private final OctopusProperties properties;
    private final OctopusEngine octopusEngine;

    private final Map<String, GridEngineSchedulerConnection> connections;

    public GridEngineJobs(OctopusProperties properties, OctopusEngine octopusEngine) {
        this.properties = properties;
        this.octopusEngine = octopusEngine;

        connections = new HashMap<String, GridEngineSchedulerConnection>();
    }

    private synchronized GridEngineSchedulerConnection getConnection(Scheduler scheduler) throws OctopusRuntimeException,
            NoSuchSchedulerException {
        if (!(scheduler instanceof SchedulerImplementation)) {
            throw new OctopusRuntimeException(GridengineAdaptor.ADAPTOR_NAME, "scheduler " + scheduler.toString()
                    + " not created by this adaptor");
        }

        String schedulerID = ((SchedulerImplementation) scheduler).getUniqueID();

        if (!connections.containsKey(schedulerID)) {
            throw new NoSuchSchedulerException(GridengineAdaptor.ADAPTOR_NAME, "cannot find scheduler " + scheduler.toString()
                    + " did you close it already?");
        }

        return connections.get(schedulerID);
    }

    private synchronized void addConnection(GridEngineSchedulerConnection connection) {
        connections.put(connection.getID(), connection);
    }

    private synchronized void removeConnection(AbstractSchedulerConnection connection) {
        connections.remove(connection.getID());
    }

    @Override
    public synchronized boolean isOpen(Scheduler scheduler) throws OctopusException, OctopusIOException {
        if (!(scheduler instanceof SchedulerImplementation)) {
            throw new OctopusRuntimeException(GridengineAdaptor.ADAPTOR_NAME, "scheduler " + scheduler.toString()
                    + " not created by this adaptor");
        }

        String schedulerID = ((SchedulerImplementation) scheduler).getUniqueID();

        return connections.containsKey(schedulerID);
    }

    @Override
    public Scheduler newScheduler(URI location, Credential credential, Properties properties) throws OctopusException,
            OctopusIOException {
        GridEngineSchedulerConnection connection = new GridEngineSchedulerConnection(location, credential, properties, octopusEngine);

        addConnection(connection);

        return connection.getScheduler();
    }
    
    @Override
    public String getDefaultQueueName(Scheduler scheduler) throws OctopusException, OctopusIOException {
        // TODO: implement!
        return null;
    }
    
    @Override
    public void close(Scheduler scheduler) throws OctopusException, OctopusIOException {
        AbstractSchedulerConnection connection = getConnection(scheduler);

        connection.close();

        removeConnection(connection);
    }

    @Override
    public Scheduler getLocalScheduler() throws OctopusException, OctopusIOException {
        throw new OctopusRuntimeException(GridengineAdaptor.ADAPTOR_NAME,
                "Error in engine: getLocalScheduler() should not be called in this adaptor");
    }

    @Override
    public QueueStatus getQueueStatus(Scheduler scheduler, String queueName) throws OctopusException, OctopusIOException {
        //find connection
        GridEngineSchedulerConnection connection = getConnection(scheduler);
        
        //fetch and parse info
        return connection.getQueueStatus(queueName);
    }

    @Override
    public QueueStatus[] getQueueStatuses(Scheduler scheduler, String... queueNames) throws OctopusException, OctopusIOException {
        //find connection
        GridEngineSchedulerConnection connection = getConnection(scheduler);

        //fetch and parse info
        return connection.getQueueStatuses(queueNames);
    }

    @Override
    public Job[] getJobs(Scheduler scheduler, String... queueNames) throws OctopusException, OctopusIOException {
        //find connection
        AbstractSchedulerConnection connection = getConnection(scheduler);

        //fetch and parse info
        return connection.getJobs(queueNames);
    }

    @Override
    public Job submitJob(Scheduler scheduler, JobDescription description) throws OctopusException, OctopusIOException {
        AbstractSchedulerConnection connection = getConnection(scheduler);

        return connection.submitJob(description);
    }

    @Override
    public JobStatus getJobStatus(Job job) throws OctopusException, OctopusIOException {
        AbstractSchedulerConnection connection = getConnection(job.getScheduler());

        return connection.getJobStatus(job);
    }

    private AbstractSchedulerConnection[] getConnections(Job[] jobs) {
        Map<String, GridEngineSchedulerConnection> result = new HashMap<String, GridEngineSchedulerConnection>();

        for (Job job : jobs) {
            if (job != null) {
                Scheduler scheduler = job.getScheduler();
                if (!(scheduler instanceof SchedulerImplementation)) {
                    throw new OctopusRuntimeException(GridengineAdaptor.ADAPTOR_NAME, "scheduler " + scheduler.toString()
                            + " not created by this adaptor");
                }

                String schedulerID = ((SchedulerImplementation) scheduler).getUniqueID();

                if (connections.containsKey(schedulerID)) {
                    result.put(schedulerID, connections.get(schedulerID));
                }
            }
        }

        return result.values().toArray(new GridEngineSchedulerConnection[0]);
    }

    private void selectJobs(AbstractSchedulerConnection connection, Job[] in, Job[] out) {
        for (int i = 0; i < in.length; i++) {
            if (in[i] != null && connection.getScheduler().equals(in[i].getScheduler())) {
                out[i] = in[i];
            } else {
                out[i] = null;
            }
        }
    }

    private void getJobStatus(AbstractSchedulerConnection connection, Job[] in, JobStatus[] out) {
        JobStatus[] result = null;
        Exception exception = null;

        try {
            result = connection.getJobStatuses(in);
        } catch (OctopusException | OctopusIOException e) {
            exception = e;
        }

        for (int i = 0; i < in.length; i++) {
            if (in[i] != null) {
                if (result != null) {
                    out[i] = result[i];
                } else {
                    out[i] = new JobStatusImplementation(in[i], null, null, exception, false, false, null);
                }
            }
        }
    }

    @Override
    public JobStatus[] getJobStatuses(Job... jobs) {
        //implementation inspired by / copy pasted from JobsEngine.getJobStatuses()

        // If we have more than one job, we first collect all connections
        AbstractSchedulerConnection[] connections = getConnections(jobs);

        JobStatus[] result = new JobStatus[jobs.length];
        Job[] tmp = new Job[jobs.length];

        // Next we iterate over the connections, and get the JobStatus for each scheduler individually, merging the result into
        // the overall result on the fly.
        for (AbstractSchedulerConnection connection : connections) {
            selectJobs(connection, jobs, tmp);
            getJobStatus(connection, tmp, result);
        }

        // Fill in all empty results with exceptions.
        for (int i = 0; i < jobs.length; i++) {
            if (jobs[i] != null && result[i] == null) {
                Exception exception = new Exception("cannot find scheduler for job");
                result[i] = new JobStatusImplementation(jobs[i], null, null, exception, false, false, null);
            }
        }

        return result;
    }

    @Override
    public JobStatus cancelJob(Job job) throws OctopusException, OctopusIOException {
        AbstractSchedulerConnection connection = getConnection(job.getScheduler());

        return connection.cancelJob(job);
    }

    public void end() {
        AbstractSchedulerConnection[] connections;

        synchronized (this) {
            connections = this.connections.values().toArray(new GridEngineSchedulerConnection[0]);
        }

        for (AbstractSchedulerConnection connection : connections) {
            try {
                connection.close();
            } catch (OctopusIOException | OctopusException e) {
                //FIXME: do something with this error, perhaps?
                logger.error("Error on closing connection to server", e);
            }
        }
    }

    @Override
    public Streams getStreams(Job job) throws OctopusException {
        throw new OctopusException(GridengineAdaptor.ADAPTOR_NAME, "Interactive jobs not supported");
    }

    @Override
    public JobStatus waitUntilDone(Job job, long timeout) throws OctopusException, OctopusIOException {
        AbstractSchedulerConnection connection = getConnection(job.getScheduler());

        return connection.waitUntilDone(job, timeout);
    }

}
