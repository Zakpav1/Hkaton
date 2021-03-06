package ru.ssau.hkaton;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import static android.R.layout.*;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;

    BluetoothAdapter bluetoothAdapter;

    ArrayList<String> pairedDeviceArrayList;

    ListView listViewPairedDevice;
    FrameLayout ButPanel;

    ArrayAdapter<String> pairedDeviceAdapter;
    private UUID myUUID;

    ThreadConnectBTdevice myThreadConnectBTdevice;
    ThreadConnected myThreadConnected;

    private StringBuilder sb = new StringBuilder();

    public TextView textInfo, d14, d11, d12, d13;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final String UUID_STRING_WELL_KNOWN_SPP = "00001101-0000-1000-8000-00805F9B34FB";

        textInfo = (TextView) findViewById(R.id.textInfo);
        d14 = (TextView) findViewById(R.id.d10);
        d11 = (TextView) findViewById(R.id.d11);
        d12 = (TextView) findViewById(R.id.d12);
        d13 = (TextView) findViewById(R.id.d13);

        listViewPairedDevice = (ListView) findViewById(R.id.pairedlist);

        ButPanel = (FrameLayout) findViewById(R.id.ButPanel);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(this, "BLUETOOTH NOT support", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this hardware platform", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String stInfo = bluetoothAdapter.getName() + " " + bluetoothAdapter.getAddress();
        textInfo.setText(String.format("?????? ????????????????????: %s", stInfo));

    } // END onCreate


    @Override
    protected void onStart() { // ???????????? ???? ?????????????????? Bluetooth
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        setup();
    }

    private void setup() { // ???????????????? ???????????? ?????????????????????? Bluetooth-??????????????????

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) { // ???????? ???????? ?????????????????????? ????????????????????

            pairedDeviceArrayList = new ArrayList<>();

            for (BluetoothDevice device : pairedDevices) { // ?????????????????? ?????????????????????? ???????????????????? - ?????? + MAC-????????????
                pairedDeviceArrayList.add(device.getName() + "\n" + device.getAddress());
            }

            pairedDeviceAdapter = new ArrayAdapter<>(this, simple_list_item_1, pairedDeviceArrayList);
            listViewPairedDevice.setAdapter(pairedDeviceAdapter);

            listViewPairedDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() { // ???????? ???? ?????????????? ????????????????????

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    listViewPairedDevice.setVisibility(View.GONE); // ?????????? ?????????? ???????????????? ????????????

                    String itemValue = (String) listViewPairedDevice.getItemAtPosition(position);
                    String MAC = itemValue.substring(itemValue.length() - 17); // ?????????????????? MAC-??????????

                    BluetoothDevice device2 = bluetoothAdapter.getRemoteDevice(MAC);

                    myThreadConnectBTdevice = new ThreadConnectBTdevice(device2);
                    myThreadConnectBTdevice.start();  // ?????????????????? ?????????? ?????? ?????????????????????? Bluetooth
                }
            });
        }
    }


    private class ThreadConnectBTdevice extends Thread { // ?????????? ?????? ???????????????? ?? Bluetooth

        private BluetoothSocket bluetoothSocket = null;

        private ThreadConnectBTdevice(BluetoothDevice device) {

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run() { // ??????????????

            boolean success = false;

            try {
                bluetoothSocket.connect();
                success = true;
            } catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "?????? ????????????????, ?????????????????? Bluetooth-???????????????????? ?? ???????????????? ??????????????????????????", Toast.LENGTH_LONG).show();
                        listViewPairedDevice.setVisibility(View.VISIBLE);
                    }
                });

                try {
                    bluetoothSocket.close();
                } catch (IOException e1) {

                    e1.printStackTrace();
                }
            }

            if (success) {  // ???????? ????????????????????????????, ?????????? ?????????????????? ???????????? ?? ???????????????? ?? ?????????????????? ?????????? ???????????? ?? ???????????????? ????????????

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        ButPanel.setVisibility(View.VISIBLE); // ?????????????????? ???????????? ?? ????????????????
                    }
                });

                myThreadConnected = new ThreadConnected(bluetoothSocket);
                myThreadConnected.start(); // ???????????? ???????????? ???????????? ?? ???????????????? ????????????
            }
        }


        public void cancel() {

            Toast.makeText(getApplicationContext(), "Close - BluetoothSocket", Toast.LENGTH_LONG).show();

            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    } // END ThreadConnectBTdevice:


    public class ThreadConnected extends Thread {    // ?????????? - ?????????? ?? ???????????????? ????????????

        public final InputStream connectedInputStream;
        public final OutputStream connectedOutputStream;
        public String sbprint;

        public ThreadConnected(BluetoothSocket socket) {

            InputStream in = null;
            OutputStream out = null;

            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            connectedInputStream = in;
            connectedOutputStream = out;
        }


        @Override
        public void run() { // ?????????? ????????????
                try {
                    byte[] buffer = new byte[1];
                    int bytes = connectedInputStream.read(buffer);
                    String strIncom = new String(buffer, 0, bytes);
                    sb.append(strIncom); // ???????????????? ?????????????? ?? ????????????
                    int endOfLineIndex = sb.indexOf("\r\n"); // ???????????????????? ?????????? ????????????
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "??????  ???????????????? ????????????", Toast.LENGTH_LONG).show();
                }
        }


        public void write(byte[] buffer) {
            try {
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

/////////////////// ?????????????? ???????????? /////////////////////
/////////////////////////D14\
// //////////////////////////

    public void onClickBut1(View v) {

        if (myThreadConnected != null) {

            byte[] bytesToSend = "e".getBytes();
            myThreadConnected.write(bytesToSend);
        }
    }


    public void onClickBut2(View v) {

        if (myThreadConnected != null) {

            byte[] bytesToSend = "E".getBytes();
            myThreadConnected.write(bytesToSend);
        }
    }

////////////////////////D11////////////////////////////

    public void onClickBut3(View v) {

        if (myThreadConnected != null) {

            byte[] bytesToSend = "b".getBytes();
            myThreadConnected.write(bytesToSend);
        }
    }


    public void onClickBut4(View v) {

        if (myThreadConnected != null) {

            byte[] bytesToSend = "B".getBytes();
            myThreadConnected.write(bytesToSend);
        }
    }

//////////////////////D12//////////////////////////

    public void onClickBut5(View v) {

        if (myThreadConnected != null) {

            byte[] bytesToSend = "c".getBytes();
            myThreadConnected.write(bytesToSend);
        }
    }


    public void onClickBut6(View v) {

        if (myThreadConnected != null) {

            byte[] bytesToSend = "C".getBytes();
            myThreadConnected.write(bytesToSend);
        }
    }

    //////////////////////D13//////////////////////////

    public void onClickBut7(View v) {

        if (myThreadConnected != null) {

            byte[] bytesToSend = "d".getBytes();
            myThreadConnected.write(bytesToSend);
        }
    }



        public void onClickBut8(View v) {

            if (myThreadConnected != null) {

                byte[] bytesToSend = "asdf".getBytes();
                myThreadConnected.write(bytesToSend);
                //myThreadConnected.run();
                try {

                    byte[] buffer = new byte[1];
                    int ger = 1;
                    int bytes = myThreadConnected.connectedInputStream.read(buffer);

                    String strIncom = new String(buffer, 0, bytes);
                    sb.append(strIncom); // ???????????????? ?????????????? ?? ????????????
                    Toast.makeText(MainActivity.this, sb, Toast.LENGTH_LONG).show();

                }
                catch (IOException e){
                    Toast.makeText(MainActivity.this, "??????  ???????????????? ????????????", Toast.LENGTH_LONG).show();
                }
            }
        }

    public String openFileToString(byte[] _bytes)// ?????????????????? ???????? ?? ????????????
    {
        String file_string = "";

        for(int i = 0; i < _bytes.length; i++)
        {
            file_string += (char)_bytes[i];
        }

        return file_string;
    }

    //////////////////////algoritm////////////////////////////////////
        public float x[] = new float[2500];
        float y[] = new float[2500];
        float z[] = new float[2500];
        float ax[] = new float[2500];
        float ay[] = new float[2500];
        float az[] = new float[2500];

        public void Sort(View view) {
//            try {

String line = " ";
            int[][] zn = new int[0][];
            while(line!="00000000") {
                line = " ";
                while (line.charAt(line.length() - 1) != '^') {
                    byte[] buffer = new byte[1];
                    int bytes = 0;
                    try {
                        bytes = myThreadConnected.connectedInputStream.read(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String strIncom = new String(buffer, 0, bytes);
                    //          sb.append(strIncom); // ???????????????? ?????????????? ?? ??????????b
                    line = sb.append(strIncom).toString();
                }
                int[] subInt = new int[5];

                int iter = 0;

                for(int i = 0;i<5;i++){
                    String temp = " ";
                    while(line.charAt(iter) != '&' && line.charAt(iter) != '^'){
                        temp = temp + String.valueOf(line.charAt(iter));
                        iter++;
                    }

//                    subInt[i] = Integer.parseInt(temp);
                    Toast.makeText(MainActivity.this, temp, Toast.LENGTH_LONG).show();

                   iter++;
                }
//                subInt = Integer.parseInt(line.split(del));

                TextView tw = (TextView) findViewById(R.id.d10);
                tw.setText(line);
//                TextView tw1 = (TextView) findViewById(R.id.d12);
//                tw1.setText(subInt[3]);
            }
 /*               int i = 0;
                while (line != "00000000") {
                    for (int k = 0; k < 7; k++) {
                        String[] subStr;
                        String delimeter = "-"; // ??????????????????????
                        subStr = line.split(delimeter); // ???????????????????? ???????????? str ?? ?????????????? ???????????? split()
                        for (int e = 0; e < subStr.length; e++) {
                            switch (e) {
                                case (0):
                                    x[i] = Float.parseFloat(subStr[i]);
                                    break;
                                case (1):
                                    y[i] = Float.parseFloat(subStr[i]);
                                    break;
                                case (2):
                                    z[i] = Float.parseFloat(subStr[i]);
                                    break;

                                case (3):
                                    ax[i] = Float.parseFloat(subStr[i]);
                                    break;
                                case (4):
                                    ay[i] = Float.parseFloat(subStr[i]);
                                    break;
                                case (5):
                                    az[i] = Float.parseFloat(subStr[i]);
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    i++;
                    buffer = new byte[1];
                    bytes = myThreadConnected.connectedInputStream.read(buffer);
                    strIncom = new String(buffer, 0, bytes);
                    sb.append(strIncom); // ???????????????? ?????????????? ?? ??????????b
                    line = sb.append(strIncom).toString();
                    TextView textView = findViewById(R.id.d10);
                    textView.setText((int) x[i]);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            */

        }
    }

