package cn.edu.pku.eecs.wit.card;

import java.util.List;

import cn.edu.pku.eecs.wit.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class CardAdapter extends BaseAdapter	//名片列表适配器
{
	private LayoutInflater inflater;
	private List<Card> lstCard;
	
	public CardAdapter(Context context, List<Card> lstNewCard)
	{
		inflater = LayoutInflater.from(context);
		lstCard =lstNewCard;		
	}	
	
	public  int getCount()			//返回数目
	{
		return lstCard.size();
	}
	
	
	public  Object getItem(int position)
	{
		return position;
	}

	public  long getItemId(int position)
	{
		return position;
	}

	
	public  View getView(int position, View convertView, ViewGroup parent)
	{											//设置列表各view
		convertView = inflater.inflate(R.layout.listitem, null);
		
		TextView txtTitle = (TextView)convertView.findViewById(R.id.txtTitle);
		TextView txtSubTitle = (TextView)convertView.findViewById(R.id.txtSubTitle);
        ImageView imgIcon = (ImageView)convertView.findViewById(R.id.imgIcon);
		
		Card card = lstCard.get(position);
		
		imgIcon.setImageResource(R.drawable.card);
		txtTitle.setText(card.getName());
		txtSubTitle.setText(Long.toString(card.getNumber()));
		
		return convertView;
	}
	
	public  Card getCard(int position)//获得名片
	{
		return lstCard.get(position);
	}
}
