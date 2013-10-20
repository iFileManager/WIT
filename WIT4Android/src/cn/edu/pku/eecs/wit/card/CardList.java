package cn.edu.pku.eecs.wit.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.WitActivity;
import cn.edu.pku.eecs.wit.ui.ActivityWithHandler;
import cn.edu.pku.eecs.wit.util.Settings;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class CardList extends ActivityWithHandler implements OnItemClickListener//名片列表
{
	private static CardList singleton;
	
	private static final int
		CREATE_CONTACT = Menu.FIRST,
		ADD_BLACKLIST = Menu.FIRST + 1,
		REMOVE_HISTORYLIST = Menu.FIRST + 2,
		REMOVE_BLACKLIST = Menu.FIRST + 3;
	
	private static final int
		NOTIFY_HISTORY = 0,
		NOTIFY_BLACK = 1;
	
	private static Set<Long> setBlack = Collections.synchronizedSet(new HashSet<Long>());//黑名单
	private static List<Card>
		lstBlack = Collections.synchronizedList(new ArrayList<Card>()),
		lstHistory = Collections.synchronizedList(new ArrayList<Card>());
	
	public static boolean isBlacklisted(long lID)						//ID是否在黑名单中
	{
		return setBlack.contains(lID);
	}
	
	private static void add(List<Card> lstCard, Card card)
	{
		for (Card containedCard : lstCard) if (card.getID() == containedCard.getID()) return;
		lstCard.add(0, card);											//加在首位
	}
	
	public static void addBlack(Card card)								//加入黑名单、历史记录
	{
		if (singleton == null)
		{
			add(lstBlack, card);
			Settings.save(WitActivity.getSingleton());
		}
		else
		{
			List<Card> lstCard = Collections.synchronizedList(new ArrayList<Card>());
			lstCard.addAll(lstBlack);
			add(lstCard, card);
			singleton.notifyAdapter(NOTIFY_BLACK, lstCard);
		}
		
		setBlack.add(card.getID());
	}
	
	public static void addHistory(Card card)
	{
		if (singleton == null)
		{
			add(lstHistory, card);
			Settings.save(WitActivity.getSingleton());
		}
		else
		{
			List<Card> lstCard = Collections.synchronizedList(new ArrayList<Card>());
			lstCard.addAll(lstHistory);
			add(lstCard, card);
			singleton.notifyAdapter(NOTIFY_HISTORY, lstCard);
		}
	}
	
	public static void removeBlack(Card card)							//从黑名单、历史记录删除
	{
		if (singleton == null)
		{
			lstBlack.remove(card);
			Settings.save(WitActivity.getSingleton());
		}
		else
		{
			List<Card> lstCard = Collections.synchronizedList(new ArrayList<Card>());
			lstCard.addAll(lstBlack);
			lstCard.remove(card);
			singleton.notifyAdapter(NOTIFY_BLACK, lstCard);
		}
		
		setBlack.remove(card.getID());
	}
	
	public static void removeHistory(Card card)
	{
		if (singleton == null)
		{
			lstHistory.remove(card);
			Settings.save(WitActivity.getSingleton());
		}
		else
		{
			List<Card> lstCard = Collections.synchronizedList(new ArrayList<Card>());
			lstCard.addAll(lstHistory);
			lstCard.remove(card);
			singleton.notifyAdapter(NOTIFY_HISTORY, lstCard);
		}
	}
	
	public static List<Card> getAllBlack()								//获取、设置全部黑名单
	{
		return lstBlack;
	}
	
	public static void setAllBlack(List<Card> lstCard)
	{
		for (Card card : lstCard) addBlack(card);
	}
	
	public static List<Card> getAllHistory()							//获取、设置全部历史记录
	{
		return lstHistory;
	}
	
	public static void setAllHistory(List<Card> lstCard)
	{
		for (Card card : lstCard) addHistory(card);
	}
	
	private ListView lstViewHistory, lstViewBlack;
	private CardAdapter adpHistory, adpBlack;
	
	private void notifyAdapter(int nWhat, List<Card> lstCard)
	{
		Message msg = new Message();
		msg.what = nWhat;
		msg.obj = lstCard;
		handler.sendMessage(msg);
	}

	public void onCreate(Bundle savedInstanceState)						//创建
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cardlist);
		
		singleton = this;
		
		handler = new Handler()
		{
			@SuppressWarnings("unchecked")
			@Override
			public void handleMessage(Message msg)
			{   
				List<Card> lstCard = (List<Card>) msg.obj;
				
				switch (msg.what)
				{
				case NOTIFY_HISTORY:
					lstHistory.clear();
					lstHistory.addAll(lstCard);
					adpHistory.notifyDataSetChanged();
					Settings.save(CardList.this);
					break;
				case NOTIFY_BLACK:
					lstBlack.clear();
					lstBlack.addAll(lstCard);
					adpBlack.notifyDataSetChanged();
					Settings.save(CardList.this);
					break;
				}
				super.handleMessage(msg);
			}
		};
		
		lstViewHistory = (ListView) findViewById(R.id.lstHistory);
		adpHistory = new CardAdapter(this, lstHistory);
		lstViewHistory.setAdapter(adpHistory);
		
		lstViewHistory.setOnCreateContextMenuListener(new OnCreateContextMenuListener()//历史记录上下文菜单
		{
			public void onCreateContextMenu(ContextMenu menu, View view,ContextMenuInfo menuInfo)
			{
				menu.add(0, CREATE_CONTACT, 0, getString(R.string.createContact));
				menu.add(0, ADD_BLACKLIST, 1, getString(R.string.addBlacklist));
				menu.add(0, REMOVE_HISTORYLIST, 2, getString(R.string.removeHistorylist));
			}
		});
		lstViewHistory.setOnItemClickListener(this);		
		
		lstViewBlack = (ListView) findViewById(R.id.lstBlack);
		adpBlack = new CardAdapter(this, lstBlack);
		lstViewBlack.setAdapter(adpBlack);
		
		lstViewBlack.setOnCreateContextMenuListener(new OnCreateContextMenuListener()//黑名单上下文菜单
		{
		
			public void onCreateContextMenu(ContextMenu menu, View view,ContextMenuInfo menuInfo)
			{
				menu.add(0, REMOVE_BLACKLIST, 0, getString(R.string.removeBlacklist));
			}
		});
	}
	
	public void onDestroy()
	{
		singleton = null;
		super.onDestroy();
	}
	
	public void onItemClick(AdapterView<?> parent, View v, int position, long id)//项目被点击
	{
		if (parent == lstViewHistory)									//是历史记录
		{
			Card card = adpHistory.getCard(position);
			
			Intent intent = new Intent(this, CardViewer.class);			//显示名片
			intent.putExtra("card", card);
			
			startActivity(intent);
			return;
		}
	}
	
    
    public boolean onContextItemSelected(MenuItem item)					//上下文菜单被点击
    {
    	Card card;
    	AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo)item.getMenuInfo();
    	int nPosition = menuInfo.position;
    			
    	switch(item.getItemId())
    	{
    	case CREATE_CONTACT:
    		card = adpHistory.getCard(nPosition);						//创建名片
    		card.createContact(this);
    		showShortToast(getString(R.string.createSuccess));
    		
    		return true;
    	case ADD_BLACKLIST:
    		card = adpHistory.getCard(nPosition);						//删除自历史记录，加入黑名单
    		removeHistory(card);
    		addBlack(card);
    		
    		return true;
    	case REMOVE_HISTORYLIST:
    		card =  adpHistory.getCard(nPosition);						//删除自历史记录
    		removeHistory(card);
    		
    		return true;
    	case REMOVE_BLACKLIST:
    		card =  adpBlack.getCard(nPosition);						//删除自黑名单
    		removeBlack(card);
    		
    		return true;
    	}
    	
    	return false;
    }
}
