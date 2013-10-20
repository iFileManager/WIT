package cn.edu.pku.eecs.wit;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.card.CardList;
import cn.edu.pku.eecs.wit.card.CardViewer;
import cn.edu.pku.eecs.wit.network.MainActivity;
import cn.edu.pku.eecs.wit.network.WitService;
import cn.edu.pku.eecs.wit.objectlist.AppList;
import cn.edu.pku.eecs.wit.objectlist.ContactList;
import cn.edu.pku.eecs.wit.objectlist.FileList;
import cn.edu.pku.eecs.wit.objectlist.MusicList;
import cn.edu.pku.eecs.wit.objectlist.PhotoList;
import cn.edu.pku.eecs.wit.ui.AboutActivity;
import cn.edu.pku.eecs.wit.util.Settings;
import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class WitActivity extends TabActivity implements OnTabChangeListener, OnTouchListener //主Activity，用标签显示各项
{	
	private static WitActivity singleton;														//单例模式
	private static final int
		SETTING = Menu.FIRST,
		VIEW_SAVE_DIR = Menu.FIRST + 1,
		ABOUT = Menu.FIRST + 2;
	
	public static WitActivity getSingleton()
	{
		return singleton;
	}
	
	private TabHost tabHost;
	protected Handler handler;
	private Handler toastHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg) 													//接收显示Toast信息
		{   
			Toast toast = Toast.makeText(WitActivity.this, (String) msg.obj, msg.what);
			toast.getView().setBackgroundColor(getResources().getColor(R.color.darkerDarkTransparent));
			toast.show();
			
			super.handleMessage(msg);
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		singleton = this;																		//设置单例
		
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		{
			showLongToast(getString(R.string.noSDCard));
			finish();
		}
		
		if (!Settings.load(this))
		{
			Intent intent = new Intent(this, CardViewer.class);
			startActivity(intent);
		}
		
		setContentView(R.layout.wit);		
		tabHost = getTabHost();
		
		tabHost.addTab(tabHost.newTabSpec("home").setIndicator(getString(R.string.home),
				getResources().getDrawable(R.drawable.home_tab)).setContent(new Intent(this, MainActivity.class)));
		
        tabHost.addTab(tabHost.newTabSpec("photo").setIndicator(getString(R.string.photo),
				getResources().getDrawable(R.drawable.photo_tab)).setContent(new Intent(this, PhotoList.class)));
        tabHost.addTab(tabHost.newTabSpec("music").setIndicator(getString(R.string.music),
				getResources().getDrawable(R.drawable.music_tab)).setContent(new Intent(this, MusicList.class)));
        tabHost.addTab(tabHost.newTabSpec("app").setIndicator(getString(R.string.app),
				getResources().getDrawable(R.drawable.app_tab)).setContent(new Intent(this, AppList.class)));
        tabHost.addTab(tabHost.newTabSpec("contact").setIndicator(getString(R.string.contact),
				getResources().getDrawable(R.drawable.contact_tab)).setContent(new Intent(this, ContactList.class)));
        tabHost.addTab(tabHost.newTabSpec("file").setIndicator(getString(R.string.file),
				getResources().getDrawable(R.drawable.file_tab)).setContent(new Intent(this, FileList.class)));
        
        tabHost.addTab(tabHost.newTabSpec("card").setIndicator(getString(R.string.card),
				getResources().getDrawable(R.drawable.card_tab)).setContent(new Intent(this, CardList.class)));
        
        refreshTabBackground();
        tabHost.setOnTabChangedListener(this);
        
        int nCount = tabHost.getTabWidget().getChildCount();
		
		for (int i = 0; i < nCount; ++i)
		{
			View v = tabHost.getTabWidget().getChildAt(i);										//选中项和非选中项背景不同
			v.setOnTouchListener(this);															//各项设置监听
		}
	}
	
	@Override
	public void onDestroy()
	{
		singleton = null;
		super.onDestroy();
	}
	
	public boolean onCreateOptionsMenu(Menu menu)												//选项菜单
	{
		menu.add(0, SETTING, 0, getString(R.string.setting)).setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, VIEW_SAVE_DIR, 1, getString(R.string.viewSaveDir)).setIcon(android.R.drawable.ic_menu_mylocation);
		menu.add(0, ABOUT, 2, getString(R.string.about)).setIcon(android.R.drawable.ic_menu_help);
		return true;
	}
	
	
	public boolean onOptionsItemSelected(MenuItem item)											//选中事件
	{
		switch (item.getItemId())
		{
		case SETTING:
			Intent intent = new Intent(this, CardViewer.class);
			startActivity(intent);
			break;
		case VIEW_SAVE_DIR:
			FileList.view(this, WitService.getSaveDir());
			tabHost.setCurrentTab(5);
			break;
		case ABOUT:
			Intent intent2 = new Intent(this, AboutActivity.class);								//关于对话框
			startActivity(intent2);
			
			break;
		}
		return false;
	}

	
	public void onTabChanged(String tabId)
	{
		refreshTabBackground();	
	}

	private void refreshTabBackground()
	{
		int nCount = tabHost.getTabWidget().getChildCount();
		
		for (int i = 0; i < nCount; ++i)
		{
			boolean bFlag = i == tabHost.getCurrentTab();
			View v = tabHost.getTabWidget().getChildAt(i);										//选中项和未选中项背景不同
			
			if (bFlag) v.setBackgroundDrawable(getResources().getDrawable(R.drawable.selected_tab));
			else v.setBackgroundColor(getResources().getColor(R.color.darkTransparent));
			
			TextView txtTitle =(TextView)v.findViewById(android.R.id.title);					//选中项与未选中项字体颜色不同
			txtTitle.setTextColor(getResources().getColor(bFlag ? R.color.yellow : android.R.color.white));
		}
	}


	public boolean onTouch(View v, MotionEvent e)
	{
		int nCount = tabHost.getTabWidget().getChildCount();
		
		for (int i = 0; i < nCount; ++i)
		{
			if (v == tabHost.getTabWidget().getChildAt(i))
			{
				switch (e.getAction())
				{
				case MotionEvent.ACTION_DOWN:
					refreshTabBackground();
					v.setBackgroundDrawable(getResources().getDrawable(R.drawable.touched_tab));//点中项单独设背景
					break;
				case MotionEvent.ACTION_UP:
					refreshTabBackground();
					break;
				}
			
				break;
			}
		}
		
		return false;
	}
	
	public void showLongToast(String szMessage)				//长Toast
	{
		Message msg = new Message();
		msg.what = Toast.LENGTH_LONG;
		msg.obj = szMessage;
		toastHandler.sendMessage(msg);	
	}
	
	public void showShortToast(String szMessage)			//短Toast
	{
		Message msg = new Message();
		msg.what = Toast.LENGTH_SHORT;
		msg.obj = szMessage;
		toastHandler.sendMessage(msg);	
	}
}
