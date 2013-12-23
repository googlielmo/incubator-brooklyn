package brooklyn.entity.database.mysql;

import java.util.Arrays;

import org.testng.annotations.Test;

import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.net.Protocol;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;

import com.google.common.collect.ImmutableList;

/**
 * The MySqlLiveTest installs MySQL on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * MySQL works like expected on these Operating Systems.
 */
public class MySqlLiveRackspaceTest extends MySqlIntegrationTest {
    @Test(groups = {"Live"})
    public void test_Debian_6() throws Exception {
        test("Debian 6");
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_10_0() throws Exception {
        test("Ubuntu 10.0");
    }

    @Test(groups = {"Live", "Live-sanity"})
    public void test_Ubuntu_12_0() throws Exception {
        test("Ubuntu 12.0");
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_13() throws Exception {
        test("Ubuntu 13");
    }

    @Test(groups = {"Live"})
    public void test_CentOS_6() throws Exception {
        test("CentOS 6");
    }

    @Test(groups = {"Live"})
    public void test_CentOS_5() throws Exception {
        test("CentOS 5");
    }

    @Test(groups = {"Live"})
    public void test_Fedora() throws Exception {
        test("Fedora ");
    }

    @Test(groups = {"Live"})
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        test("Red Hat Enterprise Linux 6");
    }

    @Test(groups = {"Live"})
    public void test_localhost() throws Exception {
        super.test_localhost();
    }

    public void test(String osRegex) throws Exception {
        MySqlNode mysql = tapp.createAndManageChild(EntitySpec.create(MySqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT));

        brooklynProperties.put("brooklyn.location.jclouds.rackspace-cloudservers-uk.imageNameRegex", osRegex);
        brooklynProperties.remove("brooklyn.location.jclouds.rackspace-cloudservers-uk.image-id");
        brooklynProperties.remove("brooklyn.location.jclouds.rackspace-cloudservers-uk.imageId");
        brooklynProperties.put("brooklyn.location.jclouds.rackspace-cloudservers-uk.inboundPorts", Arrays.asList(22, 3306));
        JcloudsLocation jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve("jclouds:rackspace-cloudservers-uk");

        tapp.start(ImmutableList.of(jcloudsLocation));

        SshMachineLocation l = (SshMachineLocation) mysql.getLocations().iterator().next();
        l.execCommands("add iptables rule", ImmutableList.of(IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, 3306, Policy.ACCEPT)));

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(DatastoreCommon.DATASTORE_URL)).readModifyAndRevertDataBase();
    } 
}
