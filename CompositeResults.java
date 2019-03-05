package edu.ggc.lutz.pixabay;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.LongSparseArray;

import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import edu.ggc.lutz.pixabay.json.Hit;
import edu.ggc.lutz.pixabay.json.PixabayHttpResponse;

public class CompositeResults {

    static final String TAG = BuildConfig.TAG;
    private long DAY_MILLIS;

    private long created;
    private PixabayHttpResponse response; // taken from jsonschema2pojo without mod

    private LongSparseArray<Bitmap> bitmaps;
    private LongSparseArray<List<EntityAnnotation>> labels;
    private Activity activity;

    public LongSparseArray<Bitmap> getBitmaps() { return bitmaps; }
    public LongSparseArray<List<EntityAnnotation>> getLabels() { return labels; }

    public CompositeResults(String json, Activity activity) throws IOException {
        DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
        this.labels = new LongSparseArray<>();
        this.bitmaps = new LongSparseArray<>();
        this.response = new Gson().fromJson(json, PixabayHttpResponse.class);
        this.created = System.currentTimeMillis(); // birthday!
        this.activity = activity;
    }

    boolean isExpired() { return created - System.currentTimeMillis() > DAY_MILLIS; }
    long size() { return response.getHits().size(); }

    Hit getHit(int index) {
        return response.getHits().get(index);
    }
}