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
import java.util.Stack;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

/**
 *
 * @author anix
 */
public class FtTopo {

    // DCN Topology nodes
    private Network net;
    private NetworkTest netTest;
    private final int p; // number of partitions to apply

    private int numLinks;
    private Map<Integer, FtNode> ftNodes; // map of nodes indexed by node ID string
    private int numFlows;

    public FtTopo(Network net, NetworkTest netTest, int k, int h, int p, int numFlows) {
        this.net = net;
        this.netTest = netTest;
        this.p = p;

        this.numFlows = numFlows;

        createFtTopology(k, h, p);

        System.out.println("Number of entities in Sim_system = "
                + Sim_system.get_num_entities());

        System.out.println("Added " + net.nodes.size() + " nodes and "
                + net.graph.edgeSet().size() + " links ");

    }

    private class FtNode {

        Node  node;
        FtPod pod; // the pod in which this node is present
        int   srDomainId;

        public FtNode(Node node, FtPod pod, int srDomainId) {
            this.node = node;
            this.pod = pod;
            this.srDomainId = srDomainId;
        }

        public Node getNode() {
            return node;
        }

        public FtPod getPod() {
            return pod;
        }
        
        public int getSrDomainId() {
            return srDomainId;
        }

    }

    private FtNode ftCreateNodeWithId(int nodeId, int nodeType, int srDomainId, FtPod pod) {

        Node swtch;

        // use dummy x,y coordinates for now
        swtch = net.createNodeWithId(nodeId, nodeType, srDomainId, 100, 100);

        return new FtNode(swtch, pod, srDomainId);

    }

    private GraphPath route(FtNode src, FtNode dst) {

        // assumption- at least one of the nodes must not be in the root pod
        FtPod srcPod = src.getPod();
        FtPod dstPod = dst.getPod();
        FtNode srcNode, dstNode;

        List<Node> srcPath;
        Stack<Node> dstPath;
        List<Link> links;

//        System.out.println("route: computing route from " + src.getNode().getNodeName()
//                + " and " + dst.getNode().getNodeName());
        if (srcPod.isRootPod() && dstPod.isRootPod()) {
            return null;
        }

        // assumption- src and dst have to be in different pods
        if (srcPod.equals(dstPod)) {
            return null;
        }

        srcPath = new ArrayList<>();
        dstPath = new Stack<>();
        links = new ArrayList<>();
        srcNode = src;
        dstNode = dst;

        // start from the lower among the heights of src & dst pod
        // and move your way up the FT
        int height = (srcPod.getH() > dstPod.getH()) ? dstPod.getH() : srcPod.getH();

        srcPath.add(srcNode.getNode());
        dstPath.push(dstNode.getNode());

        while (!srcPod.equals(dstPod)) {
            if (dstPod.getH() == height) {
                // go one level up from dst Pod
                dstPod = dstPod.parent;
                if (dstPod.equals(srcPod)) {
                    continue;
                }
                dstNode = dstPod.getRandomNode();
                dstPath.push(dstNode.getNode());
            }

            if (srcPod.getH() == height) {

                // go one level up from the src pod
                srcPod = srcPod.parent;
                if (srcPod.equals(dstPod)) {
                    continue;
                }
                srcNode = srcPod.getRandomNode();
                srcPath.add(srcNode.getNode());
            }

            height++;
        }
        // now, the path from src to dst is in srcPath and dstPath
        // pop the nodes from dstPath and add to srcPath
        while (!dstPath.isEmpty()) {
            srcPath.add(dstPath.pop());
        }

        // now src path is the full path from src to dst
        Iterator nodeItr = srcPath.iterator();
        Node currNode = null;
        Node nextNode = null;
        Link nextLink;

        while (nodeItr.hasNext()) {
            nextNode = (Node) nodeItr.next();
            if (currNode != null) {
                nextLink = net.graph.getEdge(currNode, nextNode);
//                System.out.print(nextLink.getName() + " ");
                links.add(nextLink);
            }
            currNode = nextNode;
        }

//        System.out.println(" End of Route");
        return new NetGraphPath(srcPath, links);
    }

//    private class FtGraphPath<Node, Link> implements GraphPath {
//
//        private List<Node> nodes;
//        private List<Link> edges;
//
//        public FtGraphPath(List nodes, List edges) {
//            this.nodes = nodes;
//            this.edges = edges;
//        }
//
//        public List<Node> getVertexList() {
//            return nodes;
//        }
//
//        public List<Link> getEdgeList() {
//            return edges;
//        }
//
//        @Override
//        public Graph getGraph() {
//            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//        }
//
//        @Override
//        public Object getStartVertex() {
//            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//        }
//
//        @Override
//        public Object getEndVertex() {
//            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//        }
//
//        @Override
//        public double getWeight() {
//            return 0.0;
//        }
//
//    }

