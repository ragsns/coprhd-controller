/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.asset.providers;

import static com.emc.sa.asset.providers.BlockProviderUtils.isLocalMirrorSupported;
import static com.emc.sa.asset.providers.BlockProviderUtils.isLocalSnapshotSupported;
import static com.emc.sa.asset.providers.BlockProviderUtils.isRPSourceVolume;
import static com.emc.sa.asset.providers.BlockProviderUtils.isRemoteSnapshotSupported;
import static com.emc.sa.asset.providers.BlockProviderUtils.isVpoolProtectedByVarray;
import static com.emc.vipr.client.core.util.ResourceUtils.name;
import static com.emc.vipr.client.core.util.ResourceUtils.stringId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.VirtualArrayRelatedResourceRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep.ProtectionRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.protection.ProtectionSetRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.model.vpool.VirtualPoolChangeRep;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.filters.ExportHostOrClusterFilter;
import com.emc.vipr.client.core.filters.ExportVirtualArrayFilter;
import com.emc.vipr.client.core.filters.FilterChain;
import com.emc.vipr.client.core.filters.RecoverPointPersonalityFilter;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.filters.SRDFSourceFilter;
import com.emc.vipr.client.core.filters.SRDFTargetFilter;
import com.emc.vipr.client.core.filters.SourceTargetVolumesFilter;
import com.emc.vipr.client.core.filters.VplexVolumeFilter;
import com.emc.vipr.client.core.filters.VplexVolumeVirtualPoolFilter;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


@Component
@AssetNamespace("vipr")
public class BlockProvider extends BaseAssetOptionsProvider {
    public static final String EXCLUSIVE_STORAGE = "exclusive";
    public static final String SHARED_STORAGE = "shared";
    public static final String RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_VALUE = "rp";
    public static final String LOCAL_ARRAY_SNAPSHOT_TYPE_VALUE = "local";
    
    private static final AssetOption EXCLUSIVE_STORAGE_OPTION = newAssetOption(EXCLUSIVE_STORAGE, "block.storage.type.exclusive");
    private static final AssetOption SHARED_STORAGE_OPTION = newAssetOption(SHARED_STORAGE, "block.storage.type.shared");
    private static final AssetOption RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_OPTION = newAssetOption(RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_VALUE, "block.snapshot.type.rp_bookmark");
    private static final AssetOption LOCAL_ARRAY_SNAPSHOT_TYPE_OPTION = newAssetOption(LOCAL_ARRAY_SNAPSHOT_TYPE_VALUE, "block.snapshot.type.local");
    
    private static List<AssetOption> NTFS_OPTIONS = Lists.newArrayList( newAssetOption("DEFAULT", "Default"),
    		newAssetOption("512", "512"), 
    		newAssetOption("1024", "1k"),
    		newAssetOption("2048", "2k"),
    		newAssetOption("4096", "4k"),
    		newAssetOption("8192", "8k"),
    		newAssetOption("16k", "16k"),
    		newAssetOption("32k", "32k"),
    		newAssetOption("64k", "64k") );

    private static List<AssetOption> FAT32_OPTIONS = Lists.newArrayList(newAssetOption("DEFAULT", "Default"),
    		newAssetOption("512", "512"), 
    		newAssetOption("1024", "1k"),
			newAssetOption("2048", "2k"),
			newAssetOption("4096", "4k"),
			newAssetOption("8192", "8k") );
    
    private static List<AssetOption> WINDOWS_FILESYSTEM_TYPES = Lists.newArrayList(
    		newAssetOption("ntfs", "ntfs"),
    		newAssetOption("fat32", "fat32") );

    public static boolean isExclusiveStorage(String storageType) {
        return EXCLUSIVE_STORAGE.equals(storageType);
    }

    public static boolean isSharedStorage(String storageType) {
        return SHARED_STORAGE.equals(storageType);
    }

    @Asset("blockStorageType")
    public List<AssetOption> getStorageTypes(AssetOptionsContext ctx) {
        return Lists.newArrayList(EXCLUSIVE_STORAGE_OPTION, SHARED_STORAGE_OPTION);
    }
    
    @Asset("sourceBlockVolume")
    @AssetDependencies("project")
    public List<AssetOption> getSourceVolumes(AssetOptionsContext ctx, URI project) {
        debug("getting source block volumes (project=%s)", project);
        return createVolumeOptions(null, listSourceVolumes(api(ctx), project));
    }


    /**
     * Get source volumes for a specific project.  If the deletionType is VIPR_ONLY, create
     * a filter that only retrieves Volumes with Host Exports
     * 
     * @param ctx
     * @param project
     * @param deletionType
     * @return
     */
    @Asset("sourceBlockVolumeWithDeletion")
    @AssetDependencies({"project", "deletionType"})
    public List<AssetOption> getSourceVolumesWithDeletion(final AssetOptionsContext ctx, URI project, String deletionType) {
        debug("getting source block volumes (project=%s)", project);
                
        List<VolumeRestRep> volumes;
        
        if( VolumeDeleteTypeEnum.valueOf(deletionType).equals(VolumeDeleteTypeEnum.VIPR_ONLY) ){
        	        	
        	final List<URI> volumesWithExports = new ArrayList<URI>();
            for(ExportGroupRestRep export : api(ctx).blockExports().findByProject(project)){
        		for(ExportBlockParam v : export.getVolumes()){
        			volumesWithExports.add(v.getId());
        		}
        	}  
        	
	        ResourceFilter<VolumeRestRep> volumesWithNoExports = new ResourceFilter<VolumeRestRep>() {
				@Override
				public boolean acceptId(URI id) {
					return true;
				}
	
				@Override
				public boolean accept(VolumeRestRep item) {
					if(volumesWithExports.contains(item.getId())){
						return false;
					}else{
						return true;
					}
				}
			};
			
			volumes = listSourceVolumes(api(ctx), project, volumesWithNoExports);
			
        }else{
        	volumes = listSourceVolumes(api(ctx), project);
        }
                
        return createVolumeOptions(null, volumes);
    }

    @Asset("exportedBlockVolume")
    @AssetDependencies("project")
    public List<AssetOption> getExportedVolumes(AssetOptionsContext ctx, URI project) {
        debug("getting source block volumes (project=%s)", project);
        final ViPRCoreClient client = api(ctx);
        // Filter volumes that are not RP Metadata
        List<URI> volumeIds = Lists.newArrayList();
        for (ExportGroupRestRep export: client.blockExports().findByProject(project)) {
            for (ExportBlockParam resource : export.getVolumes()) {
                if (ResourceType.isType(ResourceType.VOLUME, resource.getId())) {
                    volumeIds.add(resource.getId());
                }
            }
        }
        FilterChain<VolumeRestRep> filter = new FilterChain<VolumeRestRep>(RecoverPointPersonalityFilter.METADATA.not());
        List<VolumeRestRep> volumes = client.blockVolumes().getByIds(volumeIds, filter);
        return createVolumeWithVarrayOptions(client, volumes);
    }

    @Asset("deletionType")
    public List<AssetOption> getDeletionType(AssetOptionsContext ctx) {
        List<AssetOption> options = new ArrayList<>();

        options.add(newAssetOption(VolumeDeleteTypeEnum.FULL.toString(), "block.deletion.type.full"));
        options.add(newAssetOption(VolumeDeleteTypeEnum.VIPR_ONLY.toString(), "block.deletion.type.vipr_only"));

        return options;
    }
    
