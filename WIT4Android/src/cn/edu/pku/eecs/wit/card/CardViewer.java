package cn.edu.pku.eecs.wit.card;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.network.MainActivity;
import cn.edu.pku.eecs.wit.network.WitActions;
import cn.edu.pku.eecs.wit.network.WitService;
import cn.edu.pku.eecs.wit.ui.ActivityWithHandler;
import cn.edu.pku.eecs.wit.util.Settings;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

public class CardViewer extends ActivityWithHandler implements OnClickListener//名片显示及系统设置
{	
	private EditText edtName, edtNumber, edtSaveDir;
	private Button btnSave, btnCancel;
	private CheckBox chkEncrypt;
	
	private Card card;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Window window = getWindow();
		window.requestFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.cardviewer);
		
		edtName = (EditText) findViewById(R.id.edtName);
		edtNumber = (EditText) findViewById(R.id.edtNumber);
		edtSaveDir = (EditText) findViewById(R.id.edtSaveDir);
		
		chkEncrypt = (CheckBox) findViewById(R.id.chkEncrypt);
		
		btnSave = (Button) findViewById(R.id.btnSave);
		btnSave.setOnClickListener(this);
		
		btnCancel = (Button) findViewById(R.id.btnCancel);
		btnCancel.setOnClickListener(this);
		
		LinearLayout laySaveDir = (LinearLayout) findViewById(R.id.laySaveDir);
		
		Object obj;													//获取card
		
		if (getIntent().getExtras() != null && (obj = getIntent().getExtras().get("card")) != null)//已传入card
		{
			window.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.card);//显示图标
			
			card = (Card) obj;
			edtName.setText(card.getName());
			edtNumber.setText(Long.toString(card.getNumber()));
			
			edtName.setFocusable(false);							//禁止编辑
			edtNumber.setFocusable(false);
			
			btnSave.setVisibility(View.GONE);
			laySaveDir.setVisibility(View.GONE);					//隐藏保存地址设置和保存按钮
			chkEncrypt.setVisibility(View.GONE);
			
			setTitle(getString(R.string.card));
			btnCancel.setText(getString(R.string._return));
		}
		else														//未传入card
		{
			window.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.setting);//显示图标
			
			card = Card.getMyCard();
			if (card != null)
			{ 
				edtName.setText(card.getName());
				edtNumber.setText(Long.toString(card.getNumber()));
			}
						
			edtSaveDir.setText(WitService.getSaveDir());
			chkEncrypt.setChecked(WitService.getSendEncrpyt());
			
			edtName.setFocusable(true);
			edtNumber.setFocusable(true);
			
			btnSave.setVisibility(View.VISIBLE);
			laySaveDir.setVisibility(View.VISIBLE);					//提供对主人card和保存目录设置功能
			chkEncrypt.setVisibility(View.VISIBLE);
			
			setTitle(getString(R.string.setting));
			btnCancel.setText(getString(R.string.cancel));
		}
	}


	public void onClick(View v)										//点击
	{
		if (v == btnSave)											//保存按钮
		{
			card = Card.getMyCard();
			card.setName(edtName.getText().toString());
			long lNumber;
			
			try
			{
				lNumber = Long.parseLong(edtNumber.getText().toString());
				card.setNumber(lNumber);
			}
			catch (Exception e)
			{
				showLongToast(getString(R.string.illegalNumberFormat));
				return;
			}
						
			Card.setMyCard(card);
			WitService.setSaveDir(edtSaveDir.getText().toString());
			WitService.setSendEncrpyt(chkEncrypt.isChecked());
			
			showLongToast(getString(Settings.save(this) ? R.string.saveSuccess : R.string.saveFailed));
			MainActivity.viewMyCard();
			
			finish();
			
			Intent intent = new Intent();
			intent.setAction(WitActions.UPDATE_CARD);
			sendBroadcast(intent);
			
			return;
		}
		
		if (v == btnCancel)											//取消按钮
		{
			finish();
			return;
		}
	}
}
