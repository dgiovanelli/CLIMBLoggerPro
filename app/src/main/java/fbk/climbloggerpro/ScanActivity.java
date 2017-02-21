package fbk.climbloggerpro;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ScanActivity extends Activity {

    private final static String TAG = "ScanActivity_GIOVA";
    private Button mStartButton,mStopButton,mTagButton,mCheckInAllButton,mCheckOutAllButton,mCheckInBcastButton,mCheckOutBcastButton,mScheduleWUButton;//,mReleaseCmdButton;
    private TextView mFilenameTextView,mNodesAmountTextView;
    private CheckBox mScanForAllCheckBox;
    private Vibrator mVibrator;
    private int index = 0;
    private ArrayList<ClimbNode> climbNodeList;
    private ArrayList<ClimbNode> climbNodeList_local;
    private List<String> allowedChidren = new ArrayList<String>();
    private ArrayAdapter<ListView> adapter;
    private ClimbService mClimbService;
    private Context mContext = null;
    private EditText mConsole = null;
    private long lastBroadcastMessageSentMillis = 0;
    private int wakeUP_year = 0, wakeUP_month = 0, wakeUP_day = 0, wakeUP_hour = 0, wakeUP_minute = 0;
    private Handler mHandler;
    private String logFileName = null;
    ExpandableListView expandableListView;
    MyExpandableListAdapter expandableListAdapter;
    List<ClimbNode> expandableListTitle;
    HashMap<ClimbNode, List<String>> expandableListDetail;

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.



    private final BroadcastReceiver mClimbUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ClimbService.ACTION_DEVICE_ADDED_TO_LIST.equals(action)) {

                if(intent.hasExtra(ClimbService.EXTRA_INT_ARRAY)){
                    updateDetailsExpandableListDetails(intent.getIntArrayExtra(ClimbService.EXTRA_INT_ARRAY));
                }else{
                    updateDetailsExpandableListDetails();
                }
                //expandableListAdapter.notifyDataSetChanged();
                log("ACTION_DEVICE_ADDED_TO_LIST broadcast received");

            } else if (ClimbService.ACTION_DEVICE_REMOVED_FROM_LIST.equals(action)) {

                //expandableListAdapter.notifyDataSetChanged();
                log("ACTION_DEVICE_REMOVED_FROM_LIST broadcast received");

            }else if (ClimbService.ACTION_METADATA_CHANGED.equals(action)) {

                if(intent.hasExtra(ClimbService.EXTRA_INT_ARRAY)){
                    //updateDetailsExpandableListDetails(intent.getIntArrayExtra(ClimbService.EXTRA_INT_ARRAY));
                }else{
                    //updateDetailsExpandableListDetails();
                }
                //expandableListAdapter.notifyDataSetChanged();
                //log("ACTION_METADATA_CHANGED broadcast received");

            }else if (ClimbService.ACTION_DATALOG_ACTIVE.equals(action)) {
                logFileName = intent.getStringExtra(ClimbService.EXTRA_STRING);
                mFilenameTextView.setText(logFileName);
                log("Datalog active on file: "+logFileName);
            }else if (ClimbService.ACTION_DATALOG_INACTIVE.equals(action)) {

                log("Datalog stopped");
            }else if (ClimbService.STATE_CONNECTED_TO_CLIMB_MASTER.equals(action)) {
                Toast.makeText(getApplicationContext(),
                        "Connected with GATT? " + intent.getBooleanExtra(ClimbService.INTENT_EXTRA_SUCCESS,true),
                        Toast.LENGTH_SHORT).show();
                expandableListAdapter.notifyDataSetChanged();
                log("Connected with GATT? " + intent.getBooleanExtra(ClimbService.INTENT_EXTRA_SUCCESS,true));
                mClimbService.setNodeList(allowedChidren.toArray(new String[allowedChidren.size()]));
            }else if (ClimbService.STATE_DISCONNECTED_FROM_CLIMB_MASTER.equals(action)) {
                //climbNodeList.clear();

                 Toast.makeText(getApplicationContext(),
                    "DISCONNECTED FROM GATT! " + intent.getStringExtra(ClimbService.INTENT_EXTRA_ID),
                    Toast.LENGTH_SHORT).show();
                log("DISCONNECTED FROM GATT!");
                expandableListAdapter.notifyDataSetChanged();

            }
            else if (ClimbService.ACTION_NODE_ALERT.equals(action)) {
                if (intent.hasExtra(ClimbService.EXTRA_BYTE_ARRAY)) {
                    byte[] nodeID = intent.getByteArrayExtra(ClimbService.EXTRA_BYTE_ARRAY);
                    String alertString = "ALERT ON NODE :" + String.format("%02X", nodeID[0]);
                    Toast.makeText(getApplicationContext(),
                            alertString,
                            Toast.LENGTH_LONG).show();

                    log(alertString);
                } else {

                }
            } else if (ClimbService.STATE_CHECKEDIN_CHILD.equals(action)) {
                if(intent.hasExtra(ClimbService.INTENT_EXTRA_ID))   {
                        String nodeID = intent.getStringExtra(ClimbService.INTENT_EXTRA_ID);
                        boolean success = intent.getBooleanExtra(ClimbService.INTENT_EXTRA_SUCCESS, true);
                        String msg = intent.getStringExtra(ClimbService.INTENT_EXTRA_MSG);
                        String alertString = "CHECKIN " + nodeID + " " + success + " " + msg;
                        Toast.makeText(getApplicationContext(),
                                alertString,
                                Toast.LENGTH_LONG).show();

                        log( alertString );
                    }else{

                    }
            } else if (ClimbService.STATE_CHECKEDOUT_CHILD.equals(action)) {
                if(intent.hasExtra(ClimbService.INTENT_EXTRA_ID))   {
                    String nodeID = intent.getStringExtra(ClimbService.INTENT_EXTRA_ID);
                    boolean success = intent.getBooleanExtra(ClimbService.INTENT_EXTRA_SUCCESS, true);
                    String msg = intent.getStringExtra(ClimbService.INTENT_EXTRA_MSG);
                    String alertString = "CHECKOUT " + nodeID + " " + success + " " + msg;
                    Toast.makeText(getApplicationContext(),
                            alertString,
                            Toast.LENGTH_LONG).show();

                    log( alertString );
                }else{

                }
            }
        }
    };

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override //Questa � usata per ritornare l'oggetto IBinder (c'� solo nei bound services)
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            //la prossima istruzione ritorna l'oggetto BluetoothLeService
            mClimbService = ((ClimbService.LocalBinder) service).getService();
            mClimbService.setContext(getApplicationContext());
            //IN QUESTO PUNTO RICHIEDI LA LISTA DI DISPOSITIVI INIZIALI PER INSERIRLA NELLA LISTVIEW
            climbNodeList = mClimbService.getNodeList(); //Csaba: TODO replace
            climbNodeList_local = new ArrayList<ClimbNode>();
            //expandableListTitle = climbNodeList;
            //expandableListDetail = ExpandableListDataPump.getData(); //expandableListDetail conterrà le info aggiuntive
            //expandableListTitle = new ArrayList<String>(expandableListDetail.keySet()); //expandableListTitle dovrà contenere i nomi dei dispositivi direttamente visibili dallo smartphone
            expandableListDetail = new HashMap<ClimbNode, List<String>>();
            expandableListAdapter = new MyExpandableListAdapter(mContext, climbNodeList_local, expandableListDetail);
            expandableListView.setAdapter(expandableListAdapter);
