package edu.ggc.lutz.pixabay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.api.services.vision.v1.model.EntityAnnotation;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import edu.ggc.lutz.pixabay.cloudvision.LabelDetectionTask;
import edu.ggc.lutz.pixabay.json.Hit;
import edu.ggc.lutz.pixabay.pixabay_service.PixabayBitmapFetchTask;
import edu.ggc.lutz.pixabay.pixabay_service.PixabayFetchTask;

import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity implements OnTaskCompleted {

    static final String TAG = BuildConfig.TAG;

    private ImageView image;
    private TextView tvTags;
    private TextView tvLabels;
    private SharedPreferences prefs;
    private boolean showTags;
    private boolean showLabels;
    private CompositeResults compositeResults;
    private int currentPick;
    private TextToSpeech mySpeech;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        image = findViewById(R.id.ivPhoto);
        image.setImageResource(R.drawable.mycamera1);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tvTags = findViewById(R.id.tvTags);
        tvLabels = findViewById(R.id.tvLabels);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                boolean showLabels = prefs.getBoolean("cloud_vision_labels",true);
                boolean showTags = prefs.getBoolean("pixabay_tags",true);
            }
        });

        compositeResults = null;


        PixabayFetchTask fetch = new PixabayFetchTask(MainActivity.this, compositeResults);
        fetch.execute();


        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setImageResource(R.drawable.refreshicon);
        fab.setOnClickListener(view -> { stageNewImage(); });

        PreferenceManager.setDefaultValues(this, R.xml.app_preferences, false);


    }


    void stageNewImage() {
        tvTags.setText(R.string.pixabay_tag_na);
        tvLabels.setText(R.string.cloud_vision_labels_na);
        image.setImageResource(R.drawable.mycamera1);

        if (compositeResults == null) return;

        currentPick = (int) getRandomLong(0L, compositeResults.size());
        final Hit hit = compositeResults.getHit(currentPick);
        Log.i(TAG, hit.getWebformatURL());
        final long id = hit.getId(); // convert response's index to Pixabay's id
        URL url = null;
        try {
            url = new URL(hit.getWebformatURL());
            if (compositeResults.getBitmaps().get(id) == null) {
                PixabayBitmapFetchTask fetchTask =
                        new PixabayBitmapFetchTask(this, compositeResults, hit);

                fetchTask.execute(url);
            }
            LongSparseArray<List<EntityAnnotation>> labels = compositeResults.getLabels();
            if (labels.get(id) == null)            // lazy fetch
                new LabelDetectionTask(this, compositeResults, id).execute();
            else {

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        render();
    }

    public void render() {
        // Do not add verbose logging here -- that would be way too chatty!

        Hit hit = compositeResults.getHit(currentPick);
        final long id = hit.getId(); // convert response's index to Pixabay's id
        List<EntityAnnotation> label = compositeResults.getLabels().get(id);
        Bitmap bitmap = compositeResults.getBitmaps().get(id);

        if (bitmap == null || (hit == null && tvTags.getVisibility() == View.VISIBLE) ||
                (label == null && tvLabels.getVisibility() == View.VISIBLE)) {
            return;
        }

        if (tvTags.getVisibility() == View.VISIBLE) {
            String s = getString(R.string.pixabay_tags, hit.getTags());
            String pixabayTags = tvTags.toString();
            pixabayTags = s;
            tvTags.append(pixabayTags);
            mySpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    mySpeech.setLanguage(Locale.US);
                    mySpeech.setSpeechRate(-10235);
                    mySpeech.speak(s,TextToSpeech.QUEUE_FLUSH,null);

                }



            });

            Log.i(TAG, s);
            // TODO previous line outputs to logcat, need to format and place s in tvTags
        }

        if (tvLabels.getVisibility() == View.VISIBLE) {
            String x =getString(R.string.cloud_vision_labels, labelsToString(label));
            String cloudVision = tvLabels.toString();
            cloudVision = x ;
            tvLabels.append(cloudVision);
            Log.i(TAG, x);
            // TODO previous line outputs to logcat, need to format and place l in tvLabels
        }

        image.setImageBitmap(bitmap);
    }

    private String labelsToString(List<EntityAnnotation> labels) {

        StringBuilder message = new StringBuilder();
        String omitThreshold = prefs.getString("threshold","default value");
        for (EntityAnnotation label : labels) {
            if (message.length() > 0) message.append(", ");
            message.append(prefs.getBoolean("show_probabilities", true) ?
                    String.format(Locale.US, "%s (%.2f)", label.getDescription(), label.getScore()) :
                    String.format(Locale.US, "%s", label.getDescription()));

        }
        return message.toString();
    }


    public void onTaskCompleted(CompositeResults results) {
        compositeResults = results;
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        getMenuInflater().inflate(R.menu.menu_about,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Log.v(TAG, "Settings");
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        } else if (id == R.id.about_settings)
            Log.v(TAG, "About");
        startActivity(new Intent(MainActivity.this, About.class));

        return super.onOptionsItemSelected(item);
    }




    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private static long getRandomLong(long minimum, long maximum) {
        return ((long) (Math.random() * (maximum - minimum))) + minimum;
    }

}
