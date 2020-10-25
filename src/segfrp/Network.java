/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.awt.Color;
import java.io.File;
import java.util.logging.Level;
import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.jgrapht.alg.shortestpath.*;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;
import java.util.TreeMap;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;

/**
 *
 * @author anix
 */
public class Network {

    String name;
    String topo;
    int numFlows;
//    String outputDir;

    Map<Integer, Node> nodes; // map of nodes indexed by node ID string
    List<Node> nodeList; // list of nodes sorted by flow count

    List<Link> allLinks;
    List<Link> linksInUse; // list of links in use by flows

    Map<Integer, SrDomain> srDomains;

    // segments in the network, each uniquely identified
    // by the list of edges in its primary path
    List<Segment> segments;

    // SRFRP - Making this DIRECTED graph
    // SimpleGraph<Node, Link> graph;
    Graph<Node, Link> graph;

    LinkFactory linkFactory;
    ShortestPathAlgorithm<Node, Link> dsp;
    KShortestPathAlgorithm<Node, Link> ksp;

    private NetworkGraph netGraph;

    Map<Integer, Flow> flows;

    Map<Integer, Integer> degreeNodes; // mass function for node degree

    Map<Integer, Integer> flowHops;
    Map<Integer, Integer> flowSeCounts;

    PrintWriter writer;

    double numRounds = 0.0;

    private final static Logger logger = Logger.getLogger(Network.class.getName());
    protected final static Level defaultLogLevel = Level.OFF;

    protected static final int DEFAULT_SR_DOMAIN = 0;

    protected static final int FTS_OPT_DOMAIN = 1; // domain partioning optimization method 
    protected static final int FTS_OPT_HEURISTIC = 2; // heuristic

    public static final int SBP_MAX_BACKUPS = 7;
    public static int sbpMaxBackup = 0;

    protected static final int OPSTATE_UNKNOWN = -1;
    protected static final int OPSTATE_UP = 1;
    protected static final int OPSTATE_DOWN = 2;
    protected static final int OPSTATE_BACKUP = 3; // operating using backup

    protected static final int EDGE_WEIGHT_MAX = 100000;

//    private final Sim_predicate triggerPred = new Sim_type_p(Message.Type.TRIGGER);
    public static int MAX_ROUNDS = 300;

    // interval (in rounds) between additional flow provisioning steps
    // this is applicable for the dynamic case
    public static final int ADDNL_FLOWS_INTERVAL = 50;

    // For the dynamic case, the interval between successive performance reports
    public static final int OPT_REPORT_INTERVAL = 10;

    // Flag to run dynamic provisioning of flows
    public static final int IS_DYNAMIC = 0;

    public static int KSHORTEST_K = 8;
    public static boolean KSHORTEST_ACTIVE = true;

    public static boolean NETGRAPH_ENABLED = false;

    private NetworkTest networkTest;
    public int unitTestId = 0;
    public int deficitCost = 0;

    private int maxDomainFwdTableSize;
    private double avgDomainFwdTableSize;
    private int totalDomainFwdEntries;

    private double avgHeuristicFwdTableSize;
    private int maxHeuristicFwdTableSize;
    private int totalHeuristicFwdEntries;

    protected int totalPktHops = 0;
    protected double avgPktHops;
    protected int maxPktHops = 0;

    private int maxFlowNodes = 0;
    private double avgFlowNodes = 0;
    private int totalFlowNodes = 0;

    protected int sfcLen;
    private boolean ecmp;

    int ftTopoK;
    int ftTopoH;

    int p; // number of partitions to apply
    int numfails; // number of failures to simulate
    int maxSLD; // max segment list depth allowed

    private long startTime;  // starting time of path computation (PC)
    private long endTime;   // end time of HC

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    // private boolean DERIVE_SLD_FROM_P = true;

//    public Network(String netName, String topo, int ftTopoK, int ftTopoH,
//            String outputDir, int numFlows, int startRound, int endRound,
//            int sfcLen, boolean sfcHetero, boolean ecmp) {
    public Network(String netName, String topo, int ftTopoK, int ftTopoH,
            int numFlows, boolean ecmp, int p, int numfails) {

        this.name = netName;
        this.topo = topo;
        this.numFlows = numFlows;
        this.ecmp = ecmp;

        this.ftTopoK = ftTopoK;
        this.ftTopoH = ftTopoH;

        this.p = p;

        // SRFRP - use p as maxSLD for all cases
//        if ("ror".equals(topo)) {
//            this.maxSLD = (p == 0) ? 1 : ((int) Math.pow(2, this.p) + 1);
//        } else {
//            this.maxSLD = p;
//        }
        this.maxSLD = p;
        this.numfails = numfails;

        nodes = new HashMap<>();
        nodeList = new ArrayList<>();
        allLinks = new ArrayList<>();
        linksInUse = new ArrayList<>();
        srDomains = new HashMap<>();
        flows = new HashMap<>();

        segments = new ArrayList<>();

        degreeNodes = new TreeMap<>();

        flowHops = new TreeMap<>();
        flowSeCounts = new TreeMap<>();

        if (NETGRAPH_ENABLED) {
            netGraph = new NetworkGraph("FTS Graph");

            netGraph.setSize(1000, 1000);
            netGraph.setVisible(true);
            netGraph.setBackground(Color.WHITE);
        }

        String outDir;

        outDir = "OUT/" + topo + "/" + p + "S/" + numfails + "X/" + numFlows + "F/";

        File directory = new File(String.valueOf(outDir));

        if (!directory.exists()) {
            System.out.println("Creating directory " + outDir);
            directory.mkdirs();
            if (!directory.exists()) {
                System.out.println("Directory did not get created");
            }
        } else {
            System.out.println("Directory already exists " + outDir);
        }

        try {
            writer = new PrintWriter(outDir
                    + this.name + ".dat", "ASCII");
        } catch (IOException e) {
            warning("Unable to open dump file for writing");
            return;
        }

        // create the default domain
        SrDomain defaultDomain = new SrDomain(DEFAULT_SR_DOMAIN);
        srDomains.put(DEFAULT_SR_DOMAIN, defaultDomain);

        linkFactory = new LinkFactory();

        // SRFRP- Making this Directed Graph
        graph = new DefaultDirectedWeightedGraph<>(Link.class);

        System.out.println("Created graph. Default weight = " + Graph.DEFAULT_EDGE_WEIGHT);

//        // EXPERIMENTAL CODE TO TEST Graph ML Reader
//        topoGraph = new DefaultDirectedWeightedGraph<>(Link.class);
//        GraphReader graphReader = new GraphReader(this);
//        
//        graphReader.importGraph(topoGraph);
//        
//        System.out.println("Able to import Graph ML Topology");
//        System.out.println(topoGraph.vertexSet().size() + " Nodes and " 
//                            + topoGraph.edgeSet().size() + " Edges");
        logger.setLevel(defaultLogLevel);

        System.out.println("Created network " + netName);

        String heuristic = "C";

        networkTest = new NetworkTest(this, topo, ftTopoK, ftTopoH, numFlows, p, heuristic);

        this.startTime = System.nanoTime();

        // System.out.println("Dumping all pairs path lengths");
        networkTest.addTopoAndInitialFlows();

        // dumpAllPairsPathLengths();
        // SRFRP - Avoiding segment endpoints and SE metrics for now
//        this.hcStartTime = System.nanoTime();
//
//        //computeAvgFlowNodes();
//        //computeMaxFlowNodes();
//        // System.out.println("Max Flow Nodes = " + getMaxFlowNodes());
//        // System.out.println("Flow Node Threshold = " + getFlowNodeThreshold());
//        System.out.println("SE Metric Type = " + networkTest.getSEMetricTypeString());
//        System.out.println("Calculating SE Metric and setting the segment end points for flows");
//
//        // dumpNodeSEMetric();
//        for (Flow f : flows.values()) {
//
//            if (maxSLD > 1) {
//                // check if the flow has crossed the SE Metric threshold of samples.
//                // A flow is considered to have crossed the SE metric sampling threshold
//                // if all the nodes (except src and dst) have crossed their threshold.
//
//                f.calculateSEMetric();
//
//                f.sortFlownodes();
//            }
//
//            f.setSegmentEndpoints();
//
//            addRoutes(f, f.getPath(), FTS_OPT_HEURISTIC);
//
//            // This can be disabled for simulation runs since it is only a sanity check
//            Traffic.routePacket(f, FTS_OPT_HEURISTIC);
//        }
//
//        this.hcEndTime = System.nanoTime();
//        int nodesCrossedSEMetricThreshold = 0;
//
//        for (Node n : nodes.values()) {
//            if (n.isCrossedSeMetricThreshold()) {
//                nodesCrossedSEMetricThreshold++;
//            }
//            System.out.println("Node " + n.getNodeId() + ". Num flow nodes = " + n.getNumFlowNodes() +
//                    ": Total SE Metric = " +
//                    n.getTotalSEMetric() + " Avg = " + (int) n.getAvgSEMetric() +
//                    " Count = " + n.getSEMetricCount() + 
//                    " Zero Count = " + n.getZeroSEMetricCount() +
//                    " MAX = " + n.getMaxSEMetric() + " Threshold :" +
//                    (n.isCrossedSeMetricThreshold() ? " CROSSED" : " NOT CROSSED"));
//        }
//        System.out.println(nodesCrossedSEMetricThreshold + " nodes and "
//                + flowsCrossedSEMetricThreshold + " flows crossed sample threshold for SE Metric");
        //    computeDegreeDistribution();
        //    dumpDegreeNodes();
        //    computeFlowHopDistribution();
        //    dumpFlowHops();
        // dumpSegmentEndpointNodes();
        // computeFlowSeCountDistribution();
        // dumpFlowSeCounts();
        // Collections.sort(nodeList, new NodeComparator());
        // dumpNodeSeCount();
        //fail a random link
        simulateLinkFailures();

        updateFlowOpstats();

        this.endTime = System.nanoTime();

        prettyPrint();

//        if (NETGRAPH_ENABLED) {
//            netGraph.repaint();
//
//            Scanner scanner = new Scanner(System.in);
//            System.out.println("Enter a character to exit");
//            scanner.nextLine();
//        }
        dumpInfo();
        writer.close();
    }

