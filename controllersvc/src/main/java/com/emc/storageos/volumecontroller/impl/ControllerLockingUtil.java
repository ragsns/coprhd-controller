/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

public class ControllerLockingUtil {
	private static final Logger log = LoggerFactory.getLogger(ControllerLockingUtil.class);
	
	/**
	 * Returns a list of lock keys for export of Hosts to StorageSystems.
	 * This is constructed from a list of Initiators.
	 * All the host URIs are collected from the Initiators.
	 * If an Initiator does not have a host URI, it's hostName is converted to a URI and used.
	 * iF export type is Cluster, the clusters are looked up, and all hosts in the
	 * cluster are used.
	 * The keys are constructed from a concatenation of the host URI and the storage system URI.
	 * @param dbClient
	 * @param  type ExportGroup.ExportGroupType -- used to determine if cluster export
	 * @param initiatorURIs -- set of Initiators to consider
	 * @param storage -- URI of storage system (could be null in which case only host in key)
	 * @return List<String> where each item in list is a lockKey
	 */
	static public List<String> getHostStorageLockKeys(DbClient dbClient, ExportGroup.ExportGroupType type, 
			Collection<URI> initiatorURIs, URI storage) {
		List<String> lockKeys = new ArrayList<String>();
		// Collect the needed hosts, which can be specified either by URI or string name.
		Set<URI> hostURIs = new HashSet<URI>();
		Set<String> hostNames = new HashSet<String>();
		for (URI initiatorURI : initiatorURIs) {
			Initiator initiator = dbClient.queryObject(Initiator.class, initiatorURI);
			if (initiator == null || initiator.getInactive()) continue;
			if (! NullColumnValueGetter.isNullURI(initiator.getHost())) { 
				// There is a host URI association. Use that.
				hostURIs.add(initiator.getHost());
			} else {
				// Generate a fake URI from the host name. Remove any weird characters except colon, dash.
				String fakeURIString = initiator.getHostName().replaceAll("[^a-zA-Z0-9_:-]", "");
				String fakeURI = URI.create(fakeURIString).toString();
				hostNames.add(fakeURI);
			}
		}
		// If the export is type cluster, we want to add ALL the hosts in the clusters.
		if (type.equals(ExportGroup.ExportGroupType.Cluster)) {
			Set<URI> clusterURIs = new HashSet<URI>();
			// First determine the clusters for each real host.
			for (URI hostURI : hostURIs) {
				Host host = dbClient.queryObject(Host.class, hostURI);
				if (host == null || host.getInactive()) continue;
				if (! NullColumnValueGetter.isNullURI(host.getCluster())) {
					clusterURIs.add(host.getCluster());
				}
			}
			// Now, for each cluster, look up all the hosts in the cluster and 
			// add those hosts if not already present.
			for (URI clusterURI : clusterURIs) {
				URIQueryResultList result = new URIQueryResultList();
				dbClient.queryByConstraint(ContainmentConstraint.Factory.
		            getContainedObjectsConstraint(clusterURI, Host.class, "cluster"), result);
				Iterator<URI> iter = result.iterator();
				while(iter.hasNext()) {
					Host host = dbClient.queryObject(Host.class, iter.next());
					if (host == null || host.getInactive()) continue;
					hostURIs.add(host.getId());
				}
			}
		}
		
		// Now make a key for every host / storage pair
		for (URI hostURI : hostURIs) {
			String key = hostURI.toString() + (storage != null ? "::"+ storage.toString() : "");
			if (!lockKeys.contains(key)) {
				lockKeys.add(key);
			}
		}
		for (String hostName : hostNames) {
			String key = hostName + (storage != null ? "::"+ storage.toString() : "");
			if (!lockKeys.contains(key)) {
				lockKeys.add(key);
			}
		}
		
		log.info("Lock keys: " + lockKeys.toString());
		return lockKeys;
	}

	/**
	 * Make a consistencyGroup / storageSystem duple key.
	 * @param consistencyGroup
	 * @param storageSystem
	 * @return
	 */
	static public String getConsistencyGroupStorageKey(URI consistencyGroup, URI storageSystem) {
		return consistencyGroup.toString() + "::" + storageSystem.toString();
	}
}
