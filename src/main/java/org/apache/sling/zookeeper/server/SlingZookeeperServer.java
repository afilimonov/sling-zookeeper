/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.zookeeper.server;

import java.io.File;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Experimental Zookeeper server meant to run embedded in Sling.
 *  Inspired from Apache Solr's SolrZkServer.  
 */
@Component(metatype=true)
public class SlingZookeeperServer {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private Thread zkThread;
    private QuorumPeerConfig zkConfig;
    
    // See http://zookeeper.apache.org/doc/r3.4.5/zookeeperAdmin.html for example properties
    public static final String ZK_PROP_PREFIX = "zookeper.";
    
    @Property(value="2000")
    public static final String TICK_TIME_PROP = ZK_PROP_PREFIX + "tickTime";
    
    @Property(value="2181")
    public static final String CLIENT_PORT_PROP = ZK_PROP_PREFIX + "clientPort";
    
    @Property(value="5")
    public static final String INIT_LIMIT_PROP = ZK_PROP_PREFIX + "initLimit";
    
    @Property(value="2")
    public static final String SYNC_LIMIT_PROP = ZK_PROP_PREFIX + "syncLimit";
    
    @Activate
    public void activate(ComponentContext ctx) throws Exception {
        zkConfig = new QuorumPeerConfig();

        final Properties props = new Properties();
        final Enumeration<?> e = ctx.getProperties().keys();
        while(e.hasMoreElements()) {
            final String key = e.nextElement().toString();
            if(key.startsWith(ZK_PROP_PREFIX)) {
                props.put(key.substring(ZK_PROP_PREFIX.length()), ctx.getProperties().get(key)); 
            }
        }
        
        // TODO hacking this for now...
        props.put("server.1", "localhost:2888:3888");
        props.put("server.2", "zk1.example.com:2888:3888");
        props.put("server.3", "zk2.example.com:2888:3888");
        
        // TODO should be set based on sling.home
        final File dataDir = new File("/tmp/sling/ZKDATA");
        props.put("dataDir", dataDir.getAbsolutePath());
        
        // TODO myid should be set in a better way
        dataDir.mkdirs();
        final File myid = new File(dataDir, "myid");
        if(!myid.exists()) {
            final FileWriter w = new FileWriter(myid);
            w.write("1\n");
            w.flush();
            w.close();
        }
        
        try {
            zkConfig.parseProperties(props);
            log.info("Zookeeper configuration: clientPort={}, dataDir={}, servers={}", 
                    new Object[] {
                        zkConfig.getClientPortAddress(),
                        zkConfig.getDataDir(),
                        zkConfig.getServers()
                    }
                    );
        } catch (Exception cfge) {
            log.error("Exception while parsing ZooKeeper properties", cfge);
            throw cfge;
        }
        
        zkThread = new Thread() {
            @Override
            public void run() {
                log.info("ZooKeeper Server thread {} starts", getName());
                try {
                    if(zkConfig.getServers().size() == 0) {
                        throw new Exception("No Zookeper servers configured");
                    } else if(zkConfig.getServers().size() > 1) {
                        QuorumPeerMain zkServer = new QuorumPeerMain();
                        zkServer.runFromConfig(zkConfig);
                    } else {
                        ServerConfig sc = new ServerConfig();
                        sc.readFrom(zkConfig);
                        ZooKeeperServerMain zkServer = new ZooKeeperServerMain();
                        zkServer.runFromConfig(sc);
                    }
                } catch (Exception e) {
                    log.error("Zookeeper server exception", e);
                }
                log.info("ZooKeeper Server thread {} ends", getName());
            }
        };
        
        log.info("Starting embedded Zookeeper server with {} servers on port {}", 
                zkConfig.getServers().size(),
                zkConfig.getClientPortAddress().getPort());
        
        zkThread.setDaemon(true);
        zkThread.start();
    }
    
    @Deactivate
    public void deactivate(ComponentContext ctx) {
        if(zkThread != null) {
            log.info("Interrupting Zookeeper server thread {}", zkThread.getName());
            zkThread.interrupt();
        } else {
            log.error("Unexpected null zkThread");
        }
    }
}
