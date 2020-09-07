/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgrapht.*;
import org.jgrapht.graph.*;

/**
 *
 * @author anix
 */
public class Flow {

    // the list of nodes that carry the flow
    private List<Segment> segmentsSGP; // list of segments of the end-to-end path;
    private GraphPath<Node, Link> pathSGP, backupPathSGP;

    private Stack domainLabelStack; // label stack for domain partitioning based routing
    private Stack heuristicLabelStack; // label stack for heuristic method

    private Node src, dst;
    private Network net;

    private List<FlowNode> flowNodes;

    private int seCount; // number of segment endpoints used by this flow

    protected static int numFlowsWithNonMaxSLD = 0;

    int bitRate;
    int flowId;

//    private boolean coflowCountDone; // indicates whether co-flow calculations are done for this flow
    private boolean crossedSEMetricThreshold;

    private final static Logger logger = Logger.getLogger(Flow.class.getName());

    boolean debug;

    public Flow(Network net, Node src, Node dst, int bitRate, int flowId,
            GraphPath<Node, Link> path, GraphPath<Node, Link> backupPath) {

        this.net = net;
        this.src = src;
        this.dst = dst;

        this.bitRate = bitRate;
        this.flowId = flowId;

        this.crossedSEMetricThreshold = false;

        this.pathSGP = path;
        this.backupPathSGP = backupPath;

        this.segmentsSGP = new ArrayList<>();

//        System.out.println("Creating flow with flow ID " + flowId + " with path length " + path.getVertexList().size() +
//                " between nodes " + src.toString() + " and " + dst.toString());
        flowNodes = new ArrayList<>();

        logger.setLevel(Network.defaultLogLevel);
        this.debug = false;
    }

    public GraphPath<Node, Link> getPath() {
        return pathSGP;
    }

//    // looks like this method is not used- can be removed with caution
//    public boolean isCoflowCountDone() {
//        return coflowCountDone;
//    }
//
//    public void setCoflowCountDone(boolean coflowCountDone) {
//        this.coflowCountDone = coflowCountDone;
//    }
    public List<Node> getNodeList() {
        return pathSGP.getVertexList();
    }

    protected void setLabelStack(Stack stack, int method) {
        if (method == Network.FTS_OPT_DOMAIN) {
            this.domainLabelStack = stack;
        } else {
            this.heuristicLabelStack = stack;
        }
    }

//    protected void setHeuristicLabelStack(Stack stack) {
//        this.heuristicLabelStack = stack;
//    }
    protected Stack getLabelStack(int method) {
        if (method == Network.FTS_OPT_DOMAIN) {
            return this.domainLabelStack;
        } else {
            return this.heuristicLabelStack;
        }
    }

    public int getBitRate() {
        return bitRate;
    }

    public int getFlowId() {
        return flowId;
    }

    public Node getSrc() {
        return src;
    }

    public Node getDst() {
        return dst;
    }

    public String toString() {
        return "F" + getFlowId();
    }

    public void setDebugFlag(boolean val) {
        this.debug = val;
    }

    public void info(String msg) {
        if (logger.isLoggable(Level.INFO) || debug) {
            System.out.println(msg);
        }
    }

    public void warning(String msg) {
        if (logger.isLoggable(Level.WARNING)) {
            System.out.println("WARNING: " + msg);
        }
    }

    public void prettyPrint() {
        FlowNode fnode;

        System.out.println(toString());
        System.out.println("PATH :: " + pathSGP.getEdgeList().size() + " HOP(S) :: " 
                + pathSGP.toString());
        System.out.println("BACKUP PATH :: " + backupPathSGP.getEdgeList().size() 
                + " HOP(S) :: " + backupPathSGP.toString());

//        System.out.print("FLOW NODES :: ");
//        for (Node n : path.getVertexList()) {
//            fnode = n.getFlowNodeByFlowId(flowId);
//
//            System.out.print(fnode.node.toString() + " ");
//
//            System.out.print("] ");
//        }
        System.out.println();
    }

    private class FlowNodeComparator implements Comparator<FlowNode> {

        @Override
        public int compare(FlowNode a, FlowNode b) {
            // descending order of number of co-flow hops
            return b.getSEMetric() - a.getSEMetric();
        }
    }

    protected void addFlowNode(FlowNode fnode) {
        flowNodes.add(fnode);
    }

