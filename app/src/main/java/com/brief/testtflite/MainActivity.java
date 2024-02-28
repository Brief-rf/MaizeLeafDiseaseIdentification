package com.brief.testtflite;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.brief.testtflite.ml.Brief;

import com.flod.loadingbutton.LoadingButton;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    int imageSize = 224;
    Brief model;
    TextView result_show;
    ImageView imageView;
    LoadingButton upload;
    Button take_pic;
    Button choose_pic;
    Bitmap bitmap;
    String img_path;
    String img_type;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            apply_permission();
            initView();
        } catch (IOException e) {
            e.printStackTrace();
        }
        setView();
    }
    // apply permission for storage access
    private void apply_permission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    public static Bitmap zoomImg(Bitmap bm, int newWidth, int newHeight) {
        // get the width and height of the image
        int width = bm.getWidth();
        int height = bm.getHeight();
        // calculate the scaling ratio
        float scaleWidth = (float) newWidth / width;
        float scaleHeight = (float) newHeight / height;
        // create a matrix for the manipulation
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        // recreate the new bitmap
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
    }
    public static Bitmap zoomImg(String img_path, int newWidth, int newHeight) {
        // get the width and height of the image
        Bitmap bm = BitmapFactory.decodeFile(img_path);
        return zoomImg(bm, newWidth, newHeight);
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            String picture_path = data.getData().toString();
            // get the path of the image
            if (picture_path.startsWith("content://")) {
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = getContentResolver().query(Uri.parse(picture_path), filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                picture_path = cursor.getString(columnIndex);
                cursor.close();
            }
            // resize the image to 224*224
            bitmap = zoomImg(picture_path, 224, 224);
            img_path = picture_path;
            img_type = "path";
            imageView.setImageBitmap(bitmap);
        }
        else if(requestCode == 2 && resultCode == RESULT_OK){
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            // change imageBitmap to base64
            bitmap = zoomImg(imageBitmap, 224, 224);
            img_path = bitmapToBase64(imageBitmap);
            img_type = "base64";
            imageView.setImageBitmap(bitmap);
        }
    }
    private void setView() {
        AssetManager assetManager = getAssets();
        try {
            // load the default image test.jpg
            InputStream inputStream = assetManager.open("test.jpg");
            bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // set the click event for the buttons
        choose_pic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, 1);
            }
        });
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, 1);
            }
        });
        take_pic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent take_pic = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(take_pic, 2);
            }
        });
        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                upload.start();
                upload.setEnableRestore(true);
                if (bitmap != null) {
                    classifyImage(bitmap);
                }
                else {
                    Toast.makeText(MainActivity.this, R.string.null_pic_show, Toast.LENGTH_SHORT).show();
                }
            }
        });
        upload.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                new ImageUpload(MainActivity.this).execute("Brief-rf", img_path, img_type, "feedback");
                return true;
            }
        });
    }
    private void initView() throws IOException {
        result_show = findViewById(R.id.result_show);
        imageView = findViewById(R.id.imageView);
        upload = findViewById(R.id.upload);
        upload.setShrinkDuration(60);
        upload.setTextColor(Color.BLACK);
        take_pic = findViewById(R.id.take_picture);
        choose_pic = findViewById(R.id.choose_picture);
        model = Brief.newInstance(getApplicationContext());
    }
    @SuppressLint("SetTextI18n")
    public void classifyImage(Bitmap image){
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[imageSize * imageSize];
        image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        int pixel = 0;
        for(int i = 0; i < imageSize; i ++){
            for(int j = 0; j < imageSize; j++){
                int val = intValues[pixel++]; // RGB
                byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255));
                byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255));
                byteBuffer.putFloat((val & 0xFF) * (1.f / 255));
            }
        }
        inputFeature0.loadBuffer(byteBuffer);
        Brief.Outputs outputs = model.process(inputFeature0);
        // get the result
        List<Category> probability= outputs.getProbabilityAsCategoryList();
        for (Category category : probability) {
            Log.d("TAG", "classifyImage: " + category.getLabel() + " " + category.getScore());
        }
        // get the maximum probability
        Category maxCategory = probability.get(0);
        for (Category category : probability) {
            if (category.getScore() > maxCategory.getScore()) {
                maxCategory = category;
            }
        }
        // judge the maximum probability with 0.8
        if (maxCategory.getScore() < 0.99) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle(R.string.low_confidence_tip).
                    setMessage(R.string.low_confidence_show).
                    setPositiveButton(R.string.feedback_pic, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    new ImageUpload(MainActivity.this).execute("Brief-rf", img_path, img_type, "feedback");
                                }
                            }).
                    setNegativeButton(R.string.cancel_feedback_btn, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                    Toast.makeText(MainActivity.this, R.string.cancel_feedback, Toast.LENGTH_SHORT).show();
                                }
                            });
            builder.create().show();

            result_show.setText(R.string.cant_identify);
            upload.complete(true);
            return;
        }
        result_show.setText(maxCategory.getLabel() + " " + maxCategory.getScore());
        upload.complete(true);
    }
    public String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream); // 可以根据需要选择压缩格式和质量
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        model.close();
    }
}