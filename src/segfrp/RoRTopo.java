/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import eduni.simjava.Sim_system;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jgrapht.GraphPath;

/**
 *
 * @author anix
 */
public class RoRTopo {

    private Network net;
    private NetworkTest netTest;
    private Map<Integer, RingNode> ringNodes; // map of nodes indexed by node ID string
    private final Ring coreRing;
    private final int CORE_RING_ID = 1;

    private final int topoRingSize;
    private final int topoRingHeight;
    private final int p; // number of partitions to apply

    public RoRTopo(Network net, NetworkTest netTest, int ringSize, int height, int p) {
        this.net = net;
        this.netTest = netTest;
        this.topoRingSize = ringSize;
        this.topoRingHeight = height;
        this.p = p;

        ringNodes = new HashMap<>();

        coreRing = new Ring(ringSize, height, CORE_RING_ID, true, null, p);

        System.out.println("Number of entities in Sim_system = "
                + Sim_system.get_num_entities());

        System.out.println("Added " + net.nodes.size() + " nodes and "
                + net.getGraph().edgeSet().size() + " links ");

    }

    private class RingNode {

        Node node;
        Ring ring; // the ring to which the node belongs
        Ring childRing; // the next level down

        public RingNode(Node node, Ring ring) {
            this.node = node;
            this.ring = ring;
        }

        public Node getNode() {
            return node;
        }

        public Ring getRing() {
            return ring;
        }

        public Ring getChildRing() {
            return childRing;
        }

        public int getSrDomainId() {
            return node.getSrDomainId();
        }

    }

    protected void createInitialFlows() {

        Network.warning("Creating flows in RoR Topo");

        int flowCount = 0;

        int numFlows = netTest.getNumFlows();

        for (int flowNum = 0; flowNum < numFlows; flowNum++) {

            ringCreateFlowBetweenRandomNodes(NetworkTest.DEFAULT_FLOW_RATE);

        }
    }

    private RingNode ringCreateNodeWithId(int nodeId, int nodeType, Ring ring, int srDomainId) {

        Node swtch;

        // use dummy x,y coordinates for now
        swtch = net.createNodeWithId(nodeId, nodeType, srDomainId, 100, 100);

        return new RingNode(swtch, ring);

    }

    private class Ring {

        private final int size; // number of nodes
        private final int height; // levels of sub-rings to create
        private final boolean core;
        private RingNode parentNode;

        // Hack alert
        private RingNode firstNode = null;
        private RingNode secondNode = null;
        private RingNode lastNode = null;

        List<RingNode> nodes;

        private final int p; // number of times to apply partition

