package nl.esciencecenter.octopus.files;

import java.net.URI;

import nl.esciencecenter.octopus.engine.OctopusProperties;

public interface FileSystem {
    
    public String getAdaptorName();
    
    public URI getUri();

    public OctopusProperties getProperties();

}