    protected int getSEMetricType() {
        return networkTest.getSEMetricType();
    }

    private void computeDegreeDistribution() {

        // compute the mass function for the distribution of degrees
        for (Node n : nodes.values()) {
            int degree = n.getPorts().values().size() - 1; // exclude the host port
            Integer degreeNode = degreeNodes.get(degree);
            if (degreeNode != null) {
                degreeNodes.put(degree, degreeNode.intValue() + 1);
            } else {
                degreeNodes.put(degree, new Integer(1));
            }
        }

    }

    private void computeFlowHopDistribution() {

        for (Flow f : flows.values()) {
            int hops = f.getPath().getLength();
            Integer hopsCount = flowHops.get(hops);
            if (hopsCount != null) {
                flowHops.put(hops, hopsCount.intValue() + 1);
            } else {
                flowHops.put(hops, new Integer(1));
            }
        }
    }

    private void computeFlowSeCountDistribution() {

        for (Flow f : flows.values()) {
            int seCount = f.getSeCount();
            Integer seCountEntry = flowSeCounts.get(seCount);
            if (seCountEntry != null) {
                flowSeCounts.put(seCount, seCountEntry.intValue() + 1);
            } else {
                flowSeCounts.put(seCount, new Integer(1));
            }
        }
    }

    private class NodeComparator implements Comparator<Node> {

        @Override
        public int compare(Node a, Node b) {
            // descending order of number of co-flow hops
            return b.getSeCount() - a.getSeCount();
        }
    }

    public Map<Integer, Node> getNodes() {
        return nodes;
    }

    // return the sorted list of nodes 
    public List<Node> getNodeList() {
        return nodeList;
    }

    public Graph<Node, Link> getGraph() {
        return graph;
    }

    // SEGMENT related methods
    public Segment getSegment(int id) {
        return segments.get(id);
    }

    // check if segment exists with given type and primary path
    // returns segment if found, null otherwise.
    public Segment findSegment(int type, GraphPath primary) {

        Segment existingSeg;
        // create a segment shell to search with
        Segment seg = new Segment(type, this, null, null, primary);

        if (segments.contains(seg)) {
//            existingSeg = segments.get(segments.indexOf(seg));
//            System.out.println("find Segment: found segment. Existing Segment: ");
//            existingSeg.prettyPrint();
//            System.out.println("--------------------------------------------------- ");
//            System.out.println("New segment");
//            seg.prettyPrint();
//            System.out.println("=================================================== ");
            return segments.get(segments.indexOf(seg));
        }

        return null;
    }

    public Segment createSegment(int type, GraphPath primary) {

        Segment seg = new Segment(type, this,
                (Node) primary.getVertexList().get(0),
                (Node) primary.getVertexList().get(primary.getVertexList().size() - 1),
                primary);

        if (segments.contains(seg)) {
            System.out.println("Create Segment: Duplicate!");
            return null;
        }

        int segId = segments.size() + 1;

        seg.setId(segId);

        segments.add(seg);

//        System.out.println("Created Segment with ID " + segId);
        return seg;
    }

    // set the operational state of a link
    public void setLinkOpstate(Link link, int opstate) {

        if (link.getOpstate() == opstate) {
            //nothing to do
            return;
        }

        link.setOpstate(opstate);

        if (opstate == Network.OPSTATE_DOWN) {
            // this link is being taken down
            // set the operational state of all segments
            // that use this link as 'DOWN'
            List<GraphPath<Node, Link>> backups;
            GraphPath<Node, Link> backup;
            int index;

            for (Segment seg : segments) {
                if (seg.getPrimary().getEdgeList().contains(link)) {
                    seg.setPrimaryOpstate(Network.OPSTATE_DOWN);
                }

                backups = seg.getAllBackups();
                for (index = 0; index < backups.size(); index++) {
                    backup = backups.get(index);
                    if (backup.getEdgeList().contains(link)) {
                        seg.setBackupOpstate(index, Network.OPSTATE_DOWN);
                    }
                }
            }
        } else {
            // Link state is UP - TBD
        }
    }

    private void simulateLinkFailures() {

        Link lnk;
//        Collections.sort(linksInUse, new LinkComparator());

        System.out.println("Failing " + numfails + " links");

        int range;
        for (int i = 0; i < numfails; i++) {

            //range = (numfails * 2 + i) < linksInUse.size() ? (numfails * 2 + i) : linksInUse.size();
            range = linksInUse.size();
            failRandomLink(range);
        }
    }

    protected void createDsp() {
//        if ((numFlows >= 10000) && "inet".equals(topo)) {
//            System.out.println("Creating Johnson SP");
//            dsp = new JohnsonShortestPaths(graph);
//        } else {
//            System.out.println("Creating Dijkstra SP");
//            dsp = new DijkstraShortestPath(graph);
//        }

        System.out.println("Creating Dijkstra SP");
        dsp = new DijkstraShortestPath(graph);
    }

    protected ShortestPathAlgorithm getDsp() {
        return dsp;
    }

    protected void createKsp() {
        ksp = new BhandariKDisjointShortestPaths(graph);
    }

    private void dumpSegmentEndpointNodes() {

        System.out.println("===============================");
        System.out.println("Dumping segment endpoint nodes");

        int count = 0;
        for (Node n : nodes.values()) {
            if (n.isSegmentEndpoint()) {
                System.out.println(n.toString());
                count++;
            }
        }

        System.out.println("Number of segment endpoints is " + count);
    }

    private void dump(String str) {
        writer.print(str);
    }

    private void dumpln(String str) {
        writer.println(str);
    }

    private void dumpNodeSEMetric() {
        // DISABLED for now
        return;
        // System.out.println("Dumping node level metrics");
//        for (Node n : nodes.values()) {
//            //System.out.println(n.toString() + " " + n.getNodeSEMetric() + " " + n.getNodeSEMetricStr());
//        }
    }

    private void dumpNodeSeCount() {
        int nodeCount = 0;
        System.out.println("=======================================");

        System.out.println("Dumping SE count of TOP 20 nodes");
        for (Node n : nodeList) {
            if (++nodeCount > 20) {
                break;
            }

            System.out.println(n.getSeCount());
        }
        System.out.println("=======================================");
    }

