/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ExportVplexVolume")
public class ExportVplexVolumeService extends ViPRService {

    @Bindable
    protected ExportBlockVolumeHelper helper = new ExportBlockVolumeHelper();
		
	@Override
	public void precheck() throws Exception {
        helper.precheck();
    }
	
	@Override
	public void execute() throws Exception {
		helper.exportVolumes();
	}
}
