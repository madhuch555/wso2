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
package org.wso2.carbon.registry.lifecycle.test;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.api.clients.governance.LifeCycleAdminServiceClient;
import org.wso2.carbon.automation.api.clients.registry.ActivityAdminServiceClient;
import org.wso2.carbon.governance.custom.lifecycles.checklist.stub.CustomLifecyclesChecklistAdminServiceExceptionException;
import org.wso2.carbon.governance.custom.lifecycles.checklist.stub.beans.xsd.LifecycleBean;
import org.wso2.carbon.integration.framework.ClientConnectionUtil;
import org.wso2.carbon.integration.framework.LoginLogoutUtil;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.registry.activities.stub.RegistryExceptionException;
import org.wso2.carbon.registry.activities.stub.beans.xsd.ActivityBean;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.lifecycle.test.utils.Utils;
import org.wso2.carbon.registry.search.metadata.test.utils.GregTestUtils;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import java.rmi.RemoteException;
import java.util.Calendar;

public class PolicyDefaultLifeCycleTestCase {
    private String sessionCookie;

    private WSRegistryServiceClient registry;
    private LifeCycleAdminServiceClient lifeCycleAdminService;
    private ActivityAdminServiceClient activitySearch;
    private String userName;

    private final String ASPECT_NAME = "ServiceLifeCycle";
    private final String ACTION_PROMOTE = "Promote";
    private String policyPathDev;


    @BeforeClass
    public void init() throws Exception {
        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTP_PORT));
        sessionCookie = new LoginLogoutUtil().login();
        final String SERVER_URL = GregTestUtils.getServerUrl();
        userName = FrameworkSettings.USER_NAME;
        String password = FrameworkSettings.PASSWORD;
        lifeCycleAdminService = new LifeCycleAdminServiceClient(SERVER_URL, sessionCookie);
        activitySearch = new ActivityAdminServiceClient(SERVER_URL, userName, password);
        registry = GregTestUtils.getRegistry();
        Registry governance = GregTestUtils.getGovernanceRegistry(registry);

        String policyName = "LifeCycleTestPolicy.xml";
        policyPathDev = "/_system/governance" + Utils.addPolicy(policyName, governance);
        Thread.sleep(1000);

    }

    @Test(priority = 1, description = "Add lifecycle to a Policy")
    public void addLifecycle()
            throws RegistryException, CustomLifecyclesChecklistAdminServiceExceptionException,
                   RemoteException, InterruptedException, RegistryExceptionException {
        lifeCycleAdminService.addAspect(policyPathDev, ASPECT_NAME);
        Thread.sleep(500);
        LifecycleBean lifeCycle = lifeCycleAdminService.getLifecycleBean(policyPathDev);
        Resource service = registry.get(policyPathDev);
        Assert.assertNotNull(service, "Service Not found on registry path " + policyPathDev);
        Assert.assertEquals(service.getPath(), policyPathDev, "Service path changed after adding life cycle. " + policyPathDev);

        Assert.assertEquals(Utils.getLifeCycleState(lifeCycle), "Development", "LifeCycle State Mismatched");

        //Activity search
        Thread.sleep(1000 * 10);
    }

