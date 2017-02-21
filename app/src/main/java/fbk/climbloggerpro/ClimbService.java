package fbk.climbloggerpro;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

public class ClimbService extends Service implements ClimbServiceInterface, ClimbNode.ClimbNodeTimeout, MonitoredClimbNode.MonitoredClimbNodeTimeout {

    public final static String ACTION_DATALOG_ACTIVE ="fbk.climblogger.ClimbService.ACTION_DATALOG_ACTIVE";
    public final static String ACTION_DATALOG_INACTIVE ="fbk.climblogger.ClimbService.ACTION_DATALOG_INACTIVE";

    public final static String EXTRA_STRING ="fbk.climblogger.ClimbService.EXTRA_STRING";
    public final static String EXTRA_INT_ARRAY ="fbk.climblogger.ClimbService.EXTRA_INT_ARRAY";
    public final static String EXTRA_BYTE_ARRAY ="fbk.climblogger.ClimbService.EXTRA_BYTE_ARRAY";

    private BluetoothDevice  mBTDevice = null;
    private BluetoothGattService mBTService = null;
    private BluetoothGattCharacteristic mCIPOCharacteristic = null, mPICOCharacteristic = null;
    final static private UUID mClimbServiceUuid = ConfigVals.Service.CLIMB;
    final static private UUID mCIPOCharacteristicUuid = ConfigVals.Characteristic.CIPO;
    final static private UUID mPICOCharacteristicUuid = ConfigVals.Characteristic.PICO;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGattCallback mGattCallback;

    private boolean nodeTimeOutEnabled = false;
    private connectMasterCBack connectMasterCB;
    private String connectedMaster = null;

    private final static int TEXAS_INSTRUMENTS_MANUFACTER_ID = 0x000D;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean initialized = false;
    private IBinder mBinder;
    private final String TAG = "ClimbService_GIOVA";
    private ArrayList<ClimbNode> nodeList;

    public String dirName, file_name_log;
    File root;
    private File mFile = null;
    private FileWriter mFileWriter = null;
    private BufferedWriter mBufferedWriter = null;
    private boolean logEnabled;

    private int used_mtu = 23;
    private Context appContext = null;

    private Handler mHandler = null;
    private int masterNodeGATTConnectionState = BluetoothProfile.STATE_DISCONNECTED;

