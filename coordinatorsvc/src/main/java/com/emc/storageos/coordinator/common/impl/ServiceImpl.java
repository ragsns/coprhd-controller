/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.common.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;


/**
 * Default service implementation.  Uses properties txt serialization format
 */
public class ServiceImpl implements Service {
    private static final char RESERVED_CHAR = '_';
    private static final String NAME_KEY = "_name";
    private static final String VERSION_KEY = "_version";
    // Service id is for internal use to talk to coordinator.
    // EX: syssvc-1, syssvc-2, syssvc-10_247_100_15
    private static final String ID_KEY = "_id";
    // Node name used for external display/query purpose.
    // EX: vipr1, vipr2, dataservice-10_247_100_15
    private static final String NODE_NAME_KEY = "_nodeName";
    private static final String ENDPOINT_KEY = "_endpoint";

    private Properties _map = new Properties();

    /**
     * Here for spring friendly config
     */
    public ServiceImpl() {
    }

    protected ServiceImpl(Properties p) {
        _map = p;
    }

    @Override
    public String getName() {
        return (String) _map.get(NAME_KEY);
    }

    public void setName(String name) {
        _map.put(NAME_KEY, name);
    }

    @Override
    public String getVersion() {
        return (String) _map.get(VERSION_KEY);
    }

    @Override
    public void setVersion(String version) {
        _map.put(VERSION_KEY, version);
    }

    @Override
    public String getId() {
        return (String) _map.get(ID_KEY);
    }

    public void setId(String id) {
        _map.put(ID_KEY, id);
    }

    @Override
    public String getNodeName() {
        return (String) _map.get(NODE_NAME_KEY);
    }

    public void setNodeName(String nodeId) {
        _map.put(NODE_NAME_KEY, nodeId);
    }

    @Override
    public URI getEndpoint() {
        return getEndpoint(null);
    }

    @Override
    public URI getEndpoint(String key) {
        String endpoint = null;
        if (key == null) {
            endpoint = (String)_map.get(ENDPOINT_KEY);
        } else {
            endpoint = (String)_map.get(String.format("%1$s_%2$s", ENDPOINT_KEY, key));
        }
        if (endpoint == null) {
            return null;
        }
        return URI.create(endpoint);
    }

    /**
     * Set default endpoint on the service
     * @param endpoint
     */
    public void setEndpoint(URI endpoint) {
        _map.put(ENDPOINT_KEY, endpoint.toString());
    }

    /**
     * Set endpoint with specific key in the endpointmap
     * @param key
     * @param endpoint
     */
    public void setEndpoint(String key, URI endpoint) {
    	_map.put(String.format("%1$s_%2$s", ENDPOINT_KEY, key), endpoint.toString());
    }
    
    public void setEndpointMap(Map<String, URI> endpoint) {
        Iterator<Map.Entry<String, URI>> it = endpoint.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, URI> next = it.next();
            _map.put(String.format("%1$s_%2$s", ENDPOINT_KEY, next.getKey()), next.getValue().toString());
            _map.put(ENDPOINT_KEY, next.getValue().toString());
        }
    }

    @Override
    public String getAttribute(String key) {
        return (String) _map.get(key);
    }

    public void setAttribute(String key, String val) {
        if (key == null || key.length() == 0 || key.charAt(0) == RESERVED_CHAR) {
            throw CoordinatorException.fatals.invalidKey();
        }
        _map.put(key, val);
    }

    @Override
    public boolean isTagged(String tag) {
        return _map.containsKey(tag);
    }

    public void setTag(String tag) {
        if (tag == null || tag.length() == 0 || tag.charAt(0) == RESERVED_CHAR) {
            throw CoordinatorException.fatals.invalidKey();
        }
        _map.put(tag, "-");
    }

    public void setTags(Set<String> tag) {
        Iterator<String> it = tag.iterator();
        while (it.hasNext()) {
            setTag(it.next());
        }
    }

    public static Service parse(byte[] content) {
        try {
            Properties p = new Properties();
            p.load(new ByteArrayInputStream(content));
            return new ServiceImpl(p);
        } catch (IOException e) {
            throw CoordinatorException.fatals.invalidProperties(e);
        }
    }

    @Override
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _map.store(out, null);
        return out.toByteArray();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ServiceImpl)) {
            return false;
        }
        ServiceImpl service = (ServiceImpl) obj;
        if (!getName().equals(service.getName())) {
            return false;
        }
        if (!getVersion().equals(service.getVersion())) {
            return false;
        }
        if (!getId().equals(service.getId())) {
            return false;
        }
        if (!getNodeName().equals(service.getNodeName())) {
            return false;
        }
        if (!getEndpoint().equals(service.getEndpoint())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int hash = 1;
        hash = hash * PRIME + ((getName() == null) ? 0 : getName().hashCode());
        hash = hash * PRIME + ((getVersion() == null) ? 0 : getVersion().hashCode());
        hash = hash * PRIME + ((getId() == null) ? 0 : getId().hashCode());
        hash = hash * PRIME + ((getNodeName() == null) ? 0 : getNodeName().hashCode());
        hash = hash * PRIME + ((getEndpoint() == null) ? 0 : getEndpoint().hashCode());
        return hash;
    }
}
