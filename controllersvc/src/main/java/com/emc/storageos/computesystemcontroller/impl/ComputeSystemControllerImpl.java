/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.computecontroller.impl.ComputeDeviceController;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.adapter.ExportGroupState;
import com.emc.storageos.computesystemcontroller.impl.adapter.HostStateChange;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ComputeSystemControllerImpl implements ComputeSystemController {

    private static final Log _log = LogFactory.getLog(ComputeSystemControllerImpl.class);

    private WorkflowService _workflowService;
    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";

    private static final String ADD_INITIATOR_STORAGE_WF_NAME = "ADD_INITIATOR_STORAGE_WORKFLOW";
    private static final String REMOVE_INITIATOR_STORAGE_WF_NAME = "REMOVE_INITIATOR_STORAGE_WORKFLOW";
    private static final String REMOVE_HOST_STORAGE_WF_NAME = "REMOVE_HOST_STORAGE_WORKFLOW";
    private static final String REMOVE_IPINTERFACE_STORAGE_WF_NAME = "REMOVE_IPINTERFACE_STORAGE_WORKFLOW";
    private static final String HOST_CHANGES_WF_NAME = "HOST_CHANGES_WORKFLOW";

    private static final String DETACH_HOST_STORAGE_WF_NAME = "DETACH_HOST_STORAGE_WORKFLOW";
    private static final String DETACH_CLUSTER_STORAGE_WF_NAME = "DETACH_CLUSTER_STORAGE_WORKFLOW";
    private static final String DETACH_VCENTER_STORAGE_WF_NAME = "DETACH_VCENTER_STORAGE_WORKFLOW";
    private static final String DETACH_VCENTER_DATACENTER_STORAGE_WF_NAME = "DETACH_VCENTER_DATACENTER_STORAGE_WORKFLOW";

    private static final String DELETE_EXPORT_GROUP_STEP = "DeleteExportGroupStep";
    private static final String UPDATE_EXPORT_GROUP_STEP = "UpdateExportGroupStep";
    private static final String UPDATE_FILESHARE_EXPORT_STEP = "UpdateFileshareExportStep";
    private static final String UNEXPORT_FILESHARE_STEP = "UnexportFileshareStep";
    private static final String UPDATE_INITIATOR_CLUSTER_NAMES_STEP = "UpdateInitiatorClusterNamesStep";

    private ComputeDeviceController computeDeviceController;
    private BlockStorageScheduler _blockScheduler;

    public void setComputeDeviceController(ComputeDeviceController computeDeviceController) {
        this.computeDeviceController = computeDeviceController;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public void setBlockScheduler(BlockStorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    @Override
    public void detachHostStorage(URI host, boolean deactivateOnComplete, boolean deactivateBootVolume, String taskId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(host, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, DETACH_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            if (deactivateOnComplete) {
                waitFor = computeDeviceController.addStepsVcenterHostCleanup(workflow, waitFor, host);
            }

            waitFor = addStepsForExportGroups(workflow, waitFor, host);

            waitFor = addStepsForFileShares(workflow, waitFor, host);

            if (deactivateOnComplete) {
                waitFor = computeDeviceController.addStepsDeactivateHost(workflow, waitFor, host, deactivateBootVolume);
            }

            workflow.executePlan(completer, "Success", null, null, null, null);
        }
        catch (Exception ex) {
            String message = "detachHostStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }
    
    @Override
	public void detachVcenterStorage(URI id, boolean deactivateOnComplete, String taskId) throws ControllerException {
    	TaskCompleter completer = null;
        try {
            completer = new VcenterCompleter(id, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DETACH_VCENTER_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;
            
            // We need to find all datacenters associated to the vcenter
            List<NamedElementQueryResultList.NamedElement> datacenterUris = ComputeSystemHelper.listChildren(_dbClient, id,
                    VcenterDataCenter.class, "label", "vcenter");
            for (NamedElementQueryResultList.NamedElement datacenterUri : datacenterUris) {
            	waitFor = addStepForVcenterDataCenter(workflow, waitFor, datacenterUri.id);
            }

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachVcenterStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
	}
    
    @Override
	public void detachDataCenterStorage(URI datacenter, boolean deactivateOnComplete, String taskId) throws InternalException {
    	TaskCompleter completer = null;
        try {
            completer = new VcenterDataCenterCompleter(datacenter, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DETACH_VCENTER_DATACENTER_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;
            
            waitFor = addStepForVcenterDataCenter(workflow, waitFor, datacenter);
            
            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachDataCenterStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
	}

    /**
     * Gets all export groups that contain references to the provided host or initiators
     * Export groups that don't contain initiators for a host may stil reference the host
     * @param hostId the host id
     * @param initiators list of initiators for the given host
     * @return list of export groups containing references to the host or initiators
     */
    protected List<ExportGroup> getExportGroups(URI hostId, List<Initiator> initiators) {
        HashMap<URI, ExportGroup> exports = new HashMap<URI, ExportGroup>();
        // Get all exports that use the host's initiators
        for (Initiator item : initiators) {
            List<ExportGroup> list = ComputeSystemHelper.findExportsByInitiator(_dbClient, item.getId().toString());
            for (ExportGroup export : list) {
                exports.put(export.getId(), export);
            }
        }
        // Get all exports that reference the host (may not contain host initiators)
        List<ExportGroup> hostExports = ComputeSystemHelper.findExportsByHost(_dbClient, hostId.toString());
        for (ExportGroup export : hostExports) {
            exports.put(export.getId(),  export);
        }
        return new ArrayList<ExportGroup>(exports.values());
    }
    
    public String addStepForVcenterDataCenter(Workflow workflow, String waitFor, URI datacenterUri) {
    	VcenterDataCenter dataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
        if (dataCenter != null && !dataCenter.getInactive()) {
        	// clean all export related to host in datacenter
            List<NamedElementQueryResultList.NamedElement> hostUris = ComputeSystemHelper.listChildren(_dbClient,
                    dataCenter.getId(), Host.class, "label", "vcenterDataCenter");
            for (NamedElementQueryResultList.NamedElement hostUri : hostUris) {
                Host host = _dbClient.queryObject(Host.class, hostUri.id);
                // do not detach storage of provisioned hosts
                if (host != null && !host.getInactive() && NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                    waitFor = addStepsForExportGroups(workflow, waitFor, host.getId());
                    waitFor = addStepsForFileShares(workflow, waitFor, host.getId());
                }
            }
            // clean all the export related to clusters in datacenter
            List<NamedElementQueryResultList.NamedElement> clustersUris = ComputeSystemHelper.listChildren(_dbClient,
                    dataCenter.getId(), Cluster.class, "label", "vcenterDataCenter");
            for (NamedElementQueryResultList.NamedElement clusterUri : clustersUris) {
                Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri.id);
                if (cluster != null && !cluster.getInactive()) {
                	waitFor = addStepsForClusterExportGroups(workflow, waitFor, cluster.getId());
                }
            }
        }
        return waitFor;
    }
    
    public String addStepsForFileShares(Workflow workflow, String waitFor, URI hostId) {
        
        List<FileShare> fileShares = ComputeSystemHelper.getFileSharesByHost(_dbClient, hostId);
        List<String> endpoints = ComputeSystemHelper.getIpInterfaceEndpoints(_dbClient, hostId);
        for (FileShare fileShare : fileShares) {
            if (fileShare != null && fileShare.getFsExports() != null) {
                for (FileExport fileExport : fileShare.getFsExports().values()) {
                    if (fileExport != null && fileExport.getClients() != null) {
                        if (fileExportContainsEndpoint(fileExport, endpoints)) {
                            StorageSystem device = _dbClient.queryObject(StorageSystem.class, fileShare.getStorageDevice());
                            
                            List<String> clients = fileExport.getClients();
                            clients.removeAll(endpoints);
                            fileExport.setStoragePort(fileShare.getStoragePort().toString());
                            FileShareExport export = new FileShareExport(clients, fileExport.getSecurityType(), fileExport.getPermissions(),
                                    fileExport.getRootUserMapping(), fileExport.getProtocol(), fileExport.getStoragePortName(), 
                                    fileExport.getStoragePort(), fileExport.getPath(), fileExport.getMountPath(),"", "");

                            if (clients.isEmpty()) {
                                _log.info("Unexporting file share " + fileShare.getId());
                                waitFor = workflow.createStep(UNEXPORT_FILESHARE_STEP,
                                        String.format("Unexport fileshare %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        unexportFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            } else {
                                _log.info("Updating export for file share " + fileShare.getId());
                                waitFor = workflow.createStep(UPDATE_FILESHARE_EXPORT_STEP,
                                        String.format("Update fileshare export %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        updateFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            }
                        }
                    }
                }
            }
        }
        
        return waitFor;
    }
    
    public void addInitiatorToExport(URI hostId, URI initId, String taskId) throws ControllerException {
    	List<URI> uris = Lists.newArrayList(initId);
    	addInitiatorsToExport(hostId, uris, taskId);
    }
    
    public void addInitiatorsToExport(URI hostId, List<URI> initiators, String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new InitiatorCompleter(initiators, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, ADD_INITIATOR_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForAddInitiators(workflow, waitFor, hostId, initiators);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "addInitiatorToStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }
    
    public void removeInitiatorFromExport(URI hostId, URI initId, String taskId) throws ControllerException {
    	List<URI> uris = Lists.newArrayList(initId);
    	removeInitiatorsFromExport(hostId, uris, taskId);
    }
    
    public void removeInitiatorsFromExport(URI hostId, List<URI> initiators, String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new InitiatorCompleter(initiators, true, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_INITIATOR_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForRemoveInitiators(workflow, waitFor, hostId, initiators);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachHostStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }
    
    public void addHostsToExport(List<URI> hostIds, URI clusterId, String taskId, URI oldCluster) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(hostIds, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;
            
            if (!NullColumnValueGetter.isNullURI(oldCluster)) {
            	waitFor = addStepsForRemoveHost(workflow, waitFor, hostIds, oldCluster);
            }
            
            waitFor = addStepForUpdatingInitiatorClusterName(workflow, waitFor, hostIds, clusterId);

            waitFor = addStepsForAddHost(workflow, waitFor, hostIds, clusterId);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "addHostToExport caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }
    
    public void removeHostsFromExport(List<URI> hostIds, URI clusterId, String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(hostIds, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForRemoveHost(workflow, waitFor, hostIds, clusterId);
            
            waitFor = addStepForUpdatingInitiatorClusterName(workflow, waitFor, hostIds, clusterId);
            
            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "removeHostFromExport caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }
    
    public void removeIpInterfaceFromFileShare(URI hostId, URI ipId , String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new IpInterfaceCompleter(ipId, true, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_IPINTERFACE_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForIpInterfaceFileShares(workflow, waitFor, hostId, ipId);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "removeIpInterfaceFromFileShare caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }
    
    public String addStepsForAddInitiators(Workflow workflow, String waitFor, URI hostId, Collection<URI> inits) {
    	List<Initiator> initiators = _dbClient.queryObject(Initiator.class, inits);
    	List<ExportGroup> exportGroups = ComputeSystemHelper.findExportsByHost(_dbClient, hostId.toString());
        
        for (ExportGroup export : exportGroups) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());
    
            List<Initiator> validInitiator = ComputeSystemHelper.validatePortConnectivity(_dbClient, export, initiators);
            if (!validInitiator.isEmpty()) {
            	boolean update = false;
            	for (Initiator initiator : validInitiator) {
            		// if the initiators is not already in the list add it.
    	        	if (!updatedInitiators.contains(initiator.getId())) {
    	        		updatedInitiators.add(initiator.getId());
    	        		update = true;
    	        	}
            	}
            	
            	if (update) {
	        		waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
	                        String.format("Updating export group %s", export.getId()), waitFor,
	                        export.getId(), export.getId().toString(),
	                        this.getClass(),
	                        updateExportGroupMethod(export.getId(), updatedVolumesMap, 
	                        		updatedClusters, updatedHosts, updatedInitiators),
	                                null, null);
            	}
            }
        }
        return waitFor;
    }
    
    public String addStepsForRemoveInitiators(Workflow workflow, String waitFor, URI hostId, Collection<URI> initiatorsURI) {
    	
    	List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorsURI);
    	List<ExportGroup> exportGroups = getExportGroups(hostId, initiators);
        
        for (ExportGroup export : exportGroups) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());
            
            // Only update if the list as changed
            if (updatedInitiators.removeAll(initiatorsURI)) {
            	waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()), waitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(), updatedInitiators.isEmpty() ? new HashMap<URI, Integer>() : updatedVolumesMap, 
                        		updatedClusters, updatedHosts, updatedInitiators),
                        null, null);
            }  
        }
        return waitFor;
    }
    
    public String addStepsForRemoveHost(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId) {
        for (ExportGroup export : getSharedExports(clusterId)) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());
            
            for (URI hostId : hostIds) {
            	updatedHosts.remove(hostId);
                
                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
                for (Initiator initiator : hostInitiators) {
                	updatedInitiators.remove(initiator.getId());
                }
            }
    
            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", export.getId()), waitFor,
                    export.getId(), export.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(export.getId(), updatedInitiators.isEmpty() ? new HashMap<URI, Integer>() : updatedVolumesMap, 
                    		updatedClusters, updatedHosts, updatedInitiators),
                    null, null);
        }
        return waitFor;
    }
    
    public String addStepsForAddHost(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId) {
        List<Host> hosts = _dbClient.queryObject(Host.class, hostIds);
        for (ExportGroup eg : getSharedExports(clusterId)) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(eg.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(eg.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(eg.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(eg.getVolumes());
    
            // add host reference to export group
            for (Host host : hosts) {
            	if (!updatedHosts.contains(host.getId())) {
            		updatedHosts.add(host.getId());
                }
                
            	List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, host.getId());
                List<Initiator> validInitiators = ComputeSystemHelper.validatePortConnectivity(_dbClient, eg, hostInitiators);
                if (!validInitiators.isEmpty()) {
                	// if the initiators is not already in the list add it.
        	        for (Initiator initiator : validInitiators) {
        	        	if (!updatedInitiators.contains(initiator.getId())) {
        	        		updatedInitiators.add(initiator.getId());
        	        	}
        	        }
                }
            }
            
            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", eg.getId()), waitFor,
                    eg.getId(), eg.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(eg.getId(), updatedVolumesMap, 
                    		updatedClusters, updatedHosts, updatedInitiators),
                    null, null);
        }
        return waitFor;
    }
    
    public String addStepForUpdatingInitiatorClusterName(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId) {
        for (URI hostId : hostIds) {
            waitFor = workflow.createStep(UPDATE_INITIATOR_CLUSTER_NAMES_STEP,
                    String.format("Updating initiator cluster names for host %s to %s", hostId, clusterId), waitFor,
                    hostId, hostId.toString(),
                    this.getClass(),
                    updateInitiatorClusterNameMethod(hostId, clusterId),
                    null, null);
        }
        return waitFor;
    }
    
    public String addStepsForIpInterfaceFileShares(Workflow workflow, String waitFor, URI hostId, URI ipId) {
        
		List<FileShare> fileShares = ComputeSystemHelper.getFileSharesByHost(_dbClient, hostId);
		IpInterface ipinterface = _dbClient.queryObject(IpInterface.class, ipId);
		List<String> endpoints = Arrays.asList(ipinterface.getIpAddress());
        
        for (FileShare fileShare : fileShares) {
            if (fileShare != null && fileShare.getFsExports() != null) {
                for (FileExport fileExport : fileShare.getFsExports().values()) {
                    if (fileExport != null && fileExport.getClients() != null) {
                        if (fileExportContainsEndpoint(fileExport, endpoints)) {
                            StorageSystem device = _dbClient.queryObject(StorageSystem.class, fileShare.getStorageDevice());
                            
                            List<String> clients = fileExport.getClients();
                            clients.removeAll(endpoints);
                            fileExport.setStoragePort(fileShare.getStoragePort().toString());
                            FileShareExport export = new FileShareExport(clients, fileExport.getSecurityType(), fileExport.getPermissions(),
                                    fileExport.getRootUserMapping(), fileExport.getProtocol(), fileExport.getStoragePortName(), 
                                    fileExport.getStoragePort(), fileExport.getPath(), fileExport.getMountPath(),"","");

                            if (clients.isEmpty()) {
                                _log.info("Unexporting file share " + fileShare.getId());
                                waitFor = workflow.createStep(UNEXPORT_FILESHARE_STEP,
                                        String.format("Unexport fileshare %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        unexportFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            } else {
                                _log.info("Updating export for file share " + fileShare.getId());
                                waitFor = workflow.createStep(UPDATE_FILESHARE_EXPORT_STEP,
                                        String.format("Update fileshare export %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        updateFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            }
                        }
                    }
                }
            }
        }
        
        return waitFor;
    }
    
    protected <T extends Controller> T getController(Class<T> clazz, String hw) throws CoordinatorException {
        return _coordinator.locateService(
                clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }
    
    protected List<ExportGroup> getSharedExports(URI clusterId) {
        Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId); 
        return CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, ExportGroup.class, 
                AlternateIdConstraint.Factory.getConstraint(
                        ExportGroup.class, "clusters", cluster.getId().toString()));
    }
    
    /**
     * Returns true if the file export contains any of the provided endpoints
     * @param fileExport
     * @param endpoints
     * @return true if file export contains any of the endpoints, else false
     */
    private boolean fileExportContainsEndpoint(FileExport fileExport, List<String> endpoints) {
        for(String endpoint : endpoints) {
            if( fileExport.getClients().contains(endpoint) ) {
                return true;
            }
        }
        return false;
    }

    public String addStepsForExportGroups(Workflow workflow, String waitFor, URI hostId) {
        
        List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
        
        for (ExportGroup export : getExportGroups(hostId, hostInitiators)) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());
    
            updatedHosts.remove(hostId);
    
            for (Initiator initiator : hostInitiators) {
            	updatedInitiators.remove(initiator.getId());
            }
    
            if ((updatedInitiators.isEmpty() && export.getType().equals(ExportGroupType.Initiator.name())) ||
                    (updatedHosts.isEmpty() && export.getType().equals(ExportGroupType.Host.name()))) {
                waitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                        String.format("Deleting export group %s", export.getId()), waitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        deleteExportGroupMethod(export.getId()),
                        null, null);
            } else {
                waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()), waitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(), updatedInitiators.isEmpty() ? new HashMap<URI, Integer>() : updatedVolumesMap, 
                        		updatedClusters, updatedHosts, updatedInitiators),
                        null, null);
            }
        }
        return waitFor;
    }
    
    public Workflow.Method updateExportGroupMethod(URI exportGroupURI, Map<URI, Integer> newVolumesMap, 
            Collection<URI> newClusters, Collection<URI> newHosts, Collection<URI> newInitiators) {
        return new Workflow.Method("updateExportGroup", exportGroupURI, newVolumesMap, 
                newClusters, newHosts, newInitiators);
    }
    
    public void updateExportGroup(URI exportGroup, Map<URI, Integer> newVolumesMap, 
            List<URI> newClusters, List<URI> newHosts, List<URI> newInitiators, String stepId) {
        BlockExportController blockController = getController(BlockExportController.class, BlockExportController.EXPORT);
        blockController.exportGroupUpdate(exportGroup, newVolumesMap, newClusters,
                newHosts, newInitiators, stepId);
    }
    
    public Workflow.Method updateFileShareMethod(URI deviceId, String systemType, URI fileShareId, FileShareExport export) {
        return new Workflow.Method("updateFileShare", deviceId, systemType, fileShareId, export);
    }
    
    public void updateFileShare(URI deviceId, String systemType, URI fileShareId, FileShareExport export, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        FileController fileController = getController(FileController.class, systemType);
        FileShare fs = _dbClient.queryObject(FileShare.class, fileShareId);
        _dbClient.createTaskOpStatus(FileShare.class, fs.getId(), 
        				stepId, ResourceOperationTypeEnum.EXPORT_FILE_SYSTEM);
        fileController.export(deviceId, fileShareId, Arrays.asList(export), stepId);
        waitForAsyncFileExportTask(fileShareId, stepId);
    }
    
    public Workflow.Method unexportFileShareMethod(URI deviceId, String systemType, URI fileShareId, FileShareExport export) {
        return new Workflow.Method("unexportFileShare", deviceId, systemType, fileShareId, export);
    }
    
    public void unexportFileShare(URI deviceId, String systemType, URI fileShareId, FileShareExport export, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        FileController fileController = getController(FileController.class, systemType);
        FileShare fs = _dbClient.queryObject(FileShare.class, fileShareId);
        _dbClient.createTaskOpStatus(FileShare.class, fs.getId(), 
        				stepId, ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM);
        fileController.unexport(deviceId, fileShareId, Arrays.asList(export), stepId);
        waitForAsyncFileExportTask(fileShareId, stepId);
    }

    public Workflow.Method deleteExportGroupMethod(URI exportGroupURI) {
        return new Workflow.Method("deleteExportGroup", exportGroupURI);
    }
    
    public void deleteExportGroup(URI exportGroup, String stepId) {
        BlockExportController blockController = getController(BlockExportController.class, BlockExportController.EXPORT);
        blockController.exportGroupDelete(exportGroup, stepId);
    }

    @Override
    public void discover(AsyncTask[] tasks) throws InternalException {
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, Lock.CS_DATA_COLLECTION_LOCK, ControllerServiceImpl.CS_DISCOVERY);
        } catch (Exception e) {
            _log.error(String.format("Failed to schedule discovery job due to %s ", e.getMessage()));
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }
    }

	@Override
	public void detachClusterStorage(URI cluster, boolean deactivateOnComplete, boolean checkVms, String taskId)
			throws InternalException {
		TaskCompleter completer = null;
		try {
			completer = new ClusterCompleter(cluster, deactivateOnComplete, taskId);
			Workflow workflow = _workflowService.getNewWorkflow(this, DETACH_CLUSTER_STORAGE_WF_NAME, true, taskId);
			String waitFor = null;

			if (checkVms) {
				waitFor = computeDeviceController.addStepsVcenterClusterCleanup(workflow, waitFor, cluster);
			}

			waitFor = addStepsForClusterExportGroups(workflow, waitFor, cluster);

			workflow.executePlan(completer, "Success", null, null, null, null);
		} catch (Exception ex) {
			String message = "detachClusterStorage caught an exception.";
			_log.error(message, ex);
			ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
			completer.error(_dbClient, serviceError);
		}
	}
    
    public String addStepsForClusterExportGroups(Workflow workflow, String waitFor, URI clusterId) {
        
        List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, ExportGroup.class, 
                AlternateIdConstraint.Factory.getConstraint(
                        ExportGroup.class, "clusters", clusterId.toString()));

        for (ExportGroup export : exportGroups) {
            
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            updatedClusters.remove(clusterId);
            
            List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, clusterId, Host.class, "cluster");
            for (URI hosturi : hostUris) {
            	updatedHosts.remove(hosturi);
            	updatedInitiators.removeAll( ComputeSystemHelper.getChildrenUris(_dbClient, hosturi, Initiator.class, "host"));
            }            

            if (updatedInitiators.size() == 0) {
                waitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                      String.format("Deleting export group %s", export.getId()), waitFor,
                      export.getId(), export.getId().toString(),
                      this.getClass(),
                      deleteExportGroupMethod(export.getId()),
                      null, null);
            } else {
                waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                      String.format("Updating export group %s", export.getId()), waitFor,
                      export.getId(), export.getId().toString(),
                      this.getClass(),
                      updateExportGroupMethod(export.getId(), updatedVolumesMap, 
                    		  updatedClusters, updatedHosts, updatedInitiators),
                      null, null);
            }
        }
        return waitFor;
    }
    
    public Workflow.Method updateInitiatorClusterNameMethod(URI hostId, URI clusterId) {
        return new Workflow.Method("updateInitiatorClusterName", hostId, clusterId);
    }
    
    public void updateInitiatorClusterName(URI hostId, URI clusterId, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        ComputeSystemHelper.updateInitiatorClusterName(_dbClient, clusterId, hostId);
        WorkflowStepCompleter.stepSucceded(stepId);
    }
    
    /**
     * Waits for the file export or unexport task to complete.
     * This is required because FileDeviceController does not use a workflow.
     * @param fileShareId id of the FileShare being exported
     * @param stepId id of the workflow step
     */
    private void waitForAsyncFileExportTask(URI fileShareId, String stepId) {
        boolean done = false;
        try {
            while(!done) {
                Thread.sleep(1000);
                FileShare fsObj = _dbClient.queryObject(FileShare.class, fileShareId);
                if(fsObj.getOpStatus().containsKey(stepId)) {
                    Operation op = fsObj.getOpStatus().get(stepId);
                    if (op.getStatus().equalsIgnoreCase("ready")) {
                        WorkflowStepCompleter.stepSucceded(stepId);
                        done = true;
                    } else if (op.getStatus().equalsIgnoreCase("error")) {
                        WorkflowStepCompleter.stepFailed(stepId, op.getServiceError());
                        done = true;
                    }
                }
            }
        } catch (InterruptedException ex) {
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    private ExportGroupState getExportGroupState(Map<URI, ExportGroupState> exportGroups, ExportGroup export) {
        if (!exportGroups.containsKey(export.getId())) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());
            ExportGroupState egh = new ExportGroupState(export.getId(), updatedInitiators, updatedHosts, updatedClusters, updatedVolumesMap);
            exportGroups.put(export.getId(), egh);
        }
        return exportGroups.get(export.getId());
    }
    
    @Override
    public void processHostChanges(List<HostStateChange> changes, List<URI> deletedHosts, List<URI> deletedClusters, String taskId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new ProcessHostChangesCompleter(changes, deletedHosts, deletedClusters, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, HOST_CHANGES_WF_NAME, true, taskId);
            String waitFor = null;
            
            Map<URI, ExportGroupState> exportGroups = Maps.newHashMap();
            _log.info("There are " + changes.size() + " changes");

            for(HostStateChange change : changes) {
                
                _log.info("HostChange: " + change);
                
                URI hostId = change.getHost().getId();
                URI currentCluster = change.getHost().getCluster();
                URI oldCluster = change.getOldCluster();
                
                // For every host change (added/removed initiator, cluster change), get all exports that this host currently belongs to
                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
                Collection<URI> hostInitiatorIds = Collections2.transform(hostInitiators, CommonTransformerFunctions.fctnDataObjectToID());
                List<Initiator> newInitiatorObjects = _dbClient.queryObject(Initiator.class, change.getNewInitiators());
                
                // only update initiators if any of them have changed for this host
                if (change.getNewInitiators().size() > 0 || change.getOldInitiators().size() > 0) {
                    for (ExportGroup export : getExportGroups(hostId, hostInitiators)) {
                        ExportGroupState egh = getExportGroupState(exportGroups, export);
                        _log.info("Detected new/removed initiators for export " + export.getId() + " Change: " + change);
                        List<Initiator> validInitiators = ComputeSystemHelper.validatePortConnectivity(_dbClient, export, newInitiatorObjects);
                        Collection<URI> validInitiatorIds = Collections2.transform(validInitiators, CommonTransformerFunctions.fctnDataObjectToID());
                        egh.addInitiators(validInitiatorIds);
                        egh.removeInitiators(change.getOldInitiators());
                    }
                }
                
                // check for any cluster changes
                boolean isRemovedFromCluster = !NullColumnValueGetter.isNullURI(oldCluster) 
                        && NullColumnValueGetter.isNullURI(currentCluster)
                        && ComputeSystemHelper.isClusterInExport(_dbClient, oldCluster);
                       
                // being removed from a cluster and no longer in a cluster
                if (isRemovedFromCluster) {
                    for (ExportGroup export : getSharedExports(oldCluster)) {
                        ExportGroupState egh = getExportGroupState(exportGroups, export);
                        _log.info("Host removed from cluster and no longer in a cluster. Export: " + export.getId() + " Remove Host: " + hostId + " Remove initiators: " + hostInitiatorIds);
                        egh.removeHosts(hostId);
                        egh.removeInitiators(hostInitiatorIds);
                    }
                } else {
                    
                    boolean isAddedToCluster = NullColumnValueGetter.isNullURI(oldCluster) 
                            && !NullColumnValueGetter.isNullURI(currentCluster)
                            && ComputeSystemHelper.isClusterInExport(_dbClient, currentCluster);
                    
                    boolean isMovedToDifferentCluster = !NullColumnValueGetter.isNullURI(oldCluster) 
                            && !NullColumnValueGetter.isNullURI(currentCluster)
                            && !oldCluster.equals(currentCluster)
                            && (ComputeSystemHelper.isClusterInExport(_dbClient, oldCluster) 
                                    || ComputeSystemHelper.isClusterInExport(_dbClient, currentCluster));
                    
                    if (isAddedToCluster || isMovedToDifferentCluster) {
                        for (ExportGroup export : getSharedExports(currentCluster)) {
                            ExportGroupState egh = getExportGroupState(exportGroups, export);
                            _log.info("Non-clustered being added to a cluster. Export: " + export.getId() + " Add Host: " + hostId + " Add initiators: " + hostInitiatorIds);
                            List<Initiator> validInitiators = ComputeSystemHelper.validatePortConnectivity(_dbClient, export, hostInitiators);
                            Collection<URI> validInitiatorIds = Collections2.transform(validInitiators, CommonTransformerFunctions.fctnDataObjectToID());
                            egh.addHosts(hostId);
                            egh.addInitiators(validInitiatorIds);
                        }
                    }
                    
                    if (isMovedToDifferentCluster) {
                        for (ExportGroup export : getSharedExports(oldCluster)) {
                            ExportGroupState egh = getExportGroupState(exportGroups, export);
                            _log.info("Removing references to previous cluster. Export: " + export.getId() + " Remove Host: " + hostId + " Remove initiators: " + hostInitiatorIds);
                            egh.removeHosts(hostId);
                            egh.removeInitiators(hostInitiatorIds);
                        }
                    }
                }
            }
            
            _log.info("Number of deleted hosts: " + deletedHosts.size());
            
            for (URI hostId : deletedHosts) {
                
                Host host = _dbClient.queryObject(Host.class, hostId);
                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, host.getId());
                Collection<URI> hostInitiatorIds = Collections2.transform(hostInitiators, CommonTransformerFunctions.fctnDataObjectToID());
                
                for (ExportGroup export : getExportGroups(host.getId(), hostInitiators)) {
                    ExportGroupState egh = getExportGroupState(exportGroups, export);
                    egh.removeHosts(host.getId());
                    egh.removeInitiators(hostInitiatorIds);
                }
                
            }
            
            _log.info("Number of deleted clusters: " + deletedClusters.size());
            
            for (URI clusterId : deletedClusters) {
                // the cluster's hosts will already be processed as deletedHosts
                List<ExportGroup> clusterExportGroups = getSharedExports(clusterId);
                for (ExportGroup export : clusterExportGroups) {
                    ExportGroupState egh = getExportGroupState(exportGroups, export);
                    egh.removeCluster(clusterId);
                }
                
            }
            
            _log.info("Number of ExportGroupStates: " + exportGroups.size());
            
            for(ExportGroupState export : exportGroups.values()) {

                _log.info("ExportGroupState for " + export.getId() + " = " + export);
                
                if (export.getInitiators().size() == 0) {
                    waitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                          String.format("Deleting export group %s", export.getId()), waitFor,
                          export.getId(), export.getId().toString(),
                          this.getClass(),
                          deleteExportGroupMethod(export.getId()),
                          null, null);
                } else {
                    waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                          String.format("Updating export group %s", export.getId()), waitFor,
                          export.getId(), export.getId().toString(),
                          this.getClass(),
                          updateExportGroupMethod(export.getId(), export.getVolumesMap(), 
                                  export.getClusters(), export.getHosts(), export.getInitiators()),
                          null, null);
                }

            }

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "processHostChanges caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void setHostSanBootTargets(URI hostId,URI volumeId) throws ControllerException {
        Host host = _dbClient.queryObject(Host.class,hostId);
        if(host != null && host.getComputeElement() != null){
            ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class,host.getComputeElement());
            
            if(computeElement != null){
                computeDeviceController.setSanBootTarget(computeElement.getComputeSystem(), computeElement.getId(), hostId, volumeId,false);
            }
        }
    }
}
