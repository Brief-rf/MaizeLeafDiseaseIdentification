package com.brief.testtflite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressLint("StaticFieldLeak")
public class ImageUpload extends AsyncTask<String, Void, String> {
    private static final String GITHUB_TOKEN = "put your github token"; //
    private Context context;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public ImageUpload(Context context) {
        this.context = context;
    }
    @Override
    protected String doInBackground(String... params) {
        String user = params[0];
        String filename = params[1];
        String img_type = params[2];
        String imgInfo = params[3];
        try {
            OkHttpClient client = new OkHttpClient();
//            JSONObject json = new JSONObject();

            JSONObject data = new JSONObject();
            data.put("message", imgInfo);
            if (img_type == "base64") {
                data.put("content", filename);
                // 随机生成文件名
                filename = "img" + System.currentTimeMillis() + ".jpg";
            } else {
                File file = new File(filename);
                byte[] fileData = new byte[(int) file.length()];
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                bis.read(fileData, 0, fileData.length);
                data.put("content", Base64.encodeToString(fileData, Base64.DEFAULT));
                filename = "img" + System.currentTimeMillis() + filename.split("/")[filename.split("/").length - 1].replace(" ", "%20");
            }



            RequestBody body = RequestBody.create(JSON, data.toString());
            Request request = new Request.Builder()
                    .url("https://api.github.com/repos/" + user + "/CornDiseaseImagesStorage/contents/imgs/" + filename)
                    .put(body)
                    .addHeader("Authorization", "token " + GITHUB_TOKEN)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .build();
            Response response = client.newCall(request).execute();
//            Log.d("Response", Objects.requireNonNull(response.body()).string());
            if (response.isSuccessful()) {
                return "Successfully uploaded file.";
            } else {
                return "Failed to upload file: " + response.code() + " " + response.message();
            }
        } catch (Exception e) {
            return "Exception: " + e.getMessage();
        }
    }
    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        Toast.makeText(context, result, Toast.LENGTH_SHORT).show();
    }
}