    // A flow is considered to have crossed the SE metric sampling threshold
    // if all the nodes (except src and dst) have crossed their threshold.
    // That is they have sufficient samples of the metric over all the flows
    // going through them. 
    // At src and dst, SE metric is not applicable. Src node can never
    // be a segment endpoint and Dst is always an endpoint.
    // method to check if there are sufficient samples of 
    // SE metric for this flow
    protected boolean hasCrossedSEMetricThreshold() {
//        if (crossedSEMetricThreshold) {
//            return true;
//        } else {
//            // invoke the source node to check
//            if (getSrc().checkSEMetricThresholdForFlow(this) == true) {
//                crossedSEMetricThreshold = true;
//                return true;
//            }
//        }
        // TEMPORARILY DISABLED
        return false;
    }

    protected void calculateSEMetric() {

        int seMetricType = net.getSEMetricType();

        // System.out.println("Calc SE Metric " + seMetricType);
        if ((seMetricType == NetworkTest.SE_HEURISTIC_B)
                || (seMetricType == NetworkTest.SE_HEURISTIC_HYBRID)) {
            // System.out.println("Calc COFLOW BO");
            getDst().calculateCoFlowBoCount(this);
        }

        if ((seMetricType == NetworkTest.SE_HEURISTIC_C)
                || (seMetricType == NetworkTest.SE_HEURISTIC_HYBRID)) {
            // System.out.println("Calc US CO FLOW for " + getFlowId());
            getSrc().calculateUsCoFlowCount(this);
        }

    }

    protected void sortFlownodes() {
        Collections.sort(flowNodes, new FlowNodeComparator());
    }

    protected void dumpSegmentEndpointMetrics() {

        System.out.println("============================");
        System.out.println("Dumping SE metrics for flow " + getFlowId()
                + " from " + getSrc().toString() + " to " + getDst().toString());
        for (FlowNode fn : flowNodes) {
            System.out.println("SE Metric at Node " + fn.node.toString() + " is "
                    + fn.getSEMetric() + "(" + fn.getUsCoFlowCount()
                    + ", " + fn.getCoFlowBoCount() + ")");
        }
    }

    protected int getSeCount() {
        return seCount;
    }

    protected void setSegmentEndpoints() {

        this.seCount = 0;

        // FlowNode firstNode = flowNodes.get(0);
        // int maxCoFlowHops = 0;
        //System.out.println("");
        //System.out.println("----------------------------------");
//        for (FlowNode fn : flowNodes) {
//            info("Flow Node " + fn.node.toString() + " has coflow hops = " + fn.getCoFlowHops()
//                    + " and coflow drop = " + fn.getCoFlowDrops());
//            if (fn.getCoFlowHops() > maxCoFlowHops) {
//                maxCoFlowHops = fn.getCoFlowHops();
//            }
//        }
        // info("Max Co Flow Hops = " + maxCoFlowHops + " for flow " + getFlowId());
        // prettyPrint();
        dst.getFlowNodeByFlowId(flowId).setSegmentEndPoint();
        this.seCount++;

        Iterator itr = flowNodes.iterator();

//        double thresholdFactor = 0.6 - net.p * 0.1;
//        if(thresholdFactor <= 0.0) thresholdFactor = 0.1;
//        
//        info("Setting threshold factor to be " + thresholdFactor);
        while (this.seCount < net.maxSLD) {

            if (itr.hasNext()) {

                FlowNode nextFN = (FlowNode) itr.next();

                if (nextFN.node.equals(this.getSrc()) || nextFN.node.equals(this.getDst())) {
                    // skip source or destination of this flow
                    continue;
                }

//                if(nextFN.getUsCoFlowCount() < maxUsCoFlows * thresholdFactor) {
//                if (nextFN.getUsCoFlowCount() == 0) {
//                    continue;
//                }
//                if (nextFN.getCoFlowHops() < maxCoFlowHops * thresholdFactor) {
////                    System.out.println("Skipping nodes under threshold %age of Max CoFlow Hops for flow " + getFlowId());
//                    continue;
//                }
//                
//                if(nextFN.getCoFlowDrops() < maxCoFlowDrops * thresholdFactor) {
//                    break;
//                }
//                System.out.println("Setting " + nextFN.node.toString() + " as end point for flow " + getFlowId() +
//                        " from " + getSrc().toString() + " to " + getDst().toString());
                nextFN.setSegmentEndPoint();
                nextFN.node.setSegmentEndpoint(true);
                this.seCount++;
            } else {
                break;
            }
        }

        info(this.seCount + " segment endpoints set for flow " + getFlowId());
    }

