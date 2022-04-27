package com.chs.adherencemonitor.util;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.abedelazizshe.lightcompressorlibrary.CompressionListener;
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor;
import com.abedelazizshe.lightcompressorlibrary.VideoQuality;
import com.chs.adherencemonitor.R;
import com.chs.adherencemonitor.SessionManager;
import com.chs.adherencemonitor.activity.LoginActivity;
import com.chs.adherencemonitor.activity.PatientHomepageActivity;
import com.chs.adherencemonitor.dot.DotRepository;
import com.chs.adherencemonitor.model.OfflineDot;
import com.chs.adherencemonitor.model.Patient;
import com.chs.adherencemonitor.model.User;
import com.chs.adherencemonitor.retrofit.AuthRetrofitApiClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;

/**
 * @author kimjose
 * This class has various frequently-used methods
 */
public class Utility {
    private final Context context;
    public boolean authenticated = true;
    private static final String TAG = "Utility";
    public static final String UUID = "{055EE038-DAAE-47C4-910D-783A7AA8DFBD}";
    public static final String BASE_URL = "http://172.16.0.7:83/monitor-api/";
//    public static final String BASE_URL = "https://nimeconfirm.chskenya.org/";
    public static final int MIN_PASSWORD_LENGTH = 4;
    private static final String MY_PREFS_NAME = "AdherencePrefs";

    private final ProgressDialog progressDialog;
    private final DotRepository dotRepository;

