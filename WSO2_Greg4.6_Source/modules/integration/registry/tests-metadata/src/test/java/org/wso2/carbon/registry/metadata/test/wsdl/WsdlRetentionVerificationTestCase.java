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

package org.wso2.carbon.registry.metadata.test.wsdl;

import org.apache.axis2.AxisFault;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.authenticator.stub.LogoutAuthenticationExceptionException;
import org.wso2.carbon.automation.api.clients.authenticators.AuthenticatorClient;
import org.wso2.carbon.automation.api.clients.registry.PropertiesAdminServiceClient;
import org.wso2.carbon.automation.core.ProductConstant;
import org.wso2.carbon.automation.core.utils.UserInfo;
import org.wso2.carbon.automation.core.utils.UserListCsvReader;
import org.wso2.carbon.automation.core.utils.environmentutils.EnvironmentBuilder;
import org.wso2.carbon.automation.core.utils.environmentutils.ManageEnvironment;
import org.wso2.carbon.automation.utils.registry.RegistryProviderUtil;
import org.wso2.carbon.governance.api.common.dataobjects.GovernanceArtifact;
import org.wso2.carbon.governance.api.endpoints.dataobjects.Endpoint;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.wsdls.WsdlManager;
import org.wso2.carbon.governance.api.wsdls.dataobjects.Wsdl;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.properties.stub.PropertiesAdminServiceRegistryExceptionException;
import org.wso2.carbon.registry.properties.stub.beans.xsd.RetentionBean;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceExceptionException;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.testng.Assert.*;

public class WsdlRetentionVerificationTestCase {
    private ManageEnvironment environment;
    private int userId1 = 2;
    private UserInfo userInfo;
    private Wsdl wsdl;
    private Registry governanceRegistry;
    private PropertiesAdminServiceClient propertiesAdminServiceClient;
    private WsdlManager wsdlManager;
    private RegistryProviderUtil registryProviderUtil;
    private WsdlManager wsdlManager2;
    private String wsdlPath;
    private SimpleDateFormat dateFormat;
    private Date date;
    private Calendar calendar;
    private Wsdl wsdlAddedByFirstUser;
    private WSRegistryServiceClient wsRegistry;

    @BeforeClass(groups = "wso2.greg", alwaysRun = true)
    public void initialize() throws LoginAuthenticationExceptionException,
            RemoteException, RegistryException {

        EnvironmentBuilder builder = new EnvironmentBuilder().greg(userId1);
        environment = builder.build();

        registryProviderUtil = new RegistryProviderUtil();
        wsRegistry = registryProviderUtil
                .getWSRegistry(userId1, ProductConstant.GREG_SERVER_NAME);
        governanceRegistry = registryProviderUtil.getGovernanceRegistry(
                wsRegistry, userId1);
        wsdlManager = new WsdlManager(governanceRegistry);
    }

    @Test(groups = "wso2.greg", description = "wsdl addition for retention Verification")
    public void testAddResourcesToVerifyRetention() throws RemoteException,
            MalformedURLException,
            ResourceAdminServiceExceptionException,
            GovernanceException {


        wsdl = wsdlManager
                .newWsdl("https://svn.wso2.org/repos/wso2/carbon/platform/trunk/products/greg/modules/integration/" +
                        "registry/tests-metadata/src/test/resources/artifacts/GREG/wsdl/GeoIPService.svc.wsdl");

        wsdl.addAttribute("version", "1.0.0");
        wsdl.addAttribute("author", "Kanarupan");
        wsdl.addAttribute("description", "for retention verification");
        wsdlManager.addWsdl(wsdl);
        wsdlManager.updateWsdl(wsdl);
        wsdlPath = "/_system/governance" + wsdl.getPath();
    }

    @Test(groups = "wso2.greg", description = "Retention Verification", dependsOnMethods = "testAddResourcesToVerifyRetention")
    public void testFirstUserSetRetention() throws GovernanceException,
            RemoteException,
            PropertiesAdminServiceRegistryExceptionException,
            LogoutAuthenticationExceptionException {

        userInfo = UserListCsvReader.getUserInfo(userId1);
        propertiesAdminServiceClient = new PropertiesAdminServiceClient(
                environment.getGreg().getProductVariables().getBackendUrl(),
                environment.getGreg().getSessionCookie());

        /* getting current date and date exactly after a month */
        dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        date = new Date();
        calendar = GregorianCalendar.getInstance();
        calendar.add(Calendar.MONTH, 1);

        propertiesAdminServiceClient.setRetentionProperties(wsdlPath, "delete",
                dateFormat.format(date), dateFormat.format(calendar.getTime())); // delete
        // access
        // locked

        wsdlManager.updateWsdl(wsdl);
        RetentionBean retentionBean = propertiesAdminServiceClient
                .getRetentionProperties(wsdlPath);

        assertTrue(retentionBean.getDeleteLocked());
        assertTrue(retentionBean.getFromDate().contentEquals(
                dateFormat.format(date)));
        assertFalse(retentionBean.getWriteLocked());

        /* logout */
        new AuthenticatorClient(environment.getGreg().getBackEndUrl()).logOut();
    }

