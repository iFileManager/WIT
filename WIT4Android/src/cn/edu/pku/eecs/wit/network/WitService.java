package cn.edu.pku.eecs.wit.network;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.edu.pku.eecs.wit.R;
import cn.edu.pku.eecs.wit.card.Card;
import cn.edu.pku.eecs.wit.card.CardList;
import cn.edu.pku.eecs.wit.stream.WitInputStream;
import cn.edu.pku.eecs.wit.stream.WitOutputStream;
import cn.edu.pku.eecs.wit.stream.WitStream;
import cn.edu.pku.eecs.wit.util.Convertor;
import cn.edu.pku.eecs.wit.util.Settings;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

public class WitService extends Service
{
	public static final String DEFAULT_SAVE_DIR = 
			Environment.getExternalStorageDirectory().getPath() + File.separator + "wit";
	
	private static WitService singleton;
		
	private static String szSaveDir = DEFAULT_SAVE_DIR;
	private static boolean bSendEncrypt = false;
	
	public static WitService getSingleton()
	{
		return singleton;
	}
	
	public static boolean isGloballyConnected()								//是否连接
	{
		return singleton != null && singleton.isConnected();
	}
	
	public static void setSaveDir(String szSaveDir_)						//保存目录
	{
		szSaveDir = szSaveDir_ != null ? szSaveDir_ : DEFAULT_SAVE_DIR;
		
		File saveDir = new File(szSaveDir);
		if (!saveDir.exists() || saveDir.isFile()) saveDir.mkdirs();
	}
	
	public static String getSaveDir()
	{
		return szSaveDir;
	}
	
	public static void setSendEncrpyt(boolean bSendEncrypt_)
	{
		bSendEncrypt = bSendEncrypt_;
	}
	
	public static boolean getSendEncrpyt()
	{
		return bSendEncrypt;
	}
		
	public class ServiceBinder extends Binder
	{
		public WitService getService()
		{
			return WitService.this;
		}
	}
	
	private Object mutex = new Object();									//互斥锁，用于保护传输列表
	
	private byte[] regBuffer;												//用户广播自己的buffer
	
	private ServerReceiver receiver;
	private ServiceBinder binder = new ServiceBinder();
	
	private Card remoteCard, requestCard;									//连接的名片，尝试连接的名片
	private boolean bStopped;
	
	private Hashtable<Long, Card> mapCard = new Hashtable<Long, Card>();	//当前在线用户
	
	private Hashtable<String, WitInputStream> mapSend = new Hashtable<String, WitInputStream>();
	private Hashtable<String, WitOutputStream> mapReceive = new Hashtable<String, WitOutputStream>();
	
	private ExecutorService sendPool, receivePool;	
	
	private RSAPrivateKey myKey;
	private RSAPublicKey remoteKey;
		
	private NetworkEnv environment;											//网络环境
	private WifiManager wm;
	
	private String szBSSID;													//网关的MAC地址
	
	@Override
	public void onStart(Intent intent, int startId)
	{
		singleton = this;
		
		mapCard.clear();
		
		wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		environment = new NetworkEnv(this);									//空网络环境
		
		Settings.load(this);
				
		myKey = Card.getMyCard().getPrivateKey();							//生成自己私钥
				
		selectNetworkEnvironment();											//选择网络环境
		
		regBuffer = createPacket(WitProtocol.REG_USER);						//初始化指令缓存
		Card.getMyCard().buffer(regBuffer);									//更新注册命令用户数据
				
		registerBroadcast();												//注册广播接收器
				
		new UpdateMyCard().start();											//向网络发送心跳包，并注册
		new UpdateUserlist().start();										//检查用户列表是否有超时用户
		new ReceivePacket().start();
		
		broadcastUserlistChanged();											//通知有新用户加入或退出
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}
			
	@Override
	public void onDestroy() 
	{
		super.onDestroy();
		
		if (isConnected()) disconnect();
		unregMyCard();
		
		environment.close();
		
		unregisterReceiver(receiver);
		
		bStopped = true;
			
		singleton = null;
	}
	
	public Card getRemoteCard()
	{
		return remoteCard;
	}
	
