package cn.edu.pku.eecs.wit.util;

import java.util.List;

import cn.edu.pku.eecs.wit.card.Card;
import cn.edu.pku.eecs.wit.card.CardList;
import cn.edu.pku.eecs.wit.network.WitService;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class Settings
{
	private static boolean bLoaded = false;
	
	@SuppressWarnings("unchecked")
	public static boolean load(Context context)								//读入设置
	{		
		if (bLoaded) return true;
		bLoaded = true;
				
		boolean bRet = true;
		
		SharedPreferences pre = context.getSharedPreferences("settings", 0);
		
		String szSaveDir = pre.getString("save_dir", null);
		if (szSaveDir != null)
			WitService.setSaveDir(szSaveDir);
		else
			bRet = false;
		
		String szCard = pre.getString("my_card", null);
		if (szCard != null)
		{
			try
			{
				Card card = (Card) Convertor.string2Obj(szCard);
				Card.setMyCard(card);
			}
			catch (Exception e)
			{
				bRet = false;
			}
		}
		else bRet = false;
		
		String szHistory = pre.getString("history", null);
		if (szHistory != null)
		{
			try
			{
				List<Card> lstCard = (List<Card>) Convertor.string2Obj(szHistory);
				CardList.setAllHistory(lstCard);
			}
			catch (Exception e)
			{
				bRet = false;
			}
		}
		else bRet = false;
		
		String szBlack = pre.getString("black", null);
		if (szBlack != null)
		{
			try
			{
				List<Card> lstCard = (List<Card>) Convertor.string2Obj(szBlack);
				CardList.setAllBlack(lstCard);
			}
			catch (Exception e)
			{
				bRet = false;
			}
		}
		else bRet = false;
		
		boolean bSendEncrpyt = pre.getBoolean("encrypt", false);
		WitService.setSendEncrpyt(bSendEncrpyt);
		
		if (!bRet)
		{
			Card.setMyCard(new Card(context));
			WitService.setSaveDir(WitService.DEFAULT_SAVE_DIR);
			WitService.setSendEncrpyt(false);
			
			save(context);
		}
		
		return bRet;
	}
	
	public static boolean save(Context context)							//保存设置，各项同上
	{
		boolean bRet = true;
		
		SharedPreferences pre = context.getSharedPreferences("settings", 0);
		Editor editor = pre.edit();
		
		editor.putString("save_dir", WitService.getSaveDir());
		
		try
		{
			String szCard = Convertor.obj2String(Card.getMyCard());
			editor.putString("my_card", szCard);
		}
		catch (Exception e)
		{
			bRet = false;
		}
		
		try
		{
			String szHistory = Convertor.obj2String(CardList.getAllHistory());
			editor.putString("history", szHistory);
		}
		catch (Exception e)
		{
			bRet = false;
		}
		
		try
		{
			String szBlack = Convertor.obj2String(CardList.getAllBlack());
			editor.putString("black", szBlack);
		}
		catch (Exception e)
		{
			bRet = false;
		}
		
		editor.putBoolean("encrypt", WitService.getSendEncrpyt());
		
		return editor.commit() && bRet;
	}
}
