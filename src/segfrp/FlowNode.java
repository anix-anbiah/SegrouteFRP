/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * @author anix
 */
public class FlowNode {

    Flow flow;  // the flow of which this is a node

    Node node;      // the network node on which this flow node exists

    // ingress port for this flow path
    Port ingressPort;

    // egress port for this flow path
    Port egressPort;

    // flag to indicate if this node is a segment endpoint for this flow
    boolean segmentEndPoint;

    // BEGIN These are NOT needed for FTS optimization- can be removed
    // indices (markers) indicating the segment of the SFC
    // hosted on this node. VNF instances between the prev and next markers
    // are on this node
    int previousIndex;
    int nextIndex;

    int dsDifferential; // downstream differential
    int dsMaxUtility;
    int dsDiffNodeId;   // Node ID if the source of the d/s differential
    boolean dsOfferPending;
    double dsOfferTimestamp; // time when offer was initiated
    // END

    Map<Integer, CoFlow> coflows; // map of other flows that flow together with this flow
    // map is from flow ID of co-flow to the number of hops in shared sub-path

    Set<Flow> localCoFlows;
    Set<Flow> usCoFlows;

    private int usCoFlowCount;

    private int coFlowHopCount;
    private int coFlowBoCount; // Branch out count

    //   private boolean seMetricCalculated;
    public FlowNode(Node localNode, Flow flow, Port ingress, Port egress) {

        this.node = localNode;
        this.flow = flow;

        this.ingressPort = ingress;
        this.egressPort = egress;

        // co-flows are other flows that share the ingress port with this flow
        this.coflows = new TreeMap<>();

        //      this.seMetricCalculated = false;
    }

    public Flow getFlow() {
        return flow;
    }

    public int getPreviousIndex() {
        return previousIndex;
    }

    public int getNextIndex() {
        return nextIndex;
    }

    public void setPreviousIndex(int previousIndex) {
        this.previousIndex = previousIndex;
    }

    public void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
    }

    public void setSegmentEndPoint() {
        this.segmentEndPoint = true;
    }

    public boolean isSegmentEndPoint() {
        return segmentEndPoint;
    }

    protected Node getDsNode() {
        return egressPort.getNeighbor();
    }

    protected Node getUsNode() {
        return ingressPort.getNeighbor();
    }

    protected void calculateUsCoFlowCount(Set<Integer> usCoFlows) {

        this.usCoFlowCount = 0;
        
        Set<Integer> localFlowIds = node.getFlowNodes().keySet();

        for (Integer localFlowId : localFlowIds) {

            if (localFlowId.equals(flow.getFlowId())) {
                // don't consider the flow for which 
                // heuristic metric is being computed
                continue;
            }

            if (usCoFlows.contains(localFlowId)) {
                this.usCoFlowCount++;
            } else {
                usCoFlows.add(localFlowId);
            }
        }

        Network.info("Upstream co-flow count for " + flow.toString()
                + " at " + node.toString() + " is " + this.usCoFlowCount);

        // node.addSEMetric(usCoFlowCount);

        if (flow.getDst().equals(node)) {
            // reached the destination node
            return;
        }

        // else find the downstream node
        Node dsNode = egressPort.getNeighbor();

        dsNode.calculateUsCoFlowCount(flow, usCoFlows);
    }

    protected void calculateCoFlowBoCount() {

        // check if this node is the source for this flow
        if (flow.getSrc().equals(node)) {
            // nothing to do
            return;
        }

        // initialize the co-flow BO count
        this.coFlowBoCount = 0;

        Map<Integer, FlowNode> coFlowNodes = node.getFlowNodes();
        for (FlowNode coFlowNode : coFlowNodes.values()) {
            if (coFlowNode.equals(this)) {
                continue;
            }

            if (coFlowNode.ingressPort.equals(ingressPort)) {

                Flow coFlow = coFlowNode.getFlow();
                // add to map if co-flow shares ingress port
                coflows.put(coFlow.getFlowId(), new CoFlow(flow, coFlow));
            }
        }

        // find the upstream node 
        Node usNode = ingressPort.getNeighbor();

        // let the upstream node update the coflow map
        usNode.updateCoFlowBoCount(flow, coflows);

        // update the cumulative co-flow hops count
        coFlowHopCount = 0;
        for (CoFlow coFlow : coflows.values()) {
            coFlowHopCount += coFlow.getHops();
        }

        // this.coFlowBoCount = usCoFlowHops - coFlowHopCount;
    }

    // update the downstream co-flow map and return the 
    // local co-flow map count
    public void updateCoFlowHopCount(Map<Integer, CoFlow> dsCoFlows) {

        if (flow.getSrc().equals(node)) {
            // this is the source node for the flow
            // update the coFlowMap and return
            for (CoFlow cflow : dsCoFlows.values()) {
                cflow.incrementHops();
            }

            this.coFlowBoCount = 0;

            return;
        }

        // else this is an interim node with more nodes upstream
        // first create the local coflow map
        calculateCoFlowBoCount();

        CoFlow coFlow;

        int dsCoFlowHops = 0, dsHops;

        // now update the ingress co-flow Map and return
        for (CoFlow dsCoFlow : dsCoFlows.values()) {

            int dsCoFlowId = dsCoFlow.getCoFlow().getFlowId();
            if (coflows.containsKey(dsCoFlowId)) {
                coFlow = coflows.get(dsCoFlowId);
                dsHops = coFlow.getHops();
                dsCoFlowHops += dsHops;
                dsCoFlow.setHops(dsHops + 1);
            } else {
                // dsCoFlowHops++;
                dsCoFlow.setHops(1);
            }
        }

        coFlowBoCount = getCoFlowHopCount() - dsCoFlowHops;

        // add this SE Metric sample to this node
        node.addSEMetric(coFlowBoCount);

        coflows = null; // we don't need this map any more

        //return getCoFlowHopCount();
    }

    protected int getCoFlowHopCount() {
        return coFlowHopCount;
    }

    protected int getCoFlowBoCount() {
        return coFlowBoCount;
    }

    public int getUsCoFlowCount() {
        return usCoFlowCount;
    }

    public int getSEMetric() {

        if (flow.hasCrossedSEMetricThreshold()) {
            // this flow uses average metrics of other flows.
            // get the average metric from the node.
//            System.out.println("Using Node Max metric for flow " + flow.getFlowId() +
//                    " Flow hops = " + flow.getPath().getLength());
            return (int) node.getMaxSEMetric();
        }

        int seMetricType = node.getNetwork().getSEMetricType();

        // else return the metrics calculated for this flow
        if (seMetricType == NetworkTest.SE_HEURISTIC_B) {
            // Heuristic-B
            return getCoFlowBoCount();
        } else if (seMetricType == NetworkTest.SE_HEURISTIC_C) {

            // else, it is HEURISTIC-C
            return getUsCoFlowCount();
        } else if (seMetricType == NetworkTest.SE_HEURISTIC_N) {
            return node.getNodeSEMetric();
        }
        
        // HYBRID case
        return (int) (0.5 * getCoFlowBoCount() + 0.5 * getUsCoFlowCount());

    }
}
