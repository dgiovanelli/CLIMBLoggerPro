        package fbk.climbloggerpro;

        import android.bluetooth.BluetoothDevice;
        import android.os.Handler;

        import java.util.ArrayList;
        import java.util.Arrays;
        import java.util.List;

        /**
         * Created by user on 05/10/2015.
         */

        public class ClimbNode {

            interface ClimbNodeTimeout {
                public void climbNodeTimedout(ClimbNode node);
            }

            private BluetoothDevice bleDevice;
            private byte rssi;
            //private SparseArray<byte[]> scanResponseData;
            private byte[] scanResponseData = {};
            private byte[] lastReceivedGattData = {};
            private final String TAG = "ClimbNode_GIOVA";
            //private long lastContactMillis = 0;
            private String[] allowedChildrenList = new String[0];
            private ArrayList<MonitoredClimbNode> onBoardChildrenList;
            private boolean connectionState = false;
            private boolean isMasterNode = false;
            private ClimbNodeTimeout timedoutCallback = null;
            private MonitoredClimbNode.MonitoredClimbNodeTimeout timedoutCallback2 = null;
            private Runnable timedoutTimer = null;
            private Handler mHandler = null;
            private boolean driveTransitionToChecking = true;


            public ClimbNode(BluetoothDevice dev, byte initRssi, byte[] newScanResponse, boolean masterNode, ClimbNodeTimeout cb, MonitoredClimbNode.MonitoredClimbNodeTimeout cb2) {//SparseArray<byte[]> newScanResponse){

                bleDevice = dev;
                rssi = initRssi;
                scanResponseData = newScanResponse;
                //lastContactMillis = millisNow;
                onBoardChildrenList = new ArrayList<MonitoredClimbNode>();
                isMasterNode = masterNode;
                timedoutCallback = cb;
                timedoutCallback2 = cb2;
                mHandler = new Handler();
                return;
            }

            public void setConnectionState(boolean state) {
                connectionState = state;
                if(connectionState == false){
                    onBoardChildrenList.clear();
                    onBoardChildrenList = new ArrayList<MonitoredClimbNode>();
                    lastReceivedGattData = null;
                    timeoutRestart();
                } else {
                    timeoutStop();
                }
            }

            public boolean getConnectionState(){
                return connectionState;
            }

            public String toString() {
                String mString = "";
                if (bleDevice.getName() != null) mString = mString + bleDevice.getName() + ", ";
                else mString = mString + "Unknow device name, ";

                if (bleDevice.getAddress() != null)
                    mString = mString + "MAC: " + bleDevice.getAddress() + ", ";

                mString = mString + "RSSI: " + (rssi );

                if (bleDevice.getName() != null && bleDevice.getName().equals(ConfigVals.CLIMB_CHILD_DEVICE_NAME)) {
                    if (scanResponseData != null && scanResponseData.length > 1) {
                        mString += " Node ID (0x):" + String.format("%02X", scanResponseData[0]) + " State: " + stateToString(scanResponseData[1]);
                    }
                }

                    if(connectionState){
                    mString = mString + ". Conn.";
                }
        /*
                if(scanResponseData != null){
                    for(int i = 0; i < scanResponseData.size();i++){
                        Log.d(TAG, "scanResponseData.size() = " + scanResponseData.size());
                        mString = mString + ", Scan Response Data: ";
                        byte[] manufacterData = scanResponseData.valueAt(i);
                        for(int j = 0; j < manufacterData.length; j++){
                            //Log.d(TAG, "manufacterData.length = " + manufacterData.length);
                            mString = mString + String.format("%02X",manufacterData[j]);
                        }
                    }
                }
        *//*
                int scanResponseDataLength = scanResponseData.length;
                if(scanResponseDataLength > 0){
                    mString = mString + ", Scan Response Data: ";
                    for(int i = 0; i < scanResponseDataLength; i++){
                        mString = mString + String.format("%02X",scanResponseData[i]);

                    }
                }*/
                return mString;
            }

            public String getAddress() { return bleDevice.getAddress(); }

            public String getName() {
                return bleDevice.getName();
            }

            public String getNodeID() { return getAddress(); }

            public BluetoothDevice getBleDevice() {
                return bleDevice;
            }

            public boolean isMasterNode(){
                return isMasterNode;
            }
            /*
            public long getLastContactMillis() {
                return lastContactMillis;
            }*/

            public byte[] getlastReceivedGattData(){
                return lastReceivedGattData;
            }

            public ArrayList<MonitoredClimbNode> getMonitoredClimbNodeList(){
                return onBoardChildrenList;
            }

            public void setAllowedChildrenList(String[] children){
                allowedChildrenList = children;
            }

            public String[] getAllowedChildrenList(){
                return allowedChildrenList;
            }

            public MonitoredClimbNode getChildByID(String id) {
                for(MonitoredClimbNode n : onBoardChildrenList){
                    if( n.getNodeIDString().equals(id) ){
                        return n;
                    }
                }

                return null;
            }

            private void timedout() {
                (timedoutCallback).climbNodeTimedout(this);
            }

            private void timeoutStop() {
                if (timedoutTimer != null) {
                    mHandler.removeCallbacks(timedoutTimer);
                }
            }

            public void timeoutRestart() {
                timeoutStop();
                timedoutTimer = new Runnable() {
                    @Override
                    public void run() {
                        timedout();
                    }
                };
                mHandler.postDelayed(timedoutTimer, ConfigVals.NODE_TIMEOUT);

                //next part is for enabling timeout on monitored nodes, it could be done better using callbacks, but it is more complicated, for now it is sufficient this way
                long nowMillis = System.currentTimeMillis();
                //monitored nodes timeout check
                for(int i = 0; i < onBoardChildrenList.size(); i++){
                    MonitoredClimbNode tempMonitoredNode = onBoardChildrenList.get(i);
                    if(tempMonitoredNode != null){
                        if(nowMillis - tempMonitoredNode.getLastContactMillis() > ConfigVals.MON_NODE_TIMEOUT){
                            onBoardChildrenList.remove(i);
                        }
                    }
                }
            }

            public void updateScnMetadata(byte newRssi, byte[] newScanResponse){//, long millisNow) {//SparseArray<byte[]> newScanResponse){
                rssi = newRssi;
                scanResponseData = newScanResponse;

                if (!connectionState) timeoutRestart();
///////////// COMMENT THE NEXT PART IF ANY PROBLEM OCCOUR WHEN HANDLING MONITORED LIST
                long millisNow = System.currentTimeMillis();
                if (!this.isMasterNode()){
                    //AGGIORNA LA LISTA DEI NODI VICINI
                    try {
                        for (MonitoredClimbNode n : onBoardChildrenList) {
                            n.setOld();
                        }
                        for (int i = 2; i < scanResponseData.length - 3; i = i + 2) {
                            if (scanResponseData[i] != 0) { //se l'ID è 0x00 scartalo
                                byte[] tempNodeID = {scanResponseData[i]};//, lastReceivedGattData[i+1]};
                                byte state = (byte) 100; //any invalid state, this is the reboradcasted id-rssi pair, it doesn't contain the state
                                byte rssi = scanResponseData[i + 1];
                                MonitoredClimbNode n = findChildByID(tempNodeID);

                                if (n == null) {
                                    onBoardChildrenList.add(new MonitoredClimbNode(tempNodeID, state, rssi, millisNow, mHandler));
                                } else {
                                    n.setNodeRssi(rssi,millisNow);
                                }

                                //}
                            }
                        }
                    }catch(Exception e){
                        onBoardChildrenList.clear();
                    }
                }
////////////////////////////////////////////////////////////////////////////////////
            }

            public MonitoredClimbNode findChildByID(byte[] id) {
                for (MonitoredClimbNode n : onBoardChildrenList) {
                    if (Arrays.equals(n.getNodeID(), id)) {
                        return n;
                    }
                }
                return null;
            }

            private boolean isAllowedChild(byte[] nodeID) {
                for (String s : allowedChildrenList) {
                    if (s.equals(String.format("%02X", nodeID[0]))) {
                        return true;
                    }
                }
                return false;
            }

            public List<byte[]> updateGATTMetadata(int newRssi, byte[] cipo_metadata, long millisNow) {//SparseArray<byte[]> newScanResponse){
                //rssi = newRssi;
                lastReceivedGattData = cipo_metadata;
                //lastContactMillis = millisNow;
                if (!connectionState) timeoutRestart();

                List<byte[]> toChecking = new ArrayList<byte[]>();
                //AGGIORNA LA LISTA DEI NODI ON_BOARD
                for (int i = 0; i < lastReceivedGattData.length-2; i = i + 3) {
                    if(lastReceivedGattData[i] != 0 ) { //se l'ID è 0x00 scartalo
                        //if (lastReceivedGattData[i + 2] == 2) { //ON_BOARD
                            byte[] tempNodeID = { lastReceivedGattData[i] };//, lastReceivedGattData[i+1]};
                            byte state = lastReceivedGattData[i+1];
                            byte rssi = lastReceivedGattData[i+2];
                            MonitoredClimbNode n = findChildByID(tempNodeID);

                            if (driveTransitionToChecking) {
                                if (state == 0 && isAllowedChild(tempNodeID)) {
                                    //state = 1; //TODO: think whether this makes things faster
                                    toChecking.add(tempNodeID);
                                }
                            }
                            if (n == null) {
                                onBoardChildrenList.add(new MonitoredClimbNode(tempNodeID, state, rssi, millisNow, mHandler));
                            } else {
                                n.setNodeState(state, millisNow);
                                n.setNodeRssi(rssi);
                            }
                        //}
                    }
                }
                return toChecking;
            }

            private String stateToString(byte s) {
                switch (s) {
                    case 0: return "BY MYSELF";
                    case 1: return "CHECKING";
                    case 2: return "ON BOARD";
                    case 3: return "ALERT";
                    case 4: return "GOING TO SLEEP";
                    case 5: return "BEACON ONLY";
                    case (byte)255: return "ERROR";
                    default: return "INVALID STATE";
                }
            }

            public List<String> getClimbNeighbourList() {

                //if (scanResponseData != null) {
                    ArrayList<String> neighbourList = new ArrayList<String>();
                    String description = "";

                    if (bleDevice.getName().equals(ConfigVals.CLIMB_CHILD_DEVICE_NAME)) {
                        if (scanResponseData != null && scanResponseData.length > 1) {
                            description = "Node ID (0x):" + String.format("%02X", scanResponseData[0]) + "\nState: " + stateToString(scanResponseData[1]);


                            if (scanResponseData.length > 4) {
                                description = description + "\nBattery Voltage = " + String.format("%d", (((((int) scanResponseData[scanResponseData.length - 3]) << 24) >>> 24) << 8) + ((((int) scanResponseData[scanResponseData.length - 2]) << 24) >>> 24)) + " mV";
                                description += "\nPacket counter: "+ ((scanResponseData[scanResponseData.length - 1]<< 24) >>> 24);
                            }

                            int onBoardListSize = onBoardChildrenList.size();
                            if (onBoardListSize > 0){
                                if(onBoardListSize > 1) {
                                    description += "\nNeighbors (";
                                }else {
                                    description += "\nNeighbor (";
                                }
                                description += onBoardListSize + "):  ";
                                for (MonitoredClimbNode tempNode : onBoardChildrenList) {
                                    int tempRSSI_dbm = (int)tempNode.getNodeRssi();
                                    if(false){ //use true for plot a 0-100 value for the RSSI, Use false for true dBm values (they can be positive if the local data is old)
                                        description += "\n\t0x" + tempNode.getNodeIDString();
                                        if (tempRSSI_dbm > 0) {
                                            tempRSSI_dbm = -tempRSSI_dbm;
                                        }
                                        tempRSSI_dbm = tempRSSI_dbm + 100;
                                        description += "; Power: " + tempRSSI_dbm;
                                    }else {
                                        description += "\n\t(0x):" + tempNode.getNodeIDString();
                                        description += ": " + tempRSSI_dbm +"dBm";
                                        if (tempNode.isDataOld()) {
                                            description += "*";
                                        }
                                        if (tempRSSI_dbm > 0) {
                                            description += "*";
                                        }
                                    }
                                }
                            }
                        }
                        neighbourList.add(description);
                        return neighbourList;

                    } else if (bleDevice.getName().equals(ConfigVals.CLIMB_MASTER_DEVICE_NAME)) {

                        if (connectionState) {
                            for (MonitoredClimbNode tempNode : onBoardChildrenList) {
                                description = "Node ID (0x): " + tempNode.getNodeIDString();
                                description += "\tState: " + stateToString(tempNode.getNodeState());
                                description += "\tRSSI: " + tempNode.getNodeRssi();
                                neighbourList.add(description);
                            }
                            return neighbourList;
                        }

                    }
                    return null;
            }
        }