    // Take the primary and backup paths of the flow and divide them into
    // protected segments for SGP (Segment-Based Protection)
    protected void segmentizeSGP() {

//        System.out.println("Segmentizing Flow " + flowId);
//        prettyPrint();

        List<Node> primaryNodes = pathSGP.getVertexList();
        List<Link> primaryEdges = pathSGP.getEdgeList();

        List<Node> backupNodes = backupPathSGP.getVertexList();
        List<Link> backupEdges = backupPathSGP.getEdgeList();

        Iterator nodeItr = primaryNodes.iterator();
        Iterator backupNodeItr = backupNodes.iterator();

        Iterator edgeItr = primaryEdges.iterator();
        Iterator backupEdgeItr = backupEdges.iterator();

        List<Node> segNodes = new ArrayList<>();
        List<Link> segEdges = new ArrayList<>();

        List<Node> segBackupNodes = new ArrayList<>();
        List<Link> segBackupEdges = new ArrayList<>();

        Segment newSegment;
        GraphPath<Node, Link> newPrimary;
        GraphPath<Node, Link> newBackup;

        Node nextNode = (Node) nodeItr.next(); // get the src node
        segNodes.add(nextNode); // add to first node to the new segment

        Link nextLink = (Link) edgeItr.next(); // get the first edge
        segEdges.add(nextLink); // add to first segment

        while (nodeItr.hasNext()) {
            nextNode = (Node) nodeItr.next();

            segNodes.add(nextNode);

            if (backupNodes.contains(nextNode)) {
                // this node exists in both primary & backup. 
                // finalize the segment at this node and create the next segement

                newPrimary = new NetGraphPath(segNodes, segEdges);

                // see if this segment already exists in the network
                newSegment = net.findSegment(Segment.SEGTYPE_PROTECTED, newPrimary);

                // if not, create it
                if (newSegment == null) {
                    // System.out.println("Creating a new segment");
                    newSegment = net.createSegment(Segment.SEGTYPE_PROTECTED, newPrimary);
                } else {
//                    System.out.println("Found existing segment " + newSegment.getId());
                }

                // add this segment to the list of segments for this flow
                segmentsSGP.add(newSegment);

                // add the edges and nodes from back path
                Node nextBackupNode;
                while (backupNodeItr.hasNext()) {
                    nextBackupNode = (Node) backupNodeItr.next();
                    segBackupNodes.add(nextBackupNode);

                    if (nextBackupNode.equals(nextNode)) {
                        // back up path is complete- we have reached
                        // segment end point
                        newBackup = new NetGraphPath(segBackupNodes, segBackupEdges);
                        newSegment.setBackup(newBackup);

                        // newSegment.prettyPrint();

                        break;
                    }

                    // else continue adding edges until the segment end is reached
                    segBackupEdges.add((Link) backupEdgeItr.next());
                }

                if (nodeItr.hasNext()) {
                    // there are more nodes in the primary path to process
                    segNodes = new ArrayList<>();
                    segEdges = new ArrayList<>();

                    segNodes.add(nextNode);
                    segEdges.add((Link) edgeItr.next());

                    segBackupNodes = new ArrayList<>();
                    segBackupEdges = new ArrayList<>();

                    segBackupNodes.add(nextNode);
                    segBackupEdges.add((Link) backupEdgeItr.next());
                }

            } else {
                // this is NOT the last node in this segment
                // so, add the next link to the segment
                segEdges.add((Link) edgeItr.next());
            }

        }

    }

    public int getPrimaryPathLength() {
        int plen = 0;

        for (Segment seg : segmentsSGP) {
            plen += seg.getPrimary().getEdgeList().size();
        }

        return plen;
    }

    public int getBackupPathLength() {
        int plen = 0;

        for (Segment seg : segmentsSGP) {
            plen += seg.getBackup().getEdgeList().size();
        }

        return plen;
    }

    public int getOpPathLength() {
        int plen = 0;

        for (Segment seg : segmentsSGP) {
            if (seg.getOpstate() == Network.OPSTATE_UP) {
                plen += seg.getPrimary().getEdgeList().size();
            } else {
                plen += seg.getBackup().getEdgeList().size();
            }
        }

        return plen;
    }
    
    public int getOpstate() {
        int opstate = Network.OPSTATE_UP;
        
        for (Segment seg : segmentsSGP) {
            if (seg.getOpstate() == Network.OPSTATE_DOWN) {
                // if any of the segments are down, then the flow's opstate is down
                return Network.OPSTATE_DOWN;
            } 
            if (seg.getOpstate() == Network.OPSTATE_BACKUP) {
                // if any of the segments are using backup, flow is in backup state
                opstate = Network.OPSTATE_BACKUP;
            }
        }  
        
        return opstate;
    }
}
