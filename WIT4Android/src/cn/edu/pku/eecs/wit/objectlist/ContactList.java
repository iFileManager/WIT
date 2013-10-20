package cn.edu.pku.eecs.wit.objectlist;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.stream.WitStream.ObjectType;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

public class ContactList extends ObjectList						//联系人列表
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		type = ObjectType.Contact;
	}
	
	@Override
	public ObjectAdapter getAdapter()
	{
		return new ContactAdapter();
	}
	
	@Override
	public String getDefaultTitle()
	{
		return getString(R.string.contact);
	}
	
	@Override
	public void open(int position)								//打开
	{
		try
		{
			int nID = (Integer) lstMap.get(position).get("id");
			Intent intent = new Intent(Intent.ACTION_VIEW);
			Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, nID);
			
			intent.setData(uri); 
			startActivity(intent);
		}
		catch (Exception e)
		{
			showShortToast(getString(R.string.openFailed));
		}
	}
	
	class ContactAdapter extends ObjectAdapter					//联系人列表适配器
	{		
		@Override
		public  void load()										//加载
		{
			ContentResolver resolver = getContentResolver();
			Cursor cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null,null);
			
			int nIndex, nID, nAvatar;
			String szName, szNumber;
			Bitmap avatar;
			
			List<Map<String, Object> > lstNewMap = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
			
			if (cursor != null)
			{
				while (cursor.moveToNext())
				{
					nIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME);
					szName = cursor.getString(nIndex);
					
					nIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID);
					nID = cursor.getInt(nIndex);
					
					szNumber = "";
					Cursor cursor2 = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,  
							ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + nID, null, null);
					
					if (cursor2 != null)
					{
						while(cursor2.moveToNext())
						{
							nIndex = cursor2.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
							szNumber += cursor2.getString(nIndex) + " ";
						}
						szNumber = szNumber.trim();
						cursor2.close();
					}
					
					nIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_ID);
					nAvatar = cursor.getInt(nIndex);
					
					if (nAvatar > 0)
					{
						Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, nID);
						InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(resolver, uri);
						avatar = BitmapFactory.decodeStream(input); 
					}
					else avatar = null;
					
					Map<String, Object> map = Collections.synchronizedMap(new HashMap<String, Object>());
					map.put("data", szName);
					map.put("number", szNumber);
					map.put("avatar", avatar);
					map.put("id", nID);
					
					lstNewMap.add(map);
					refreshListView(lstNewMap);
				}
				
				cursor.close();
			}
		}
		
		@Override
		public void setView(ObjectItem item, Map<String, Object> map)//设置view
		{
			Object obj = map.get("avatar");
			Bitmap avatar = obj != null ? (Bitmap) obj : BitmapFactory.decodeResource(getResources(), R.drawable.people);
			
			item.imgIcon.setImageBitmap(avatar);
			
			item.txtTitle.setText((String) map.get("data"));
			item.txtSubTitle.setText((String) map.get("number"));
		}
		
		public String getData(int position)							//保存数据（暂时只提供姓名和号码）
		{
			try
			{
			    String data;
				
				Map<String, Object> map = lstMap.get(position);
				data = map.get("number").toString();
				return data;
			}
			catch (Exception e)
			{
				return null;
			}
		}
		
		@Override
		public String getShortName(int position)					//返回名称
		{
			return (String) lstMap.get(position).get("data");
		}
	}
}
