/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

/**
 *
 * @author anix
 */
public class Segment {

    public static final int SEGTYPE_UNPROTECTED = 1;
    public static final int SEGTYPE_PROTECTED = 2;

    private int type; // type of the segment;

    private Network net;

    private int primaryOpstate; // the operational state of the primary path
    private int backupOpstate[];  // the operational state of the backup path

    private GraphPath<Node, Link> primary;

    private Node src, dst; // source and destination node of the segment

    private List<GraphPath<Node, Link>> backupPaths; // numBackups number of backup paths

    private int id; // Segment ID;

    public Segment(int type, Network net, Node src, Node dst,
            GraphPath<Node, Link> primary) {
        this.type = type;
        this.net = net;
        this.primaryOpstate = Network.OPSTATE_UP;

        backupOpstate = new int[Network.SBP_MAX_BACKUPS];
        for (int i = 0; i < Network.SBP_MAX_BACKUPS; i++) {
            this.backupOpstate[i] = Network.OPSTATE_UP;
        }
        this.primary = primary;
        this.backupPaths = new ArrayList<>();

        this.src = src;
        this.dst = dst;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // get the opstate of the segment
    // subject to the maximum number of backups
    public int getOpstate(int maxBackup) {

        // if primary is UP, then segment is UP
        if (getPrimaryOpstate() == Network.OPSTATE_UP) {
            return Network.OPSTATE_UP;
        }

        // number of backup paths to check is the greater of
        // (i) maximum backups allowed by SBP
        // (ii) actual number of backups available
        int backupsToCheck = (maxBackup > backupPaths.size()) ? backupPaths.size() : maxBackup;

//        if (backupsToCheck > 5) {
//            System.out.println("getOpstate: Checking more than 5 backup paths");
//        }

        // if any of the backup paths are UP, then the segment is in BACKUP state
        for (int i = 0; i < backupsToCheck; i++) {
            if (backupOpstate[i] == Network.OPSTATE_UP) {
//                System.out.println("Segment.getOpstate: Backup #" +
//                        (i+1) + " is UP for " + "Segment " + getId() +
//                        ". Opstate is BACKUP");
                return Network.OPSTATE_BACKUP;
            }
        }

        // if we get here, then the primary and all the backups are DOWN
        return Network.OPSTATE_DOWN;
    }

    public int getPrimaryOpstate() {
        return primaryOpstate;
    }

    public void setPrimaryOpstate(int opstate) {
        this.primaryOpstate = opstate;
    }

    public int getBackupOpstate() {
        return backupOpstate[0];
    }

    public int getBackupOpstate(int index) {
        return backupOpstate[index];
    }

    public void setBackupOpstate(int index, int opstate) {
        this.backupOpstate[index] = opstate;
    }

    public GraphPath<Node, Link> getOpPath() {
        if (getPrimaryOpstate() == Network.OPSTATE_UP) {
            return getPrimary();
        }

        for (int i = 0; i < backupPaths.size(); i++) {
            if (backupOpstate[i] == Network.OPSTATE_UP) {
                return getBackup(i);
            }
        }

        return null;
    }

    public int getOpPathLength() {

        GraphPath<Node, Link> opPath = getOpPath();

        if (opPath == null) {
            System.out.println("Warning: Segment.getOpPathLength: Segment is not operational");
            return -1;
        }

        return opPath.getEdgeList().size();
    }

    public void addInitialBackup(GraphPath<Node, Link> backup) {

        // if the primary is longer than the backup, switch them
        // this is a HACK to overcome an issue with the Bhandari k-disjoint
        // algorithm, where sometimes the primary is longer than the backup
        if (!backupPaths.isEmpty()) {
            // add initial backup only once
            // System.out.println("Warning: addInitialBackup- backup already exists");
            return;
        }

        if (backup.getEdgeList().size() < primary.getEdgeList().size()) {
            backupPaths.add(primary);
            this.primary = backup;
        } else {
            backupPaths.add(backup);
        }
    }

    private void blockPath(GraphPath<Node, Link> path) {
        for (Link lnk : path.getEdgeList()) {
            net.graph.setEdgeWeight(lnk, Network.EDGE_WEIGHT_MAX);
        }
        return;
    }

    private void unblockPath(GraphPath<Node, Link> path) {
        for (Link lnk : path.getEdgeList()) {
            net.graph.setEdgeWeight(lnk, Graph.DEFAULT_EDGE_WEIGHT);
        }
        return;
    }

    public void addBackup() {
        // block the current primary and backup paths
        // compute an additional backup and add it 
        // to the list of backup paths
        if (backupPaths.size() == Network.SBP_MAX_BACKUPS) {
            System.out.println("Warning: addBackup: max. backup paths already");
            return;
        }

//        System.out.println("Adding additional backup for Segment " + getId());
        blockPath(primary);
        for (GraphPath<Node, Link> bkup : backupPaths) {
            blockPath(bkup);
        }

        // find the new backup from src to dst
        GraphPath<Node, Link> backup = net.getShortestPath(src, dst);

        // Unblock the primary and pre-existing backup paths
        unblockPath(primary);
        for (GraphPath<Node, Link> bkup : backupPaths) {
            unblockPath(bkup);
        }

        if (backup == null) {
            if (backupPaths.size() >= 5) {
                System.out.println("Unable to find additional (>5) backup path between "
                        + src.toString() + " and " + dst.toString());
            }
            return;
        }
        // finally, add the new backup path 
//        System.out.println("Additional backup path found between "
//                + src.toString() + " and " + dst.toString());
        backupPaths.add(backup);

        net.updateSbpMaxBackup(backupPaths.size());

//        System.out.println("Total number of backups = " + backupPaths.size());
        return;
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof Segment)) {
            return false;
        }

        // Type must match
        if (getType() != ((Segment) o).getType()) {
            return false;
        }

        // Two GraphPaths are equal if their edge lists are equal
        return (primary.getEdgeList().equals(((Segment) o).getPrimary().getEdgeList()));
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.primary);
        return hash;

    }

    private class GraphPathComparator implements Comparator<GraphPath> {

        @Override
        public int compare(GraphPath a, GraphPath b) {
            // Implementation TBD
            return 0;
        }
    }

    public int getType() {
        return type;
    }

    public GraphPath getPrimary() {
        return primary;
    }

    public GraphPath getBackup() {
        return backupPaths.get(0);
    }

    public GraphPath getBackup(int index) {
        return backupPaths.get(index);
    }

    public List<GraphPath<Node, Link>> getAllBackups() {
        return backupPaths;
    }

    public void prettyPrint() {
        System.out.println("Segment ID " + id);
        System.out.println("Type = "
                + ((type == SEGTYPE_PROTECTED) ? "Protected" : "Unprotected"));
        System.out.println("Primary Path = " + primary.toString());
        if (type == SEGTYPE_PROTECTED) {
            System.out.println("Number of backups = " + backupPaths.size());
            if (backupPaths.size() > 0) {
                System.out.println("First Backup Path = " + backupPaths.get(0).toString());
            }
        }
    }

}