/*
            //crea un adapter per gestire la listView
            adapter = new ArrayAdapter<ListView>(mContext, android.R.layout.simple_list_item_1, android.R.id.text1,climbNodeList);

            // Assign adapter to ListView
            listView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
*/
            if(mClimbService != null) {
                mClimbService.setScanForAll(mScanForAllCheckBox.isChecked());
                updateUI(false);
                logFileName = mClimbService.getLogFilename();
                mFilenameTextView.setText(logFileName);
            }
            Log.i(TAG, "Service connected!");


            if(expandableListAdapter != null) { //automatically open the first group
                int count = expandableListAdapter.getGroupCount();
                for(int i = 0; i < count; i++) {
                    expandableListView.expandGroup(i);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mClimbService = null;
            Log.i(TAG,"Service disconnected!");
        }
    };

    View.OnClickListener startButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {
            if(mClimbService != null){
                mClimbService.init();
                mVibrator.vibrate(ConfigVals.vibrationTimeout);
                Log.i(TAG, "Start scan with data logging!");
                log("Start scan with data logging command sent!");
            }else{
                Log.i(TAG, "Start scan not sent!");
                log("Start scan not sent!");
            }

        }
    };

    View.OnClickListener stopButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            if(mClimbService != null){
                mVibrator.vibrate(ConfigVals.vibrationTimeout);
                Log.i(TAG, java.util.Arrays.toString(mClimbService.getLogFiles()));
                mClimbService.StopMonitoring();
                Log.i(TAG, "Stop scan!");
                log("Stop scan command sent!");
            }else{
                Log.i(TAG, "Stop scan not sent!");
                log("Stop scan not sent!");
            }

        }
    };

    View.OnClickListener tagButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            if(mClimbService != null){
                if(mClimbService.insertTag("Manually_inserted_tag")){
                    mVibrator.vibrate(ConfigVals.vibrationTimeout);
                    log("Tag Inserted!");
                }else{
                    log("Tag not inserted! Something went wrong in ClimbService!");
                }

            }else{
                Log.i(TAG, "Tag not inserted! mClimbService == null");
                log("Tag not inserted! mClimbService == null");
            }

        }
    };

    View.OnClickListener ckInAllButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            if( (SystemClock.uptimeMillis() - lastBroadcastMessageSentMillis) > ConfigVals.consecutiveBroadcastMessageTimeout_ms) {
                if (mClimbService != null) {
                    if (mClimbService.checkinChildren(mClimbService.getChildren())) {
                        mVibrator.vibrate(ConfigVals.vibrationTimeout);
                        lastBroadcastMessageSentMillis = SystemClock.uptimeMillis();
                    } else {
                        Log.i(TAG, "Check in all not sent!");
                        log("Check in all not sent!");
                    }
                } else {
                    Log.i(TAG, "Check in all not sent!");
                    log("Check in all not sent!");
                }
            }else{
                String alertString = "Wait a little";
                Toast.makeText(getApplicationContext(),
                        alertString,
                        Toast.LENGTH_LONG).show();
            }
        }
    };
    View.OnClickListener ckOutAllButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            if ((SystemClock.uptimeMillis() - lastBroadcastMessageSentMillis) > ConfigVals.consecutiveBroadcastMessageTimeout_ms) {
                if (mClimbService != null) {
                    if (mClimbService.checkoutChildren(mClimbService.getChildren())) {
                        mVibrator.vibrate(ConfigVals.vibrationTimeout);
                        lastBroadcastMessageSentMillis = SystemClock.uptimeMillis();
                    } else {
                        Log.i(TAG, "Check out all not sent!");
                        log("Check out all not sent!");
                    }
                } else {
                    Log.i(TAG, "Check out all not sent!");
                    log("Check out all not sent!");
                }

            }else{
                String alertString = "Wait a little";
                Toast.makeText(getApplicationContext(),
                        alertString,
                        Toast.LENGTH_LONG).show();
            }
        }
    };
    View.OnClickListener ckInBcastButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            if( (SystemClock.uptimeMillis() - lastBroadcastMessageSentMillis) > ConfigVals.consecutiveBroadcastMessageTimeout_ms) {
                if (mClimbService != null) {
                    if (mClimbService.SendCheckInAllCmd()) {
                        mVibrator.vibrate(ConfigVals.vibrationTimeout);
                        lastBroadcastMessageSentMillis = SystemClock.uptimeMillis();
                    } else {
                        Log.i(TAG, "Check in all not sent!");
                        log("Check in all not sent!");
                    }
                } else {
                    Log.i(TAG, "Check in all not sent!");
                    log("Check in all not sent!");
                }
            }else{
                String alertString = "Wait a little";
                Toast.makeText(getApplicationContext(),
                        alertString,
                        Toast.LENGTH_LONG).show();
            }
        }
    };
    View.OnClickListener ckOutBcastButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            if ((SystemClock.uptimeMillis() - lastBroadcastMessageSentMillis) > ConfigVals.consecutiveBroadcastMessageTimeout_ms) {
                if (mClimbService != null) {
                    if (mClimbService.SendCheckOutAllCmd()) {
                        mVibrator.vibrate(ConfigVals.vibrationTimeout);
                        lastBroadcastMessageSentMillis = SystemClock.uptimeMillis();
                    } else {
                        Log.i(TAG, "Check out all not sent!");
                        log("Check out all not sent!");
                    }
                } else {
                    Log.i(TAG, "Check out all not sent!");
                    log("Check out all not sent!");
                }

            }else{
                String alertString = "Wait a little";
                Toast.makeText(getApplicationContext(),
                        alertString,
                        Toast.LENGTH_LONG).show();
            }
        }
    };
    View.OnClickListener scheduleWUButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            mVibrator.vibrate(ConfigVals.vibrationTimeout);

            DialogFragment newFragment = new DatePickerFragment();
            newFragment.show(getFragmentManager(), "datePicker");

