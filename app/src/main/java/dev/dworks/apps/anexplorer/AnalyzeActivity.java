package dev.dworks.apps.anexplorer;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.txusballesteros.widgets.FitChart;
import com.txusballesteros.widgets.FitChartValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import dev.dworks.apps.anexplorer.common.ActionBarActivity;
import dev.dworks.apps.anexplorer.misc.AsyncTask;
import dev.dworks.apps.anexplorer.misc.FileUtils;
import dev.dworks.apps.anexplorer.misc.MimeTypes;
import dev.dworks.apps.anexplorer.misc.SystemBarTintManager;
import dev.dworks.apps.anexplorer.misc.Utils;
import dev.dworks.apps.anexplorer.setting.SettingsActivity;
import me.grantland.widget.AutofitTextView;

import static dev.dworks.apps.anexplorer.DocumentsActivity.getStatusBarHeight;
import static dev.dworks.apps.anexplorer.DocumentsApplication.isSpecialDevice;

public class AnalyzeActivity extends ActionBarActivity {

    FitChart fitChart;
    ProgressBar progressBar;
    TextView consumed;
    TextView consumedText;

    @SuppressLint("DefaultLocale")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);
        String path = getIntent().getStringExtra("RootPath");
        long totalBytes = getIntent().getLongExtra("TotalBytes", 0L);
        long availableBytes = getIntent().getLongExtra("AvailableBytes", 0L);
        String title = getIntent().getStringExtra("RootName");

        Toolbar mToolbar = findViewById(R.id.toolbar);
        mToolbar.setTitleTextAppearance(this, R.style.TextAppearance_AppCompat_Widget_ActionBar_Title);
        if (Utils.hasKitKat() && !Utils.hasLollipop()) {
            ((LinearLayout.LayoutParams) mToolbar.getLayoutParams()).setMargins(0,
                    getStatusBarHeight(this), 0, 0);
            mToolbar.setPadding(0, getStatusBarHeight(this), 0, 0);
        }
        mToolbar.setTitle(title);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        changeActionBarColor();

        fitChart = findViewById(R.id.chart);
        progressBar = findViewById(R.id.progress);

        consumed = findViewById(R.id.consumed);
        consumedText = findViewById(R.id.consumed_text);

        new CountDataTask(path, totalBytes, availableBytes).execute();

        ((ViewGroup) findViewById(R.id.rootLayout)).getLayoutTransition()
                .enableTransitionType(LayoutTransition.CHANGING);
    }

    @Override
    public String getTag() {
        return null;
    }

    private Drawable oldBackground;

    private void changeActionBarColor() {

        if (isSpecialDevice()) {
            return;
        }

        int color = SettingsActivity.getPrimaryColor(this);
        Drawable colorDrawable = new ColorDrawable(color);

        if (oldBackground == null) {
            getSupportActionBar().setBackgroundDrawable(colorDrawable);
        } else {
            TransitionDrawable td = new TransitionDrawable(new Drawable[]{oldBackground,
                    colorDrawable});
            getSupportActionBar().setBackgroundDrawable(td);
            td.startTransition(200);
        }

        oldBackground = colorDrawable;

        setUpStatusBar();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setUpStatusBar() {
        int color = Utils.getStatusBarColor(SettingsActivity.getPrimaryColor(this));
        if (Utils.hasLollipop()) {
            getWindow().setStatusBarColor(color);
        } else if (Utils.hasKitKat()) {
            SystemBarTintManager systemBarTintManager = new SystemBarTintManager(this);
            systemBarTintManager.setTintColor(color);
            systemBarTintManager.setStatusBarTintEnabled(true);
        }
    }

    @SuppressLint("DefaultLocale")
    protected void generateChartData(StorageInfo storageInfo, long totalBytes, long availableBytes) {

        long rootUsed = totalBytes - availableBytes;

        System.out.println("Root info total: " + rootUsed);

        System.out.println("Root info total readable: " + FileUtils.convertToHumanReadableSize
                (this, rootUsed));

        long otherUsed = (rootUsed - storageInfo.audioSize
                - storageInfo.imagesSize
                - storageInfo.videoSize
                - storageInfo.textSize);
        //otherUsed += storageInfo.unknownSize;

        Collection<FitChartValue> values = new ArrayList<>();


        progressBar.setVisibility(View.GONE);
        fitChart.setVisibility(View.VISIBLE);

        final double percent = (((totalBytes - availableBytes) / (double) totalBytes) * 100);
        Utils.animateProgress(AnalyzeActivity.this, percent, 40, progress -> consumed.setText(String.format("%.1f%%", progress)));

        consumedText.setVisibility(View.VISIBLE);

        values = addChartData(storageInfo.imagesSize, totalBytes, "Images: %s", R.id.images, values, R.color.accent_red);
        values = addChartData(storageInfo.audioSize, totalBytes, "Audio: %s", R.id.audio, values, R.color.accent_pink);
        values = addChartData(storageInfo.videoSize, totalBytes, "Video: %s", R.id.video, values, R.color.accent_cyan);
        values = addChartData(storageInfo.textSize, totalBytes, "Text: %s", R.id.text, values, R.color.accent_green);
        values = addChartData(otherUsed, totalBytes, "Other: %s", R.id.other, values, R.color.accent_amber);

        fitChart.setValues(values);
    }


    private Collection<FitChartValue> addChartData(long value, long total, String text, @IdRes int itemId, Collection<FitChartValue> values, @ColorRes int color) {
        float add = ((float) value) / total * 100;
        System.out.println(text + String.valueOf(add));
        values.add(new FitChartValue(add, ContextCompat.getColor(this, color)));
        AutofitTextView item = findViewById(itemId);
        item.setText(String.format(text, getFormatString(value)));
        int percent = Math.round(add);
        item.setCompoundDrawablesRelativeWithIntrinsicBounds(TextDrawable.builder()
                        .beginConfig()
                        .height(Utils.dpToPx(24))
                        .width(Utils.dpToPx(24))
                        .textColor(Color.BLACK)
                        .endConfig()
                        .buildRound(percent + ""
                                , ContextCompat.getColor(this, color)),
                null, null, null);
        Utils.animateProgress(AnalyzeActivity.this, add, 120, progress -> {
            long realProgress = (long) (value * progress / 100);
            item.setText(String.format(text, getFormatString(realProgress)));
        });
        return values;
    }

    private String getFormatString(long data) {
        return FileUtils.convertToHumanReadableSize(this, data);
    }

    public StorageInfo getTotalFileCount(File parentDir, StorageInfo storageInfo) {

        for (File f : parentDir.listFiles()) {
            if (f == null) continue;
            if (f.isDirectory()) {
                getTotalFileCount(f, storageInfo);
            } else {
                if (!f.exists()) continue;
                String mime = MimeTypes.getMimeTypeFromExtension(FileUtils.getExtFromFilename(f
                        .getName()));
                if (mime != null) {
                    if (mime.contains("audio")) {
                        storageInfo.audioCount++;
                        storageInfo.audioSize += f.length();
                    } else if (mime.contains("video")) {
                        storageInfo.videoCount++;
                        storageInfo.videoSize += f.length();
                    } else if (mime.contains("image")) {
                        storageInfo.imagesCount++;
                        storageInfo.imagesSize += f.length();
                    } else if (mime.contains("text")) {
                        storageInfo.textCount++;
                        storageInfo.textSize += f.length();
                    } else {
                        storageInfo.unknownCount++;
                        storageInfo.unknownSize += f.length();
                    }
                } else {
                    storageInfo.unknownCount++;
                    storageInfo.unknownSize += f.length();
                }

                storageInfo.totalCount++;
                storageInfo.totalSize += f.length();
            }
        }
        return storageInfo;
    }

    private class StorageInfo {
        private long unknownSize = 0;
        private long unknownCount = 0;

        private long imagesSize = 0;
        private long imagesCount = 0;

        private long audioSize = 0;
        private long audioCount = 0;

        private long videoSize = 0;
        private long videoCount = 0;

        private long textSize = 0;
        private long textCount = 0;

        private long totalSize = 0;
        private long totalCount = 0;

        public long getUnknownSize() {
            return unknownSize;
        }

        public void setUnknownSize(long unknownSize) {
            this.unknownSize = unknownSize;
        }

        public long getUnknownCount() {
            return unknownCount;
        }

        public void setUnknownCount(long unknownCount) {
            this.unknownCount = unknownCount;
        }

        public long getImagesSize() {
            return imagesSize;
        }

        public void setImagesSize(long imagesSize) {
            this.imagesSize = imagesSize;
        }

        public long getImagesCount() {
            return imagesCount;
        }

        public void setImagesCount(long imagesCount) {
            this.imagesCount = imagesCount;
        }

        public long getAudioSize() {
            return audioSize;
        }

        public void setAudioSize(long audioSize) {
            this.audioSize = audioSize;
        }

        public long getAudioCount() {
            return audioCount;
        }

        public void setAudioCount(long audioCount) {
            this.audioCount = audioCount;
        }

        public long getVideoSize() {
            return videoSize;
        }

        public void setVideoSize(long videoSize) {
            this.videoSize = videoSize;
        }

        public long getVideoCount() {
            return videoCount;
        }

        public void setVideoCount(long videoCount) {
            this.videoCount = videoCount;
        }

        public long getTextSize() {
            return textSize;
        }

        public void setTextSize(long textSize) {
            this.textSize = textSize;
        }

        public long getTextCount() {
            return textCount;
        }

        public void setTextCount(long textCount) {
            this.textCount = textCount;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public void setTotalSize(long totalSize) {
            this.totalSize = totalSize;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        @Override
        public String toString() {
            return "Audio count: " + audioCount
                    + " Audio total size: " + FileUtils.convertToHumanReadableSize(AnalyzeActivity.this,
                    audioSize)
                    + "\nVideo count: " + videoCount
                    + " Video total size: " + FileUtils.convertToHumanReadableSize(AnalyzeActivity.this,
                    videoSize)
                    + "\nImages count: " + imagesCount
                    + " Images total size: " + FileUtils.convertToHumanReadableSize(AnalyzeActivity.this,
                    imagesSize)
                    + "\nText count: " + textCount
                    + " Text total size: " + FileUtils.convertToHumanReadableSize(AnalyzeActivity.this,
                    textSize) +
                    "\nUnknown count: " + unknownCount
                    + " Unknown total size: " + FileUtils.convertToHumanReadableSize(AnalyzeActivity.this,
                    unknownSize);

        }
    }

    private class CountDataTask extends AsyncTask<Void, Integer, StorageInfo> {
        String path;
        long totalBytes;
        long availableBytes;

        public CountDataTask(String path, long totalBytes, long availableBytes) {
            this.path = path;
            this.totalBytes = totalBytes;
            this.availableBytes = availableBytes;
        }

        @Override
        protected StorageInfo doInBackground(Void... voids) {
            StorageInfo storageInfo = new StorageInfo();
            return getTotalFileCount(new File(path), storageInfo);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(StorageInfo storageInfo) {
            super.onPostExecute(storageInfo);
            generateChartData(storageInfo, totalBytes, availableBytes);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onCancelled(StorageInfo storageInfo) {
            super.onCancelled(storageInfo);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }
    }
}
