package cn.edu.pku.eecs.wit.ui;

import cn.edu.pku.eecs.wit.R;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;

public class AboutActivity extends ActivityWithHandler implements OnClickListener
{
	private Button btnReturn;							//返回按钮
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Window window = getWindow();
		window.requestFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.about);
		window.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_launcher);//显示图标
		
		btnReturn = (Button) findViewById(R.id.btnReturn);
		btnReturn.setOnClickListener(this);
	}
	
	
	public void onClick(View v)
	{
		if (v == btnReturn)								//点击了返回按钮
		{
			finish();
			return;
		}
	}
}
