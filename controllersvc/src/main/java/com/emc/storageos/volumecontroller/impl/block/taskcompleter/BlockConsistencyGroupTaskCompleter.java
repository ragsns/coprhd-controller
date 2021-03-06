/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public abstract class BlockConsistencyGroupTaskCompleter extends TaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockConsistencyGroupTaskCompleter.class);

    private URI _consistencyGroupURI;

    public BlockConsistencyGroupTaskCompleter(Class clazz, URI consistencyGroup, String opId) {
        super(clazz, consistencyGroup, opId);
        _consistencyGroupURI = consistencyGroup;
    }

    public void recordBourneBlockConsistencyGroupEvent(DbClient dbClient, URI consistencyGroupURI,
                                             RecordableEventManager.EventType evtType,
                                             Operation.Status status, String desc)
            throws Exception {
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupURI);
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(consistencyGroup, evtType.name(),
                desc, "", dbClient, ControllerUtils.BLOCK_EVENT_SERVICE, RecordType.Event.name(),
                ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _log.info("Bourne {} event recorded", evtType.name());
        } catch (Throwable t) {
            _log.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType.name(), t);
        }
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException{
        updateWorkflowStatus(status, coded);
    }

    public URI getConsistencyGroupURI() {
        return _consistencyGroupURI;
    }
}
