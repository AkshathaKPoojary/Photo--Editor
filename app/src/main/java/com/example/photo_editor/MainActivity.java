package com.example.photo_editor;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }
    private static final int REQUEST_PERMISSIONS=1234;
    private static final String[] PERMISSIONS={
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int PERMISSIONS_COUNT=2;
    private boolean notPermissions(){
        for(int i=0; i<PERMISSIONS_COUNT; i++){
            if (checkSelfPermission(PERMISSIONS[i])!=PackageManager.PERMISSION_GRANTED){
                return true;
            }
        }
        return false;
    }

    static {
        System.loadLibrary("photoEditor");
    }
    private static native void blackAndWhite(int[] pixels,int width, int height);

    @Override
    protected void onResume(){
        super.onResume();
        if(notPermissions()){
            requestPermissions(PERMISSIONS,REQUEST_PERMISSIONS);
        }
    }
    //@Override
//    public  void onRequestPermissionResult(int requestCode, String[] permissions, int[]grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == REQUEST_PERMISSIONS && grantResults.length > 0) {
//            if (notPermissions()) {
//                ((ActivityManager) this.getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();
//                recreate();
//            }
//        }
//    }
    public  void onBackPressed(){
        if(editMode){
            findViewById(R.id.editScreen).setVisibility(View.GONE);
            findViewById(R.id.welcomeScreen).setVisibility(View.VISIBLE);
            editMode=false;
        }else{
            super.onBackPressed();
        }

    }

    private static final int REQUEST_PICK_IMAGE=12345;

    private ImageView imageView;
    @SuppressLint("QueryPermissionsNeeded")
    private void init() {
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());


        imageView = findViewById(R.id.imageView);

        if (!MainActivity.this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            findViewById(R.id.takePhotoButton).setVisibility(View.GONE);
        }

        final Button selectImageButton = findViewById(R.id.selectImageButton);
        selectImageButton.setOnClickListener(view -> {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            final Intent pickIntent = new Intent(Intent.ACTION_PICK);
            pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            final Intent chooserIntent = Intent.createChooser(intent, "Select Image");
            startActivityForResult(chooserIntent, REQUEST_PICK_IMAGE);
        });

        final Button takePhotoButton = findViewById(R.id.takePhotoButton);
        takePhotoButton.setOnClickListener(view -> {
            final Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                //create a file for the photo that was just taken
                final File photoFile = createImageFile();
                imageUri = Uri.fromFile(photoFile);
                final SharedPreferences myPrefs = getSharedPreferences(appID, 0);
                myPrefs.edit().putString("path", photoFile.getAbsolutePath()).apply();
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        });

        final Button blackAndWhiteButton = findViewById(R.id.blackAndWhite);
        blackAndWhiteButton.setOnClickListener(view -> new Thread(() -> {
            blackAndWhite(pixels, width, height);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            runOnUiThread(() -> imageView.setImageBitmap(bitmap));
        }).start());

        final Button saveImageButton = findViewById(R.id.saveImage);
        saveImageButton.setOnClickListener(view -> {
            final AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogTheme);
            final DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    final File outFile = createImageFile();
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        Intent saveImage =new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                        imageUri = Uri.parse("file://" + outFile.getAbsolutePath());
                        sendBroadcast(saveImage);
                        Toast.makeText(MainActivity.this, "Image was saved", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            };
            builder1.setMessage("Save current photo to gallery?").
                    setPositiveButton("yes", dialogClickListener).
                    setNegativeButton("no", dialogClickListener).show();
        });

        final Button backButton=findViewById(R.id.back);
        backButton.setOnClickListener(view -> {
            findViewById(R.id.editScreen).setVisibility(View.GONE);
            findViewById(R.id.welcomeScreen).setVisibility(View.VISIBLE);
            editMode=false;
        });
    }

    private static final int REQUEST_IMAGE_CAPTURE=1012;

    private static final String appID="photoEditor";

    private Uri imageUri;

    private File createImageFile(){
        final String timestamp= new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        final String imageFileName="/JPEG_" +timestamp+".jpg";
        final File storageDir= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return  new File(storageDir+ imageFileName);
    }

    private boolean editMode=false;
    private boolean nestEdit=false;
    private Bitmap bitmap;
    private int width=0;
    private int height=0;
    private static final int MAX_PIXEL_COUNT=2048;

    private int[] pixels;
    private int pixelCount=0;

    @Override
    public void onActivityResult(int requestCode, int resultCode,Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode!= RESULT_OK){
            return;
        }
        if(requestCode==REQUEST_IMAGE_CAPTURE){
            if(imageUri==null){
                final SharedPreferences p=getSharedPreferences("appID",0);
                final String path= p.getString("path","");
                if(path.length()<1){
                    recreate();
                    return;
                }
                imageUri=Uri.parse("file://"+path);
            }
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imageUri));

        }else if(data==null){
            recreate();
            return;
        }else if(requestCode==REQUEST_PICK_IMAGE){
            imageUri=data.getData();
        }
        final ProgressDialog dialog= ProgressDialog.show(MainActivity.this,"Loading","Please wait",true);
        editMode=true;
        findViewById(R.id.welcomeScreen).setVisibility(View.GONE);
        findViewById(R.id.editScreen).setVisibility(View.VISIBLE);

        new Thread(() -> {
            bitmap = null;
            final BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
            bmpOptions.inBitmap = bitmap;
            bmpOptions.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(imageUri)) {
                bitmap = BitmapFactory.decodeStream(input, null, bmpOptions);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bmpOptions.inJustDecodeBounds = false;
            width = bmpOptions.outWidth;
            height = bmpOptions.outHeight;
            int resizeScale = 1;
            if (width > MAX_PIXEL_COUNT) {
                resizeScale = width / MAX_PIXEL_COUNT;
            } else if (height > MAX_PIXEL_COUNT) {
                resizeScale = height / MAX_PIXEL_COUNT;
            }
            if(width/resizeScale>MAX_PIXEL_COUNT || height/resizeScale>MAX_PIXEL_COUNT){
                resizeScale++;
            }
            bmpOptions.inSampleSize=resizeScale;
            InputStream input;
            try {
                input=getContentResolver().openInputStream(imageUri);
            }
            catch (FileNotFoundException e){
                e.printStackTrace();
                recreate();
                return;
            }
            bitmap=BitmapFactory.decodeStream(input, null, bmpOptions);
            runOnUiThread(() -> {
                imageView.setImageBitmap(bitmap);
                dialog.cancel();
            });
            width=bitmap.getWidth();
            height=bitmap.getHeight();
            bitmap=bitmap.copy(Bitmap.Config.ARGB_8888, true);

            pixelCount=width*height;
            pixels=new int[pixelCount];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        }).start();
    }
}