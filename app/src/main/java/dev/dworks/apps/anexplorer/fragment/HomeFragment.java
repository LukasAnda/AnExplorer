/*
 * Copyright (C) 2014 Hari Krishna Dulipudi
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.dworks.apps.anexplorer.fragment;

import android.app.ActivityManager;
import android.app.Dialog;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Loader;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import dev.dworks.apps.anexplorer.BaseActivity;
import dev.dworks.apps.anexplorer.DocumentsActivity;
import dev.dworks.apps.anexplorer.DocumentsApplication;
import dev.dworks.apps.anexplorer.R;
import dev.dworks.apps.anexplorer.adapter.CommonInfo;
import dev.dworks.apps.anexplorer.adapter.HomeAdapter;
import dev.dworks.apps.anexplorer.common.DialogBuilder;
import dev.dworks.apps.anexplorer.common.RecyclerFragment;
import dev.dworks.apps.anexplorer.adapter.RecentsAdapter;
import dev.dworks.apps.anexplorer.adapter.RootInfoAdapter;
import dev.dworks.apps.anexplorer.adapter.ShortcutsAdapter;
import dev.dworks.apps.anexplorer.cursor.LimitCursorWrapper;
import dev.dworks.apps.anexplorer.loader.RecentLoader;
import dev.dworks.apps.anexplorer.misc.AnalyticsManager;
import dev.dworks.apps.anexplorer.misc.AsyncTask;
import dev.dworks.apps.anexplorer.misc.IconHelper;
import dev.dworks.apps.anexplorer.misc.IconUtils;
import dev.dworks.apps.anexplorer.misc.RootsCache;
import dev.dworks.apps.anexplorer.misc.Utils;
import dev.dworks.apps.anexplorer.model.DirectoryResult;
import dev.dworks.apps.anexplorer.model.DocumentInfo;
import dev.dworks.apps.anexplorer.model.DocumentsContract;
import dev.dworks.apps.anexplorer.model.RootInfo;
import dev.dworks.apps.anexplorer.provider.AppsProvider;
import dev.dworks.apps.anexplorer.setting.SettingsActivity;

import static dev.dworks.apps.anexplorer.BaseActivity.State.MODE_GRID;
import static dev.dworks.apps.anexplorer.DocumentsApplication.isTelevision;
import static dev.dworks.apps.anexplorer.DocumentsApplication.isWatch;
import static dev.dworks.apps.anexplorer.adapter.HomeAdapter.TYPE_MAIN;
import static dev.dworks.apps.anexplorer.adapter.HomeAdapter.TYPE_RECENT;
import static dev.dworks.apps.anexplorer.adapter.HomeAdapter.TYPE_SHORTCUT;
import static dev.dworks.apps.anexplorer.misc.AnalyticsManager.FILE_TYPE;
import static dev.dworks.apps.anexplorer.provider.AppsProvider.getRunningAppProcessInfo;

/**
 * Display home.
 */
public class HomeFragment extends RecyclerFragment implements HomeAdapter.OnItemClickListener {
    public static final String TAG = "HomeFragment";
    public static final String ROOTS_CHANGED = "android.intent.action.ROOTS_CHANGED";
    private static final int MAX_RECENT_COUNT = isTelevision() ? 20 : 10;
    
    private final int mLoaderId = 42;
    //private HomeItem storageStats;
    //private HomeItem memoryStats;
    private Timer storageTimer;
    private Timer secondatyStorageTimer;
    private Timer usbStorageTimer;
    private Timer processTimer;
    private RootsCache roots;
    private RecyclerView mRecentsRecycler;
    private RecyclerView mShortcutsRecycler;
    private ViewPager mRootsPager;
    private RecentsAdapter mRecentsAdapter;
    private LoaderManager.LoaderCallbacks<DirectoryResult> mCallbacks;
    private RootInfo mHomeRoot;
    //private HomeItem secondayStorageStats;
    //private HomeItem usbStorageStats;
    private BaseActivity mActivity;
    private IconHelper mIconHelper;
    
