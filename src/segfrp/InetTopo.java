/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.Map;
import org.jgrapht.io.Attribute;
import org.jgrapht.io.EdgeProvider;
import org.jgrapht.io.GraphImporter;
import org.jgrapht.io.GraphMLImporter;
import org.jgrapht.io.VertexProvider;

/**
 *
 * @author anix
 */
public class InetTopo {

    FileReader topoReader;   // reader for topology graph

    String demandFileName;
    FileReader demandReader; // reader for demand matrix (src/dst pairs of flows)
    BufferedReader demandBr; // buffered reader for flows

    PrintWriter demandWriter;

    int numFlows;
    Network net;
    NetworkTest netTest;
//    int numNodes;
//    int topoIndex; // index of the topology file to read
    boolean readFlowsFromFile;
    
    GraphImporter<Node, Link> importer;

    private class NodeProvider implements VertexProvider<Node> {

        @Override
        public Node buildVertex(String id, Map<String,Attribute> attributes) {
            Attribute nodeIdAttr = attributes.get("id");
            int nodeId = Integer.parseInt(nodeIdAttr.getValue());
//            System.out.println("InetTopo: creating node " + nodeId);
            return net.createNodeWithId(nodeId, Node.Type.OTHER);
        }

    }
    
    private class LinkProvider implements EdgeProvider<Node,Link> {

        @Override
        public Link buildEdge(Node from, Node to, String label, Map<String,Attribute> attributes) {
            return net.addEdge(from, to);
        }

    }

    // topoArg2 is node used
    public InetTopo(Network net, NetworkTest netTest, int p, int numFlows) {

        this.net = net;
        this.netTest = netTest;
        this.numFlows = numFlows;
//        this.numNodes = numNodes;
//        this.topoIndex = topoArg2;
        this.readFlowsFromFile = false;

        createInetTopology(p);

    }

    private void createInetTopology(int p) {

        String topoName = "Kdl";
        String topoFileName = "IN/" + topoName + ".graphml";

        try {
            System.out.println("Reading Inet topology from " + topoFileName);
            topoReader = new FileReader(topoFileName);
        } catch (IOException e) {
            System.out.println("InetTopo: Unable to open topo file for reading");
            return;
        }

        demandFileName = "IN/Flows/Flows" + "_" + topoName + "_" + numFlows + "F.dat";
        File demandFile = new File(demandFileName);

        if (demandFile.exists()) {
            System.out.println("Reading flows from file " + demandFileName);
            readFlowsFromFile = true;
            try {
                demandReader = new FileReader(demandFileName);
                demandBr = new BufferedReader(demandReader);
            } catch (IOException e) {
                System.out.println("InetTopo: Unable to open demand file for reading");
                return;
            }
        } else {
            System.out.println("Creating flows dynamically; output file " + demandFileName);

            readFlowsFromFile = false;
            demandWriter = createDemandWriter();
        }
        
        importer = new GraphMLImporter<>(new NodeProvider(), new LinkProvider());
        
        importer.importGraph(net.graph, topoReader);

//        // Read the topology first
//        BufferedReader br = new BufferedReader(topoReader);  //creates a buffering character input stream  
//        String line;
//        String[] content, nodesLine;
//        int nodeId, x, y, n1, n2, weight;
//        Node swtch, node1, node2;
//
//        try {
//            // read off the first two lead lines
//            line = br.readLine();
//            line = br.readLine();
//
//            nodesLine = line.split("\\s+");
//            numFileNodes = Integer.parseInt(nodesLine[0]);
//
//            if (numFileNodes < numNodes) {
//                System.out.println("Topo file has less nodes that required nodes");
//                return;
//            }
//
//            // first parse the nodes
//            while (nodesRead < numFileNodes) {
//                line = br.readLine();
//                // System.out.println("Read line " + line + " from file");
//                content = line.split("\\s+");
//                nodeId = Integer.parseInt(content[0]) + 1; // avoid index 0
//                x = Integer.parseInt(content[1]);
//                y = Integer.parseInt(content[2]);
//
//                nodesRead++;
//
//                if (nodesRead > numNodes) {
//                    continue;
//                }
//
//                // System.out.println("Creating Node " + nodeId + " (x,y) = (" + x + ", " + y + ")");
//                // offset x and y coordinates by 50 so that they don't hug the borders of the frame
//                swtch = net.createNodeWithId(nodeId, Node.Type.OTHER, 0, x + 50, y + 50);
//                nodeCount++;
//            }
//
//            System.out.println("Created " + nodeCount + " nodes");
//
//            while ((line = br.readLine()) != null) {
//
//                // System.out.println("Read line " + line + " from file");
//                content = line.split("\\s+");
//
//                n1 = Integer.parseInt(content[0]) + 1;
//                n2 = Integer.parseInt(content[1]) + 1;
//
//                weight = Integer.parseInt(content[2]);
//                // System.out.println("Creating Link between N" + n1 + " and N" + n2 + " with weight= " + weight);
//
//                node1 = net.getNodeByNodeId(n1);
//                node2 = net.getNodeByNodeId(n2);
//
//                if (node1 == null || node2 == null) {
//                    System.out.println("Unknow src or dst for link");
//                    continue;
//                }
//
//                linkCount++;
//                net.addEdge(node1, node2);
//
//            }
//        } catch (IOException e) {
//            System.out.println("IO Exception while reading topo file");
//        }

//        System.out.println("Created " + net.graph.edgeSet().size() + " links");
    }

