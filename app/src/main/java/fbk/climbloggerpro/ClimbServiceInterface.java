package fbk.climbloggerpro;

import android.content.Context;

import java.util.ArrayList;

public interface ClimbServiceInterface {

    public enum State {
        BYMYSELF(0), CHECKING(1), ONBOARD(2), ALERT(3), GOINGTOSLEEP(4), ERROR(5);
        private final int id;
        State(int id) { this.id = id; }
        public int getValue() { return id; }
    }

    public class NodeState{
        public String nodeID;
        public int state;
        public long lastSeen;   //local time in millisec
        public long lastStateChange; //local time in millisec
    }

    public final static String ACTION_DEVICE_ADDED_TO_LIST = "fbk.climblogger.ClimbService.ACTION_DEVICE_ADDED_TO_LIST";
    public final static String ACTION_DEVICE_REMOVED_FROM_LIST = "fbk.climblogger.ClimbService.ACTION_DEVICE_REMOVED_FROM_LIST";
    public final static String ACTION_METADATA_CHANGED ="fbk.climblogger.ClimbService.ACTION_METADATA_CHANGED";
    public final static String ACTION_NODE_ALERT ="fbk.climblogger.ClimbService.ACTION_NODE_ALERT";
    public final static String ACTION_DATA_AVAILABLE ="fbk.climblogger.ClimbService.ACTION_DATA_AVAILABLE";

    public final static String STATE_CONNECTED_TO_CLIMB_MASTER = "fbk.climblogger.ClimbService.STATE_CONNECTED_TO_CLIMB_MASTER";
    public final static String STATE_DISCONNECTED_FROM_CLIMB_MASTER = "fbk.climblogger.ClimbService.STATE_DISCONNECTED_FROM_CLIMB_MASTER";
    public final static String STATE_CHECKEDIN_CHILD = "fbk.climblogger.ClimbService.STATE_CHECKEDIN_CHILD";
    public final static String STATE_CHECKEDOUT_CHILD = "fbk.climblogger.ClimbService.STATE_CHECKEDOUT_CHILD";

    /*
     * NodeID in string format
     */
    public final static String INTENT_EXTRA_ID ="fbk.climblogger.ClimbService.INTENT_EXTRA_ID";
    /*
     * Success/failure as boolean
     */
    public final static String INTENT_EXTRA_SUCCESS ="fbk.climblogger.ClimbService.INTENT_EXTRA_SUCCESS";
    /*
     * Message describing failure reason in case of failure
     */
    public final static String INTENT_EXTRA_MSG ="fbk.climblogger.ClimbService.INTENT_EXTRA_MSG";

    /**
     * Initialize the service.
     *
     * @return whether initialization wa successful. If false, other functions of the service should
     * not be used.
     */
    public boolean init();

    /**
     * Pass on the application context to the service, required for initializing the underlying
     * Bluetooth GATT. Its utilization is not documented in the Android API.
     */
    public void setContext(Context context);


    /**
     * Get list of recently seen master nodes.
     *
     * @return list of master node IDs, if any, or empty list.
     */
    public String[] getMasters();

    /**
     * Connect to the selected master.
     *
     * @return true if successfully started the connection process. If true, a
     * STATE_CONNECTED_TO_CLIMB_MASTER(true/false) message will signal the successful or
     * unsuccessful end of the connection attempt. If false, STATE_CONNECTED_TO_CLIMB_MASTER will
     * not be fired.
     */
    public boolean connectMaster(String master);

    /**
     * Disconnect from the connected master.
     *
     * @return true if successfully started the disconnection process. If true, a
     * STATE_DISCONNECTED_FROM_CLIMB_MASTER(true/false) message will signal the successful or
     * unsuccessful end of the disconnection attempt. If false, STATE_DISCONNECTED_FROM_CLIMB_MASTER
     * will not be fired.
     */
    public boolean disconnectMaster();

    /**
     * Get path of the log files. As a side effect, flush these files.
     *
     * @return absolute path of each log file, if there is any.
     */
    public String[] getLogFiles();

    /**
     * Set the list of all nodes that might belong to the connected master, i.e. nodes for which the
     * master can change state.
     *
     * @param children List of node IDs.
     * @return false if master is not connected or other error occurs
     */
    public boolean setNodeList(String[] children);

    /**
     * Get state of a single child node.
     *
     * @param id Id of node.
     * @return State if the node, if available, null otherwise.
     */
    public NodeState getNodeState(String id);

    /**
     * Get state of every child node seen by the master.
     *
     * @return Array of node states.
     */
    public NodeState[] getNetworkState();

    /**
     * Check in a given child node.
     *
     * @param child Id of node.
     * @return true if successfully started the checkin. If true, a STATE_CHECKEDIN_CHILD (true/false)
     * message will be fired signaling successful/unsuccessful checkin.
     */
    public boolean checkinChild(String child);

    /**
     * Check in child nodes.
     *
     * @param children Id of child nodes to check in.
     * @return true if successfully started the checkin for every single child node in the list.
     * A STATE_CHECKEDIN_CHILD (true/false) message will be fired signaling successful/unsuccessful checkin
     * for every child node where the checkin process stated successfully.
     */
    public boolean checkinChildren(String[] children);

    /**
     * Check out a given child node.
     *
     * @param child Id of node.
     * @return true if successfully started the checkout. If true, a STATE_CHECKEDOUT_CHILD (true/false)
     * message will be fired signaling successful/unsuccessful checkout.
     */
    public boolean checkoutChild(String child);

    /**
     * Check out child nodes.
     *
     * @param children Id of child nodes to check out.
     * @return true if successfully started the checkout for every single child node in the list.
     * A STATE_CHECKEDOUT_CHILD (true/false) message will be fired signaling successful/unsuccessful checkout
     * for every child node where the checkout process stated successfully.
     */
    public boolean checkoutChildren(String[] children);
}
