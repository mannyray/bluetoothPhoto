package com.szonov.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;




public class MainActivity extends AppCompatActivity {

    final byte delimiter = 33;
    int readBufferPosition = 0;

    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;

    UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard Seri-alPortService ID

    String oneImage = "one picture";
    String message = oneImage;
    String multipleImage = "multiple pictures";
    String shutdown = "shutdown";
    Context context;
    int seekBarValue = 0;

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.option1:
                if (checked)
                    message = oneImage;
                    break;
            case R.id.option2:
                if (checked) {
                    message = multipleImage;
                    break;
                }
        }
    }


    public void writebt(BluetoothSocket socket, String message) throws IOException {

        String msg = message;
        mmSocket = socket;
        OutputStream mmOutputStream = null;
        mmOutputStream = mmSocket.getOutputStream();
        mmOutputStream.write(msg.getBytes());

    }

    public void readbt(final BluetoothSocket socket) throws IOException, InterruptedException {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mmSocket = socket;
                        InputStream mmInputStream = null;
                        while(true) {
                            try {
                                mmInputStream = mmSocket.getInputStream();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            byte[] buffer = new byte[256];
                            int length = 0;
                            try {
                                length = mmInputStream.read(buffer);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            final String text = new String(buffer, 0, length);
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        Spinner spinner = (Spinner) findViewById(R.id.timeChoice);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.time_intervals, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinners
        spinner.setAdapter(adapter);



        final TextView tv = (TextView)findViewById(R.id.countDescription);
        SeekBar seekBar = (SeekBar) findViewById(R.id.seekCount);
        seekBar.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarValue = progress;
                tv.setText("Shot count: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        }
        );

        final Handler handler = new Handler();

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        int REQUEST_ENABLE_BT= 9999;
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        final class workerThread implements Runnable {

            private String btMsg;

            public workerThread(String msg) {
                btMsg = msg;
            }

            public void run() {
                while (true) {
                    int bytesAvailable;
                    try {
                        final InputStream mmInputStream;
                        mmInputStream = mmSocket.getInputStream();
                        bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            Log.e("recv bt", ""+bytesAvailable);


                            boolean packageRegistered = false;
                            boolean lengthKnown = false;
                            String currentLength = "";
                            int messageLength = 0;
                            boolean imageMessage = false;
                            boolean textMessage = false;

                            byte[] packetBytes = new byte[bytesAvailable];
                            Log.e("recv bt", "bytes available");
                            int chunkLength = 512;
                            byte[] readBuffer = new byte[chunkLength];
                            mmInputStream.read(packetBytes);

                            String delimiter = "!@#$%^";
                            String lastNCharacters = "";
                            int N = delimiter.length();

                            for (int i = 0; i < bytesAvailable; i++) {

                                byte b = packetBytes[i];
                                String bChar = new String( new byte[] { b }, "US-ASCII" );
                                Log.e("recv bt", bChar);

                                if ( !packageRegistered )
                                {
                                    if( lastNCharacters.length() < N )
                                    {
                                        lastNCharacters += bChar;
                                    }
                                    else{
                                        if ( lastNCharacters.equals(delimiter) ){
                                            packageRegistered = true;
                                        }
                                        lastNCharacters = lastNCharacters.substring(1,N);
                                        lastNCharacters += bChar;
                                    }
                                }

                                if ( packageRegistered ) {
                                    Log.e("recv bt", "registered");

                                    if (!lengthKnown) {
                                        if (bChar.equals("_")) {
                                            imageMessage = true;
                                            lengthKnown = true;
                                            messageLength = Integer.parseInt(currentLength);
                                            readBufferPosition = 0;
                                        }
                                        else if (bChar.equals("=")) {
                                            textMessage = true;
                                            lengthKnown = true;
                                            messageLength = Integer.parseInt(currentLength);
                                            readBufferPosition = 0;
                                        }
                                        else {
                                            currentLength += bChar;
                                        }
                                    } else {
                                        final byte[] message = new byte[messageLength];
                                        int initialLength = delimiter.length() + currentLength.length() + 1;
                                        int processedLength = initialLength;
                                        int startingIndex = initialLength;
                                        while (processedLength < messageLength + initialLength ) {
                                            Log.e("recv bt", "processedLength" + processedLength);

                                            for (int k = startingIndex; k < bytesAvailable; k++) {
                                                message[processedLength - initialLength ] = packetBytes[k];
                                                processedLength++;
                                                if(processedLength == messageLength  + initialLength ){
                                                    break;
                                                }
                                            }
                                            bytesAvailable = mmInputStream.available();
                                            packetBytes = new byte[bytesAvailable];
                                            mmInputStream.read(packetBytes);
                                            startingIndex = 0;
                                        }

                                        Log.e("recv bt", currentLength);

                                        byte[] encodedBytes = new byte[messageLength];
                                        final String data = new String(message, "US-ASCII");
                                        Log.e("recv bt", data);

                                        final boolean finalImageMessage = imageMessage;
                                        final boolean finalTextMessage = textMessage;
                                        handler.post(new Runnable() {
                                            public void run() {
                                                if (finalImageMessage) {
                                                    byte[] decodeString = null;
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                        try {
                                                            decodeString = Base64.getDecoder().decode(new String(data).getBytes("UTF-8"));
                                                        } catch (UnsupportedEncodingException e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                    Bitmap bmp = BitmapFactory.decodeByteArray(decodeString, 0, decodeString.length);
                                                    ImageView image = findViewById(R.id.cameraImage);
                                                    image.setImageBitmap(bmp);
                                                    image.setVisibility(View.VISIBLE);
                                                }
                                                else if (finalTextMessage){
                                                    Toast.makeText(context, data, Toast.LENGTH_LONG).show();
                                                }
                                            }
                                        });
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        final ImageButton uparrow = (ImageButton) findViewById(R.id.uparrow);
        final TextView status = (TextView) findViewById(R.id.status);

        uparrow.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    status.setText(message);

                    try {
                        String dataToSend = message;
                        if (message.equals(oneImage)){
                        }
                        else if(message.equals(multipleImage)){
                            String intervalBetweenShots = ((Spinner) ((Activity) context).findViewById(R.id.timeChoice)).getSelectedItem().toString();
                            dataToSend +=  " " + seekBarValue + " " + intervalBetweenShots;
                        }
                        writebt(mmSocket, dataToSend);
                        if(message.equals(shutdown)){
                            Intent intent = getIntent();
                            finish();
                            startActivity(intent);
                        }
                        //readbt( mmSocket );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }
        });


       final RadioGroup rb = (RadioGroup) findViewById(R.id.radioGroup);
       rb.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {
               // Is the button now checked?
               boolean checked = ((RadioButton) view).isChecked();

               // Check which radio button was clicked
               switch(view.getId()) {
                   case R.id.option1:
                       //if (checked)
                       message = oneImage;
                       break;
                   case R.id.option2:
                       //if (checked)
                       message = multipleImage;
                       break;
                   case R.id.option3:
                       message = shutdown;
                       break;
               }
           }
       });
       rb.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
           @Override
           public void onCheckedChanged(RadioGroup group, int checkedId) {
               TextView t1 = (TextView) findViewById(R.id.timeDescription);
               TextView t2 = (TextView) findViewById(R.id.countDescription);
               Spinner s1 = (Spinner) findViewById(R.id.timeChoice);
               SeekBar e2 = (SeekBar)findViewById(R.id.seekCount);


               if(checkedId == R.id.option1){
                   t1.setVisibility(View.INVISIBLE);
                   t2.setVisibility(View.INVISIBLE);
                   s1.setVisibility(View.INVISIBLE);
                   e2.setVisibility(View.INVISIBLE);
                   message = oneImage;
               }
               else if(checkedId == R.id.option2){
                   t1.setVisibility(View.VISIBLE);
                   t2.setVisibility(View.VISIBLE);
                   s1.setVisibility(View.VISIBLE);
                   e2.setVisibility(View.VISIBLE);
                   message = multipleImage;
               }
               else if(checkedId == R.id.option3){
                   t1.setVisibility(View.INVISIBLE);
                   t2.setVisibility(View.INVISIBLE);
                   s1.setVisibility(View.INVISIBLE);
                   e2.setVisibility(View.INVISIBLE);
                   message = shutdown;
               }
           }
       });

        final Button connect = (Button)findViewById(R.id.button);
        final TextView textview = (TextView)findViewById(R.id.textView);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
                    if (!mmSocket.isConnected()) {
                        connect.setText("Connecting... Please Wait...");
                        mmSocket.connect();
                        connect.setText("Connected");
                        (new Thread(new workerThread("hello"))).start();
                        uparrow.setVisibility(View.VISIBLE);
                        rb.setVisibility(View.VISIBLE);
                        textview.setVisibility(View.INVISIBLE);
                        connect.setVisibility(View.INVISIBLE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    connect.setText("Failed to Connect");
                    final TextView textMessage = (TextView)findViewById(R.id.textView);
                    textMessage.setText("Failed to connect");
                }
            }
        });

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("raspberrypi")) //Note, you will need to change this to match the name of your device
                {
                    mmDevice = device;
                    break;
                }
            }
        }
    }
}