    public static void show(FragmentManager fm) {
        final HomeFragment fragment = new HomeFragment();
        final FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container_directory, fragment, TAG);
        ft.commitAllowingStateLoss();
    }
    
    public static HomeFragment get(FragmentManager fm) {
        return (HomeFragment) fm.findFragmentByTag(TAG);
    }
    
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        storageTimer = new Timer();
        secondatyStorageTimer = new Timer();
        usbStorageTimer = new Timer();
        processTimer = new Timer();
        recents = (TextView) view.findViewById(R.id.recents);
        recents_container = view.findViewById(R.id.recents_container);
        
        mShortcutsRecycler = (RecyclerView) view.findViewById(R.id.shortcuts_recycler);
        mRecentsRecycler = (RecyclerView) view.findViewById(R.id.recents_recycler);
        mRootsPager = view.findViewById(R.id.roots_pager);
        
        mActivity = ((BaseActivity) getActivity());
        mIconHelper = new IconHelper(mActivity, MODE_GRID);
        
        roots = DocumentsApplication.getRootsCache(getActivity());
        mHomeRoot = roots.getHomeRoot();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        showData();
        registerReceiver();
    }

    @Override
    public void onPause() {
        unRegisterReceiver();
        super.onPause();
    }
    
    public void showData() {
        updateUI();
        //showStorage();
        //showOtherStorage();
        //showMemory(0);
        showRoots();
        showShortcuts();
        getLoaderManager().restartLoader(mLoaderId, null, mCallbacks);
    }
    
    private void updateUI() {
        mIconHelper.setThumbnailsEnabled(mActivity.getDisplayState().showThumbnail);
        recents_container.setVisibility(SettingsActivity.getDisplayRecentMedia() ? View.VISIBLE :
                View.GONE);
        roots = DocumentsApplication.getRootsCache(getActivity());
        int accentColor = SettingsActivity.getAccentColor();
        recents.setTextColor(accentColor);
        //storageStats.updateColor();
        //memoryStats.updateColor();
        //secondayStorageStats.updateColor();
        //usbStorageStats.updateColor();
    }
    
    public void reloadData() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showData();
            }
        }, 500);
    }
    
    private void showRoots(){
        ArrayList<RootInfo> availableRoots = new ArrayList<>();
        if(roots.getPrimaryRoot() != null)
            availableRoots.add(roots.getPrimaryRoot());
        if(roots.getSecondaryRoot() != null)
            availableRoots.add(roots.getSecondaryRoot());
        if(roots.getUSBRoot() != null)
            availableRoots.add(roots.getUSBRoot());
        if(roots.getProcessRoot() != null)
            availableRoots.add(roots.getProcessRoot());
        RootInfoAdapter adapter = new RootInfoAdapter(getActivity(), availableRoots, this::openRoot);
        mRootsPager.setAdapter(adapter);
        mRootsPager.setOffscreenPageLimit(availableRoots.size());
        
    }
    
    private void showShortcuts() {
        ArrayList<RootInfo> data = roots.getShortcutsInfo();
        mShortcutsAdapter = new ShortcutsAdapter(getActivity(), data);
        mShortcutsAdapter.setOnItemClickListener(new ShortcutsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ShortcutsAdapter.ViewHolder item, int position) {
                openRoot(mShortcutsAdapter.getItem(position));
            }
        });
        mShortcutsRecycler.setAdapter(mShortcutsAdapter);
    }
    
    private void showRecents() {
        final RootInfo root = roots.getRecentsRoot();
        recents.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openRoot(root);
            }
        });
        
        mRecentsAdapter = new RecentsAdapter(getActivity(), null, mIconHelper);
        mRecentsAdapter.setOnItemClickListener(new RecentsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(RecentsAdapter.ViewHolder item, int position) {
                openDocument(item.mDocumentInfo);
            }
        });
        mRecentsRecycler.setAdapter(mRecentsAdapter);
        LinearSnapHelper helper = new LinearSnapHelper();
        helper.attachToRecyclerView(mRecentsRecycler);
        
        final BaseActivity.State state = getDisplayState(this);
        mCallbacks = new LoaderManager.LoaderCallbacks<DirectoryResult>() {
            
            @Override
            public Loader<DirectoryResult> onCreateLoader(int id, Bundle args) {
                return new RecentLoader(getActivity(), roots, state);
            }
            
            @Override
            public void onLoadFinished(Loader<DirectoryResult> loader, DirectoryResult result) {
                if (!isAdded())
                    return;
                if (null == result.cursor || (null != result.cursor && result.cursor.getCount()
                        == 0)) {
                    recents_container.setVisibility(View.GONE);
                } else {
                    //recents_container.setVisibility(View.VISIBLE);
                    mRecentsAdapter.swapCursor(new LimitCursorWrapper(result.cursor,
                            MAX_RECENT_COUNT));
                if(null != result.cursor && result.cursor.getCount() != 0) {
                    mAdapter.setRecentData(new LimitCursorWrapper(result.cursor, MAX_RECENT_COUNT));
                }
            }
            
            @Override
            public void onLoaderReset(Loader<DirectoryResult> loader) {
                mAdapter.setRecentData(null);
            }
        };
        if(SettingsActivity.getDisplayRecentMedia()) {
            LoaderManager.getInstance(getActivity()).restartLoader(mLoaderId, null, mCallbacks);
        }
    }

    public void reloadData(){
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showData();
            }
        }, 500);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onItemClick(HomeAdapter.ViewHolder item, View view, int position) {
        switch (item.commonInfo.type) {
            case TYPE_MAIN:
            case TYPE_SHORTCUT:
                if(item.commonInfo.rootInfo.rootId.equals("clean")){
                    cleanRAM();
                } else {
                    openRoot(item.commonInfo.rootInfo);
                }
                break;
            case TYPE_RECENT:
                try {
                    final DocumentInfo documentInfo = ((HomeAdapter.GalleryViewHolder)item).getItem(position);
                    openDocument(documentInfo);
                } catch (Exception ignore) {}
                break;
        }
    }

    @Override
    public void onItemLongClick(HomeAdapter.ViewHolder item, View view, int position) {

    }

    @Override
    public void onItemViewClick(HomeAdapter.ViewHolder item, View view, int position) {
        switch (view.getId()) {
            case R.id.recents:
                openRoot(roots.getRecentsRoot());
                break;

            case R.id.action:
                Bundle params = new Bundle();
                if(item.commonInfo.rootInfo.isAppProcess()) {
                    cleanRAM();
                } else {
                    Intent intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                    if(Utils.isIntentAvailable(getActivity(), intent)) {
                        getActivity().startActivity(intent);
                    } else  {
                        Utils.showSnackBar(getActivity(), "Coming Soon!");
                    }
                    AnalyticsManager.logEvent("storage_analyze", params);
                }
                break;
        }

    }

    private void cleanRAM(){
        Bundle params = new Bundle();
        new OperationTask(processRoot).execute();
        AnalyticsManager.logEvent("process_clean", params);
    }
    
    private class OperationTask extends AsyncTask<Void, Void, Boolean> {
        
        private MaterialProgressDialog progressDialog;
        private RootInfo root;
        private long currentAvailableBytes;
        
        public OperationTask(RootInfo root) {
            DialogBuilder builder = new DialogBuilder(getActivity());
            builder.setMessage("Cleaning up RAM...");
            builder.setIndeterminate(true);
            progressDialog = builder.create();
            this.root = root;
            currentAvailableBytes = root.availableBytes;
        }
        
        @Override
        protected void onPreExecute() {
            progressDialog.show();
            super.onPreExecute();
        }
        
        @Override
        protected Boolean doInBackground(Void... params) {
            boolean result = false;
            cleanupMemory(getActivity());
            return result;
        }
        
        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!Utils.isActivityAlive(getActivity())) {
                return;
            }
            AppsProvider.notifyDocumentsChanged(getActivity(), root.rootId);
            AppsProvider.notifyRootsChanged(getActivity());
            RootsCache.updateRoots(getActivity(), AppsProvider.AUTHORITY);
            roots = DocumentsApplication.getRootsCache(getActivity());
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //showMemory(currentAvailableBytes);
                    progressDialog.dismiss();
                }
            }, 500);
        }
    }
    
    private void animateProgress(final HomeItem item, final Timer timer, RootInfo root) {
        try {
            final double percent = (((root.totalBytes - root.availableBytes) / (double) root
                    .totalBytes) * 100);
            item.setProgress(0);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (Utils.isActivityAlive(getActivity())) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (item.getProgress() >= (int) percent) {
                                    timer.cancel();
                                } else {
                                    item.setProgress(item.getProgress() + 1);
                                }
                            }
                        });
                    }
                }
            }, 50, 20);
            
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Total files: " + item.getTotalFileCount(new File(root.path)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            
            Thread t = new Thread(r);
            t.start();
        } catch (Exception e) {
            item.setVisibility(View.GONE);
            CrashReportingManager.logException(e);
        }
    }
    
    private static BaseActivity.State getDisplayState(Fragment fragment) {
        return ((BaseActivity) fragment.getActivity()).getDisplayState();
    }
    
    private void openRoot(RootInfo rootInfo) {
        DocumentsActivity activity = ((DocumentsActivity) getActivity());
        activity.onRootPicked(rootInfo, mHomeRoot);
        AnalyticsManager.logEvent("open_shortcuts", rootInfo, new Bundle());
    }
    
    public void cleanupMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context
                .ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcessesList =
                getRunningAppProcessInfo(context);
        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcessesList) {
            activityManager.killBackgroundProcesses(processInfo.processName);
        }
    }
    
    private void openDocument(DocumentInfo doc) {
        ((BaseActivity) getActivity()).onDocumentPicked(doc);
        Bundle params = new Bundle();
        String type = IconUtils.getTypeNameFromMimeType(doc.mimeType);
        params.putString(FILE_TYPE, type);
        AnalyticsManager.logEvent("open_image_recent", params);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setListAdapter(mAdapter);
        showData();
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }

        ((GridLayoutManager)getListView().getLayoutManager()).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int spanSize = 1;
                switch (mAdapter.getItem(position).type) {
                    case TYPE_MAIN:
                        spanSize = totalSpanSize;
                        break;
                    case TYPE_SHORTCUT:
                        spanSize = isWatch() ? 1 : 2;
                        break;
                    case TYPE_RECENT:
                        spanSize = totalSpanSize;
                        break;
                }
                return spanSize;
            }
        });
    }

    private void registerReceiver() {
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(ROOTS_CHANGED));
    }

    private void unRegisterReceiver() {
        if(null != broadcastReceiver) {
            getActivity().unregisterReceiver(broadcastReceiver);
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showData();
        }
    };
}