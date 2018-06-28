/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.gsc.net.discover;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.gsc.common.utils.CollectionUtils;
import org.gsc.config.Args;
import org.gsc.db.Manager;
import org.gsc.net.discover.NodeHandler.State;
import org.gsc.net.discover.table.NodeTable;
import org.gsc.net.message.discover.FindNodeMessage;
import org.gsc.net.message.discover.Message;
import org.gsc.net.message.discover.NeighborsMessage;
import org.gsc.net.message.discover.PingMessage;
import org.gsc.net.message.discover.PongMessage;
import org.gsc.net.udp.handler.EventHandler;
import org.gsc.net.udp.handler.UdpEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NodeManager implements EventHandler {

  @Autowired
  private Args args;

  private Manager dbManager;

  private static final long LISTENER_REFRESH_RATE = 1000;
  private static final long DB_COMMIT_RATE = 1 * 60 * 1000;
  static final int MAX_NODES = 2000;
  static final int NODES_TRIM_THRESHOLD = 3000;

  Consumer<UdpEvent> messageSender;

  NodeTable table;
  private Map<String, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();
  final Node homeNode;
  private List<Node> bootNodes = new ArrayList<>();

  // option to handle inbounds only from known peers (i.e. which were discovered by ourselves)
  boolean inboundOnlyFromKnownNodes = false;

  private boolean discoveryEnabled;

  private Map<DiscoverListener, ListenerHandler> listeners = new IdentityHashMap<>();

  private boolean inited = false;
  private Timer logStatsTimer = new Timer();
  private Timer nodeManagerTasksTimer = new Timer("NodeManagerTasks");
  private ScheduledExecutorService pongTimer;

  @Autowired
  public NodeManager(Manager dbManager) {
    this.dbManager = dbManager;
    discoveryEnabled = args.isNodeDiscoveryEnable();

    homeNode = new Node(args.getMyKey().getNodeId(), args.getNodeExternalIp(),
        args.getNodeListenPort());

    for (String boot : args.getSeedNode().getIpList()) {
      bootNodes.add(Node.instanceOf(boot));
    }

    logger.info("homeNode : {}", homeNode);
    logger.info("bootNodes : size= {}", bootNodes.size());

    table = new NodeTable(homeNode);

    logStatsTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        logger.trace("Statistics:\n {}", dumpAllStatistics());
      }
    }, 1 * 1000L, 60 * 1000L);

    this.pongTimer = Executors.newSingleThreadScheduledExecutor();
  }

  public ScheduledExecutorService getPongTimer() {
    return pongTimer;
  }

  @Override
  public void channelActivated() {
    if (!inited) {
      inited = true;
      nodeManagerTasksTimer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          processListeners();
        }
      }, LISTENER_REFRESH_RATE, LISTENER_REFRESH_RATE);

      if (args.isNodeDiscoveryPersist()) {
        dbRead();
        nodeManagerTasksTimer.scheduleAtFixedRate(new TimerTask() {
          @Override
          public void run() {
            dbWrite();
          }
        }, DB_COMMIT_RATE, DB_COMMIT_RATE);
      }

      for (Node node : bootNodes) {
        getNodeHandler(node);
      }
    }
  }

