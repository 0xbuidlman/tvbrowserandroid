/*
 * TV-Browser for Android
 * Copyright (C) 2013 René Mach (rene@tvbrowser.org)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to use, copy, modify or merge the Software,
 * furthermore to publish and distribute the Software free of charge without modifications and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.tvbrowser.tvbrowser;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import org.tvbrowser.content.TvBrowserContentProvider;
import org.tvbrowser.settings.SettingConstants;
import org.tvbrowser.utils.CompatUtils;
import org.tvbrowser.utils.IOUtils;
import org.tvbrowser.utils.PrefUtils;
import org.tvbrowser.utils.ProgramUtils;
import org.tvbrowser.utils.UiUtils;
import org.tvbrowser.view.SeparatorDrawable;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.LongSparseArray;
import android.text.Spannable;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class FragmentProgramsListRunning extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>, OnSharedPreferenceChangeListener {
  private static final String WHERE_CLAUSE_KEY = "WHERE_CLAUSE_KEY";
  private static final String DAY_CLAUSE_KEY = "DAY_CLAUSE_KEY";
    
  private Handler handler = new Handler();
  
  private boolean mKeepRunning;
  private Thread mUpdateThread;
  private int mWhereClauseTime;
  
  private BroadcastReceiver mDataUpdateReceiver;
  private BroadcastReceiver mRefreshReceiver;
  private BroadcastReceiver mMarkingChangeReceiver;
  private BroadcastReceiver mDontWantToSeeReceiver;
  private BroadcastReceiver mChannelUpdateDone;
  
  private static final GradientDrawable BEFORE_GRADIENT;
  private static final GradientDrawable AFTER_GRADIENT;
  
  private ArrayAdapter<ChannelProgramBlock> mRunningProgramListAdapter;
  
  private ArrayList<ChannelProgramBlock> mProgramBlockList;
  private ArrayList<ChannelProgramBlock> mCurrentViewList;
  
  private LongSparseArray<String[]> mMarkingsMap;
  private LongSparseArray<String> mTitleMap;
  
  private long mCurrentTime;
  
  int mProgramIDColumn;
  int mStartTimeColumn;
  int mEndTimeColumn;
  int mTitleColumn;
  int mPictureColumn;
  int mPictureCopyrightColumn;
  int mCategoryColumn;
  int mGenreColumn;
  int mEpsiodeColumn;
  int mChannelNameColumn;
  int mChannelIDColumn;
  
  private boolean showEpisode;
  private boolean showInfo;
  private boolean mShowOrderNumber;
      
  private View.OnClickListener mOnClickListener;
  private View.OnClickListener mChannelSwitchListener;
  private View mContextView;
  private long mContextProgramID;
  private long mDayStart;
  
  private ListView mListView;
  private LinearLayout mTimeBar;
  private Spinner mDateSelection;
  
  private ArrayAdapter<DateSelection> mDateAdapter;
  
  static {
    BEFORE_GRADIENT = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[] {Color.argb(0x84, 0, 0, 0xff),Color.WHITE});
    BEFORE_GRADIENT.setCornerRadius(0f);
    
    AFTER_GRADIENT = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[] {Color.WHITE,Color.argb(0x84, 0, 0, 0xff)});
    AFTER_GRADIENT.setCornerRadius(0f);
  }
  
  @Override
  public void onResume() {
    super.onResume();
    
    mKeepRunning = true;
    startUpdateThread();
  }
  
  @Override
  public void onPause() {
    super.onPause();
    
    mKeepRunning = false;
  }
  
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    
    mDataUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(final Context context, final Intent intent) {
        handler.post(new Runnable() {
          public void run() {
            updateDateSelection();
            
            if(intent != null) {
              startUpdateThread();
            }
          }
        });
      }
    };
    
    mRefreshReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if(mWhereClauseTime == -1) {
          startUpdateThread();
        }
        else {
          showEpisode = PrefUtils.getBooleanValue(R.string.SHOW_EPISODE_IN_RUNNING_LIST, R.bool.show_episode_in_running_list_default);
          showInfo = PrefUtils.getBooleanValue(R.string.SHOW_INFO_IN_RUNNING_LIST, R.bool.show_info_in_running_list_default);
          mShowOrderNumber = PrefUtils.getBooleanValue(R.string.SHOW_SORT_NUMBER_IN_RUNNING_LIST, R.bool.show_sort_number_in_running_list_default);
          
          new Thread() {
            public void run() {
              if(getActivity() != null && isAdded()) {
                ViewGroup list = (ViewGroup)getListView();
                
                for(int i = 0; i < list.getChildCount(); i++) {
                  CompactLayoutViewHolder holder = (CompactLayoutViewHolder) list.getChildAt(i).getTag();
                  
                  if(holder.mPrevious.getVisibility() == View.VISIBLE) {
                    if(holder.mPreviousStartTimeValue <= System.currentTimeMillis()) {
                      String[] markedColumns = mMarkingsMap.get(holder.mPreviousProgramID);
                      
                      UiUtils.handleMarkings(getActivity(), null, holder.mPreviousStartTimeValue, holder.mPreviousEndTimeValue, holder.mPrevious, markedColumns, handler);
                    }
                  }
                  
                  if(holder.mNowStartTimeValue <= System.currentTimeMillis()) {
                    String[] markedColumns = mMarkingsMap.get(holder.mNowProgramID);
                    
                    UiUtils.handleMarkings(getActivity(), null, holder.mNowStartTimeValue, holder.mNowEndTimeValue, holder.mNow, markedColumns, handler);
                  }
  
                  if(holder.mNextStartTimeValue <= System.currentTimeMillis()) {
                    String[] markedColumns = mMarkingsMap.get(holder.mNextProgramID);
                    
                    UiUtils.handleMarkings(getActivity(), null, holder.mNextStartTimeValue, holder.mNextEndTimeValue, holder.mNext, markedColumns, handler);
                  }
                }
              }
            }
          }.start();
        }
      }
    };
    
    mDontWantToSeeReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        startUpdateThread();
      }
    };
    
    LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mDontWantToSeeReceiver, new IntentFilter(SettingConstants.DONT_WANT_TO_SEE_CHANGED));
    
    mMarkingChangeReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, final Intent intent) {
        new Thread() {
          public void run() {
            final long programID = intent.getLongExtra(SettingConstants.EXTRA_MARKINGS_ID, -1);
            
            if(mMarkingsMap.indexOfKey(programID) >= 0 && IOUtils.isDatabaseAccessible(getActivity())) {
              String[] projection = TvBrowserContentProvider.getColumnArrayWithMarkingColums(TvBrowserContentProvider.DATA_KEY_STARTTIME,TvBrowserContentProvider.DATA_KEY_ENDTIME);
              
              Cursor c = getActivity().getContentResolver().query(ContentUris.withAppendedId(TvBrowserContentProvider.CONTENT_URI_DATA, programID), projection, null, null, null);
              
              if(c.moveToFirst()) {
                try {
                  final View view = getListView().findViewWithTag(programID);
                  
                  if(view != null) {
                    ArrayList<String> markedColumns = new ArrayList<String>();
                    
                    for(String column : TvBrowserContentProvider.MARKING_COLUMNS) {
                      int index = c.getColumnIndex(column);
                      
                      if(index >= 0 && c.getInt(index) >= 1) {
                        markedColumns.add(column);
                      }
                      else if(column.equals(TvBrowserContentProvider.DATA_KEY_MARKING_MARKING) && ProgramUtils.isMarkedWithIcon(getActivity(), programID)) {
                        markedColumns.add(column);
                      }
                    }
                    
                    mMarkingsMap.put(programID, IOUtils.getStringArrayFromList(markedColumns));
                    
                    handler.post(new Runnable() {
                      @Override
                      public void run() {
                        getListView().invalidateViews();
                      }
                    });
                  }
                }catch(NullPointerException npe) {}
              }
                            
              c.close();
            }
          }
        }.start();
      }
    };
    
    mChannelUpdateDone = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        startUpdateThread();
      }
    };
    
    IntentFilter intent = new IntentFilter(SettingConstants.DATA_UPDATE_DONE);
    IntentFilter markingsFilter = new IntentFilter(SettingConstants.MARKINGS_CHANGED);
    IntentFilter channelsChanged = new IntentFilter(SettingConstants.CHANNEL_UPDATE_DONE);
    
    LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mChannelUpdateDone, channelsChanged);
    getActivity().registerReceiver(mDataUpdateReceiver, intent);
    LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mRefreshReceiver, SettingConstants.RERESH_FILTER);
    LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMarkingChangeReceiver, markingsFilter);
  }
  
  public void setDay(long start) {
    if(start != mDayStart) {
      mDayStart = start;
      
      Calendar today = Calendar.getInstance();
      today.set(Calendar.HOUR_OF_DAY, 0);
      today.set(Calendar.MINUTE, 0);
      today.set(Calendar.SECOND, 0);
      today.set(Calendar.MILLISECOND, 0);
      
      if((mDayStart > System.currentTimeMillis() || mDayStart < today.getTimeInMillis()) && mWhereClauseTime < System.currentTimeMillis()) {
        Button time = (Button)((ViewGroup)((ViewGroup)getView().getParent()).getParent()).findViewWithTag(mWhereClauseTime);
        Button now = (Button)((ViewGroup)((ViewGroup)getView().getParent()).getParent()).findViewById(R.id.now_button);
        
        if(time != null && !time.equals(now)) {
          time.performClick();
        }
        else {
          if(mTimeBar.getChildCount() > 1) {
            ((Button)mTimeBar.getChildAt(1)).performClick();
          }
        }
        
        startUpdateThread();
      }
      else {
        startUpdateThread();
      }
    }
  }
  
  public void setWhereClauseTime(Object time) {
    if(time instanceof Integer) {
      int testValue = ((Integer) time).intValue();
      
      if(testValue != mWhereClauseTime) {
        
        Button test = (Button)((View)getView().getParent()).findViewWithTag(Integer.valueOf(mWhereClauseTime));
        
        if(test != null) {
          test.setBackgroundResource(android.R.drawable.list_selector_background);
        }
        
        int oldWhereClauseTime = mWhereClauseTime;
        
        mWhereClauseTime = testValue;
        
        Calendar now = Calendar.getInstance();
        
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        
        if(mWhereClauseTime != -1 && PrefUtils.getBooleanValue(R.string.RUNNING_PROGRAMS_NEXT_DAY, R.bool.running_programs_next_day_default)) {
          int test1 = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
          
          if((test1 - mWhereClauseTime) > 180 && mDayStart < System.currentTimeMillis() && mDayStart >= today.getTimeInMillis()) {
            Spinner date = (Spinner)((ViewGroup)getView().getParent()).findViewById(R.id.running_date_selection);
            
            if(date.getCount() > 2) {
              date.setSelection(2);
            }
          }
          else {
            startUpdateThread();
          }
        }
        else if(oldWhereClauseTime != -1 && mWhereClauseTime == -1) {
          Spinner date = (Spinner)((ViewGroup)getView().getParent()).findViewById(R.id.running_date_selection);
          
          if(date.getCount() > 1) {
            date.setSelection(1);
          }
          
          startUpdateThread();
        }
        else {
          startUpdateThread();
        }
      }
    }
  }
    
  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putInt(WHERE_CLAUSE_KEY, mWhereClauseTime);
    outState.putLong(DAY_CLAUSE_KEY, mDayStart);
    super.onSaveInstanceState(outState);
  }
  
  private static final class CompactLayoutViewHolder {
    static final int PREVIOUS = 0;
    static final int NOW = 1;
    static final int NEXT = 2;
    
    long mPreviousProgramID;
    long mNowProgramID;
    long mNextProgramID;
    
    long mPreviousStartTimeValue;
    long mNowStartTimeValue;
    long mNextStartTimeValue;
    
    long mPreviousEndTimeValue;
    long mNowEndTimeValue;
    long mNextEndTimeValue;
    
    int mCurrentOrientation;
    float mCurrentScale;
    
    ViewGroup mChannelInfo;
    ImageView mChannelLogo;
    TextView mChannel;
    
    View mPrevious;
    View mNow;
    View mNext;
    
    View mSeparator1;
    View mSeparator2;
    
    TextView mPreviousStartTime;
    TextView mPreviousTitle;
    TextView mPreviousInfos;
    TextView mPreviousEpisode;
    
    TextView mNowStartTime;
    TextView mNowTitle;
    TextView mNowInfos;
    TextView mNowEpisode;
    
    TextView mNextStartTime;
    TextView mNextTitle;
    TextView mNextInfos;
    TextView mNextEpisode;
    
    public void setVisibility(int type, int visibility) {
      switch(type) {
        case PREVIOUS:
          mPrevious.setVisibility(visibility);
          mPreviousStartTime.setVisibility(visibility);
          mPreviousTitle.setVisibility(visibility);
          mPreviousEpisode.setVisibility(visibility);
          break;
        case NOW:
          mNow.setVisibility(visibility);
          mNowStartTime.setVisibility(visibility);
          mNowTitle.setVisibility(visibility);
          mNowEpisode.setVisibility(visibility);
          break;
        case NEXT:
          mNext.setVisibility(visibility);
          mNextStartTime.setVisibility(visibility);
          mNextTitle.setVisibility(visibility);
          mNextEpisode.setVisibility(visibility);
          break;
      }
    }
    
    public void setSeparatorVisibility(int visibility) {
       if(mSeparator1 != null) {
         mSeparator1.setVisibility(visibility);
         mSeparator2.setVisibility(visibility);
       }
    }
    
    public boolean orientationChanged(int orientation) {
      return mCurrentOrientation != orientation;
    }
    
    public void setColor(int type, int color) {
      switch (type) {
        case PREVIOUS:
          mPreviousEpisode.setTextColor(color);
          mPreviousTitle.setTextColor(color);
          mPreviousStartTime.setTextColor(color);
          mPreviousInfos.setTextColor(color);
          break;
        case NOW:
          mNowEpisode.setTextColor(color);
          mNowTitle.setTextColor(color);
          mNowStartTime.setTextColor(color);
          mNowInfos.setTextColor(color);
          break;
        case NEXT:
          mNextEpisode.setTextColor(color);
          mNextTitle.setTextColor(color);
          mNextStartTime.setTextColor(color);
          mNextInfos.setTextColor(color);
          break;
      }
    }
  }
  
  
  @SuppressLint("NewApi")
  private boolean fillCompactLayout(final CompactLayoutViewHolder viewHolder, final int type, final ChannelProgramBlock block, final java.text.DateFormat timeFormat, final int DEFAULT_TEXT_COLOR, boolean channelSet) {
    TextView startTimeView = null;
    TextView titleView = null;
    TextView infoView = null;
    TextView episodeView = null;
    View layout = null;
    
    long startTime = 0;
    long endTime = 0;
    long programID = -1;
    String title = null;
    String episode = null;
    Spannable infos = null;
    
    switch(type) {
      case CompactLayoutViewHolder.PREVIOUS:
        layout = viewHolder.mPrevious;
        startTimeView = viewHolder.mPreviousStartTime;
        titleView = viewHolder.mPreviousTitle;
        episodeView = viewHolder.mPreviousEpisode;
        infoView = viewHolder.mPreviousInfos;
        startTime = block.mPreviousStart;
        endTime = block.mPreviousEnd;
        episode = block.mPreviousEpisode;
        infos = block.mPreviousCategory;
        programID = block.mPreviousProgramID;
        break;
      case CompactLayoutViewHolder.NOW:
        layout = viewHolder.mNow;
        startTimeView = viewHolder.mNowStartTime;
        titleView = viewHolder.mNowTitle;
        episodeView = viewHolder.mNowEpisode;
        infoView = viewHolder.mNowInfos;
        startTime = block.mNowStart;
        endTime = block.mNowEnd;
        episode = block.mNowEpisode;
        infos = block.mNowCategory;
        programID = block.mNowProgramID;
        break;
      case CompactLayoutViewHolder.NEXT:
        layout = viewHolder.mNext;
        startTimeView = viewHolder.mNextStartTime;
        titleView = viewHolder.mNextTitle;
        episodeView = viewHolder.mNextEpisode;
        infoView = viewHolder.mNextInfos;
        startTime = block.mNextStart;
        endTime = block.mNextEnd;
        episode = block.mNextEpisode;
        infos = block.mNextCategory;
        programID = block.mNextProgramID;
        break;
    }
    
    title = mTitleMap.get(programID);
    
    if(startTime > 0 && title != null) {
      switch(type) {
        case CompactLayoutViewHolder.PREVIOUS:
          viewHolder.mPreviousStartTimeValue = startTime;
          viewHolder.mPreviousEndTimeValue = endTime;
          viewHolder.mPreviousProgramID = programID;
          break;
        case CompactLayoutViewHolder.NOW:
          viewHolder.mNowStartTimeValue = startTime;
          viewHolder.mNowEndTimeValue = endTime;
          viewHolder.mNowProgramID = programID;
          break;
        case CompactLayoutViewHolder.NEXT:
          viewHolder.mNextStartTimeValue = startTime;
          viewHolder.mNextEndTimeValue = endTime;
          viewHolder.mNextProgramID = programID;
          break;
      }
      
      viewHolder.setVisibility(type, View.VISIBLE);
    
      startTimeView.setText(timeFormat.format(startTime));
      titleView.setText(ProgramUtils.getMarkIcons(getActivity(), programID, title));
      
      if(!showEpisode || episode == null || episode.trim().length() == 0) {
        episodeView.setVisibility(View.GONE);
      }
      else {
        episodeView.setText(episode);
        episodeView.setVisibility(View.VISIBLE);
      }
      
      if(!showInfo || infos == null || infos.toString().trim().length() == 0) {
        infoView.setVisibility(View.GONE);
      }
      else {
        infoView.setText(infos);
        infoView.setVisibility(View.VISIBLE);
      }
      
      if(endTime <= System.currentTimeMillis()) {
        viewHolder.setColor(type, UiUtils.getColor(UiUtils.EXPIRED_COLOR_KEY, getActivity()));
      }
      else {
        viewHolder.setColor(type, DEFAULT_TEXT_COLOR);
      }
      
      if(!channelSet) {
        String logoNamePref = PrefUtils.getStringValue(R.string.CHANNEL_LOGO_NAME_RUNNING, R.string.channel_logo_name_running_default);
        
        boolean showChannelName = logoNamePref.equals("0") || logoNamePref.equals("2");
        boolean showChannelLogo = logoNamePref.equals("0") || logoNamePref.equals("1");
        boolean showBigChannelLogo = logoNamePref.equals("3");
        
        Drawable logo = null;
        
        if(showBigChannelLogo) {
          logo = SettingConstants.MEDIUM_LOGO_MAP.get(block.mChannelID);
        }
        else if(showChannelLogo) {
          logo = SettingConstants.SMALL_LOGO_MAP.get(block.mChannelID);
        }
        
        if(logo != null) {
          viewHolder.mChannelLogo.setImageDrawable(logo);
          viewHolder.mChannelLogo.setVisibility(View.VISIBLE);
        }
        else {
          viewHolder.mChannelLogo.setVisibility(View.GONE);
        }
        
        String shortName = SettingConstants.SHORT_CHANNEL_NAMES.get(block.mChannelName);
        
        if(shortName == null) {
          shortName = block.mChannelName;
        }
        
        if(mShowOrderNumber && (logo == null || showChannelName)) {
          shortName = block.mChannelOrderNumber + ". " + shortName;
        }
        else if(mShowOrderNumber) {
          shortName = block.mChannelOrderNumber + ".";
        }
        
        if(logo == null || mShowOrderNumber || showChannelName) {
          viewHolder.mChannel.setText(shortName);
          viewHolder.mChannel.setVisibility(View.VISIBLE);
        }
        else {
          viewHolder.mChannel.setVisibility(View.GONE);
        }
        
        viewHolder.mChannelInfo.setTag(block.mChannelID);
        viewHolder.mChannelInfo.setOnClickListener(mChannelSwitchListener);
        
        channelSet = true;
      }
      
      layout.setTag(Long.valueOf(programID));
      layout.setOnClickListener(mOnClickListener);
      
      final String[] markingsValue = mMarkingsMap.get(programID);
      
      if(startTime <= System.currentTimeMillis() || (markingsValue != null && markingsValue.length > 0)) {
        final long startTime1 = startTime;
        final long endTime1 = endTime;
        final View layout1 = layout;
        
        new Thread() {
          public void run() {
            UiUtils.handleMarkings(getActivity(), null, startTime1, endTime1, layout1, markingsValue, handler);
          }
        }.start();
      }
      else {
        CompatUtils.setBackground(layout, getResources().getDrawable(android.R.drawable.list_selector_background));
      }
    }
    else {
      int viewType = View.GONE;
      boolean isPortrait = viewHolder.mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT;
      
      Configuration config = getResources().getConfiguration();
      
      if(Build.VERSION.SDK_INT >= 13) {
        if(type == CompactLayoutViewHolder.PREVIOUS) {
          if(config.smallestScreenWidthDp >= 600 && !isPortrait) {
            viewType = View.INVISIBLE;
          }
        }
        else if(type == CompactLayoutViewHolder.NOW && (config.smallestScreenWidthDp >= 600 || !isPortrait)) {
          viewType = View.INVISIBLE;
        }
      }
      
      viewHolder.setVisibility(type, viewType);
      
      if(type == CompactLayoutViewHolder.PREVIOUS) {
        viewHolder.setSeparatorVisibility(viewType);
      }
      
      titleView.setVisibility(View.GONE);
      episodeView.setVisibility(View.GONE);
      infoView.setVisibility(View.GONE);
    }
    
    return channelSet;
  }
  
  private View getCompactView(View convertView, ViewGroup parent, java.text.DateFormat timeFormat, ChannelProgramBlock block, int DEFAULT_TEXT_COLOR) {
    CompactLayoutViewHolder viewHolder = null;
    
    float textScale = Float.valueOf(PrefUtils.getStringValue(R.string.PREF_PROGRAM_LISTS_TEXT_SCALE, R.string.pref_program_lists_text_scale_default));
    
    if(convertView == null || ((CompactLayoutViewHolder)convertView.getTag()).orientationChanged(SettingConstants.ORIENTATION) || ((CompactLayoutViewHolder)convertView.getTag()).mCurrentScale !=  textScale) {
      convertView = getActivity().getLayoutInflater().inflate(R.layout.compact_program_panel, parent, false);
      
      UiUtils.scaleTextViews(convertView, textScale);
      
      viewHolder = new CompactLayoutViewHolder();
      
      viewHolder.mCurrentOrientation = SettingConstants.ORIENTATION;
      viewHolder.mCurrentScale = textScale;
      
      viewHolder.mChannelInfo = (ViewGroup)convertView.findViewById(R.id.running_list_channel_info);
      viewHolder.mChannelLogo = (ImageView)convertView.findViewById(R.id.running_list_channel_logo);
      viewHolder.mChannel = (TextView)convertView.findViewById(R.id.running_compact_channel_label);
      
      viewHolder.mSeparator1 = convertView.findViewById(R.id.running_separator_1);
      viewHolder.mSeparator2 = convertView.findViewById(R.id.running_separator_2);
      
      viewHolder.mPrevious = convertView.findViewById(R.id.running_compact_previous);
      viewHolder.mNow = convertView.findViewById(R.id.running_compact_now);
      viewHolder.mNext = convertView.findViewById(R.id.running_compact_next);
      
      registerForContextMenu(viewHolder.mPrevious);
      registerForContextMenu(viewHolder.mNow);
      registerForContextMenu(viewHolder.mNext);
      
      viewHolder.mPreviousStartTime = (TextView)convertView.findViewById(R.id.running_compact_previous_start);
      viewHolder.mNowStartTime = (TextView)convertView.findViewById(R.id.running_compact_now_start);
      viewHolder.mNextStartTime = (TextView)convertView.findViewById(R.id.running_compact_next_start);
      
      viewHolder.mPreviousTitle = (TextView)convertView.findViewById(R.id.running_compact_previous_title);
      viewHolder.mNowTitle = (TextView)convertView.findViewById(R.id.running_compact_now_title);
      viewHolder.mNextTitle = (TextView)convertView.findViewById(R.id.running_compact_next_title);
      
      viewHolder.mPreviousInfos = (TextView)convertView.findViewById(R.id.running_compact_previous_infos);
      viewHolder.mNowInfos = (TextView)convertView.findViewById(R.id.running_compact_now_infos);
      viewHolder.mNextInfos = (TextView)convertView.findViewById(R.id.running_compact_next_infos);
      
      viewHolder.mPreviousEpisode = (TextView)convertView.findViewById(R.id.running_compact_previous_episode);
      viewHolder.mNowEpisode = (TextView)convertView.findViewById(R.id.running_compact_now_episode);
      viewHolder.mNextEpisode = (TextView)convertView.findViewById(R.id.running_compact_next_episode);
      
      convertView.setTag(viewHolder);
    }
    else {
      viewHolder = (CompactLayoutViewHolder)convertView.getTag();
    }
    
    if(viewHolder != null && block != null /*&& mCurrentCursor != null && !mCurrentCursor.isClosed()*/) {
      boolean channelSet = false;
      
      if(mWhereClauseTime != -1) {
        viewHolder.setSeparatorVisibility(View.VISIBLE);
        channelSet = fillCompactLayout(viewHolder, CompactLayoutViewHolder.PREVIOUS, block, timeFormat, DEFAULT_TEXT_COLOR, channelSet);
      }
      else {       
        viewHolder.setVisibility(CompactLayoutViewHolder.PREVIOUS, View.GONE);
        viewHolder.setSeparatorVisibility(View.GONE);
      }
      
      channelSet = fillCompactLayout(viewHolder, CompactLayoutViewHolder.NOW, block, timeFormat, DEFAULT_TEXT_COLOR, channelSet);
      channelSet = fillCompactLayout(viewHolder, CompactLayoutViewHolder.NEXT, block, timeFormat, DEFAULT_TEXT_COLOR, channelSet);
      
      ViewGroup group = (ViewGroup)convertView;
      
      for(int i = 0; i < group.getChildCount(); i++) {
        View child = group.getChildAt(i);
        
        if(child instanceof LinearLayout) {
          RelativeLayout.LayoutParams para = (RelativeLayout.LayoutParams)child.getLayoutParams();
        
          para.height = -2;
        }
      }
    }
    
    return convertView;
  }
    
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
    pref.registerOnSharedPreferenceChangeListener(this);
    
    if(savedInstanceState != null) {
      mWhereClauseTime = savedInstanceState.getInt(WHERE_CLAUSE_KEY,-1);
      mDayStart = savedInstanceState.getLong(DAY_CLAUSE_KEY,-1);
    }
    else {
      mWhereClauseTime = -1;
      mDayStart = -1;
    }
        
    mMarkingsMap = new LongSparseArray<String[]>();
    mTitleMap = new LongSparseArray<String>();
    
    mOnClickListener = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Long tag = (Long)v.getTag();
        
        if(tag != null) {
          UiUtils.showProgramInfo(getActivity(), tag.longValue(), getActivity().getCurrentFocus(), handler);
        }
      }
    };
    
    mChannelSwitchListener = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Integer id = (Integer)v.getTag();
        boolean handle = PrefUtils.getBooleanValue(R.string.PREF_RUNNING_LIST_CLICK_TO_CHANNEL_TO_LIST, R.bool.pref_running_list_click_to_channel_to_list_default);
        
        if(handle && id != null) {
          Intent showChannel = new Intent(SettingConstants.SHOW_ALL_PROGRAMS_FOR_CHANNEL_INTENT);
          showChannel.putExtra(SettingConstants.CHANNEL_ID_EXTRA,id);
          
          Calendar now = Calendar.getInstance();
          
          if(mDayStart > System.currentTimeMillis()) {
            now.setTimeInMillis(mDayStart);
          }
          
          if(mWhereClauseTime >= 0) {
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            now.set(Calendar.HOUR_OF_DAY, mWhereClauseTime / 60);
            now.set(Calendar.MINUTE, mWhereClauseTime % 60);
          }
          
          showChannel.putExtra(SettingConstants.START_TIME_EXTRA, now.getTimeInMillis());
          
          LocalBroadcastManager.getInstance(getActivity()).sendBroadcastSync(showChannel);
        }
      }
    };
        
    mProgramBlockList = new ArrayList<FragmentProgramsListRunning.ChannelProgramBlock>();
    mCurrentViewList = new ArrayList<FragmentProgramsListRunning.ChannelProgramBlock>();
    
    java.text.DateFormat mTimeFormat = DateFormat.getTimeFormat(getActivity());
    String value = ((SimpleDateFormat)mTimeFormat).toLocalizedPattern();
    
    if((value.charAt(0) == 'H' && value.charAt(1) != 'H') || (value.charAt(0) == 'h' && value.charAt(1) != 'h')) {
      value = value.charAt(0) + value;
    }
    
    final java.text.DateFormat timeFormat = new SimpleDateFormat(value, Locale.getDefault());
    final int DEFAULT_TEXT_COLOR = new TextView(getActivity()).getTextColors().getDefaultColor();
        
    mRunningProgramListAdapter = new ArrayAdapter<FragmentProgramsListRunning.ChannelProgramBlock>(getActivity(), R.layout.running_list_entries, mCurrentViewList) {
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        ChannelProgramBlock block = getItem(position);
        
       // if(mIsCompactLayout) {
          return getCompactView(convertView, parent, timeFormat, block, DEFAULT_TEXT_COLOR);
        /*}
        else {
          return getLongView(convertView, parent, timeFormat, block, DEFAULT_TEXT_COLOR);
        }*/
      }
    };
    
    mListView.setAdapter(mRunningProgramListAdapter);
    
    SeparatorDrawable drawable = new SeparatorDrawable(getActivity());
    
    getListView().setDivider(drawable);
    
    setDividerSize(PrefUtils.getStringValue(R.string.PREF_RUNNING_DIVIDER_SIZE, R.string.pref_running_divider_size_default));
    
    getLoaderManager().initLoader(0, null, this);
  }
  
  @Override
  public void onDetach() {
    super.onDetach();
    
    if(mDataUpdateReceiver != null) {
      getActivity().unregisterReceiver(mDataUpdateReceiver);
    }
    if(mRefreshReceiver != null) {
      LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mRefreshReceiver);
    }
    if(mMarkingChangeReceiver != null) {
      LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mMarkingChangeReceiver);
    }
    if(mDontWantToSeeReceiver != null) {
      LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mDontWantToSeeReceiver);
    }
    if(mChannelUpdateDone != null) {
      LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mChannelUpdateDone);
    }
    
    mKeepRunning = false;
  }
    
  private synchronized void startUpdateThread() {
    if(mKeepRunning && (mUpdateThread == null || !mUpdateThread.isAlive())) {
      mUpdateThread = new Thread() {
        public void run() {
          handler.post(new Runnable() {
            @Override
            public void run() {
              if(!isDetached() &&  mKeepRunning && !isRemoving()) {
                getLoaderManager().restartLoader(0, null, FragmentProgramsListRunning.this);
              }
            }
          });
        }
      };
      mUpdateThread.start();
    }
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.running_program_fragment, container, false);
    
    mListView = (ListView)view.findViewById(R.id.running_list_fragment_list_view);
    mTimeBar = (LinearLayout)view.findViewById(R.id.runnning_time_bar);
    
    initialize(view);
    
    return view;
  }
  
  private void initialize(View rootView) {
    final Button now = (Button)rootView.findViewById(R.id.now_button);
    mDateSelection = (Spinner)rootView.findViewById(R.id.running_date_selection);
    now.setTag(Integer.valueOf(-1));
    
    final View.OnClickListener listener = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if(v.equals(now) && mDateSelection.getCount() > 1) {
          mDateSelection.setSelection(1);
        }
        
        setWhereClauseTime(v.getTag());
      }
    };
    
    LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
    
    IntentFilter timeButtonsUpdateFilter = new IntentFilter(SettingConstants.UPDATE_TIME_BUTTONS);
    
    final BroadcastReceiver receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        for(int i = mTimeBar.getChildCount() - 1; i >= 0; i--) {
          Button button = (Button)mTimeBar.getChildAt(i);
          
          if(button != null) {
            button.setOnClickListener(null);
            mTimeBar.removeViewAt(i);
          }
        }
        
        if(getActivity() != null) {
          SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
          now.setOnClickListener(listener);
          
          mTimeBar.addView(now);
          
          ArrayList<Integer> values = new ArrayList<Integer>();
          
          int[] defaultValues = getResources().getIntArray(R.array.time_button_defaults);
          
          int timeButtonCount = pref.getInt(getString(R.string.TIME_BUTTON_COUNT),getResources().getInteger(R.integer.time_button_count_default));
          
          for(int i = 1; i <= Math.min(timeButtonCount, getResources().getInteger(R.integer.time_button_count_default)); i++) {
            try {
              Class<?> string = R.string.class;
              
              Field setting = string.getDeclaredField("TIME_BUTTON_" + i);
              
              Integer value = Integer.valueOf(pref.getInt(getResources().getString((Integer)setting.get(string)), defaultValues[i-1]));
              
              if(value >= -1 && !values.contains(value)) {
                values.add(value);
              }
            } catch (Exception e) {}
          }
          
          for(int i = 7; i <= timeButtonCount; i++) {
              Integer value = Integer.valueOf(pref.getInt("TIME_BUTTON_" + i, 0));
              
              if(value >= -1 && !values.contains(value)) {
                values.add(value);
              }
          }
          
          if(PrefUtils.getBooleanValue(R.string.SORT_RUNNING_TIMES, R.bool.sort_running_times_default)) {
            Collections.sort(values);
          }
          
          for(Integer value : values) {
            getActivity().getLayoutInflater().inflate(R.layout.time_button, mTimeBar);
            
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, value / 60);
            cal.set(Calendar.MINUTE, value % 60);
            
            Button time = (Button)mTimeBar.getChildAt(mTimeBar.getChildCount()-1);
            time.setText(DateFormat.getTimeFormat(getActivity().getApplicationContext()).format(cal.getTime()));
            time.setTag(value);
            time.setOnClickListener(listener);
          }
        }
      }
    };
    
    localBroadcastManager.registerReceiver(receiver, timeButtonsUpdateFilter);
    receiver.onReceive(null, null);
    
    ArrayList<DateSelection> dateEntries = new ArrayList<DateSelection>();
    
    mDateAdapter = new ArrayAdapter<DateSelection>(getActivity(), android.R.layout.simple_spinner_item, dateEntries);
    mDateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mDateSelection.setAdapter(mDateAdapter);

    mDateSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, 
          int pos, long id) {
        DateSelection selection = mDateAdapter.getItem(pos);
        
        setDay(selection.getTime());
      }
      
      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        setDay(-1);
      }
    });
    
    updateDateSelection();
    
    if(mDateSelection.getCount() > 1) {
      mDateSelection.setSelection(1);
    }
  }

  private void updateDateSelection() {
    if(getActivity() != null && !isDetached() && mDateSelection != null) {
      int pos = mDateSelection.getSelectedItemPosition();
      
      mDateAdapter.clear();
      
      long last = PrefUtils.getLongValueWithDefaultKey(R.string.META_DATA_DATE_LAST_KNOWN, R.integer.meta_data_date_known_default);
      
      Calendar lastDay = Calendar.getInstance();
      lastDay.setTimeInMillis(last);
      
      lastDay.set(Calendar.HOUR_OF_DAY, 4);
      lastDay.set(Calendar.MINUTE, 0);
      lastDay.set(Calendar.SECOND, 0);
      lastDay.set(Calendar.MILLISECOND, 0);
      
      Calendar yesterday = Calendar.getInstance();
      yesterday.set(Calendar.HOUR_OF_DAY, 4);
      yesterday.set(Calendar.MINUTE, 0);
      yesterday.set(Calendar.SECOND, 0);
      yesterday.set(Calendar.MILLISECOND, 0);
      yesterday.add(Calendar.DAY_OF_YEAR, -1);
      
      long yesterdayStart = yesterday.getTimeInMillis();
      long lastStart = lastDay.getTimeInMillis();
      
      Calendar cal = Calendar.getInstance();
      
      for(long day = yesterdayStart; day <= lastStart; day += (24 * 60 * 60000)) {
        cal.setTimeInMillis(day);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        
        mDateAdapter.add(new DateSelection(cal.getTimeInMillis(), getActivity()));
      }
      
      if(mDateSelection.getCount() > pos) {
        mDateSelection.setSelection(pos);
      }
      else {
        mDateSelection.setSelection(mDateSelection.getCount()-1);
      }
    }
  }
  
  private ListView getListView() {
    return mListView;
  }
  
  @Override
  public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
    String[] infoCategories = TvBrowserContentProvider.INFO_CATEGORIES_COLUMNS_ARRAY;
    int startIndex = 13 + infoCategories.length;
    
    String[] projection = new String[startIndex + TvBrowserContentProvider.MARKING_COLUMNS.length];
    
    showEpisode = PrefUtils.getBooleanValue(R.string.SHOW_EPISODE_IN_RUNNING_LIST, R.bool.show_episode_in_running_list_default);
    showInfo = PrefUtils.getBooleanValue(R.string.SHOW_INFO_IN_RUNNING_LIST, R.bool.show_info_in_running_list_default);
    mShowOrderNumber = PrefUtils.getBooleanValue(R.string.SHOW_SORT_NUMBER_IN_RUNNING_LIST, R.bool.show_sort_number_in_running_list_default);
    
    projection[0] = TvBrowserContentProvider.KEY_ID;
    projection[1] = TvBrowserContentProvider.CHANNEL_KEY_CHANNEL_ID;
    projection[2] = TvBrowserContentProvider.DATA_KEY_STARTTIME;
    projection[3] = TvBrowserContentProvider.DATA_KEY_ENDTIME;
    projection[4] = TvBrowserContentProvider.DATA_KEY_TITLE;
    projection[5] = TvBrowserContentProvider.DATA_KEY_SHORT_DESCRIPTION;
    projection[6] = TvBrowserContentProvider.CHANNEL_KEY_ORDER_NUMBER;
    projection[7] = TvBrowserContentProvider.DATA_KEY_EPISODE_TITLE;
    projection[8] = TvBrowserContentProvider.DATA_KEY_GENRE;
    projection[9] = TvBrowserContentProvider.DATA_KEY_PICTURE_COPYRIGHT;
    projection[10] = TvBrowserContentProvider.DATA_KEY_CATEGORIES;
    projection[11] = TvBrowserContentProvider.CHANNEL_KEY_NAME;
    projection[12] = TvBrowserContentProvider.DATA_KEY_DONT_WANT_TO_SEE;
    
    for(int i = 0; i < infoCategories.length; i++) {
      projection[13+i] = infoCategories[i];
    }
    
    for(int i = startIndex ; i < (startIndex + TvBrowserContentProvider.MARKING_COLUMNS.length); i++) {
      projection[i] = TvBrowserContentProvider.MARKING_COLUMNS[i-startIndex];
    }
    
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 30);
    
    Calendar now = Calendar.getInstance();
    now.setTimeInMillis(System.currentTimeMillis());
    
    if(mWhereClauseTime >= 0) {
      if(mDayStart >= 0) {
        cal.setTimeInMillis(mDayStart);
      }
      
      cal.set(Calendar.HOUR_OF_DAY, mWhereClauseTime / 60);
      cal.set(Calendar.MINUTE, mWhereClauseTime % 60);
    }
    if(mWhereClauseTime < 0) {
      cal.setTimeInMillis(System.currentTimeMillis());
    }
    
    if(getView().getParent() != null) {
      Button test = (Button)((View)getView().getParent()).findViewWithTag(Integer.valueOf(mWhereClauseTime));
      
      if(test != null) {
        test.setBackgroundColor(UiUtils.getColor(UiUtils.RUNNING_TIME_SELECTION_KEY, getActivity()));
      }      
    }
    
    mCurrentTime = ((long)cal.getTimeInMillis() / 60000) * 60000;
    
    String sort = TvBrowserContentProvider.DATA_KEY_STARTTIME + " ASC";
    
    String where = " ( ( "  + TvBrowserContentProvider.DATA_KEY_ENDTIME + "<=" + mCurrentTime + " AND " + TvBrowserContentProvider.DATA_KEY_ENDTIME + ">" + (mCurrentTime - (60000 * 60 * 12)) + " ) ";
    
    if(mWhereClauseTime == -1) {
      where = " ( ( " + TvBrowserContentProvider.DATA_KEY_ENDTIME + ">" + mCurrentTime + " AND " + TvBrowserContentProvider.DATA_KEY_STARTTIME + "<" + (mCurrentTime + (60000 * 60 * 12)) + " ) ";
    }
    else {
      where += " OR ( " + TvBrowserContentProvider.DATA_KEY_ENDTIME + ">" + mCurrentTime + " AND " + TvBrowserContentProvider.DATA_KEY_STARTTIME + "<" + (mCurrentTime + (60000 * 60 * 12)) + " ) ";
    }
    
    where += ") " + UiUtils.getDontWantToSeeFilterString(getActivity());
    
    where += ((TvBrowser)getActivity()).getFilterSelection(false);
    
    CursorLoader loader = new CursorLoader(getActivity(), TvBrowserContentProvider.CONTENT_URI_DATA_WITH_CHANNEL, projection, where, null, TvBrowserContentProvider.CHANNEL_KEY_ORDER_NUMBER + " , " + TvBrowserContentProvider.CHANNEL_KEY_CHANNEL_ID + " COLLATE NOCASE, " + sort);
    
    return loader;
  }
  
  private static final class ChannelProgramBlock {
    public int mChannelID;
    private String mChannelName;
    private int mChannelOrderNumber;
    
    public int mPreviousPosition;
    public long mPreviousStart;
    public long mPreviousEnd;
    public long mPreviousProgramID;
//    public String mPreviousTitle;
    public String mPreviousEpisode;
    public String mPreviousGenre;
    public Spannable mPreviousCategory;
    public String mPreviousPictureCopyright;
    public byte[] mPreviousPicture;

    public int mNowPosition;
    public long mNowStart;
    public long mNowEnd;
    public long mNowProgramID;
   // public String mNowTitle;
    public String mNowEpisode;
    public String mNowGenre;
    public Spannable mNowCategory;
    public String mNowPictureCopyright;
    public byte[] mNowPicture;
    
    public int mNextPosition;
    public long mNextStart;
    public long mNextEnd;
    public long mNextProgramID;
   // public String mNextTitle;
    public String mNextEpisode;
    public String mNextGenre;
    public Spannable mNextCategory;
    public String mNextPictureCopyright;
    public byte[] mNextPicture;

    public boolean mIsComplete;
    
    public ChannelProgramBlock() {
      mIsComplete = false;
    }
  }
  
  @Override
  public synchronized void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, final Cursor c) {
    if(c != null) {
      SparseArray<ChannelProgramBlock> channelProgramMap = new SparseArray<ChannelProgramBlock>();
      SparseArray<ChannelProgramBlock> currentProgramMap = new SparseArray<ChannelProgramBlock>();
      boolean showDontWantToSee = PrefUtils.getStringValue(R.string.PREF_I_DONT_WANT_TO_SEE_FILTER_TYPE, R.string.pref_i_dont_want_to_see_filter_type_default).equals(getResources().getStringArray(R.array.pref_simple_string_value_array2)[1]);
      
      mProgramBlockList.clear();
      mCurrentViewList.clear();
      mMarkingsMap.clear();
      mTitleMap.clear();
      
      mProgramIDColumn = c.getColumnIndex(TvBrowserContentProvider.KEY_ID);
      mStartTimeColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_STARTTIME);
      mEndTimeColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_ENDTIME);
      mTitleColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_TITLE);
      mPictureColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_PICTURE);
      mPictureCopyrightColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_PICTURE_COPYRIGHT);
      mCategoryColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_CATEGORIES);
      mGenreColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_GENRE);
      mEpsiodeColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_EPISODE_TITLE);
      mChannelNameColumn = c.getColumnIndex(TvBrowserContentProvider.CHANNEL_KEY_NAME);
      mChannelIDColumn = c.getColumnIndex(TvBrowserContentProvider.CHANNEL_KEY_CHANNEL_ID);
      int channelOrderColumn = c.getColumnIndex(TvBrowserContentProvider.CHANNEL_KEY_ORDER_NUMBER);
      int dontWantToSeeColumn = c.getColumnIndex(TvBrowserContentProvider.DATA_KEY_DONT_WANT_TO_SEE);
      
      HashMap<String, Integer> markingColumnsMap = new HashMap<String, Integer>();
      
      c.moveToPosition(-1);
      
      for(String column : TvBrowserContentProvider.MARKING_COLUMNS) {
        int index = c.getColumnIndex(column);
        
        if(index >= 0) {
          markingColumnsMap.put(column, Integer.valueOf(index));
        }
      }
      
      if(c.getCount() > 0) {
        try {
          while(!c.isClosed() && c.moveToNext()) {
            int channelID = c.getInt(mChannelIDColumn);
            
            ChannelProgramBlock block = channelProgramMap.get(channelID);
            ArrayList<String> markedColumsList = new ArrayList<String>();
            
            if(block == null) {
              block = new ChannelProgramBlock();
              
              channelProgramMap.put(channelID, block);
              mProgramBlockList.add(block);
            }
            
            if(!block.mIsComplete) {
              long startTime = c.getLong(mStartTimeColumn);
              long endTime = c.getLong(mEndTimeColumn);
              long programID = c.getLong(mProgramIDColumn);
              String title = c.getString(mTitleColumn);
              String episode = c.getString(mEpsiodeColumn);
              
              for(String column : TvBrowserContentProvider.MARKING_COLUMNS) {
                Integer value = markingColumnsMap.get(column);
                
                if(value != null && c.getInt(value.intValue()) >= 1) {
                  markedColumsList.add(column);
                }
                else if(column.equals(TvBrowserContentProvider.DATA_KEY_MARKING_MARKING) && ProgramUtils.isMarkedWithIcon(getActivity(), programID)) {
                  markedColumsList.add(column);
                }
              }
              
              String channelName = c.getString(mChannelNameColumn);
              int channelOrderNumber = c.getInt(channelOrderColumn);
      
              String genre = null;
              Spannable category = null;
              String pictureCopyright = null;
              byte[] picture = null;
              
              if(showInfo) {
                category = IOUtils.getInfoString(c.getInt(mCategoryColumn), getResources());
              }
                          
              if(showDontWantToSee || c.getInt(dontWantToSeeColumn) == 0) {
                block.mChannelID = channelID;
                block.mChannelName = channelName;
                block.mChannelOrderNumber = channelOrderNumber;
                
                if(startTime <= mCurrentTime) {
                  if(endTime <= mCurrentTime) {
                    block.mPreviousPosition = c.getPosition();
                    block.mPreviousProgramID = programID;
                    block.mPreviousStart = startTime;
                    block.mPreviousEnd = endTime;
                    mTitleMap.put(programID, title);
                    block.mPreviousEpisode = episode;
                    block.mPreviousGenre = genre;
                    block.mPreviousPicture = picture;
                    block.mPreviousPictureCopyright = pictureCopyright;
                    block.mPreviousCategory = category;
                  }
                  else if(startTime <= mCurrentTime && mCurrentTime < endTime) {
                    block.mNowPosition = c.getPosition();
                    block.mNowProgramID = programID;
                    block.mNowStart = startTime;
                    block.mNowEnd = endTime;
                    mTitleMap.put(programID, title);
                    block.mNowEpisode = episode;
                    block.mNowGenre = genre;
                    block.mNowPicture = picture;
                    block.mNowPictureCopyright = pictureCopyright;
                    block.mNowCategory = category;
                    
                    if(currentProgramMap.indexOfKey(channelID) < 0) { 
                      currentProgramMap.put(channelID, block);
                      mCurrentViewList.add(block);
                    }
                  }
                }
                else {
                  block.mNextPosition = c.getPosition();
                  block.mNextStart = startTime;
                  block.mNextEnd = endTime;
                  block.mNextProgramID = programID;
                  mTitleMap.put(programID, title);
                  block.mNextEpisode = episode;
                  block.mNextGenre = genre;
                  block.mNextPicture = picture;
                  block.mNextPictureCopyright = pictureCopyright;
                  block.mNextCategory = category;
                  
                  block.mIsComplete = true;
                  
                  if(currentProgramMap.indexOfKey(channelID) < 0) { 
                    currentProgramMap.put(channelID, block);
                    mCurrentViewList.add(block);
                  }
                }
                
                mMarkingsMap.put(programID, IOUtils.getStringArrayFromList(markedColumsList));
                markedColumsList.clear();
                markedColumsList = null;
              }
            }
          }
        }catch(IllegalStateException e1) {}
      }
      
      c.close();
      currentProgramMap.clear();
      channelProgramMap.clear();
    }
    
    mRunningProgramListAdapter.notifyDataSetChanged();
  }

  @Override
  public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {
    mCurrentViewList.clear();
    mProgramBlockList.clear();
  }
  
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    Long test = (Long)v.getTag();
    
    if(test != null) {
      mContextProgramID = test.longValue();
      mContextView = v;
      UiUtils.createContextMenu(getActivity(), menu, mContextProgramID);
    }
  }
  
  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if(getUserVisibleHint() && mContextProgramID >= 0) {
      UiUtils.handleContextMenuSelection(getActivity(), item, mContextProgramID, mContextView, getActivity().getCurrentFocus());
      
      mContextProgramID = -1;
      return true;
    }
    
    return false;
  }
  
  private void setDividerSize(String size) {    
    getListView().setDividerHeight(UiUtils.convertDpToPixel(Integer.parseInt(size), getResources()));
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if(!isDetached() && getActivity() != null) {
      if(getString(R.string.PREF_RUNNING_DIVIDER_SIZE).equals(key)) {
        setDividerSize(PrefUtils.getStringValue(R.string.PREF_RUNNING_DIVIDER_SIZE, R.string.pref_running_divider_size_default));
      }
    }
  }
  
  public void selectTime(int time) {
    for(int i = 0; i < mTimeBar.getChildCount(); i++) {
      View button = mTimeBar.getChildAt(i);
      
      if(button.getTag().equals(Integer.valueOf(time-1))) {
        button.performClick();
        ((HorizontalScrollView)mTimeBar.getParent()).scrollTo(button.getLeft(), button.getTop());
        break;
      }
    }
  }
}
