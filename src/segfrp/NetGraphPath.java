/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.Iterator;
import java.util.List;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

/**
 *
 * @author anix
 */
public class NetGraphPath implements GraphPath {

    private List<Node> nodes;
    private List<Link> edges;

    public NetGraphPath(List nodes, List edges) {
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
    
    public String toString() {
        String str = edges.size() + " HOP(S) :: " + "NODES: ";
        Iterator nodeItr = nodes.iterator();
        while(nodeItr.hasNext()) {
            Node nd = (Node) nodeItr.next();
            str += nd.toString();
            str += " ";
        }
        
        str += "EDGES: ";
        
        Iterator edgeItr = edges.iterator();
        while(edgeItr.hasNext()) {
            Link lnk = (Link) edgeItr.next();
            str += lnk.toString();
            str += " ";
        }
        
        return str;
    }
    
}