    /**
     * With SecondUser, couldn't access the artifact using
     * wsdlManager.getWsdl(path), used getAllWsdls()
     *
     * @throws Exception
     */

    @Test(groups = "wso2.greg", description = "Retention verificaiton: second user", dependsOnMethods = "testFirstUserSetRetention")
    public void testSecondUserVerifyRetention() throws Exception {

        int userId2 = 3;
        EnvironmentBuilder builder = new EnvironmentBuilder().greg(userId2);
        environment = builder.build();
        userInfo = UserListCsvReader.getUserInfo(userId2);

        propertiesAdminServiceClient = new PropertiesAdminServiceClient(
                environment.getGreg().getProductVariables().getBackendUrl(),
                environment.getGreg().getSessionCookie());

        WSRegistryServiceClient wsRegistry2 = registryProviderUtil
                .getWSRegistry(userId2, ProductConstant.GREG_SERVER_NAME);
        Registry governanceRegistry1 = registryProviderUtil
                .getGovernanceRegistry(wsRegistry2, userId2);

        wsdlManager2 = new WsdlManager(governanceRegistry1);
        Wsdl[] wsdls = wsdlManager2.getAllWsdls();

        for (Wsdl tmpWsdl : wsdls) {
            if (tmpWsdl.getQName().getLocalPart().contains("GeoIPService")) {
                wsdlAddedByFirstUser = tmpWsdl;
            }
        }

        assertTrue(wsdlAddedByFirstUser.getQName().getLocalPart()
                .contains("GeoIPService"));
        assertTrue(wsdlAddedByFirstUser.getAttribute("author").contains(
                "Kanarupan"));

        wsdlAddedByFirstUser.addAttribute("WriteAccess", "enabled");
        wsdlManager2.updateWsdl(wsdlAddedByFirstUser);
        assertTrue(wsdlAddedByFirstUser.getAttribute("WriteAccess").contains(
                "enabled"));

        RetentionBean retentionBean = propertiesAdminServiceClient
                .getRetentionProperties(wsdlPath);

        assertEquals(retentionBean.getFromDate(), dateFormat.format(date));
        assertEquals(retentionBean.getUserName(), UserListCsvReader.getUserInfo(2).getUserNameWithoutDomain());

        assertFalse(retentionBean.getWriteLocked());
        assertTrue(retentionBean.getDeleteLocked());

    }

    @Test(groups = "wso2.greg", description = "second user deletion check: blocked by first user", expectedExceptions = GovernanceException.class, dependsOnMethods = "testSecondUserVerifyRetention")
    public void testSecondUserRetentionDeleteCheck() throws GovernanceException {
        wsdlManager2.removeWsdl(wsdlAddedByFirstUser.getPath());
    }

    @AfterClass(groups = "wso2.greg", alwaysRun = true, description = "cleaning up the artifacts added")
    public void tearDown() throws AxisFault, RegistryException {

        if(wsRegistry.resourceExists("/_system/governance/trunk/services")){
            wsRegistry.delete("/_system/governance/trunk/services");
        }
        if(wsRegistry.resourceExists("/_system/governance/trunk/wsdls")){
            wsRegistry.delete("/_system/governance/trunk/wsdls");
        }

        if(wsRegistry.resourceExists("/_system/governance/trunk")){
            wsRegistry.delete("/_system/governance/trunk");
        }

        if(wsRegistry.resourceExists("/_system/governance/branches")){
            wsRegistry.delete("/_system/governance/branches");
        }
        wsRegistry = null;
        wsdl = null;
        wsdlManager = null;
        wsdlAddedByFirstUser = null;
        wsdlManager2 = null;
        date = null;
        dateFormat = null;
        calendar = null;
        governanceRegistry = null;
        environment = null;
        propertiesAdminServiceClient = null;
    }
}