    // ----- Helpers ----------------------------------------------

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        Log.v(TAG, "Sending broadcast, action = " + action);

        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final String id) {
        final Intent intent = new Intent(action);
        if (id != null) {
            intent.putExtra(INTENT_EXTRA_ID, id);
        }

        Log.d(TAG, "Sending broadcast, action = " + action +" id = " + id);

        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final String id, final boolean success, final String msg) {
        final Intent intent = new Intent(action);
        if (id != null) {
            intent.putExtra(INTENT_EXTRA_ID, id);
        }
        intent.putExtra(INTENT_EXTRA_SUCCESS, success);
        intent.putExtra(INTENT_EXTRA_MSG, msg);

        Log.d(TAG, "Sending broadcast, action = " + action + " id = " + id + " " + success + " m=" + msg);

        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final String extra_type, final byte[] extra_data) {
        final Intent intent = new Intent(action);

        intent.putExtra(extra_type,extra_data);

        Log.d(TAG, "Sending broadcast, action = " + action + ". extra_type = " + extra_type + ". extra dimensions = " + extra_data.length);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final String extra_type, String extra_string) {
        final Intent intent = new Intent(action);

        intent.putExtra(extra_type,extra_string);
        Log.d(TAG, "Sending broadcast, action = " + action + ". extra_type = " + extra_type);
        sendBroadcast(intent);
    }

    // ----- Service ----------------------------------------------

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "ClimbService bound");

        return mBinder;
    }


    public class LocalBinder extends Binder {
        public ClimbService getService() {
            return ClimbService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "ClimbService created");
        insertTag("climb_service_created");

        mBinder = new LocalBinder();
        mHandler = new Handler();

        if(nodeList == null)  nodeList = new ArrayList<ClimbNode>(); //crea la lista (vuota) che terrà conto dei dispositivi attualmente visibili
        //TODO: why the if above? could it be not empty on onCreate?
        if(mBluetoothManager == null) { //TODO: why this if?
            initialize_bluetooth(); //TODO: handle error somehow
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        Log.i(TAG, "ClimbService onDestroy");
        insertTag("climb_service_destroyed");

        StopMonitoring();

        if (mBufferedWriter != null) {
            try {
                mBufferedWriter.flush();
            } catch (IOException e) {
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "ClimbService onUnbind");

        if (mBufferedWriter != null) {
            try {
                mBufferedWriter.flush();
            } catch (IOException e) {
            }
        }
        insertTag("climb_service_unbind");

        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        return START_STICKY; // run until explicitly stopped.
    }

    private boolean initialize_bluetooth() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (Build.VERSION.SDK_INT < 18) {
            Log.e(TAG, "API level " + Build.VERSION.SDK_INT + " not supported!");
            return false;
        }

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE); //qua era context.BLUETOOTH_SERVICE
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    // ------- old API -----------------------------------------

    public ArrayList getNodeList(){ // old API

        return nodeList;
    }

    public int StartMonitoring(boolean enableDatalog){ //TODO: not exposed in main API

        if(mBluetoothAdapter != null) {

            if(enableDatalog) {

                startDataLog();
                logEnabled = true;
                insertTag("Start_Monitoring " + ConfigVals.libVersion );
                insertTag("initializing" +
                        " API: " + Build.VERSION.SDK_INT +
                        " Release: " + Build.VERSION.RELEASE +
                        " Manuf: " + Build.MANUFACTURER +
                        " Product: " + Build.PRODUCT +
                        "");

                if (mBluetoothAdapter != null) {
                    insertTag("BTadapter" +
                            " state: " + mBluetoothAdapter.getState() +
                            " name: " + mBluetoothAdapter.getName() +
                            " address: " + mBluetoothAdapter.getAddress() +
                            "");
                }
                broadcastUpdate(ACTION_DATALOG_ACTIVE,EXTRA_STRING,file_name_log);
            }

            if (Build.VERSION.SDK_INT < 18) {
                Log.e(TAG, "API level " + Build.VERSION.SDK_INT + " not supported!");
                return 0;
            } else if (Build.VERSION.SDK_INT < 21) {
                mLeScanCallback = new myLeScanCallback();
                if (! mBluetoothAdapter.startLeScan(mLeScanCallback)) {
                    insertTag("can't start BLE scan");
                    return 0;
                };
            } else {
                //prepara il filtro che fa in modo di fare lo scan solo per device compatibili con climb (per ora filtra il nome)
                ScanFilter mScanFilter = new ScanFilter.Builder().setDeviceName(ConfigVals.CLIMB_MASTER_DEVICE_NAME).build();
                List<ScanFilter> mScanFilterList = new ArrayList<ScanFilter>();
                mScanFilterList.add(mScanFilter);
                mScanFilter = new ScanFilter.Builder().setDeviceName(ConfigVals.CLIMB_CHILD_DEVICE_NAME).build();
                mScanFilterList.add(mScanFilter);

                //imposta i settings di scan. vedere dentro la clase ScanSettings le opzioni disponibili
                ScanSettings mScanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();

                mScanCallback = new myScanCallback();
                mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                if (mBluetoothLeScanner == null) {
                    Log.e(TAG, "Unable to obtain a mBluetoothLeScanner.");
                    insertTag("Unable to obtain a mBluetoothLeScanner.");
                    return 0;
                }
                mBluetoothLeScanner.startScan(mScanFilterList, mScanSettings, mScanCallback);
            }
            enableNodeTimeout();
        }else{
            Log.w(TAG, "mBluetoothAdapter == NULL!!");
            insertTag("mBluetoothAdapter == NULL!!");
            return 0;
        }
        //TODO: iniziare la ricerca ble
        //TODO: avviare il log
        return 1;
    }

    public int StopMonitoring(){ //TODO: not exposed in main API
        if(mBluetoothAdapter != null) {
            disableNodeTimeout();
            if (Build.VERSION.SDK_INT < 21) {
                if (mBluetoothAdapter != null) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            } else {
                if (mBluetoothLeScanner != null) {
                    mBluetoothLeScanner.stopScan(mScanCallback);
                }
            }

            if(logEnabled){
                logEnabled = false;
                insertTag("Stop_Monitoring");
                stopDataLog();
                broadcastUpdate(ACTION_DATALOG_INACTIVE);
            }
        }else{
            Log.w(TAG, "mBluetoothAdapter == NULL!!");
        }
        //TODO: spegnere la ricerca ble
        //TODO: fermare il log
        return 1;
    }

    public String getLogFilename(){  //TODO: not exposed in main API
        if( mBufferedWriter == null ){
            return null;
        }
        return file_name_log;
    }

    public boolean insertTag(String tagDescriptiveString){ //TODO: not exposed in main API

            if (logEnabled) {
                if (mBufferedWriter != null) {
                    long nowMillis = System.currentTimeMillis();
                    final String timestamp = new SimpleDateFormat("yyyy MM dd HH mm ss").format(new Date()); // salva il timestamp per il log
                    try {

                        try {
                            String tagString = "" + timestamp +
                                    " " + nowMillis +
                                    " " + mBTDevice.getAddress() +
                                    " LOCAL_DEVICE " +
                                    "TAG data " +
                                    tagDescriptiveString +
                                    "\n";

                            mBufferedWriter.write(tagString);

                        /*
                        mBufferedWriter.write(timestamp + " " + nowMillis);
                        mBufferedWriter.write(" " + mBTDevice.getAddress());
                        mBufferedWriter.write(" LOCAL_DEVICE "); //NAME
                        mBufferedWriter.write("TAG" + " ");

                        mBufferedWriter.write("\n");
                        mBufferedWriter.flush();
*/
                            return true;
                        } catch (NullPointerException e){
                            String tagString = "" + timestamp +
                                    " " + nowMillis +
                                    " NO_ADDRESS" +
                                    " LOCAL_DEVICE " +
                                    "TAG data " +
                                    tagDescriptiveString +
                                    "\n";

                            mBufferedWriter.write(tagString);
                            return true;
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "Exception throwed while writing data to file.");
                        return false;
                    }
                }else{
                    return false;
                }
            }else{
                return false;
            }
        }

    public boolean SendCheckInAllCmd() { //TODO: not exposed in main API
        if (mBluetoothAdapter != null) {

            if (mBluetoothGatt == null) {
                Toast.makeText(appContext,
                        "Master NOT CONNECTED!",
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            if(mPICOCharacteristic != null) {
                byte[] gattData = {(byte) 0xFF, (byte) 0x01,(byte) 0x02};
                insertTag("Checking_in_all_nodes");
                mPICOCharacteristic.setValue(gattData);
                mBluetoothGatt.writeCharacteristic(mPICOCharacteristic);
                return true;
            }else{
                Log.w(TAG, "mPICOCharacteristic not already discovered?");
                return false;
            }

        }
        return false;
    }

    public boolean SendCheckOutAllCmd(){ //TODO: not exposed in main API
        if (mBluetoothAdapter != null) {

            if (mBluetoothGatt == null) {
                Toast.makeText(appContext,
                        "Master NOT CONNECTED!",
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            if(mPICOCharacteristic != null) {
                byte[] gattData = {(byte) 0xFF,(byte) 0x01, (byte) 0x00};
                insertTag("Checking_out_all_nodes");
                mPICOCharacteristic.setValue(gattData);
                mBluetoothGatt.writeCharacteristic(mPICOCharacteristic);
                return true;
            }else{
                Log.w(TAG, "mPICOCharacteristic not already discovered?");
                return false;
            }

        }
        return false;
    }

    public boolean ScheduleWakeUpCmd(int timeout_sec){
        if (mBluetoothAdapter != null) {

            if (mBluetoothGatt == null) {
                Toast.makeText(appContext,
                        "Master NOT CONNECTED!",
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            if(mPICOCharacteristic != null) {

                byte[] gattData = {(byte) 0xFF,(byte) 0x02, (byte)((timeout_sec>>16)&0xFF), (byte)((timeout_sec>>8)&0xFF), (byte)(timeout_sec&0xFF)};
                insertTag("Sending_wake_up_schedule");
                mPICOCharacteristic.setValue(gattData);
                mBluetoothGatt.writeCharacteristic(mPICOCharacteristic);
                return true;
            }else{
                Log.w(TAG, "mPICOCharacteristic not already discovered?");
                return false;
            }

        }
        return false;
    }

    public boolean SendReleaseAllCmd(){
        if (mBluetoothAdapter != null) {

            if (mBluetoothGatt == null) {
                Toast.makeText(appContext,
                        "Master NOT CONNECTED!",
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            if(mPICOCharacteristic != null) {
                byte[] gattData = {(byte) 0xFF, (byte) 0xFF};
                insertTag("Accepting all nodes");
                mPICOCharacteristic.setValue(gattData);
                mBluetoothGatt.writeCharacteristic(mPICOCharacteristic);
                return true;
            }else{
                Log.w(TAG, "mPICOCharacteristic not already discovered?");
            }

        }
        return false;
    }

    public void setScanForAll(boolean newValue){  //TODO: not exposed in main API
        scanForAll = newValue;
        int i = 0;
        if(!newValue) {
            while (i < nodeList.size()) {
                if (!nodeList.get(i).isMasterNode()) {
                    nodeList.remove(i);
                } else {
                    i++;
                }
            }
        }
    }

    // ------ CLIMB API Helpers ---------------------------------------------

    private ClimbNode nodeListGet(String master) {  //TODO: include in nodeList
        for(ClimbNode n : nodeList) {
            if (n.getNodeID().equals(master)) {
                return n;
            }
        }

        return null;
    }

    private ClimbNode nodeListGetConnectedMaster() {  //TODO: include in nodeList
        for(ClimbNode n : nodeList){
            if (n.getConnectionState()) {
                return n;
            }
        }

        return null;
    }

    // ------ CLIMB API Implementation ---------------------------------------------

    public boolean init() {

        closeGatt(); //make sure we start from a connection closed state, even if the service remained alive
        boolean ret = (StartMonitoring(true) == 1);
        initialized = ret;
        insertTag("init: " + ret);
        return ret;
    }

    public String[] getLogFiles() {
        //if (!initialized) return new String[0];

        String[] r;
        if (mFile != null) {
            if (mBufferedWriter != null) {
                try {
                    mBufferedWriter.flush();
                } catch (IOException e) {
                }
            }
            r = new String[1];
            r[0] = mFile.getAbsolutePath();
        } else {
            r = new String[0];
        }

        return r;
    }

    public void setContext(Context context)
    {
        appContext = context;
    }

    public String[] getMasters() {
        if (!initialized) return new String[0];

        ArrayList<String> ids = new ArrayList<String>();
        for(ClimbNode n : nodeList) {
            if (n.isMasterNode()) {
                ids.add(n.getNodeID());
            }
        }
        return ids.toArray(new String[ids.size()]);
    }

    public boolean setNodeList(String[] children) {
        if (!initialized) return false;

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            return false;
        }
        master.setAllowedChildrenList(children);
        //TODO: handle changes
        return true;
    }

    private NodeState getNodeState(MonitoredClimbNode n) {
        NodeState nodeState = new NodeState();
        nodeState.nodeID = n.getNodeIDString();
        nodeState.state = n.getNodeState();
        nodeState.lastSeen = n.getLastContactMillis();
        nodeState.lastStateChange = n.getLastStateChangeMillis();

        return nodeState;
    }

    public NodeState getNodeState(String id){
        if (!initialized) return null;

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            // TODO
            return null;
        }
        ArrayList<MonitoredClimbNode> children = master.getMonitoredClimbNodeList();
        NodeState nodeState = null;
        for (MonitoredClimbNode n : children){
            if (n.getNodeIDString().equals(id)) {
                nodeState = getNodeState(n);
                break;
            }
        }
        return nodeState; //null if not found
    }

    public NodeState[] getNetworkState() {
        if (!initialized) return new NodeState[0];

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            // TODO
            return null;
        }
        ArrayList<MonitoredClimbNode> children = master.getMonitoredClimbNodeList();
        NodeState[] nodeStates = new NodeState[children.size()];

        for (int i = 0; i < children.size(); i++){
            MonitoredClimbNode n = children.get(i);
            nodeStates[i] = getNodeState(n);
        }

        return nodeStates;
    }

    public String[] getChildren() {
        if (!initialized) return new String[0];

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            // TODO
            return null;
        }
        ArrayList<MonitoredClimbNode> children = master.getMonitoredClimbNodeList();
        String[] ids = new String[children.size()];

        for (int i = 0; i < children.size(); i++){
            ids[i] = children.get(i).getNodeIDString();
        }

        return ids;
    }

    private class connectMasterCBack implements Runnable {
        public String id;
        public boolean timedout = false;

        connectMasterCBack(String master){id = master;}

        @Override
        public void run() {
            timedout = true;
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect(); //be consistent, do not try anymore
                mBluetoothGatt.close(); //HTC one with android 5.0.2 is not calling the callback after disconnect. Needs to close here
                mBluetoothGatt = null;
            }
            Log.i(TAG, "Connect to " + id + " failed: timeout.");
            insertTag("Connect to " + id + " failed: timeout.");
            broadcastUpdate(STATE_CONNECTED_TO_CLIMB_MASTER, id, false, "Connect timed out");
        }
    }

    public boolean connectMaster(final String master) {
        if (!initialized) return false;

        connectedMaster = master;
        ClimbNode node = nodeListGet(master);
        insertTag("Request_connect_to_GATT "+ master + ((node == null ? " not_in_list" : (" " + node.isMasterNode()))));
        Log.i(TAG,"Request_connect_to_GATT "+ master + ((node == null ? " not_in_list" : (" " + node.isMasterNode()))));
        if (node != null && node.isMasterNode()) { //do something only if it is a master node
            if (mBluetoothGatt == null) {
                if (Build.VERSION.SDK_INT < 18) {
                    Log.e(TAG, "API level " + Build.VERSION.SDK_INT + " not supporting GATT callbacks!");
                    return false;
                }
                mBTDevice = node.getBleDevice();
                if (node.getConnectionState()) {// unfortunately mBTDevice.isConnected() is a SystemApi and thus can't be called
                    insertTag("this should not happen ... already connected?");
                }

                if (mBTDevice == null) {
                    insertTag("this should not happen ... mBTDevice == null!");
                }

                masterNodeGATTConnectionState = BluetoothProfile.STATE_CONNECTING;

                mGattCallback = new BluetoothGattCBack();

                insertTag("Connecting_to_GATT " + (mBTDevice != null ? mBTDevice.getAddress() : "null"));
                // The following call to the 4 parameter version of cpnnectGatt is public, but hidden with @hide
                // To make it work in API level 21 and 22, we need the trick from http://stackoverflow.com/questions/27633680/bluetoothdevice-connectgatt-with-transport-parameter
                java.lang.reflect.Method connectGattMethod;

                try {
                    connectGattMethod = mBTDevice.getClass().getMethod("connectGatt", Context.class, boolean.class, BluetoothGattCallback.class, int.class);
                    try {
                        mBluetoothGatt = (BluetoothGatt) connectGattMethod.invoke(mBTDevice, appContext, false, mGattCallback, 2); // (2 == LE, 1 == BR/EDR)
                        insertTag("using connectGatt4");
                    } catch (Exception e) {
                        mBluetoothGatt = mBTDevice.connectGatt(appContext, false, mGattCallback); //TODO: check why context is needed
                        insertTag("connectGatt4 failed, using connectGatt3");
                    }

                } catch (NoSuchMethodException e) {
                    mBluetoothGatt = mBTDevice.connectGatt(appContext, false, mGattCallback); //TODO: check why context is needed
                    insertTag("connectGatt4 doesn't exist, using connectGatt3");
                    //NoSuchMethod
                }

                if (mBluetoothGatt == null) {
                    Log.e(TAG, "connectGatt returned null!");
                    insertTag("connectGatt returned null!");
                    masterNodeGATTConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                    return false;
                }

                Log.i(TAG, "Try to connect a CLIMB master node ...");
                Toast.makeText(appContext,
                        "Connecting ...",
                        Toast.LENGTH_SHORT).show();
                connectMasterCB = new connectMasterCBack(master);
                mHandler.postDelayed(connectMasterCB, ConfigVals.CONNECT_TIMEOUT);
            } else {
                if (connectMasterCB == null) {
                    broadcastUpdate(STATE_CONNECTED_TO_CLIMB_MASTER, master, true, "Already connected"); //TODO: we are fireing the intent before returning true. Possible race condition?
                    insertTag("already connected!");
                    return true; //already connected
                } else { //connection in progress, do not accept another one
                    insertTag("already connecting!");
                    return false;
                }
            }
        } else {
            Log.w(TAG, "node " + master + " unknown or not a master node, can't connect!");
            insertTag("node " + master + " unknown or not a master node, can't connect!");
            return false;
        }
        return true;
    }

    public boolean disconnectMaster() { //TODO: handle several masters?
        Log.i(TAG, "Climb master node disconnecting ...");
        insertTag("Request_disconnect_from_GATT");
        if (!initialized) return false;

        masterNodeGATTConnectionState = BluetoothProfile.STATE_DISCONNECTING;

        if (mBluetoothGatt != null) {

            mBluetoothGatt.disconnect();
            //mBluetoothGatt.close(); //TODO: check if this is needed here, or should better be done when disconnected
            //mBluetoothGatt = null;
            //mBTService = null;
            //mCIPOCharacteristic = null;
            //mPICOCharacteristic = null;

            Toast.makeText(appContext,
                    "Disconnecting...",
                    Toast.LENGTH_SHORT).show();

            if (mBTDevice != null) {
                int index = isAlreadyInList(mBTDevice);
                if (index >= 0) {
                    nodeList.get(index).setConnectionState(false);
                } else {
                    Log.d(TAG, "Master not found in the list, CHECK!!!!");
                }
            }
            //broadcastUpdate(STATE_DISCONNECTED_FROM_CLIMB_MASTER);
            return true;
        } else {
            return true;
        }
    }

    private byte[] checkinCommand(MonitoredClimbNode monitoredChild){
        byte[] clickedChildID = monitoredChild.getNodeID();
        byte clickedChildState = monitoredChild.getNodeState();
        byte[] gattData = null;

        if(clickedChildState == 1) { //se lo stato è CHECKING
            if (!monitoredChild.setImposedState((byte) 2, this, ConfigVals.MON_NODE_TIMEOUT)) {
                Log.i(TAG, "Cannot change state of child " + monitoredChild.getNodeIDString() + ": another change is in progress");
            } else {
                Log.i(TAG, "Checking in child " + monitoredChild.getNodeIDString());
                insertTag("Accepting_node_" + clickedChildID[0]);
                gattData = new byte[]{clickedChildID[0], 2}; //assegna lo stato ON_BAORD
            }
        }

        return gattData;
    }

    private byte[] checkoutCommand(MonitoredClimbNode monitoredChild){
        byte[] clickedChildID = monitoredChild.getNodeID();
        byte clickedChildState = monitoredChild.getNodeState();
        byte[] gattData = null;

        if(clickedChildState == 2) { //se lo stato è CHECKING
            if (!monitoredChild.setImposedState((byte) 0, this, ConfigVals.MON_NODE_TIMEOUT)) {
                Log.i(TAG, "Cannot change state of child " + monitoredChild.getNodeIDString() + ": another change is in progress");
            } else {
                Log.i(TAG, "Checking out child " + monitoredChild.getNodeIDString());
                insertTag("Checking_out_node_" + clickedChildID[0]);
                gattData = new byte[]{clickedChildID[0], 0}; //assegna lo stato ON_BAORD
            }
        }

        return gattData;
    }

    private LinkedList<byte[]> PICOCharacteristicSendQueue = new LinkedList<byte[]>();
    private boolean PICOCharacteristicSending = false;

    private boolean sendPICOCharacteristic(byte[] m) {
        mPICOCharacteristic.setValue(m);
        if (!PICOCharacteristicSending && mBluetoothGatt.writeCharacteristic(mPICOCharacteristic)) {
            PICOCharacteristicSending = true;
            Log.i(TAG, "send: sent");
        } else {
            Log.i(TAG, "send: queuing message qlen:" +PICOCharacteristicSendQueue.size());
            return PICOCharacteristicSendQueue.add(m.clone()); //clone to be on the safe side. Might not be needed.
        }
        return true;
    }

    public boolean checkinChild(String child) {
        if (!initialized) return false;

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            return false; //TODO: exception?
        }
        MonitoredClimbNode monitoredChild = master.getChildByID(child);
        if(monitoredChild != null){
            byte[] gattData = checkinCommand(monitoredChild);
            if (gattData != null) {
                return sendPICOCharacteristic(gattData);
            } //TODO: error
        } //TODO: error
        return false;
    }

    public boolean checkinChildren(String[] children) {
        if (!initialized) return false;

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            return false; //TODO: exception?
        }

        boolean ret = true;
        byte[] gattData = new byte[used_mtu-4]; //TODO: verify -4 in specs
        int p = 0;

        for (String child : children) {
            MonitoredClimbNode monitoredChild = master.getChildByID(child);
            if (monitoredChild != null) {
                byte[] gattDataFrag = checkinCommand(monitoredChild);
                if (gattDataFrag != null) {
                    if (gattData.length < p + gattDataFrag.length) {
                        ret &= sendPICOCharacteristic(Arrays.copyOf(gattData,p));
                        p = 0;
                    }
                    System.arraycopy(gattDataFrag, 0, gattData, p, gattDataFrag.length);
                    p += gattDataFrag.length;
                }
            } else {
                ret = false;
            }
        }
        if (p > 0) {
            ret &= sendPICOCharacteristic(Arrays.copyOf(gattData,p));
        }
        return ret;
    }

    public boolean checkoutChild(String child) {
        if (!initialized) return false;

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            return false; //TODO: exception?
        }
        MonitoredClimbNode monitoredChild = master.getChildByID(child);
        if(monitoredChild != null){
            byte[] gattData = checkoutCommand(monitoredChild);
            if (gattData != null) {
                return sendPICOCharacteristic(gattData);
            } //TODO: error
        } //TODO: error
        return false;
    }

    public boolean checkoutChildren(String[] children) {
        if (!initialized) return false;

        ClimbNode master = nodeListGetConnectedMaster();
        if (master == null) {
            return false; //TODO: exception?
        }

        boolean ret = true;
        byte[] gattData = new byte[used_mtu-4]; //TODO: verify -4 in specs
        int p = 0;

        for (String child : children) {
            MonitoredClimbNode monitoredChild = master.getChildByID(child);
            if (monitoredChild != null) {
                byte[] gattDataFrag = checkoutCommand(monitoredChild);
                if (gattDataFrag != null) {
                    if (gattData.length < p + gattDataFrag.length) {
                        ret &= sendPICOCharacteristic(Arrays.copyOf(gattData,p));
                        p = 0;
                    }
                    System.arraycopy(gattDataFrag, 0, gattData, p, gattDataFrag.length);
                    p += gattDataFrag.length;
                }
            } else {
                ret = false;
            }
        }
        if (p > 0) {
            ret &= sendPICOCharacteristic(Arrays.copyOf(gattData,p));
        }
        return ret;
    }

    //---------------------------------------------------------------------
    boolean scanForAll = true;

    private boolean logScanResult(final BluetoothDevice device, int rssi, byte[] manufacterData, long nowMillis) {
        boolean ret = false;
        if (mBufferedWriter != null) { // questo significa che il log � stato abilitato
            final String timestamp = new SimpleDateFormat("yyyy MM dd HH mm ss").format(new Date()); // salva il timestamp per il log
            String manufString = "";

            if (manufacterData != null) {
                for (int i = 0; i < manufacterData.length; i++) {
                    manufString = manufString + String.format("%02X", manufacterData[i]);
                }
            }


            try {
                String logLine = "" + timestamp +
                        " " + nowMillis +
                        " " + device.getAddress() +
                        " " + device.getName() +
                        " ADV " +
                        rssi +
                        " " + manufString +
                        "\n";
                //TODO: AGGIUNGERE RSSI
                //mBufferedWriter.write(timestamp + " " + nowMillis);
                //mBufferedWriter.write(" " + result.getDevice().getAddress()); //MAC ADDRESS
                //mBufferedWriter.write(" " + result.getDevice().getName() + " "); //NAME
                //mBufferedWriter.write(" " + "ADV data" + " ");
                mBufferedWriter.write(logLine);
                ret = true;

            } catch (IOException e) {
                Log.w(TAG, "Exception throwed while writing data to file.");
            }
        }
        return ret;
    }

    // --- Android 5.x specific code ---
    private ScanCallback mScanCallback;
    class myScanCallback extends ScanCallback {

        @Override
        public void onBatchScanResults(List<ScanResult> results){

        }

        @Override
        public void onScanFailed(int errorCode){

        }

        @Override
        public void onScanResult(int callbackType, ScanResult result){  //public for SO, not for upper layer!
            Log.v(TAG, "onScanResult called!");
            if(callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                long nowMillis = System.currentTimeMillis();
                //PRIMA DI TUTTO SALVA IL LOG
                if (logEnabled) {
                    logScanResult(result.getDevice(),
                            result.getRssi(),
                            result.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID),
                            nowMillis);
                }

                if (scanForAll || result.getDevice().getName().equals(ConfigVals.CLIMB_MASTER_DEVICE_NAME)) {  //AGGIUNGI alla lista SOLO I NODI MASTER!!!!
                    //POI AVVIA IL PROCESSO PER AGGIORNARE LA UI
                    int index = isAlreadyInList(result.getDevice());
                    if (index >= 0) {
                        Log.v(TAG, "Found device is already in database and it is at index: " + index);
                        updateScnMetadata(index, result.getRssi(), result.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID), nowMillis);
                    } else {
                        Log.d(TAG, "New device found, adding it to database!");
                        addToList(result, nowMillis);
                    }
                }

                try {
                    if (result.getDevice().getName().equals(ConfigVals.CLIMB_CHILD_DEVICE_NAME)) {
                        byte[] scanData = result.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID);

                        int battVoltage = (((((int) scanData[scanData.length - 3]) << 24) >>> 24) << 8) + ((((int) scanData[scanData.length - 2]) << 24) >>> 24);
                        if (battVoltage < ConfigVals.ALLERT_BATTERY_VOLTAGE_LEVEL_mV) {
                            Toast.makeText(appContext,
                                    "Battery low on node (0x):" + String.format("%02X", scanData[0]) + "\nV = " + battVoltage,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }catch(Exception e){
                    Log.w(TAG, "An exception has been catched during battery check!");
                }
 /*               //se trovo il master connetilo!
                if(result.getDevice().getName().equals(ConfigVals.CLIMB_MASTER_DEVICE_NAME) && mBluetoothGatt == null){

                    mBluetoothGatt = result.getDevice().connectGatt(appContext, false, mGattCallback);
                    Log.i(TAG, "Climb master has been found, try to connect it!");
                }

   */
               // }
            }
        }

    };

    // --- Android 4.x specific code ---
    private myLeScanCallback mLeScanCallback;
    class myLeScanCallback implements BluetoothAdapter.LeScanCallback {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            long nowMillis = System.currentTimeMillis();
            Log.v(TAG, "onLeScan called: " + (device != null ? device.getName() : "NULL"));
            byte[] manufacturerData = extractManufacturerSpecificData(scanRecord, TEXAS_INSTRUMENTS_MANUFACTER_ID);
            if (device != null && device.getName() != null) {
                if (logEnabled && (device.getName().equals(ConfigVals.CLIMB_MASTER_DEVICE_NAME) || device.getName().equals(ConfigVals.CLIMB_CHILD_DEVICE_NAME))) {
                    logScanResult(device,
                            rssi,
                            manufacturerData,
                            nowMillis);
                }
                if (scanForAll || device.getName().equals(ConfigVals.CLIMB_MASTER_DEVICE_NAME)) {  //AGGIUNGI alla lista SOLO I NODI MASTER!!!!
                    //POI AVVIA IL PROCESSO PER AGGIORNARE LA UI
                    int index = isAlreadyInList(device);
                    if (index >= 0) {
                        Log.v(TAG, "Found device is already in database and it is at index: " + index);
                        //updateScnMetadata(index, scanRecord, nowMillis);
                        updateScnMetadata(index, rssi, manufacturerData, nowMillis);
                    } else {
                        Log.d(TAG, "New device found, adding it to database!");
                        if (android.os.Looper.myLooper()==null) android.os.Looper.prepare(); //TODO: check why this was needed. Otherwise to was throwing "Can't create handler inside thread that has not called Looper.prepare()"
                        addToList(device, rssi, manufacturerData, nowMillis);
                    }
                }

                if (device.getName().equals(ConfigVals.CLIMB_CHILD_DEVICE_NAME)){

                    int battVoltage = (((((int) manufacturerData[manufacturerData.length - 3]) << 24) >>> 24) << 8) + ((((int) manufacturerData[manufacturerData.length - 2]) << 24) >>> 24);
                    if (battVoltage < ConfigVals.ALLERT_BATTERY_VOLTAGE_LEVEL_mV) {
                        Toast.makeText(appContext,
                                "Battery low on node (0x):" + String.format("%02X", manufacturerData[0]) + "\nV = " + battVoltage,
                                Toast.LENGTH_SHORT).show();
                    }
                }

            }
        }

        private byte[] extractManufacturerSpecificData(byte[] scanRecord, int manufacturer_id){

            if(scanRecord != null) {
                int ptr = 0;
                while (ptr < scanRecord.length && scanRecord[ptr] != 0) {
                    int field_length = scanRecord[ptr];
                    if (scanRecord[ptr + 1] == (byte) (0xFF)) { //this is true when the manufacturer specific data field has been found
                        if (((scanRecord[ptr + 3] << 8) + scanRecord[ptr + 2]) == manufacturer_id) {
                            byte[] manufacturerSpecificData = new byte[field_length - 3];
                            System.arraycopy(scanRecord, ptr + 4, manufacturerSpecificData, 0, field_length - 3);
                            return manufacturerSpecificData;
                        }
                    }
                    ptr += (field_length + 1);
                }
                return null;
            }else{
                return null;
            }
        }
    };

    private void closeGatt() {
        if (mBluetoothGatt != null) {
            //mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        mBTDevice = null;
        mBTService = null;
        mCIPOCharacteristic = null;
        mPICOCharacteristic = null;
        used_mtu = 23;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private class BluetoothGattCBack extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (mBluetoothGatt == null || gatt != mBluetoothGatt) {
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.

                insertTag("Connected_to_GATT");
                if (Build.VERSION.SDK_INT < 21) {
                    Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());
                } else {
                    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    mBluetoothGatt.requestMtu(256);
                }

                //callback is called only after onServicesDiscovered()+getClimbService()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { //TODO: check if this was intentional or not. If not, try to do something.

                // for status codes, check https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-4.4.4_r2.0.1/stack/include/gatt_api.h
                Log.i(TAG, "Disconnected from GATT server " + connectedMaster + " Status: " + status);
                insertTag("Disconnected_from_GATT " + connectedMaster + " Status:" + status);

                if (connectMasterCB != null) { //timeout still active, in connection phase
                    if (mBluetoothGatt != null) {
                        if (!connectMasterCB.timedout) {
                            Log.w(TAG, "Connect attempt failed. Trying to reconnect ...");
                            insertTag("Connect attempt failed. Trying to reconnect ...");
                            mBluetoothGatt.connect();
                        } else {
                            Log.w(TAG, "Disconnect while ending connect due to timeout");
                            insertTag("Disconnect while ending connect due to timeout");
                        }
                        return;
                    } else {
                        Log.w(TAG, "Connect attempt failed. no GATT, no reconnect");
                        insertTag("Connect attempt failed. no GATT, no reconnect");
                        closeGatt();
                        String id = connectMasterCB.id;
                        mHandler.removeCallbacks(connectMasterCB);
                        connectMasterCB = null;
                        broadcastUpdate(STATE_CONNECTED_TO_CLIMB_MASTER, id, false, "connect failed with code " + status);
                    }
                } else { // was already connected, disconnected for some reason
                    if (masterNodeGATTConnectionState == BluetoothProfile.STATE_CONNECTED) {
                        if (mBluetoothGatt != null && mBluetoothGatt.connect()) {
                            Log.w(TAG, "Disconnected, trying quick reconnect ...");
                            insertTag("Disconnected, trying quick reconnect ...");
                            return;
                        }
                    }

                    Log.w(TAG, "Disconnected");
                    insertTag("Disconnected");
                    if(mBTDevice != null) {
                        int index = isAlreadyInList(mBTDevice);
                        if (index >= 0) {
                            nodeList.get(index).setConnectionState(false);
                        } else {
                            Log.d(TAG, "Master not found in the list, CHECK!!!!");
                        }
                    }
                    closeGatt();
                    String id = connectedMaster; //broadcastUpdate could trigger connectMaster, so save id in temp variable first
                    connectedMaster = null;
                    masterNodeGATTConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                    broadcastUpdate(STATE_DISCONNECTED_FROM_CLIMB_MASTER, id);
                }
            }else if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.i(TAG, "Connecting to GATT server. Status: " + status);

            }else if (newState == BluetoothProfile.STATE_DISCONNECTING) {   //TODO: understand difference from DISCONNECTED
                Log.i(TAG, "Disconnecting from GATT server. Status: " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            insertTag("onServicesDiscovered received: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // cerca subito i servizi necessari (aggiorna il broadcast solo quando tutte le caratteristiche saranno salvate)
                Log.i(TAG, "onServicesDiscovered received: " + status);
                getClimbService();
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            Log.d(TAG, "onCharacteristicChanged called!");
            // if(callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES){
            long nowMillis = System.currentTimeMillis();
            //PRIMA DI TUTTO SALVA IL LOG
            if (logEnabled) {
                if (mBufferedWriter != null) { // questo significa che il log � stato abilitato
                    final String timestamp = new SimpleDateFormat("yyyy MM dd HH mm ss").format(new Date()); // salva il timestamp per il log
                    String gattString = "";
                    byte[] gattData = characteristic.getValue();
                    for (int i = 0; i < gattData.length; i++) {
                        gattString = gattString + String.format("%02X", gattData[i]); //gatt DATA
                    }

                    try {
                        String logLine = "" + timestamp +
                                        " " + nowMillis +
                                        " " + mBTDevice.getAddress() +
                                        " " + mBTDevice.getName() +
                                        " GATT data " +
                                        " " + gattString +
                                        "\n";
                        mBufferedWriter.write(logLine);

                        //mBufferedWriter.write(timestamp + " " + nowMillis);
                        //mBufferedWriter.write(" " + mBTDevice.getAddress()); //MAC ADDRESS
                        //mBufferedWriter.write(" " + mBTDevice.getName() + " "); //NAME
                        //mBufferedWriter.write(" " + "GATT data ");



                        //mBufferedWriter.write("\n");
                        mBufferedWriter.flush();
                    } catch (IOException e) {
                        Log.w(TAG, "Exception throwed while writing data to file.");
                    }
                }
            }

            //POI AVVIA IL PROCESSO PER AGGIORNARE LA UI
            int index = isAlreadyInList(mBTDevice); // TODO: check multiple master case
            if (index >= 0) {
                Log.v(TAG, "Found device is already in database and it is at index: " + index);
                updateGATTMetadata(index, characteristic.getValue(), nowMillis);
            } else {
                Log.d(TAG, "New device found, it should be already in the list...verify!");
            }
            broadcastUpdate(ACTION_METADATA_CHANGED);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if (gatt != mBluetoothGatt) {
                Log.e(TAG, "onCharacteristicWrite for unknown GATT!");
            }
            if (characteristic != mPICOCharacteristic) {
                Log.e(TAG, "onCharacteristicWrite for unknown characteristic!");
            }

            PICOCharacteristicSending = false;
            if(status != BluetoothGatt.GATT_SUCCESS){
                Log.e(TAG, "onCharacteristicWrite: failed with status " + status);
                insertTag("onCharacteristicWrite: failed with status " + status);
            } else {
                Log.i(TAG, "onCharacteristicWrite: success " + status);
                //if there are queued writes do them here
                if (! PICOCharacteristicSendQueue.isEmpty()) {
                    mPICOCharacteristic.setValue(PICOCharacteristicSendQueue.element());
                    if (mBluetoothGatt.writeCharacteristic(mPICOCharacteristic)) {
                        PICOCharacteristicSending = true;
                        Log.i(TAG, "onCharacteristicWrite: sent queued message");
                        PICOCharacteristicSendQueue.remove();
                    } else {
                        Log.i(TAG, "onCharacteristicWrite: can't send queued message");
                    }
                }
            }
        }

        @Override
        public void onMtuChanged (BluetoothGatt gatt, int mtu, int status){
            Log.i(TAG, "MTU changed. MTU = "+mtu);
            Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());
            insertTag("MTU " +mtu);
            if(status == 0){
                used_mtu = mtu;
                return;
            }
            return;
        }

    };

    // ------ BLE integration ---------------------------------------------

    public boolean getClimbService() {
        Log.i(TAG, "Getting CLIMB Service");
        insertTag("Getting CLIMB Service");
        mBTService = mBluetoothGatt.getService(mClimbServiceUuid); //QUI VIENE CONTROLLATO CHE IL SERVER SU CUI SI E' CONNESSI ABBIA IL SERVIZIO ADATTO

        if(mBTService == null) {
            Log.e(TAG, "Could not get CLIMB Service");
            insertTag("Could not get CLIMB Service");
            return false;
        }
        else {
            Log.i(TAG, "CLIMB Service successfully retrieved");
            if(getCIPOCharacteristic() && getPICOCharacteristic()){
                int index = isAlreadyInList(mBTDevice);
                if (index >= 0) {
                    nodeList.get(index).setConnectionState(true);
                    Log.d(TAG, "Set master as connected!!!!");
                } else {
                    Log.d(TAG, "Master not found in the list, CHECK!!!!");
                }

                masterNodeGATTConnectionState = BluetoothProfile.STATE_CONNECTED;
                mHandler.removeCallbacks(connectMasterCB);
                connectMasterCB = null;
                broadcastUpdate(STATE_CONNECTED_TO_CLIMB_MASTER, nodeListGetConnectedMaster().getNodeID(), true, null); //TODO: add timeout on this
                insertTag("CLIMB Service acquired");
                return true;
            } else {
                insertTag("Could not get CLIMB characteristics");
                return false;
            }
        }
    }

    private boolean getCIPOCharacteristic() {
        Log.i(TAG, "Getting CIPO characteristic");
        mCIPOCharacteristic = mBTService.getCharacteristic(mCIPOCharacteristicUuid);

        if(mCIPOCharacteristic == null) {
            Log.i(TAG, "Could not find CIPO Characteristic");
            return false;
        }
        else {
            Log.i(TAG, "CIPO characteristic retrieved properly");
            return enableNotificationForCIPO();
        }
    }

    private boolean getPICOCharacteristic() {
        Log.i(TAG, "Getting PICO characteristic");
        mPICOCharacteristic = mBTService.getCharacteristic(mPICOCharacteristicUuid);

        if(mPICOCharacteristic == null) {
            Log.i(TAG, "Could not find PICO Characteristic");
            return false;
        }
        else {
            Log.i(TAG, "PICO characteristic retrieved properly");
            return true;
        }
    }

    private boolean enableNotificationForCIPO() {

        Log.i(TAG, "Enabling notification on Android API for CIPO");
        if(mCIPOCharacteristic == null){
            Log.w(TAG, "mCIPOCharacteristic == null !!");
            return false;
        }
        boolean success = mBluetoothGatt.setCharacteristicNotification(mCIPOCharacteristic, true);
        if(!success) {
            Log.i(TAG, "Enabling Android API notification failed!");
            return false;
        }
        else{
            Log.i(TAG, "Notification enabled on Android API!");
        }

        BluetoothGattDescriptor descriptor = mCIPOCharacteristic.getDescriptor(ConfigVals.Descriptor.CHAR_CLIENT_CONFIG);
        if(descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
            Log.i(TAG, "Notification on remote device (CCCD) enabled.");
        }
        else {
            Log.i(TAG, "Could not get descriptor for characteristic! CCCD Notification are not enabled.");
            return false;
        }
        return true;
    }

    private int isAlreadyInList(BluetoothDevice device){

        for(int i = 0; i < nodeList.size(); i++){
            if( nodeList.get(i).getAddress().equals(device.getAddress()) ){
                return i;
            }
        }

        return -1;
    }

    private boolean updateScnMetadata(int recordIndex, ScanResult targetNode, long nowMillis){

        //if(targetNode.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID) != null) {
            //if (targetNode.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID).length > 0) {
        nodeList.get(recordIndex).updateScnMetadata((byte) targetNode.getRssi(), targetNode.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID));//, nowMillis);

        //broadcastUpdate(ACTION_METADATA_CHANGED, EXTRA_INT_ARRAY, new int[]{recordIndex}); //questa allega  al broadcast l'indice che è cambiato, per ora non serve
                broadcastUpdate(ACTION_METADATA_CHANGED); //TODO: add nodeID
                return true;
            //}
        //}
       // return false;
    }

    private boolean updateScnMetadata(int recordIndex, int rssi,byte[] manufSpecificData, long nowMillis){

        //if(targetNode.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID) != null) {
        //if (targetNode.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID).length > 0) {
        nodeList.get(recordIndex).updateScnMetadata((byte)rssi, manufSpecificData);//, nowMillis);

        //broadcastUpdate(ACTION_METADATA_CHANGED, EXTRA_INT_ARRAY, new int[]{recordIndex}); //questa allega  al broadcast l'indice che è cambiato, per ora non serve
        broadcastUpdate(ACTION_METADATA_CHANGED); //TODO: add nodeID
        return true;
        //}
        //}
        // return false;
    }

    private boolean updateGATTMetadata(int recordIndex, byte[] cipo_data, long nowMillis) {

//TODO: L'rssi viene letto tramite un'altra callback, quindi per ora non ne tengo conto (in ClimbNode.updateGATTMetadata l'rssi non viene toccato)
        ClimbNode master = nodeList.get(recordIndex);
        List<byte[]> toChecking = master.updateGATTMetadata(0, cipo_data, nowMillis);

        //broadcastUpdate(ACTION_METADATA_CHANGED, EXTRA_INT_ARRAY, new int[]{recordIndex}); //questa allega  al broadcast l'indice che è cambiato, per ora non serve
        broadcastUpdate(ACTION_METADATA_CHANGED);

        // TODO: move this code down to ClimbNode
        byte[] gattData = new byte[used_mtu - 4]; //TODO: verify -4 in specs
        int p = 0;

        for (byte[] nodeID : toChecking) {
            MonitoredClimbNode n = master.findChildByID(nodeID);
            if (n != null && n.getImposedState() != 1 ) {
                n.setImposedState((byte) 1, null, 0);
            } else {
                continue;
            }
            Log.i(TAG, "Allowing child " + MonitoredClimbNode.nodeID2String(nodeID));
            insertTag("Allowing_node_" + MonitoredClimbNode.nodeID2String(nodeID));
            byte[] gattDataFrag = {nodeID[0], 1}; //assegna lo stato CHECKING e invia tutto al gatt
            System.arraycopy(gattDataFrag, 0, gattData, p, gattDataFrag.length);
            p += gattDataFrag.length;
            if (gattData.length - p < gattDataFrag.length) {
                //we have filled the packet. Send it out and start now one.
                if (! sendPICOCharacteristic(Arrays.copyOf(gattData,p))) {
                    Log.e(TAG, "Can't send state change message");
                }
                p = 0;
            }
        }

        if (p > 0) {
            if (! sendPICOCharacteristic(Arrays.copyOf(gattData,p))) {
                Log.e(TAG, "Can't send state change message");
            }
        }

        return true;
    }

    @Override
    public void climbNodeTimedout(ClimbNode node) {
        boolean nodeRemoved = false;
        if(node.isMasterNode() && masterNodeGATTConnectionState !=  BluetoothProfile.STATE_DISCONNECTED) {
            nodeList.remove(node);
            nodeRemoved = true;
        }else if(!node.isMasterNode()){
            nodeList.remove(node);
            nodeRemoved = true;
        }else{
            nodeRemoved = false;
        }
        if(nodeRemoved) {
            broadcastUpdate(ACTION_DEVICE_REMOVED_FROM_LIST, node.getNodeID());
            Log.i(TAG, "Timeout: node removed with index: " + nodeList.indexOf(node));
        }else{
            node.timeoutRestart();
            Log.i(TAG, "Timeout: node not removed: masterNodeGATTConnectionState = " + masterNodeGATTConnectionState +"\nTimeout restarted!");
        }
    }

    @Override
    public void monitoredClimbNodeChangeTimedout(MonitoredClimbNode node, byte imposedState, byte state) {
        switch (imposedState) {
            case 0:
                broadcastUpdate(STATE_CHECKEDOUT_CHILD, node.getNodeIDString(), false, "checkout failed: timeout"); //TODO: add param: failed
                Log.w(TAG, "Timeout: error changing child node state: " + node.getNodeIDString());
                break;
            case 2:
                broadcastUpdate(STATE_CHECKEDIN_CHILD, node.getNodeIDString(), false, "checkin failed: timeout"); //TODO: add param: failed
                Log.w(TAG, "Timeout: error changing child node state: " + node.getNodeIDString());
                break;
            default:
                Log.w(TAG, "Timeout: error changing child node state: " + node.getNodeIDString());
        }
    }

    @Override
    public void monitoredClimbNodeChangeSuccess(MonitoredClimbNode node, byte state) {
        switch (state) {
            case 0:
                broadcastUpdate(STATE_CHECKEDOUT_CHILD, node.getNodeIDString(), true, ""); //TODO: add param: success
                Log.d(TAG, "Timeout: error changing child node state: " + node.getNodeIDString());
                break;
            case 2:
                broadcastUpdate(STATE_CHECKEDIN_CHILD, node.getNodeIDString(), true, ""); //TODO: add param: success
                Log.d(TAG, "Timeout: error changing child node state: " + node.getNodeIDString());
                break;
            default:
                Log.d(TAG, "Timeout: error changing child node state: " + node.getNodeIDString());
        }
    }

    private boolean addToList(ScanResult targetNode, long nowMillis){
        BluetoothDevice device = targetNode.getDevice();
        //nodeID id =
        boolean isMaster = device.getName().equals(ConfigVals.CLIMB_MASTER_DEVICE_NAME);
        ClimbNode newNode = new ClimbNode(device,
                //id,
                (byte) targetNode.getRssi(),
                targetNode.getScanRecord().getManufacturerSpecificData(TEXAS_INSTRUMENTS_MANUFACTER_ID),
                isMaster, this, this);
                                //nowMillis);
        nodeList.add(newNode);
        broadcastUpdate(ACTION_DEVICE_ADDED_TO_LIST, newNode.getNodeID());
        Log.d(TAG, "Node added with index: " + nodeList.indexOf(newNode));
        return true;
    }

    private boolean addToList(final BluetoothDevice device, int rssi, byte[] scanRecord, long nowMillis){
        //nodeID id =
        boolean isMaster = device.getName().equals(ConfigVals.CLIMB_MASTER_DEVICE_NAME);
        ClimbNode newNode = new ClimbNode(device,
                //id,
                (byte) rssi,
                scanRecord, //TODO: check if this is equivalent
                isMaster, this, this);
        //nowMillis);
        nodeList.add(newNode);
        broadcastUpdate(ACTION_DEVICE_ADDED_TO_LIST, newNode.getNodeID());
        Log.d(TAG, "Node added with index: " + nodeList.indexOf(newNode));
        return true;
    }

    private String startDataLog(){
        //TODO:se il file c'� gi� non crearlo, altrimenti creane un'altro
        if(mBufferedWriter == null){ // il file non � stato creato, quindi crealo
            if( get_log_num() == 1 ){
                return file_name_log;
            }
        } else{
            return null;
        }

        return null;
    }
    private String stopDataLog(){
        //TODO:chiudi il file
        if(mBufferedWriter != null){ // il file � presente
            try {
                mBufferedWriter.close();
                mFile = null;
                mBufferedWriter = null;
                file_name_log = null;
            }catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        return null;
    }

    private int get_log_num(){
        Log.i(TAG, "Initializing log file.");
        root = Environment.getExternalStorageDirectory();
        TimeZone tz = TimeZone.getTimeZone("Europe/Rome");

        Calendar rightNow = Calendar.getInstance(tz);// .getInstance();
        dirName=ConfigVals.folderName;
        //dirName2="CUPID_data/"+rightNow.get(Calendar.DAY_OF_MONTH)+"_"+ (rightNow.get(Calendar.MONTH) + 1) +"_"+ rightNow.get(Calendar.YEAR) +"/";

        try{
//	    		    dirName = "/sdcard/"+dirName2;
//	    			//dirName = Environment.getExternalStorageDirectory().getPath()+dirName2;
            File newFile = new File(dirName);
            newFile.mkdirs();
            Log.i(TAG, "Directory \""+ dirName + "\" created.");

        }
        catch(Exception e)
        {
            Log.w(TAG, "Exception creating folder");
            return -1;
        }

        if (root.canRead()) {

        }
        if (root.canWrite()){

            file_name_log = "log_"+rightNow.get(Calendar.DAY_OF_YEAR)+"_"+rightNow.get(Calendar.HOUR_OF_DAY)+"."+rightNow.get(Calendar.MINUTE)+"."+rightNow.get(Calendar.SECOND)+".txt";

            //mFile = new File(root,"log_data/"+file_name_log);
            mFile = new File(dirName,file_name_log);


            try {
                mFileWriter = new FileWriter(mFile);
                mBufferedWriter = new BufferedWriter(mFileWriter);
                Log.i(TAG, "Log file \""+ file_name_log + "\"created!");

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                Log.w(TAG, "IOException in creating file");
            }

            //setOutFile(mBufferedWriter);
        }else{
            Log.w(TAG, "Can't write to file");
            return -1;
        }

        return 1;
    }

    private void enableNodeTimeout(){
        nodeTimeOutEnabled = true;
    }

    private void disableNodeTimeout(){
        nodeTimeOutEnabled = false;
    }
}