    private void dumpDegreeNodes() {

        int degreeSum = 0;
        int cumulNodes = 0;
        System.out.println("Dumping distribution of degrees");
        for (Integer degree : degreeNodes.keySet()) {
            int numNodes = degreeNodes.get(degree);

            degreeSum += numNodes * degree;
            cumulNodes += numNodes;
            System.out.println(degree + ", " + numNodes + ", " + degreeSum);
        }

        System.out.println("Number of links = " + degreeSum / 2);
    }

    private void dumpAllPairsPathLengths() {
        GraphPath<Node, Link> gp;

        System.out.println("Dumping ALL PAIRS path lengths");

        for (Node src : nodes.values()) {
            for (Node dst : nodes.values()) {

                if (src.equals(dst)) {
                    continue;
                }

                gp = dsp.getPath(src, dst);
                int hops = gp.getLength();
                Integer hopsCount = flowHops.get(hops);
                if (hopsCount != null) {
                    flowHops.put(hops, hopsCount.intValue() + 1);
                } else {
                    flowHops.put(hops, new Integer(1));
                }

            }
        }

        dumpFlowHops();
    }

    private void dumpFlowHops() {

        int cumulNumFlows = 0, numFlows;

        System.out.println("");
        System.out.println("Dumping distribution of flow hops");
        for (Integer hops : flowHops.keySet()) {
            numFlows = flowHops.get(hops);
            cumulNumFlows += numFlows;

            System.out.println(hops + ", " + numFlows + ", " + cumulNumFlows);
        }

        System.out.println("Number of flows = " + cumulNumFlows);
    }

    private void dumpFlowSeCounts() {

        int cumulNumFlows = 0, numFlows;

        System.out.println("");
        System.out.println("Dumping SE counts of flows");
        for (Integer seCount : flowSeCounts.keySet()) {
            numFlows = flowSeCounts.get(seCount);
            cumulNumFlows += numFlows;

            System.out.println(seCount + "," + numFlows);
        }

        System.out.println("Number of flows = " + cumulNumFlows);
    }

    // dump out the network- including nodes, flows and VNFs
    // in order to feed as input to the IP solver
    private void dumpInfo() {

        String nodeString, linkString;
        Link l;
        dumpln("# FT Topology K = " + ftTopoK + "; H = " + ftTopoH);
        dumpln("# Num of Flows = " + numFlows);
        dumpln("# Num of nodes = " + nodes.size() + "; Num of Links = " + graph.edgeSet().size());
        dumpln("# Max SLD = " + maxSLD);

        // dump Flow Op stats
        dumpln("# OPSTAT Numflows " + numFlows + " :: NumFailures " + numfails
                + " :: MaxSLD " + maxSLD
                + " :: SBP_1 "
                + " :: Dropped " + numDroppedFlowsSBP_1
                + " :: PercentageDropped " + percentageDroppedFlowsSBP_1
                + " :: Backup " + numBackupFlowsSBP_1
                + " :: PercentageBackup " + percentageBackupFlowsSBP_1
                + " :: AvgPrimaryPathLen " + avgPrimaryPathLenSBP
                + " :: AvgBackupPathLen " + avgBackupPathLenSBP_1
                //                + " :: CumulativePrimaryPathLen " + cumulPrimaryPathLen
                //                + " :: CumulativeOpPathLen " + cumulOpPathLen
                + " :: PercentIncrease " + percentagePathLenIncreaseSBP
                + " :: AvgOpPrimaryPathLen " + avgOpPrimaryPathLenSBP_1
                + " :: AvgOpPathLen " + avgOpPathLenSBP_1
                + " :: PercentOpIncrease " + percentageOpPathLenIncreaseSBP_1
                + " :: SBP_3 "
                + " :: PercetageDropped " + percentageDroppedFlowsSBP_3
                + " :: PercentageOpIncrease " + percentageOpPathLenIncreaseSBP_3
                + " :: SBP_5 "
                + " :: PercetageDropped " + percentageDroppedFlowsSBP_5
                + " :: PercentageOpIncrease " + percentageOpPathLenIncreaseSBP_5
                + " :: SBP_7 "
                + " :: PercetageDropped " + percentageDroppedFlowsSBP_7
                + " :: PercentageOpIncrease " + percentageOpPathLenIncreaseSBP_7                
                + " :: TILFA "
                + " :: Dropped " + numDroppedFlowsTILFA
                + " :: PercentageDropped " + percentageDroppedFlowsTILFA
                + " :: AvgOpPrimaryPathLen " + avgOpPrimaryPathLenTILFA
                + " :: AvgOpPathLen " + avgOpPathLenTILFA
                + " :: PercentageOpIncrease " + percentageOpPathLenIncreaseTILFA
                + " :: TIMFA "
                + " :: Dropped " + numDroppedFlowsTIMFA
                + " :: PercentageDropped " + percentageDroppedFlowsTIMFA
                + " :: AvgOpPrimaryPathLen " + avgOpPrimaryPathLenTIMFA
                + " :: AvgOpPathLen " + avgOpPathLenTIMFA
                + " :: PercentageOpIncrease " + percentageOpPathLenIncreaseTIMFA);

        // Now, dump the link state
        dumpln("# LINKSTAT SBP " + sbpTopLinksStr + " TILFA " + tilfaTopLinksStr
                + " TIMFA " + timfaTopLinksStr);

//        dumpln("# Max FTS = " + maxDomainFwdTableSize
//                + " (Domain) :: " + maxHeuristicFwdTableSize + " (Heuristic); Avg "
//                + avgHeuristicFwdTableSize + " Total " + totalHeuristicFwdEntries + " :: Objective ");
//        dumpln("# Time Taken for Path Computation = " + (long) (hcStartTime - pcStartTime) / 1000000 + " msecs;"
//                + " Heuristic Calculation = " + (long) (hcEndTime - hcStartTime) / 1000000 + " msecs: "
//                + (double) ((int) ((hcEndTime - hcStartTime) * 10000 / (1000000 * numFlows))) / 10000.0 + " per flow");
//        dumpln("");
//        dumpln("");
//        dumpln("data;"); // enter AMPL data mode
//        dumpln("");
//
//        // Now dump the parameters
//        // first, dump the network parameter
//        dumpln("param sld := " + this.maxSLD + ";");
//        dumpln("");
//        if ("inet".equals(topo)) {
//            // For INET mesh topology, no need to dump the 
//            // stuff required to run AMPL
//            return;
//        }
//
//        if (numFlows > 1000) {
//            // For large number of flows, no need to dump
//            // the stuff required for AMPL
//            return;
//        }
//        dump("set NETWORK := " + name);
//        dumpln(";");
//        dumpln("");
        // next, dump out the nodes
//        dump("set NODES :=");
//        nodeString = "";
//        for (Node n : nodes.values()) {
//            nodeString += (" " + n.name);
//        }
//
//        dumpln(nodeString + ";");
//        dumpln("");
//
////        dump("set LINKS :=");
////        linkString = "";
////        Iterator linkItr = graph.edgeSet().iterator();
////        while (linkItr.hasNext()) {
////            l = (Link) linkItr.next();
////            linkString += (" (" + l.getFrom().name + ", " + l.getTo().name + ")");
////            linkString += (" (" + l.getTo().name + ", " + l.getFrom().name + ")");
////
////        }
////
////        dumpln(linkString + ";");
////        dumpln("");
////        dump("set FLOW_ENDPOINTS :=");
////        for (Flow f : flows.values()) {
////            dump(" (" + f.getSrc().name + ", " + f.getDst().name + ")");
////        }
////        dumpln(";");
////        dumpln("");
//        dump("set FLOWS :=");
//        for (Flow f : flows.values()) {
//            dump(" " + f.toString());
//        }
//        dumpln(";");
//        dumpln("");
//
//        dump("param path: " + nodeString + ":= ");
//        List<Node> flowNodeList;
//        int index;
//
//        for (Flow f : flows.values()) {
//
////            System.out.println("Getting node list for flow " + f.toString());
////            f.prettyPrint();
//            flowNodeList = f.getNodeList();
//
//            dumpln("");
//
//            dump(f.toString());
//
//            for (Node n : nodes.values()) {
//                index = flowNodeList.indexOf(n);
//                if (index == -1) {
//                    dump(" 0");
//                } else {
//                    dump(" " + (index + 1));
//                }
//            }
//        }
//        dumpln(";");
//        dumpln("");
//
////        // next, capacities of the nodes
////        dump("param capacity :=");
////        for (Node n : nodes.values()) {
////            dump(" " + n.name + " " + n.capacity);
////        }
////        dumpln(";");
////        dumpln("");
////        dump("param path: " + nodeString + ":= ");
////        List<Node> path;
////        int index;
////
////        for (Flow f : flows.values()) {
////            path = f.getPath().getVertexList();
////
////            dumpln("");
////
////            dump(f.toString());
////
////            for (Node n : nodes.values()) {
////                index = path.indexOf(n);
////                if (index == -1) {
////                    dump(" 0");
////                } else {
////                    dump(" " + (index + 1));
////                }
////            }
////        }
////        dumpln(";");
////        dumpln("");
////
////        dumpln(";");
////        dumpln("");
////
////        // finally, dump the rate of all the flows
////        dumpln("param rate " + ":= ");
////        for (Flow f : flows.values()) {
////            dump(" " + f.toString() + " " + f.getBitRate());
////        }
////        dumpln(";");
//        dumpln("");
//        dumpln("option solver gurobi;"); // Use Gurobi solver;
//        dumpln("solve;"); // Instruct AMPL to solve this file
//        dumpln("display sld;");
//        dumpln("display FLOW_ENDPOINTS;");
//        dumpln("display fte;");
//        dumpln("display flowlabel;");
    }

//    protected Node createNodeWithId(int nodeId) {
//        this.createNodeWithId(nodeId, DEFAULT_SR_DOMAIN);
//    }
//
//    protected Node createNodeWithId(int nodeId, int srDomainId) {
//        String nodeName = name + "-N" + nodeId;
//
//        Node n = new Node(this, graph, nodeId, nodeName, 1000, srDomainId);
//        nodes.put(nodeId, n);
//        graph.addVertex(n);
//
//        return n;
//    }
    protected Node createNodeWithId(int nodeId, int nodeType) {
        return createNodeWithId(nodeId, nodeType, DEFAULT_SR_DOMAIN, 100, 100);
    }