//  void channelActivated() {
//    // channel activated now can send messages
//    if (!inited) {
//      // no another init on a new channel activation
//      inited = true;
//
//      // this task is done asynchronously with some fixed rate
//      // to avoid any overhead in the NodeStatistics classes keeping them lightweight
//      // (which might be critical since they might be invoked from time critical sections)
//      nodeManagerTasksTimer.scheduleAtFixedRate(new TimerTask() {
//        @Override
//        public void run() {
//          processListeners();
//        }
//      }, LISTENER_REFRESH_RATE, LISTENER_REFRESH_RATE);
//
//
//      //TODO: get neighbours from nodes data persistence
////      if (args.isNodeDiscoveryPersist()) {
////        dbRead();
////        nodeManagerTasksTimer.scheduleAtFixedRate(new TimerTask() {
////          @Override
////          public void run() {
////            dbWrite();
////          }
////        }, DB_COMMIT_RATE, DB_COMMIT_RATE);
////      }
//
//      for (Node node : bootNodes) {
//        getNodeHandler(node);
//      }
//
//      //TODO: init active nodes
//
////      for (Node node : args.getNodeActive()) {
////        getNodeHandler(node).getNodeStatistics().setPredefined(true);
////      }
//    }
//  }

  public boolean isNodeAlive(NodeHandler nodeHandler) {
    return nodeHandler.state.equals(State.Alive) ||
        nodeHandler.state.equals(State.Active) ||
        nodeHandler.state.equals(State.EvictCandidate);
  }

  private void dbRead() {
    //TODO: nodes data persistence
//    Set<Node> Nodes = this.dbManager.readNeighbours();
//    logger.info("Reading Node statistics from PeersStore: " + Nodes.size() + " nodes.");
//    Nodes.forEach(node -> getNodeHandler(node).getNodeStatistics()
//        .setPersistedReputation(node.getReputation()));
  }

  private void dbWrite() {
    //TODO: nodes data persistence
//    Set<Node> batch = new HashSet<>();
//    synchronized (this) {
//      for (NodeHandler nodeHandler : nodeHandlerMap.values()) {
//        //if (isNodeAlive(nodeHandler)) {
//        nodeHandler.getNode()
//            .setReputation(nodeHandler.getNodeStatistics().getPersistedReputation());
//        batch.add(nodeHandler.getNode());
//        //}
//      }
//    }
//    logger.info("Write Node statistics to PeersStore: " + batch.size() + " nodes.");
//    dbManager.clearAndWriteNeighbours(batch);
  }

  public void setMessageSender(Consumer<UdpEvent> messageSender) {
    this.messageSender = messageSender;
  }

  private String getKey(Node n) {
    return getKey(new InetSocketAddress(n.getHost(), n.getPort()));
  }

  private String getKey(InetSocketAddress address) {
    InetAddress addr = address.getAddress();
    return (addr == null ? address.getHostString() : addr.getHostAddress()) + ":" + address
        .getPort();
  }

  public synchronized NodeHandler getNodeHandler(Node n) {
    String key = getKey(n);
    NodeHandler ret = nodeHandlerMap.get(key);
    if (ret == null) {
      trimTable();
      ret = new NodeHandler(n, this);
      nodeHandlerMap.put(key, ret);
      logger.info("Add new node: {}, size={}", ret, nodeHandlerMap.size());
    } else if (ret.getNode().isDiscoveryNode() && !n.isDiscoveryNode()) {
      logger.info("Change node: old {} new {}, size ={}", ret, n, nodeHandlerMap.size());
      ret.node = n;
    }
    return ret;
  }

  private void trimTable() {
    if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
      List<NodeHandler> sorted = new ArrayList<>(nodeHandlerMap.values());
      // reverse sort by reputation
      sorted.sort((o1, o2) -> o1.getNodeStatistics().getReputation() - o2.getNodeStatistics()
          .getReputation());

      for (NodeHandler handler : sorted) {
        nodeHandlerMap.remove(getKey(handler.getNode()));
        if (nodeHandlerMap.size() <= MAX_NODES) {
          break;
        }
      }
    }
  }

  boolean hasNodeHandler(Node n) {
    return nodeHandlerMap.containsKey(getKey(n));
  }

  public NodeTable getTable() {
    return table;
  }

  public NodeStatistics getNodeStatistics(Node n) {
    return getNodeHandler(n).getNodeStatistics();
  }


  @Override
  public void handleEvent(UdpEvent udpEvent) {
    Message m = udpEvent.getMessage();
    InetSocketAddress sender = udpEvent.getAddress();

    Node n = new Node(m.getNodeId(), sender.getHostString(), sender.getPort());

    if (inboundOnlyFromKnownNodes && !hasNodeHandler(n)) {
      logger.debug("Inbound packet from unknown peer {}.", sender.getAddress());
      return;
    }
    NodeHandler nodeHandler = getNodeHandler(n);

    switch (m.getType()) {
      case DISCOVER_PING:
        nodeHandler.handlePing((PingMessage) m);
        break;
      case DISCOVER_PONG:
        nodeHandler.handlePong((PongMessage) m);
        break;
      case DISCOVER_FIND_NODE:
        nodeHandler.handleFindNode((FindNodeMessage) m);
        break;
      case DISCOVER_NEIGHBORS:
        nodeHandler.handleNeighbours((NeighborsMessage) m);
        break;
    }
  }


  public void sendOutbound(UdpEvent discoveryEvent) {
    if (discoveryEnabled && messageSender != null) {
      messageSender.accept(discoveryEvent);
    }
  }

  public synchronized List<NodeHandler> getNodes(int minReputation) {
    List<NodeHandler> ret = new ArrayList<>();
    for (NodeHandler nodeHandler : nodeHandlerMap.values()) {
      if (nodeHandler.getNodeStatistics().getReputation() >= minReputation) {
        ret.add(nodeHandler);
      }
    }
    return ret;
  }

  public List<NodeHandler> getNodes(Predicate<NodeHandler> predicate, int limit) {
    ArrayList<NodeHandler> filtered = new ArrayList<>();
    synchronized (this) {
      for (NodeHandler handler : nodeHandlerMap.values()) {
        if (predicate.test(handler)) {
          filtered.add(handler);
        }
      }
    }

    logger.debug("nodeHandlerMap size {} filter peer  size {}", nodeHandlerMap.size(),
        filtered.size());

    //TODO: here can use head num sort.
    filtered.sort((o1, o2) -> o2.getNodeStatistics().getReputation() - o1.getNodeStatistics()
        .getReputation());

    return CollectionUtils.truncate(filtered, limit);
  }

  public List<NodeHandler> dumpActiveNodes() {
    List<NodeHandler> handlers = new ArrayList<>();
    for (NodeHandler handler :
        this.nodeHandlerMap.values()) {
      if (isNodeAlive(handler)) {
        handlers.add(handler);
      }
    }

    return handlers;
  }

  private synchronized void processListeners() {
    for (ListenerHandler handler : listeners.values()) {
      try {
        handler.checkAll();
      } catch (Exception e) {
        logger.error("Exception processing listener: " + handler, e);
      }
    }
  }

  public synchronized void addDiscoverListener(DiscoverListener listener,
      Predicate<NodeStatistics> filter) {
    listeners.put(listener, new ListenerHandler(listener, filter));
  }

  public synchronized String dumpAllStatistics() {
    List<NodeHandler> l = new ArrayList<>(nodeHandlerMap.values());
    l.sort((o1, o2) -> -(o1.getNodeStatistics().getReputation() - o2.getNodeStatistics()
        .getReputation()));

    StringBuilder sb = new StringBuilder();
    int zeroReputCount = 0;
    for (NodeHandler nodeHandler : l) {
      if (nodeHandler.getNodeStatistics().getReputation() > 0) {
        sb.append(nodeHandler).append("\t").append(nodeHandler.getNodeStatistics()).append("\n");
      } else {
        zeroReputCount++;
      }
    }
    sb.append("0 reputation: ").append(zeroReputCount).append(" nodes.\n");
    return sb.toString();
  }

  public Node getPublicHomeNode() {
    return homeNode;
  }

  public void close() {
    try {
      nodeManagerTasksTimer.cancel();
      pongTimer.shutdownNow();
      logStatsTimer.cancel();
    } catch (Exception e) {
      logger.warn("close failed.", e);
    }
  }

  private class ListenerHandler {

    Map<NodeHandler, Object> discoveredNodes = new IdentityHashMap<>();
    DiscoverListener listener;
    Predicate<NodeStatistics> filter;

    ListenerHandler(DiscoverListener listener, Predicate<NodeStatistics> filter) {
      this.listener = listener;
      this.filter = filter;
    }

    void checkAll() {
      for (NodeHandler handler : nodeHandlerMap.values()) {
        boolean has = discoveredNodes.containsKey(handler);
        boolean test = filter.test(handler.getNodeStatistics());
        if (!has && test) {
          listener.nodeAppeared(handler);
          discoveredNodes.put(handler, null);
        } else if (has && !test) {
          listener.nodeDisappeared(handler);
          discoveredNodes.remove(handler);
        }
      }
    }
  }

}
