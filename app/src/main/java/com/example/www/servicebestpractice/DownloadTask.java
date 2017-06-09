package com.example.www.servicebestpractice;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Administrator on 2017/6/8.
 */

public class DownloadTask extends AsyncTask<String,Integer,Integer> {

    private static final String TAG = "DownloadTask";
    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;

    private DownloadListener downloadListener;

    private boolean isCanceled;
    private boolean isPaused;

    private int lastProgress;

    public DownloadTask(DownloadListener listener){
        this.downloadListener = listener;
    }
    @Override
    protected Integer doInBackground(String... params) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;

        long downloadedLength = 0; //记录已下载的文件长度
        String downloadUrl = params[0]; //获得下载链接
        String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/")); //获取下载文件名
        String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();  //获取下载文件保存路径 存储卡的downlaods目录
        file = new File(directory+fileName);
        Log.d(TAG, "文件保存路径："+file.getAbsoluteFile());
        if(file.exists()){
            downloadedLength = file.length();
            Log.d(TAG, "downloadedLength:"+downloadedLength);
        }
        try {
            long contentLength = getContentLength(downloadUrl);  //获取下载文件总长度
            Log.d(TAG, "文件总长(字节)："+contentLength);
            if(contentLength == 0){
                return TYPE_FAILED;
            }else if(contentLength == downloadedLength){
                return TYPE_SUCCESS;
            }
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    //断点下载，指定从哪个字节开始下载
                    .addHeader("RANGE","bytes="+downloadedLength+"-")
                    .url(downloadUrl)
                    .build();
            Response response = client.newCall(request).execute();
            Log.d(TAG, "response:"+response);
            Log.d(TAG, "size:"+response.body().contentLength());
            if(response != null && response.isSuccessful()){
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file,"rw");
                savedFile.seek(downloadedLength);     //跳过已下载的字节，进行写入
                byte[] b = new byte[1024];
                int total = 0;
                int len;
                while ((len = is.read(b)) != -1){
                    if(isCanceled){
                        return TYPE_CANCELED;
                    }else if(isPaused){
                        return TYPE_PAUSED;
                    }else{
                        total += len;
                        savedFile.write(b,0,len);
                        //计算下载的百分比
                        int progress = (int)((total+downloadedLength)*100/contentLength);
                        //通知界面更新下载的百分比
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if(is != null){
                    is.close();
                }
                if(savedFile != null){
                    savedFile.close();
                }
                if(isCanceled && file != null){
                    file.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;
    }

    /**
     * 获取下载文件的大小
     * @param downloadUrl
     * @return
     * @throws IOException
     */
    private long getContentLength(String downloadUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(downloadUrl).build();
        Response response = client.newCall(request).execute();
        if(response != null && response.isSuccessful()){
            long contentLength = response.body().contentLength();
            response.close();
            return contentLength;
        }
        return 0;
    }
    /**
     * 在界面上更新下载进度
     * @param values
     */
    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        if(progress > lastProgress){
            downloadListener.onProgress(progress);
            lastProgress = progress;
        }
    }

    /**
     * 通知最终的下载结果
     * @param status
     */
    @Override
    protected void onPostExecute(Integer status) {
        switch (status){
            case TYPE_SUCCESS :
                downloadListener.onSuccess();
                break;
            case TYPE_FAILED :
                downloadListener.onFailed();
                break;
            case TYPE_PAUSED :
                downloadListener.onPaused();
                break;
            case TYPE_CANCELED :
                downloadListener.onCanceled();
                break;
            default:
        }
    }

    /**
     * 暂停下载
     */
    public void pauseDownload(){
        isPaused = true;
    }

    /**
     * 取消下载
     */
    public void cancelDownload(){
        isCanceled = true;
    }
}
