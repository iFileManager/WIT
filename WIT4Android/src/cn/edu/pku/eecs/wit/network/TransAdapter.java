package cn.edu.pku.eecs.wit.network;

import java.util.List;
import java.util.Vector;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.stream.WitInputStream;
import cn.edu.pku.eecs.wit.stream.WitStream;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TransAdapter extends BaseAdapter
{
	private List<WitStream> lstTrans = new Vector<WitStream>();	//传输列表 
	private MainActivity context;
	
	public TransAdapter(MainActivity context)
	{
		this.context = context;
	}
	
	public int getCount()
	{
		return lstTrans.size();
	}

	public Object getItem(int position)
	{
		return lstTrans.get(position);
	}

	
	public long getItemId(int position)
	{
		return position;
	}
	
	public View getView(int position, View view, ViewGroup parent)
	{
		LayoutInflater inflater = LayoutInflater.from(context);
		View v = inflater.inflate(R.layout.transitem, null);
		
		ImageView imgIcon = (ImageView) v.findViewById(R.id.imgTransIcon);
		TextView txtName = (TextView) v.findViewById(R.id.txtTransName);
		TextView txtSize = (TextView) v.findViewById(R.id.txtTransSize);
		
		ProgressBar pgbTrans = (ProgressBar)v.findViewById(R.id.prgTrans);
		TextView txtPercent = (TextView)v.findViewById(R.id.txtTransPercent);
		
		WitStream stream = lstTrans.get(position);
		
		int nIconId = R.drawable.file;
		
		switch (stream.getType())								//根据不同的类型，切换图标
		{ 
		case Photo: nIconId = R.drawable.image; break;
		case Music: nIconId = R.drawable.music; break;
		case Contact: nIconId = R.drawable.people; break;
		case App: nIconId = R.drawable.apk; break;
		default:
		}
		
		imgIcon.setImageResource(nIconId);
		
		txtName.setText(context.getString(
			(stream instanceof WitInputStream ? R.string.send : R.string.receive)) + ": " + stream.getData());
		txtSize.setText(Formatter.formatShortFileSize(context, stream.getSize()));
																//显示文本
		int nPercent = stream.getPercent();
		
		pgbTrans.setMax(100);
		pgbTrans.setProgress(nPercent);
		
		txtPercent.setText(nPercent + "%");						//显示百分比
		
		return v;
	}
	
	public void refreshTrans()
	{
		lstTrans = context.getWitService().getTransList();
	}
	
}