/*
*Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/

package org.wso2.carbon.registry.governance.api.test.old;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.automation.core.ProductConstant;
import org.wso2.carbon.automation.core.utils.UserInfo;
import org.wso2.carbon.automation.core.utils.UserListCsvReader;
import org.wso2.carbon.automation.utils.registry.RegistryProviderUtil;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.services.ServiceManager;
import org.wso2.carbon.governance.api.services.dataobjects.Service;
import org.wso2.carbon.governance.api.wsdls.WsdlManager;
import org.wso2.carbon.governance.api.wsdls.dataobjects.Wsdl;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import javax.xml.namespace.QName;
import java.net.MalformedURLException;
import java.rmi.RemoteException;

import static org.testng.Assert.assertTrue;

/**
 * test case for issue  String[] children = collection.getChildren();      via Governance Registry API
 * https://wso2.org/jira/browse/CARBON-12200
 */
public class GovApiGetChildrenTestCase {
    private static final Log log = LogFactory.getLog(GovApiGetChildrenTestCase.class);

    Registry governanceRegistry;
    WSRegistryServiceClient registryWS;
    ServiceManager serviceManager;
    Service service;
    WsdlManager wsdlMgr;
    Wsdl wsdl;

    @BeforeTest(alwaysRun = true)
    public void setEnvironment()
            throws LoginAuthenticationExceptionException, RemoteException, RegistryException,
            MalformedURLException {
        int userId = 1;
        UserInfo userInfo = UserListCsvReader.getUserInfo(userId);
        registryWS =
                new RegistryProviderUtil().getWSRegistry(userId,
                        ProductConstant.GREG_SERVER_NAME);
        governanceRegistry = new RegistryProviderUtil().getGovernanceRegistry(registryWS, userId);
        serviceManager = new ServiceManager(governanceRegistry);
        wsdlMgr = new WsdlManager(governanceRegistry);
    }

    @Test(groups = {"wso2.greg", "wso2.greg.GovernanceServiceCreation"})
    public void deployArtifact() throws InterruptedException, RemoteException,
            MalformedURLException, GovernanceException {
        wsdl = wsdlMgr.newWsdl("http://ws.strikeiron.com/donotcall2_5?WSDL");
        wsdlMgr.addWsdl(wsdl);
        service = serviceManager.newService(new QName("http://my.service.ns1", "MyService"));

    }

    @Test(groups = {"wso2.greg", "wso2.greg.GovernanceServiceCreation"}, dependsOnMethods = "deployArtifact")
    public void testCheckChildList() throws Exception {
        serviceManager.addService(service);
        service.addAttribute("Owner", "Financial Department");
        serviceManager.updateService(service);


        String service_namespace = "http://example.com/demo/services";
        String service_name = "ExampleService";
        String service_path = "/_system/governance/trunk/services/com/example/demo/services/ExampleService";

        Service service2 = serviceManager.newService(new QName(service_namespace, service_name));
        serviceManager.addService(service2);

        assertTrue(registryWS.resourceExists(service_path), "Service doesn't Exists");

        Collection collection = registryWS.get("/", 0,
                Integer.MAX_VALUE);
        String[] children = collection.getChildren();
        if (children.length == 0) {
            Assert.assertFalse(true, "child list is null");
        }
        serviceManager.removeService(service2.getId());
    }


    @AfterClass(alwaysRun = true, groups = {"wso2.bps", "wso2.bps.bpelactivities"})
    public void removeArtifacts() throws RegistryException {
        serviceManager.removeService(service.getId());
        registryWS.delete("/_system/governance/trunk/endpoints/com/strikeiron");
        registryWS.delete("/_system/governance/trunk/services/com/strikeiron");
        registryWS.delete("/_system/governance/trunk/wsdls/com/strikeiron");


        governanceRegistry = null;
        registryWS = null;
        serviceManager = null;
        service = null;
        wsdlMgr = null;
        wsdl = null;
    }


}
