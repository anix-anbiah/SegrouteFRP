/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import org.jgrapht.*;
import org.jgrapht.graph.*;

/**
 *
 * @author anix- factory for creating links
 */
public class LinkFactory {
   // public class LinkFactory implements EdgeFactory<Node, Link> {

    /**
     *
     * @param source
     * @param target
     * @return
     */
    public Link createEdge(Node source, Node target) {
        
        Link link = new Link(source, target);
        
        return link;
    }
}
