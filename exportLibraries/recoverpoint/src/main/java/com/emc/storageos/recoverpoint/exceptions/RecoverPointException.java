/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/* Generic Exception for REST operation errors */
public class RecoverPointException extends InternalException {

	private static final long serialVersionUID = 8337210596778544218L;

    /** Holds the methods used to create recover point related exceptions */
    public static final RecoverPointExceptions exceptions = ExceptionMessagesProxy.create(RecoverPointExceptions.class);

    /** Holds the methods used to create recovery point related error conditions */
    public static final RecoverPointErrors errors = ExceptionMessagesProxy.create(RecoverPointErrors.class);

    protected RecoverPointException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
