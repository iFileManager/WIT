package cn.edu.pku.eecs.wit.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TabWidget;

public class VerticalTabWigdet extends TabWidget				//竖版TabWidget
{	
	public VerticalTabWigdet(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setPadding(getPaddingLeft() - 4, getPaddingTop(), getPaddingRight() - 4, getPaddingBottom());
		setOrientation(LinearLayout.VERTICAL);					//设置方向
	}

	@Override
	public void addView(View v)
	{
		LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1.0f);
		params.setMargins(0, 0, 0, 0);
		v.setLayoutParams(params);
		
		super.addView(v);
	}
}