    private PrintWriter createDemandWriter() {

        String dir = "IN/Flows/";

        File directory = new File(String.valueOf(dir));

        if (!directory.exists()) {
            System.out.println("Creating IN/Flows/ directory " + dir);
            directory.mkdirs();
            if (!directory.exists()) {
                System.out.println("InetTopo- Directory did not get created");
            }
        }

        try {
            demandWriter = new PrintWriter(demandFileName, "ASCII");
        } catch (IOException e) {
            System.out.println("Unable to open dump file for writing");
            return null;
        }

        return demandWriter;
    }

    protected void createInitialFlows() {

        String line;
        String[] content;
        int srcNodeId = 0, dstNodeId = 0;
        Node srcNode, dstNode;
        Flow flow;

        // System.out.println("Adding " + numFlows + " INITIAL flows");
        int flowsCreated = 0;
        while (flowsCreated < numFlows) {

            if (readFlowsFromFile) {
                try {
                    line = demandBr.readLine();
                    content = line.split("\\s+");

                    srcNodeId = Integer.parseInt(content[0]);
                    dstNodeId = Integer.parseInt(content[1]);

                } catch (IOException e) {
                    System.out.println("IO Exception while reading from demand file");
                }

                srcNode = net.getNodeByNodeId(srcNodeId);
                dstNode = net.getNodeByNodeId(dstNodeId);
//                System.out.println("Creating flow between " + srcNodeId
//                + " and " + dstNodeId + " from file");
                flow = net.createFlowBetweenNodes(srcNode, dstNode, NetworkTest.DEFAULT_FLOW_RATE);
                if(flow != null)
                    flowsCreated++;

            } else {
                flow = createFlowBetweenRandomNodes(NetworkTest.DEFAULT_FLOW_RATE);
                if(flow != null)
                    flowsCreated++;
            }

        }

        try {
            if (readFlowsFromFile) {
                demandBr.close();
            } else {
                demandWriter.close();
                System.out.println("Successfully closed demand writer");
            }
        } catch (IOException e) {
            System.out.println("IO Exception while closing reader or writer");
        }
        
        System.out.println("createInitialFlows: Inet topo: created " + flowsCreated + 
                " flows");
                
    }

    private Node findRandomValidEndNode(Node avoidNode) {
        Node node = null;
        int degree;
        int numNodes = net.getNodes().values().size();

        while (node == null) {
            int nodeIx = (int) (Math.random() * (numNodes - 1));
            
            node = net.getNodeList().get(nodeIx);
            if(node.equals(avoidNode)) {
                
                // make sure that it is not the node to be avoided
                node = null;
                continue;
            }
            
            degree = net.graph.degreeOf(node);
            // find a node with degree at least two.
            if (degree < 2) {
                node = null;
            }
        }

        return node;

    }

    private Flow createFlowBetweenRandomNodes(int flowRate) {

        Node srcNode, dstNode;

        srcNode = findRandomValidEndNode(null);
        dstNode = findRandomValidEndNode(srcNode);

//        System.out.println("Creating random flow between " + srcNode.toString() + " and " + dstNode.toString());
        Flow flow = net.createFlowBetweenNodes(srcNode, dstNode, flowRate);

        if (!readFlowsFromFile && (flow !=null)) {
            demandWriter.println(srcNode.getNodeId() + " " + dstNode.getNodeId());
        }
        
        return flow;

    }

}
