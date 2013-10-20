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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;	
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Thumbnails;

public class PhotoList extends ObjectList						//图像列表
{	
	@Override
	public void onCreate(Bundle savedInstanceState)				//创建
	{
		super.onCreate(savedInstanceState);
		type = ObjectType.Photo;
	}
	
	@Override
	public ObjectAdapter getAdapter()
	{
		return new PhotoAdapter();
	}
	
	@Override
	public String getDefaultTitle()
	{
		return getString(R.string.allphoto);
	}
	
	@Override
	public void open(int position)								//打开
	{
		try
		{
			File file = new File((String) lstMap.get(position).get("data"));
			Intent intent = new Intent(Intent.ACTION_VIEW);
			Uri uri = Uri.fromFile(file);
			
			intent.setDataAndType(uri, "image/*"); 
			startActivity(intent);
		}
		catch (Exception e)
		{
			showShortToast(getString(R.string.openFailed));
		}
	}
	
	class PhotoAdapter extends ObjectAdapter					//图像列表适配器
	{
		@Override
		public void load()										//加载图库
		{
			ContentResolver resolver = getContentResolver();
			Cursor cursor = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, null);
			
			int nIndex;
			Long nID;
			String szSize, szData;
			
			List<Map<String, Object> > lstNewMap = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
			
			if (cursor != null)
			{
				while(cursor.moveToNext())
				{
					nIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
					nID = cursor.getLong(nIndex);
					
					nIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
					szData = cursor.getString(nIndex);
					
					@SuppressWarnings("unused")
					Bitmap bitmap = null; 
					
					Options options = new Options();
					options.inJustDecodeBounds = true;
					bitmap = BitmapFactory.decodeFile(szData, options);//获取尺寸
					szSize = options.outWidth + " X " + options.outHeight;
					
					Map<String, Object> map = Collections.synchronizedMap(new HashMap<String, Object>());
					map.put("id", nID);
					map.put("size", szSize);
					map.put("data", szData);
					
					lstNewMap.add(map);
					refreshListView(lstNewMap);
				}
				
				cursor.close();
			}
		}
		
		@Override
		public void setView(ObjectItem item, Map<String, Object> map)//设置view
		{
			Bitmap bitmap = Thumbnails.getThumbnail(getContentResolver(), (Long) map.get("id"), Thumbnails.MICRO_KIND, null);
			if (bitmap != null) item.imgIcon.setImageBitmap(bitmap);
			item.txtTitle.setText((String) map.get("data"));
			item.txtSubTitle.setText((String) map.get("size"));
		}
	}
}
