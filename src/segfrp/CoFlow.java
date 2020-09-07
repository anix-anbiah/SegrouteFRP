/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

/**
 *
 * @author anix
 */
public class CoFlow {
    
    Flow flow;  
    Flow coFlow;
    int  hops; // number of hops in their shared sub-path

    public CoFlow(Flow flow, Flow coFlow) {
        this.flow = flow;
        this.coFlow = coFlow;
        
        hops = 0;
    }

    public Flow getFlow() {
        return flow;
    }

    public Flow getCoFlow() {
        return coFlow;
    }

    public int getHops() {
        return hops;
    }

    public void setHops(int hops) {
        this.hops = hops;
    }
    
    public void incrementHops() {
        hops++;
    }
    
}