    protected Node createNodeWithId(int nodeId, int nodeType, int srDomainId, int x, int y) {
        String nodeName = "N" + nodeId;
        SrDomain domain;

        // see if the domain exists, if not, create it
        domain = srDomains.get(srDomainId);
        if (domain == null) {
            domain = new SrDomain(srDomainId);
            srDomains.put(srDomainId, domain);
        }

        Node n = new Node(this, graph, nodeId, nodeName, 1000, nodeType, srDomainId, x, y);
        nodes.put(nodeId, n);
        nodeList.add(nodeList.size(), n);
        graph.addVertex(n);

        if (NETGRAPH_ENABLED) {
            netGraph.addNode(nodeName, n.getX(), n.getY());
        }

        domain.addNode(n);

        return n;
    }

//    private class NodeComparator implements Comparator<Node> {
//
//        @Override
//        public int compare(Node a, Node b) {
//            // descending order of number of flow nodes
//            return b.getNumFlowNodes() - a.getNumFlowNodes();
//        }
//    }
    protected Node createNode() {
        int nodeId = nodes.size() + 1;

        return createNodeWithId(nodeId, Node.Type.OTHER);
    }

    protected Link addEdge(Node n1, Node n2) {
//        System.out.println("Creating link from " + n1.toString() + " to " + n2.toString());
        if (NETGRAPH_ENABLED) {
            netGraph.addEdge(n1.nodeId - 1, n2.nodeId - 1);
        }

        Link link = new Link(n1, n2);

        if (graph.addEdge(n1, n2, link) == false) {
            warning("Duplicate edge");
        }

//        System.out.println("Link added with weight " + graph.getEdgeWeight(link));
        allLinks.add(link);

        // SRFRP - Change for DIRECTED GRAPH
        Link revlink = new Link(n2, n1);

        if (graph.addEdge(n2, n1, revlink) == false) {
            warning("Duplicate edge");
        }

//        System.out.println("Rev Link added with weight " + graph.getEdgeWeight(revlink));
        allLinks.add(revlink);

        return link;
    }

    protected void removeEdge(Link link) {

    }

    protected Flow createFlowBetweenNodes(Node src, Node dst, int bitRate) {
        return createFlowBetweenNodes(src, dst, bitRate, null);
    }

    protected Flow createFlowBetweenNodes(Node src, Node dst, int bitRate, GraphPath path) {

        // shortest path needed for TI-LFA
        GraphPath<Node, Link> spath;

        // pair of disjoint paths needed for Segment-based protection (SBP)
        List<GraphPath<Node, Link>> sbpPaths = null;
        Stack labelStack = null;

        if (src.equals(dst)) {
            System.out.println("Cannot create flow when src,dst are equal");
            return null;
        }

        // compute the disjoint paths needed for SBP
        sbpPaths = getDisjointPaths(src, dst);

        if (sbpPaths.size() < 2) {
            System.out.println("Unable to find disjoint paths. Found " + sbpPaths.size() + " path(s)");
            return null;
        }

//        System.out.println("requesting path between " + src.getNodeName()
//                + " and " + dst.getNodeName());
        // Now get the shortest path needed for TI-LFA
        if (path == null) {
            spath = getShortestPath(src, dst);
        } else {
            spath = path;
        }

        int flowId = flows.size() + 1;

        //create the flow object
        Flow flow = new Flow(this, src, dst, bitRate, flows.size() + 1, spath, sbpPaths.get(0), sbpPaths.get(1));

        // segmentize the flow
        flow.segmentizeForSBP(this.maxSLD);

        flows.put(flowId, flow);

        // mark the links used by this flow to be 'in use'
        markLinksInUse(flow, spath);
        markLinksInUse(flow, sbpPaths.get(0));
        markLinksInUse(flow, sbpPaths.get(1));

//        System.out.println("Created Flow " + flowId);
//        flow.prettyPrint();
        if (!ecmp) {
            // DISABLING ADDROUTES AND ROUTE PACKET FOR FRSR

            // add the route (forwarding entries for this flow
//            addRoutes(flow, gp, FTS_OPT_DOMAIN);
            // System.out.println("Routing flow from " + flow.getSrc().toString() + " and " + flow.getDst().toString());
//            Traffic.routePacket(flow, FTS_OPT_DOMAIN);
        }
//        else {
//            ecmpPaths.stream().forEach((GraphPath<Node, Link> ecmpPath) -> {
////                System.out.println("Adding routes for ECMP path");
//                labelStack = addRoutes(flow, ecmpPath);
//            });
//        }

        return flow;
    }

    public void markLinksInUse(Flow flow, GraphPath<Node, Link> gp) {

        for (Link lnk : gp.getEdgeList()) {
            if (!linksInUse.contains(lnk)) {
                linksInUse.add(lnk);
            }

            // add the flow to the link
//            lnk.addFlow(flow);
        }
    }

    private class SbpLinkComparator implements Comparator<Link> {

        @Override
        public int compare(Link a, Link b) {
            // descending order of number of flows carried by the link
            return b.getSbpOpFlows() - a.getSbpOpFlows();
        }
    }

    private class TilfaLinkComparator implements Comparator<Link> {

        @Override
        public int compare(Link a, Link b) {
            // descending order of number of flows carried by the link
            return b.getTilfaOpFlows() - a.getTilfaOpFlows();
        }
    }

    private class TimfaLinkComparator implements Comparator<Link> {

        @Override
        public int compare(Link a, Link b) {
            // descending order of number of flows carried by the link
            return b.getTimfaOpFlows() - a.getTimfaOpFlows();
        }
    }

    public void failRandomLink(int range) {

//        System.out.println("Fail Random Link. Range = " + range);
        int index = (int) (Math.random() * range);
        Link lnk = linksInUse.get(index);

        while (lnk.getOpstate() == Network.OPSTATE_DOWN) {
//            System.out.println("failRandomLink: link is alredy down, finding another link; index = " + index
//                    + " Link = " + lnk.toString() + " Opstate = " + lnk.getOpstate());
            index = (int) (Math.random() * range);
            lnk = linksInUse.get(index);
        }

//        System.out.println("Failing random link " + lnk.toString()
//                + " with "
//                + lnk.getNumFlows() + " flows");
        setLinkOpstate(lnk, Network.OPSTATE_DOWN);
    }

