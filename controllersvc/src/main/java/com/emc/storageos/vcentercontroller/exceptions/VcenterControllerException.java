/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.vcentercontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;

public class VcenterControllerException extends ControllerException {
	
	private static final long serialVersionUID = -8944213196467572717L;

	public static final VcenterControllerExceptions exceptions = ExceptionMessagesProxy
            .create(VcenterControllerExceptions.class);
	
    protected VcenterControllerException(final ServiceCode code, final Throwable cause,
                                         final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

}
