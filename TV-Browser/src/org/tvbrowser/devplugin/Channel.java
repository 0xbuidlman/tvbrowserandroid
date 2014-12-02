/*
 * TV-Browser for Android
 * Copyright (C) 2014 René Mach (rene@tvbrowser.org)
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
package org.tvbrowser.devplugin;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * A parcelable class with informations about a TV-Browser channel.
 * 
 * @author René Mach
 */
public final class Channel implements Parcelable {
  private static final int VERSION = 1;
  
  private int mId;
  private String mChannelName;
  private byte[] mChannelIcon;
  
  public static final Parcelable.Creator<Channel> CREATOR = new Parcelable.Creator<Channel>() {
    @Override
    public Channel createFromParcel(Parcel source) {
      return new Channel(source);
    }

    @Override
    public Channel[] newArray(int size) {
      return new Channel[size];
    }
  };
  
  public Channel(Parcel source) {
    readFromParcel(source);
  }
  
  public Channel(int id, String channelName, byte[] channelIcon) {
    mId = id;
    mChannelName = channelName;
    mChannelIcon = channelIcon;
  }
  
  public int getChannelId() {
    return mId;
  }
  
  public String getChannelName() {
    return mChannelName;
  }
  
  public byte[] getIcon() {
    return mChannelIcon;
  }
  
  public int getVersion() {
    return VERSION;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public void readFromParcel(Parcel source) {
    source.readInt(); // read version
    mId = source.readInt();
    mChannelName = source.readString();
    
    int iconSize = source.readInt();
    Log.d("info23"," ICON SIZE " + iconSize);
    if(iconSize > 0) {
      mChannelIcon = new byte[iconSize];
      source.readByteArray(mChannelIcon);
    }
  }
  
  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(VERSION);
    dest.writeInt(mId);
    dest.writeString(mChannelName);
    dest.writeInt((mChannelIcon != null ? mChannelIcon.length : 0));
    dest.writeByteArray(mChannelIcon);
  }
}
