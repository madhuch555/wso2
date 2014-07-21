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
package org.wso2.carbon.registry.lifecycle.test.old;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.api.clients.governance.LifeCycleAdminServiceClient;
import org.wso2.carbon.automation.api.clients.registry.ActivityAdminServiceClient;
import org.wso2.carbon.automation.core.ProductConstant;
import org.wso2.carbon.automation.core.utils.UserInfo;
import org.wso2.carbon.automation.core.utils.UserListCsvReader;
import org.wso2.carbon.automation.core.utils.environmentutils.EnvironmentBuilder;
import org.wso2.carbon.automation.core.utils.environmentutils.ManageEnvironment;
import org.wso2.carbon.automation.utils.registry.RegistryProviderUtil;
import org.wso2.carbon.governance.custom.lifecycles.checklist.stub.CustomLifecyclesChecklistAdminServiceExceptionException;
import org.wso2.carbon.governance.custom.lifecycles.checklist.stub.beans.xsd.LifecycleBean;
import org.wso2.carbon.governance.lcm.stub.LifeCycleManagementServiceExceptionException;
import org.wso2.carbon.registry.activities.stub.RegistryExceptionException;
import org.wso2.carbon.registry.activities.stub.beans.xsd.ActivityBean;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.lifecycle.test.utils.LifeCycleUtils;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import java.rmi.RemoteException;
import java.util.Calendar;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class SchemaDefaultLCTestCase {
    private int userId = 1;
    private UserInfo userInfo = UserListCsvReader.getUserInfo(userId);
    private ManageEnvironment environment;

    private WSRegistryServiceClient wsRegistry;
    private LifeCycleAdminServiceClient lifeCycleAdminServiceClient;
    private ActivityAdminServiceClient activityAdminServiceClient;

    private final String ASPECT_NAME = "ServiceLifeCycle";
    private final String ACTION_PROMOTE = "Promote";
    private String schemaPathDev;


    /**
     * @throws Exception
     */
    @BeforeClass
    public void init() throws Exception {
        EnvironmentBuilder builder = new EnvironmentBuilder().greg(userId);
        environment = builder.build();

        lifeCycleAdminServiceClient = new LifeCycleAdminServiceClient(
                environment.getGreg()
                        .getProductVariables()
                        .getBackendUrl(),
                userInfo.getUserName(),
                userInfo.getPassword());
        activityAdminServiceClient = new ActivityAdminServiceClient(environment.getGreg()
                                                                            .getProductVariables()
                                                                            .getBackendUrl(),
                                                                    userInfo.getUserName(),
                                                                    userInfo.getPassword());

        RegistryProviderUtil registryProviderUtil = new RegistryProviderUtil();
        wsRegistry = registryProviderUtil.getWSRegistry(userId, ProductConstant.GREG_SERVER_NAME);
        Registry governance = registryProviderUtil.getGovernanceRegistry(wsRegistry, userId);

        String schemaName = "LifeCycleTest.xsd";
        schemaPathDev = "/_system/governance" + LifeCycleUtils.addSchema(schemaName, governance);
        Thread.sleep(1000);

    }

    /**
     * @throws org.wso2.carbon.registry.core.exceptions.RegistryException
     *
     * @throws org.wso2.carbon.governance.custom.lifecycles.checklist.stub.CustomLifecyclesChecklistAdminServiceExceptionException
     *
     * @throws java.rmi.RemoteException
     * @throws InterruptedException
     * @throws org.wso2.carbon.registry.activities.stub.RegistryExceptionException
     *
     */
    @Test(groups = "wso2.greg", description = "Add lifecycle to a Schema")
    public void addLifecycle()
            throws RegistryException, CustomLifecyclesChecklistAdminServiceExceptionException,
                   RemoteException, InterruptedException, RegistryExceptionException {
        lifeCycleAdminServiceClient.addAspect(schemaPathDev, ASPECT_NAME);
        Thread.sleep(500);
        LifecycleBean lifeCycle = lifeCycleAdminServiceClient.getLifecycleBean(schemaPathDev);
        Resource service = wsRegistry.get(schemaPathDev);
        assertNotNull(service, "Service Not found on registry path " + schemaPathDev);
        assertEquals(service.getPath(), schemaPathDev, "Service path changed after adding life cycle. " + schemaPathDev);

        assertEquals(LifeCycleUtils.getLifeCycleState(lifeCycle), "Development", "LifeCycle State Mismatched");


        //Activity search
        Thread.sleep(1000 * 10);
        ActivityBean activityObj = activityAdminServiceClient.getActivities(environment.getGreg().getSessionCookie(), userInfo.getUserName()
                , schemaPathDev, LifeCycleUtils.formatDate(Calendar.getInstance().getTime())
                , "", ActivityAdminServiceClient.FILTER_ASSOCIATE_ASPECT, 1);
        assertNotNull(activityObj, "Activity object null for Associate Aspect");
        assertNotNull(activityObj.getActivity(), "Activity list object null for Associate Aspect");
        assertTrue((activityObj.getActivity().length > 0), "Activity list object null");
        String activity = activityObj.getActivity()[0];
        assertTrue(activity.contains(userInfo.getUserName()), "User name not found on activity last activity. " + activity);
        assertTrue(activity.contains("associated the aspect ServiceLifeCycle"),
                   "associated the aspect ServiceLifeCycle not contain in last activity. " + activity);
        assertTrue(activity.contains("0m ago"), "current time not found on activity. " + activity);


    }

    /**
     * @throws org.wso2.carbon.governance.custom.lifecycles.checklist.stub.CustomLifecyclesChecklistAdminServiceExceptionException
     *
     * @throws java.rmi.RemoteException
     * @throws InterruptedException
     * @throws org.wso2.carbon.registry.core.exceptions.RegistryException
     *
     * @throws org.wso2.carbon.registry.activities.stub.RegistryExceptionException
     *
     */
    @Test(groups = "wso2.greg", dependsOnMethods = {"addLifecycle"}, description = "Promote to Testing")
    public void promoteToTesting()
            throws CustomLifecyclesChecklistAdminServiceExceptionException, RemoteException,
                   InterruptedException, RegistryException, RegistryExceptionException {

        lifeCycleAdminServiceClient.invokeAspect(schemaPathDev, ASPECT_NAME,
                                                 ACTION_PROMOTE, null);
        Thread.sleep(2000);

        LifecycleBean lifeCycle = lifeCycleAdminServiceClient.getLifecycleBean(schemaPathDev);
        Resource service = wsRegistry.get(schemaPathDev);
        assertNotNull(service, "Service Not found on registry path " + schemaPathDev);

        assertEquals(LifeCycleUtils.getLifeCycleState(lifeCycle), "Testing", "LifeCycle State Mismatched");
        assertEquals(wsRegistry.get(schemaPathDev).getPath(), schemaPathDev,
                     "Resource not exist on trunk. Preserve original not working fine");

        //activity search for trunk
        Thread.sleep(1000 * 10);
        ActivityBean activityObjTrunk = activityAdminServiceClient.getActivities(environment.getGreg().getSessionCookie(), userInfo.getUserName()
                , schemaPathDev, LifeCycleUtils.formatDate(Calendar.getInstance().getTime())
                , "", ActivityAdminServiceClient.FILTER_RESOURCE_UPDATE, 1);
        assertNotNull(activityObjTrunk, "Activity object null in trunk");
        assertNotNull(activityObjTrunk.getActivity(), "Activity list object null");
        assertTrue((activityObjTrunk.getActivity().length > 0), "Activity list object null");
        String activity = activityObjTrunk.getActivity()[0];
        assertTrue(activity.contains(userInfo.getUserName()), "Activity not found. User name not found on last activity. " + activity);
        assertTrue(activity.contains("has updated the resource"),
                   "Activity not found. has updated not contain in last activity. " + activity);
        assertTrue(activity.contains("0m ago"), "Activity not found. current time not found on last activity. " + activity);

    }

    /**
     * @throws org.wso2.carbon.governance.custom.lifecycles.checklist.stub.CustomLifecyclesChecklistAdminServiceExceptionException
     *
     * @throws java.rmi.RemoteException
     * @throws InterruptedException
     * @throws org.wso2.carbon.registry.core.exceptions.RegistryException
     *
     * @throws org.wso2.carbon.registry.activities.stub.RegistryExceptionException
     *
     */
    @Test(groups = "wso2.greg", dependsOnMethods = {"promoteToTesting"}, description = "Promote to Testing")
    public void promoteToProduction()
            throws CustomLifecyclesChecklistAdminServiceExceptionException, RemoteException,
                   InterruptedException, RegistryException, RegistryExceptionException {

        lifeCycleAdminServiceClient.invokeAspect(schemaPathDev, ASPECT_NAME,
                                                 ACTION_PROMOTE, null);
        Thread.sleep(2000);

        Resource service = wsRegistry.get(schemaPathDev);
        assertNotNull(service, "Service Not found on registry path " + schemaPathDev);

        assertEquals(wsRegistry.get(schemaPathDev).getPath(), schemaPathDev,
                     "Resource not exist on trunk. Preserve original not working fine");

        //activity search for trunk
        Thread.sleep(1000 * 10);
        ActivityBean activityObjTrunk = activityAdminServiceClient.getActivities(environment.getGreg().getSessionCookie(), userInfo.getUserName()
                , schemaPathDev, LifeCycleUtils.formatDate(Calendar.getInstance().getTime())
                , "", ActivityAdminServiceClient.FILTER_RESOURCE_UPDATE, 1);
        assertNotNull(activityObjTrunk, "Activity object null in trunk");
        assertNotNull(activityObjTrunk.getActivity(), "Activity list object null");
        assertTrue((activityObjTrunk.getActivity().length > 0), "Activity list object null");
        String activity = activityObjTrunk.getActivity()[0];
        assertTrue(activity.contains(userInfo.getUserName()), "Activity not found. User name not found on last activity. " + activity);
        assertTrue(activity.contains("has updated the resource"),
                   "Activity not found. has updated not contain in last activity. " + activity);
        assertTrue(activity.contains("0m ago"), "Activity not found. current time not found on last activity. " + activity);

    }

    /**
     * @throws RegistryException
     * @throws LifeCycleManagementServiceExceptionException
     *
     * @throws RemoteException
     */
    @AfterClass()
    public void cleanup()
            throws RegistryException, LifeCycleManagementServiceExceptionException,
                   RemoteException {

        if (schemaPathDev != null) {
            wsRegistry.delete(schemaPathDev);
        }
    }

}
