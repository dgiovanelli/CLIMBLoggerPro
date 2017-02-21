package fbk.climbloggerpro;

import android.os.Handler;

import java.util.Arrays;

/**
 * Created by user on 24/11/2015.
 */


public class MonitoredClimbNode{

    interface MonitoredClimbNodeTimeout {
        public void monitoredClimbNodeChangeSuccess(MonitoredClimbNode node, byte state);
        public void monitoredClimbNodeChangeTimedout(MonitoredClimbNode node, byte imposedState, byte state);
    }

    private byte[] nodeID = {};

    private byte imposedState = 5;
    private byte nodeState = 5; //5 = INVALID_STATE
    private long lastContactMillis = 0;
    private long lastStateChangeMillis = 0;
    private boolean timedOut = false;
    private byte RSSI;
    private MonitoredClimbNodeTimeout timedoutCallback = null;
    private Runnable timedoutTimer = null;
    private Handler mHandler = null;
    private boolean dataOld = false;

    public MonitoredClimbNode(byte[] newNodeID, byte newNodeState, byte newRSSI, long newLastContactMillis, Handler handler){
        nodeID = newNodeID;
        nodeState = newNodeState;
        timedOut = false;
        setNodeRssi(newRSSI);
        lastContactMillis = newLastContactMillis;
        lastStateChangeMillis = lastContactMillis;
        //mHandler = new Handler(); // cannot create handler when this one is called from GATT
        mHandler = handler;
    }

    public void setTimedOut(boolean value){
        timedOut = value;
    }

    public boolean getTimedOut(){
        return timedOut;
    }

    public byte[] getNodeID(){
        return nodeID;
    }

    public static String nodeID2String(byte[] id) {
        return String.format("%02X", id[0]);
    }

    public String getNodeIDString() {
        return nodeID2String(nodeID);
    }

    public byte getNodeState(){
        return nodeState;
    }

    public byte getImposedState() {
        return imposedState;
    }

    public byte getNodeRssi() {return RSSI;}

    public long getLastContactMillis(){
        return lastContactMillis;
    }

    public long getLastStateChangeMillis(){
        return lastStateChangeMillis;
    }

    public void setNodeState(byte newState){ // TODO: handle lastStateChangeMillis
        nodeState = newState;
    }

    public void setNodeState(byte newState, long newLastContactMillis){
        if (timedoutTimer != null) {
            if (newState == imposedState) {
                mHandler.removeCallbacks(timedoutTimer);
                timedoutTimer = null;
                (timedoutCallback).monitoredClimbNodeChangeSuccess(this, imposedState);
            } //TODO: handle error
        }

        if (nodeState != newState) {
            nodeState = newState;
            lastStateChangeMillis = newLastContactMillis;
        }
        lastContactMillis = newLastContactMillis;
    }

    public void setOld(){
        dataOld = true;
    }

    public boolean isDataOld(){
        return dataOld;
    }
    /*
     * Set imposed state. If a callback is provided, it will be called when the state changes to the imposed state, or after
     * a timeout. If a callback is not provided, imposed state only stores the data.
     */
    public boolean setImposedState(byte newImposedState, final MonitoredClimbNodeTimeout cb, int tout) {
        if (timedoutTimer != null) {
            return false; //another state change is in progress
            //mHandler.removeCallbacks(timedoutTimer);
        }
        timedoutCallback = cb;

        if (cb != null) {
            timedoutTimer = new Runnable() {
                @Override
                public void run() {
                    (cb).monitoredClimbNodeChangeTimedout(MonitoredClimbNode.this, imposedState, nodeState);
                    timedoutTimer = null;
                }
            };
            mHandler.postDelayed(timedoutTimer, tout);
        }
        imposedState = newImposedState;
        return true;
    }

    public void setNodeRssi(byte newRssi, long newLastContactMillis) {
        setNodeRssi(newRssi);
        lastStateChangeMillis = newLastContactMillis;
    }

    public void setNodeRssi(byte newRssi) {
        RSSI = newRssi;

        if((int)RSSI > 0){
            dataOld = true;
        }else{
            dataOld = false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        MonitoredClimbNode node = (MonitoredClimbNode)obj;
        if(Arrays.equals(this.getNodeID(), node.getNodeID()) ){
            return true;
        }else{
            return false;
        }

    }
}