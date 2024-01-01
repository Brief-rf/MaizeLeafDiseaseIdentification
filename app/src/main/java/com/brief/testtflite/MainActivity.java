package com.brief.testtflite;

import android.annotation.SuppressLint;
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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.brief.testtflite.ml.Brief;

import com.flod.loadingbutton.LoadingButton;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

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

    private void apply_permission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    public static Bitmap zoomImg(Bitmap bm, int newWidth, int newHeight) {
        // 获得图片的宽高
        int width = bm.getWidth();
        int height = bm.getHeight();
        // 计算缩放比例
        float scaleWidth = (float) newWidth / width;
        float scaleHeight = (float) newHeight / height;
        // 取得想要缩放的matrix参数
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        // 得到新的图片
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
    }
    public static Bitmap zoomImg(String img_path, int newWidth, int newHeight) {
        // 获得图片的宽高
        Bitmap bm = BitmapFactory.decodeFile(img_path);
        return zoomImg(bm, newWidth, newHeight);
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            String picture_path = data.getData().toString();
            // 判断是否需要转换为绝对路径
            if (picture_path.startsWith("content://")) {
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = getContentResolver().query(Uri.parse(picture_path), filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                picture_path = cursor.getString(columnIndex);
                cursor.close();
            }
            bitmap = zoomImg(picture_path, 224, 224);
            imageView.setImageBitmap(bitmap);
        }
        else if(requestCode == 2 && resultCode == RESULT_OK){
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            bitmap = zoomImg(imageBitmap, 224, 224);
            imageView.setImageBitmap(bitmap);
        }
    }
    private void setView() {
        AssetManager assetManager = getAssets();
        try {
            InputStream inputStream = assetManager.open("test.jpg");
            bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
                    Toast.makeText(MainActivity.this, "请选择一张图片", Toast.LENGTH_SHORT).show();
                }
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
        // 获取中间feature map

        List<Category> probability= outputs.getProbabilityAsCategoryList();
        for (Category category : probability) {
            Log.d("TAG", "classifyImage: " + category.getLabel() + " " + category.getScore());
        }
        // 获取Score最大的类别
        Category maxCategory = probability.get(0);
        for (Category category : probability) {
            if (category.getScore() > maxCategory.getScore()) {
                maxCategory = category;
            }
        }
        result_show.setText(maxCategory.getLabel() + " " + maxCategory.getScore());
        upload.complete(true);
    }

//    public void classifyImageFeatureMap(Bitmap image) throws IOException {
//        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
//        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
//        byteBuffer.order(ByteOrder.nativeOrder());
//        int[] intValues = new int[imageSize * imageSize];
//        image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
//        int pixel = 0;
//        for(int i = 0; i < imageSize; i ++){
//            for(int j = 0; j < imageSize; j++){
//                int val = intValues[pixel++]; // RGB
//                byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255));
//                byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255));
//                byteBuffer.putFloat((val & 0xFF) * (1.f / 255));
//            }
//        }
//        inputFeature0.loadBuffer(byteBuffer);
//        FeatureMap featureMap = FeatureMap.newInstance(getApplicationContext());
//        FeatureMap.Outputs outputs = featureMap.process(inputFeature0);
//
//        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
//        TensorBuffer outputFeature1 = outputs.getOutputFeature1AsTensorBuffer();
//        TensorBuffer outputFeature2 = outputs.getOutputFeature2AsTensorBuffer();
//        TensorBuffer outputFeature3 = outputs.getOutputFeature3AsTensorBuffer();
//        TensorBuffer outputFeature4 = outputs.getOutputFeature4AsTensorBuffer();
//        TensorBuffer outputFeature5 = outputs.getOutputFeature5AsTensorBuffer();
//        TensorBuffer outputFeature6 = outputs.getOutputFeature6AsTensorBuffer();
//        TensorBuffer outputFeature7 = outputs.getOutputFeature7AsTensorBuffer();
//        TensorBuffer outputFeature8 = outputs.getOutputFeature8AsTensorBuffer();
//        TensorBuffer outputFeature9 = outputs.getOutputFeature9AsTensorBuffer();
//        TensorBuffer outputFeature10 = outputs.getOutputFeature10AsTensorBuffer();
//        TensorBuffer outputFeature11 = outputs.getOutputFeature11AsTensorBuffer();
//        // outputFeature0 概率输出
//        Log.d("TAG", "classifyImageFeatureMap: " + outputFeature0.getFloatArray()[0]);
//        tensorBufferToBitmap2(outputFeature1);
//        // 关闭模型
//        featureMap.close();
//    }

//    public void tensorBufferToBitmap2(TensorBuffer tensorBuffer) {
//        // tensorbuffer 有112个通道，每个通道的大小为7*7 遍历每个通道，将每个通道的数据转换为bitmap 其中的width和height需要转换为bitmap的height和width
//        // 获取TensorBuffer的形状和数据类型
//        int[] shape = tensorBuffer.getShape();
//        DataType dataType = tensorBuffer.getDataType();
//        // 获取TensorBuffer的数据
//        float[] data = tensorBuffer.getFloatArray();
//        // 获取通道数
//        int channel = shape[3];
//        // 获取每个通道的宽高
//        int width = shape[1];
//        int height = shape[2];
//        // 创建一个空的bitmap
//        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        // 遍历每个通道
//        for (int i = 0; i < channel; i++) {
//            // 获取每个通道的数据
//            float[] channelData = new float[width * height];
//            System.arraycopy(data, i * width * height, channelData, 0, width * height);
//            // 将每个通道的数据转换为bitmap
//            Bitmap channelBitmap = channelDataToBitmap(channelData, width, height);
//            // 将每个通道的bitmap合并到一起
//            bitmap = mergeBitmap(bitmap, channelBitmap);
//            // 将bitmap的width和height通道数据转换为bitmap的height和width
//            bitmap = Bitmap.createScaledBitmap(bitmap, height, width, true);
//        }
//
//    }
//
//    private Bitmap mergeBitmap(Bitmap bitmap, Bitmap channelBitmap) {
//        // 获取bitmap的宽高
//        int width = bitmap.getWidth();
//        int height = bitmap.getHeight();
//        // 创建一个空的bitmap
//        Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        // 遍历每个像素点
//        for (int i = 0; i < width; i++) {
//            for (int j = 0; j < height; j++) {
//                // 获取每个像素点的颜色
//                int color = bitmap.getPixel(i, j);
//                int channelColor = channelBitmap.getPixel(i, j);
//                // 将颜色设置到bitmap上
//                newBitmap.setPixel(i, j, Color.argb(255, Color.red(color), Color.green(color), Color.blue(channelColor)));
//            }
//        }
//        return newBitmap;
//    }
//
//    private Bitmap channelDataToBitmap(float[] channelData, int width, int height) {
//        // 创建一个空的bitmap
//        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        // 遍历每个像素点
//        for (int i = 0; i < width; i++) {
//            for (int j = 0; j < height; j++) {
//                // 获取每个像素点的数据
//                float data = channelData[i * height + j];
//                // 将数据转换为颜色
//                int color = (int) (data * 255);
//                // 将颜色设置到bitmap上
//                bitmap.setPixel(i, j, Color.argb(255, color, color, color));
//            }
//        }
//        return bitmap;
//
//
//    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        model.close();
    }
}