    private class FtPod { // a pod in a fat tree topology

        private final int k; // part of a k-ary fat tree
        private final int h;
        FtPod parent;
        
        List<FtNode> switches;
        List<FtPod> children;

        public FtPod(int k, int h, int podNum, int p) { // p = number of times partition is applied
            this(k, h, podNum, null, p);
        }

        public FtPod(int k, int h, int podNum, FtPod parent, int p) {
            this.k = k;
            this.h = h;
            this.parent = parent;

            if (k % 2 != 0) {
                // k must be even
                System.out.println("Pod creator: k has to be even");
                return;
            }

//            info("Creating pod " + podNum + "; k= " + k + "; h = " + h);
            switches = new ArrayList<>();
            children = new ArrayList<>();

            FtNode swtch;
            int switchId;
            int srDomainId = 0;

            for (int sw = 1; sw <= k; sw++) {

                // create the Aggr switch
                switchId = podNum * 100 + sw;
                srDomainId = computeSrDomainId(switchId, podNum, k, podNum);
                // podNum is also the switchId of the parent in case
                // of non-root pods, else it is zero
                //System.out.println("SR Domain ID for switch " + switchId + " is " + srDomainId);
                
                swtch = ftCreateNodeWithId(switchId, ((h == 1) ? Node.Type.TOR : Node.Type.AGGR), 
                        srDomainId, this);
                switches.add(swtch);
                ftNodes.put(switchId, swtch);

//                System.out.println("Created switch with id " + switchId + " at level " + h);
            }

            if (h == 1) { // we have reached the bottom of the tree
                return;
            }

            FtPod childPod;

            // else, create the children pods
            for (int sw = 1; sw <= k; sw++) {
                childPod = new FtPod(k / 2, h - 1, podNum * 100 + sw, this, p);
                children.add(childPod);
            }
        }

        private int computeSrDomainId(int switchId, int podNum, int podSize, 
                int parentSwitchId) {

//            int srDomainId;
//            FtNode parent;
//
//            // calculate the SR domain ID
//            srDomainId = 0; // default
//
//            if (podNum != 0) {
//                parent = ftNodes.get(parentSwitchId);
//                srDomainId = parent.getSrDomainId();
//
//            } else {
//                if (p == 0) {
//                    //default p=0
//                    srDomainId = 0;
//                } else {
//
//                    if (p == 1) {
//                        // p = 1
//                        srDomainId = (switchId <= podSize / 2) ? 1 : 2;
//                    } else if (p == 2) {
//                        // p = 2
//                        if (switchId <= podSize / 4) {
//                            srDomainId = 1;
//                        } else if (switchId <= podSize / 2) {
//                            srDomainId = 2;
//                        } else if (switchId <= podSize * 3 / 4) {
//                            srDomainId = 3;
//                        } else {
//                            srDomainId = 4;
//                        }
//                    } else if (p == 3) {
//                        // p = 3
//                        if (switchId <= podSize / 8) {
//                            srDomainId = 1;
//                        } else if (switchId <= podSize / 4) {
//                            srDomainId = 2;
//                        } else if (switchId <= podSize * 3 / 8) {
//                            srDomainId = 3;
//                        } else if (switchId <= podSize / 2) {
//                            srDomainId = 4;
//                        } else if (switchId <= podSize * 5 / 8) {
//                            srDomainId = 5;
//                        } else if (switchId <= podSize * 3 / 4) {
//                            srDomainId = 6;
//                        } else if (switchId <= podSize * 7 / 8) {
//                            srDomainId = 7;
//                        } else {
//                            srDomainId = 8;
//                        }
//                    }
//                }
//            }

            return 0; // default - domain partitioning is not meaningful for FT topo
        }

