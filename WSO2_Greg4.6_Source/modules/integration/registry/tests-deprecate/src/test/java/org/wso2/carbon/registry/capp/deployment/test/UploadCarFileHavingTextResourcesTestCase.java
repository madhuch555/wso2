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
package org.wso2.carbon.registry.capp.deployment.test;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.application.mgt.stub.ApplicationAdminExceptionException;
import org.wso2.carbon.automation.api.clients.application.mgt.ApplicationAdminClient;
import org.wso2.carbon.automation.api.clients.application.mgt.CarbonAppUploaderClient;
import org.wso2.carbon.integration.framework.ClientConnectionUtil;
import org.wso2.carbon.integration.framework.LoginLogoutUtil;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.registry.capp.deployment.test.Utils.CAppTestUtils;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.search.metadata.test.utils.GregTestUtils;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import javax.activation.DataHandler;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;

public class UploadCarFileHavingTextResourcesTestCase {
    private String sessionCookie;
    private WSRegistryServiceClient registry;
    private CarbonAppUploaderClient cAppUploader;
    private ApplicationAdminClient adminServiceApplicationAdmin;

    private final String cAppName = "text_resources_1.0.0";
    private final String txtPath = "/_system/capps/buggggg.txt";
    private final String xmlPath = "/_system/capps/text_files.xml";


    @BeforeClass
    public void init() throws Exception {
        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTP_PORT));
        sessionCookie = new LoginLogoutUtil().login();
        final String SERVER_URL = GregTestUtils.getServerUrl();
        registry = GregTestUtils.getRegistry();
        cAppUploader = new CarbonAppUploaderClient(SERVER_URL, sessionCookie);
        adminServiceApplicationAdmin = new ApplicationAdminClient(SERVER_URL, sessionCookie);

    }

    @Test(priority = 1, description = "Upload CApp having Text Resources")
    public void uploadCApplicationWithTextResource()
            throws MalformedURLException, RemoteException, InterruptedException,
                   ApplicationAdminExceptionException {
        String filePath = GregTestUtils.getResourcePath() + File.separator +
                          "car" + File.separator + "text_resources_1.0.0.car";
        cAppUploader.uploadCarbonAppArtifact("text_resources_1.0.0.car",
                                             new DataHandler(new URL("file:///" + filePath)));

        Assert.assertTrue(CAppTestUtils.isCAppDeployed(sessionCookie, cAppName, adminServiceApplicationAdmin)
                , "Deployed CApplication not in CApp List");
    }

//    @Test(description = "Search whether CApp is in /_system/config/repository/applications", dependsOnMethods = {"uploadCApplicationWithTextResource"})
//    public void isCApplicationInRegistry() throws RegistryException {
//        registry.get("/_system/config/repository/applications/" + cAppName);
//    }

    @Test(description = "Verify Uploaded Resources", dependsOnMethods = {"uploadCApplicationWithTextResource"})
    public void isResourcesExist() throws RegistryException {

        Assert.assertTrue(registry.resourceExists(xmlPath), xmlPath + " resource does not exist");
        Assert.assertTrue(registry.resourceExists(txtPath), txtPath + " resource does not exist");

    }

    @Test(description = "Delete Carbon Application ", dependsOnMethods = {"isResourcesExist"})
    public void deleteCApplication()
            throws ApplicationAdminExceptionException, RemoteException, InterruptedException,
                   RegistryException {
        adminServiceApplicationAdmin.deleteApplication(cAppName);

        Assert.assertTrue(CAppTestUtils.isCAppDeleted(sessionCookie, cAppName, adminServiceApplicationAdmin)
                , "Deployed CApplication still in CApp List");
    }

    @Test(description = "Verify Resource Deletion", dependsOnMethods = {"deleteCApplication"})
    public void isResourcesDeleted() throws RegistryException {

        Assert.assertFalse(registry.resourceExists(xmlPath), "Resource not deleted");
        Assert.assertFalse(registry.resourceExists(txtPath), "Resource not deleted");
//        Assert.assertFalse(registry.resourceExists("/_system/config/repository/applications/" + cAppName), "Resource not deleted");

    }

    @AfterClass
    public void destroy() {
        sessionCookie = null;
        cAppUploader = null;
        adminServiceApplicationAdmin = null;
        registry = null;
    }
}