    @Asset("fullOnlyDeletionType")
    public List<AssetOption> getFullOnlyDeletionType(AssetOptionsContext ctx) {
        List<AssetOption> options = new ArrayList<>();

        options.add(newAssetOption(VolumeDeleteTypeEnum.FULL.toString(), "block.deletion.type.full"));

        return options;
    }
    
    @Asset("virtualPoolChangeOperation")
    public List<AssetOption> getVirtualPoolchangeOperations(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (VirtualPoolChangeOperationEnum operation : VirtualPoolChangeOperationEnum.values()) {
            options.add(newAssetOption(operation.name(), "virtualPoolChange.operation." + operation.name()));
        }
        return options;
    }

    @Asset("virtualPoolChangeVolumeWithSource")
    @AssetDependencies({"project", "blockVirtualPool"})
    public List<AssetOption> getVpoolChangeVolumes(AssetOptionsContext ctx, URI projectId, URI virtualPoolId) {
        return createVolumeOptions(api(ctx), listSourceVolumes(api(ctx), projectId, new VirtualPoolFilter(virtualPoolId)));
    }
    
    @Asset("virtualArrayChangeVolume")
    @AssetDependencies({"project", "targetVirtualArray"})
    public List<AssetOption> getVirtualArrayChangeCandidateVolumes(AssetOptionsContext ctx, URI projectId, URI varrayId) {
        ViPRCoreClient client = api(ctx);
        NamedVolumesList volumeList = client.blockVolumes().listVirtualArrayChangeCandidates(projectId, varrayId);
        List<VolumeRestRep> volumes = client.blockVolumes().getByRefs(volumeList.getVolumes());
        return createBaseResourceOptions(volumes);
    }
    
    @Asset("targetVirtualArray")
    @AssetDependencies("project")
    public List<AssetOption> getTargetVplexVolumeVirtualArrays(AssetOptionsContext ctx, URI projectId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> targets = Lists.newArrayList();
        for (VirtualArrayRestRep varray : client.varrays().getAll()) {
            targets.add(createBaseResourceOption(varray));
        }
        return targets;
    } 
    
    @Asset("targetVirtualPool")
    @AssetDependencies({"sourceBlockVolume", "virtualPoolChangeOperation"})
    public List<AssetOption> getTargetVirtualPools(AssetOptionsContext ctx, URI volumeId, String vpoolChangeOperation) {
        List<VirtualPoolChangeRep> vpoolChanges = api(ctx).blockVolumes().listVirtualPoolChangeCandidates(volumeId);
        return createVpoolChangeOptions(vpoolChangeOperation, vpoolChanges);
    }
    
    @Asset("targetVirtualPool")
    @AssetDependencies({"project", "blockVirtualPool", "virtualPoolChangeOperation"})
    public List<AssetOption> getTargetVirtualPoolsForVpool(AssetOptionsContext ctx, URI projectId, URI virtualPoolId, String vpoolChangeOperation) {
       List<VolumeRestRep> volumes = listSourceVolumes(api(ctx),  projectId, new VirtualPoolFilter(virtualPoolId));
       List<URI> volumeIds = Lists.newArrayList();
       for (VolumeRestRep volume : volumes) {
           volumeIds.add(volume.getId());
       }
       if (CollectionUtils.isNotEmpty(volumeIds)) {
           BulkIdParam input = new BulkIdParam();
           input.setIds(volumeIds);
           List<VirtualPoolChangeRep> vpoolChanges = api(ctx).blockVpools().listVirtualPoolChangeCandidates(virtualPoolId, input);
           return createVpoolChangeOptions(vpoolChangeOperation, vpoolChanges);
       }
       return Collections.emptyList();
    }
    