    public static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9.%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}");

    private final SimpleDateFormat sdf;

    boolean append = true;
    private BufferedWriter writer;

    public Utility(Context context) {
        this.context = context;
        dotRepository = new DotRepository(context);
        progressDialog = new ProgressDialog(context);
        progressDialog.setCancelable(false);//you can cancel it by pressing back button
        progressDialog.setTitle(context.getResources().getString(R.string.uploading_files));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        //progressDialog.setProgress(0);//initially progress is 0

        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Constants constants = new Constants(context);
        File logPath = constants.ERROR_LOG_PATH;
        try {
            writer = new BufferedWriter(new FileWriter(logPath, append));
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }
    }


    public void writeErrorLog(String TAG, String error) {
        try {
            Date errortime = Calendar.getInstance().getTime();
            writer.newLine();
            writer.write(sdf.format(errortime) + " " + TAG + error);
            writer.close();
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public ProgressDialog getProgressDialog() {
        return progressDialog;
    }

    public Map<String, String> uploadFile(@NonNull String filePath, String uploadUrl, boolean updateUi) {
        Log.i(TAG, "uploadFile: Path " + filePath);
        progressDialog.setTitle(context.getResources().getString(R.string.uploading_files));
        progressDialog.setMessage(context.getResources().getString(R.string.uploading_files_content));

        if (updateUi) ((Activity) context).runOnUiThread(progressDialog::show);

        Map<String, String> response = new LinkedHashMap<>();

        HttpsURLConnection conn;
        DataOutputStream dos;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize, serverResponseCode;
        byte[] buffer;
        int maxBufferSize = 2 * 1024;
        File videoFile = new File(filePath);


        try { // open a URL connection to the Servlet
            if (!videoFile.isFile()) {
                Log.e(TAG, "video File Does not exist");
                writeErrorLog(TAG, ": Video file not found");

                throw new Exception("File not found");
            }
            FileInputStream fileInputStream = new FileInputStream(videoFile);
            bytesAvailable = fileInputStream.available(); // create a buffer of  maximum size
            Log.i(TAG, "Initial .available : " + bytesAvailable);

            URL url = new URL(uploadUrl);
            conn = (HttpsURLConnection) url.openConnection(); // Open a HTTP  connection to  the URL
            conn.setDoInput(true); // Allow Inputs
            conn.setDoOutput(true); // Allow Outputs
            conn.setUseCaches(false); // Don't use a Cached Copy
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("UUID", Utility.UUID);
            conn.setRequestProperty("TOKEN", SessionManager.INSTANCE.getUser().getToken());
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            conn.setRequestProperty("upload_file", videoFile.getAbsolutePath());
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible) ");
            conn.setRequestProperty("Accept", "*/*");
            conn.setChunkedStreamingMode(100);
            dos = new DataOutputStream(conn.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"upload_file\";filename=\"" + videoFile.getAbsolutePath() + "\"" + lineEnd);
            dos.writeBytes(lineEnd);


            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // read file and write it into form...
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            int uploaded = 0;
            progressDialog.setMax(bytesAvailable);//sets the maximum value 100

            while (bytesRead > 0) {
                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                uploaded += bytesRead;
                progressDialog.setProgress(uploaded);
            }

            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            dos.flush();
            Log.i(TAG, "uploadFile: conn " + conn.getHeaderFields());
            // Responses from the server (code and message)
            serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();

            Log.i(TAG, "HTTP Response is : " + serverResponseMessage + ": " + serverResponseCode);
            Log.i(TAG, filePath + " File is written");

            Log.i(TAG, "submit: " + serverResponseCode);
            if (serverResponseCode == HttpsURLConnection.HTTP_OK) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    Log.i(TAG, "RES Message: " + line);
                    sb.append(line);
                }

                rd.close();
                response.put("code", String.valueOf(serverResponseCode));
                response.put("message", "file uploaded successfully");
                response.put("extras", sb.toString());
            } else if (serverResponseCode == Constants.UNAUTHORIZED_RESPONSE_CODE) {
                response.put("code", String.valueOf(serverResponseCode));
                response.put("message", "Session expired");
            } else throw new Exception("Could not upload file" + serverResponseCode);
            fileInputStream.close();
            dos.close();
            conn.disconnect();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
            writeErrorLog(TAG, ": " + ex.getMessage());

            Log.e("Upload file to server", "error: " + ex.getMessage(), ex);
            response.put("code", "-1");
            response.put("message", "file upload failed");
            response.put("extras", ex.getMessage());
        } catch (Exception e) {
            e.getMessage();
            Log.e("Upload file to server", "error: " + e.getMessage(), e);
            writeErrorLog(TAG, ": " + e.getMessage());

            response.put("code", "-1");
            response.put("message", "file upload failed");
            response.put("extras", e.getMessage());
        }

        if (updateUi) progressDialog.dismiss();
        return response;
    }

    /**
     * This method saves a DOT to room db
     *
     * @param dot The DOT to be saved.
     */
    public void insertOffline(OfflineDot dot) {
        Thread thread = new Thread(() -> dotRepository.insert(dot));
        Executor executor = Executors.newCachedThreadPool();
        executor.execute(thread);
    }

    /**
     * This method uploads DOTs saved offline to the server
     * NB: This method should not be called by the UI thread
     *
     * @param dot The dot to be uploaded.
     */
    private void uploadOfflineDot(OfflineDot dot) {

        Log.i(TAG, "uploadOfflineDot: uploading " + dot.getVideoUrl());

        String videoUpload = BASE_URL + "uploadVideo";

        if (dot.getVideoUrl() != null && !dot.getVideoUrl().equals("")) {
            File videoFile = new File(dot.getVideoUrl());
            if (!videoFile.isFile()){
                Log.i(TAG, "uploadOfflineDot: Is Not A file....Deleting");
                Executor executor = Executors.newCachedThreadPool();
                executor.execute(() -> dotRepository.delete(dot));
                checkOffline();
                return;
            }
            Log.i(TAG, "uploadOfflineDot: Is file....Now Uploading...");
            Map<String, String> videoResponse = uploadFile(dot.getVideoUrl(), videoUpload, false);
            int videoCode = Integer.parseInt(videoResponse.get("code"));
            String videoMessage = videoResponse.get("message");
            String videoExtras = videoResponse.get("extras");

            if (videoCode == HttpURLConnection.HTTP_OK) {
                AuthRetrofitApiClient.getInstance(context)
                        .getAuthorizedApi()
                        .uploadDot(dot.getCccNo(), SessionManager.INSTANCE.getUser().getId(), dot.getRegimen(),
                                dot.getLatitude(), dot.getLongitude(), dot.getStartTime(), dot.getEndTime(), videoExtras, "", dot.getComment())
                        .enqueue(new Callback<Patient>() {
                            @Override
                            public void onResponse(@NotNull Call<Patient> call, @NotNull Response<Patient> response) {
                                if (response.isSuccessful()) {
                                    deleteFile(dot.getAudioUrl());
                                    deleteFile(dot.getVideoUrl());
                                    Executor executor = Executors.newCachedThreadPool();
                                    executor.execute(() -> dotRepository.delete(dot));
                                    checkOffline();
                                } else if(response.code() == Constants.UNAUTHORIZED_RESPONSE_CODE) {
                                    refreshToken(dialog -> {
                                        if (!authenticated) {
                                            context.startActivity(new Intent(context, LoginActivity.class));
                                            ((AppCompatActivity)context).finish();
                                        } else checkOffline();
                                    });
                                } else {
                                    Log.d(TAG, "onResponse: " + response.message());
                                    writeErrorLog(TAG, ": onResponse: " + response.message());
                                }
                            }

                            @Override
                            public void onFailure(@NotNull Call<Patient> call, @NotNull Throwable t) {
                                Log.d(TAG, "onFailure: " + t.getMessage());
                                writeErrorLog(TAG, ": onFailure: " + t.getMessage());

                            }
                        });
            } else {
                Log.d(TAG, "submit: video upload failed: " + videoMessage);
            }
        } else {
            AuthRetrofitApiClient.getInstance(context)
                    .getAuthorizedApi()
                    .uploadDot(dot.getCccNo(), SessionManager.INSTANCE.getUser().getId(), dot.getRegimen(),
                            dot.getLatitude(), dot.getLongitude(), dot.getStartTime(), dot.getEndTime(), "", "", dot.getComment())
                    .enqueue(new Callback<Patient>() {
                        @Override
                        public void onResponse(@NotNull Call<Patient> call, @NotNull Response<Patient> response) {
                            if (response.isSuccessful()) {
                                deleteFile(dot.getAudioUrl());
                                Executor executor = Executors.newCachedThreadPool();
                                executor.execute(() -> dotRepository.delete(dot));
                            } else {
                                Log.d(TAG, "onResponse: " + response.message());
                                writeErrorLog(TAG, ": onResponse: " + response.message());

                            }
                        }

                        @Override
                        public void onFailure(@NotNull Call<Patient> call, @NotNull Throwable t) {
                            Log.d(TAG, "onFailure: " + t.getMessage());
                            writeErrorLog(TAG, ": onFailure: " + t.getMessage());

                        }
                    });
        }

    }

    /**
     * @deprecated
     */
    private void deleteOfflineDot(OfflineDot dot) {
        Executor executor = Executors.newCachedThreadPool();
        Thread thread = new Thread(() -> dotRepository.delete(dot));
        executor.execute(thread);
    }

    public void checkOffline() {
        Executor executor = Executors.newCachedThreadPool();
        executor.execute(() -> {
            OfflineDot[] dots = dotRepository.getAll();
            Log.i(TAG, "checkOffline: dots0: " + new Gson().toJson(dots, OfflineDot[].class));
            if (dots.length > 0) {
                uploadOfflineDot(dots[0]);
            }
            Log.i(TAG, "checkOffline: dots: " + new Gson().toJson(dots, OfflineDot[].class));
        });
    }

    public void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            if (file.delete()) {
                Log.i(TAG, "deleteFile: file has been deleted.");
            } else {
                Log.d(TAG, "deleteFile: Failed to delete file.");
            }
        }
    }

    public boolean checkInternetState() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public void updateToken(String token) {
        SharedPreferences pref = context.getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor tokeneditor = pref.edit();
        tokeneditor.putString("token", token);
        tokeneditor.apply();
    }

    public void compressVideo(String src, String dest) {
        VideoCompressor.start(src, dest, new CompressionListener() {
            @Override
            public void onStart() {
                Log.i(TAG, "onStart: ");
                progressDialog.setTitle(context.getResources().getString(R.string.video_compress_title));
                progressDialog.setMessage(context.getResources().getString(R.string.please_wait));
                progressDialog.setMax(100);
                ((Activity) context).runOnUiThread(progressDialog::show);
            }

            @Override
            public void onSuccess() {
                Log.i(TAG, "onSuccess: ");
                synchronized (this) {
                    this.notifyAll();
                }
                ((Activity) context).runOnUiThread(() -> {
                    progressDialog.dismiss();
                    ((PatientHomepageActivity) context).showSheet(dest);//Hahahaha
                });
                deleteFile(src);
//                fragment.uploadFiles(dest);
            }

            @Override
            public void onFailure(@NotNull String s) {
                Log.i(TAG, "onFailure: " + s);
                synchronized (this) {
                    this.notifyAll();
                }
                deleteFile(src);
                ((Activity) context).runOnUiThread(progressDialog::dismiss);
            }

            @Override
            public void onProgress(float v) {
                progressDialog.setProgress((int) v);
            }

            @Override
            public void onCancelled() {
                Log.i(TAG, "onCancelled: ");
                synchronized (this) {
                    this.notifyAll();
                }
                ((Activity) context).runOnUiThread(progressDialog::dismiss);
                deleteFile(src);
            }
        }, VideoQuality.LOW, false);

    }

    public void refreshToken(DialogInterface.OnDismissListener dismissListener) {
        authenticated = false;
        ((AppCompatActivity) context).runOnUiThread(()->{

            Dialog d = new Dialog(context);
            d.setContentView(R.layout.dialog_refresh_token);
            d.setCancelable(false);
            d.setCanceledOnTouchOutside(false);
            d.setOnDismissListener(dismissListener);
            TextInputEditText passwordInput = d.findViewById(R.id.passwordInput);
            TextView tvInfo = d.findViewById(R.id.tvInfo);
            TextView tvRefreshTokenInfo = d.findViewById(R.id.tvRefreshTokenInfo);
            if (SessionManager.INSTANCE.getUser() != null) tvRefreshTokenInfo.setText(context.getString(R.string.expired_session_info, SessionManager.INSTANCE.getUser().getMobile()));
            Button btnCancel = d.findViewById(R.id.btnCancel);
            Button btnRefreshToken = d.findViewById(R.id.btnRefreshToken);
            btnCancel.setOnClickListener(view -> {
                context.startActivity(new Intent(context, LoginActivity.class));
                ((AppCompatActivity) context).finish();
            });
            btnRefreshToken.setOnClickListener(view -> {
                String password = passwordInput.getText().toString().trim();
                if (password.length() < MIN_PASSWORD_LENGTH) {
                    passwordInput.setError(context.getResources().getString(R.string.password_error));
                } else {
                    tvInfo.setVisibility(View.VISIBLE);
                    tvInfo.setText(R.string.authenticating);
                    AuthRetrofitApiClient.getInstance(context)
                            .getAuthorizedApi()
                            .refreshToken(password)
                            .enqueue(new Callback<User>() {
                                @Override
                                public void onResponse(Call<User> call, Response<User> response) {
                                    if (response.isSuccessful()) {
                                        SessionManager.INSTANCE.setUser(response.body());
                                        updateToken(response.body().getToken());
                                        authenticated = true;
                                        AuthRetrofitApiClient.resetRetrofitClient();
                                        ((AppCompatActivity) context).runOnUiThread(d::dismiss);
                                    } else {
                                        ((AppCompatActivity) context).runOnUiThread(() -> {
                                            tvInfo.setText(R.string.authenticating_error);
                                        });
                                    }
                                }

                                @Override
                                public void onFailure(Call<User> call, Throwable t) {
                                    writeErrorLog(TAG, t.getMessage());
                                    tvInfo.setText(R.string.authenticating_error);
                                }
                            });
                }
            });
            ((AppCompatActivity) context).runOnUiThread(d::show);
        });
    }
}
