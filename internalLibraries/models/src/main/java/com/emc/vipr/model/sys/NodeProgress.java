/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys;

import javax.xml.bind.annotation.XmlElement;

public class NodeProgress {
    public enum DownloadStatus {
        NORMAL,
        DOWNLOADERROR,
        CHECKSUMERROR, 
        CANCELLED, 
        COMPLETED;
    }
    long bytesDownloaded;
    int downloadErrorCount;
    int checksumErrorCount;
    DownloadStatus status;
    
    public NodeProgress() {}
    public NodeProgress(long bytes, DownloadStatus s, int dCount, int cCount) {
        bytesDownloaded = bytes;
        status = s;
        downloadErrorCount = dCount;
        checksumErrorCount = cCount;
    }

    @XmlElement (name = "bytes_downloaded")
    public long getBytesDownloaded(){
         return bytesDownloaded;
    }

    public void setBytesDownloaded(long bytesDownloaded) {
        this.bytesDownloaded = bytesDownloaded;
    }
    
    @XmlElement (name = "download_status")
    public DownloadStatus getStatus(){
        return status;
    }

    public void setStatus(DownloadStatus status) {
        this.status = status;
    }
    
    @XmlElement (name = "download_error_count")
    public int getDownloadErrorCount(){
        return downloadErrorCount;
    }

    public void setDownloadErrorCount(int downloadErrorCount) {
        this.downloadErrorCount = downloadErrorCount;
    }
    
    @XmlElement (name = "checksum_error_count")
    public int getChecksumErrorCount(){
        return checksumErrorCount;
    }

    public void setChecksumErrorCount(int checksumErrorCount) {
        this.checksumErrorCount = checksumErrorCount;
    }
}