    // add routes for domain partitioning method
    private void addRoutes(Flow flow, GraphPath gp, int method) {

        List<Link> pathLinks = gp.getEdgeList();
        List<Node> pathNodes = gp.getVertexList();

        // reverse the path links and nodes so that
        // we traverse the graph path in reverse
        // from destination to source
        Collections.reverse(pathLinks);
        Collections.reverse(pathNodes);

        // Now walk through the path and create the Fwd Entries.
        Iterator linkItr = pathLinks.iterator();
        Iterator nodeItr = pathNodes.iterator();

        int flowId = flow.getFlowId();

        Link prevl = null, nextl = null;
        Node n, prevn = null;
        Port ingressPort, egressPort;

        nextl = (Link) linkItr.next();

        // at the bottom of the label stack is the destination Node SID
        int label = flow.getDst().getNodeId();

        Stack labelStack = new Stack<>();
        labelStack.push(label);

        while (nodeItr.hasNext()) {

            n = (Node) nodeItr.next();

            if (prevl == null) { // this is the first node i.e. DST node since we're proceeding in reverse
                egressPort = n.getHostPort();
            } else {
                egressPort = prevl.getPortOnNode(n);
            }

            if (nextl == null) { // this is the last node (SRC node)
                ingressPort = n.getHostPort();
            } else {
                ingressPort = nextl.getPortOnNode(n);
            }

            if (prevl != null) { // this is NOT the dst node

                if (method == FTS_OPT_DOMAIN) {
                    // check if the prevl spans sr domain boundaries
                    // System.out.println("Processing link " + prevl.toString());

                    if (prevn.getSrDomainId() != n.getSrDomainId()) {
//                        System.out.println("addRoutes: Crossing SR Domain boundary at " + prevl.toString() +
//                               " from domain " + prevn.getSrDomainId() + " to " + n.getSrDomainId());

                        // push the adj SID so that when forwarding
                        // using this label stack, this node will
                        // forward on this adjacency
                        label = prevl.getPortOnNode(n).getAdjSID();
                        // System.out.println("addRoutes- pushing ADJ label " + Integer.toHexString(label) + " at node " + n.toString());
                        labelStack.push(label);

                        // push the Node SID to the stack so that previous
                        // nodes on the path will route to this domain border node
                        label = n.getNodeId();
                        labelStack.push(label);
                        // System.out.println("addRoutes- pushing label " + label + " at node " + n.toString());

                    } else {
                        // add a route for this flow on node
                        n.addRoute(label, egressPort, FTS_OPT_DOMAIN);
                        //System.out.println("addRoutes- pushing label " + label + " at node " + n.toString());

                    }
                } else {

                    n.addRoute(label, egressPort, FTS_OPT_HEURISTIC);

                    if (n.getFlowNodeByFlowId(flowId).isSegmentEndPoint()) {
                        label = n.getNodeId();
                        labelStack.push(label);
                    }
                }

            }

            if (method == FTS_OPT_DOMAIN) {
                // HACK alert!
                // Move this to a separate method to set flow nodes for all flows
                n.addFlowNode(flow, ingressPort, egressPort);
            }

//            info("createFlow: Processing " + prevl.toString() + " " + nextl.toString());
            prevl = nextl;
            prevn = n;

            if (linkItr.hasNext()) {
                nextl = (Link) linkItr.next();
            } else {
                nextl = null;
            }

        }

        // reverse the path links and nodes again to set them back to original order
        Collections.reverse(pathLinks);
        Collections.reverse(pathNodes);

        if (method == FTS_OPT_DOMAIN) {
            // set the label stacks of the flow
            flow.setLabelStack(labelStack, FTS_OPT_DOMAIN);
        } else {

            flow.setLabelStack(labelStack, FTS_OPT_HEURISTIC);
        }

    }

//    private List getEcmpPaths(Node src, Node dst) {
//
//        List<GraphPath<Node, Link>> kshortestpaths;
//        GraphPath<Node, Link> shortestPath, nextPath;
//        int shortestDistance, numShortestPaths;
//
//        if (src.equals(dst)) {
//            return null;
//        }
//
//        if (!ecmp) {
//            return null;
//        }
//
//        // KSHORTEST PATH is ACTIVE
//        kshortestpaths = ksp.getPaths(src, dst);
//
//        shortestPath = kshortestpaths.get(0);
//        shortestDistance = shortestPath.getLength();
//        numShortestPaths = 1;
//
//        for (int i = 1; i < kshortestpaths.size(); i++) {
//            nextPath = kshortestpaths.get(i);
//            if (nextPath.getLength() > shortestDistance) {
//                break;
//            }
//            numShortestPaths += 1;
//        }
//
//        return kshortestpaths.subList(0, numShortestPaths);
//    }
    GraphPath getShortestPath(Node src, Node dst) {

        if (src.equals(dst)) {
            return null;
        }

        return dsp.getPath(src, dst);

    }

    private List<GraphPath<Node, Link>> getDisjointPaths(Node src, Node dst) {

        List<GraphPath<Node, Link>> disjointpaths;

        if (src.equals(dst)) {
            return null;
        }

        // FIND 2 EDGE-DISJOINT PATH is ACTIVE
        disjointpaths = ksp.getPaths(src, dst, 2);

        if (disjointpaths.isEmpty()) {
            System.out.println("Empty disjoint paths");
        }

        return disjointpaths;
    }

    protected SrDomain getSrDomainById(int srDomainId) {
        return (SrDomain) srDomains.get(srDomainId);
    }

    protected Node getNodeByNodeId(int nodeId) {
        return (Node) nodes.get(nodeId);
    }

    protected Node getRandomNode() {
        return getRandomNode(null);
    }

    protected Node getRandomNode(Node excludedNode) {
        Object[] allNodes;
        Node randomNode;
        int numNodes = nodes.size();
        int index;

        allNodes = (nodes.values().toArray());

        index = (int) (Math.random() * numNodes);
        randomNode = (Node) allNodes[index];

        while (randomNode.equals(excludedNode)) {
            // try again
            index = (int) (Math.random() * numNodes);
            randomNode = (Node) allNodes[index];
        }

        return randomNode;
    }

    protected Flow getFlowByFlowId(int flowId) {
        return (Flow) flows.get(flowId);
    }

    protected Flow getFlowBySrcAndDst(Node src, Node dst) {
        for (Flow f : flows.values()) {
            if (f.getSrc().equals(src) && f.getDst().equals(dst)) {
                return f;
            }
        }
        return null;
    }

    protected List getFlowsByDst(Node dst) {
        List flowList = new ArrayList();
        for (Flow f : flows.values()) {
            if (f.getDst().equals(dst)) {
                flowList.add(f);
            }
        }

        return flowList;
    }

    public Logger getLogger() {
        return logger;
    }

    public static void info(String msg) {
        if (logger.isLoggable(Level.INFO)) {
            System.out.println(msg);
        }
    }

    public static void warning(String msg) {
        if (logger.isLoggable(Level.WARNING)) {
            System.out.println("WARNING: " + msg);
        }
    }

//    public static double clock() {
//        return Sim_system.clock();
//    }
    private void updateFwdEntryCount() {

        maxDomainFwdTableSize = 0;
        avgDomainFwdTableSize = 0.0;
        totalDomainFwdEntries = 0;

        avgHeuristicFwdTableSize = 0.0;
        maxHeuristicFwdTableSize = 0;
        totalHeuristicFwdEntries = 0;

        int domainFwdTableSize, heuristicFwdTableSize;

        for (Node n : nodes.values()) {
            domainFwdTableSize = n.getFwdTableSize(FTS_OPT_DOMAIN);
            if (domainFwdTableSize > maxDomainFwdTableSize) {
                maxDomainFwdTableSize = domainFwdTableSize;
            }
            totalDomainFwdEntries += domainFwdTableSize;

            heuristicFwdTableSize = n.getFwdTableSize(FTS_OPT_HEURISTIC);
            if (heuristicFwdTableSize > maxHeuristicFwdTableSize) {
                maxHeuristicFwdTableSize = heuristicFwdTableSize;
            }
            totalHeuristicFwdEntries += heuristicFwdTableSize;
        }

        avgDomainFwdTableSize = (totalDomainFwdEntries * 100 / nodes.size()) / 100.0;
        avgHeuristicFwdTableSize = (totalHeuristicFwdEntries * 100 / nodes.size()) / 100.0;

        avgPktHops = (totalPktHops * 100 / flows.size()) / 100.0;
    }