	public void setRemoteCard(Card card)
	{		
		remoteCard = card;
		
		if (card != null)
		{
			CardList.addHistory(card);
			remoteKey = card.getPublicKey();								//生成对方公钥
		}
		else remoteKey = null;
		
		Intent intent = new Intent();
		intent.setAction(WitActions.REMOTE_CARD_CHANGED);
		sendBroadcast(intent);
		
		broadcastUserlistChanged();	
	}
	
	public void setRequestCard(Card card)
	{
		requestCard = card;
		
		Intent intent = new Intent();
		intent.setAction(WitActions.REMOTE_CARD_CHANGED);
		sendBroadcast(intent);
		
		broadcastUserlistChanged();
	}
	
	public Card getRequestCard()
	{
		return requestCard;
	}
	
	public List<List<Card> > getGroup()										//获得所有用户对象
	{
		List<Card> lstCard = new Vector<Card>();

		for (Card card : mapCard.values())
		{
			if (!CardList.isBlacklisted(card.getID())) lstCard.add(card);	//只加非黑名单
		}
		
		List<List<Card> > lstGroup = new Vector<List<Card> >();
		lstGroup.add(lstCard);
		
		return lstGroup;
	}
	
	private class UpdateMyCard extends Thread								//每隔5秒发送一个心跳包
	{
		@Override
		public void run()
		{
			while(!bStopped)
			{
				try
				{
					updateMyCard();
					sleep(WitProtocol.REFRESH_PERIOD);
				}
				catch (Exception e) { }
			}
		}
	}
	
	private class UpdateUserlist extends Thread								//检测用户是否在线，如果超过15秒则从列表中清除该用户
	{
		@Override
		public void run()
		{
			while(!bStopped)
			{
				Hashtable<Long, Card> mapNewCard = new Hashtable<Long, Card>();
				mapNewCard.putAll(mapCard);
				
				for (Card card : mapCard.values())
				{
					if (System.currentTimeMillis() - card.getStamp() > WitProtocol.TIMEOUT)
					{
						if (isWaitingForConnectResponse() && card.getID() == requestCard.getID())
						{
							showToast(getString(R.string.connectionRequestRefused));
							setRequestCard(null);
						}
						
						if (isConnected() && card.getID() == remoteCard.getID())
							disconnect();
						
						mapNewCard.remove(card.getID());
					}
				}
				
				mapCard = mapNewCard;
			
				broadcastUserlistChanged();
				
				try
				{
					sleep(5000);
				}
				catch (Exception e) { }
			}
		}
	}
	
	private class ReceivePacket extends Thread								//接收消息包
	{
		@Override
		public void run()
		{
			byte[] receivedBuffer = new byte[WitProtocol.PACKET_SIZE];
			
			while (!bStopped)
			{
				Arrays.fill(receivedBuffer, (byte) 0);
	        	environment.receivePacket(receivedBuffer);	        	
	        	decode(receivedBuffer);
			}
		}
	}
	
	public byte[] createPacket(int nCommandType)							//编码数据
	{
		byte[] buffer = new byte[WitProtocol.PACKET_SIZE];
		
		Arrays.fill(buffer, (byte) 0);
		buffer[WitProtocol.TAG_POS] = (byte)nCommandType;					//第一个字节代表操作类型
		
		byte[] bytes = Convertor.long2Bytes(Card.getMyCard().getID());
		System.arraycopy(bytes, 0, buffer, WitProtocol.ID_START, bytes.length);//加入名片ID
		
		return buffer;
	}
	
	private void broadcastUserlistChanged()									//发送用户更新广播
	{
		Intent intent = new Intent();
		intent.setAction(WitActions.USERLIST_CHANGED);
		sendBroadcast(intent);
	}
	
	private void broadcastTranslistChanged()
	{
		Intent intent = new Intent();
		intent.setAction(WitActions.TRANS_CHANGED);
		sendBroadcast(intent);
	}
	
	private void updateMyCard()
	{
		regBuffer[WitProtocol.TAG_POS] = WitProtocol.REG_USER;				//恢复成注册请求标志，向网络中注册自己
		environment.sendPacketToAll(regBuffer);
	}
	
