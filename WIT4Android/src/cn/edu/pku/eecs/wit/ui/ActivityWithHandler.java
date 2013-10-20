package cn.edu.pku.eecs.wit.ui;

import cn.edu.pku.eecs.wit.R;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

public abstract class ActivityWithHandler extends Activity	//包含一个Handler的Activity
{
	protected Handler handler;
	private Handler toastHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg) 				//接收显示Toast信息
		{   
			Toast toast = Toast.makeText(ActivityWithHandler.this, (String) msg.obj, msg.what);
			toast.getView().setBackgroundColor(getResources().getColor(R.color.darkerDarkTransparent));
			toast.show();
			
			super.handleMessage(msg);   
		}
	};
	
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