    protected void addFlowHops(int hops) {
        this.totalFlowNodes += hops;
    }

    private void computeMaxFlowNodes() {
        int maxFlows = 0;

        for (Node n : nodes.values()) {
            if (n.getNumFlows() > maxFlows) {
                maxFlows = n.getNumFlows();
            }
        }

        this.maxFlowNodes = maxFlows;
    }

    private int getMaxFlowNodes() {
        return this.maxFlowNodes;
    }

    private void computeAvgFlowNodes() {
        this.avgFlowNodes = (this.totalFlowNodes * 100 / nodes.size()) / 100.0;
    }

    protected double getAvgFlowNodes() {
        return this.avgFlowNodes;
    }

    protected double getFlowNodeThreshold() {
//        return (getAvgFlowNodes() + getMaxFlowNodes() * 3.0) /4.0;
        int numNodes = nodes.values().size() * 5 / 100;
        int nodeCount = 0;
        for (Node n : nodes.values()) {
            if (nodeCount++ < numNodes) {
                continue;
            } else {
                return n.getNumFlows();
            }
        }
        return 0.0;
    }

    // Dropped flow stats for SBP 1,3 & 5
    private int numDroppedFlowsSBP_1;
    private double percentageDroppedFlowsSBP_1;

    private int numDroppedFlowsSBP_3;
    private double percentageDroppedFlowsSBP_3;

    private int numDroppedFlowsSBP_5;
    private double percentageDroppedFlowsSBP_5;

    private int numDroppedFlowsSBP_7;
    private double percentageDroppedFlowsSBP_7;

    // Dropped stats for TI-LFA
    private int numDroppedFlowsTILFA;
    private double percentageDroppedFlowsTILFA;

    // Dropped stats for TI-MFA
    private int numDroppedFlowsTIMFA;
    private double percentageDroppedFlowsTIMFA;

    // Backup flow stats for SBP 1,3 & 5
    private int numBackupFlowsSBP_1;
    private double percentageBackupFlowsSBP_1;

    private int numBackupFlowsSBP_3;
    private double percentageBackupFlowsSBP_3;

    private int numBackupFlowsSBP_5;
    private double percentageBackupFlowsSBP_5;

    private int numBackupFlowsSBP_7;
    private double percentageBackupFlowsSBP_7;

    private int cumulPrimaryPathLenSBP;
    private int cumulBackupPathLenSBP;
    private int cumulShortestPathLen;

    private int cumulOpPrimaryPathLenSBP_1;
    private int cumulOpPrimaryPathLenSBP_3;
    private int cumulOpPrimaryPathLenSBP_5;
    private int cumulOpPrimaryPathLenSBP_7;

    private int cumulOpPrimaryPathLenTILFA;
    private int cumulOpPrimaryPathLenTIMFA;

    private int cumulOpPathLenSBP_1;
    private int cumulOpPathLenSBP_3;
    private int cumulOpPathLenSBP_5;
    private int cumulOpPathLenSBP_7;

    private int cumulOpPathLenTILFA;
    private int cumulOpPathLenTIMFA;

    private double avgPrimaryPathLenSBP;
    private double avgBackupPathLenSBP_1;

    private double avgShortestPathLen;

    private double percentagePathLenIncreaseSBP;

    private double avgOpPrimaryPathLenSBP_1;
    private double avgOpPrimaryPathLenSBP_3;
    private double avgOpPrimaryPathLenSBP_5;
    private double avgOpPrimaryPathLenSBP_7;

    private double avgOpPathLenSBP_1;
    private double avgOpPathLenSBP_3;
    private double avgOpPathLenSBP_5;
    private double avgOpPathLenSBP_7;

    private double percentageOpPathLenIncreaseSBP_1;
    private double percentageOpPathLenIncreaseSBP_3;
    private double percentageOpPathLenIncreaseSBP_5;
    private double percentageOpPathLenIncreaseSBP_7;

    private double avgOpPrimaryPathLenTILFA;
    private double avgOpPathLenTILFA;
    private double percentageOpPathLenIncreaseTILFA;

    private double avgOpPrimaryPathLenTIMFA;
    private double avgOpPathLenTIMFA;
    private double percentageOpPathLenIncreaseTIMFA;

    private int NUM_TOP_LINKS = 20;

    private int[] sbpTopLinks = new int[NUM_TOP_LINKS];
    private int[] tilfaTopLinks = new int[NUM_TOP_LINKS];
    private int[] timfaTopLinks = new int[NUM_TOP_LINKS];

    private String sbpTopLinksStr, tilfaTopLinksStr, timfaTopLinksStr;
    
    public void updateSbpMaxBackup(int maxbackup) {
        if(sbpMaxBackup < maxbackup) {
            sbpMaxBackup = maxbackup;
        }
    }