/*            if( (SystemClock.uptimeMillis() - lastBroadcastMessageSentMillis) > ConfigVals.consecutiveBroadcastMessageTimeout_ms) {
                if (mClimbService != null) {
                    if (mClimbService.ScheduleWakeUpCmd()) {
                        mVibrator.vibrate(ConfigVals.vibrationTimeout);
                        lastBroadcastMessageSentMillis = SystemClock.uptimeMillis();
                    } else {
                        Log.i(TAG, "schedule wake up not sent!");
                        log("schedule wake up not sent!");
                    }
                } else {
                    Log.i(TAG, "schedule wake up not sent!");
                    log("schedule wake up not sent!");
                }
            }else{
                String alertString = "Wait a little";
                Toast.makeText(getApplicationContext(),
                        alertString,
                        Toast.LENGTH_LONG).show();
            }*/
        }
    };

    View.OnClickListener  scanForAllCkBoxChangeHandler = new View.OnClickListener(){
        public void onClick(View v) {

            mVibrator.vibrate(ConfigVals.vibrationTimeout);

            if (mClimbService != null) {
                mClimbService.setScanForAll(mScanForAllCheckBox.isChecked());
                updateUI(false);
            }
        }
    };

    public void setWakeUpDate(int year, int month, int day){
        wakeUP_year = year;
        wakeUP_month = month;
        wakeUP_day = day;
    }

    public void setWakeUpHour(int hour, int minute){
        wakeUP_hour = hour;
        wakeUP_minute = minute;
    }

    public void sendWakeUpCMD(){

        if( (SystemClock.uptimeMillis() - lastBroadcastMessageSentMillis) > ConfigVals.consecutiveBroadcastMessageTimeout_ms) {
            if (mClimbService != null) {

                GregorianCalendar wakeUpDate = new GregorianCalendar(wakeUP_year,wakeUP_month,wakeUP_day,wakeUP_hour,wakeUP_minute);
                GregorianCalendar nowDate = new GregorianCalendar(Locale.ITALY);

                long wakeUpDate_millis = wakeUpDate.getTimeInMillis();
                long nowDate_millis = nowDate.getTimeInMillis();

                if( wakeUpDate_millis > nowDate_millis ) {
                    long diff_Sec = (wakeUpDate_millis-nowDate_millis)/1000;
                    if(diff_Sec < ConfigVals.MAX_WAKE_UP_DELAY_SEC && diff_Sec == (int)diff_Sec){

                        if (mClimbService.ScheduleWakeUpCmd((int) diff_Sec)) {
                            mVibrator.vibrate(ConfigVals.vibrationTimeout);
                            lastBroadcastMessageSentMillis = SystemClock.uptimeMillis();
                            Log.i(TAG, "schedule wake up sent!");
                            return;
                        } else {
                            Log.i(TAG, "schedule wake up not sent!");
                            log("schedule wake up not sent!");
                        }
                        return;

                    }
                }

                Toast.makeText(getApplicationContext(),
                        "Date not accepted...",
                        Toast.LENGTH_SHORT).show();
                mVibrator.vibrate(ConfigVals.vibrationTimeout*2);

            } else {
                Log.i(TAG, "schedule wake up not sent!");
                log("schedule wake up not sent!");
            }
        }else{
            String alertString = "Wait a little";
            Toast.makeText(getApplicationContext(),
                    alertString,
                    Toast.LENGTH_LONG).show();
        }

    }

