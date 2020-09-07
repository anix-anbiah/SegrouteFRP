/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import static segfrp.NetworkTest.DEFAULT_FLOW_RATE;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

/**
 *
 * @author anix
 */
public class BCubeTopo {

    private int k;
    private int n;
    private Network net;
    
    private int numFlows;

    BcNode[] bcHosts;
    BcNode[][] bcSwitches;
    Map<String, BcNode> bcHostsByIndex;

    private final int BCNODE_HOST_BASE = 1000 * 1000;
    private final int BCNODE_SWITCH_BASE = 2000 * 1000;

    public BCubeTopo(Network net, int k, int n, int numFlows) {
        this.net = net;
        this.k = k;
        this.n = n;
        
        this.numFlows = numFlows;

        int n_pow_k = (int) Math.pow(n, k);
        int n_pow_kplus1 = (int) Math.pow(n, (k + 1));
        int numLinks = 0;

        bcHosts = new BcNode[n_pow_kplus1];
        bcSwitches = new BcNode[k + 1][n_pow_k];

        bcHostsByIndex = new TreeMap<>();

        System.out.println("Creating BCube topology with k = " + k
                + " and n = " + n);

        // initialize the switches
        for (int level = 0; level <= k; level++) {
            for (int j = 0; j < n_pow_k; j++) {
                int nodeId = BCNODE_SWITCH_BASE + (level * 1000) + j;
//                System.out.println("Created switch with nodeId = " + nodeId);
                BcNode bcSwitch = new BcNode(nodeId, k);
                bcSwitches[level][j] = bcSwitch;

            }
        }

        // initialize the hosts (switches at the lowest level)
        for (int hostIndex = 0; hostIndex < n_pow_kplus1; hostIndex++) {

            int nodeId = BCNODE_HOST_BASE + hostIndex;
            BcNode bcHost = new BcNode(nodeId, k);

            bcHosts[hostIndex] = bcHost;

            Network.info("Creating host switch with nodeId = " + nodeId);
            for (int level = 0; level <= k; level++) {
                int swBlockSize = (int) Math.pow(n, level);
                int hostBlockSize = swBlockSize * n; // n^(level+1)

                int swBase = swBlockSize * (hostIndex / hostBlockSize);
                int swIndex = swBase + hostIndex % swBlockSize;

                int lvlIndex = (hostIndex / ((int) Math.pow(n, level))) % n;

                // create a link from hosts switch to switch at level 'level'
                BcNode bcSwitch = bcSwitches[level][swIndex];

                bcHost.setLevelIndex(level, lvlIndex);
                bcHost.setLevelSwitch(level, bcSwitch);

                Network.info("Adding link to level-" + level + " switch "
                        + bcSwitch.getNode().toString());
                net.addEdge(bcHosts[hostIndex].getNode(), bcSwitch.getNode());
                numLinks++;

            }

            bcHostsByIndex.put(bcHost.switchId(), bcHost);

            Network.info("Switch ID for " + bcHost.getNode().getNodeId() + " is " + bcHost.switchId());
        }

        System.out.println("Added " + net.nodes.size() + " nodes and "
                + numLinks + " links ");
    }

    private class BcNode {

        Node node;
        private final int nodeType;
        int nodeId;
        int[] lvlIndex;
        BcNode[] lvlSwitches;

        public BcNode(int nodeId, int k) {
            this.nodeId = nodeId;
            this.nodeType = Node.Type.OTHER;
            lvlIndex = new int[k + 1];
            lvlSwitches = new BcNode[k + 1];

            node = net.createNodeWithId(nodeId, nodeType);
        }

        public Node getNode() {
            return node;
        }

        private void setLevelIndex(int level, int index) {
            lvlIndex[level] = index;
        }

        private void setLevelSwitch(int level, BcNode bcNode) {
            lvlSwitches[level] = bcNode;
        }

        private BcNode getLevelSwitch(int level) {
            return lvlSwitches[level];
        }