    @Asset("volumeExport") 
    @AssetDependencies("blockVolume")
    public List<AssetOption> getExportsForVolume(AssetOptionsContext ctx, URI volumeId ) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForVolume(ctx, volumeId);
        return createNamedResourceOptions(exports);
    }
    
    @Asset("snapshotExport") 
    @AssetDependencies("blockSnapshot")
    public List<AssetOption> getExportsForSnapshot(AssetOptionsContext ctx, URI snapshotId ) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForSnapshot(ctx, snapshotId);
        return createNamedResourceOptions(exports);
    }
    
    @Asset("volumeExport") 
    @AssetDependencies("sourceBlockVolume")
    public List<AssetOption> getExportsForSourceVolume(AssetOptionsContext ctx, URI volumeId) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForVolume(ctx, volumeId);
        return createNamedResourceOptions(exports);
    }

    @Asset("volumeExport")
    @AssetDependencies("exportedBlockVolume")
    public List<AssetOption> getExportsForExportedVolume(AssetOptionsContext ctx, URI volumeId) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForVolume(ctx, volumeId);
        return createBaseResourceOptions(api(ctx).blockExports().getByRefs(exports));
    }

    /**
     * Gets the set of unique exports for a given volume.
     * 
     * @param ctx
     *        the asset options context
     * @param volumeId
     *        the volume id.
     * @return the export resource IDs.
     */
    protected Set<NamedRelatedResourceRep> getUniqueExportsForVolume(AssetOptionsContext ctx, URI volumeId) {
        return convertITLListToRelatedResourceRestReps(api(ctx).blockVolumes().getExports(volumeId));
    }
    
    /** Gets the set of unique exports for a given snapshot  */
    protected Set<NamedRelatedResourceRep> getUniqueExportsForSnapshot(AssetOptionsContext ctx, URI snapshotId) {
        return convertITLListToRelatedResourceRestReps(api(ctx).blockSnapshots().listExports(snapshotId));
    }
    
    /** Find the unique exports for the given */
    protected Set<NamedRelatedResourceRep> convertITLListToRelatedResourceRestReps(List<ITLRestRep> exports) {
        Set<NamedRelatedResourceRep> relatedRestReps = Sets.newHashSet();
        for (ITLRestRep export : exports) {
            relatedRestReps.add(export.getExport());
        }
        return relatedRestReps;
    }
    
    @Asset("unassignedBlockVolume")
    @AssetDependencies({"host", "project"})
    public List<AssetOption> getBlockVolumes(AssetOptionsContext ctx, URI hostOrClusterId, final URI projectId) {
        ViPRCoreClient client = api(ctx);
        Set<URI> exportedBlockResources = BlockProvider.getExportedVolumes(api(ctx), projectId, hostOrClusterId, null);
        UnexportedBlockResourceFilter<VolumeRestRep> unexportedFilter = new UnexportedBlockResourceFilter<VolumeRestRep>(exportedBlockResources);
        SourceTargetVolumesFilter sourceTargetVolumesFilter = new SourceTargetVolumesFilter();
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(projectId, unexportedFilter.and(sourceTargetVolumesFilter));

        return createBaseResourceOptions(volumes);
    }
    
    @Asset("unassignedVplexBlockVolume")
    @AssetDependencies({"project", "host", "virtualArray"})
    public List<AssetOption> getBlockVolumes(AssetOptionsContext ctx, final URI projectId, URI hostOrClusterId, URI virtualArrayId) {
        // get a list of all 'source' volumes that :
        // - are in this project and not exported to the given host/cluster
        // - are a 'source' volume
        // - are vplex volumes
        ViPRCoreClient client = api(ctx);
        Set<URI> exportedBlockResources = BlockProvider.getExportedVolumes(client, projectId, hostOrClusterId, virtualArrayId);
        UnexportedBlockResourceFilter<VolumeRestRep> unexportedFilter = new UnexportedBlockResourceFilter<VolumeRestRep>(exportedBlockResources);
        SourceTargetVolumesFilter sourceTargetVolumesFilter = new SourceTargetVolumesFilter();
        VplexVolumeFilter vplexVolumeFilter = new VplexVolumeFilter();
        
        CachedResources<BlockVirtualPoolRestRep> blockVpools = new CachedResources<>(client.blockVpools());
        VplexVolumeVirtualPoolFilter virtualPoolFilter = new VplexVolumeVirtualPoolFilter(blockVpools);
        
        FilterChain<VolumeRestRep> filter = sourceTargetVolumesFilter.and(unexportedFilter).and(vplexVolumeFilter.or(virtualPoolFilter));

        // perform the query and apply the filter
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(projectId, filter);
        
        // get a list of all volumes from our list that are in the target VArray, or use the target VArray for protection
        List<BlockObjectRestRep> acceptedVolumes = getVPlexVolumesInTargetVArray(client, virtualArrayId, volumes);
        
        return createBaseResourceOptions(acceptedVolumes);
    }
    
    @Asset("unassignedBlockSnapshot")
    @AssetDependencies({"host", "project"})
    public List<AssetOption> getBlockSnapshots(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        // get a list of all snapshots that are in this project that are not exported to the given host/cluster
        Set<URI> exportedBlockResources = getExportedVolumes(api(ctx), projectId, hostOrClusterId, null);
        UnexportedBlockResourceFilter<BlockSnapshotRestRep> unexportedSnapshotFilter = 
                new UnexportedBlockResourceFilter<BlockSnapshotRestRep>(exportedBlockResources);
        return getSnapshotOptionsForProject(ctx, projectId, unexportedSnapshotFilter);    
    }
    
    public static class UnexportedBlockResourceFilter<T extends BlockObjectRestRep> extends DefaultResourceFilter<T> {
        
        /** The list of block resources ids that have been exported to this host/cluster */
        private final Set<URI> exportedBlockResources;
        
        public UnexportedBlockResourceFilter(Set<URI> exportedBlockResources) {
            this.exportedBlockResources = exportedBlockResources;
        }
        
        @Override
        public boolean acceptId(URI resourceId) {
            // accept this volume if it hasn't been exported to this host/cluster
            return !exportedBlockResources.contains(resourceId);
        }
        
    }

    @Asset("blockVirtualPool")
    public List<AssetOption> getBlockVirtualPools(AssetOptionsContext ctx) {
        debug("getting blockVirtualPools");
        return createBaseResourceOptions(api(ctx).blockVpools().getAll());
    }

    /**
     * Returns the virtual pools for a given virtualArray (initially added for the Create Volume service)
     * @param ctx
     * @param virtualArray
     * @return 
     */
    @Asset("blockVirtualPool")
    @AssetDependencies({"virtualArray"})
    public List<AssetOption> getVirtualPoolsForVirtualArray(AssetOptionsContext ctx, URI virtualArray) {
    	debug("getting virtualPoolsForVirtualArray(virtualArray=%s)", virtualArray);
        List<BlockVirtualPoolRestRep> virtualPools = api(ctx).blockVpools().getByVirtualArray(virtualArray);
    	return createVirtualPoolResourceOptions(virtualPools);
    }
    
    @Asset("blockVolume")
    @AssetDependencies("project")
    public List<AssetOption> getVolumes(AssetOptionsContext ctx, URI project) {
        debug("getting volumes (project=%s)", project);
        ViPRCoreClient client = api(ctx);
        return createVolumeOptions(client, listVolumes(client, project));
    }

    @Asset("protectedBlockVolume")
    @AssetDependencies("project")
    public List<AssetOption> getProtectedVolumes(AssetOptionsContext ctx, URI project) {
        debug("getting protectedVolumes (project=%s)", project);
        // Allow recoverpoint or SRDF sources
        ResourceFilter<VolumeRestRep> filter = RecoverPointPersonalityFilter.SOURCE.or(new SRDFSourceFilter());
        ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(project, filter);
        return createVolumeOptions(client, volumes);
    }

    @Asset("failoverTarget")
    @AssetDependencies("protectedBlockVolume")
    public List<AssetOption> getFailoverTarget(AssetOptionsContext ctx, URI protectedBlockVolume) {
        debug("getting failoverTargets (protectedBlockVolume=%s)", protectedBlockVolume);
        ViPRCoreClient client = api(ctx);
        VolumeRestRep volume = client.blockVolumes().get(protectedBlockVolume);
        
        ProtectionRestRep protection = volume.getProtection();
        if (protection != null) {
            // RecoverPoint protection
            if (protection.getRpRep() != null && protection.getRpRep().getProtectionSet() != null) {
                return getRpFailoverTargets(client, volume);
            }
            // VMAX SRDF protection
            if (protection.getSrdfRep() != null && protection.getSrdfRep().getSRDFTargetVolumes() != null
                    && protection.getSrdfRep().getSRDFTargetVolumes().size() > 0) {
                return getSrdfFailoverTargets(client, volume);
            }
        }
        return Lists.newArrayList();
    }

    protected List<AssetOption> getRpFailoverTargets(ViPRCoreClient client, VolumeRestRep volume) {
        Map<String, String> targetVolumes = Maps.newLinkedHashMap();
        URI protectionSetId = volume.getProtection().getRpRep().getProtectionSet().getId();
        ProtectionSetRestRep localProtectionSet = client.blockVolumes().getProtectionSet(volume.getId(),
                protectionSetId);
        String sourceSiteName = volume.getProtection().getRpRep().getInternalSiteName();
        CachedResources<VirtualArrayRestRep> virtualArrays = new CachedResources<VirtualArrayRestRep>(client.varrays());

        List<RelatedResourceRep> rpTargets = localProtectionSet.getVolumes();
        for (VolumeRestRep protectionSetVolume : client.blockVolumes().getByRefs(rpTargets, RecoverPointPersonalityFilter.TARGET)) {
            String targetSiteName = protectionSetVolume.getProtection().getRpRep().getInternalSiteName();
            boolean isLocal = StringUtils.equals(sourceSiteName, targetSiteName);
            String rpType = isLocal ? "local" : "remote";
            VirtualArrayRestRep virtualArray = virtualArrays.get(protectionSetVolume.getVirtualArray());
            String label = getMessage("recoverpoint.target", name(protectionSetVolume), rpType, name(virtualArray));
            targetVolumes.put(stringId(protectionSetVolume), label);
        }

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<String, String> entry : targetVolumes.entrySet()) {
            options.add(new AssetOption(entry.getKey(), entry.getValue()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> getSrdfFailoverTargets(ViPRCoreClient client, VolumeRestRep volume) {
        Map<String, String> targetVolumes = Maps.newLinkedHashMap();
        CachedResources<VirtualArrayRestRep> virtualArrays = new CachedResources<VirtualArrayRestRep>(client.varrays());

        List<VirtualArrayRelatedResourceRep> srdfTargets = volume.getProtection().getSrdfRep().getSRDFTargetVolumes();
        for (VolumeRestRep protectionSetVolume : client.blockVolumes().getByRefs(srdfTargets, new SRDFTargetFilter())) {
            VirtualArrayRestRep virtualArray = virtualArrays.get(protectionSetVolume.getVirtualArray());
            String label = getMessage("srdf.target", name(protectionSetVolume), name(virtualArray));
            targetVolumes.put(stringId(protectionSetVolume), label);
        }

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<String, String> entry : targetVolumes.entrySet()) {
            options.add(new AssetOption(entry.getKey(), entry.getValue()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }
    
    @Asset("blockSnapshot")
    @AssetDependencies({"project"})
    public List<AssetOption> getBlockSnapshotsByVolume(AssetOptionsContext ctx, URI project ) {
        debug("getting blockSnapshots (project=%s)", project);
        return getSnapshotOptionsForProject(ctx, project);
    }
    
    @Asset("blockSnapshotType")
    public List<AssetOption> getBlockSnapshotTypeLockable(AssetOptionsContext ctx) {
        debug("getting blockSnapshotTypes");
        List<AssetOption> options = Lists.newArrayList();
        options.add(LOCAL_ARRAY_SNAPSHOT_TYPE_OPTION);
        options.add(RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_OPTION);
        return options;
    }

    @Asset("blockSnapshotType")
    @AssetDependencies("snapshotBlockVolume")
    public List<AssetOption> getBlockSnapshotType(AssetOptionsContext ctx, URI blockVolume) {
        debug("getting blockSnapshotTypes (blockVolume=%s)", blockVolume);
        // These are hard coded values for now. In the future, this may be available through an API
        List<AssetOption> options = Lists.newArrayList();
        ViPRCoreClient client = api(ctx);
        VolumeRestRep volume = client.blockVolumes().get(blockVolume);
        BlockVirtualPoolRestRep virtualPool = client.blockVpools().get(volume.getVirtualPool());
        
        if (isLocalSnapshotSupported(virtualPool)) {
            options.add(LOCAL_ARRAY_SNAPSHOT_TYPE_OPTION);
        }
        
        if (isRPSourceVolume(volume)) {
            options.add(RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_OPTION);
        }
        
        return options;
    }

    private List<AssetOption> getBlockVolumesForHost(ViPRCoreClient client, URI tenant, URI host, boolean mounted) {
        return createVolumeOptions(client, null, host, BlockProviderUtils.getBlockVolumes(client, tenant, host, mounted));
    }

    private List<AssetOption> getProjectBlockVolumesForHost(ViPRCoreClient client, URI project, URI host,
            boolean mounted) {
        return createVolumeOptions(client, project, host, getProjectBlockVolumes(client, host, project, mounted));
    }

    private List<AssetOption> getBlockResourcesForHost(ViPRCoreClient client, URI tenant, URI host, boolean mounted) {
        return getBlockResourcesForHost(client, tenant, host, mounted, null);
    }

    private List<AssetOption> getProjectBlockResourcesForHost(ViPRCoreClient client, URI project, URI host,
            boolean mounted) {
        return getProjectBlockResourcesForHost(client, project, host, mounted, null);
    }

    @SuppressWarnings("unchecked")
    private List<AssetOption> getBlockResourcesForHost(ViPRCoreClient client, URI tenant, URI host, boolean mounted,
            ResourceFilter<BlockObjectRestRep> filter) {
        List<? extends BlockObjectRestRep> resources = BlockProviderUtils.getBlockResources(client, tenant, host, mounted);
        ResourceUtils.applyFilter((List<BlockObjectRestRep>) resources, filter);
        return createVolumeOptions(client, null, host, resources);
    }

    @SuppressWarnings("unchecked")
    private List<AssetOption> getProjectBlockResourcesForHost(ViPRCoreClient client, URI project, URI host,
            boolean mounted, ResourceFilter<BlockObjectRestRep> filter) {
        List<? extends BlockObjectRestRep> resources = getProjectBlockResources(client, host, project, mounted);
        ResourceUtils.applyFilter((List<BlockObjectRestRep>) resources, filter);
        return createVolumeOptions(client, project, host, resources);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("host")
    public List<AssetOption> getMountedBlockVolumesForHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "host", "project" })
    public List<AssetOption> getMountedBlockVolumesForHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getMountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "linuxHost", "project" })
    public List<AssetOption> getMountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getMountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true);
    }
        
    @Asset("mountedBlockVolume")
    @AssetDependencies({ "windowsHost", "project" })
    public List<AssetOption> getMountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true);
    }

	@Asset("fileSystemType")
	public List<AssetOption> getWindowsFilesystemTypes(AssetOptionsContext context) {
		return WINDOWS_FILESYSTEM_TYPES;
	}

	@Asset("blockSize")
	@AssetDependencies({ "fileSystemType" })
	public List<AssetOption> getWindowsBlockSize(AssetOptionsContext context,String fileSystemType) {

		if ("ntfs".equals(fileSystemType)) {
			return NTFS_OPTIONS;
		} else {
			return FAT32_OPTIONS;
		}
	}

    @Asset("mountedBlockVolume")
    @AssetDependencies("esxHost")
    public List<AssetOption> getMountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "esxHost", "project" })
    public List<AssetOption> getMountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("host")
    public List<AssetOption> getUnmountedBlockVolumesForHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({"host", "project"})
    public List<AssetOption> getUnmountedBlockVolumesForHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getUnmountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({"linuxHost", "project"})
    public List<AssetOption> getUnmountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getUnmountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({"windowsHost", "project"})
    public List<AssetOption> getUnmountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("esxHost")
    public List<AssetOption> getUnmountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({"esxHost", "project"})
    public List<AssetOption> getUnmountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false);
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("host")
    public List<AssetOption> getMountedBlockResources(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, true);
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("host")
    public List<AssetOption> getMountedBlockResourcesNoTargets(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, true, new BlockObjectSRDFTargetFilter().not());
    }

    @Asset("unmountedBlockResourceNoTargets")
    @AssetDependencies({"host"})
    public List<AssetOption> getUnmountedBlockResourcesNoTargets(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, false, new BlockObjectSRDFTargetFilter().not());
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({"host"})
    public List<AssetOption> getUnmountedBlockResources(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({"host", "project"})
    public List<AssetOption> getUnmountedBlockResources(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, host, false);
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getLinuxMountedBlockResources(AssetOptionsContext context, URI linuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), linuxHost, true);
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getLinuxMountedBlockResourcesNoTargets(AssetOptionsContext context, URI linuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), linuxHost, true, new BlockObjectSRDFTargetFilter().not());
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({"linuxHost"})
    public List<AssetOption> getLinuxUnmountedBlockResources(AssetOptionsContext context, URI linuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), linuxHost, false);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({"linuxHost", "project"})
    public List<AssetOption> getLinuxUnmountedBlockResources(AssetOptionsContext context, URI linuxHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, linuxHost, false);
    }

    @Asset("mountedBlockResourcePath")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getLinuxMountedBlockResourcePaths(AssetOptionsContext context, URI linuxHost) {
        List<? extends BlockObjectRestRep> volumes = BlockProviderUtils.getBlockResources(api(context), context.getTenant(), linuxHost, true);
        return createStringOptions(getTagValues(KnownMachineTags.getHostMountPointTagName(linuxHost), volumes));
    }
    
    @Asset("unmountedBlockResource")
    @AssetDependencies({"aixHost"})
    public List<AssetOption> getAixUnmountedBlockResources(AssetOptionsContext context, URI aixHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), aixHost, false);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({"aixHost", "project"})
    public List<AssetOption> getAixUnmountedBlockResources(AssetOptionsContext context, URI aixHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, aixHost, false);
    }
    
    @Asset("mountedBlockResource")
    @AssetDependencies("aixHost")
    public List<AssetOption> getAixMountedBlockResources(AssetOptionsContext context, URI aixHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), aixHost, true);
    }
    
    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("aixHost")
    public List<AssetOption> getAixMountedBlockResourcesNoTargets(AssetOptionsContext context, URI aixHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), aixHost, true, new BlockObjectSRDFTargetFilter().not());
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getWindowsMountedBlockResources(AssetOptionsContext context, URI windowsHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), windowsHost, true);
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getWindowsMountedBlockResourcesNoTargets(AssetOptionsContext context, URI windowsHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), windowsHost, true, new BlockObjectSRDFTargetFilter().not());
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({"windowsHost"})
    public List<AssetOption> getWindowsUnmountedBlockResources(AssetOptionsContext context, URI windowsHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), windowsHost, false);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({"windowsHost", "project"})
    public List<AssetOption> getWindowsUnmountedBlockResources(AssetOptionsContext context, URI windowsHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, windowsHost, false);
    }

    @Asset("mountedBlockResource")
    @AssetDependencies({ "esxHost" })
    public List<AssetOption> getESXMountedBlockResources(AssetOptionsContext context, URI esxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), esxHost, true);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "esxHost" })
    public List<AssetOption> getESXUnmountedBlockResources(AssetOptionsContext context, URI esxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), esxHost, false);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "esxHost", "project" })
    public List<AssetOption> getESXUnmountedBlockResources(AssetOptionsContext context, URI esxHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, esxHost, false);
    }

    @Asset("snapshotBlockVolume")
    @AssetDependencies({ "project" })
    public List<AssetOption> getSnapshotBlockVolumes(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);
        
        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {
            if (isLocalSnapshotSupported(detail.vpool) || isRPSourceVolume(detail.volume)) {
                options.add(createVolumeOption(client, null, detail.volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("blockVolumeWithSnapshot")
    @AssetDependencies({ "project" })
    public List<AssetOption> getBlockVolumesWithSnapshot(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        Set<URI> volumeIds = Sets.newHashSet();
        for (BlockSnapshotRestRep snapshot: client.blockSnapshots().findByProject(project)) {
            volumeIds.add(snapshot.getParent().getId());
        }
        List<VolumeRestRep> volumes = client.blockVolumes().getByIds(volumeIds);
        return createVolumeOptions(client, volumes);
    }

    @Asset("localSnapshotBlockVolume")
    @AssetDependencies({ "project" })
    public List<AssetOption> getLocalSnapshotBlockVolumes(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);
        
        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {
            if (isLocalSnapshotSupported(detail.vpool)) {
                options.add(createVolumeOption(client, null, detail.volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("remoteSnapshotBlockVolume")
    @AssetDependencies({ "project" })
    public List<AssetOption> getRemoteSnapshotBlockVolumes(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);
        
        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {
            if (isRemoteSnapshotSupported(detail.volume)) {
                options.add(createVolumeOption(client, null, detail.volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("localMirrorBlockVolume")
    @AssetDependencies({ "project" })
    public List<AssetOption> getLocalMirrorBlockVolumes(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        
        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {
            if (isLocalMirrorSupported(detail.vpool) && detail.volume.getConsistencyGroup() == null) {
                options.add(createVolumeOption(client, null, detail.volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("volumeWithContinuousCopies")
    @AssetDependencies("project")
    public List<AssetOption> getVolumesWithContinuousCopies(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(project, new SourceTargetVolumesFilter() {
            @Override
            public boolean acceptId(URI id) {
                return !client.blockVolumes().getContinuousCopies(id).isEmpty();
            }
        });
        return createVolumeOptions(client, volumes);
    }

    @Asset("continuousCopies")
    @AssetDependencies("volumeWithContinuousCopies")
    public List<AssetOption> getContinuousCopies(AssetOptionsContext ctx, URI volume) {
        return createBaseResourceOptions(api(ctx).blockVolumes().getContinuousCopies(volume));
    }
    
    @Asset("volumeWithoutConsistencyGroup")
    @AssetDependencies("project")
    public List<AssetOption> getVolumesWithoutConsistencyGroup(AssetOptionsContext ctx, URI project) {
        debug("getting volumes that don't belong to a consistency group (project=%s)", project);
        ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(project);
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        List<AssetOption> options = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            if (volume.getConsistencyGroup() == null) {
                options.add(createVolumeOption(client, null, volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("volumeWithFullCopies")
    @AssetDependencies("project")
    public List<AssetOption> getVolumesWithFullCopies(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(project, new DefaultResourceFilter<VolumeRestRep>() {
            @Override
            public boolean acceptId(URI id) {
                return client.blockVolumes().getFullCopies(id).size() > 0;
            }
        });
        return createVolumeOptions(client, volumes);
    }

    @Asset("fullCopy")
    @AssetDependencies("volumeWithFullCopies")
    public List<AssetOption> getFullCopies(AssetOptionsContext ctx, URI volumeId) {
        return createBaseResourceOptions(api(ctx).blockVolumes().getFullCopies(volumeId));
    }
    
    @Asset("fullCopyAvailableForDetach")
    @AssetDependencies("volumeWithFullCopies")
    public List<AssetOption> getFullCopiedDetach(AssetOptionsContext ctx, URI volumeId) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().getFullCopies(volumeId, new DefaultResourceFilter<VolumeRestRep>() {
            @Override
            public boolean accept(VolumeRestRep volume) {
                String replicaState = volume.getProtection().getFullCopyRep().getReplicaState();
                return replicaState != null &&
                        !(replicaState.equals(ReplicationState.DETACHED.name()));
            }
        });
        return createVolumeOptions(client, volumes);
    }
    
    @Asset("fullCopyAvailableForResynchronize")
    @AssetDependencies("volumeWithFullCopies")
    public List<AssetOption> getFullCopiedSynchronize(AssetOptionsContext ctx, URI volumeId) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().getFullCopies(volumeId, new DefaultResourceFilter<VolumeRestRep>() {
            @Override
            public boolean accept(VolumeRestRep volume) {
                String replicaState = volume.getProtection().getFullCopyRep().getReplicaState();
                return replicaState != null &&
                        !(replicaState.equals(ReplicationState.DETACHED.name())) &&
                        !(replicaState.equals(ReplicationState.INACTIVE.name()));
            }
        });
        return createVolumeOptions(client, volumes);
    }
    
    @Asset("fullCopyAvailableForRestore")
    @AssetDependencies("volumeWithFullCopies")
    public List<AssetOption> getFullCopiedRestore(AssetOptionsContext ctx, URI volumeId) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().getFullCopies(volumeId, new DefaultResourceFilter<VolumeRestRep>() {
            @Override
            public boolean accept(VolumeRestRep volume) {
                String replicaState = volume.getProtection().getFullCopyRep().getReplicaState();
                return replicaState != null &&
                        !(replicaState.equals(ReplicationState.RESTORED.name())) && 
                        !(replicaState.equals(ReplicationState.INACTIVE.name())) && 
                        !(replicaState.equals(ReplicationState.DETACHED.name()));
            }
        });
        return createVolumeOptions(client, volumes);
    }
    
    class VirtualPoolFilter extends DefaultResourceFilter<VolumeRestRep> {
        
        private URI virtualPoolId;
        
        public VirtualPoolFilter(URI virtualPoolId) {
            this.virtualPoolId = virtualPoolId;
        }
        
        @Override
        public boolean accept(VolumeRestRep item) {
            return ObjectUtils.equals(ResourceUtils.id(item.getVirtualPool()), virtualPoolId);
        }
        
    }
    
    @SafeVarargs
    public static List<VolumeRestRep> listSourceVolumes(ViPRCoreClient client, URI project, ResourceFilter<VolumeRestRep>... filters) {
        // Filter volumes that are not SRDF Targets, not RP Targets and not RP Metadata 
        FilterChain<VolumeRestRep> filter = new FilterChain<VolumeRestRep>(new SRDFTargetFilter().not());
        filter.and(RecoverPointPersonalityFilter.TARGET.not());
        filter.and(RecoverPointPersonalityFilter.METADATA.not());
        for (ResourceFilter<VolumeRestRep> additionalFilter : filters) {
            filter.and(additionalFilter);
        }
        return client.blockVolumes().findByProject(project, filter);
    }
    
    protected List<VolumeRestRep> listVolumes(ViPRCoreClient client, URI project) {
        return client.blockVolumes().findByProject(project);
    }
    
    protected static List<AssetOption> createVolumeWithVarrayOptions(ViPRCoreClient client, Collection<? extends BlockObjectRestRep> blockObjects) {
        List<URI> varrayIds = getVirtualArrayIds(blockObjects);
        Map<URI, VirtualArrayRestRep> varrayNames = getVirutalArrayNames(client, varrayIds);
        List<AssetOption> options = Lists.newArrayList();
        for (BlockObjectRestRep blockObject : blockObjects) {
            options.add(createVolumeWithVarrayOption(blockObject, varrayNames));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }
    
    protected static AssetOption createVolumeWithVarrayOption(BlockObjectRestRep blockObject, Map<URI, VirtualArrayRestRep> varrayNames) {
        String label = getBlockObjectLabelWithVarray(blockObject, varrayNames);
        String name = getBlockObjectName(null, blockObject);
        if (StringUtils.isNotBlank(name)) {
            label = String.format("%s: %s", name, label);
        }
        return new AssetOption(blockObject.getId(), label);
    }
    
    private static String getBlockObjectLabelWithVarray(BlockObjectRestRep blockObject, Map<URI, VirtualArrayRestRep> varrayNames) {
        if (blockObject instanceof VolumeRestRep) {
            VolumeRestRep volume = (VolumeRestRep) blockObject;
            String varrayName = varrayNames.get(volume.getVirtualArray().getId()).getName();
            return getMessage("block.unexport.volume", volume.getName(), volume.getProvisionedCapacity(), varrayName);
        }
        return blockObject.getName();
    }
    
    private static List<URI> getVirtualArrayIds(Collection<? extends BlockObjectRestRep> blockObjects) {
        List<URI> varrayIds = Lists.newArrayList();
        for (BlockObjectRestRep blockObject : blockObjects) {
            if (blockObject instanceof VolumeRestRep) {
                VolumeRestRep volume = (VolumeRestRep) blockObject;
                varrayIds.add(volume.getVirtualArray().getId());
            }
        }
        return varrayIds;
    }
    
    protected static Map<URI, VirtualArrayRestRep> getVirutalArrayNames(ViPRCoreClient client, List<URI> varrays) {
        if (varrays == null) {
            return Collections.emptyMap();
        }
        return ResourceUtils.mapById(client.varrays().getByIds(varrays));
    }
    
    protected static List<AssetOption> createVolumeOptions(ViPRCoreClient client, Collection<? extends BlockObjectRestRep> blockObjects) {
        return createVolumeOptions(client, null, null, blockObjects);
    }

    protected static List<AssetOption> createVolumeOptions(ViPRCoreClient client, URI project, URI hostId, Collection<? extends BlockObjectRestRep> blockObjects) {
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        List<AssetOption> options = Lists.newArrayList();
        for (BlockObjectRestRep blockObject : blockObjects) {
            options.add(createVolumeOption(client, hostId, blockObject, volumeNames));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected static AssetOption createVolumeOption(ViPRCoreClient client, URI hostId, BlockObjectRestRep blockObject, Map<URI, VolumeRestRep> volumeNames) {
        String label = getBlockObjectLabel(client, blockObject, volumeNames);
        String name = getBlockObjectName(hostId, blockObject);
        if (StringUtils.isNotBlank(name)) {
            label = String.format("%s: %s", name, label);
        }
        return new AssetOption(blockObject.getId(), label);
    }

    protected static Map<URI, VolumeRestRep> getProjectVolumeNames(ViPRCoreClient client, URI project) {
        if (project == null) {
            return Collections.emptyMap();
        }
        return ResourceUtils.mapById(client.blockVolumes().findByProject(project));
    }
    
    protected static String getBlockObjectName(URI hostId, BlockObjectRestRep blockObject) {
        if (hostId != null) {
            String mountPoint = KnownMachineTags.getBlockVolumeMountPoint(hostId, blockObject);
            if (StringUtils.isNotBlank(mountPoint)) {
                return mountPoint;
            }
        }

        String datastore = KnownMachineTags.getBlockVolumeVMFSDatastore(hostId, blockObject);
        if (StringUtils.isNotBlank(datastore)) {
            return datastore;
        }
        return null;
    }

    protected boolean isMounted(Collection<URI> hostIds, VolumeRestRep volume) {
        for (URI hostId : hostIds) {
            if (BlockProviderUtils.isMounted(hostId, volume)) {
                return true;
            }
        }
        return false;
    }

    private static String getBlockObjectLabel(ViPRCoreClient client, BlockObjectRestRep blockObject, Map<URI, VolumeRestRep> volumeNames) {
        if (blockObject instanceof VolumeRestRep) {
            VolumeRestRep volume = (VolumeRestRep) blockObject;
            return getMessage("block.volume", volume.getName(), volume.getProvisionedCapacity());
        }
        else if (blockObject instanceof BlockSnapshotRestRep) {
            BlockSnapshotRestRep snapshot = (BlockSnapshotRestRep)blockObject;
            return getMessage("block.snapshot.label", snapshot.getName(), getBlockSnapshotParentVolumeName(volumeNames, snapshot));
        }
        return blockObject.getName();
    }

    protected static String getBlockSnapshotParentVolumeName(Map<URI, VolumeRestRep> volumeNames, BlockSnapshotRestRep snapshot) {
        if (snapshot.getParent() != null && 
                snapshot.getParent().getId() != null &&
                volumeNames != null && 
                volumeNames.get(snapshot.getParent().getId()) != null) {
            return ResourceUtils.name(volumeNames.get(snapshot.getParent().getId()));
        }
        return getMessage("block.snapshot.unknown.volume");
    }
    
    private List<AssetOption> getSnapshotOptionsForProject(AssetOptionsContext ctx, URI project, ResourceFilter<BlockSnapshotRestRep> filter) {
        ViPRCoreClient client = api(ctx);
        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().findByProject(project, filter);
        return constructSnapshotOptions(client, project, snapshots);
    }
    
    private List<AssetOption> getSnapshotOptionsForProject(AssetOptionsContext ctx, URI project) {
        ViPRCoreClient client = api(ctx);
        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().findByProject(project);
        return constructSnapshotOptions(client, project, snapshots);
    }
    
    protected List<AssetOption> constructSnapshotOptions(ViPRCoreClient client, URI project, List<BlockSnapshotRestRep> snapshots) {
        List<AssetOption> options = Lists.newArrayList();
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        for (BlockSnapshotRestRep snapshot : snapshots) {
            options.add(new AssetOption(snapshot.getId(), getBlockObjectLabel(client, snapshot, volumeNames)));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }
    
    /**
     * Gets the set of volume IDs associated with the given exports.
     *
     * @param exports
     *        the export groups.
     * @return the set of volume IDs.
     */
    protected static Set<URI> getBlockVolumeIdsForExports(List<ExportGroupRestRep> exports) {
        Set<URI> ids = Sets.newHashSet();
        for (ExportGroupRestRep export : exports) {
            if (export.getVolumes() != null) {
                for (ExportBlockParam volume : export.getVolumes()) {
                    ids.add(volume.getId());
                }
            }
        }
        return ids;
    }

    /**
     * Gets the set of volume IDs for volumes in the given project that are exported to the given host/cluster
     * 
     * @param client An instance of the ViPRCoreClient
     * @param projectId The ViPR ID of the project 
     * @param hostOrClusterId The ViPR ID of the host/cluster 
     * 
     * @return The set of Volume IDs
     */
    protected static Set<URI> getExportedVolumes(ViPRCoreClient client, URI projectId, URI hostOrClusterId, URI virtualArrayId) {
        // determine host and cluster id
        URI hostId = BlockStorageUtils.isHost(hostOrClusterId) ? hostOrClusterId : null;
        URI clusterId = BlockStorageUtils.isCluster(hostOrClusterId) ? hostOrClusterId : null;

        // get a list of all the exports in this project that expose resources to this host/cluster
        ResourceFilter<ExportGroupRestRep> filter;
        if (virtualArrayId == null) {
            filter = new ExportHostOrClusterFilter(hostId, clusterId);
        }
        else {
            filter = new ExportHostOrClusterFilter(hostId, clusterId).and(new ExportVirtualArrayFilter(virtualArrayId));
        }
        List<ExportGroupRestRep> exports = client.blockExports().findByProject(projectId, filter);
        
        return getBlockVolumeIdsForExports(exports);
    }      
    
    /**
     * Gets the value of the specified tag from the given volumes.
     * 
     * @param tagName
     *        the tag name.
     * @param volumes
     *        the volumes.
     * @return the tag values.
     */
    public static Set<String> getTagValues(String tagName, Collection<? extends BlockObjectRestRep> volumes) {
        Set<String> values = Sets.newHashSet();
        for (BlockObjectRestRep volume : volumes) {
            String value = MachineTagUtils.getBlockVolumeTag(volume, tagName);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Returns a list of virtual pools where volume can be moved
     */
    protected List<BlockVirtualPoolRestRep> listTargetVirtualPools(AssetOptionsContext ctx, URI volumeId, ResourceFilter<BlockVirtualPoolRestRep> filter) {
        ViPRCoreClient client = api(ctx);
    	List<VirtualPoolChangeRep> vpoolChanges = client.blockVolumes().listVirtualPoolChangeCandidates(volumeId);

        List<URI> vpoolIds = Lists.newArrayList();
        for(VirtualPoolChangeRep change : vpoolChanges) {
    		if (change.getAllowed()) {
                vpoolIds.add(change.getId());
    		}
    	}
    	return client.blockVpools().getByIds(vpoolIds, filter);
    }
    
    /**
     * Gets the volume details for a collection of volumes.
     * 
     * @param client the bourne client.
     * @param volumes the collection of volumes.
     * @return the volume details.
     */
    protected static List<VolumeDetail> getVolumeDetails(ViPRCoreClient client, Collection<VolumeRestRep> volumes) {
        Set<URI> blockVirtualPoolIds = getBlockVirtualPoolIdsForVolumes(volumes);
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = getBlockVirtualPools(client, blockVirtualPoolIds);
        
        List<VolumeDetail> details = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            BlockVirtualPoolRestRep virtualPool = virtualPoolMap.get(volume.getVirtualPool().getId());
            details.add(new VolumeDetail(volume, virtualPool));
        }
        return details;
    }
    
    /**
     * Gets the unique set of BlockVirtualPool IDs for the given volumes.
     * 
     * @param volumes the volumes.
     * @return the block virtual pool IDs.
     */
    protected static Set<URI> getBlockVirtualPoolIdsForVolumes(Collection<VolumeRestRep> volumes){
        Set<URI> ids = Sets.newHashSet();
        for (VolumeRestRep volume : volumes) {
            ids.add(volume.getVirtualPool().getId());
        }
        return ids;
    }
    
    /**
     * Gets all BlockVirtualPools mapped by ID.
     * 
     * @param client the ViPR client instance.
     * @param ids the IDs.
     * @return the mapping of ID->BlockVirtualPoolRestRep
     */
    public static Map<URI, BlockVirtualPoolRestRep> getBlockVirtualPools(ViPRCoreClient client, Collection<URI> ids) {
        Map<URI, BlockVirtualPoolRestRep> virtualPools = Maps.newHashMap();
        for (BlockVirtualPoolRestRep virtualPool: client.blockVpools().getByIds(ids)) {
            virtualPools.put(virtualPool.getId(), virtualPool);
        }
        return virtualPools;
    }
    
    /**
     * Gets all BlockVirtualPools mapped by ID.
     * 
     * @param client the ViPR client instance.
     * @param volumes the volumes for which we need the VPool information.
     * @return the mapping of ID->BlockVirtualPoolRestRep
     */
    public static Map<URI, BlockVirtualPoolRestRep> getVpoolsForVolumes(ViPRCoreClient client, List<VolumeRestRep> volumes) {
        // collect the id of each vpool used by a volume in the given list
        Set<URI> vpoolIds = Sets.newHashSet();
        for (VolumeRestRep volume : volumes) {
            vpoolIds.add(volume.getVirtualPool().getId());
        }
        
        // build up a map of URI to VPool
        return getBlockVirtualPools(client, vpoolIds);
    }  
    
    /**
     * Gets all {@link VolumeRestRep}s that are either in the target VArray or use the target VArray for protection
     * @param client the ViPR client instance.
     * @param targetVArrayId the target VArray ID.
     * @param volumes the volumes we are concerned with. (These should be VPlex volumes)
     * @return List of {@link VolumeRestRep}s that are VPlex volumes that are in the target VArray
     */
    public static List<BlockObjectRestRep> getVPlexVolumesInTargetVArray(ViPRCoreClient client, URI targetVArrayId, List<VolumeRestRep> volumes) {
        // collect vpools used by these volumes
        Map<URI, BlockVirtualPoolRestRep> vpools = getVpoolsForVolumes(client, volumes);
        
        // sift through the volumes to find ones with the correct VArray 
        List<BlockObjectRestRep> acceptedVolumes = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            if (volume.getVirtualArray().getId().equals(targetVArrayId)) {
                addVolume(acceptedVolumes, volume);
            }
            else {
                // if this volume's HA type is 'distributed' and its distributed VArray matches the target VArray we can accept the volume
                URI vpoolId = volume.getVirtualPool().getId();
                BlockVirtualPoolRestRep volumeVpool = vpools.get(vpoolId);
                if (volumeVpool != null && isVpoolProtectedByVarray(volumeVpool, targetVArrayId)) {
                    addVolume(acceptedVolumes, volume);
                }
            }
        }
        
        return acceptedVolumes;
    }
    
    /**
     * Class for holding all volume detail information, currently the volume and virtual pool.
     * 
     * @author jonnymiller
     */
    public static class VolumeDetail {
        public VolumeRestRep volume;
        public BlockVirtualPoolRestRep vpool;

        public VolumeDetail(VolumeRestRep volume) {
            this.volume = volume;
        }

        public VolumeDetail(VolumeRestRep volume, BlockVirtualPoolRestRep vpool) {
            this.volume = volume;
            this.vpool = vpool;
        }
    }

    public List<AssetOption> createVirtualPoolResourceOptions(Collection<? extends VirtualPoolCommonRestRep> virtualPools) {

        List<AssetOption> options = Lists.newArrayList();

        for(VirtualPoolCommonRestRep virtualPool : virtualPools) {
            options.add(createVirtualPoolResourceOption(virtualPool));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    public AssetOption createVirtualPoolResourceOption(VirtualPoolCommonRestRep virtualPool) {
        boolean hasPools = (virtualPool.getUseMatchedPools() && !CollectionUtils.isEmpty(virtualPool.getMatchedStoragePools()) ||
                (!virtualPool.getUseMatchedPools() && !CollectionUtils.isEmpty(virtualPool.getAssignedStoragePools())));

        String label = virtualPool.getName();
        if (!hasPools) {
            label = getMessage("block.virtualPool.noStorage", virtualPool.getName());
        }

        return  new AssetOption(virtualPool.getId(), label);
    }

    protected List<AssetOption> createVpoolChangeOptions(String vpoolChangeOperation, List<VirtualPoolChangeRep> vpoolChanges) {
        List<AssetOption> options = Lists.newArrayList();
        for (VirtualPoolChangeRep vpoolChange : vpoolChanges) {
            if (vpoolChange.getAllowedChangeOperations() != null) {
                for (StringHashMapEntry allowedChangeOperation : vpoolChange.getAllowedChangeOperations()) {
                    String operation = allowedChangeOperation.getName();
                    boolean isCorrectOperation = 
                            StringUtils.isNotBlank(operation) && 
                            operation.equalsIgnoreCase(vpoolChangeOperation);
                    if (isCorrectOperation) {
                        options.add(new AssetOption(vpoolChange.getId(), vpoolChange.getName()));
                    }                
                }
            }
        }
        return options;
    }

    protected List<BlockObjectRestRep> getProjectBlockResources(ViPRCoreClient client, URI hostOrClusterId, URI project, boolean onlyMounted) {
        List<BlockObjectRestRep> blockObjects = Lists.newArrayList();

        // the list of host ids that the volume could be mounted to 
        List<URI> hostIds = getHostIds(client, hostOrClusterId, onlyMounted);
        
        // get a list of all volumes and snapshots in the project
        List<VolumeRestRep> projectVolumes = client.blockVolumes().findByProject(project);
        List<BlockSnapshotRestRep> projectSnapshots = client.blockSnapshots().findByProject(project);
        
        // cycle through every volume in the project and add the volume and its snapshots
        for (VolumeRestRep volume : projectVolumes) {
            boolean isMounted = isMounted(hostIds, volume);
            if ((onlyMounted && isMounted) || (!onlyMounted && !isMounted)){
                addVolume(blockObjects, volume, projectSnapshots);
            }
        }
        
        // remaining snapshots can be added now. We don't know their parent volume but we need them in the list anyway
        blockObjects.addAll(projectSnapshots);
        
        return blockObjects;
    }

    protected List<VolumeRestRep> getProjectBlockVolumes(ViPRCoreClient client, URI hostOrClusterId, URI project, boolean onlyMounted) {
        List<VolumeRestRep> volumes = Lists.newArrayList();

        // the list of host ids that the volume could be mounted to 
        List<URI> hostIds = getHostIds(client, hostOrClusterId, onlyMounted);
        
        // get a list of all volumes and snapshots in the project
        List<VolumeRestRep> projectVolumes = client.blockVolumes().findByProject(project);
        
        // cycle through every volume in the project and add those that match
        for (VolumeRestRep volume : projectVolumes) {
            boolean isMounted = isMounted(hostIds, volume);
            if ((onlyMounted && isMounted) || (!onlyMounted && !isMounted)){
                volumes.add(volume);
            }
        }
        return volumes;
    }

    protected List<URI> getHostIds(ViPRCoreClient client, URI hostOrClusterId,
            boolean onlyMounted) {
        List<URI> hostIds = Lists.newArrayList();

        if (onlyMounted) {
            hostIds.add(hostOrClusterId);
        }
        else {
            hostIds.addAll(HostProvider.getHostIds(client, hostOrClusterId));

            // Add Cluster ID to the end of the host ID's
            if (BlockStorageUtils.isCluster(hostOrClusterId)) {
                hostIds.add(hostOrClusterId);
            } 
            else {
                HostRestRep host = client.hosts().get(hostOrClusterId);
                if (host.getCluster() != null) {
                    hostIds.add(host.getCluster().getId());
                }
            }
        }
        return hostIds;
    }
    
    protected static void addVolume(List<BlockObjectRestRep> blockObjects, VolumeRestRep volume) {
        addVolume(blockObjects, volume, null);
    }

    /**
     * Add the volume and it's snapshots to the 'blockObjects' list.
     * 
     * When the method completes the snapshots that have been added to the blockObjects list will be removed from the snapshots list.
     */
    protected static void addVolume(List<BlockObjectRestRep> blockObjects, VolumeRestRep volume, List<BlockSnapshotRestRep> snapshots) {
        blockObjects.add(volume);
        if (CollectionUtils.isNotEmpty(snapshots)) {
            // use an iterator so we can do proper removes
            Iterator<BlockSnapshotRestRep> snapshotIter = snapshots.iterator();
            while (snapshotIter.hasNext()) {
                BlockSnapshotRestRep snap = snapshotIter.next();
                if (ResourceUtils.idEquals(snap.getParent(), volume)) {
                    blockObjects.add(snap);
                    snapshotIter.remove();
                }
            }
        }
    }

    /**
     * SRDF filter for block objects.
     */
    private static class BlockObjectSRDFTargetFilter extends DefaultResourceFilter<BlockObjectRestRep> {
        private SRDFTargetFilter filter = new SRDFTargetFilter();

        @Override
        public boolean accept(BlockObjectRestRep item) {
            if (item instanceof VolumeRestRep) {
                return filter.accept((VolumeRestRep) item);
            }
            return false;
        }
    }
}