    // dump the operational stats for the flows
    public void updateFlowOpstats() {

        int numFlows = flows.size();
        numDroppedFlowsSBP_1 = 0; // number of flows with SBP operational state DOWN
        numDroppedFlowsSBP_3 = 0;
        numDroppedFlowsSBP_5 = 0;
        numDroppedFlowsSBP_7 = 0;

        numDroppedFlowsTILFA = 0; // same for TI-LFA
        numDroppedFlowsTIMFA = 0; // same for TI-MFA

        numBackupFlowsSBP_1 = 0; // number of flows in BACKUP state
        numBackupFlowsSBP_3 = 0;
        numBackupFlowsSBP_5 = 0;
        numBackupFlowsSBP_7 = 0;

        cumulPrimaryPathLenSBP = 0; // cumulative primary path length
        cumulBackupPathLenSBP = 0;
        cumulShortestPathLen = 0;

        cumulOpPrimaryPathLenSBP_1 = 0;
        cumulOpPrimaryPathLenSBP_3 = 0;
        cumulOpPrimaryPathLenSBP_5 = 0;
        cumulOpPrimaryPathLenSBP_7 = 0;

        cumulOpPathLenSBP_1 = 0; //cumulative operational path length
        cumulOpPathLenSBP_3 = 0;
        cumulOpPathLenSBP_5 = 0;
        cumulOpPathLenSBP_7 = 0;

        cumulOpPrimaryPathLenTILFA = 0;
        cumulOpPathLenTILFA = 0;

        cumulOpPrimaryPathLenTIMFA = 0;
        cumulOpPathLenTIMFA = 0;

        int sbpOpstate_1, sbpOpstate_3, sbpOpstate_5, sbpOpstate_7, tilfaOpstate, timfaOpstate;

        for (Flow flow : flows.values()) {

            cumulPrimaryPathLenSBP += flow.getSbpPrimaryPathLength();
            cumulBackupPathLenSBP += flow.getSbpBackupPathLength();
            cumulShortestPathLen += flow.getShortestPathLength();

            if (flow.getSbpPrimaryPathLength() != flow.getShortestPathLength()) {
//                System.out.println("SBP Primary longer than SP for " + flow.getFlowId());
            }

            sbpOpstate_1 = flow.getSbpOpstate(1);
            sbpOpstate_3 = flow.getSbpOpstate(3);
            sbpOpstate_5 = flow.getSbpOpstate(5);
            sbpOpstate_7 = flow.getSbpOpstate(7);

            tilfaOpstate = flow.getTilfaOpstate(false);
            timfaOpstate = flow.getTilfaOpstate(true);

            // STATS for SBP-1
            if (sbpOpstate_1 == Network.OPSTATE_DOWN) {
                numDroppedFlowsSBP_1++;
            } else {
                // cumulative path len stats apply only for
                // flows that are not dropped
                cumulOpPrimaryPathLenSBP_1 += flow.getSbpPrimaryPathLength();
                cumulOpPathLenSBP_1 += flow.getSbpOpPathLength(false);
            }

            if (sbpOpstate_1 == Network.OPSTATE_BACKUP) {
                numBackupFlowsSBP_1++;
            }

            // STATS for SBP-3
            if (sbpOpstate_3 == Network.OPSTATE_DOWN) {
                numDroppedFlowsSBP_3++;
            } else {
                // cumulative path len stats apply only for
                // flows that are not dropped
                cumulOpPrimaryPathLenSBP_3 += flow.getSbpPrimaryPathLength();
                cumulOpPathLenSBP_3 += flow.getSbpOpPathLength(false);
            }

            if (sbpOpstate_3 == Network.OPSTATE_BACKUP) {
                numBackupFlowsSBP_3++;
            }

            // STATS FOR SBP-5
            if (sbpOpstate_5 == Network.OPSTATE_DOWN) {
                numDroppedFlowsSBP_5++;
            } else {
                // cumulative path len stats apply only for
                // flows that are not dropped
                cumulOpPrimaryPathLenSBP_5 += flow.getSbpPrimaryPathLength();
                cumulOpPathLenSBP_5 += flow.getSbpOpPathLength(true);
            }

            if (sbpOpstate_5 == Network.OPSTATE_BACKUP) {
                numBackupFlowsSBP_5++;
            }

            // STATS FOR SBP-7
            if (sbpOpstate_7 == Network.OPSTATE_DOWN) {
                numDroppedFlowsSBP_7++;
            } else {
                // cumulative path len stats apply only for
                // flows that are not dropped
                cumulOpPrimaryPathLenSBP_7 += flow.getSbpPrimaryPathLength();
                cumulOpPathLenSBP_7 += flow.getSbpOpPathLength(false);
            }

            if (sbpOpstate_7 == Network.OPSTATE_BACKUP) {
                numBackupFlowsSBP_7++;
            }

            // STATS for TILFA
            if (tilfaOpstate == Network.OPSTATE_DOWN) {
                numDroppedFlowsTILFA++;
            } else {
                cumulOpPrimaryPathLenTILFA += flow.getTilfaPrimaryPathLength();
                cumulOpPathLenTILFA += flow.getTilfaOpPathLength();
            }

            // STATS for TIMFA
            if (timfaOpstate == Network.OPSTATE_DOWN) {
                numDroppedFlowsTIMFA++;
            } else {
                cumulOpPrimaryPathLenTIMFA += flow.getTimfaPrimaryPathLength();
                cumulOpPathLenTIMFA += flow.getTimfaOpPathLength();
            }
//            if ((sbpOpstate_1 == Network.OPSTATE_DOWN)
//                    && (tilfaOpstate == Network.OPSTATE_UP)) {
//                System.out.println("SBP is DOWN, but TI-LFA is UP");
//            }
//
//            if (((sbpOpstate_1 == Network.OPSTATE_UP) || (sbpOpstate_1 == Network.OPSTATE_BACKUP))
//                    && (tilfaOpstate == Network.OPSTATE_DOWN)) {
//                System.out.println("SBP is OPERATIONAL, but TI-LFA is DOWN for flow " + flow.getFlowId()); 
//                
//                flow.prettyPrint();
//            }
        }

        // update the link state- i.e. the top congested links for each scheme
        // First SBP
        Collections.sort(allLinks, new SbpLinkComparator());
        sbpTopLinksStr = "";
        Iterator sbpItr = allLinks.iterator();
        for (int i = 0; i < NUM_TOP_LINKS; i++) {
            sbpTopLinks[i] = ((Link) sbpItr.next()).getSbpOpFlows();
            sbpTopLinksStr += sbpTopLinks[i] + " ";
        }

        // Next TI-LFA
        Collections.sort(allLinks, new TilfaLinkComparator());
        tilfaTopLinksStr = "";
        Iterator tilfaItr = allLinks.iterator();
        for (int i = 0; i < NUM_TOP_LINKS; i++) {
            tilfaTopLinks[i] = ((Link) tilfaItr.next()).getTilfaOpFlows();
            tilfaTopLinksStr += tilfaTopLinks[i] + " ";
        }

        // Finally TI-MFA
        Collections.sort(allLinks, new TimfaLinkComparator());
        timfaTopLinksStr = "";
        Iterator timfaItr = allLinks.iterator();
        for (int i = 0; i < NUM_TOP_LINKS; i++) {
            timfaTopLinks[i] = ((Link) timfaItr.next()).getTimfaOpFlows();
            timfaTopLinksStr += timfaTopLinks[i] + " ";

        }

        System.out.println("FLOW STATS");
        System.out.println("============================");

        System.out.println("Total number of flows = " + flows.size());

        if (flows.size() == numDroppedFlowsSBP_1) {
            System.out.println("ALL FLOWS DROPPED!");
            return; //bail
        }

        System.out.println("Number of link failures = " + numfails);

        percentageBackupFlowsSBP_1 = Util.percentage(numBackupFlowsSBP_1, flows.size() - numDroppedFlowsSBP_1);
        percentageDroppedFlowsSBP_1 = Util.percentage(numDroppedFlowsSBP_1, flows.size());

        percentageBackupFlowsSBP_3 = Util.percentage(numBackupFlowsSBP_3, flows.size() - numDroppedFlowsSBP_3);
        percentageDroppedFlowsSBP_3 = Util.percentage(numDroppedFlowsSBP_3, flows.size());

        percentageBackupFlowsSBP_5 = Util.percentage(numBackupFlowsSBP_5, flows.size() - numDroppedFlowsSBP_5);
        percentageDroppedFlowsSBP_5 = Util.percentage(numDroppedFlowsSBP_5, flows.size());

        percentageBackupFlowsSBP_7 = Util.percentage(numBackupFlowsSBP_7, flows.size() - numDroppedFlowsSBP_7);
        percentageDroppedFlowsSBP_7 = Util.percentage(numDroppedFlowsSBP_7, flows.size());

        percentageDroppedFlowsTILFA = Util.percentage(numDroppedFlowsTILFA, flows.size());
        percentageDroppedFlowsTIMFA = Util.percentage(numDroppedFlowsTIMFA, flows.size());

        System.out.println("Total number of segments = " + segments.size());

        avgPrimaryPathLenSBP = Util.ratio(cumulPrimaryPathLenSBP, flows.size());
        avgBackupPathLenSBP_1 = Util.ratio(cumulBackupPathLenSBP, flows.size());
        avgShortestPathLen = Util.ratio(cumulShortestPathLen, flows.size());

        // OP Path Len STATS for SBP-1,3 & 5
        avgOpPrimaryPathLenSBP_1 = Util.ratio(cumulOpPrimaryPathLenSBP_1, flows.size() - numDroppedFlowsSBP_1);
        avgOpPathLenSBP_1 = Util.ratio(cumulOpPathLenSBP_1, flows.size() - numDroppedFlowsSBP_1);

        avgOpPrimaryPathLenSBP_3 = Util.ratio(cumulOpPrimaryPathLenSBP_3, flows.size() - numDroppedFlowsSBP_3);
        avgOpPathLenSBP_3 = Util.ratio(cumulOpPathLenSBP_3, flows.size() - numDroppedFlowsSBP_3);

        avgOpPrimaryPathLenSBP_5 = Util.ratio(cumulOpPrimaryPathLenSBP_5, flows.size() - numDroppedFlowsSBP_5);
        avgOpPathLenSBP_5 = Util.ratio(cumulOpPathLenSBP_5, flows.size() - numDroppedFlowsSBP_5);

        avgOpPrimaryPathLenSBP_7 = Util.ratio(cumulOpPrimaryPathLenSBP_7, flows.size() - numDroppedFlowsSBP_7);
        avgOpPathLenSBP_7 = Util.ratio(cumulOpPathLenSBP_7, flows.size() - numDroppedFlowsSBP_7);

        // OP Path Len STATS for TILFA & TIMFA
        avgOpPrimaryPathLenTILFA = Util.ratio(cumulOpPrimaryPathLenTILFA, flows.size() - numDroppedFlowsTILFA);
        avgOpPathLenTILFA = Util.ratio(cumulOpPathLenTILFA, flows.size() - numDroppedFlowsTILFA);

        avgOpPrimaryPathLenTIMFA = Util.ratio(cumulOpPrimaryPathLenTIMFA, flows.size() - numDroppedFlowsTIMFA);
        avgOpPathLenTIMFA = Util.ratio(cumulOpPathLenTIMFA, flows.size() - numDroppedFlowsTIMFA);

        // Op Path len increase STAT for SBP-1,3 & 5
        percentagePathLenIncreaseSBP = Util.percentage(cumulPrimaryPathLenSBP - cumulShortestPathLen, cumulShortestPathLen);
        percentageOpPathLenIncreaseSBP_1 = Util.percentage(cumulOpPathLenSBP_1 - cumulOpPrimaryPathLenSBP_1, cumulOpPrimaryPathLenSBP_1);
        percentageOpPathLenIncreaseSBP_3 = Util.percentage(cumulOpPathLenSBP_3 - cumulOpPrimaryPathLenSBP_3, cumulOpPrimaryPathLenSBP_3);
        percentageOpPathLenIncreaseSBP_5 = Util.percentage(cumulOpPathLenSBP_5 - cumulOpPrimaryPathLenSBP_5, cumulOpPrimaryPathLenSBP_5);
        percentageOpPathLenIncreaseSBP_7 = Util.percentage(cumulOpPathLenSBP_7 - cumulOpPrimaryPathLenSBP_7, cumulOpPrimaryPathLenSBP_7);

        // Op Path Len increase STAT for TILFA & TIMFA
        percentageOpPathLenIncreaseTILFA = Util.percentage(cumulOpPathLenTILFA - cumulOpPrimaryPathLenTILFA, cumulOpPrimaryPathLenTILFA);
        percentageOpPathLenIncreaseTIMFA = Util.percentage(cumulOpPathLenTIMFA - cumulOpPrimaryPathLenTIMFA, cumulOpPrimaryPathLenTIMFA);

        System.out.println("============ SBP Provisioned =================");
        System.out.println("Average primary path length = " + avgPrimaryPathLenSBP);
        System.out.println("Average backup path length = " + avgBackupPathLenSBP_1);
        System.out.println("Average shortest path length = " + avgShortestPathLen);
        System.out.println("Percentage increase (Provisioning Cost) = "
                + percentagePathLenIncreaseSBP + "%");
        System.out.println("Max backup paths = " + sbpMaxBackup);

//        System.out.println("Cumulative provisioned primary path length = " + cumulPrimaryPathLen);
//        System.out.println("Cumulative provisioned backup path length = " + cumulBackupPathLen);
//
//        System.out.println("Cumulative operational primary path length =" + cumulOpPrimaryPathLen);
//        System.out.println("Cumulative operational path length = " + cumulOpPathLen);
        System.out.println("============ SBP-1 Operational =================");
        System.out.println("Number of dropped flows SBP-1 " + numDroppedFlowsSBP_1 + " ("
                + percentageDroppedFlowsSBP_1 + "%)");
        System.out.println("Number of flows in BACKUP state " + numBackupFlowsSBP_1
                + " (" + percentageBackupFlowsSBP_1 + "%)");
        System.out.println("Average Op primary path length = " + avgOpPrimaryPathLenSBP_1);
        System.out.println("Average Op path length = " + avgOpPathLenSBP_1);
        System.out.println("Percentage Op increase = "
                + percentageOpPathLenIncreaseSBP_1 + "%");

        System.out.println("\n============ SBP-3 Operational =================");
        System.out.println("Number of dropped flows SBP-3 " + numDroppedFlowsSBP_3 + " ("
                + percentageDroppedFlowsSBP_3 + "%)");
        System.out.println("Number of flows in BACKUP state " + numBackupFlowsSBP_3
                + " (" + percentageBackupFlowsSBP_3 + "%)");
        System.out.println("Average Op primary path length = " + avgOpPrimaryPathLenSBP_3);
        System.out.println("Average Op path length = " + avgOpPathLenSBP_3);
        System.out.println("Percentage Op increase = "
                + percentageOpPathLenIncreaseSBP_3 + "%");

        System.out.println("\n============ SBP-5 Operational =================");
        System.out.println("Number of dropped flows SBP-5 " + numDroppedFlowsSBP_5 + " ("
                + percentageDroppedFlowsSBP_5 + "%)");
        System.out.println("Number of flows in BACKUP state " + numBackupFlowsSBP_5
                + " (" + percentageBackupFlowsSBP_5 + "%)");
        System.out.println("Average Op primary path length = " + avgOpPrimaryPathLenSBP_5);
        System.out.println("Average Op path length = " + avgOpPathLenSBP_5);
        System.out.println("Percentage Op increase = "
                + percentageOpPathLenIncreaseSBP_5 + "%");
        
        System.out.println("\n============ SBP-7 Operational =================");
        System.out.println("Number of dropped flows SBP-7 " + numDroppedFlowsSBP_7 + " ("
                + percentageDroppedFlowsSBP_7 + "%)");
        System.out.println("Number of flows in BACKUP state " + numBackupFlowsSBP_7
                + " (" + percentageBackupFlowsSBP_7 + "%)");
        System.out.println("Average Op primary path length = " + avgOpPrimaryPathLenSBP_7);
        System.out.println("Average Op path length = " + avgOpPathLenSBP_7);
        System.out.println("Percentage Op increase = "
                + percentageOpPathLenIncreaseSBP_7 + "%");        

        System.out.println("\n============ TILFA Operational ================");
        System.out.println("Number of dropped flows TILFA " + numDroppedFlowsTILFA + " ("
                + percentageDroppedFlowsTILFA + "%)");
        System.out.println("Average Op primary path length = " + avgOpPrimaryPathLenTILFA);
        System.out.println("Average Op path length = " + avgOpPathLenTILFA);
        System.out.println("Percentage Op increase = "
                + percentageOpPathLenIncreaseTILFA + "%");

        System.out.println("\n============ TIMFA Operational ================");
        System.out.println("Number of dropped flows TIMFA " + numDroppedFlowsTIMFA + " ("
                + percentageDroppedFlowsTIMFA + "%)");
        System.out.println("Average Op primary path length = " + avgOpPrimaryPathLenTIMFA);
        System.out.println("Average Op path length = " + avgOpPathLenTIMFA);
        System.out.println("Percentage Op increase = "
                + percentageOpPathLenIncreaseTIMFA + "%");

        System.out.println("\n============ Link Opstat ======================");
        System.out.println("Top congested links in SBP    " + sbpTopLinksStr);
        System.out.println("Top congested links in TI-LFA " + tilfaTopLinksStr);
        System.out.println("Top congested links in TI-MFA " + timfaTopLinksStr);

    }