        public boolean isRootPod() {
            return (parent == null);
        }

        public int getK() {
            return k;
        }

        public int getH() {
            return h;
        }

        public void addEdges() {

            Iterator swItr, parentItr, childItr;
            FtNode swtch, parentSw;
            FtPod child;

            // every switch within the pod is connected to k parent switches
            swItr = switches.iterator();
            while ((parent != null) && swItr.hasNext()) {
                swtch = (FtNode) swItr.next();
//                boolean isEvenSw = (swtch.getNodeId() % 2 == 0);

                parentItr = parent.switches.iterator();
                while (parentItr.hasNext()) {
                    parentSw = (FtNode) parentItr.next();
//                    boolean isParentEvenSw = (parentSw.getNodeId() % 2 == 0);

//                    if (isEvenSw != isParentEvenSw) {
                    net.addEdge(parentSw.getNode(), swtch.getNode());
                    numLinks++;
//                    }
                }

            }

            childItr = children.iterator();
            while (childItr.hasNext()) {
                child = (FtPod) childItr.next();

                child.addEdges();
            }
        }

        public FtNode getRandomNode() {
            return switches.get((int) (Math.random() * k));
        }

        public FtNode getRandomLeafNode() {
            FtPod randomChild;

            if (h > 1) {
                randomChild = children.get((int) (Math.random() * k));

                return randomChild.getRandomLeafNode();
            }

            // else we have reached a leaf pod- return a random switch
            return switches.get((int) (Math.random() * k));
        }

    }

    protected FtNode getRandomFtNode() {
        return getRandomFtNode(null, true);
    }

    protected FtNode getRandomFtNode(FtNode excludedNode, boolean isRootPodAllowed) {
        Object[] allNodes;
        FtNode randomNode;
        int numNodes = ftNodes.size();
        int index;

        allNodes = (ftNodes.values().toArray());

        index = (int) (Math.random() * numNodes);
        randomNode = (FtNode) allNodes[index];

        while (randomNode.equals(excludedNode)
                || (randomNode.getPod().isRootPod() && !isRootPodAllowed)) {
            Network.info("Trying again");
            // try again
            index = (int) (Math.random() * numNodes);
            randomNode = (FtNode) allNodes[index];
        }

        return randomNode;
    }

    private void ftCreateFlowBetweenRandomNodes(int bitRate) {

        FtNode srcNode = getRandomFtNode();

        boolean isRootPodAllowed = !srcNode.getPod().isRootPod();

        FtNode dstNode = getRandomFtNode(srcNode, isRootPodAllowed);

        GraphPath path = route(srcNode, dstNode);

        net.createFlowBetweenNodes(srcNode.getNode(), dstNode.getNode(), bitRate, path);

    }

    private void createFtTopology(int k, int h, int p) { // build a k-ary fat-tree topology of height h 

        Map<Integer, FtPod> pods;

        pods = new HashMap<>();
        ftNodes = new HashMap<>();

        FtPod pod;

        pod = new FtPod(k, h, 0, p);

        net.createDsp();
        // net.createKsp();

        System.out.println("Number of entities in Sim_system = "
                + Sim_system.get_num_entities());

        numLinks = 0;

        pod.addEdges();

        System.out.println("Added " + net.nodes.size() + " nodes and "
                + numLinks + " links ");
        System.out.println("Testing for " + numFlows + " flows");

        int flowCount = 0;

        // System.out.println("Adding " + numFlows + " INITIAL flows");

        for (int flowNum = 0; flowNum < numFlows; flowNum++) {

            ftCreateFlowBetweenRandomNodes(segfrp.NetworkTest.DEFAULT_FLOW_RATE);

        }

    }

    // code to add additional flows in the network. This is for
    // testing the dynamic case. 
    protected void addAdditionalFlows(int numAddnlFlows) {

        for (int flowNum = 0; flowNum < numAddnlFlows; flowNum++) {
            ftCreateFlowBetweenRandomNodes(NetworkTest.DEFAULT_FLOW_RATE);
        }

    }
}