        public Ring(int size, int height, int ringId, boolean core, RingNode parentNode, int p) {
            this.size = size;
            this.height = height;
            this.core = core;
            this.parentNode = parentNode; // null for core ring!

            this.p = p; // number of partitions to apply

            // SRFRP change
            // If this is a child ring, make the parent ring part of the child ring
            // So, create one less number of nodes
            int nodesToCreate = (parentNode == null) ? size : size - 1;
            int srDomainId;

            nodes = new ArrayList<>();

            // This is no longer true due to dual homing
//            if (core != null) {
//                // core node is part of this ring
//                nodes.add(core);
//                nodesToCreate--;
//            }
            RingNode node;
            int nodeId;

            for (int nd = 1; nd <= nodesToCreate; nd++) {

                // calculate the SR domain ID
                srDomainId = 0; // default

                // DELETED FOR SRFRP
//                if (!core) {
//                    srDomainId = parentNode.getSrDomainId();
//
//                } else {
//                    if (p == 0) {
//                        //default p=0
//                        srDomainId = 0;
//                    } else {
//
//                        if (p == 1) {
//                            // p = 1
//                            srDomainId = (nd <= size/2) ? 1:2; 
//                        } else if (p == 2) {
//                            // p = 2
//                            if (nd <= size / 4) {
//                                srDomainId = 1;
//                            } else if (nd <= size / 2) {
//                                srDomainId = 2;
//                            } else if (nd <= size * 3 / 4) {
//                                srDomainId = 3;
//                            } else {
//                                srDomainId = 4;
//                            }
//                        } else if (p == 3) {
//                            // p = 3
//                            if (nd <= size / 8) {
//                                srDomainId = 1;
//                            } else if (nd <= size / 4) {
//                                srDomainId = 2;
//                            } else if (nd <= size * 3 / 8) {
//                                srDomainId = 3;
//                            } else if (nd <= size / 2) {
//                                srDomainId = 4;
//                            } else if (nd <= size * 5 / 8) {
//                                srDomainId = 5;
//                            } else if (nd <= size * 3 / 4) {
//                                srDomainId = 6;
//                            } else if (nd <= size * 7 / 8) {
//                                srDomainId = 7;
//                            } else {
//                                srDomainId = 8;
//                            }
//                        }
//                    }
//                }
                nodeId = ringId * 100 + nd;
                node = ringCreateNodeWithId(nodeId,
                        ((height == 1) ? Node.Type.OTHER : Node.Type.CORE),
                        this, srDomainId);

                nodes.add(node);
                ringNodes.put(nodeId, node);

                if (nd == 1) {
                    firstNode = node;
                }

                if (nd == 2) {
                    secondNode = node;
                }

                if (nd == nodesToCreate) {
                    lastNode = node;
                }

//                if (height > 1 && (nd % 2 == 0)) {
                if (height > 1 && (nd % 2 == 0)
                        && (core || (nd != 2))) {
                    // create a child ring only IF
                    // (i) this is not a LEAF ring (last level)
                    // (ii) on an even node in the ring (create one child ring for 
                    //      each pair of nodes in the ring for dual homing)
                    // (iii) NOT on a node which is connected to parent ring
                    //       (first & second nodes connect to parent ring)
                    // node.childRing = new Ring(size, height - 1, nodeId, node);
                    node.childRing = new Ring(size, height - 1, nodeId, false, node, p);

                }

            }

            // now add the edges in this ring
            Iterator<RingNode> nodeItr = nodes.iterator();
            RingNode initNode = nodeItr.next();
            RingNode prevNode = initNode;

            if (parentNode != null) {
                net.addEdge(parentNode.getNode(), initNode.getNode());
            }

            RingNode nextNode;

            while (nodeItr.hasNext()) {
                nextNode = nodeItr.next();

                net.addEdge(prevNode.getNode(), nextNode.getNode());

//                ndId = nextNode.getNode().getNodeId();
//
//                chRing = nextNode.getChildRing();
//                if (chRing != null) {
////                if (ndId % 2 == 0 && (height > 1)) {
//                    // HACK - even node
//
//                    // TEMPORARY CHANGE FOR EDGE-DISJOINT
//                    // DUAL HOME THE CHILD RING TO THE SAME NODE
//                    // ON THE PARENT RING.
//                    net.addEdge(nextNode.getNode(), chRing.firstNode.getNode());
//
//                    // FOLLOWING CODE DUAL HOMES CHILD RING AT TWO DIFF NODES
//                    // net.addEdge(prevNode.getNode(), chRing.firstNode.getNode());
//                    net.addEdge(nextNode.getNode(), chRing.secondNode.getNode());
//
//// Adding the cross links
////                    net.addEdge(prevNode.getNode(), chRing.secondNode.getNode());
////                    net.addEdge(nextNode.getNode(), chRing.firstNode.getNode());
//                }
                prevNode = nextNode;

            }

            // SRFRP- if this is a child ring, add edge between last node
            // and the parent node essentially linking parent node into the child ring
            // else, finish the ring by adding edge between last node and first node
            if (parentNode != null) {
                net.addEdge(prevNode.getNode(), parentNode.getNode());
            } else {
                net.addEdge(prevNode.getNode(), initNode.getNode());
            }

        }

        public boolean isCoreRing() {
            return core;
        }

        public int getSize() {
            return size;
        }

        public int getHeight() {
            return height;
        }

    }

    // get a random ring node. Nodes on core ring and given excluded ring are excluded
// from consideration
    private RingNode getRandomRingNode(Ring excludedRing, RingNode excludedNode) {
        Object[] allNodes;
        RingNode randomNode;
        int numNodes = ringNodes.size();
        int index;

        allNodes = (ringNodes.values().toArray());

        index = (int) (Math.random() * numNodes);
        randomNode = (RingNode) allNodes[index];

        while (randomNode.getRing().equals(excludedRing)
                || randomNode.equals(excludedNode)) {
            //    || randomNode.getRing().equals(coreRing)) {
            // System.out.println("Trying again");
            // try again
            index = (int) (Math.random() * numNodes);
            randomNode = (RingNode) allNodes[index];
        }

        return randomNode;
    }

    private RingNode getRandomRingNode() {
        return getRandomRingNode(null, null);
    }

    GraphPath route(RingNode srcNode, RingNode dstNode) {
        return null;
    }

    private void ringCreateFlowBetweenRandomNodes(int bitRate) {

        Flow flow;

        RingNode srcNode = getRandomRingNode((topoRingHeight == 1) ? null : coreRing, null);

        //    boolean isRootPodAllowed = !srcNode.getPod().isRootPod();
        // RingNode dstNode = getRandomRingNode(srcNode.getRing());
        RingNode dstNode = getRandomRingNode((topoRingHeight == 1) ? null : coreRing, srcNode);

//        if(net.getFlowBySrcAndDst(srcNode.getNode(), dstNode.getNode()) != null) {
//            System.out.println("Duplicate flow- trying different src/dst pair");
//            ringCreateFlowBetweenRandomNodes(bitRate);
//            return;
//        }
        //    GraphPath path = route(srcNode, dstNode);
//        System.out.println("Creating flow between " + srcNode.getNode().name +
//                " and " + dstNode.getNode().name);
        flow = net.createFlowBetweenNodes(srcNode.getNode(), dstNode.getNode(), bitRate);
        net.addFlowHops(flow.getPath().getVertexList().size());

        // This should happen in Network class
//        Traffic.routePacket(flow, Network.FTS_OPT_DOMAIN);
    }

}