	private void unregMyCard()
	{
		try
		{
			regBuffer[WitProtocol.TAG_POS] = WitProtocol.UNREG_USER;		//把命令类型修改为注销标志，并广播发送，从所有用户中退出
			environment.sendPacketToAll(regBuffer);
		}
		catch (Exception e) { }
	}
	
	private void updateUserlist(long lID, byte[] buffer)					//更新或加用户信息到用户列表中
	{
		if (lID == Card.getMyCard().getID()) return;						//不需要加自己
		
		Card card = new Card(buffer);
		mapCard.put(card.getID(), card);
		
		broadcastUserlistChanged();
	}
	
	public void sendConnectRequest(Card card)								//发送连接请求
	{
		if (isConnected()) return;
		
		setRequestCard(card);
		
		byte[] connectBuffer = createPacket(WitProtocol.CONNECT);
		environment.sendPacketToOne(connectBuffer, card);
	}
	
	public void sendDisconnectRequest()										//发送断开连接
	{
		if (!isConnected()) return;
		
		byte[] disconnectBuffer = createPacket(WitProtocol.DISCONNECT);
		environment.sendPacketToOne(disconnectBuffer, remoteCard);
		
		disconnect();														//断开不需要确认
	}
	
	public void acceptConnectRequest(long lID)								//接收连接请求
	{
		Card card = mapCard.get(lID);
		if (card == null) return;
		
		byte[] acceptConnectBuffer = createPacket(WitProtocol.ACCEPT_CONNECT);
		environment.sendPacketToOne(acceptConnectBuffer, card);
		
		requestCard = null;
		connect(card);
	}
	
	public void refuseConnectRequest(long lID)								//拒绝连接请求
	{
		Card card = mapCard.get(lID);
		if (card == null) return;
		
		byte[] refuseConnectBuffer = createPacket(WitProtocol.REFUSE_CONNECT);
		environment.sendPacketToOne(refuseConnectBuffer, card);
	}
	
	private void registerBroadcast()										//注册广播接收器
	{
		receiver = new ServerReceiver();
		IntentFilter filter = new IntentFilter();
		
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction(WitActions.WIFI_AP_CHANGED);
		filter.addAction(WitActions.ETH_STATE);
		filter.addAction(WitActions.UPDATE_CARD);
		
		registerReceiver(receiver, filter);
	}
	
	private void showToast(String szMsg)									//显示消息
	{
		Intent intent = new Intent();
		intent.setAction(WitActions.SHOW_TOAST);
		intent.putExtra("msg", szMsg);
		sendBroadcast(intent);
	}
	
	private void connect(Card card)
	{
		setRemoteCard(card);
		
		sendPool = Executors.newFixedThreadPool(WitProtocol.PARALLEL_THREADS);//发送线程池
		receivePool = Executors.newFixedThreadPool(WitProtocol.PARALLEL_THREADS);//接收线程池
		
		new Transmission().start();
	}
	
	private void disconnect()												//断开连接
	{
		showToast(getString(R.string.connectionTerminated));
		setRemoteCard(null);
		
		sendPool.shutdown();
		receivePool.shutdown();												//关闭两个线程池
		
		synchronized(mutex)													//清空传输列表
		{
			for (WitInputStream input : mapSend.values()) input.terminate();
			for (WitOutputStream output : mapReceive.values()) output.terminate();
			
			mapSend.clear();
			mapReceive.clear();
		}
		
		broadcastTranslistChanged();
	}
	
	public boolean isConnected()
	{
		return remoteCard != null;
	}
	
	public boolean isWaitingForConnectResponse()
	{
		return requestCard != null;
	}
	
	private void refuseStranger(Card card)									//拒绝未连接用户的连接特权请求
	{
		byte[] disconnectBuffer = createPacket(WitProtocol.DISCONNECT);
		environment.sendPacketToOne(disconnectBuffer, card);
	}
	
