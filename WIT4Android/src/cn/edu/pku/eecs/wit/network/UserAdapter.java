package cn.edu.pku.eecs.wit.network;

import java.util.List;
import java.util.Vector;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.card.Card;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

public class UserAdapter extends BaseExpandableListAdapter
{
	private MainActivity context;
	private LayoutInflater inflater;
	
	private String[] arrGroupLabeles;
	private List<List<Card> > lstGroup;
		
	public UserAdapter(MainActivity context)
	{
		this.context = context;
		
		arrGroupLabeles = context.getResources().getStringArray(R.array.groupIndicatorLabeles);
		inflater = LayoutInflater.from(context);
		lstGroup = new Vector<List<Card> >();
	}
	
	public void setGroup(List<List<Card> > lstGroup)
	{
		this.lstGroup = lstGroup;
	}
	
	public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView,ViewGroup parentView)
	{
		if (groupPosition >= lstGroup.size()) return null;				//显示对应用户的信息
		
		Card card = lstGroup.get(groupPosition).get(childPosition);
		View view = inflater.inflate(R.layout.listitem, null);
		
		ImageView imgIcon = (ImageView) view.findViewById(R.id.imgIcon);
		imgIcon.setImageResource(R.drawable.card);
		
		TextView txtTitle = (TextView) view.findViewById(R.id.txtTitle);
		txtTitle.setText(card.getName());
		
		TextView txtSubTitle = (TextView) view.findViewById(R.id.txtSubTitle);
		txtSubTitle.setText(Long.toString(card.getNumber()));
		
		Card remoteCard = context.getWitService().getRemoteCard();
		
		CheckBox chkConnect = (CheckBox) view.findViewById(R.id.chkSelect);
		
		chkConnect.setVisibility(context.getWitService().isConnected() ? 
				(remoteCard.getID() == card.getID() ? View.VISIBLE : View.GONE) : View.VISIBLE);
		
		chkConnect.setChecked(context.getWitService().isConnected() && remoteCard.getID() == card.getID());
		
		chkConnect.setTag(card);
		chkConnect.setEnabled(false);
		
		view.setFocusable(false);
		view.setTag(chkConnect);
		
		view.setOnClickListener(new OnClickListener()					//点击用户栏目后
		{
			public void onClick(View v)
			{
				CheckBox chkConnect = (CheckBox) v.getTag();
				Card card = (Card) chkConnect.getTag();
				
				if (!context.getWitService().isConnected())				//如果未连接，向该用户发出连接请求
					context.getWitService().sendConnectRequest(card);
				else if (card.getID() == context.getWitService().getRemoteCard().getID())
					context.getWitService().sendDisconnectRequest();	//如果点击的是已连接用户，断开请求
			}
		});
		
		return view;
	}
	
	
	public int getChildrenCount(int groupPosition)						//获得某个用户组中的用户数
	{
		int childrenCount = 0;
		if (groupPosition < lstGroup.size()) childrenCount = lstGroup.get(groupPosition).size();
		return childrenCount;
	}
	

	public Object getGroup(int groupPosition)							//获得媒个用户组对象
	{
		return lstGroup.get(groupPosition);
	}
	
	public int getGroupCount()											//获得用户组数量,该处的用户组数量返回的是组名称的数量
	{
		return arrGroupLabeles.length;
	}

	public long getGroupId(int groupPosition)							//获得用户组序号
	{
		return groupPosition;
	}
	
	public View getGroupView(int groupPosition, boolean isExpanded, View convertView,ViewGroup parent)
	{																	//生成用户组布局View
		AbsListView.LayoutParams lp = new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 60);
        
		TextView txtLabel = new TextView(context);
        txtLabel.setLayoutParams(lp);
        txtLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        txtLabel.setPadding(50, 0, 0, 0);
        
        int childrenCount = 0;
        if (groupPosition < lstGroup.size())							//如果groupPosition序号能从children列表中获得children对象，则获得该children对象中的用户数量
        {
        	childrenCount = lstGroup.get(groupPosition).size();
        }
		
        txtLabel.setText(arrGroupLabeles[groupPosition] + "(" + childrenCount + ")");
		txtLabel.setTextColor(Color.BLACK);

		return txtLabel;
	}

	public boolean hasStableIds()
	{
		return true;
	}


	public boolean isChildSelectable(int groupPosition, int childPosition)
	{
		return true;
	}


	public Object getChild(int groupPosition, int childrenPosition)
	{
		if (groupPosition >= lstGroup.size()) return null;
		return lstGroup.get(groupPosition).get(childrenPosition);
	}


	public long getChildId(int groupPosition, int childrenPosition)
	{
		return childrenPosition;
	}
}	
