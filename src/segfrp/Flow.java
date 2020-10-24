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

    // Segment-Based Protection (SBP) member variables
    private final List<Segment> segmentsSBP; // list of segments of the end-to-end path;
    private final GraphPath<Node, Link> pathSBP, backupPathSBP;

    // TI-LFA member variables
    private final GraphPath<Node, Link> spath; // shortest path
    private int tilfaOpstate;   // Op state for TI-LFA
    private int tilfaOpPathlen; // path len of TI-LFA

    // TI-MFA member variables
    private int timfaOpstate;   // Op state for TI-MFA
    private int timfaOpPathlen; // path len of TI-MFA

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
            GraphPath<Node, Link> spath,
            GraphPath<Node, Link> sbpPath, GraphPath<Node, Link> sbpBackupPath) {

        this.net = net;
        this.src = src;
        this.dst = dst;

        this.bitRate = bitRate;
        this.flowId = flowId;

        this.crossedSEMetricThreshold = false;

        this.spath = spath; // shortest path
        this.tilfaOpstate = Network.OPSTATE_UNKNOWN;
        this.tilfaOpPathlen = 0;

        this.timfaOpstate = Network.OPSTATE_UNKNOWN;
        this.timfaOpPathlen = 0;

        this.pathSBP = sbpPath;  // SBP Primary
        this.backupPathSBP = sbpBackupPath; // SBP Backup

        this.segmentsSBP = new ArrayList<>();

//        System.out.println("Creating flow with flow ID " + flowId + " with path length " + path.getVertexList().size() +
//                " between nodes " + src.toString() + " and " + dst.toString());
        flowNodes = new ArrayList<>();

        logger.setLevel(Network.defaultLogLevel);
        this.debug = false;
    }

    public GraphPath<Node, Link> getPath() {
        return pathSBP;
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
        return pathSBP.getVertexList();
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
        System.out.println("SBP PRIMARY PATH :: " + pathSBP.getEdgeList().size() + " HOP(S) :: "
                + pathSBP.toString());
        System.out.println("SBP BACKUP PATH :: " + backupPathSBP.getEdgeList().size()
                + " HOP(S) :: " + backupPathSBP.toString());

        System.out.println("SHORTEST PATH :: " + spath.getLength() + " HOP(S) :: "
                + spath.toString());

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

    private class NodeComparator implements Comparator<Node> {

        @Override
        public int compare(Node a, Node b) {
            // descending order of number of co-flow hops
            return b.getNumFlows() - a.getNumFlows();
        }
    }

    private List<Node> findSegmentEndPoints(int maxSLD) {

        List<Node> segmentEndPoints = new ArrayList<>();
        int seCount = 0;

        Iterator primaryNodesItr = pathSBP.getVertexList().iterator();
        List<Node> backupNodes = backupPathSBP.getVertexList();

        // add the destination node as one of the segment end points by default
        segmentEndPoints.add(dst);
        seCount++;

        Node nextNode;
        while (primaryNodesItr.hasNext()) {
            nextNode = (Node) primaryNodesItr.next();

            // skip the source node
            if (nextNode.equals(src)) {
                continue;
            }

            // skip destination node as it has already been added
            // and exit since we've already reached the destination
            if (nextNode.equals(dst)) {
                break;
            }

            if (backupNodes.contains(nextNode)) {
                segmentEndPoints.add(nextNode);
                seCount++;
            }
        }

        if (seCount <= maxSLD) {
            return segmentEndPoints;
        }

        // sort the segment endpoints based on the number
        // of flows routed through them (in decreasing order)
        Collections.sort(segmentEndPoints, new NodeComparator());

        seCount = 0;

        // now pick the top maxSLD segment endpoints
        List<Node> finalSegmentEndPoints = new ArrayList<>();

        // add the destination node by default
        finalSegmentEndPoints.add(dst);
        seCount++;

        Iterator seItr = segmentEndPoints.iterator();
        while (seItr.hasNext() & (seCount < maxSLD)) {
            nextNode = (Node) seItr.next();
            if (dst.equals(nextNode)) {
                continue;
            }

            finalSegmentEndPoints.add(nextNode);
            seCount++;
        }

        return finalSegmentEndPoints;

    }

    // Take the primary and backup paths of the flow and divide them into
    // protected segments for SGP (Segment-Based Protection)
    protected void segmentizeForSBP(int maxSLD) {

        List<Node> sePoints = findSegmentEndPoints(maxSLD);

//        System.out.println("Segmentizing Flow " + flowId);
//        prettyPrint();
        List<Node> primaryNodes = pathSBP.getVertexList();
        List<Link> primaryEdges = pathSBP.getEdgeList();

        List<Node> backupNodes = backupPathSBP.getVertexList();
        List<Link> backupEdges = backupPathSBP.getEdgeList();

        Iterator nodeItr = primaryNodes.iterator();
        Iterator backupNodeItr = backupNodes.iterator();

        Iterator edgeItr = primaryEdges.iterator();
        Iterator backupEdgeItr = backupEdges.iterator();

        List<Node> segNodes = new ArrayList<>();
        List<Link> segEdges = new ArrayList<>();

        List<Node> segBackupNodes = new ArrayList<>();
        List<Link> segBackupEdges = new ArrayList<>();

        Segment nextSegment;
        GraphPath<Node, Link> newPrimary;
        GraphPath<Node, Link> newBackup;

        Node nextNode = (Node) nodeItr.next(); // get the src node
        segNodes.add(nextNode); // add to first node to the new segment

        Link nextLink = (Link) edgeItr.next(); // get the first edge
        segEdges.add(nextLink); // add to first segment

        while (nodeItr.hasNext()) {
            nextNode = (Node) nodeItr.next();

            segNodes.add(nextNode);

            // check if this node is a segment endpoint
            if (sePoints.contains(nextNode)) {
                // this node exists in both primary & backup and is a segment endpoint. 
                // finalize the segment at this node and create the next segement

                newPrimary = new NetGraphPath(segNodes, segEdges);

                boolean newSegment = false;

                // see if this segment already exists in the network
                nextSegment = net.findSegment(Segment.SEGTYPE_PROTECTED, newPrimary);

                // if not, create it
                if (nextSegment == null) {
                    // System.out.println("Creating a new segment");
                    nextSegment = net.createSegment(Segment.SEGTYPE_PROTECTED, newPrimary);
                    newSegment = true;
                } else {
//                    System.out.println("Found existing segment " + newSegment.getId());
                }

                // add this segment to the list of segments for this flow
                segmentsSBP.add(nextSegment);

                // add the edges and nodes from back path
                Node nextBackupNode;
                while (backupNodeItr.hasNext()) {
                    nextBackupNode = (Node) backupNodeItr.next();
                    segBackupNodes.add(nextBackupNode);

                    if (nextBackupNode.equals(nextNode)) {
                        // back up path is complete- we have reached
                        // segment end point
                        // if this is a new segment, add the backup paths
                        if (newSegment) {
                            newBackup = new NetGraphPath(segBackupNodes, segBackupEdges);
                            nextSegment.addInitialBackup(newBackup);

                            for (int i = 0; i < (Network.SBP_MAX_BACKUPS - 1); i++) {
//                                System.out.println("SegmentizeSBP: Adding backup #" +
//                                        (i+2) + " for flow " + toString());
                                nextSegment.addBackup();
                            }

                            // TBD We may need to sort backups based on path length
                        }

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

    public int getSbpPrimaryPathLength() {
        int plen = 0;

        for (Segment seg : segmentsSBP) {
            plen += seg.getPrimary().getEdgeList().size();
        }

        return plen;
    }
    
    public int getShortestPathLength() {
        return spath.getLength();
    }

    public int getTilfaPrimaryPathLength() {
        return spath.getLength();
    }

    public int getTimfaPrimaryPathLength() {
        return spath.getLength();
    }

    public int getTilfaOpPathLength() {
        if (tilfaOpstate == Network.OPSTATE_UNKNOWN) {
            Network.warning("getTilfaOpPathLength: state is UNKNOWN");
            return -1;
        }

        return tilfaOpPathlen;
    }

    public int getTimfaOpPathLength() {
        if (timfaOpstate == Network.OPSTATE_UNKNOWN) {
            Network.warning("getTimfaOpPathLength: state is UNKNOWN");
            return -1;
        }

        return timfaOpPathlen;
    }

    public int getSbpBackupPathLength() {
        int plen = 0;

        for (Segment seg : segmentsSBP) {
            plen += seg.getBackup().getEdgeList().size();
        }

        return plen;
    }

    // SBP Op Path length of the flow is the sum of op path lengths of the segments
    public int getSbpOpPathLength(boolean updateLinkStats) {
        int plen = 0;
        GraphPath<Node,Link> opPath;
        Iterator itr;
        Link lnk;

        for (Segment seg : segmentsSBP) {
            opPath = seg.getOpPath();
            plen += seg.getOpPathLength();
            
            if(!updateLinkStats) {
                continue;
            }
            
            // update the SBP operational flow count of 
            // links in the operational path
            itr = opPath.getEdgeList().iterator();
            while(itr.hasNext()) {
                lnk = (Link) itr.next();
                lnk.addSbpOpFlow(this);
            }
        }

        return plen;
    }

    // get the operational state of this flow, subject to 
    // the allowed max backup for each segment
    public int getSbpOpstate(int maxBackup) {
        int opstate = Network.OPSTATE_UP;
        
        int segOpstate;

        for (Segment seg : segmentsSBP) {
            segOpstate = seg.getOpstate(maxBackup);
            
            if (segOpstate == Network.OPSTATE_DOWN) {
                // if any of the segments are down, then the flow's opstate is down
                return Network.OPSTATE_DOWN;
            }
            if (segOpstate == Network.OPSTATE_BACKUP) {
                // if any of the segments are using backup, flow is in backup state
                opstate = Network.OPSTATE_BACKUP;
            }
        }

        return opstate;
    }

    // Get the operationl state for TI-LFA or TI-MFA (based on boolean param)
    public int getTilfaOpstate(boolean timfa) {

        int spLen = 0;
        List<Link> failedLinks = new ArrayList<>();
        int opstate;

        // check if any link along the shortest path of the flow is down
        for (Link lnk : spath.getEdgeList()) {
            if (lnk.getOpstate() == Network.OPSTATE_DOWN) {
//                spLen = tilfaOpPathlen;

                opstate = findTilfaRouteState(lnk.getSource(), lnk, failedLinks, timfa);

//                System.out.println("getTilfaOpstate. Flow Id " + getFlowId()
//                        + ". Op Path Len = " + tilfaOpPathlen
//                        + ". Shortest Path len = " + spath.getEdgeList().size() 
//                        + ". Initial unfailed path segment = " + spLen
//                        + ". Num of failed links = " + failedLinks.size()
//                        + ". Op state = " + ((tilfaOpstate == Network.OPSTATE_UP) ? "UP" : "DOWN"));
                if (timfa) {
                    // restore all the failed links in the graph
                    for (Link flnk : failedLinks) {
                        net.graph.setEdgeWeight(flnk, Graph.DEFAULT_EDGE_WEIGHT);
                    }

                    timfaOpstate = opstate;
                } else {

                    tilfaOpstate = opstate;
                }

                return opstate;
            }

            if (timfa) {
                lnk.addTimfaOpFlow(this);
                timfaOpPathlen++;
            } else {
                lnk.addTilfaOpFlow(this);
                tilfaOpPathlen++;
            }
        }

        if (timfa) {
            timfaOpstate = Network.OPSTATE_UP;
        } else {
            tilfaOpstate = Network.OPSTATE_UP;
        }

        return Network.OPSTATE_UP;

//            if (lnk.getOpstate() == Network.OPSTATE_DOWN) {
//                // now check if the Shortest path from link source
//                // to the flow destination has any downed links
////                System.out.println("TILFA Op state for Flow " + getFlowId());
////                System.out.println("Link " + lnk.toString() + " is down");
////                System.out.println((getSbpOpstate() == Network.OPSTATE_DOWN)
////                        ? "SBP Op state is DOWN " : "SBP Op state is UP ");
//
//                if (getSbpOpstate() == Network.OPSTATE_DOWN) {
////                    System.out.println("SBP Primary " + pathSBP.toString());
////                    System.out.println("SBP Backup  " + backupPathSBP.toString());
//                }
//
//                // Remove the downed link from the network before
//                // computing the repair path
//                net.graph.setEdgeWeight(lnk, 100000);
//
//                rPath = net.getShortestPath(lnk.getSource(), dst);
//
//                net.graph.setEdgeWeight(lnk, Graph.DEFAULT_EDGE_WEIGHT);
//
////                System.out.println("Found repair path " + rPath.toString());
//                for (Link rlnk : rPath.getEdgeList()) {
//
//                    if (rlnk.getOpstate() == Network.OPSTATE_DOWN) {
////                        System.out.println("Repair path has a link down " + rlnk.toString());
//
//                        // the repair path also has a link down
//                        // check the if the repair path from this
//                        // down link uses the original downed link.
//                        // If so, this flow will be dropped.
//                        net.graph.setEdgeWeight(rlnk, 100000);
//
//                        rrPath = net.getShortestPath(rlnk.getSource(), dst);
//
//                        net.graph.setEdgeWeight(rlnk, Graph.DEFAULT_EDGE_WEIGHT);
//
////                        System.out.println("Re-repair path is " + rrPath.toString());
//                        for (Link rrlnk : rrPath.getEdgeList()) {
//                            if (rrlnk.equals(lnk)) {
////                                System.out.println("Re-repair path has original down link");
//
//                                return Network.OPSTATE_DOWN;
//
//                            }
//
//                            // This is probably not right. This needs to be recursive
//                            if (rrlnk.getOpstate() == Network.OPSTATE_DOWN) {
////                                System.out.println("Re-repair path has link down");
//
//                                return Network.OPSTATE_DOWN;
//                            }
//                        }
//
////                        System.out.println("Re-repair path is operational");
//                        // must not continue along repair path
//                        return Network.OPSTATE_UP;
//                    }
//                }
//
////                System.out.println("Repair path is operational");
//                return Network.OPSTATE_UP;
//
//            }
//        }
    }

    // Find if there is a route to destination from a local point of repair
    // to the destination. The local link that has failed and the list of
    // previously known failedlinks are also given
    private int findTilfaRouteState(Node lpr, Link localFailedLink, List<Link> failedLinks, boolean timfa) {

//        System.out.println("findTilfaRouteState: Flow Id " + getFlowId()
//                + " from " + src + " to " + dst
//                + " LPR = " + lpr.toString() + ". Failed link = " + localFailedLink.toString()
//                + " Num failed links = " + failedLinks.size());
        // first find the shortest path from LPR (local point of repair)
        // to the destination, but exclude the local failed link
        net.graph.setEdgeWeight(localFailedLink, Network.EDGE_WEIGHT_MAX);

        failedLinks.add(localFailedLink);

        GraphPath<Node, Link> route = net.getShortestPath(lpr, dst);

//        System.out.println("Repair path = " + route.toString());
        // If it is TI-LFA, then LPR only uses the local failed link to
        // find the repair path. For TI-MFA, all upstream failures are
        // taken into account. So, don't restore the failed link just yet.
        // Failed links will be restored later when repair path is fully computed.
        if (!timfa) {
            net.graph.setEdgeWeight(localFailedLink, Graph.DEFAULT_EDGE_WEIGHT);
        }

        for (Link lnk : route.getEdgeList()) {

            if (failedLinks.contains(lnk)) {
                return Network.OPSTATE_DOWN;
            }

            if (lnk.getOpstate() == Network.OPSTATE_DOWN) {

                return findTilfaRouteState(lnk.getSource(), lnk, failedLinks, timfa);
            }

            if (timfa) {
                lnk.addTimfaOpFlow(this);
                timfaOpPathlen++;
            } else {
                lnk.addTilfaOpFlow(this);
                tilfaOpPathlen++;
            }
        }

        // there are now downed links along the path
        return Network.OPSTATE_UP;

    }

}