	private class ServerReceiver extends BroadcastReceiver					//广播接收器处理类
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)
					|| intent.getAction().equals(WitActions.WIFI_AP_CHANGED)
					|| intent.getAction().equals(WitActions.ETH_STATE))
			{
				selectNetworkEnvironment();
				Card.getMyCard().buffer(regBuffer);
				updateMyCard();
				
				return;
			}
			
			if (intent.getAction().equals(WitActions.UPDATE_CARD))
			{
				Card.getMyCard().buffer(regBuffer);
				updateMyCard();
				
				return;
			}
		}
	}
	
	private synchronized void selectNetworkEnvironment()					//选择网络环境
	{		
		boolean bHotspot = false;
		
		try
		{
			Method method = wm.getClass().getMethod("isWifiApEnabled");
			bHotspot =  (Boolean) method.invoke(wm);
		}
		catch (Exception e)
		{
			Log.d("GET_AP_STATE_ERR", e.getMessage(), e);
		}
		
		if (bHotspot)
		{
			szBSSID = null;
			environment.refreshAddresses(WitProtocol.ENV_HOTSPOT);
			
			return;
		}
		
		ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		
		if (netInfo.isConnected())											//已连接
		{
			String szNewBSSID = wm.getConnectionInfo().getBSSID();			//获取AP的MAC地址
			
			if (szBSSID != null && szBSSID.equals(szNewBSSID))
				return;														//网络无改变
			
			environment.refreshAddresses(WitProtocol.ENV_WIFI);
			
			szBSSID = szNewBSSID;
		}
		else
		{
			szBSSID = null;
			environment.refreshAddresses(WitProtocol.ENV_NONE);
		}
	}
	
	private synchronized void decode(byte[] buffer)							//解析接收到的数据包
	{
		Card card;
		int nCommandType = buffer[WitProtocol.TAG_POS];
		
		byte[] bytes = new byte[WitProtocol.ID_LEN];						//获得用户ID号
		System.arraycopy(buffer, WitProtocol.ID_START, bytes, 0, bytes.length);
		final long lID = Convertor.bytes2Long(bytes);
		
		String szFileName;
		WitInputStream input;
		WitOutputStream output;
		
		switch (nCommandType)
		{
		case WitProtocol.REG_USER:											//注册用户
			updateUserlist(lID, buffer);				
			break;
		case WitProtocol.UNREG_USER:										//取消注册
			card = mapCard.get(lID);
			
			if (card != null) mapCard.remove(card.getID());
			
			if (isWaitingForConnectResponse() && lID == requestCard.getID())
			{
				showToast(getString(R.string.connectionRequestRefused));
				setRequestCard(null);
			}
			
			if (isConnected() && lID == remoteCard.getID())
				disconnect();
			
			broadcastUserlistChanged();
		
			break;
		case WitProtocol.CONNECT:											//连接请求
			if (CardList.isBlacklisted(lID))
			{
				refuseConnectRequest(lID);
				return;
			}
			
			if (isConnected())
			{
				if (remoteCard.getID() == lID)
					acceptConnectRequest(lID);
				else
					refuseConnectRequest(lID);
				return;
			}
			
			card = mapCard.get(lID);
			if (card != null)
			{
				String szName = card.getName();
				
				Intent intent = new Intent();
				intent.setAction(WitActions.CONNECTION_REQUEST);
				
				intent.putExtra("CardID", lID);
				intent.putExtra("CardName", szName);
				
				sendBroadcast(intent);
			}
			
			break;
		case WitProtocol.ACCEPT_CONNECT:									//接收连接
			if (requestCard != null && requestCard.getID() == lID)
			{
				showToast(getString(R.string.connectionRequestAccepted));
								
				card = mapCard.get(lID);
				if (card != null)
				{
					requestCard = null;
					connect(card);
				}
			}
			
			break;
		case WitProtocol.REFUSE_CONNECT:									//拒绝连接
			if (requestCard != null && requestCard.getID() == lID)
			{
				showToast(getString(R.string.connectionRequestRefused));
				setRequestCard(null);
			}
			
			break;
		case WitProtocol.DISCONNECT:										//断开连接
			if (!isConnected() || lID != remoteCard.getID())
			{
				card = mapCard.get(lID);
				if (card != null) refuseStranger(card);
				
				return;
			}
			
			disconnect();
			
			break;
		case WitProtocol.SEND_FILENAME:										//将发送文件名添加到对方列表中
			if (!isConnected() || lID != remoteCard.getID())
			{
				card = mapCard.get(lID);
				if (card != null) refuseStranger(card);
				
				return;
			}
						
			try
			{
				WitOutputStream received = new WitOutputStream(buffer);
				addReceive(received);
			}
			catch (IOException e) { }
			
			break;
		case WitProtocol.SEND_FILE:											//发送文件开始
			if (!isConnected() || lID != remoteCard.getID())
			{
				card = mapCard.get(lID);
				if (card != null) refuseStranger(card);
				
				return;
			}
			
			bytes = new byte[WitProtocol.PORT_LEN];
			System.arraycopy(buffer, WitProtocol.PORT_START, bytes, 0, bytes.length);
			final int nPort = Convertor.bytes2Int(bytes);					//获取端口号
			
			int nAESKeySize = buffer[WitProtocol.AES_POS];
			if (nAESKeySize < 0) nAESKeySize += 256;
	    	
			int nFileNameStart = WitProtocol.AES_START + nAESKeySize;
			bytes = new byte[WitProtocol.PACKET_SIZE - nFileNameStart];
			System.arraycopy(buffer, nFileNameStart, bytes, 0, bytes.length);
			szFileName = new String(bytes).trim();
			
			synchronized(mutex)
			{
				output = mapReceive.get(szFileName);
			}
			
			final WitOutputStream output1 = output;
			if (output1 != null && !output1.isStarted())
			{
				output1.startTransmission();								//标记为开始传输
				receivePool.execute(new Runnable()							//加入线程池
				{
					public void run()
					{
						receive(output1, nPort);
					}
				});
			}
			
			break;
		case WitProtocol.RECEIVE_SUCCESS:
			if (!isConnected() || lID != remoteCard.getID())				//对方接收成功
			{
				card = mapCard.get(lID);
				if (card != null) refuseStranger(card);
				
				return;
			}
			
			nAESKeySize = buffer[WitProtocol.AES_POS];
			if (nAESKeySize < 0) nAESKeySize += 256;
	    	
			nFileNameStart = WitProtocol.AES_START + nAESKeySize;
			bytes = new byte[WitProtocol.PACKET_SIZE - nFileNameStart];
			System.arraycopy(buffer, nFileNameStart, bytes, 0, bytes.length);
			szFileName = new String(bytes).trim();
			
			synchronized(mutex)
			{
				input = mapSend.get(szFileName);
			}
			
			if (input != null)
			{
				showToast(input.getName() + ' ' + getString(R.string.sendSuccess));
				
				synchronized(mutex)
				{
					mapSend.remove(input.getName());
				}
				broadcastTranslistChanged();
			}
			
			break;
		case WitProtocol.RECEIVE_FAILED:									//对方接收失败
			if (!isConnected() || lID != remoteCard.getID())
			{
				card = mapCard.get(lID);
				if (card != null) refuseStranger(card);
				
				return;
			}
			
			nAESKeySize = buffer[WitProtocol.AES_POS];
			if (nAESKeySize < 0) nAESKeySize += 256;
	    	
			nFileNameStart = WitProtocol.AES_START + nAESKeySize;
			bytes = new byte[WitProtocol.PACKET_SIZE - nFileNameStart];
			System.arraycopy(buffer, nFileNameStart, bytes, 0, bytes.length);
			szFileName = new String(bytes).trim();
			
			synchronized(mutex)
			{
				input = mapSend.get(szFileName);
			}
			
			if (input != null)
			{
				showToast(input.getName() + ' ' + getString(R.string.sendFailed));
				
				synchronized(mutex)
				{
					mapSend.remove(input.getName());
				}
				broadcastTranslistChanged();
			}
			
			break;
		}
	}
	
	public void addSend(WitInputStream sent)								//发送文件
	{
		boolean bConflicted = false;
		
		synchronized(mutex)
		{
			bConflicted = mapSend.containsKey(sent.getName());
		}
		
		if (bConflicted)
		{
			showToast(getString(R.string.nameConflict) + ": " + sent.getName());
			return;
		}
		
		byte[] fileSendBuffer = createPacket(WitProtocol.SEND_FILENAME);
		
		synchronized(mutex)
		{
			mapSend.put(sent.getName(), sent);
		}
		
		sent.setEncrypted(bSendEncrypt, remoteKey);
		sent.buffer(fileSendBuffer);										//收集生成要发送文件的相关资料
		environment.sendPacketToOne(fileSendBuffer, remoteCard);
		
		broadcastTranslistChanged();
	}
	
	private void addReceive(WitOutputStream received)
	{
		synchronized(mutex)
		{
			mapReceive.put(received.getName(), received);
		}
		
		broadcastTranslistChanged();
	}
	
	private class Transmission extends Thread								//传输线程
	{
		@Override
		public void run()
		{
			while (isConnected())
			{
				List<WitInputStream> lstOldSend;
				
				synchronized(mutex)
				{
					lstOldSend = new Vector<WitInputStream>();
					lstOldSend.addAll(mapSend.values());
				}
				
				for (WitInputStream sent : lstOldSend)						//依次执行各发送操作
				{
					final WitInputStream input = sent;
					if (!input.isStarted() && !input.isTerminated())
					{
						input.startTransmission();							//标记为开始发送
						sendPool.execute(new Runnable()
						{
							public void run()
							{
								send(input);								//发送文件
							}
						});
					}
				}
					
				try
				{
					sleep(500);
				} 
				catch (Exception e) { }
			}
		}
	}
	
	private void send(WitInputStream input)									//发送文件
	{		
		OutputStream output = null;
		
		ServerSocket server = null;
		Socket socket = null;
		
		boolean bNeedsChecksum = input.needsChecksum();
		
		try
		{
			server = new ServerSocket(0);									//绑定一个随机端口
						
			int nPort = server.getLocalPort();								//获取端口号
			
			byte[] fileSendBuffer = createPacket(WitProtocol.SEND_FILE);
			input.buffer(fileSendBuffer);
			
			byte[] portBuffer = Convertor.int2Bytes(nPort);
			System.arraycopy(portBuffer, 0, fileSendBuffer, WitProtocol.PORT_START, portBuffer.length);
			
			environment.sendPacketToOne(fileSendBuffer, remoteCard);
			
			boolean bClientConnected = false;
			while (!bClientConnected)
			{
				socket = server.accept();									//等待客户端连接
				socket.setSoTimeout(WitProtocol.TIMEOUT);
				
				InetSocketAddress remoteSocketAddr = (InetSocketAddress) socket.getRemoteSocketAddress();
				String szRemoteAddress = remoteSocketAddr.getAddress().getHostAddress();
				
				if (!szRemoteAddress.equals(remoteCard.getIP()))
				{
					socket.close();
					continue;
				}
				
				bClientConnected = true;
				
				output = socket.getOutputStream();
				while(!input.isCompleted() && !input.isOverflowed() && !input.isTerminated())//循环把文件内容发送给对方
				{
					byte[] writeBuffer = input.read();
					
					output.write(writeBuffer);								//把内容写到输出流中发送给对方
					output.flush();
					
					broadcastTranslistChanged();
				}
				
				if (input.isCompleted() && !input.isTerminated())			//文件完全传输
				{
					if (!bNeedsChecksum)									//若不需要校验，直接输出发送成功
						showToast(input.getName() + ' ' + getString(R.string.sendSuccess));
				}
				else
				{
					bNeedsChecksum = false;									//发送失败
					showToast(input.getName() + ' ' + getString(R.string.sendFailed));
				}
			}
		} 
		catch (Exception e)
		{
			bNeedsChecksum = false;
			showToast(input.getName() + ' ' + getString(R.string.sendFailed));
		}
		finally
		{
			if (!bNeedsChecksum)
			{
				synchronized(mutex)
				{
					mapSend.remove(input.getName());
				}
			}
			
			try																//关闭各流和套接字
			{
				input.close();
				
				if (output != null) output.close();
				
				if (socket != null) socket.close();
				if (server != null) server.close();
			}
			catch (Exception e1) { }
			
			broadcastTranslistChanged();
		}
	}
	
	private void receive(WitOutputStream output, int nPort)					//接收
	{
		InputStream input = null;
		Socket socket = null;
		
		boolean bNeedsChecksum = output.needsChecksum();
		
		try
		{
			if (output.isEncrypted()) output.decryptSecretKey(myKey);
			
			Thread.sleep(100);
			
			socket = new Socket(InetAddress.getByName(remoteCard.getIP()), nPort);
			socket.setSoTimeout(WitProtocol.TIMEOUT);
			
			byte[] readBuffer = new byte[WitProtocol.STREAM_ENCRPYTED_BUFFER_SIZE];//定义数据接收缓冲区，准备接收对方传过来的文件内容
			byte[] cache = new byte[WitProtocol.STREAM_ENCRPYTED_BUFFER_SIZE];
			
			int nRead = 0, nCacheLength = 0, nResidual;
			
			input = socket.getInputStream();
			while (!output.isCompleted() && !output.isOverflowed() && !output.isTerminated())//循环读取内容
			{
				nRead = input.read(readBuffer);
				
				if (nRead == -1)
				{
					byte[] temp = new byte[nCacheLength];
					System.arraycopy(cache, 0, temp, 0, nCacheLength);					
					if (temp.length > 0) output.write(temp);
					
					break;
				}
				else if (nRead + nCacheLength < WitProtocol.STREAM_ENCRPYTED_BUFFER_SIZE)
				{
					System.arraycopy(readBuffer, 0, cache, nCacheLength, nRead);
					nCacheLength += nRead;
				}
				else														//每128字节输出
				{
					nResidual = WitProtocol.STREAM_ENCRPYTED_BUFFER_SIZE - nCacheLength;
					System.arraycopy(readBuffer, 0, cache, nCacheLength, nResidual);
					output.write(cache);
										
					nCacheLength = nRead - nResidual;
					if (nCacheLength > 0)
						System.arraycopy(readBuffer, nResidual, cache, 0, nCacheLength);
					
					broadcastTranslistChanged();
				}
			}
			
			if (output.isCompleted() && !output.isTerminated())				//是否完成
			{
				if (!bNeedsChecksum)
				{
					showToast(output.getName() + ' ' + getString(R.string.receiveSuccess));
					output.doAfterTransmission(this);
				}
				else if (output.checksum())									//校验
				{
					showToast(output.getName() + ' ' + getString(R.string.receiveSuccess));
					output.doAfterTransmission(this);
					
					byte[] fileSendBuffer = createPacket(WitProtocol.RECEIVE_SUCCESS);
					output.buffer(fileSendBuffer);
					environment.sendPacketToOne(fileSendBuffer, remoteCard);
				}															//回复发送成功
				else
				{
					showToast(output.getName() + ' ' + getString(R.string.receiveFailed));
					output.delete();										//删除失败文件
					
					byte[] fileSendBuffer = createPacket(WitProtocol.RECEIVE_FAILED);
					output.buffer(fileSendBuffer);
					environment.sendPacketToOne(fileSendBuffer, remoteCard);
				}															//回复发送失败
			}
			else
			{
				showToast(output.getName() + ' ' + getString(R.string.receiveFailed));
				output.delete();
				
				if (bNeedsChecksum)
				{
					byte[] fileSendBuffer = createPacket(WitProtocol.RECEIVE_FAILED);
					output.buffer(fileSendBuffer);
					environment.sendPacketToOne(fileSendBuffer, remoteCard);
				}
			}
		}
		catch (Exception e)
		{
			showToast(output.getName() + ' ' + getString(R.string.receiveFailed));
			output.delete();
			
			if (bNeedsChecksum)
			{
				byte[] fileSendBuffer = createPacket(WitProtocol.RECEIVE_FAILED);
				output.buffer(fileSendBuffer);
				environment.sendPacketToOne(fileSendBuffer, remoteCard);
			}
		}
		finally
		{
			synchronized(mutex)
			{
				mapReceive.remove(output.getName());
			}
			
			try																//关闭各流和套接字
			{
				output.close();
				
				if (input != null) input.close();
				
				if (socket != null) socket.close();
			}
			catch (Exception e1) { }
			
			broadcastTranslistChanged();
		}
	}
	
	public List<WitStream> getTransList()									//显示传输列表
	{
		List<WitStream> lstTrans = new Vector<WitStream>();
		
		synchronized(mutex)
		{
			lstTrans.addAll(mapSend.values());
			lstTrans.addAll(mapReceive.values());
		}
		
		return lstTrans;
	}
}
