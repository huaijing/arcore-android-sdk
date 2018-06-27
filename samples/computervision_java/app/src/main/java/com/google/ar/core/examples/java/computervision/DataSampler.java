package com.google.ar.core.examples.java.computervision;

import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;

/**
 * Created by admin on 2017/6/13.
 */

public class DataSampler {
    ///storage/emulated/0/IMUSampler/data/
    public final String IMUSAMPLER_DATA_DIR="/sdcard/IMUSampler";

    public String genCurSamplerDir(){
        SimpleDateFormat sDateFormat =  new  SimpleDateFormat("yyyy_MM_dd_hh_mm_ss");
        String d = sDateFormat.format(new java.util.Date());
        String fold = IMUSAMPLER_DATA_DIR +'/' + Build.MODEL.replace(" ", "_") + "_" + d + '/';
        boolean flag = createSDCardCustomDir(fold);

        return fold ;
    }

    public String getDeviceManufacturer(){
        return Build.MANUFACTURER;
    }

    public String getDeviceModel(){
        return Build.MODEL;
    }

    public String getDeviceRelease(){
        return Build.VERSION.RELEASE;
    }


    //storage
    public boolean isSDCardMounted() {
        // return Environment.getExternalStorageState().equals("mounted");
        return Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
    }
    //get root dir
    public String getSDCardBaseDir() {
        if (isSDCardMounted()) {
            return Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        return null;
    }

    public boolean createSDCardCustomDir(String dir) {
        if (isSDCardMounted()) {
            File file = new File(dir);
            if (!file.exists()) {
                return file.mkdirs();
            }
        }
        return false;
    }
}