/*    View.OnClickListener releaseCmdButtonHandler = new View.OnClickListener(){
        public void onClick(View v) {

            DialogFragment newFragment = new DatePickerFragment();
            newFragment.show(getFragmentManager(), "datePicker");
        }

    };*/

    ExpandableListView.OnGroupExpandListener mOnGroupExpandListener = new ExpandableListView.OnGroupExpandListener() {
        @Override
        public void onGroupExpand(int groupPosition) {
            Log.i(TAG, "Group expanded, position: " + groupPosition);
            ClimbNode clickedNode = climbNodeList_local.get(groupPosition);
            if(clickedNode.isMasterNode()) {
                if (! mClimbService.connectMaster(clickedNode.getNodeID())) {
                    Log.w(TAG, "Cannot connect to " + clickedNode.getNodeID());
                }
            }
            mVibrator.vibrate(ConfigVals.vibrationTimeout);
        }
    };

    ExpandableListView.OnGroupCollapseListener mOnGroupCollapseListener = new ExpandableListView.OnGroupCollapseListener() {
        @Override
        public void onGroupCollapse(int groupPosition) {
            ClimbNode clickedNode = climbNodeList_local.get(groupPosition);
            if(clickedNode.isMasterNode()) {
                mClimbService.disconnectMaster();
                mVibrator.vibrate(ConfigVals.vibrationTimeout);
            }
        }
    };

    ExpandableListView.OnChildClickListener mOnChildClickListener = new ExpandableListView.OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView parent, View v,
                                    int groupPosition, int childPosition, long id) {
            //TODO: implement
            ClimbNode clickedNode = climbNodeList_local.get(groupPosition);
            if (clickedNode.isMasterNode()) {
                MonitoredClimbNode monitoredChild = clickedNode.getMonitoredClimbNodeList().get(childPosition);
                String actionString = "";
                String childID = monitoredChild.getNodeIDString();
                switch (monitoredChild.getNodeState()) {
                    case 0:
                        if (!allowedChidren.contains(childID)) {
                            allowedChidren.add(childID);
                        }
                        mClimbService.setNodeList(allowedChidren.toArray(new String[allowedChidren.size()]));
                        actionString = "allowing " + childID;
                        break;
                    case 1:
                        mClimbService.checkinChild(childID);
                        actionString = "checkin " + childID;
                        break;
                    case 2:
                        mClimbService.checkoutChild(childID);
                        actionString = "checkout " + childID;
                        break;
                    default:
                }
                mVibrator.vibrate(ConfigVals.vibrationTimeout);
                Toast.makeText(getApplicationContext(),
                        actionString,
                        Toast.LENGTH_LONG).show();
            }

            return false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mContext = this.getApplicationContext();
        //mConsole = (EditText) findViewById(R.id.console_item);

        //listView = (ListView) findViewById(R.id.list);

        mStartButton = (Button) findViewById(R.id.buttonStart);
        mStartButton.setOnClickListener(startButtonHandler);

        mStopButton = (Button) findViewById(R.id.buttonStop);
        mStopButton.setOnClickListener(stopButtonHandler);

        mTagButton = (Button) findViewById(R.id.buttonTag);
        mTagButton.setOnClickListener(tagButtonHandler);

        mCheckInAllButton = (Button) findViewById(R.id.buttonCheckInAll);
        mCheckInAllButton.setOnClickListener(ckInAllButtonHandler);

        mCheckOutAllButton = (Button) findViewById(R.id.buttonCheckOutAll);
        mCheckOutAllButton.setOnClickListener(ckOutAllButtonHandler);

        mCheckInBcastButton = (Button) findViewById(R.id.buttonCheckInBcast);
        mCheckInBcastButton.setOnClickListener(ckInBcastButtonHandler);

        mCheckOutBcastButton = (Button) findViewById(R.id.buttonCheckOutBcast);
        mCheckOutBcastButton.setOnClickListener(ckOutBcastButtonHandler);

        mScheduleWUButton = (Button) findViewById(R.id.scheduleWakeUpAll);
        mScheduleWUButton.setOnClickListener(scheduleWUButtonHandler);

        mFilenameTextView = (TextView)findViewById(R.id.filenameTextBox);
        mNodesAmountTextView = (TextView)findViewById(R.id.nodesAmountTextBox);

        mScanForAllCheckBox = (CheckBox)findViewById(R.id.scanforall_ckbox) ;
        mScanForAllCheckBox.setOnClickListener(scanForAllCkBoxChangeHandler);

        mVibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        expandableListView = (ExpandableListView) findViewById(R.id.list);

        Intent climbServiceIntent = new Intent(ScanActivity.this, ClimbService.class); //capire come si comporta nel caso in qui il servizio sia ancora in esecuzione in background
        startService(climbServiceIntent);


        expandableListView.setOnGroupExpandListener( mOnGroupExpandListener );

        expandableListView.setOnGroupCollapseListener( mOnGroupCollapseListener );

        expandableListView.setOnChildClickListener( mOnChildClickListener );

        mHandler = new Handler();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUI(true);
            }
        });
    }

    private void updateUI(boolean autoSchedule){
        if(expandableListAdapter != null) {
            climbNodeList_local.clear();
            for(int i = 0; i <  climbNodeList.size(); i++){
                climbNodeList_local.add(climbNodeList.get(i));
            }
            if(expandableListAdapter != null) {
                updateDetailsExpandableListDetails();
                expandableListAdapter.notifyDataSetChanged();
            }
            int[] nodesAmout = getNodesAmount();
            mNodesAmountTextView.setText("("+nodesAmout[0]+" m "+nodesAmout[1]+" c)");
        }

        if(autoSchedule) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateUI(true);
                }
            }, ConfigVals.UI_UPDATE_INTERVAL_MS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();


    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "ScanActivity.onResume() called.");
        log("ScanActivity.onResume() called.");

        //AVVIA IL SERVIZIO E INIZIALIZZA IL BIND
        Intent climbServiceIntent = new Intent(ScanActivity.this, ClimbService.class); //capire come si comporta nel caso in qui il servizio sia ancora in esecuzione in background
        bindService(climbServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mClimbUpdateReceiver, makeClimbServiceIntentFilter());
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "ScanActivity.onPause() called.");
        log("ScanActivity.onPause() called.");
        super.onPause();
        unregisterReceiver(mClimbUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "ScanActivity.onDestroy() called.");
        log("ScanActivity.onDestroy() called.");

       //stopService(new Intent(ScanActivity.this, ClimbService.class));
        //climbNodeList = null;
        unbindService(mServiceConnection);
        //mClimbService = null;
        //expandableListDetail = null;
        //expandableListAdapter = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateDetailsExpandableListDetails(final int[] changedClimbNodeIndex){
        if(climbNodeList_local != null) {
            for (int i = 0; i < changedClimbNodeIndex.length; i++) {
                ClimbNode actualNode = climbNodeList_local.get(changedClimbNodeIndex[i]);
                if (actualNode != null) {
                    List<String> actualNodeDetailsList = actualNode.getClimbNeighbourList();
                    expandableListDetail.put(actualNode, actualNodeDetailsList); //il metodo put sostituisce il vecchio valore, quindi nella hashmap non si creano doppioni
                }
            }
        }

    }

    private void updateDetailsExpandableListDetails(){
        if(climbNodeList_local != null) {
            for (int i = 0; i < climbNodeList_local.size(); i++) {
                ClimbNode actualNode = climbNodeList_local.get(i);
                if (actualNode != null) {
                    List<String> actualNodeDetailsList = actualNode.getClimbNeighbourList();
                    expandableListDetail.put(actualNode, actualNodeDetailsList); //il metodo put sostituisce il vecchio valore, quindi nella hashmap non si creano doppioni
                }
            }
        }

    }

    private int[] getNodesAmount(){ //to be refined. If the service is running with scanForAll = true and a master is connected, every child is counted twice
        int amountOfMasters = 0;
        int amountOfChildren = 0;
        int[] ret = null;
        if(climbNodeList_local != null) {
            amountOfMasters = climbNodeList_local.size();
            for (int i = 0; i < climbNodeList_local.size(); i++) {
                ClimbNode actualNode = climbNodeList_local.get(i);
                if(!actualNode.isMasterNode()){
                    amountOfMasters--;
                }
                if (actualNode.getClimbNeighbourList() != null) {
                    amountOfChildren += actualNode.getClimbNeighbourList().size();
                }
            }
            ret = new int[]{amountOfMasters, amountOfChildren};
        }
        return ret;
    }

    private void log(final String txt) {
        if(mConsole == null) return;

        final String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConsole.setText(timestamp + " : " + txt + "\n" + mConsole.getText());
            }
        });
    }

    private static IntentFilter makeClimbServiceIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ClimbService.ACTION_DATALOG_ACTIVE);
        intentFilter.addAction(ClimbService.ACTION_DATALOG_INACTIVE);
        intentFilter.addAction(ClimbService.ACTION_METADATA_CHANGED);
        intentFilter.addAction(ClimbService.ACTION_DEVICE_ADDED_TO_LIST);
        intentFilter.addAction(ClimbService.STATE_CONNECTED_TO_CLIMB_MASTER);
        intentFilter.addAction(ClimbService.STATE_DISCONNECTED_FROM_CLIMB_MASTER);
        intentFilter.addAction(ClimbService.ACTION_DEVICE_REMOVED_FROM_LIST);
        intentFilter.addAction(ClimbService.ACTION_NODE_ALERT);
        intentFilter.addAction(ClimbService.STATE_CHECKEDIN_CHILD);
        intentFilter.addAction(ClimbService.STATE_CHECKEDOUT_CHILD);
        return intentFilter;
    }
}
