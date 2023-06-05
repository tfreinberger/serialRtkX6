package com.handheldgroup.nx6implementationdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ArrayAdapter;

import com.handheldgroup.nx6implementationdemo.databinding.ActivityMainBinding;
import com.handheldgroup.serialport.SerialPort;
import com.mmi.IMmiDevice;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    // Serial
    SerialPort serialPort;
    InputStream inputStream;
    OutputStream outputStream;
    ReadNmea readNmea;
    ArrayList<String> nmeaList;
    ArrayAdapter<String> nmeaAdapter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        binding.buttonPowerOn.setOnClickListener(v -> {
            int baudrate = Integer.parseInt(binding.editBaudRate.getText().toString());
            try {
                setPowerX6(true);
                serialPort = new SerialPort(new File("/dev/ttyHSL1"), baudrate, 0);
                inputStream = serialPort.getInputStream();
                outputStream = serialPort.getOutputStream();
                readNmea = new ReadNmea();
                readNmea.start();
                nmeaList = new ArrayList<>();
                nmeaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nmeaList);
                binding.listNmea.setAdapter(nmeaAdapter);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        binding.buttonPowerOff.setOnClickListener(v -> {
            if (readNmea != null) {
                readNmea.interrupt();
                nmeaList.clear();
                nmeaAdapter.notifyDataSetChanged();
            }
            try {
                outputStream.close();
                inputStream.close();
                serialPort.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    class ReadNmea extends Thread {

        byte[] buffer = new byte[4096];
        int index = 0;

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    int b = inputStream.read();
                    buffer[index] = (byte) b;
                    if (buffer[index] == '\n') {
                        String nmea = new String(buffer, 0, index, StandardCharsets.ISO_8859_1);
                        Arrays.fill(buffer, 0, index, (byte) 0);
                        index = 0;
                        runOnUiThread(() -> {
                            nmeaList.add(nmea);
                            nmeaAdapter.notifyDataSetChanged();
                        });
                    } else {
                        index++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setPowerX6(boolean powerOn) {
        try {
            @SuppressLint("PrivateApi")
            IBinder iBinder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "mmi_device");
            IMmiDevice mService = IMmiDevice.Stub.asInterface(iBinder);
            //noinspection ConstantConditions
            mService.writeForBackNode("/sys/class/ext_dev/function/ext_dev_5v_enable", powerOn ? "1" : "0");
            mService.writeForBackNode("/sys/class/ext_dev/function/pin10_en", powerOn ? "1" : "0");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}