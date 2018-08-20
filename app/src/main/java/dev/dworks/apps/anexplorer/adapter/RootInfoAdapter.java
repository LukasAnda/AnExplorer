package dev.dworks.apps.anexplorer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import dev.dworks.apps.anexplorer.R;
import dev.dworks.apps.anexplorer.model.RootInfo;
import dev.dworks.apps.anexplorer.setting.SettingsActivity;
import dev.dworks.apps.anexplorer.ui.HomeItem;

public class RootInfoAdapter extends PagerAdapter {
    
    private Context mContext;
    private RootInfoAdapter.OnItemClickListener onItemClickListener;
    private ArrayList<RootInfo> mData;
    
    public RootInfoAdapter(Context context, ArrayList<RootInfo> data, OnItemClickListener listener) {
        mContext = context;
        mData = data;
        onItemClickListener = listener;
    }
    
    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup collection, int position) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.item_root_info, collection, false);
        HomeItem homeItem = layout.findViewById(R.id.homeItem);
        homeItem.setInfo(mData.get(position));
        homeItem.setCardListener(view -> onItemClickListener.onItemClick(mData.get(position)));
        collection.addView(layout);
        return layout;
    }
    
    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object view) {
        container.removeView((View) view);
    }
    
    @Override
    public int getCount() {
        return this.mData.size();
    }
    
    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }
    
    public interface OnItemClickListener {
        void onItemClick(RootInfo rootInfo);
    }
}