        private String switchId() {
            String swId = "";
            for (int level = k; level >= 0; level--) {
                swId += lvlIndex[level];
            }

            return swId;
        }

        private BcNode getNextHop(int level, int index) {
            // get the next hop based on the level and index
//            System.out.println("Get next hop for " + switchId() +
//                    " level = " + level + " index = " + index);
            String swId = "";
            for (int lvl = k; lvl >= 0; lvl--) {
                if (lvl == level) {
                    swId += index;
                } else {
                    swId += lvlIndex[lvl];
                }
            }

//            System.out.println("Returning switch with Id " + swId);
            return bcHostsByIndex.get(swId);
        }

    }

    private class BcGraphPath<Node, Link> implements GraphPath {

        private List<Node> nodes;
        private List<Link> edges;

        public BcGraphPath(List nodes, List edges) {
            this.nodes = nodes;
            this.edges = edges;
        }

        public List<Node> getVertexList() {
            return nodes;
        }

        public List<Link> getEdgeList() {
            return edges;
        }

        @Override
        public Graph getGraph() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Object getStartVertex() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Object getEndVertex() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public double getWeight() {
            return 0.0;
        }

    }

    GraphPath route(BcNode src, BcNode dst) {

        List<Node> nodes = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        
        Network.info("BCube- computing path between " + src.getNode().toString() 
                + "(" + src.switchId() + ")" + " and " + dst.getNode().toString()
                + "(" + dst.switchId() + ")");

        BcNode currNode = src;
        for (int level = k; level >= 0; level--) {
            if (currNode.lvlIndex[level] == dst.lvlIndex[level]) {
                Network.info("Index matches at level " + level);
                continue;
            }
            
            Network.info(currNode.getNode().toString() + " " +
                    currNode.switchId());
            Network.info(currNode.getLevelSwitch(level).getNode().toString());

            // do a hop at this level
            nodes.add(currNode.getNode());
            nodes.add(currNode.getLevelSwitch(level).getNode());

            currNode = currNode.getNextHop(level, dst.lvlIndex[level]);

            if (currNode.equals(dst)) {
                break;
            }
        }
        
        Network.info(currNode.getNode().toString() + " " +
                currNode.switchId());

        nodes.add(currNode.getNode());

        Iterator nodeItr = nodes.iterator();
        Node prevNode = null;
        Node nextNode = null;
        Link nextLink;

        while (nodeItr.hasNext()) {
            nextNode = (Node) nodeItr.next();
            if (prevNode != null) {
                nextLink = net.graph.getEdge(prevNode, nextNode);
                Network.info("Adding link " + nextLink.toString());
                links.add(nextLink);
            }
            prevNode = nextNode;
        }

        return new BcGraphPath(nodes, links);
    }

    private BcNode getRandomNode(int exludedNodeId) {

        BcNode randomNode;

        do {
            randomNode = getRandomNode();
            if(exludedNodeId == randomNode.getNode().getNodeId()) {
                Network.info("Bcube get random node- Got excluded node - trying again");
            }
        } while (exludedNodeId == (randomNode.node.getNodeId()));

        return randomNode;

    }

    private BcNode getRandomNode() {
        int randomNodeIndex = (int) (Math.pow(n,(k + 1)) * Math.random());
        return bcHosts[randomNodeIndex];
    }

    private void bcubeCreateFlowBetweenRandomNodes(int bitRate) {
        
        BcNode src = getRandomNode();

        BcNode dst = getRandomNode(src.node.getNodeId());

        Network.info("Creating flow between " + src.node.toString() + " and "
                + dst.node.toString());

        GraphPath path = route(src, dst);
        
        net.createFlowBetweenNodes(src.getNode(), dst.getNode(), bitRate, path);

    }

    protected void createInitialFlows() {

        int flowCount = 0;

        for (int flowNum = 0; flowNum < numFlows; flowNum++) {

            bcubeCreateFlowBetweenRandomNodes(NetworkTest.DEFAULT_FLOW_RATE);

        }

    }
}
