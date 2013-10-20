package cn.edu.pku.eecs.wit.network;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.card.Card;
import cn.edu.pku.eecs.wit.card.CardViewer;
import cn.edu.pku.eecs.wit.ui.ActivityWithHandler;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends ActivityWithHandler implements OnClickListener, OnTouchListener
{
	private static MainActivity singleton;									//单例模式
	
	public static void viewMyCard()											//在标题显示我的名片
	{
		if (singleton == null) return;
		
		Message msg = new Message();
		msg.what = VIEW_MY_CARD;
		singleton.handler.sendMessage(msg);
	}
	
	private static final int
		VIEW_MY_CARD = 0,													//标题显示我的名片
		VIEW_REMOTE_CARD = 1;												//改变对方名片
	
	private TextView txtMyCard, txtRemoteCard;
	private ImageView imgSetting;											//设置按钮
	
	private ExpandableListView lstViewOnline;
	private ListView lstViewTrans;
	
	private ActivityReceiver receiver;
	private IntentFilter filter;
		
	private WitService witService;
	private Intent intent;
	
	private UserAdapter userAdapter;
	private TransAdapter transAdapter;
		
	private ServiceConnection connection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName name, IBinder service_)
		{
			witService = ((WitService.ServiceBinder)service_).getService();
		}

		public void onServiceDisconnected(ComponentName name)
		{
			witService = null;
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		imgSetting = (ImageView) findViewById(R.id.imgSetting);
		imgSetting.setOnClickListener(this);
		imgSetting.setOnTouchListener(this);								//各种监听
		
		txtMyCard = (TextView) findViewById(R.id.txtMyCard);
		viewMyCardAtOnce();													//设置标题
		
		txtRemoteCard = (TextView) findViewById(R.id.txtRemoteCard);
				
		lstViewOnline = (ExpandableListView) findViewById(R.id.lstOnline);   
		lstViewTrans = (ListView) findViewById(R.id.lstTransmission);
		
		handler = new Handler()
		{
			@Override
			public void handleMessage(Message msg) 							//handler
			{   
				switch (msg.what)
				{
				case VIEW_MY_CARD:
					viewMyCardAtOnce();
					break;
				case VIEW_REMOTE_CARD:
					viewRemoteCardAtOnce();
					break;
				}
				
				super.handleMessage(msg);
			}
		};
		
		singleton = this;
                
        intent = new Intent(MainActivity.this, WitService.class);			//绑定Service
        getApplicationContext().bindService(intent, connection, BIND_AUTO_CREATE);
        
        startService(intent);
        registerBroadcast();
	}
	
	@Override
    protected void onDestroy()
    {
		super.onDestroy();
		
		singleton = null;
    	
		unregisterReceiver(receiver);
    	stopService(intent);
    	getApplicationContext().unbindService(connection);
    }
		
	public void onClick(View view)
	{		
		if (view == imgSetting)
		{
			Intent intent = new Intent(this, CardViewer.class);
			startActivity(intent);												//设置			
			return;
		}
		
	}
	
	public boolean onTouch(View v, MotionEvent e)
	{
		if (v == imgSetting)
		{			
			switch (e.getAction())
			{
			case MotionEvent.ACTION_DOWN:
				imgSetting.setImageResource(R.drawable.setting_touched);		//发光效果
				break;
			case MotionEvent.ACTION_UP:
				imgSetting.setImageResource(R.drawable.setting);				//正常效果
				break;
			}
			
			return false;
		}
		
		return false;
	}
	
	public WitService getWitService()
	{
		return witService;
	}
	
	private void viewMyCardAtOnce()												//立即在标题显示我的名片
	{
		String szName, szNumber, szTempName;
		
		szName = getString(R.string.noname);									//未命名
		szNumber = getString(R.string.nonumber);
		
		Card card = Card.getMyCard();
		if (card == null)
		{
			Intent intent = new Intent(this, CardViewer.class);
			startActivity(intent);												//设置
		}
		else
		{
			szTempName = card.getName();
			szNumber = Long.toString(card.getNumber());
			
			if (!szTempName.trim().equals("")) szName = szTempName;
		}
		
		txtMyCard.setText(szName + "(" + szNumber + ")");
	}
	
	private void viewRemoteCardAtOnce()											//立即在显示连接方的名片
	{
		String szName, szTempName;
		
		szName = getString(R.string.noname);									//未命名
		
		Card card = witService.getRemoteCard();
		if (card == null)
		{
			card = witService.getRequestCard();
			if (card == null) txtRemoteCard.setText(getString(R.string.defaultCurrentConnection));
			else
			{
				szTempName = card.getName();
				if (!szTempName.trim().equals("")) szName = szTempName;
				
				txtRemoteCard.setText(getString(R.string.currentConnection) + szName + "(" + getString(R.string.requesting) + ")");
			}
		}
		else
		{
			szTempName = card.getName();
			if (!szTempName.trim().equals("")) szName = szTempName;
			
			txtRemoteCard.setText(getString(R.string.currentConnection) + szName);
		}
	}
	    
    private class ActivityReceiver extends BroadcastReceiver					//接收Android广播
    {
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(WitActions.SHOW_TOAST))				//显示消息
			{
				String szMsg = intent.getExtras().getString("msg");
				if (szMsg != null) showShortToast(szMsg);
				
				return;
			}
			
			if (intent.getAction().equals(WitActions.USERLIST_CHANGED))			//切换用户列表
	     	{
				if(userAdapter == null)
				{
					userAdapter = new UserAdapter(MainActivity.this);
					userAdapter.setGroup(witService.getGroup());
					
			        lstViewOnline.setAdapter(userAdapter);
			        lstViewOnline.expandGroup(0);			        
		        }
				else userAdapter.setGroup(witService.getGroup());
		        userAdapter.notifyDataSetChanged();
		        
		        return;
	     	}
			
			if (intent.getAction().equals(WitActions.REMOTE_CARD_CHANGED))		//更改已连接的用户名片
			{
				Message msg = new Message();
				msg.what = VIEW_REMOTE_CARD;
				handler.sendMessage(msg);
				
				return;
			}
			
			if (intent.getAction().equals(WitActions.CONNECTION_REQUEST))		//弹出连接请求对话框
			{
				final String szName = intent.getStringExtra("CardName");
				final long lID = intent.getLongExtra("CardID", 0);
				
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				
				builder.setTitle(getString(R.string.requestConnectionTitle));
				builder.setMessage(szName + " " + getString(R.string.requestConnectionMsg));
				
				builder.setIcon(R.drawable.card);
				
				builder.setPositiveButton(getString(R.string.yes),
					new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface arg0, int arg1)
						{
							witService.acceptConnectRequest(lID);
						}
					});
				builder.setNegativeButton(getString(R.string.no),
					new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface arg0, int arg1)
						{
							witService.refuseConnectRequest(lID);
						}
					});
				
				builder.show();
				
				return;
			}
			
			
			if (intent.getAction().equals(WitActions.TRANS_CHANGED))			//文件传输进度更新
			{
				if(transAdapter == null)
				{
					transAdapter = new TransAdapter(MainActivity.this);
					transAdapter.refreshTrans();
					
					lstViewTrans.setAdapter(transAdapter);        
		        }
				else transAdapter.refreshTrans();
		        transAdapter.notifyDataSetChanged();
		        
		        return;
			}
		}
    }
    
    private void registerBroadcast()											//注册广播事件
	{
        receiver = new ActivityReceiver();
        filter = new IntentFilter();
        
        filter.addAction(WitActions.SHOW_TOAST);
        
        filter.addAction(WitActions.REMOTE_CARD_CHANGED);
        
        filter.addAction(WitActions.USERLIST_CHANGED);
        filter.addAction(WitActions.TRANS_CHANGED);
        
        filter.addAction(WitActions.CONNECTION_REQUEST);    
                
        registerReceiver(receiver, filter);
	}

	 
}
