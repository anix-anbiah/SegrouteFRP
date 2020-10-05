/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import eduni.simjava.Sim_system;
import static segfrp.Network.info;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.jgrapht.*;

/**
 *
 * @author anix
 */
public class NetworkTest {

    private int unitTestId = 8;

    private Network net;
    private String topo; // topology on which to test
    private int topoArg1;
    private int topoArg2;  // parameters for the topology- for e.g. DCN has k & h
    private int numFlows; // number of flows to test with
    private int p; // number of partitions to apply

    public static final int SE_HEURISTIC_B = 1;
    public static final int SE_HEURISTIC_C = 2;
    public static final int SE_HEURISTIC_N = 3;
    public static final int SE_HEURISTIC_HYBRID = 4;

    private int seHeuristicType;

    int vnfInstCount = 0;
    int vnfInstTor = 0;
    int vnfInstAggr = 0;
    int vnfInstCore = 0;
    int vnfInstOther = 0;

    protected static final int DEFAULT_FLOW_RATE = 100;

    public NetworkTest(Network net, String topo, int topoArg1, int topoArg2,
            int numFlows, int p, String heuristic) {
        this.net = net;
        this.topo = topo;
        this.topoArg1 = topoArg1;
        this.topoArg2 = topoArg2;
        this.numFlows = numFlows;
        this.p = p;

        setHeuristicType(heuristic);
    }

    public int getTopoArg1() {
        return topoArg1;
    }

    public int getTopoArg2() {
        return topoArg2;
    }

    public int getNumFlows() {
        return numFlows;
    }

    private void setHeuristicType(String heuristic) {

        switch (heuristic) {
            case "b":
            case "B":
                seHeuristicType = SE_HEURISTIC_B;
                return;
            case "n":
            case "N":
                seHeuristicType = SE_HEURISTIC_N;
                return;
            default:
                seHeuristicType = SE_HEURISTIC_C;
                return;
        }
    }

    // unit test for DFP
    protected void addTopoAndInitialFlows() {

        System.out.println("Testing network with " + topo + " topology "
                + "k = " + topoArg1 + "; h = " + topoArg2 + "; INITIAL flows = "
                + numFlows);

        if ("ft".equals(topo)) {

            FtTopo ftTopo = new FtTopo(net, this, topoArg1, topoArg2, p, numFlows);

            return;
        } else if ("ror".equals(topo)) {

            RoRTopo rorTopo = new RoRTopo(net, this, topoArg1, topoArg2, p);

            // topo has been added- create the SPTs
            net.createDsp();

            net.createKsp();

            rorTopo.createInitialFlows();
        } else if ("inet".equals(topo)) {
            System.out.println("Creating Inet Topo with " + topoArg1 + " nodes");
            InetTopo inetTopo = new InetTopo(net, this, topoArg1, topoArg2, p, numFlows);

            net.createDsp();

            net.createKsp();

            System.out.println("Inet Topo created with " + net.getNodes().values().size()
                    + " nodes " + net.graph.edgeSet().size() + " links");

            inetTopo.createInitialFlows();

            System.out.println("Inet Topo tested with " + net.getNodes().values().size()
                    + " nodes " + net.graph.edgeSet().size() + " links");

        } else if ("test".equals(topo)) {
            TestTopo tstTopo = new TestTopo(net, this);

            net.createDsp();
            net.createKsp();

            tstTopo.createFlows();
        } else if ("bcube".equals(topo)) {

            BCubeTopo bcube = new BCubeTopo(net, topoArg1, topoArg2, numFlows);

            System.out.println("Creating Dijkstra's SP");

            net.createDsp();

            System.out.println("Creating Bhandari Disjoint Path");

            net.createKsp();

            System.out.println("Done creating SP algorithms");

            bcube.createInitialFlows();

        }

        info("Network has been provisioned with topology and initial flows");
    }

    protected int getSEMetricType() {
        return seHeuristicType;
    }

    protected String getSEMetricTypeString() {

        switch (seHeuristicType) {
            case SE_HEURISTIC_B:
                return "HEURISTIC-B";
            case SE_HEURISTIC_C:
                return "HEURISTIC-C";
            case SE_HEURISTIC_N:
                return "HEURISTIC-N";
            case SE_HEURISTIC_HYBRID:
                return "HYBRID";
            default:
                return "UNKNOWN";
        }

    }

}
