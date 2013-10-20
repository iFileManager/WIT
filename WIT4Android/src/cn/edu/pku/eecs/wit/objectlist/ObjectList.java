package cn.edu.pku.eecs.wit.objectlist;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.network.WitService;
import cn.edu.pku.eecs.wit.stream.WitInputStream;
import cn.edu.pku.eecs.wit.stream.WitStream.ObjectType;
import cn.edu.pku.eecs.wit.ui.ActivityWithHandler;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public abstract class ObjectList extends ActivityWithHandler implements OnItemClickListener, OnClickListener, OnTouchListener//项目列表
{
	protected static final int													//菜单项
		OPEN = Menu.FIRST,
		SEND = Menu.FIRST + 1;
	
	protected static final int													//Handler项
		SET_TITLE = 0,															//设置标题
		REFRESH_LISTVIEW = 1,													//刷新ListView
		SWITCH_BUTTON = 2,														//控制按钮显示
		SWITCH_REFRESH = 3,														//控制刷新按钮是否可点击
		SET_SELECTION = 4,														//控制复选框是否选中
		SET_VISIBILITY = 5;														//控制复选框是否显示
	
	protected ObjectType type;													//类型
	
	private Button btnSend;
	
	private ImageView imgRefresh;
	private ProgressBar prgRefresh;
	
	private TextView txtTitle;
	
	private ListView lstView;
	private ObjectAdapter adapter;
	
	protected List<Map<String, Object> > lstMap;
	private int nSelectCount;													//选中项个数
	
	@SuppressLint("HandlerLeak")
	@Override
	public void onCreate(Bundle savedInstanceState)								//创建
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.objectlist);
		
		btnSend = (Button) findViewById(R.id.btnSend);
		btnSend.setOnClickListener(this);
		
		txtTitle = (TextView)findViewById(R.id.txtTitle);
		
		imgRefresh = (ImageView) findViewById(R.id.imgRefresh);
		imgRefresh.setOnClickListener(this);
		imgRefresh.setOnTouchListener(this);
		
		prgRefresh = (ProgressBar) findViewById(R.id.prgRefresh);
		prgRefresh.setIndeterminate(true);										//不确定模式
		
		lstView = (ListView)findViewById(R.id.lstObj);
		
		adapter = getAdapter();		
		lstView.setAdapter(adapter);
		
		lstView.setOnCreateContextMenuListener(this);
		lstView.setOnItemClickListener(this);
		
		handler = new Handler()													//多线程UI
		{
			@SuppressWarnings("unchecked")
			@Override
			public void handleMessage(Message msg) 
			{  
				switch (msg.what)
				{
				case SET_TITLE:
					txtTitle.setText((String) msg.obj);							//设置标题
					
					break;
				case REFRESH_LISTVIEW:
					lstMap.clear();
					lstMap.addAll((List<Map<String, Object> >) msg.obj);		//刷新列表
					adapter.notifyDataSetChanged();
					
					break;
				case SWITCH_BUTTON:
					btnSend.setVisibility(nSelectCount > 0 ? View.VISIBLE : View.GONE);
					btnSend.setText(getString(R.string.send) + "(" + nSelectCount + ")");
					
					break;
				case SWITCH_REFRESH:
					if ((Boolean) msg.obj)
					{
						prgRefresh.setVisibility(View.GONE);
						imgRefresh.setVisibility(View.VISIBLE);
					}
					else
					{
						imgRefresh.setVisibility(View.GONE);
						prgRefresh.setVisibility(View.VISIBLE);
					}
					
					break;
				case SET_SELECTION:
					Pair<Integer, Boolean> pairSelection = (Pair<Integer, Boolean>) msg.obj;
					
					Map<String, Object> mapSelection = lstMap.get(pairSelection.first);
					mapSelection.put("select", pairSelection.second);			//设置项
					nSelectCount += pairSelection.second ? 1 : -1;				//选中项个数
					
					adapter.notifyDataSetChanged();
					switchButton();												//切换按钮状态
					
					break;
				case SET_VISIBILITY:
					Pair<Integer, Integer> pairVisibility = (Pair<Integer, Integer>) msg.obj;
					
					Map<String, Object> mapVisibility = lstMap.get(pairVisibility.first);
					mapVisibility.put("visibility", pairVisibility.second);		//设置项
					
					adapter.notifyDataSetChanged();
					
					break;
				}
				
				super.handleMessage(msg);
			}
		};
		
		txtTitle.setText(getDefaultTitle());									//设置默认标题
		load();
	}
	
	public String getPath(int position)
	{
	return (String) lstMap.get(position).get("data");
	}
	public long getSize(int position)
	{
		return adapter.getSize(position);
	}
	
	public void onClick(View v)													//点击操作
	{
		if (v == btnSend)														//是发送按钮
		{
			if (!WitService.isGloballyConnected())
			{
				showShortToast(getString(R.string.notConnected));
				return;
			}
			
			for (int i = 0; i < lstMap.size(); ++i)
			{
				if (adapter.getSelection(i))
				{
					send(i);												//依次发送各项
					adapter.setSelection(i, false);							//去除选中
				}
			}
			
			return;
		}
		
		if (v == imgRefresh)
		{
			load();																//点击刷新
			return;
		}
	}
	

	public void onCreateContextMenu(ContextMenu menu, View view,ContextMenuInfo menuInfo)//上下文菜单
	{
		menu.add(0, OPEN, 0, getString(R.string.open));
		menu.add(0, SEND, 1, getString(R.string.send));
	}
	

    public boolean onContextItemSelected(MenuItem item)							//上下文菜单被选中
    {
    	if (isRefreshing()) return false;
    	
    	AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo)item.getMenuInfo();
    	int nPosition = menuInfo.position;
    			
    	switch(item.getItemId())												//打开
    	{
    	case OPEN:
    		open(nPosition);
    		return true;
    	case SEND:
    		for (int i = 0; i < lstMap.size(); ++i) adapter.setSelection(i, false);//去除选中
    		
    		if (!WitService.isGloballyConnected()) showShortToast(getString(R.string.notConnected));
    		else send(nPosition);												//发送
    		
    		return true;
    	}
    	
    	return false;
    }
	
	
	public boolean onTouch(View v, MotionEvent e)								//触摸刷新按钮
	{
		if (v == imgRefresh)
		{			
			switch (e.getAction())
			{
			case MotionEvent.ACTION_DOWN:
				imgRefresh.setImageResource(R.drawable.refresh_touched);		//发光效果
				break;
			case MotionEvent.ACTION_UP:
				imgRefresh.setImageResource(R.drawable.refresh);				//正常效果
				break;
			}
			
			return false;
		}
		
		return false;
	}
	
	class LoadTask extends AsyncTask<Void, Void, Void>							//利用AsyncTask刷新
	{

        protected void onPreExecute()
		{  
			nSelectCount = 0;													//没有选中项
			
			setDefaultTitle();
			switchButton();														//切换按钮状态
			switchRefresh(false);												//刷新不显示
			
			super.onPreExecute();  
        } 
		
	
		protected Void doInBackground(Void... arg0)
		{
			try
			{
				if (adapter != null) adapter.load();							//加载
			}
			catch (Exception e) { }
			return null;
		}
		
		
        protected void onPostExecute(Void result)
		{			
			for (int i = 0; i < lstMap.size(); ++i) adapter.setVisibility(i, View.VISIBLE);//显示复选框
			switchRefresh(true);												//显示刷新
			
			super.onPostExecute(result);  
        }
	}
	
	public void setTitle(String szTitle)										//线程更新标题
	{
		Message msg = new Message();
		msg.what = SET_TITLE;
		msg.obj = szTitle;
		
		handler.sendMessage(msg);
	}
	
	public void setDefaultTitle()												//设置每种列表默认标题
	{
		setTitle(getDefaultTitle());
	}
	
	public void refreshListView(List<Map<String, Object> > lstNewMap)			//线程更新列表
	{		
		Message msg = new Message();
		msg.what = REFRESH_LISTVIEW;
		msg.obj = lstNewMap;
		
		handler.sendMessage(msg);
	}
	
	public void switchButton()													//切换按钮状态
	{
		Message msg = new Message();
		msg.what = SWITCH_BUTTON;
		
		handler.sendMessage(msg);
	}
	
	public void switchRefresh(boolean bNotRefreshing)							//切换刷新状态
	{
		Message msg = new Message();
		msg.what = SWITCH_REFRESH;
		msg.obj = bNotRefreshing;
		
		handler.sendMessage(msg);
	}
	

	public void onItemClick(AdapterView<?> parent, View v, int position, long id)//点击打开
	{
		if (!isRefreshing()) open(position);
	}
	
	public void load()
	{
		new LoadTask().execute();												//刷新
	}
	
	public abstract String getDefaultTitle();
	
	public void send(int position)												//发送某一个项目
	{
		try
		{
			WitService.getSingleton().addSend(adapter.createWit(position));
		}
		catch (Exception e)
		{
			showShortToast(adapter.getShortName(position) + " " + getString(R.string.sendFailed));
		}
	}
	
	protected boolean isRefreshing()
	{
		return imgRefresh.getVisibility() == View.GONE;
	}
	
	public abstract void open(int position);									//打开
	
	public abstract ObjectAdapter getAdapter();
	
	public abstract class ObjectAdapter extends BaseAdapter						//列表适配器
	{
		private LayoutInflater inflater;
		
		public ObjectAdapter()
		{
			inflater = LayoutInflater.from(ObjectList.this);
			lstMap = Collections.synchronizedList(new ArrayList<Map<String, Object> >());
		}
		
		
		public int getCount()
		{
			return lstMap.size();
		}
		
	
		public Object getItem(int position)
		{
			return position;
		}
		
	
		public long getItemId(int position)
		{
			return position;
		}
		
	
		public View getView(final int position, View convertView, ViewGroup parent)	//显示各项
		{
			ObjectItem item;
			
			if (convertView == null)
			{
				item = new ObjectItem();
				convertView = inflater.inflate(R.layout.listitem, null);
				
				item.txtTitle = (TextView)convertView.findViewById(R.id.txtTitle);
                item.txtSubTitle = (TextView)convertView.findViewById(R.id.txtSubTitle);
                item.imgIcon = (ImageView)convertView.findViewById(R.id.imgIcon);
                item.chkSelect = (CheckBox) convertView.findViewById(R.id.chkSelect);
                
                lstMap.get(position).put("select", false);						//未选择
                
                convertView.setTag(item);
			}
			else item = (ObjectItem) convertView.getTag();
			
			item.chkSelect.setTag(position);			
			setView(item, lstMap.get(position));								//各子类设置
			
			item.chkSelect.setChecked(getSelection((Integer) item.chkSelect.getTag()));//设置选中项
			item.chkSelect.setVisibility(getVisibility((Integer) item.chkSelect.getTag()));
			
			item.chkSelect.setOnClickListener(new OnClickListener()
            {
				public void onClick(View v)
				{
					setSelectionAtOnce((Integer) v.getTag(), ((CheckBox) v).isChecked());//更新选择
				}	
            });
			
			return convertView;
		}
		
		public String getData(int position)										//获取数据（字节数组）
		{
			String szFilename = (String) lstMap.get(position).get("data");
			return szFilename;
		}
		
		public long getSize(int position)
		{
			try
			{
				String szFilename = (String) lstMap.get(position).get("data");
				FileInputStream fis = new FileInputStream(new File(szFilename));
				long nSize = fis.available();
				
				fis.close();
				return nSize;
			}
			catch (Exception e)
			{
				return 0;
			}
		}
				
		public String getShortName(int position)								//获取短文件名
		{
			String szName = (String) lstMap.get(position).get("data");
			File file = new File(szName);
			return file.getName();
		}
		
		public WitInputStream createWit(int position) throws IOException		//生成WitStream
		{
			return new WitInputStream(type, getShortName(position), getData(position));
		}
		
		public boolean getSelection(int position)								//返回选中的项
		{
			Map<String, Object> map = lstMap.get(position);
			
			Object obj = map.get("select");
			if (obj == null) return false;
			
			return (Boolean) obj;
		}
		
		public void setSelection(int position, boolean bSelected)				//设置选中的项
		{
			boolean bOldSelected = getSelection(position);
			if (bSelected == bOldSelected) return;								//选中结果不变
						
			Message msg = new Message();
			msg.what = SET_SELECTION;
			msg.obj = new Pair<Integer, Boolean>(position, bSelected);
			
			handler.sendMessage(msg);
		}
		
		private void setSelectionAtOnce(int position, boolean bSelected)		//不经过Handler
		{
			boolean bOldSelected = getSelection(position);
			if (bSelected == bOldSelected) return;								//选中结果不变
						
			Map<String, Object> mapSelection = lstMap.get(position);
			mapSelection.put("select", bSelected);								//设置项
			adapter.notifyDataSetChanged();
			
			nSelectCount += bSelected ? 1 : -1;									//选中项个数			
			btnSend.setVisibility(nSelectCount > 0 ? View.VISIBLE : View.GONE);
			btnSend.setText(getString(R.string.send) + "(" + nSelectCount + ")");
		}
		
		public int getVisibility(int position)
		{
			Map<String, Object> map = lstMap.get(position);
			
			Object obj = map.get("visibility");
			if (obj == null) return View.GONE;
			
			return (Integer) obj;
		}
		
		public void setVisibility(int position, int nVisibility)
		{
			int nOldVisibility = getVisibility(position);
			if (nVisibility == nOldVisibility) return;							//选中结果不变
			
			Message msg = new Message();
			msg.what = SET_VISIBILITY;
			msg.obj = new Pair<Integer, Integer>(position, nVisibility);
			
			handler.sendMessage(msg);
		}
		
		public abstract void load();
		public abstract void setView(ObjectItem item, Map<String, Object> map);
	}
	
	class ObjectItem															//列表项
	{
		TextView txtTitle, txtSubTitle;
		ImageView imgIcon;
		
		CheckBox chkSelect;
	}
}

