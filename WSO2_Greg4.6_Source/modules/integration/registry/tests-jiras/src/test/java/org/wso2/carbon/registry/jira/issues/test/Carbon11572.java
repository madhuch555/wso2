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


package org.wso2.carbon.registry.jira.issues.test;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.util.AXIOMUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.automation.api.clients.governance.ListMetaDataServiceClient;
import org.wso2.carbon.automation.api.clients.registry.ResourceAdminServiceClient;
import org.wso2.carbon.automation.api.clients.server.admin.ServerAdminClient;
import org.wso2.carbon.automation.core.ProductConstant;
import org.wso2.carbon.automation.core.ServerGroupManager;
import org.wso2.carbon.automation.core.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.core.annotations.SetEnvironment;
import org.wso2.carbon.automation.core.utils.environmentutils.EnvironmentBuilder;
import org.wso2.carbon.automation.core.utils.environmentutils.ManageEnvironment;
import org.wso2.carbon.automation.core.utils.frameworkutils.FrameworkFactory;
import org.wso2.carbon.automation.core.utils.frameworkutils.FrameworkProperties;
import org.wso2.carbon.automation.utils.registry.RegistryProviderUtil;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.services.ServiceManager;
import org.wso2.carbon.governance.api.services.dataobjects.Service;
import org.wso2.carbon.governance.api.wsdls.WsdlManager;
import org.wso2.carbon.governance.api.wsdls.dataobjects.Wsdl;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceExceptionException;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;
import org.wso2.carbon.utils.CarbonUtils;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Iterator;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


public class Carbon11572 {


    private ResourceAdminServiceClient resourceAdminClient;
    private ListMetaDataServiceClient listMetaDataServiceClient;
    private ServerAdminClient serverAdminClient;
    private FileOutputStream fileOutputStream;
    private XMLStreamWriter writer;
    private int userId = ProductConstant.ADMIN_USER_ID;
    private Registry governance;
    private WsdlManager wsdlManager;
    private ServiceManager serviceManager;

    @BeforeClass(alwaysRun = true)
    @SetEnvironment(executionEnvironments = {ExecutionEnvironment.integration_user})
    public void initialize() throws Exception, IOException,
                                    InterruptedException {
        init();
    }

    private void init() throws RemoteException, LoginAuthenticationExceptionException, RegistryException {
        EnvironmentBuilder builder = new EnvironmentBuilder().greg(userId);
        ManageEnvironment environment = builder.build();
        serverAdminClient = new ServerAdminClient(environment.getGreg().getProductVariables().getBackendUrl(),
                                                  environment.getGreg().getSessionCookie());
        resourceAdminClient =
                new ResourceAdminServiceClient(environment.getGreg().getBackEndUrl(),
                                               environment.getGreg().getSessionCookie());
        listMetaDataServiceClient =
                new ListMetaDataServiceClient(environment.getGreg().getBackEndUrl(),
                                              environment.getGreg().getSessionCookie());
        WSRegistryServiceClient wsRegistry =
                new RegistryProviderUtil().getWSRegistry(userId,
                        ProductConstant.GREG_SERVER_NAME);
        RegistryProviderUtil registryProviderUtil = new RegistryProviderUtil();
        wsRegistry =
                registryProviderUtil.getWSRegistry(userId, ProductConstant.GREG_SERVER_NAME);
        governance = registryProviderUtil.getGovernanceRegistry(wsRegistry, userId);

         wsdlManager = new WsdlManager(governance);

         serviceManager = new ServiceManager(governance);

    }

    @Test(groups = {"wso2.greg"}, description = "change registry.xml and restart server")
    @SetEnvironment(executionEnvironments = {ExecutionEnvironment.integration_user})
    public void editRegistryXML() throws Exception {

        String registryXmlPath = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator
                                 + "conf" + File.separator + "registry.xml";

        File srcFile = new File(registryXmlPath);
        try {
            OMElement handlerConfig = AXIOMUtil.stringToOM("<property name=\"createService\">false</property>");
            OMElement registryXML = getRegistryXmlOmElement();

            OMElement om1 = null;
            for (Iterator iterator = registryXML.getChildrenWithName(new QName("handler")); iterator.hasNext(); ) {
                OMElement om = (OMElement) iterator.next();

                if (om.getAttribute(new QName("class")).getAttributeValue().equals("org.wso2.carbon.registry.extensions.handlers" +
                                                                                   ".WSDLMediaTypeHandler")) {
                    om1 = om;
                    om1.addChild(handlerConfig);
                    registryXML.addChild(om1);
                    registryXML.build();
                    break;
                }

            }

            fileOutputStream = new FileOutputStream(srcFile);
            writer =
                    XMLOutputFactory.newInstance().createXMLStreamWriter(fileOutputStream);
            registryXML.serialize(writer);


        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Registry.xml file not found" + e);

        } catch (XMLStreamException e) {
            throw new XMLStreamException("XML stream exception" + e);

        } catch (IOException e) {
            throw new IOException("IO exception" + e);

        } finally {
            writer.close();
            fileOutputStream.close();
        }

        restartServer();
        init(); //reinitialize environment after server restart.


    }

