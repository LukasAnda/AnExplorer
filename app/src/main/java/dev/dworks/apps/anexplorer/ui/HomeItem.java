package dev.dworks.apps.anexplorer.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;

import org.eazegraph.lib.charts.PieChart;
import org.eazegraph.lib.models.PieModel;

import java.io.File;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.cardview.widget.CardView;
import dev.dworks.apps.anexplorer.R;
import dev.dworks.apps.anexplorer.misc.FileUtils;
import dev.dworks.apps.anexplorer.misc.IconUtils;
import dev.dworks.apps.anexplorer.misc.MimeTypes;
import dev.dworks.apps.anexplorer.misc.Utils;
import dev.dworks.apps.anexplorer.model.RootInfo;
import dev.dworks.apps.anexplorer.setting.SettingsActivity;
import me.grantland.widget.AutofitTextView;


public class HomeItem extends FrameLayout {
    
    StorageInfo storageInfo = new StorageInfo();
    
    private Context mContext;
    private ImageView icon;
    private TextView title;
    private TextView summary;
    private NumberProgressBar progress;
    private int color;
    private int accentColor;
    private ImageView action;
    private View action_layout;
    private CardView card_view;
    private int mActionDrawable;
    private PieChart pieChart;
    
    public HomeItem(Context context) {
        super(context);
        init(context, null);
    }
    
    public HomeItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    
    public HomeItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        mContext = context;
        color = SettingsActivity.getPrimaryColor();
        accentColor = SettingsActivity.getAccentColor();
        LayoutInflater.from(context).inflate(R.layout.item_home_directory, this, true);
        pieChart = findViewById(R.id.pie);
        card_view = findViewById(R.id.card_view);
        //icon = (ImageView) findViewById(android.R.id.icon);
        title = findViewById(android.R.id.title);
        //summary = (TextView) findViewById(android.R.id.summary);
        //progress = (NumberProgressBar) findViewById(android.R.id.progress);
        
        //action_layout = findViewById(R.id.action_layout);
        action = findViewById(R.id.floatingActionButton);
    }
    
    public void setInfo(RootInfo root) {
        
        storageInfo = new StorageInfo();
        title.setText(root.title);
        try {
            StorageInfo info = getTotalFileCount(new File(root.path));
            generatePieData(info, root);
        } catch (Exception e) {
            generatePieData(root);
        }
    }
    
    @SuppressLint("ResourceAsColor")
    protected void generatePieData(StorageInfo storageInfo, RootInfo rootInfo) {
        
        long storageInfoTotal = (storageInfo.unknownSize + storageInfo.textSize + storageInfo
                .imagesSize + storageInfo
                .videoSize + storageInfo.audioSize);
        
        
        System.out.println("Storage info total: " + storageInfoTotal);
        
        System.out.println("Storage info total readable: " + FileUtils.convertToHumanReadableSize
                (getContext(), storageInfoTotal));
        
        long rootUsed = rootInfo.totalBytes - rootInfo.availableBytes;
        
        System.out.println("Root info total: " + rootUsed);
        
        System.out.println("Root info total readable: " + FileUtils.convertToHumanReadableSize
                (getContext(), rootUsed));
        
        long otherUsed = (rootUsed - storageInfo.audioSize
                - storageInfo.imagesSize
                - storageInfo.videoSize
                - storageInfo.textSize);
        
        loadPieSlice(rootInfo,"Images: ",storageInfo.imagesSize,R.id.item1,R.color.accent_red);
        loadPieSlice(rootInfo,"Audio: ",storageInfo.audioSize,R.id.item2,R.color.accent_purple);
        loadPieSlice(rootInfo, "Video: ", storageInfo.videoSize, R.id.item3, R.color.accent_cyan);
        loadPieSlice(rootInfo, "Docs: ", storageInfo.textSize, R.id.item4, R.color.accent_teal);
        loadPieSlice(rootInfo, "Other: ", otherUsed, R.id.item5, R.color.accent_green);
        loadPieSlice(rootInfo, "Free: ", rootInfo.availableBytes, R.id.item6, R.color.accent_amber);
    }
    
    @SuppressLint("ResourceAsColor")
    protected void generatePieData(RootInfo rootInfo){
        loadPieSlice(rootInfo, "Free: ", rootInfo.availableBytes, R.id.item1,R.color.accent_green);
        loadPieSlice(rootInfo, "Used: ", rootInfo.totalBytes - rootInfo.availableBytes, R.id.item2,R.color.accent_red);
    }
    
    @SuppressLint({"ResourceType", "SetTextI18n"})
    private void loadPieSlice(RootInfo rootInfo, String text, long total, @IdRes int itemId,
                              @ColorInt int color) {
        if (total <= 0) return;
        pieChart.addPieSlice(new PieModel((float) 100 * total / rootInfo
                .totalBytes, getContext().getResources().getColor(color)));
        AutofitTextView item = findViewById(itemId);
        item.setText(text + getFormatString(total));
        item.setCompoundDrawablesRelativeWithIntrinsicBounds(TextDrawable.builder()
                        .beginConfig()
                        .height(Utils.dpToPx(24))
                        .width(Utils.dpToPx(24))
                        .textColor(Color.BLACK)
                        .endConfig()
                        .buildRect(100 * total / rootInfo.totalBytes + ""
                                , getContext().getResources().getColor(color)),
                null, null, null);
        item.setVisibility(VISIBLE);
    }
    
    private String getFormatString(long data) {
        return FileUtils.convertToHumanReadableSize(getContext(), data);
    }
    
    public StorageInfo getTotalFileCount(File parentDir) {
        
        for (File f : parentDir.listFiles()) {
            if (f == null) continue;
            if (f.isDirectory()) {
                getTotalFileCount(f);
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
            }
        }
        
        return storageInfo;
    }
    
    
    public void setProgress(int value) {
        //progress.setProgress(value);
    }
    
    public int getProgress() {
        //return progress.getProgress();
        return 0;
    }
    
    public void setAction(int drawableId, OnClickListener listener) {
        mActionDrawable = drawableId;
        
        action.setImageDrawable(IconUtils.getDrawable(mContext, mActionDrawable));
        pieChart.setOnClickListener(listener);
    }
    
    public void setCardListener(OnClickListener listener) {
        card_view.setOnClickListener(listener);
    }
    
    public void updateColor() {
        color = SettingsActivity.getPrimaryColor();
        accentColor = SettingsActivity.getAccentColor();
        //progress.setColor(color);
        //action.setImageDrawable(IconUtils.applyTint(mContext, mActionDrawable, accentColor));
    }
    
    public class StorageInfo {
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
        
        @Override
        public String toString() {
            return "Audio count: " + audioCount
                    + " Audio total size: " + FileUtils.convertToHumanReadableSize(getContext(),
                    audioSize)
                    + "\nVideo count: " + videoCount
                    + " Video total size: " + FileUtils.convertToHumanReadableSize(getContext(),
                    videoSize)
                    + "\nImages count: " + imagesCount
                    + " Images total size: " + FileUtils.convertToHumanReadableSize(getContext(),
                    imagesSize)
                    + "\nText count: " + textCount
                    + " Text total size: " + FileUtils.convertToHumanReadableSize(getContext(),
                    textSize) +
                    "\nUnknown count: " + unknownCount
                    + " Unknown total size: " + FileUtils.convertToHumanReadableSize(getContext(),
                    unknownSize);
            
        }
    }
}