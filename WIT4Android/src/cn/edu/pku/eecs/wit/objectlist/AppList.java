package cn.edu.pku.eecs.wit.objectlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.stream.WitStream.ObjectType;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

public class AppList extends ObjectList							//应用列表
{	
	@Override
	public void onCreate(Bundle savedInstanceState)				//创建
	{
		super.onCreate(savedInstanceState);
		type = ObjectType.App;									//设置类型
	}
	
	@Override
	public ObjectAdapter getAdapter()
	{
		return new AppAdapter();
	}
	
	@Override
	public String getDefaultTitle()
	{
		return getString(R.string.allapp);
	}
	
	@Override
	public void open(int position)								//打开应用
	{
		try
		{
			String szPackage;
			szPackage = (String) lstMap.get(position).get("package");
			
			if (szPackage.equals("cn.edu.pku.eecs.wit")) return;
			
			Intent intent = getPackageManager().getLaunchIntentForPackage(szPackage);
			startActivity(intent);								//启动应用
		}
		catch (Exception e)
		{
			showLongToast(getString(R.string.openFailed));
		}
	}
	
	class AppAdapter extends ObjectAdapter						//应用列表适配器
	{		
		@Override
		public void load()
		{
			PackageManager manager = getPackageManager();
			List<PackageInfo> lstInfo = manager.getInstalledPackages(0);
			
			List<Map<String, Object> > lstNewMap = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
			
			for (PackageInfo info : lstInfo)					//获取各应用信息
			{
				ApplicationInfo app = info.applicationInfo;
				if ((app.flags & ApplicationInfo.FLAG_SYSTEM) > 0) continue;//禁止传系统应用
				if (!app.sourceDir.endsWith(".apk")) continue;
				
				Map<String, Object> map = Collections.synchronizedMap(new HashMap<String, Object>());
				map.put("name", app.loadLabel(manager).toString());
				map.put("version", info.versionName);
				map.put("icon", app.loadIcon(manager));
				map.put("data", app.sourceDir);
				
				map.put("package", app.packageName);
				
				lstNewMap.add(map);
				refreshListView(lstNewMap);
			}
		}
		
		@Override
		public void setView(ObjectItem item, Map<String, Object> map)
		{
			Object obj = map.get("icon");
			if (obj != null)
			{
				Drawable icon = (Drawable) obj;
				item.imgIcon.setImageDrawable(icon);
			}
			
			item.txtTitle.setText((String) map.get("name"));
			item.txtSubTitle.setText((String) map.get("version"));
		}
	}
}
