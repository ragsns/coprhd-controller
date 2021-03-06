/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.constraint;

import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.EndpointUtility;

import java.net.URI;


/**
 * Constraint for querying a record by alias
 */
public interface AlternateIdConstraint extends Constraint {
    /**
     * Factory for creating alternate ID constraint
     */
    public static class Factory {
        public static AlternateIdConstraint getFileShareNativeIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), altId);
        }
        
        public static AlternateIdConstraint getFileSystemMountPathConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("mountPath"), altId);
        }

        public static AlternateIdConstraint getVolumeNativeGuidConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), altId);
        }

        public static AlternateIdConstraint getVolumeNativeIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeId"), altId);
        }

        public static AlternateIdConstraint getFileSystemNativeGUIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), altId);
        }
        
        public static AlternateIdConstraint getUnManagedExportMaskPathConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(UnManagedExportMask.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("maskingViewPath"), altId);
        }
        
        public static AlternateIdConstraint getUnManagedExportMaskKnownInitiatorConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(UnManagedExportMask.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("knownInitiatorNetworkIds"), altId);
        }
        
        public static AlternateIdConstraint getUnManagedVolumeInitiatorNetworkIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(UnManagedVolume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("initiatorNetworkIds"), altId);
        }
        
        public static AlternateIdConstraint getVolumeInfoNativeIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(UnManagedVolume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), altId);
        }
         
         public static AlternateIdConstraint getFileSystemInfoNativeGUIdConstraint(String altId) {
             DataObjectType doType = TypeMap.getDoType(UnManagedFileSystem.class);
             return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), altId);
         }

        public static AlternateIdConstraint getVolumeWwnConstraint(String wwn) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("wwn"), wwn);
        }
        
        public static AlternateIdConstraint getBlockSnapshotWwnConstraint(String wwn) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("wwn"), wwn);
        }

        public static AlternateIdConstraint getStorageDeviceSmisIpConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(StorageSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("smisProviderIP"), altId);
        }
        
        public static AlternateIdConstraint getStorageDeviceSerialNumberConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(StorageSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("serialNumber"), altId);
        }

        public static AlternateIdConstraint getStoragePortEndpointConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("portNetworkId"), altId);
        }
        
        public static AlternateIdConstraint getExportMasksByPort(String portId) {
            DataObjectType doType = TypeMap.getDoType(ExportMask.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("storagePorts"), portId);
        }
        
        public static AlternateIdConstraint getUnManagedMaskByPort(String portId) {
        	DataObjectType doType = TypeMap.getDoType(UnManagedExportMask.class);
        	return new AlternateIdConstraintImpl(doType.getColumnField("knownStoragePortUris"), portId);
        }

        public static AlternateIdConstraint getVpoolTypeVpoolConstraint(VirtualPool.Type vitualPoolType) {
            DataObjectType doType = TypeMap.getDoType(VirtualPool.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("type"), vitualPoolType.name());
        }

        public static AlternateIdConstraint getTenantOrgAttributeConstraint(String attribute) {
            DataObjectType doType = TypeMap.getDoType(TenantOrg.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("attributes"), attribute);
        }

        public static AlternateIdConstraint getProviderByInterfaceTypeConstraint(String interfaceType) {
            DataObjectType doType = TypeMap.getDoType(StorageProvider.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("interfaceType"), interfaceType);
        }
        
        public static AlternateIdConstraint getSMISProviderByProviderIDConstraint(String providerID) {
            DataObjectType doType = TypeMap.getDoType(SMISProvider.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("providerID"), providerID);
        }
        
        public static AlternateIdConstraint getStorageProviderByProviderIDConstraint(String providerID) {
            DataObjectType doType = TypeMap.getDoType(StorageProvider.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("providerID"), providerID);
        }

        public static AlternateIdConstraint getStorageSystemByMgmtAccessPointConstraint(String mgmtAccessPoint) {
            DataObjectType doType = TypeMap.getDoType(StorageSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("mgmtAccessPoint"), mgmtAccessPoint);
        }
        
        public static AlternateIdConstraint getStorageSystemByNativeGuidConstraint(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(StorageSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }

        public static AlternateIdConstraint getSnapshotNativeGuidConstraint(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(Snapshot.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }
        
        public static AlternateIdConstraint getStoragePortByNativeGuidConstraint(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }

        public static AlternateIdConstraint getStoragePoolByNativeGuidConstraint(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(StoragePool.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }

        public static AlternateIdConstraint getBlockSnapshotsByNativeGuid(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }
        
        public static AlternateIdConstraint getQuotaDirsByNativeGuid(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(QuotaDirectory.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }
        
        public static AlternateIdConstraint getMirrorByNativeGuid(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(BlockMirror.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }

        public static AlternateIdConstraint getStoragePoolSettingByIDConstraint(String poolsettingID) {
            DataObjectType doType = TypeMap.getDoType(StoragePoolSetting.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("poolsettingID"), poolsettingID);
        }

        public static Constraint getStorageHADomainByNativeGuidConstraint(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(StorageHADomain.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }

        public static AlternateIdConstraint getBlockObjectsByConsistencyGroup(String cgId) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("consistencyGroup"), cgId);
        }

        public static AlternateIdConstraint getBlockSnapshotsBySnapsetLabel(String label) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("snapsetLabel"), label);
        }

        public static AlternateIdConstraint getVirtualArrayStoragePoolsConstraint(String varrayId) {
            DataObjectType doType = TypeMap.getDoType(StoragePool.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("taggedVirtualArrays"), varrayId);
        }

        public static AlternateIdConstraint getImplicitVirtualArrayStoragePortsConstraint(String varrayId) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("connectedVirtualArrays"), varrayId);
        }
        
        public static AlternateIdConstraint getAssignedVirtualArrayStoragePortsConstraint(String varrayId) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("assignedVirtualArrays"), varrayId);
        }

        public static AlternateIdConstraint getVirtualArrayStoragePortsConstraint(String varrayId) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("taggedVirtualArrays"), varrayId);
        }

        public static AlternateIdConstraint getImplicitVirtualArrayStoragePoolsConstraint(String varrayId) {
            DataObjectType doType = TypeMap.getDoType(StoragePool.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("connectedVirtualArrays"), varrayId);
        }
        
        public static AlternateIdConstraint getVirtualArrayFileSharesConstraint(String varrayId) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("varray"), varrayId);
        }
        
        public static AlternateIdConstraint getExportMaskInitiatorConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(ExportMask.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("initiators"), altId);
        }

        public static AlternateIdConstraint getNetworkStoragePortConstraint(String networkId) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("network"), networkId);
        }

        public static AlternateIdConstraint getEndpointNetworkConstraint(String endpoint) {
            DataObjectType doType = TypeMap.getDoType(Network.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("endpoints"),
                    EndpointUtility.changeCase(endpoint));
        }

        public static AlternateIdConstraint getNetworkDeviceSmisIpConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(NetworkSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("smisProviderIP"), altId);
        }

        public static AlternateIdConstraint getFCZoneReferenceKeyConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(FCZoneReference.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("pwwnKey"), altId);
        }

        public static AlternateIdConstraint getFCEndpointRemotePortNameConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(FCEndpoint.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("remotePortName"), altId);
        }

        public static AlternateIdConstraint getFCEndpointByFabricWwnConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(FCEndpoint.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("fabricWwn"), altId);
        }
        
        public static AlternateIdConstraint getVpoolProtectionVarraySettingsConstraint(String virtualPoolId) {
            DataObjectType doType = TypeMap.getDoType(VpoolProtectionVarraySettings.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("virtualPool"), virtualPoolId);
        }

        public static AlternateIdConstraint getProviderStorageSystemConstraint(
                String providerId) {
            DataObjectType doType = TypeMap.getDoType(StorageSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("providers"),
                    providerId);
        }
        
        public static AlternateIdConstraint getCloneReplicationGroupInstanceConstraint(
                String replicaGroupInstance) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("replicationGroupInstance"),
                    replicaGroupInstance);
        }
        /**
         * Policy Names matching an Array will be returned.
         * Policy ID format : serialID-PolicyName
         * @param policyID
         * @return
         */
        public static AlternateIdConstraint getAutoTieringPolicyByNativeGuidConstraint(String policyID) {
            DataObjectType doType = TypeMap.getDoType(AutoTieringPolicy.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"),
                    policyID);
        }
        /**
         * Policy Names matching across Arrays will be returned.
         * @param policyName
         * @return
         */
        public static AlternateIdConstraint getFASTPolicyByNameConstraint(String policyName) {
            DataObjectType doType = TypeMap.getDoType(AutoTieringPolicy.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("policyName"),
                    policyName);
        }


        public static AlternateIdConstraint getPoolFASTPolicyConstraint(String poolId) {
            DataObjectType doType = TypeMap.getDoType(AutoTieringPolicy.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("pools"),
                    poolId);
        }

        public static AlternateIdConstraint getStorageTierFASTPolicyConstraint(String autoTieringPolicy) {
            DataObjectType doType = TypeMap.getDoType(StorageTier.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("autoTieringPolicies"),
                    autoTieringPolicy);
        }

        public static AlternateIdConstraint getStorageTierByIdConstraint(String tiernativeGuid) {
            DataObjectType doType = TypeMap.getDoType(StorageTier.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"),
                    tiernativeGuid);
        }

        public static AlternateIdConstraint getDriveTypeStoragePoolConstraint(String driveType) {
            DataObjectType doType = TypeMap.getDoType(StoragePool.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("supportedDriveTypes"),
                    driveType);
        }

        public static AlternateIdConstraint getInitiatorPortInitiatorConstraint(String initiatorPort) {
            DataObjectType doType = TypeMap.getDoType(Initiator.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("iniport"),
                    EndpointUtility.changeCase(initiatorPort));
        }
        
        public static AlternateIdConstraint getIpInterfaceIpAddressConstraint(String ipAddress) {
            DataObjectType doType = TypeMap.getDoType(IpInterface.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("ipAddress"), EndpointUtility.changeCase(ipAddress));
        }

        public static AlternateIdConstraint getExportGroupInitiatorConstraint(String initiatorId) {
            DataObjectType doType = TypeMap.getDoType(ExportGroup.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("initiators"), initiatorId);
        }

        public static AlternateIdConstraint getUserIdsByUserName(String userName) {
            DataObjectType doType = TypeMap.getDoType(StorageOSUserDAO.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("username"), userName);
        }

        public static AlternateIdConstraint getAuthnProviderDomainConstraint(String domain) {
            DataObjectType doType = TypeMap.getDoType(AuthnProvider.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("domains"), domain);
        }

        public static AlternateIdConstraint getVolumeByAssociatedVolumesConstraint(String volumeId) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("associatedVolumes"), volumeId);
        }
        
        public static AlternateIdConstraint getStorageSystemByAssociatedSystemConstraint(String SystemId) {
            DataObjectType doType = TypeMap.getDoType(StorageSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("associatedStorageSystems"), SystemId);
        }

        public static AlternateIdConstraint getProtectionSystemByNativeGuidConstraint(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(ProtectionSystem.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }
        
        public static AlternateIdConstraint getRAGroupByNativeGuidConstraint(String nativeGuid) {
            DataObjectType doType = TypeMap.getDoType(RemoteDirectorGroup.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), nativeGuid);
        }

        public static AlternateIdConstraint getRPSiteArrayProtectionSystemConstraint(String protectionSystemId) {
            DataObjectType doType = TypeMap.getDoType(RPSiteArray.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("rpProtectionSystem"), protectionSystemId);
        }

        public static AlternateIdConstraint getRPSiteArrayByStorageSystemConstraint(String storageDevice) {
            DataObjectType doType = TypeMap.getDoType(RPSiteArray.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("storageSystem"), storageDevice);
        }
        public static AlternateIdConstraint getProxyTokenUserNameConstraint(String userName) {
            DataObjectType doType = TypeMap.getDoType(ProxyToken.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("username"), userName);
        }

        public static AlternateIdConstraint getRequestedTokenMapTokenIdConstraint(String tokenId) {
            DataObjectType doType = TypeMap.getDoType(RequestedTokenMap.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("tokenId"), tokenId);
        }
        
        public static AlternateIdConstraint getDecommissionedResourceIDConstraint(String resourceId) {
            DataObjectType doType = TypeMap.getDoType(DecommissionedResource.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("decommissionedId"), resourceId);
        }

        public static AlternateIdConstraint getDecommissionedResourceNativeGuidConstraint(String resourceId) {
            DataObjectType doType = TypeMap.getDoType(DecommissionedResource.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), resourceId);
        }

        public static AlternateIdConstraint getConstraint(Class<? extends DataObject> type,
                                                          String columnField,
                                                          String queryCond) {
            DataObjectType doType = TypeMap.getDoType(type);
            return new AlternateIdConstraintImpl(doType.getColumnField(columnField), queryCond);
       }
        
        public static AlternateIdConstraint
        getExportMaskByNameConstraint(String maskName) {
            DataObjectType doType = TypeMap.getDoType(ExportMask.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("maskName"),
                    maskName);
        }
        
        public static AlternateIdConstraint getBlockConsistencyGroupByAlternateNameConstraint(String alternateName) {
            DataObjectType doType = TypeMap.getDoType(BlockConsistencyGroup.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("alternateLabel"), alternateName);
        }
        
        public static AlternateIdConstraint getVirtualDataCenterByShortIdConstraint(String vdcShortId) {
            DataObjectType doType = TypeMap.getDoType(VirtualDataCenter.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("shortId"), vdcShortId);
        }
        
        public static AlternateIdConstraint getVirtualPoolByMirrorVpool(String mirrorVpool) {
            DataObjectType doType = TypeMap.getDoType(VirtualPool.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("mirrorVirtualPool"), mirrorVpool);
        }

        /**
         * Deprecated - Needed only for 2.1 migration callback.
         * @param cg
         * @return
         */
        @Deprecated
        public static AlternateIdConstraint getVolumesByConsistencyGroup(String cg) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("consistencyGroups"),
                    cg);
        }
        
        /**
         * Deprecated - Needed only for 2.1 migration callback.
         * @param cg
         * @return
         */
        @Deprecated
        public static AlternateIdConstraint getBlockSnapshotByConsistencyGroup(String cg) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("consistencyGroups"),
                    cg);
        }

        public static AlternateIdConstraint getTasksByRequestIdConstraint(String requestId) {
            DataObjectType doType = TypeMap.getDoType(Task.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("requestId"), requestId);
        }
        
        public static AlternateIdConstraint getCustomConfigByConfigType(String configType) {
            DataObjectType doType = TypeMap.getDoType(CustomConfig.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("configType"),
                    configType);
        }
    

        public static AlternateIdConstraint getTasksByResourceConstraint(URI resourceId) {
            DataObjectType doType = TypeMap.getDoType(Task.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("resource"), resourceId.toString());
        }
        
        public static AlternateIdConstraint getFileExportRuleConstraint(String fsExportIndex) {
            DataObjectType doType = TypeMap.getDoType(FileExportRule.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("fsExportIndex"), fsExportIndex);
        }

        public static AlternateIdConstraint getSnapshotExportRuleConstraint(String snapExportIndex) {
            DataObjectType doType = TypeMap.getDoType(FileExportRule.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("snapshotExportIndex"), snapExportIndex);
        }
        
	    public static AlternateIdConstraint getFileExporRuleNativeGUIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(UnManagedFileExportRule.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), altId);
        }
	    
	    public static AlternateIdConstraint getFileCifsACLNativeGUIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(UnManagedCifsShareACL.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("nativeGuid"), altId);
        }
	    
        public static AlternateIdConstraint getVolumesByAssociatedId(String volumeId) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("associatedVolumes"), volumeId);
        }
        
        public static AlternateIdConstraint getWorkflowByOrchTaskId(String orchTaskId) {
            DataObjectType doType = TypeMap.getDoType(Workflow.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("orchTaskId"), orchTaskId);
        }
        
        public static AlternateIdConstraint getRpSourceVolumeByTarget(String targetVolumeId) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("rpTargets"),
                    targetVolumeId);
        }
            
        public static AlternateIdConstraint getSnapshotShareACLConstraint(String snapshotShareACLIndex) {
            DataObjectType doType = TypeMap.getDoType(CifsShareACL.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("snapshotShareACLIndex"), snapshotShareACLIndex);
        }
        
        public static AlternateIdConstraint getFileSystemShareACLConstraint(String fileSystemShareACLIndex) {
            DataObjectType doType = TypeMap.getDoType(CifsShareACL.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("fileSystemShareACLIndex"), fileSystemShareACLIndex);
        }
    }

}
