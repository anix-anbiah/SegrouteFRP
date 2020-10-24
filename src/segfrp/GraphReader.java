/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

//import com.tinkerpop.blueprints.pgm.Edge;
//import com.tinkerpop.blueprints.pgm.Graph;
//import com.tinkerpop.blueprints.pgm.Vertex;
//import com.tinkerpop.blueprints.pgm.impls.tg.TinkerGraph;
//import com.tinkerpop.blueprints.pgm.util.io.graphml.GraphMLReader;
//import java.io.BufferedInputStream;
//import java.io.FileInputStream;
//import java.io.InputStream;
//import java.util.Iterator;
import org.jgrapht.io.SimpleGraphMLImporter;
import org.jgrapht.io.GraphMLImporter;
import org.jgrapht.io.GraphImporter;
import org.jgrapht.io.Attribute;

import org.jgrapht.*;
import org.jgrapht.io.VertexProvider;
import org.jgrapht.io.EdgeProvider;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Supplier;

import java.util.Map;

/**
 *
 * @author anix
 */
public class GraphReader {

    private static final String XML_FILE = "IN/Tinet.graphml";
    FileReader topoReader;
    Graph<Node, Link> graph;
    GraphImporter<Node, Link> importer;
    private int nodeId=1;
    private Network net;

    private class NodeProvider implements VertexProvider<Node> {

        public Node buildVertex(String id, Map<String,Attribute> attributes) {
            return new Node(net, graph, nodeId, "N"+nodeId++, 1000, Node.Type.OTHER);
        }

    }
    
    private class LinkProvider implements EdgeProvider<Node,Link> {

        public Link buildEdge(Node from, Node to, String label, Map<String,Attribute> attributes) {
            return new Link(from, to);
        }

    }

    public GraphReader(Network net) {
        this.importer = new GraphMLImporter<>(new NodeProvider(), new LinkProvider());
        this.net = net;
        try {
            System.out.println("Reading Graph topology from " + XML_FILE);
            topoReader = new FileReader(XML_FILE);
        } catch (IOException e) {
            System.out.println("GraphReader: Unable to open topo file for reading");
            return;
        }
    }

    public void importGraph(Graph<Node, Link> graph) {

        importer.importGraph(graph, topoReader);

    }
}
