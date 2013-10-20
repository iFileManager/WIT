package cn.edu.pku.eecs.wit.objectlist;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.stream.WitStream.ObjectType;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

public class MusicList extends ObjectList						//音乐列表
{
	@Override
	public void onCreate(Bundle savedInstanceState)				//创建
	{
		super.onCreate(savedInstanceState);
		type = ObjectType.Music;
	}
	
	@Override
	public ObjectAdapter getAdapter()
	{
		return new MusicAdapter();
	}
	
	@Override
	public String getDefaultTitle()
	{
		return getString(R.string.allmusic);
	}
	
	@Override
	public void open(int position)								//播放音乐
	{
		try
		{
			File file = new File((String) lstMap.get(position).get("data"));
			Intent intent = new Intent(Intent.ACTION_VIEW);
			Uri uri = Uri.fromFile(file);
			
			intent.setDataAndType(uri, "audio/*"); 
			startActivity(intent);
		}
		catch (Exception e)
		{
			showShortToast(getString(R.string.openFailed));
		}
	}
	
	class MusicAdapter extends ObjectAdapter					//音乐列表适配器
	{
		@Override
		public void load()										//加载音乐
		{
			ContentResolver resolver = getContentResolver();
			Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null, null);
			
			int nIndex;
			String szTitle, szArtist, szSize, szData;
			
			List<Map<String, Object> > lstNewMap = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
			
			if (cursor != null)
			{
				while (cursor.moveToNext())
				{
					nIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
					szTitle = cursor.getString(nIndex);
					if (szTitle == null) szTitle = "UNTITLED";
					
					nIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
					szArtist = cursor.getString(nIndex);
					if (szArtist != null) szTitle += " - " + szArtist;
					
					nIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
					szSize = cursor.getString(nIndex);
					
					nIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
					szData = cursor.getString(nIndex);
					
					Map<String, Object> map = Collections.synchronizedMap(new HashMap<String, Object>());	
					map.put("title", szTitle);
					map.put("size", szSize + " Bytes");
					map.put("data", szData);
					
					lstNewMap.add(map);
					refreshListView(lstNewMap);
				}
				
				cursor.close();
			}
		}
		
		@Override
		public void setView(ObjectItem item, Map<String, Object> map)//显示项
		{
			item.imgIcon.setImageResource(R.drawable.music);
			item.txtTitle.setText((String) map.get("title"));
			item.txtSubTitle.setText((String) map.get("data"));
		}
	}
}
