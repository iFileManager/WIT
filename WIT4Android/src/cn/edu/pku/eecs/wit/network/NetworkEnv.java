package cn.edu.pku.eecs.wit.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.text.format.Formatter;
import android.util.Log;

import cn.edu.pku.eecs.wit.card.Card;
import cn.edu.pku.eecs.wit.util.Convertor;

public class NetworkEnv
{	
	private WifiManager wm;
		
	private InetAddress broadcastAddress;
	private DatagramSocket socket;

	private MulticastLock lock;
		
	public NetworkEnv(WitService context)								//初始化底层网络环境
	{
		wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		
		lock = wm.createMulticastLock("cn.edu.pku.eecs.wit.multicast_lock");
		lock.acquire();													//获取组播锁（HTC专用）
		
		try
		{
			refreshAddresses(WitProtocol.ENV_NONE);						//获取IP地址和组播地址
			socket = new DatagramSocket(WitProtocol.CTRL_PORT);			//绑定本地端口
		}
		catch (Exception e)
		{
			Log.e("INIT_ENV_ERR", e.getMessage(), e);
		}
	}
	
	public void close()													//关闭套接字和组播锁
	{
		try
		{
			lock.release();
			socket.close();
		}
		catch (Exception e)
		{
			Log.e("CLOSE_ENV_ERR", e.getMessage(), e);
		}
	}
	
	public void receivePacket(byte[] receivedBuffer)					//接收数据包
	{
		try
		{
			DatagramPacket packet = new DatagramPacket(receivedBuffer, receivedBuffer.length);
			socket.receive(packet);
		}
		catch (Exception e)
		{
			discardBuffer(receivedBuffer);
			Log.e("RECV_ERR", e.getMessage(), e);
		}
	}
	
	public void sendPacketToAll(final byte[] sentBuffer)				//发送给全部在线用户
	{
		Thread send = new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					DatagramPacket packet = new DatagramPacket(sentBuffer, WitProtocol.PACKET_SIZE,
							broadcastAddress, WitProtocol.CTRL_PORT);
					socket.send(packet);
				}
				catch (Exception e)
				{
					Log.e("SEND_TO_ALL_ERR", e.getMessage(), e);
				}
			}
		};
				
		try
		{
			send.start();
			send.join();
		}
		catch (InterruptedException e)
		{
			Log.e("SEND_TO_ALL_ERR", e.getMessage(), e);
		}
	}
	
	public void sendPacketToOne(final byte[] sentBuffer, Card card)		//发送给特定用户
	{
		final String szIP = card.getIP();
		
		Thread send = new Thread()
		{
			@Override
			public void run()
			{
				try
				{					
					DatagramPacket packet = new DatagramPacket(sentBuffer, WitProtocol.PACKET_SIZE,
							InetAddress.getByName(szIP), WitProtocol.CTRL_PORT);
					socket.send(packet);
				}
				catch (Exception e)
				{
					Log.e("SEND_TO_ONE_ERR", e.getMessage(), e);
				}
			}
		};
		
		try
		{
			send.start();
			send.join();
		}
		catch (InterruptedException e)
		{
			Log.e("SEND_TO_ONE_ERR", e.getMessage(), e);
		}
	}
	
	public void refreshAddresses(int nEnv)								//刷新地址
	{
		String szMyAddress, szBroadcastAddress;
		szMyAddress = szBroadcastAddress = WitProtocol.LOCALHOST;
		
		try
		{
			switch (nEnv)
			{
			case WitProtocol.ENV_WIFI:									//普通WIFI
				DhcpInfo dhcpInfo = wm.getDhcpInfo();
				
				int nMyAddress = dhcpInfo.ipAddress;
				szMyAddress = Formatter.formatIpAddress(nMyAddress);
				
				int nBroadcastAddress = (nMyAddress & dhcpInfo.netmask) | (~dhcpInfo.netmask);
				szBroadcastAddress = Formatter.formatIpAddress(nBroadcastAddress);
				
				break;													//计算出广播地址
			case WitProtocol.ENV_HOTSPOT:								//本机为热点
				WifiInfo wifiInfo = wm.getConnectionInfo();
				
				String szMacAddress = wifiInfo.getMacAddress();			//获取本机MAC
				
				Enumeration<NetworkInterface> enmInterface;
		        for (enmInterface = NetworkInterface.getNetworkInterfaces(); enmInterface.hasMoreElements();)
		        {  
		            NetworkInterface intf = enmInterface.nextElement();
		            
		            byte[] arrIntfMacAddress = intf.getHardwareAddress();
		            if (arrIntfMacAddress == null) continue;
		            
		            String szIntfMacAddress = Convertor.macBytes2String(arrIntfMacAddress);
		            if (!szMacAddress.equalsIgnoreCase(szIntfMacAddress)) continue;
		            													//检查网络接口是否为给定的MAC地址
		            szMyAddress = intf.getInetAddresses().nextElement().getHostAddress();  
		                                                				//获取IP
		            List<InterfaceAddress> lstInftAddresses = intf.getInterfaceAddresses();
		            
		            for (InterfaceAddress intfAddress : lstInftAddresses)
		            {
		            	if (intfAddress.getAddress().getHostAddress().equals(szMyAddress))
		            	{
		            		szBroadcastAddress = intfAddress.getBroadcast().getHostAddress();
		            	}												//获取广播
		            }
		            
		            break;
		        }
				
				break;
			default:
				break;
			}
			
			Card.getMyCard().setIP(szMyAddress);
			broadcastAddress = InetAddress.getByName(szBroadcastAddress);
		}
		catch (Exception e)
		{
			Log.e("REFRESH_IP_ERR", e.getMessage(), e);
			
			try
			{
				broadcastAddress = InetAddress.getByName(WitProtocol.LOCALHOST);
			}
			catch (Exception e1) { }
		}
	}
	
	private void discardBuffer(byte[] receivedBuffer)					//废弃掉buffer
	{
		Arrays.fill(receivedBuffer, (byte) -1);
	}
}
