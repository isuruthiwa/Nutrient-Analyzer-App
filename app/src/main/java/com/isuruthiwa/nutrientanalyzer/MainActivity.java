package com.isuruthiwa.nutrientanalyzer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {

    private String deviceName = null;
    public static Handler handler;
    public static BluetoothSocket mmSocket;
    public static boolean connectStatus;
    public static ConnectedThread connectedThread;
    public static CreateConnectThread createConnectThread;

    private final static int CONNECTING_STATUS = 1; // used in bluetooth handler to identify message status
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int LOCATION_CHANGE = 3; // used in bluetooth handler to identify message update

    private final static int ALL_PERMISSIONS_RESULT = 101;
    private FusedLocationProviderClient mFusedLocationClient;

    private double wayLatitude = 0.0, wayLongitude = 0.0;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private ColorReading mostRecentReading = null;

    private final String fileName = "readings.csv";

    private int readingCounter = 0;
    private int selectedNutrient = -1;

    private final static int NITROGEN_SELECTED = 0;
    private final static int PHOSPHORUS_SELECTED = 1;
    private final static int POTASSIUM_SELECTED = 2;

    Geocoder geocoder;
    String closestCity;


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(20 * 1000);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.e("Location", "Null location");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        wayLatitude = location.getLatitude();
                        wayLongitude = location.getLongitude();

                        Log.e("Location", "Getting location");
                        Log.e("Location", String.format(Locale.US, "%s -- %s", wayLatitude, wayLongitude));
                    }
                }
            }
        };
        
        // UI Initialization
        final Button buttonConnect = findViewById(R.id.connectBtn);
        final TextView buttonRead = findViewById(R.id.readBtn);
        final Button saveBtn = findViewById(R.id.saveBtn);
        final TextView locationView = findViewById(R.id.locationTxt);
        final TextView counterView = findViewById(R.id.readCounterTxt);
        final Spinner nutrientSpinner = findViewById(R.id.spinner);
        final EditText amountTxt = findViewById(R.id.kReadTxt);
        amountTxt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.nutrients, R.layout.support_simple_spinner_dropdown_item);
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        nutrientSpinner.setAdapter(adapter);

        locationView.setText("Location : Null");

        geocoder = new Geocoder(this, Locale.getDefault());

        // If a bluetooth device has been selected from SelectDeviceActivity
        deviceName = "NutrientAnalyzer";
        if (deviceName != null){
            // Get the device address to make BT Connection
            Log.i("INFO", "Connecting to " + deviceName + "...");
            buttonConnect.setEnabled(false);

            /*
            This is the most important piece of code. When "deviceName" is found
            the code will call a new thread to create a bluetooth connection to the
            selected device (see the thread code below)
             */
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            createConnectThread = new CreateConnectThread(bluetoothAdapter, deviceName);
            createConnectThread.start();
        }

        /*
        Second most important piece of Code. GUI Handler
         */
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg){
                switch (msg.what){
                    case CONNECTING_STATUS:
                        switch(msg.arg1){
                            case 1:
                                connectStatus = true;
                                buttonConnect.setEnabled(false);
                                buttonConnect.setBackgroundColor(getResources().getColor(R.color.connected));
                                buttonConnect.setTextColor(Color.WHITE);
                                buttonConnect.setText("CONNECTED");
                                break;
                            case -1:
                                Log.e("Connection","Device fails to connect");
                                buttonConnect.setBackgroundColor(Color.RED);
                                connectStatus = false;
                                buttonConnect.setEnabled(true);
                                break;
                        }
                        break;

                    case MESSAGE_READ:
                        String colorReading = msg.obj.toString(); // Read message from Arduino
                        Log.e("MSG", colorReading);
                        Gson colorReadGson = new Gson();
                        mostRecentReading = colorReadGson.fromJson(colorReading, ColorReading.class);
                        Log.e("Color", mostRecentReading.toString());
                        readingCounter++;
                        counterView.setText(String.valueOf(readingCounter));
                        Toast.makeText(MainActivity.this, "Received color reading from device", Toast.LENGTH_SHORT)
                                .show();
                        break;

                    case LOCATION_CHANGE:
                        locationView.setText("Location: "+closestCity);
                        break;
                }
            }
        };

        // Select Bluetooth Device
        buttonConnect.setOnClickListener(view -> {
            if(connectStatus){
                createConnectThread.cancel();
            }
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            createConnectThread = new CreateConnectThread(bluetoothAdapter, deviceName);
            createConnectThread.start();
        });

        // Send Read Command to Device
        buttonRead.setOnClickListener(v -> {
            if(connectStatus){
                Log.i("Reading", "Reading data from device");
                connectedThread.write("1");
                Toast.makeText(MainActivity.this, "Sent reading command to device, waiting for reply", Toast.LENGTH_SHORT)
                        .show();
            }else{
                Log.e("Reading", "Not connected to device");
            }
        });

        // Save Data to CSV
        saveBtn.setOnClickListener(v->{
            getLocation();
            String  nutrientAmount = amountTxt.getText().toString();
            if(nutrientAmount!=null && !nutrientAmount.equals("")) {
                Log.i("Amount", nutrientAmount.toString());
                if (!isAvailable() || isReadOnly()) {
                    Log.e("Write", "Unavailable");
                } else {
                    if (mostRecentReading != null) {
                        saveToFile(mostRecentReading, selectedNutrient, nutrientAmount.toString());
                        Toast.makeText(MainActivity.this, "Data saved", Toast.LENGTH_SHORT)
                                .show();
                    }else {
                        Log.e("ERROR", "No read value to save");
                        Toast.makeText(MainActivity.this, "Error in saving data", Toast.LENGTH_SHORT)
                                .show();
                    }
                }
                amountTxt.setText("");
            }else{
                Toast.makeText(MainActivity.this, "No nutrient amount", Toast.LENGTH_SHORT)
                        .show();
            }
        });

        nutrientSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i("Nutrient", "Selected item "+ position);
                selectedNutrient = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedNutrient = -1;
            }
        });

        createFile();
        getLocation();
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                   1001);

        } else {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
                if (location != null) {
                    wayLatitude = location.getLatitude();
                    wayLongitude = location.getLongitude();
                    Log.e("Loc",String.format(Locale.US, "%s - %s", wayLatitude, wayLongitude));
                    try {
                        closestCity = geocoder.getFromLocation(wayLatitude, wayLongitude, 1).get(0).getLocality();
                        handler.obtainMessage(LOCATION_CHANGE, closestCity).sendToTarget();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
                }
            });

        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestPermission();
    }
    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;
    final private int REQUEST_LOCATION_ASK_PERMISSIONS = 10;

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat
                    .requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_ASK_PERMISSIONS);
            ActivityCompat
                    .requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_ASK_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    Toast.makeText(MainActivity.this, "Permission Granted", Toast.LENGTH_SHORT)
                            .show();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    public static class CreateConnectThread extends Thread {

        public CreateConnectThread(BluetoothAdapter bluetoothAdapter, String address) {
            /*
            Use a temporary object that is later assigned to mmSocket
            because mmSocket is final.
             */
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            String deviceAddress = null;
            for (BluetoothDevice device : pairedDevices) {
                if(address.equals(device.getName())) {
                    deviceAddress = device.getAddress(); // MAC address
                }
            }

            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
            BluetoothSocket tmp = null;
            UUID uuid = bluetoothDevice.getUuids()[0].getUuid();

            try {
                /*
                Get a BluetoothSocket to connect with the given BluetoothDevice.
                Due to Android device varieties,the method below may not work fo different devices.
                You should try using other methods i.e. :
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                 */
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                Log.e("Status", "Device connected");
                handler.obtainMessage(CONNECTING_STATUS, 1, -1).sendToTarget();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                    Log.e("Status", "Cannot connect to device");
                    handler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connectedThread = new ConnectedThread(mmSocket);
            connectedThread.run();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    /* =============================== Thread for Data Transfer =========================================== */
    public static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = (byte) mmInStream.read();
                    String readMessage;
                    if (buffer[bytes] == '\n'){
                        readMessage = new String(buffer,0,bytes);
                        Log.e("Arduino Message",readMessage);
                        handler.obtainMessage(MESSAGE_READ,readMessage).sendToTarget();
                        bytes = 0;
                    } else {
                        bytes++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes(); //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("Send Error","Unable to send message",e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    /* ============================ Terminate Connection at BackPress ====================== */
    @Override
    public void onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (createConnectThread != null){
            createConnectThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }

    // check external storage for reading
    private static boolean isReadOnly() {
        String storageState = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState);
    }

    // check availability for external storage
    private static boolean isAvailable() {
        String storageState = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(storageState);
    }


    public int createFile(){

        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String filePath = baseDir + File.separator + fileName;
        Log.e("FilePath", filePath);
        File f = new File(filePath);

        FileWriter outputFile = null;
        CSVWriter writer = null;

        try {
            // File already exists
            if(f.exists() && f.isFile())
            {
                Log.e("File", "Already exists");
                return 0;
            }

            outputFile = new FileWriter(f, true);
            writer = new CSVWriter(outputFile);
            // Even if header or records == null, this (dd) will be write
            String[] dd = {"ts","r_1","r_2","r_3","g_1","g_2","g_3","b_1","b_2","b_3","loc","lat"};
            writer.writeNext(dd);
            writer.close();
            return 1;
        } catch (IOException e) {
            f.delete(); // if something happened wrong, delete file
            e.printStackTrace();
            return -1;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public int saveToFile(ColorReading colorReading, int selectedNutrient, String nutrientAmount){
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String filePath = baseDir + File.separator + fileName;
        Log.e("FilePath", filePath);
        File f = new File(filePath);

        FileWriter outputFile = null;
        CSVWriter writer = null;

        String nutrient = "";

        switch (selectedNutrient){
            case NITROGEN_SELECTED:
                nutrient = "Nitrogen";
                break;
            case PHOSPHORUS_SELECTED:
                nutrient = "Phosphorous";
                break;
            case POTASSIUM_SELECTED:
                nutrient = "Potassium";
                break;
            default:
                nutrient = "_undefined";
                break;
        }


        try {
            outputFile = new FileWriter(f, true);
            writer = new CSVWriter(outputFile);

            // Even if header or records == null, this (dd) will be write
            String[] dd = {
                    String.valueOf(new Date()),
                    String.valueOf(colorReading.red_read.red),
                    String.valueOf(colorReading.red_read.green),
                    String.valueOf(colorReading.red_read.blue),
                    String.valueOf(colorReading.green_read.red),
                    String.valueOf(colorReading.green_read.green),
                    String.valueOf(colorReading.green_read.blue),
                    String.valueOf(colorReading.blue_read.red),
                    String.valueOf(colorReading.blue_read.green),
                    String.valueOf(colorReading.blue_read.blue),
                    String.valueOf(wayLatitude),
                    String.valueOf(wayLongitude),
                    nutrient,
                    String.valueOf(nutrientAmount)
            };
            writer.writeNext(dd);
            sendDataToFirebase(new DataObject(new Date(),mostRecentReading, wayLatitude, wayLongitude, nutrient, Double.valueOf(nutrientAmount)));
            writer.close();
            return 1;
        } catch (IOException e) {
            f.delete(); // if something happened wrong, delete file
            e.printStackTrace();
            return -1;
        }
    }

    public void sendDataToFirebase(DataObject obj){
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Create a new user with a first and last name
        Map<String, Object> data = new HashMap<>();
        data.put("ts", obj.ts);
        data.put("r_1", obj.colorReading.red_read.red);
        data.put("r_2", obj.colorReading.red_read.green);
        data.put("r_3", obj.colorReading.red_read.blue);
        data.put("g_1", obj.colorReading.green_read.red);
        data.put("g_2", obj.colorReading.green_read.green);
        data.put("g_3", obj.colorReading.green_read.blue);
        data.put("b_1", obj.colorReading.blue_read.red);
        data.put("b_2", obj.colorReading.blue_read.green);
        data.put("b_3", obj.colorReading.blue_read.blue);
        data.put("l_1", obj.latitude);
        data.put("l_2", obj.longitude);
        data.put("nut", obj.nutrient);
        data.put("amt", obj.nutrientAmount);

        // Add a new document with a generated ID
        db.collection("dataReadings")
                .add(data)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });
    }

}