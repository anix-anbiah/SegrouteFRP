/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

import java.util.Comparator;
import java.util.Objects;
import org.jgrapht.GraphPath;

/**
 *
 * @author anix
 */
public class Segment {

    public static final int SEGTYPE_UNPROTECTED = 1;
    public static final int SEGTYPE_PROTECTED = 2;

    private int type; // type of the segment;

    private int primaryOpstate; // the operational state of the primary path
    private int backupOpstate;  // the operational state of the backup path

    private GraphPath<Node, Link> primary;
    private GraphPath<Node, Link> backup; // null for unprotected segments

    private int id; // Segment ID;

    public Segment(int type, GraphPath<Node, Link> primary) {
        this.type = type;
        this.primaryOpstate = Network.OPSTATE_UP;
        this.backupOpstate = Network.OPSTATE_UP;
        this.primary = primary;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // get the opstate of the segment
    public int getOpstate() {

        // if primary is UP, then segment is UP
        if (getPrimaryOpstate() == Network.OPSTATE_UP) {
            return Network.OPSTATE_UP;
        }

        // else, if back up is DOWN, then segment is DOWN
        if (getBackupOpstate() == Network.OPSTATE_DOWN) {
            return Network.OPSTATE_DOWN;
        }

        // else, segment is operating using the BACKUP
        return Network.OPSTATE_BACKUP;
    }

    public int getPrimaryOpstate() {
        return primaryOpstate;
    }

    public void setPrimaryOpstate(int opstate) {
        this.primaryOpstate = opstate;
    }

    public int getBackupOpstate() {
        return backupOpstate;
    }

    public void setBackupOpstate(int opstate) {
        this.backupOpstate = opstate;
    }

    public void setBackup(GraphPath<Node, Link> backup) {

        // if the primary is longer than the backup, switch them
        // this is a HACK to overcome an issue with the Bhandari k-disjoint
        // algorithm, where sometimes the primary is longer than the backup
        if (backup.getEdgeList().size() < primary.getEdgeList().size()) {
            this.backup = primary;
            this.primary = backup;
        } else {
            this.backup = backup;
        }
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
        return backup;
    }

    public void prettyPrint() {
        System.out.println("Segment ID " + id);
        System.out.println("Type = "
                + ((type == SEGTYPE_PROTECTED) ? "Protected" : "Unprotected"));
        System.out.println("Primary Path = " + primary.toString());
        if (type == SEGTYPE_PROTECTED) {
            System.out.println("Backup Path = " + backup.toString());
        }
    }

}
