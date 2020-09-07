/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.Stack;

/**
 *
 * @author anix
 */
public class Traffic {
    
    static int pktId = 1;
    //static int maxStackSize = 0;
    
    static int[] maxStackSize = {0, 0, 0, 0};
    
    protected static void routePacket(Flow flow, int method) {
        
        Stack labelStack;
        
//        System.out.println("Routing a packet from " + flow.getSrc().toString() +
//                " to " + flow.getDst().toString() + " method " + method);
        
        Packet pkt = new Packet(pktId++, flow.getSrc(), flow.getDst());
        labelStack = flow.getLabelStack(method);
        if(labelStack.isEmpty()) {
            System.out.println("Empty label stack for flow " + flow.getFlowId());
        }
        pkt.setLabelStack(labelStack);
        
        int stackSize = labelStack.size();
        if(stackSize > maxStackSize[method]) {
            maxStackSize[method] = stackSize;
        }
        
        flow.getSrc().routePacket(pkt, method);
    }
    
    protected static int getMaxStackSize(int method) {
        return maxStackSize[method];
    }
    
}
