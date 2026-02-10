package com.example.trafficsign.data;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.trafficsign.model.DetectionBox;
import com.example.trafficsign.model.HistoryItem;

import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryRepository {

    private final Context ctx;
    private final Gson gson = new Gson();

    public HistoryRepository(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    private File historyDir() {
        File dir = new File(ctx.getFilesDir(), "history");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File indexFile() { return new File(historyDir(), "index.json"); }

    public List<HistoryItem> loadAll() {
        File f = indexFile();
        if (!f.exists()) return new ArrayList<>();
        try (Reader r = new FileReader(f)) {
            Type t = new TypeToken<List<HistoryItem>>(){}.getType();
            List<HistoryItem> list = gson.fromJson(r, t);
            return (list != null) ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveAll(List<HistoryItem> list) {
        try (Writer w = new FileWriter(indexFile(), false)) {
            gson.toJson(list, w);
        } catch (Exception ignored) {}
    }

    public void saveHistoryFromUri(Uri uri, DetectionBox[] boxes) {
        Bitmap bmp = decodeUriToBitmap(ctx, uri, 1600);
        if (bmp == null) return;

        String id = UUID.randomUUID().toString();
        File imgFile = new File(historyDir(), id + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(imgFile)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
        } catch (Exception ignored) {}

        HistoryItem item = new HistoryItem();
        item.id = id;
        item.timestamp = System.currentTimeMillis();
        item.imagePath = imgFile.getAbsolutePath();
        item.boxesJson = gson.toJson(boxes);

        List<HistoryItem> list = loadAll();
        list.add(0, item); // 最新在前
        saveAll(list);
    }

    public static Bitmap decodeUriToBitmap(Context ctx, Uri uri, int maxSize) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            try (InputStream is = cr.openInputStream(uri)) {
                if (is == null) return null;
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, opt);
                int w = opt.outWidth, h = opt.outHeight;

                int inSample = 1;
                while (w / inSample > maxSize || h / inSample > maxSize) inSample *= 2;

                BitmapFactory.Options opt2 = new BitmapFactory.Options();
                opt2.inSampleSize = inSample;

                try (InputStream is2 = cr.openInputStream(uri)) {
                    if (is2 == null) return null;
                    return BitmapFactory.decodeStream(is2, null, opt2);
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    public String exportJsonString() {
        return gson.toJson(loadAll());
    }

    public String exportCsvString() {
        List<HistoryItem> list = loadAll();
        StringBuilder sb = new StringBuilder();
        sb.append("id,timestamp,imagePath,boxesJson\n");
        for (HistoryItem it : list) {
            sb.append(safe(it.id)).append(",");
            sb.append(it.timestamp).append(",");
            sb.append(safe(it.imagePath)).append(",");
            sb.append(safe(it.boxesJson)).append("\n");
        }
        return sb.toString();
    }

    private String safe(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