    @Test(groups = {
            "wso2.greg"}, description = "add a WSDL without creating a service", dependsOnMethods = "editRegistryXML")
    @SetEnvironment(executionEnvironments = {ExecutionEnvironment.integration_user})
    public void addWSDL()
            throws ResourceAdminServiceExceptionException, RemoteException, MalformedURLException, GovernanceException {
        boolean nameExists = false;
        //add WSDL file
        String path1 = ProductConstant.SYSTEM_TEST_RESOURCE_LOCATION + "artifacts" + File.separator
                       + "GREG" + File.separator + "wsdl" + File.separator + "echo.wsdl";
        DataHandler dataHandler1 = new DataHandler(new URL("file:///" + path1));


       Wsdl[] wsdls = wsdlManager.getAllWsdls();

        //delete wsdl if exists
        if (wsdls != null) {
                for (Wsdl wsdl : wsdls) {
                    if ("echo.wsdl".equals(wsdl.getQName().getLocalPart())) {
                        resourceAdminClient.deleteResource(wsdl.getPath());
                        break;
                    }
                }
        }

       Service[] services = serviceManager.getAllServices();

        //delete service if exists
        if (services != null) {
                for (Service service : services) {
                    if ("echoyuSer1".equals(service.getPath())) {
                        resourceAdminClient.deleteResource(service.getPath());
                        break;
                    }
            }
        }
        resourceAdminClient.addWSDL("desc 1", dataHandler1);

        wsdls = wsdlManager.getAllWsdls();

            for (Wsdl wsdl: wsdls) {
                if ("echo.wsdl".equals(wsdl.getQName().getLocalPart())) {
                    nameExists = true;
                }

        }
        assertTrue(nameExists);
        //check whether the service is created

        boolean serviceStatus = false;

        services = serviceManager.getAllServices();

        for (Service service : services) {
            if ("echoyuSer1".equals(service.getQName().getLocalPart())) {
                serviceStatus = true;
                break;
            }
        }

        assertFalse(serviceStatus);
    }

    @AfterClass(alwaysRun = true)
    @SetEnvironment(executionEnvironments = {ExecutionEnvironment.integration_user})
    public void cleanUp()
            throws IOException, ResourceAdminServiceExceptionException, XMLStreamException {

        String registryXmlPath = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator
                                 + "conf" + File.separator + "registry.xml";

        File srcFile = new File(registryXmlPath);
        OMElement element = getRegistryXmlOmElement();
        OMElement omElement = null;

        for (Iterator iterator = element.getChildrenWithName(new QName("handler")); iterator.hasNext(); ) {
            OMElement om = (OMElement) iterator.next();

            if (om.getAttribute(new QName("class")).getAttributeValue().equals("org.wso2.carbon.registry.extensions.handlers" +
                                                                               ".WSDLMediaTypeHandler")) {

                omElement = om;
                for (Iterator it = omElement.getChildrenWithName(new QName("property")); it.hasNext(); ) {
                    OMElement omRemove = (OMElement) it.next();
                    if (omRemove.getAttribute(new QName("name")).getAttributeValue().equals("createService")) {
                        it.remove();
                        element.build();
                        break;
                    }
                }
                break;
            }
        }

        fileOutputStream = new FileOutputStream(srcFile);
        writer = XMLOutputFactory.newInstance().createXMLStreamWriter(fileOutputStream);
        element.serialize(writer);
        element.build();
        writer.close();
        fileOutputStream.close();


        String pathToDelete = "/_system/governance/" + listMetaDataServiceClient.listWSDLs().getPath()[0];

        if (pathToDelete != null && listMetaDataServiceClient.listWSDLs().getPath()[0] != null) {
            resourceAdminClient.deleteResource(pathToDelete);
        }
        resourceAdminClient = null;
        listMetaDataServiceClient = null;
        serverAdminClient = null;
    }

    public static OMElement getRegistryXmlOmElement()
            throws FileNotFoundException, XMLStreamException {
        String registryXmlPath = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator
                                 + "conf" + File.separator + "registry.xml";


        File registryFile = new File(registryXmlPath);

        FileInputStream inputStream = new FileInputStream(registryFile);
        XMLStreamReader parser = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);
        StAXOMBuilder builder = new StAXOMBuilder(parser);

        return builder.getDocumentElement();
    }


    private void restartServer() throws Exception {
        FrameworkProperties frameworkProperties =
                FrameworkFactory.getFrameworkProperties(ProductConstant.GREG_SERVER_NAME);
        ServerGroupManager.getServerUtils().restartGracefully(serverAdminClient, frameworkProperties);
    }
}
