/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.builders;

import org.apache.commons.lang.ObjectUtils;

import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam;

public class FileVirtualPoolUpdateBuilder extends VirtualPoolUpdateBuilder {
    private FileVirtualPoolRestRep oldVirtualPool;
    private FileVirtualPoolUpdateParam virtualPool;

    public FileVirtualPoolUpdateBuilder(FileVirtualPoolRestRep oldVirtualPool) {
        this(oldVirtualPool, new FileVirtualPoolUpdateParam());
    }

    protected FileVirtualPoolUpdateBuilder(FileVirtualPoolRestRep oldVirtualPool, FileVirtualPoolUpdateParam virtualPool) {
        super(oldVirtualPool, virtualPool);
        this.oldVirtualPool = oldVirtualPool;
        this.virtualPool = virtualPool;
    }

    @Override
    public FileVirtualPoolRestRep getOldVirtualPool() {
        return oldVirtualPool;
    }

    @Override
    public FileVirtualPoolUpdateParam getVirtualPoolUpdate() {
        return virtualPool;
    }

    protected FileVirtualPoolProtectionParam getProtection() {
        if (virtualPool.getProtection() == null) {
            virtualPool.setProtection(new FileVirtualPoolProtectionParam());
        }
        return virtualPool.getProtection();
    }

    private Integer getOldMaxSnapshots() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getSnapshots() != null)) {
            return oldVirtualPool.getProtection().getSnapshots().getMaxSnapshots();
        }
        return null;
    }

    public FileVirtualPoolUpdateBuilder setSnapshots(Integer maxSnapshots) {
        if (!ObjectUtils.equals(maxSnapshots, getOldMaxSnapshots())) {
            getProtection().setSnapshots(new VirtualPoolProtectionSnapshotsParam(maxSnapshots));
        }
        return this;
    }

    public static FileVirtualPoolProtectionParam getProtection(FileVirtualPoolRestRep virtualPool) {
        return virtualPool != null ? virtualPool.getProtection() : null;
    }

    public static VirtualPoolProtectionSnapshotsParam getSnapshots(FileVirtualPoolRestRep virtualPool) {
        return getSnapshots(getProtection(virtualPool));
    }

    public static VirtualPoolProtectionSnapshotsParam getSnapshots(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getSnapshots() : null;
    }
    
    public FileVirtualPoolUpdateBuilder setLongTermRetention (Boolean longTermRetention) {
        if (!ObjectUtils.equals(longTermRetention, oldVirtualPool.getLongTermRetention())) {
            getVirtualPoolUpdate().setLongTermRetention(longTermRetention);
        }
        return this;
    }

}