//    Extracting out the activity search related testing
//    https://wso2.org/jira/browse/REGISTRY-1178
    @Test( description = "Get the activity list" ,dependsOnMethods ="addLifecycle")
    public void testLifecycleAddActivities()
            throws RegistryException, CustomLifecyclesChecklistAdminServiceExceptionException,
            RemoteException, InterruptedException, RegistryExceptionException {
        ActivityBean activityObj = activitySearch.getActivities(sessionCookie, userName
                , policyPathDev, null
                , "", ActivityAdminServiceClient.FILTER_ASSOCIATE_ASPECT, 1);

        Assert.assertNotNull(activityObj, "Activity object null for Associate Aspect");
        Assert.assertNotNull(activityObj.getActivity().length > 0, "Activity list object null for Associate Aspect");
        Assert.assertTrue((activityObj.getActivity().length > 0), "Activity list object null");
        // activity string format is
        //true|admin|admin| has updated the resource |/_system/governance/trunk/policies/LifeCycleTestPolicy.xml|/_system/governance/trunk/policies/LifeCycleTestPolicy.xml|  0m ago.
        boolean lcAddActivityFound = false;
        for (String activity : activityObj.getActivity()) {
            if (activity.contains(userName) && activity.contains(policyPathDev) &&
                    activity.contains("associated the aspect ServiceLifeCycle")) {
                lcAddActivityFound = true;
            }
        }
        Assert.assertTrue(lcAddActivityFound, "LC add activity not found");
    }

    @Test(dependsOnMethods = "addLifecycle", description = "Promote to Testing")
    public void promoteToTesting()
            throws CustomLifecyclesChecklistAdminServiceExceptionException, RemoteException,
                   InterruptedException, RegistryException, RegistryExceptionException {

        lifeCycleAdminService.invokeAspect(policyPathDev, ASPECT_NAME,
                                           ACTION_PROMOTE, null);
        Thread.sleep(2000);

        LifecycleBean lifeCycle = lifeCycleAdminService.getLifecycleBean(policyPathDev);
        Resource service = registry.get(policyPathDev);
        Assert.assertNotNull(service, "Service Not found on registry path " + policyPathDev);

        Assert.assertEquals(Utils.getLifeCycleState(lifeCycle), "Testing", "LifeCycle State Mismatched");
        Assert.assertEquals(registry.get(policyPathDev).getPath(), policyPathDev,
                            "Resource not exist on trunk. Preserve original not working fine");

        //activity search for trunk
        Thread.sleep(1000 * 10);
        ActivityBean activityObjTrunk = activitySearch.getActivities(sessionCookie, userName
                , policyPathDev, Utils.formatDate(Calendar.getInstance().getTime())
                , "", ActivityAdminServiceClient.FILTER_RESOURCE_UPDATE, 1);
        Assert.assertNotNull(activityObjTrunk, "Activity object null in trunk");
        Assert.assertNotNull(activityObjTrunk.getActivity(), "Activity list object null");
        Assert.assertTrue((activityObjTrunk.getActivity().length > 0), "Activity list object null");
        String activity = activityObjTrunk.getActivity()[0];
        Assert.assertTrue(activity.contains(userName), "Activity not found. User name not found on last activity. " + activity);
        Assert.assertTrue(activity.contains("has updated the resource"),
                          "Activity not found. has updated not contain in last activity. " + activity);
        Assert.assertTrue(activity.contains("0m ago"), "Activity not found. current time not found on last activity. " + activity);

    }

    @Test(dependsOnMethods = "promoteToTesting", description = "Promote to Testing")
    public void promoteToProduction()
            throws CustomLifecyclesChecklistAdminServiceExceptionException, RemoteException,
                   InterruptedException, RegistryException, RegistryExceptionException {

        lifeCycleAdminService.invokeAspect(policyPathDev, ASPECT_NAME,
                                           ACTION_PROMOTE, null);
        Thread.sleep(2000);

        Resource service = registry.get(policyPathDev);
        Assert.assertNotNull(service, "Service Not found on registry path " + policyPathDev);

        Assert.assertEquals(registry.get(policyPathDev).getPath(), policyPathDev,
                            "Resource not exist on trunk. Preserve original not working fine");

        //activity search for trunk
        Thread.sleep(1000 * 10);
        ActivityBean activityObjTrunk = activitySearch.getActivities(sessionCookie, userName
                , policyPathDev, Utils.formatDate(Calendar.getInstance().getTime())
                , "", ActivityAdminServiceClient.FILTER_RESOURCE_UPDATE, 1);
        Assert.assertNotNull(activityObjTrunk, "Activity object null in trunk");
        Assert.assertNotNull(activityObjTrunk.getActivity(), "Activity list object null");
        Assert.assertTrue((activityObjTrunk.getActivity().length > 0), "Activity list object null");
        String activity = activityObjTrunk.getActivity()[0];
        Assert.assertTrue(activity.contains(userName), "Activity not found. User name not found on last activity. " + activity);
        Assert.assertTrue(activity.contains("has updated the resource"),
                          "Activity not found. has updated not contain in last activity. " + activity);
        Assert.assertTrue(activity.contains("0m ago"), "Activity not found. current time not found on last activity. " + activity);

    }

    @AfterClass
    public void cleanUp() throws RegistryException {
        if (policyPathDev != null) {
            registry.delete(policyPathDev);
        }
        registry = null;
        activitySearch = null;
        lifeCycleAdminService = null;
    }
}
