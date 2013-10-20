package cn.edu.pku.eecs.wit.objectlist;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.WitActivity;
import cn.edu.pku.eecs.wit.stream.WitStream.ObjectType;
import cn.edu.pku.eecs.wit.ui.ActivityWithHandler;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class FileList extends ObjectList						//文件列表
{
	private static ObjectList singleton;	
	private static File currentDir;
	
	public static void view(Context context, String szDir)		//打开指定目录
	{
		if (singleton != null && singleton.isRefreshing())
		{
			if (context instanceof ActivityWithHandler)
			{
				((ActivityWithHandler) context).showShortToast(context.getString(R.string.filelistRefreshing));
			}
			else
			{
				if (context instanceof WitActivity) ((WitActivity) context).showShortToast(context.getString(R.string.filelistRefreshing));
			}
			return;
		}
		
		try
		{
			File dir = new File(szDir);
			if (!dir.isDirectory())
			{
				if (context instanceof ActivityWithHandler)
				{
					((ActivityWithHandler) context).showShortToast(context.getString(R.string.openFailed));//打开失败
				}
				else
				{
					if (context instanceof WitActivity) ((WitActivity) context).showShortToast(context.getString(R.string.openFailed));
				}
				
				return;
			}
			
			 if (singleton != null) ((FileList) singleton).chdir(dir);
			 else currentDir = dir;
		}
		catch (Exception e)
		{
			if (context instanceof ActivityWithHandler)
			{
				((ActivityWithHandler) context).showShortToast(context.getString(R.string.openFailed));//打开失败
			}
			else
			{
				if (context instanceof WitActivity) ((WitActivity) context).showShortToast(context.getString(R.string.openFailed));
			}
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		type = ObjectType.File;
		singleton = this;
	}
	
	@Override
	public void onDestroy()
	{
		singleton = null;
		super.onDestroy();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view,ContextMenuInfo menuInfo)//上下文菜单
	{
		menu.add(0, OPEN, 0, getString(R.string.open));
		
    	int nPosition = ((AdapterContextMenuInfo) menuInfo).position;
    	boolean bFile = (Boolean) lstMap.get(nPosition).get("flag");
    	
		if (bFile) menu.add(0, SEND, 1, getString(R.string.send));
	}
	
	public void chdir(File dir)									//切换当前目录
	{
		currentDir = dir;
		load();
	}

	@Override
	public ObjectAdapter getAdapter()
	{
		return new FileAdapter();
	}
	
	@Override
	public String getDefaultTitle()
	{
		return currentDir.getAbsolutePath();
	}
	
	@Override
	public void open(int position)								//打开
	{
		String szName = (String) lstMap.get(position).get("data");
		File file = new File(szName);
		
		if (file.isDirectory()) chdir(file);					//打开目录
		else													//打开文件
		{
			try
			{
				Intent intent = new Intent(Intent.ACTION_VIEW);
				Uri uri = Uri.fromFile(file);
				
				intent.setDataAndType(uri, "*/*"); 
				startActivity(intent);
			}
			catch (Exception e)
			{
				showShortToast(getString(R.string.openFailed));
			}
		}
	}
	
	class FileAdapter extends ObjectAdapter						//文件列表适配器
	{
		public FileAdapter()
		{
			if (currentDir == null)	currentDir = Environment.getExternalStorageDirectory();
		}
		
		@Override
		public void load()
		{
			String szData;
			long nSize;
			
			String[] arrList = currentDir.list();
			
			List<Map<String, Object> > lstNewMap = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
			
			List<Map<String, Object> > lstFile, lstFolder;
			
			lstFile = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
			lstFolder = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
			
			if (!currentDir.equals(Environment.getExternalStorageDirectory()))
			{
				File file = new File(currentDir.getParent());
				Map<String, Object> map = Collections.synchronizedMap(new HashMap<String, Object>());
				
				map.put("size", "");
				map.put("data", file.getAbsolutePath());
				map.put("flag", false);
				map.put("parent", true);
				
				lstFolder.add(map);
				lstNewMap.clear();
				lstNewMap.addAll(lstFolder);
				refreshListView(lstNewMap);
			}
			
			if (arrList != null)
			{
				for (String szFile : arrList)
				{
					try
					{
						File file = new File(currentDir.getAbsolutePath() + "/" + szFile);
						boolean bFile = file.isFile();
						
						szData = file.getAbsolutePath();
					
						if (bFile)
						{
							FileInputStream fis = new FileInputStream(file);
							nSize = fis.available();
							fis.close();
						}
						else nSize = 0;
						
						Map<String, Object> map = Collections.synchronizedMap(new HashMap<String, Object>());	
						map.put("size", nSize);
						map.put("data", szData);
						map.put("flag", bFile);
						map.put("parent", false);
						
						if (bFile) lstFile.add(map);
						else lstFolder.add(map);
						
						lstNewMap.clear();
						lstNewMap.addAll(lstFolder);
						lstNewMap.addAll(lstFile);
						refreshListView(lstNewMap);
					}
					catch (Exception e) { }
				}
			}
			else
			{
				lstNewMap.clear();
				lstNewMap.addAll(lstFolder);
				refreshListView(lstNewMap);
			}
		}
		
		@Override
		public void setView(ObjectItem item, Map<String, Object> map)//设置显示项
		{
			String szName = (String) map.get("data");
			boolean bFile = (Boolean) map.get("flag");
			File file = new File(szName);

			if (bFile) item.imgIcon.setImageResource(R.drawable.file);
			else item.imgIcon.setImageResource(R.drawable.folder);
			
			item.txtTitle.setText((Boolean) map.get("parent") ? getString(R.string.upperDir) : file.getName());
			item.txtSubTitle.setText(bFile ? Formatter.formatShortFileSize(FileList.this, (Long) map.get("size")) : "");
		}
		
		@Override
		public void setVisibility(int position, int nVisibility)
		{
			int nOldVisibility = getVisibility(position);
			if (nVisibility == nOldVisibility) return;				//选中结果不变
			
			boolean bFile = (Boolean) lstMap.get(position).get("flag");
			int nFileItemVisibility = bFile ? nVisibility : View.GONE;//目录不显示复选框
			
			Message msg = new Message();
			msg.what = SET_VISIBILITY;
			msg.obj = new Pair<Integer, Integer>(position, nFileItemVisibility);
			
			handler.sendMessage(msg);		
		}
	}
}
