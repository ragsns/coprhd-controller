/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Element;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

/**
 */
public class DeleteClusterResourceMethod extends WinRMInvokeOperation<Boolean> {
    public static final String DELETE_CLUSTER_RESOURCE_URI = WinRMConstants.WMI_BASE_URI + "root/mscluster/MSCluster_Resource";
    public static final String CLUSTER_AVAILABLE_DISKS_ACTION_URI = DELETE_CLUSTER_RESOURCE_URI+"/DeleteResource";

    private static final XPath XPATH = XmlUtils.createXPath(String.format("ns=%s", DELETE_CLUSTER_RESOURCE_URI));
    private static final XPathExpression PATH_EXPR = XmlUtils.compileXPath(XPATH, "ns:Path");
    private static final XPathExpression RETURN_VALUE_EXPR = XmlUtils.compileXPath(XPATH, "ns:ReturnValue");

    public DeleteClusterResourceMethod(WinRMTarget target, String resourceId) {
        super(target, DELETE_CLUSTER_RESOURCE_URI, CLUSTER_AVAILABLE_DISKS_ACTION_URI);
        setSelector("name",resourceId);
    }

    @Override
    protected String createInput() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start("DeleteResource_INPUT").attr("xmlns", getResourceUri());
        xml.end();
        return xml.toString();
    }

    @Override
    protected Boolean processOutput(Element output) {
        return getReturnValue(output);
    }

    public String getPath(Element parent) {
        for (Element e : XmlUtils.selectElements(PATH_EXPR, parent)) {
            return XmlUtils.getText(e);
        }
        return null;
    }

    public boolean getReturnValue(Element parent) {
        for (Element e : XmlUtils.selectElements(RETURN_VALUE_EXPR, parent)) {
            return Boolean.parseBoolean(XmlUtils.getText(e));
        }
        return false;
    }
}
