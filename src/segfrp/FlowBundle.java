/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author anix
 */
public class FlowBundle {
    
    int bundleId; // ID of the bundle
    Map<Integer, FlowBundle> subBundles;

    public FlowBundle(Flow flow) {
        this.bundleId = flow.getFlowId();
        subBundles = new TreeMap<>();
    }
    
    public FlowBundle(FlowBundle b1, FlowBundle b2) {
        // merge two flow bundles and create new one
        this.bundleId = (b1.getBundleId() < b2.getBundleId()) ? b1.getBundleId() : b2.getBundleId();
        this.subBundles = new TreeMap<>();
        subBundles.put(b1.getBundleId(), b1);
        subBundles.put(b2.getBundleId(), b2);
    }

    public void addBundle(FlowBundle b) {
        return;
    }
            
    int getBundleId() {
        return this.bundleId;
    }
}
