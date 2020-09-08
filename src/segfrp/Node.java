/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgrapht.*;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.graph.*;
import segfrp.FwdEntry.FwdAction;

/**
 *
 * @author anix
 */
public class Node {

    int nodeId; // ID of the node

    static private final int DEFAULT_CAPACITY = 100;

    static protected final int PKT_MAX_HOPS = 50;

    private final static Logger logger = Logger.getLogger(Node.class.getName());

    private Network net;
    private Graph<Node, Link> graph;
    private FwdTable domainFwdTable; // forwarding table for domain partitioning method
    private FwdTable heuristicFwdTable; // forwarding table for heuristic method

    private boolean crossedSeMetricThreshold; // whether or not we have sufficient SE metric samples
    private int seMetricCount; // number of SE metric values collected at this node
    private int zeroSEMetricCount;
    private int totalSEMetric;
    private int maxSEMetric;
    private double seMetricThreshold; // fraction of flow nodes for which SE metric
    // must be computed for sufficient samples

    boolean segmentEndpoint; // indicates whether this node is a segment endpoint for any flow
    int     seCount; // number of flows for which this node is segment endpoint

    SrDomain srDomain;
    
    ShortestPathAlgorithm.SingleSourcePaths<Node, Link> paths; // paths too all destinations with this node as src

    private int x, y;

    public static class Type {

        static final int OTHER = 0;
        static final int TOR = 1;
        static final int AGGR = 2;
        static final int CORE = 3;
        static final int HYPER = 4;
    };

    public static final String NODE_TYPE[] = {"OTHER", "TOR", "AGGR", "CORE"};

    String name; // name of the node
    int totalLoad;
    int capacity;
    int type;

    // Map<Integer, Flow> flows; // a map of flows indexed by flow ID
    Map<Integer, FlowNode> flowNodes; // a map of flow nodes indexed by flow ID

    Map<Integer, Port> neighborPorts; // ports indexed by neighbor node ID
    Map<Integer, Port> ports; // ports indexed by port ID
    Map<Integer, Node> neighbors; // neighbors indexed by node ID
    
    int activeIngressPorts; // number of ports with ingress flows
    int activeEgressPorts;   // number of flows with egress flows


    final double LOAD_THRESHOLD_FACTOR = 0.7;

    boolean debug;

    public Map<Integer, Port> getPorts() {
        return ports;
    }

    public Node(Network net, Graph<Node, Link> graph,
            int nodeId, String name, int capacity, int type) {
        this(net, graph, nodeId, name, capacity, type, Network.DEFAULT_SR_DOMAIN, 0, 0);
    }

    public Node(Network net, Graph<Node, Link> graph,
            int nodeId, String name, int capacity, int type, int srDomainId,
            int x, int y) {

        this.net = net;
        this.graph = graph;
        this.nodeId = nodeId;
        this.name = name;
        this.type = type;
        this.srDomain = net.getSrDomainById(srDomainId);

        // set x,y coordinates for the node
        this.x = x;
        this.y = y;

        this.domainFwdTable = new FwdTable(this);
        this.heuristicFwdTable = new FwdTable(this);

        this.seMetricCount = 0;
        this.zeroSEMetricCount = 0;
        this.totalSEMetric = 0;
        this.maxSEMetric = 0;
        this.crossedSeMetricThreshold = false;
        this.seMetricThreshold = 0.0; // initially, this is zero since number of
        // flow nodes is not known

        this.segmentEndpoint = false;
        this.seCount = 0;

        logger.setLevel(Network.defaultLogLevel);
        this.debug = false;

        this.capacity = capacity;
        this.paths = null;

        this.ports = new HashMap<>();
        this.flowNodes = new HashMap<>();
        
        this.activeIngressPorts = 0;
        this.activeEgressPorts = 0;

        // create the HOST port for this node
        Port hostPort = new Port(this, Port.HOST_PORT_ID);
        this.ports.put(Port.HOST_PORT_ID, hostPort);

    }

    public Node(Network net, SimpleGraph<Node, Link> graph,
            int nodeId, String name, int capacity) {
        this(net, graph, nodeId, name, capacity, Type.OTHER);
    }

