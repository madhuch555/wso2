/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.registry.ws.client.test.general.old;

import org.apache.axis2.AxisFault;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.core.ProductConstant;
import org.wso2.carbon.automation.utils.registry.RegistryProviderUtil;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import static org.testng.Assert.assertEquals;

/**
 * A test case which tests registry copy operation
 */
public class TestCopyWSTestCase {

    private WSRegistryServiceClient registry;

    @BeforeClass(groups = {"wso2.greg"})
    public void initTest() throws RegistryException, AxisFault {
        int userId = 0;
        RegistryProviderUtil registryProviderUtil = new RegistryProviderUtil();
        registry = registryProviderUtil.getWSRegistry(userId, ProductConstant.GREG_SERVER_NAME);
    }

    @Test(groups = {"wso2.greg"})
    public void resourceCopy() throws Exception {

        Resource r1 = registry.newResource();
        r1.setProperty("test", "copy");
        r1.setContent("c");
        registry.put("/test1/copy/c1/copy1", r1);

        Collection c1 = registry.newCollection();
        registry.put("/test1/move", c1);

        registry.copy("/test1/copy/c1/copy1", "/test1/copy/c2/copy2");

        Resource newR1 = registry.get("/test1/copy/c2/copy2");
        assertEquals(newR1.getProperty("test"), "copy",
                     "Copied resource should have a property named 'test' with value 'copy'.");

        Resource oldR1 = registry.get("/test1/copy/c1/copy1");
        assertEquals(oldR1.getProperty("test"), "copy",
                     "Original resource should have a property named 'test' with value 'copy'.");

        String newContent = new String((byte[]) newR1.getContent());
        String oldContent = new String((byte[]) oldR1.getContent());
        assertEquals(newContent, oldContent,
                     "Contents are not equal in copied resources");
    }

    @Test(groups = {"wso2.greg"})
    public void collectionCopy() throws Exception {

        Resource r1 = registry.newResource();
        r1.setProperty("test", "copy");
        r1.setContent("c");
        registry.put("/test1/copy/copy3/c3/resource1", r1);

        Collection c1 = registry.newCollection();
        registry.put("/test1/move", c1);

        registry.copy("/test1/copy/copy3", "/test1/newc/copy3");

        Resource newR1 = registry.get("/test1/newc/copy3/c3/resource1");
        assertEquals(newR1.getProperty("test"), "copy",
                     "Copied resource should have a property named 'test' with value 'copy'.");

        Resource oldR1 = registry.get("/test1/copy/copy3/c3/resource1");
        assertEquals(oldR1.getProperty("test"), "copy",
                     "Original resource should have a property named 'test' with value 'copy'.");
    }

    @AfterClass
    public void cleanup() throws RegistryException {
        registry.delete("/test1");
    }
}
