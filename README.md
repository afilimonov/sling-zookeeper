sling-zookeeper
===============

Experimenting with embedding Zookeeper in Sling.

This does not work so far, here's the current status:

Need to patch zookeeper 3.4.5's build.xml with

    <attribute name="Import-Package" value='javax.management,org.apache.log4j,org.osgi.framework;version="[1.4,2.0)",org.osgi.util.tracker;version="[1.1,2.0)",org.slf4j;version="[1.5,2)"'/>

then build and install the resulting bundle.

Start Sling with -Dzookeeper.jmx.log4j.disable=true to avoid requiring log4j JMX classes.

org.apache.zookeeper.server.quorum.QuorumPeerMain fails as it doesn't find the javax/security/auth/login/LoginException class, need to look at this.