    public Network getNetwork() {
        return net;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getSrDomainId() {
        return srDomain.getId();
    }

    protected SrDomain getSrDomain() {
        return srDomain;
    }

    public Graph<Node, Link> getGraph() {
        return graph;
    }

    public String getNodeName() {
        return name;
    }
    
    public GraphPath getPath(Node dst) {
        
        if(paths == null) {
            paths = net.getDsp().getPaths(this);
        }
        
        return paths.getPath(dst);
    }

    public int getSeCount() {
        return seCount;
    }
    
    private final boolean USE_SE_METRIC_THRESHOLD = false;

    protected void addSEMetric(int seMetric) {

        int numFlowNodes = flowNodes.size();

        // set the threshold if it is not already set
        if (seMetricThreshold == 0.0) {
            if (numFlowNodes < 1000) {
                seMetricThreshold = 0.9;
            } else if (numFlowNodes < 2000) {
                seMetricThreshold = 0.8;
            } else if (numFlowNodes < 3000) {
                seMetricThreshold = 0.7;
            } else if (numFlowNodes < 4000) {
                seMetricThreshold = 0.6;
            } else if (numFlowNodes < 10000) {
                seMetricThreshold = 0.5;
            } else if (numFlowNodes < 20000) {
                seMetricThreshold = 0.4;
            } else if (numFlowNodes < 30000) {
                seMetricThreshold = 0.3;
            } else if (numFlowNodes < 40000) {
                seMetricThreshold = 0.2;
            } else {
                seMetricThreshold = 0.1;
            }
            // for now, set the threshold to 1.0
//            seMetricThreshold = 1.0;
        }

        if (seMetric == 0) {
            // don't collect zero samples
            this.zeroSEMetricCount++;
            return;
        }

        // now calculate the new total and average SE Metric
        totalSEMetric += seMetric;

        if (maxSEMetric < seMetric) {
            maxSEMetric = seMetric;
        }

        seMetricCount++;

        if (!USE_SE_METRIC_THRESHOLD) {
            // avoid setting the threshold
            // in this case, all nodes will use the actual SE metric
            // and not the max value for node once threshold is reached.
            return;
        }

        if (crossedSeMetricThreshold == false) {
            if (seMetricCount > numFlowNodes * seMetricThreshold) {
                this.crossedSeMetricThreshold = true;
//                System.out.println("Node " + toString() + " crossed sample threshold. Num flow nodes = "
//                        + numFlowNodes + ". Num samples = " + seMetricCount + ". Max metric = " + maxSEMetric
//                        + " Avg metric = " + getAvgSEMetric());

            }
        }
    }

    protected int getSEMetricCount() {
        return seMetricCount;
    }

    protected int getZeroSEMetricCount() {
        return zeroSEMetricCount;
    }

    protected double getAvgSEMetric() {
        if (seMetricCount == 0) {
            return 0.0;
        } else {
            return totalSEMetric / seMetricCount;
        }
    }

    protected int getMaxSEMetric() {
        return maxSEMetric;
    }

    protected int getTotalSEMetric() {
        return totalSEMetric;
    }

    protected boolean checkSEMetricThresholdForFlow(Flow flow) {

        if (this.equals(flow.getDst())) {

            // we have reached the destination. 
            // Threshold does not apply at the destination node. 
            // Return true unless this is a single hop flow
            if (flow.getPath().getLength() == 1) {
                return false;
            } else {
                return true;
            }
        }

        // find the flow node
        FlowNode fNode = getFlowNodeByFlowId(flow.getFlowId());

        // find the downstream node
        Node dsNode = fNode.getDsNode();

        if (this.equals(flow.getSrc())) {
            // Threshold does not apply to source node either. 
            // Simply return what downstream node returns;
            return dsNode.checkSEMetricThresholdForFlow(flow);
        }

        // else, this is an intermediate node (between src and dst)
        // check the local threshold
        if (isCrossedSeMetricThreshold()) {
            // this node has crossed threshold, check with downstream modes
            return dsNode.checkSEMetricThresholdForFlow(flow);
        }

        // else, this node has not crossed threshold. Return false
        return false;
    }

    public boolean isCrossedSeMetricThreshold() {
        return crossedSeMetricThreshold;
    }
    
    public int getNodeSEMetric() {
        return flowNodes.size() * activeIngressPorts * activeEgressPorts;
    }
    
    public String getNodeSEMetricStr() {
        
        return "(I=" + activeIngressPorts + ", F=" + flowNodes.size() + 
                ", E=" + activeEgressPorts + ")";
        
    }

    public boolean isSegmentEndpoint() {
        return segmentEndpoint;
    }

    public void setSegmentEndpoint(boolean isSegmentEndpoint) {
        this.segmentEndpoint = isSegmentEndpoint;
        this.seCount++;
    }

    public String toString() {
        return name;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

//    public void body() {
//
//        double round;
//
//        while (Sim_system.running()) {
//
////            triggerCount = sim_waiting(triggerPred);
////
////            if (triggerCount == 0) {
////                // wait till you receive a trigger
////            }
////            offerCount = sim_waiting(offerPred);
////            info("Trigger received by Node " + toString() + "; Trigger Count "
////                    + triggerCount);
//            Sim_event e = new Sim_event();
//
//            round = Network.clock();
//
//            info("Trigger event received at time " + round);
//
//            info("Processing a round at node " + getNodeName()
//                    + " at time " + e.event_time());
//
//            // Process the trigger event
//            sim_process(0.0);
//
//            // The event has been serviced
//            sim_completed(e);
//
//        }
//    }
    private boolean isPastEndRound() {
        return false;

    }

    public Port addNetworkLink(Node neighbor, Link link) {
        // get the next available port ID
        int portId = ports.values().size();

        Port port = new Port(this, neighbor, portId, link);
        ports.put(portId, port);

        return port;

    }

    protected Port getPortById(int portId) {
        return ports.get(portId);
    }

    // add forwarding entry for domain partitioning method
    protected void addFwdEntry(int label, int action, Port egress, int method) {
        FwdTable fwdTable;

        if (method == Network.FTS_OPT_DOMAIN) {
            fwdTable = domainFwdTable;
        } else {
            fwdTable = heuristicFwdTable;
            info("Adding heuristic forwarding entry at " + toString());
            info("Label " + label + "; Egress to " + egress.getNeighbor().toString());
        }

        fwdTable.addEntry(label, action, egress);
    }

    // add forwarding entry for heuristic method
    protected void addHeuristicFwdEntry(int label, int action, Port egress) {
        heuristicFwdTable.addEntry(label, action, egress);
    }

    protected void addRoute(int label, Port egress, int method) {
        if(label == getNodeId()) {
            // no need to add a route for local node
            return;
        }
        addFwdEntry(label, FwdAction.CONTINUE, egress, method);
    }

    protected void routePacket(Packet pkt, int method) {

        int label;
        Port port;
        Node nextNode;
        FwdTable fwdTable;
        FwdEntry fwdEntry;

        if (method == Network.FTS_OPT_DOMAIN) {
            fwdTable = domainFwdTable;
        } else {
            fwdTable = heuristicFwdTable;
        }

        if (this.equals(pkt.getDst())) {

            net.totalPktHops += pkt.hops;
            if (net.maxPktHops < pkt.hops) {
                net.maxPktHops = pkt.hops;
            }

            // we are done- SEGR TBD- update some stats
            return;
        }

        if (pkt.hops > PKT_MAX_HOPS) {
            warning("Packet " + pkt.toString() + " reached MAX HOPS");
            return;
        }

        label = pkt.getLabel();
        
        // Special case to handle domain crossing at the source
        // or double-crossing, that is, two consecutive domain crossings in the path
        if(label == getNodeId()) {
            // System.out.println("Label matches local node id - popping label " + label);
            pkt.next(); // pop the label
            routePacket(pkt, method);
            return;
        }

        // first check if the label is an Adj SID
        if (Port.isAdjSID(label)) {
            info("Adj SID in label stack at " + toString());
            port = getPortById(Port.getPortIdFromAdjSID(label));
            if(port == null) {
                System.out.println("Adj SID- null port " + Integer.toHexString(label) + " at node " + toString());
            }
            pkt.next(); // pop the Adj SID 
            nextNode = port.getNeighbor();
        } else {

            fwdEntry = fwdTable.lookup(label);
            if (fwdEntry == null) {
                info("Unable to find fwd entry for Packet " + pkt.toString());
                return;
            }

//        if (fwdEntry.getAction() == FwdAction.NEXT) {
//            pkt.next(); // advance to the next segment of the source route
//        }
            port = fwdEntry.getPort();
            nextNode = port.getNeighbor();
        }

        if (label == nextNode.getNodeId()) {
            // penultimate node pop
            info("Penultimate node label pop at " + toString());
            pkt.next();
        }

        pkt.hops++;
        nextNode.routePacket(pkt, method);

        return;

    }

    public Port getHostPort() {
        return (Port) ports.get(Port.HOST_PORT_ID);
    }

    public Map<Integer, FlowNode> getFlowNodes() {
        return flowNodes;
    }

    public FlowNode getFlowNodeByFlowId(int flowId) {
        return (FlowNode) flowNodes.get(flowId);
    }

    protected void calculateUsCoFlowCount(Flow flow) {
        // at the source node upstream co-flow set is empty
        Set<Integer> usCoFlows = new HashSet<>();

        calculateUsCoFlowCount(flow, usCoFlows);

        usCoFlows = null;
    }

    protected void calculateUsCoFlowCount(Flow flow, Set<Integer> usCoFlows) {

        FlowNode fNode = getFlowNodeByFlowId(flow.getFlowId());

        fNode.calculateUsCoFlowCount(usCoFlows);
    }

    protected void calculateCoFlowBoCount(Flow flow) {
        FlowNode fNode = getFlowNodeByFlowId(flow.getFlowId());

        fNode.calculateCoFlowBoCount();

        info("Co-Flow Hops Count = " + fNode.getCoFlowHopCount()
                + " for flow " + flow.getFlowId() + " at dst " + toString());
    }

    // request to update the co-flow map of a given flow from the downstreatm
    // node of that flow
    protected void updateCoFlowBoCount(Flow flow, Map<Integer, CoFlow> coflows) {

        FlowNode fNode;
        fNode = getFlowNodeByFlowId(flow.getFlowId());

        fNode.updateCoFlowHopCount(coflows);

    }

    protected int getCoFlowHops(Flow flow) {
        FlowNode fNode = getFlowNodeByFlowId(flow.getFlowId());

        return fNode.getCoFlowHopCount();
    }

    public void addFlowNode(Flow flow, Port ingress, Port egress) {

        FlowNode fnode = new FlowNode(this, flow, ingress, egress);
        flowNodes.put(flow.getFlowId(), fnode);

        flow.addFlowNode(fnode);

//        info("Adding Flow Node Flow ID " + flow.getFlowId() + " at Node " + toString() + "; Ingress "
//                + ingress.getPortId() + "; Egress " + egress.getPortId());
        // add the flow to the ingress and egress ports
        if(ingress.getIngressFlows().isEmpty()) {
            activeIngressPorts++;
        }
        
        ingress.addFlow(flow, true);
        
        if(egress.getEgressFlows().isEmpty()) {
            activeEgressPorts++;
        }
        
        egress.addFlow(flow, false);

    }

    protected int getNumFlows() {
        return flowNodes.size();
    }

    public void setDebugFlag(boolean val) {
        this.debug = val;
    }

    public void info(String msg) {
        if (logger.isLoggable(Level.INFO) || debug) {
            System.out.println(toString() + " " + msg);
        }
    }

    public static void warning(String msg) {
        if (logger.isLoggable(Level.WARNING)) {
            System.out.println("WARNING: " + msg);
        }
    }

    public void prettyPrint() {
        System.out.print("Node: " + name + " " + " ");
        System.out.print("Neighbors :: ");
        for (Port p : ports.values()) {
            p.prettyPrint();
        }

        System.out.println();
    }

    protected int getFwdTableSize(int method) {
        if (method == Network.FTS_OPT_DOMAIN) {
            return domainFwdTable.size();
        } else {
            return heuristicFwdTable.size();
        }
    }

}