    public final void prettyPrint() {

        updateFwdEntryCount();

        System.out.println("Topo " + topo + "(" + ftTopoK + "," + ftTopoH + "); "
                + "ECMP " + (ecmp ? "Enabled" : "Disabled"));
        System.out.println("Max SLD = " + this.maxSLD);
        System.out.print("SR Domains ");

        srDomains.values().stream().forEach((domain) -> {
            System.out.print(" Domain " + domain.getId() + " (" + domain.getSize() + " nodes); ");
        });

        System.out.println("");

//        System.out.println("Number of flows      = " + flows.size());
//
//        System.out.println("Avg Hops = " + avgPktHops);
//        System.out.println("Max Hops = " + maxPktHops);
//
//        System.out.println("");
//        System.out.println("Avg Flow Hops = " + avgFlowNodes);
//        // System.out.println("Flow with non-max SLD = " + Flow.numFlowsWithNonMaxSLD);
//        System.out.println("");
//        System.out.println("============= Domain Partioning Stats ===============");
//
//        System.out.println("Max Fwd Table Size = " + maxDomainFwdTableSize);
//        System.out.println("Avg Fwd Table Size = " + avgDomainFwdTableSize);
//        System.out.println("Total Fwd Entries  = " + totalDomainFwdEntries);
//        System.out.println("Max Label Stack Size = " + Traffic.getMaxStackSize(FTS_OPT_DOMAIN));
//        System.out.println("");
//
//        System.out.println("================= Heuristic Stats ==================");
//
//        System.out.println("Max Fwd Table Size = " + maxHeuristicFwdTableSize);
//        System.out.println("Avg Fwd Table Size = " + avgHeuristicFwdTableSize);
//        System.out.println("Total Fwd Entries  = " + totalHeuristicFwdEntries);
//        System.out.println("Max Label Stack Size = " + Traffic.getMaxStackSize(FTS_OPT_HEURISTIC));
//        System.out.println("");
        System.out.println("");
        // System.out.println("================= Time Stats ==================");
        // System.out.println("Time for Path Computation = " + (long) (hcStartTime - pcStartTime) / 1000000 + " msecs");
        // System.out.println("Time for Heuristic Calculation = " + (long) (hcEndTime - hcStartTime) / 1000000 + " msecs");

    }

    // Methods that return the STATS required for the graph plot
    protected int roundsTaken() {
        return 0;
    }

    protected int initialVnfInstances() {
        return 0;
    }

    protected int finalVnfInstances() {
        return 0;
    }
}
